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

import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.scope.InternalArtifactType.ANNOTATION_PROCESSOR_LIST
import com.android.build.gradle.internal.scope.InternalArtifactType.AP_GENERATED_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_ARTIFACT
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.sdklib.AndroidTargetHash
import org.gradle.api.JavaVersion
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.util.PatternSet
import java.util.concurrent.Callable

/**
 * [TaskCreationAction] for the [JavaCompile] task.
 *
 * Note that when Kapt is not used (e.g., in Java-only projects), [JavaCompile] performs both
 * annotation processing and compilation. When Kapt is used (e.g., in most Kotlin-only or hybrid
 * Kotlin-Java projects), [JavaCompile] performs compilation only, without annotation processing.
 */
class JavaCompileCreationAction(private val variantScope: VariantScope) :
    TaskCreationAction<JavaCompile>() {

    private val classesOutputDirectory =
        variantScope.globalScope.project.objects.directoryProperty()
    private val annotationProcessorOutputDirectory =
        variantScope.globalScope.project.objects.directoryProperty()
    private val bundleArtifactFolderForDataBinding =
        variantScope.globalScope.project.objects.directoryProperty()

    init {
        val compileSdkVersion = variantScope.globalScope.extension.compileSdkVersion
        if (compileSdkVersion != null && isPostN(compileSdkVersion) && !JavaVersion.current().isJava8Compatible) {
            throw RuntimeException(
                "compileSdkVersion '$compileSdkVersion' requires JDK 1.8 or later to compile."
            )
        }
    }

    override val name: String
        get() = variantScope.getTaskName("compile", "JavaWithJavac")

    override val type: Class<JavaCompile>
        get() = JavaCompile::class.java

    override fun handleProvider(taskProvider: TaskProvider<out JavaCompile>) {
        super.handleProvider(taskProvider)

        variantScope.taskContainer.javacTask = taskProvider

        classesOutputDirectory.set(
            variantScope.artifacts.getOperations().getOutputDirectory(JAVAC, "classes")
        )
        variantScope.artifacts.producesDir(
            JAVAC,
            taskProvider,
            { classesOutputDirectory },
            fileName = "classes"
        )

        annotationProcessorOutputDirectory.set(
            variantScope.artifacts.getOperations().getOutputDirectory(AP_GENERATED_SOURCES)
        )
        variantScope.artifacts.producesDir(
            AP_GENERATED_SOURCES,
            taskProvider,
            { annotationProcessorOutputDirectory }
        )

        // Data binding artifact is one of the annotation processing outputs, only if kapt is not
        // configured.
        if (variantScope.globalScope.buildFeatures.dataBinding) {
            bundleArtifactFolderForDataBinding.set(
                variantScope.artifacts.getOperations().getOutputDirectory(DATA_BINDING_ARTIFACT)
            )
            variantScope.artifacts.producesDir(
                DATA_BINDING_ARTIFACT,
                taskProvider,
                { bundleArtifactFolderForDataBinding }
            )
        }
    }

    override fun configure(task: JavaCompile) {
        task.dependsOn(variantScope.taskContainer.preBuildTask)
        task.extensions.add(PROPERTY_VARIANT_NAME_KEY, variantScope.name)

        task.configureProperties(variantScope)
        task.configurePropertiesForAnnotationProcessing(
            variantScope,
            annotationProcessorOutputDirectory
        )

        // Wrap sources in Callable to evaluate them just before execution, b/117161463.
        val sourcesToCompile = Callable { listOf(variantScope.variantData.javaSources) }
        // Include only java sources, otherwise we hit b/144249620.
        val javaSourcesFilter = PatternSet().include("**/*.java")
        task.source = task.project.files(sourcesToCompile).asFileTree.matching(javaSourcesFilter)

        task.options.isIncremental = variantScope.globalScope.extension.compileOptions.incremental
            ?: DEFAULT_INCREMENTAL_COMPILATION

        // Record apList as input. It impacts handleAnnotationProcessors() below.
        val apList = variantScope.artifacts.getFinalProduct(ANNOTATION_PROCESSOR_LIST)
        task.inputs.files(apList).withPathSensitivity(PathSensitivity.NONE)
            .withPropertyName("annotationProcessorList")

        task.handleAnnotationProcessors(apList, variantScope.name)

        task.setDestinationDir(classesOutputDirectory.asFile)

        // Manually declare our output directory as a Task output since it's not annotated as
        // an OutputDirectory on the task implementation.
        task.outputs.dir(classesOutputDirectory)
        task.outputs.dir(annotationProcessorOutputDirectory).optional()
        task.outputs.dir(bundleArtifactFolderForDataBinding).optional()

        task.logger.info(
            "Configuring Java sources compilation with source level " +
                    "${task.sourceCompatibility} and target level ${task.targetCompatibility}."
        )
    }
}

private fun JavaCompile.handleAnnotationProcessors(
    processorListFile: Provider<RegularFile>,
    variantName: String
) {
    doFirst {
        val hasKapt = this.project.pluginManager.hasPlugin(KOTLIN_KAPT_PLUGIN_ID)

        val annotationProcessors =
            readAnnotationProcessorsFromJsonFile(processorListFile.get().asFile)
        val nonIncrementalAPs =
            annotationProcessors.filter { it.value == java.lang.Boolean.FALSE }
        val allAPsAreIncremental = nonIncrementalAPs.isEmpty()

        // Warn users about non-incremental annotation processors
        if (!hasKapt && !allAPsAreIncremental && options.isIncremental) {
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
            annotationProcessors, project.path, variantName
        )
    }
}

private fun isPostN(compileSdkVersion: String): Boolean {
    val hash = AndroidTargetHash.getVersionFromHash(compileSdkVersion)
    return hash != null && hash.apiLevel >= 24
}