/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("JavaCompileUtils")

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.CONFIG_NAME_ANDROID_JDK_IMAGE
import com.android.build.gradle.internal.dependency.JDK_IMAGE_OUTPUT_DIR
import com.android.build.gradle.internal.dependency.getJdkImageFromTransform
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.builder.errors.IssueReporter
import com.android.sdklib.AndroidTargetHash
import com.android.utils.FileUtils
import com.google.common.base.Joiner
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.wireless.android.sdk.stats.AnnotationProcessorInfo
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.Serializable
import java.io.UncheckedIOException
import java.util.jar.JarFile

const val ANNOTATION_PROCESSORS_INDICATOR_FILE =
    "META-INF/services/javax.annotation.processing.Processor"
const val INCREMENTAL_ANNOTATION_PROCESSORS_INDICATOR_FILE =
    "META-INF/gradle/incremental.annotation.processors"

/** Whether incremental compilation is enabled or disabled by default. */
const val DEFAULT_INCREMENTAL_COMPILATION = true

/**
 * Configures a [JavaCompile] task with necessary properties to perform compilation and/or
 * annotation processing.
 *
 * @see [JavaCompile.configurePropertiesForAnnotationProcessing]
 */
fun JavaCompile.configureProperties(creationConfig: ComponentCreationConfig, task: JavaCompile) {
    val globalScope = creationConfig.globalScope
    val compileOptions = globalScope.extension.compileOptions

    if (compileOptions.sourceCompatibility.isJava9Compatible) {
        checkSdkCompatibility(globalScope.extension.compileSdkVersion!!, creationConfig.services.issueReporter)
        checkNotNull(task.project.configurations.findByName(CONFIG_NAME_ANDROID_JDK_IMAGE)) {
            "The $CONFIG_NAME_ANDROID_JDK_IMAGE configuration must exist for Java 9+ sources."
        }
        val jdkImage = getJdkImageFromTransform(creationConfig.services.projectInfo.getProject())

        this.options.compilerArgumentProviders.add(JdkImageInput(jdkImage))
        // Make Javac generate legacy bytecode for string concatenation, see b/65004097
        this.options.compilerArgs.add("-XDstringConcat=inline")
        this.classpath = project.files(
            // classes(e.g. android.jar) that were previously passed through bootstrapClasspath need to be provided
            // through classpath
            creationConfig.variantScope.bootClasspath,
            creationConfig.getJavaClasspath(COMPILE_CLASSPATH, CLASSES_JAR, null)
        )
    } else {
        this.options.bootstrapClasspath = creationConfig.variantScope.bootClasspath
        this.classpath = creationConfig.getJavaClasspath(COMPILE_CLASSPATH, CLASSES_JAR, null)
    }

    this.sourceCompatibility = compileOptions.sourceCompatibility.toString()
    this.targetCompatibility = compileOptions.targetCompatibility.toString()
    this.options.encoding = compileOptions.encoding
}

/**
 * Configures a [JavaCompile] task with necessary properties to perform annotation processing.
 *
 * @see [JavaCompile.configureProperties]
 */
fun JavaCompile.configurePropertiesForAnnotationProcessing(
    creationConfig: ComponentCreationConfig
) {
    val processorOptions = creationConfig.variantDslInfo.javaCompileOptions.annotationProcessorOptions
    val compileOptions = this.options

    configureAnnotationProcessorPath(creationConfig)

    if (processorOptions.classNames.isNotEmpty()) {
        compileOptions.compilerArgs.add("-processor")
        compileOptions
            .compilerArgs
            .add(Joiner.on(',').join(processorOptions.classNames))
    }

    for ((key, value) in processorOptions.arguments) {
        compileOptions.compilerArgs.add("-A$key=$value")
    }

    compileOptions.compilerArgumentProviders.addAll(processorOptions.compilerArgumentProviders)
}

/**
 * Configures the annotation processor path for a [JavaCompile] task.
 *
 * @see [JavaCompile.configurePropertiesForAnnotationProcessing]
 */
fun JavaCompile.configureAnnotationProcessorPath(creationConfig: ComponentCreationConfig) {
    // Optimization: For project jars, query for JAR instead of PROCESSED_JAR as project jars are
    // currently considered already processed (unlike external jars).
    val projectJars = creationConfig.variantDependencies
            .getArtifactFileCollection(ANNOTATION_PROCESSOR, PROJECT, JAR)
    val externalJars = creationConfig.variantDependencies
            .getArtifactFileCollection(ANNOTATION_PROCESSOR, EXTERNAL, PROCESSED_JAR)
    options.annotationProcessorPath = projectJars.plus(externalJars)
}

data class SerializableArtifact(
    val displayName: String,
    val file: File
) : Serializable {

    constructor(artifact: ResolvedArtifactResult) : this(artifact.id.displayName, artifact.file)
}


/**
 * Detects all the annotation processors that will be executed and finds out whether they are
 * incremental or not.
 *
 * NOTE: The format of the annotation processor names is currently not consistent. If the processors
 * are specified from the DSL's annotation processor options, the format is
 * "com.example.processor.SampleProcessor". If the processors are auto-detected on the annotation
 * processor classpath, the format is "processor.jar (com.example.processor:processor:1.0)".
 *
 * @return the map from annotation processors to Boolean values indicating whether they are
 * incremental or not
 */
