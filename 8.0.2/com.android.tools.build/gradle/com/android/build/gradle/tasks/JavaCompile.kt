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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_EXPORT_CLASS_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.util.PatternSet
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.CommandLineArgumentProvider

/**
 * [TaskCreationAction] for the [JavaCompile] task.
 *
 * Note that when Kapt is not used (e.g., in Java-only projects), [JavaCompile] performs both
 * annotation processing and compilation. When Kapt is used (e.g., in most Kotlin-only or hybrid
 * Kotlin-Java projects), [JavaCompile] performs compilation only, without annotation processing.
 */
class JavaCompileCreationAction(
    private val creationConfig: ComponentCreationConfig,
    objectFactory: ObjectFactory,
    private val usingKapt: Boolean
) : TaskCreationAction<JavaCompile>() {

    private val dataBindingArtifactDir = objectFactory.directoryProperty()
    private val dataBindingExportClassListFile = objectFactory.fileProperty()

    override val name: String
        get() = creationConfig.computeTaskName("compile", "JavaWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<JavaCompile>) {
        super.handleProvider(taskProvider)

        creationConfig.taskContainer.javacTask = taskProvider

        val artifacts = creationConfig.artifacts

        artifacts
            .setInitialProvider(taskProvider) { it.destinationDirectory }
            .withName("classes")
            .on(JAVAC)

        artifacts
            .setInitialProvider(taskProvider) { it.options.generatedSourceOutputDirectory }
            // Setting a name is not required, but a lot of AGP and IDE tests are assuming this name
            // so we leave it here for now.
            .withName(AP_GENERATED_SOURCES_DIR_NAME)
            .on(AP_GENERATED_SOURCES)

        if (creationConfig.buildFeatures.dataBinding) {
            // Register data binding artifacts as outputs. There are 2 ways to do this:
            //    (1) Register with JavaCompile when Kapt is not used, and register with Kapt when
            //        Kapt is used.
            //    (2) Always register with JavaCompile, and when Kapt is used, replace them with
            //        Kapt.
            // The first way is simpler but unfortunately will break the publishing of the artifacts
            // because publishing takes place before the registration with Kapt (bug 161814391).
            // Therefore, we'll have to do it the second way.
            registerDataBindingOutputs(
                dataBindingArtifactDir,
                dataBindingExportClassListFile,
                creationConfig.componentType.isExportDataBindingClassList,
                taskProvider,
                artifacts,
                forJavaCompile = true
            )
        }
    }

    override fun configure(task: JavaCompile) {
        task.dependsOn(creationConfig.taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, creationConfig.name)

        // Use Gradle toolchain configured via java extension
        task.project.extensions.getByType(JavaPluginExtension::class.java).let {
            if (it.toolchain.languageVersion.isPresent) {
                val toolchainService =
                    task.project.extensions.getByType(JavaToolchainService::class.java)
                task.javaCompiler.set(toolchainService.compilerFor(it.toolchain))
            }
        }

        task.configureProperties(creationConfig)
        // Set up the annotation processor classpath even when Kapt is used, because Java compiler
        // plugins like ErrorProne share their classpath with annotation processors (see
        // https://github.com/gradle/gradle/issues/6573), and special annotation processors like
        // Lombok want to run via JavaCompile (see https://youtrack.jetbrains.com/issue/KT-7112).
        task.configurePropertiesForAnnotationProcessing(creationConfig)

        task.source = computeJavaSourceWithoutDependencies(creationConfig)

        task.options.compilerArgumentProviders.add(
            JavaCompileOptionsForRoom(
                creationConfig.artifacts.get(ANNOTATION_PROCESSOR_LIST),
                creationConfig.global.compileOptions.targetCompatibility.isJava8Compatible
            )
        )
        task.options.isIncremental = creationConfig.global.compileOptionsIncremental
            ?: DEFAULT_INCREMENTAL_COMPILATION

        // Record apList as input. It impacts recordAnnotationProcessors() below.
        val apList = creationConfig.artifacts.get(ANNOTATION_PROCESSOR_LIST)
        task.inputs.files(apList).withPathSensitivity(PathSensitivity.NONE)
            .withPropertyName("annotationProcessorList")
        task.recordAnnotationProcessors(
            apList,
            creationConfig.name,
            getBuildService(creationConfig.services.buildServiceRegistry)
        )

        if (creationConfig.buildFeatures.dataBinding) {
            // Data binding artifacts are part of the annotation processing outputs of JavaCompile
            // if Kapt is not used; otherwise, they are the outputs of Kapt.
            if (!usingKapt) {
                task.outputs.dir(dataBindingArtifactDir).withPropertyName("dataBindingArtifactDir")
                if (creationConfig.componentType.isExportDataBindingClassList) {
                    task.outputs.file(dataBindingExportClassListFile)
                        .withPropertyName("dataBindingExportClassListFile")
                }
            }
        }

        task.logger.debug(
            "Configuring Java sources compilation for '${task.name}' with source level " +
                    "${task.sourceCompatibility} and target level ${task.targetCompatibility}."
        )
    }
}

/** Registers data binding artifacts as outputs of JavaCompile or Kapt. */
fun registerDataBindingOutputs(
    dataBindingArtifactDir: DirectoryProperty,
    dataBindingExportClassListFile: RegularFileProperty,
    isExportDataBindingClassList: Boolean,
    taskProvider: TaskProvider<out Task>,
    artifacts: ArtifactsImpl,
    forJavaCompile: Boolean /* Whether the task is JavaCompile or Kapt */
) {
    if (forJavaCompile) {
        artifacts
            .setInitialProvider(taskProvider) { dataBindingArtifactDir }
            .on(DATA_BINDING_ARTIFACT)
        if (isExportDataBindingClassList) {
            artifacts
                .setInitialProvider(taskProvider) { dataBindingExportClassListFile }
                .on(DATA_BINDING_EXPORT_CLASS_LIST)
        }
    } else {
        artifacts
            .use(taskProvider)
            .wiredWith { dataBindingArtifactDir }
            .toCreate(DATA_BINDING_ARTIFACT)
        if (isExportDataBindingClassList) {
            artifacts
                .use(taskProvider)
                .wiredWith { dataBindingExportClassListFile }
                .toCreate(DATA_BINDING_EXPORT_CLASS_LIST)
        }
    }
}

private class JavaCompileOptionsForRoom(

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val annotationProcessorListFile: Provider<RegularFile>,

    @get:Input
    val isTargetJava8Compatible: Boolean
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(annotationProcessorListFile.get().asFile).keys
        if (annotationProcessors.any { it.contains(ANDROIDX_ROOM_ROOM_COMPILER) }) {
            // Add javac option `-parameters` to store parameter names of methods in the generated
            // class files, which Room annotation processor requires in order for it to be
            // incremental (see bugs 189326895, 159501719).
            // Note that this option is only available on JDK version 8+ and for target Java version
            // 8+ (see bug 169252018).
            if (JavaVersion.current().isJava8Compatible && isTargetJava8Compatible) {
                return listOf(PARAMETERS)
            }
        }
        return emptyList()
    }
}

private fun JavaCompile.recordAnnotationProcessors(
    processorListFile: Provider<RegularFile>,
    variantName: String,
    analyticsService: Provider<AnalyticsService>
) {
    val projectPath = this.project.path
    doFirst {
        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs =
            annotationProcessors.filter { it.value == ProcessorInfo.NON_INCREMENTAL_AP }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // Warn users about non-incremental annotation processors
        if (!allAPsAreIncremental && options.isIncremental) {
            logger
                .warn(
                    "The following annotation processors are not incremental:" +
                            " ${nonIncrementalAPs.keys.joinToString(", ")}.\n" +
                            "Make sure all annotation processors are incremental" +
                            " to improve your build speed."
                )
        }

        // Record annotation processors for analytics purposes. This recording needs to happen here
        // instead of JavaPreCompileTask as it needs to be done even in incremental builds where
        // JavaPreCompileTask may be UP-TO-DATE.
        recordAnnotationProcessorsForAnalytics(
            annotationProcessors, projectPath, variantName, analyticsService.get()
        )
    }
}

fun computeJavaSourceWithoutDependencies(creationConfig: ComponentCreationConfig): FileTree {
    // Include only java sources, otherwise we hit b/144249620.
    val javaSourcesFilter = PatternSet().include("**/*.java")
    return creationConfig.services.fileCollection().also { fileCollection ->
        // do not resolve the provider before execution phase, b/117161463.
       creationConfig.sources.java { javaSources ->
           // the KAPT plugin is looking up the JavaCompile.sources and resolving it at
           // configuration time which requires us to pass the old variant API version.
           // see b/259343260
           fileCollection.from(javaSources.getAsFileTreesForOldVariantAPI())
       }
    }.asFileTree.matching(javaSourcesFilter)
}

fun computeJavaSource(creationConfig: ComponentCreationConfig): FileTree {
    // Include only java sources, otherwise we hit b/144249620.
    val javaSourcesFilter = PatternSet().include("**/*.java")
    return creationConfig.services.fileCollection().also { fileCollection ->
        // do not resolve the provider before execution phase, b/117161463.
        creationConfig.sources.java { javaSources ->
            // the KAPT plugin is looking up the JavaCompile.sources and resolving it at
            // configuration time which requires us to pass the old variant API version.
            // see b/259343260
            fileCollection.from(javaSources.getAsFileTrees())
        }
    }.asFileTree.matching(javaSourcesFilter)
}

private const val AP_GENERATED_SOURCES_DIR_NAME = "out"
private const val ANDROIDX_ROOM_ROOM_COMPILER = "androidx.room:room-compiler"
private const val PARAMETERS = "-parameters"