fun detectAnnotationProcessors(
    apOptionClassNames: List<String>,
    processorClasspath: Collection<SerializableArtifact>
): Map<String, Boolean> {
    val processors = mutableMapOf<String, Boolean>()

    if (!apOptionClassNames.isEmpty()) {
        // If the processor names are specified, the Java compiler will run only those
        for (processor in apOptionClassNames) {
            // TODO Assume the annotation processors are non-incremental for now, we will improve
            // this later. We will also need to check if the processor names can be found on the
            // annotation processor or compile classpath.
            processors[processor] = false
        }
    } else {
        // If the processor names are not specified, the Java compiler will auto-detect them on the
        // annotation processor classpath.
        val processorArtifacts = mutableMapOf<SerializableArtifact, Boolean>()
        processorArtifacts.putAll(detectAnnotationProcessors(processorClasspath))

        processors.putAll(processorArtifacts.mapKeys { it.key.displayName })
    }

    return processors
}

/**
 * Detects all the annotation processors in the given [ArtifactCollection] and finds out whether
 * they are incremental or not.
 *
 * @return the map from annotation processors to Boolean values indicating whether they are
 * incremental or not
 */
fun detectAnnotationProcessors(
    artifacts: Collection<SerializableArtifact>
): Map<SerializableArtifact, Boolean> {
    // TODO We assume that an artifact has an annotation processor if it contains
    // ANNOTATION_PROCESSORS_INDICATOR_FILE, and the processor is incremental if it contains
    // INCREMENTAL_ANNOTATION_PROCESSORS_INDICATOR_FILE. We need to revisit this assumption as the
    // processors may register as incremental dynamically.
    val processors = mutableMapOf<SerializableArtifact, Boolean>()

    for (artifact in artifacts) {
        val artifactFile = artifact.file
        if (artifactFile.isDirectory) {
            if (File(artifactFile, ANNOTATION_PROCESSORS_INDICATOR_FILE).exists()) {
                processors[artifact] =
                        File(artifactFile, INCREMENTAL_ANNOTATION_PROCESSORS_INDICATOR_FILE)
                            .exists()
            }
        } else if (artifactFile.isFile) {
            try {
                JarFile(artifactFile).use { jarFile ->
                    if (jarFile.getJarEntry(ANNOTATION_PROCESSORS_INDICATOR_FILE) != null) {
                        processors[artifact] = jarFile.getJarEntry(
                            INCREMENTAL_ANNOTATION_PROCESSORS_INDICATOR_FILE
                        ) != null
                    }
                }
            } catch (e: IOException) {
                // Can happen when we encounter a folder instead of a jar; for instance, in
                // sub-modules. We're just displaying a warning, so there's no need to stop the
                // build here. See http://issuetracker.google.com/64283041.
            }
        }
    }

    return processors
}

/**
 * Writes the map from annotation processors to Boolean values indicating whether they are
 * incremental or not, to the given file in Json format.
 */
fun writeAnnotationProcessorsToJsonFile(
    processors: Map<String, Boolean>, processorListFile: File
) {
    val gson = GsonBuilder().create()
    try {
        FileUtils.deleteIfExists(processorListFile)
        FileWriter(processorListFile).use { writer -> gson.toJson(processors, writer) }
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }
}

/**
 * Returns the map from annotation processors to Boolean values indicating whether they are
 * incremental or not, from the given Json file.
 *
 * NOTE: The format of the annotation processor names is currently not consistent. See
 * [detectAnnotationProcessors] where the processors are detected.
 */
fun readAnnotationProcessorsFromJsonFile(
    processorListFile: File
): Map<String, Boolean> {
    val gson = GsonBuilder().create()
    try {
        FileReader(processorListFile).use { reader ->
            return gson.fromJson(reader, object :
                TypeToken<Map<String, Boolean>>() {
            }.type)
        }
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }

}

/**
 * Records names & incrementality of annotation processors, and whether all of them
 * are incremental to see if the user can run annotation processing incrementally.
 */
fun recordAnnotationProcessorsForAnalytics(
    processors: Map<String, Boolean>,
    projectPath: String,
    variantName: String,
    analyticService: AnalyticsService
) {
    val variant = analyticService.getVariantBuilder(projectPath, variantName)
    for (processor in processors.entries) {
        val builder = AnnotationProcessorInfo.newBuilder()
        builder.spec = processor.key
        builder.isIncremental = processor.value
        variant?.addAnnotationProcessors(builder)
    }
    variant?.isAnnotationProcessingIncremental = !processors.values.contains(false)
}

private fun checkSdkCompatibility(compileSdkVersion: String, issueReporter: IssueReporter) {
    compileSdkVersion.let {
        if (AndroidTargetHash.getVersionFromHash(it)!!.featureLevel < 30) {
            issueReporter
                .reportError(
                    IssueReporter.Type.GENERIC, "In order to compile Java 9+ source, " +
                    "please set compileSdkVersion to 30 or above"
                )
        }
    }
}

class JdkImageInput(@get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val jdkImage: FileCollection) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("--system", jdkImage.singleFile.resolve(JDK_IMAGE_OUTPUT_DIR).absolutePath)
}
