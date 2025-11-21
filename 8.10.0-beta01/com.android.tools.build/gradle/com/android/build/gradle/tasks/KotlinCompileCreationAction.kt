/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_GENERATED_JAVA_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_GENERATED_KOTLIN_SOURCES
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.KotlinBaseApiVersion
import com.android.build.gradle.internal.services.KotlinServices
import com.android.build.gradle.internal.utils.MINIMUM_BUILT_IN_KOTLIN_VERSION
import com.android.builder.errors.IssueReporter
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KotlinCompileCreationAction(
    creationConfig: ComponentCreationConfig,
    private val kotlinServices: KotlinServices
) : KotlinTaskCreationAction<KotlinJvmCompile>(creationConfig) {

    private val kotlinJvmFactory = kotlinServices.factory

    override val taskName: String = creationConfig.computeTaskNameInternal("compile", "Kotlin")

    override fun getTaskProvider(): TaskProvider<out KotlinJvmCompile> {
        if (kotlinServices.kotlinBaseApiVersion > KotlinBaseApiVersion.VERSION_1) {
            val compilerOptions =
                creationConfig.global.kotlinAndroidProjectExtension?.compilerOptions
            // TODO(b/341765853) never allow null compilerOptions once AGP always adds the kotlin
            //  extension.
            val allowNullCompilerOptions =
                creationConfig.componentType.isTestFixturesComponent ||
                        creationConfig.componentType.isForScreenshotPreview
            if (compilerOptions == null && !allowNullCompilerOptions) {
                // This should never happen.
                creationConfig.services
                    .issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC,
                        RuntimeException("Unable to access kotlin extension.")
                    )
            }
            return kotlinJvmFactory.registerKotlinJvmCompileTask(
                taskName,
                compilerOptions ?: kotlinJvmFactory.createCompilerJvmOptions(),
                creationConfig.services
                    .provider { creationConfig.global.kotlinAndroidProjectExtension?.explicitApi }
            )
        }
        return kotlinJvmFactory.registerKotlinJvmCompileTask(taskName, creationConfig.name)
    }

    override fun handleProvider(task: TaskProvider<out KotlinJvmCompile>) {
        val artifacts = creationConfig.artifacts
        artifacts.setInitialProvider(task) { it.destinationDirectory }
            .withName("classes")
            .on(InternalArtifactType.BUILT_IN_KOTLINC)
    }

    override fun configureTask(task: KotlinJvmCompile) {
        creationConfig.sources.kotlin {
            task.source(it.getAsFileTrees())
        }
        creationConfig.sources.java {
            task.source(it.getAsFileTrees())
        }
        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_GENERATED_JAVA_SOURCES)?.let { task.source(it) }
        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_GENERATED_KOTLIN_SOURCES)
            ?.let { task.source(it) }

        val taskClasspath =
            creationConfig.services.fileCollection().from(
                creationConfig.global.bootClasspath,
                creationConfig.getJavaClasspath(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR,
                    null
                ),
            )
        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_CLASSES_DIR)?.let { taskClasspath.from(it) }
        task.libraries.setFrom(taskClasspath)

        task.sourceSetName.set(creationConfig.name)
        task.useModuleDetection.set(true)
        task.multiPlatformEnabled.set(false)
        task.pluginClasspath.from(kotlinJvmFactory.getCompilerPlugins())

        // TODO(b/259523353) - fix this
        // task.pluginOptions.addAll(creationConfig.kotlinCompilerOptions!!)

        // Add friendPaths to allow access to internal properties of main variant
        if (creationConfig is NestedComponentCreationConfig) {
            val mainVariant = creationConfig.mainVariant
            val internalArtifactType =
                PublishingSpecs.getVariantPublishingSpec(mainVariant.componentType)
                    .getSpec(
                        AndroidArtifacts.ArtifactType.CLASSES_JAR,
                        AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH.publishedTo
                    )
                    ?.outputType
            internalArtifactType?.let {
                task.friendPaths.from(
                    creationConfig.services.fileCollection(mainVariant.artifacts.get(it))
                )
            }
        }

        if (kotlinServices.kotlinBaseApiVersion < KotlinBaseApiVersion.VERSION_2) {
            creationConfig.global
                .kotlinAndroidProjectExtension
                ?.compilerOptions
                ?.let { task.applyCompilerOptions(it) }
        }
    }
}

/** Base class for Built-in Kotlin/Kapt task registration. */
abstract class KotlinTaskCreationAction<TASK : Task>(
    protected val creationConfig: ComponentCreationConfig
) {

    protected abstract val taskName: String

    protected abstract fun getTaskProvider(): TaskProvider<out TASK>

    protected abstract fun handleProvider(task: TaskProvider<out TASK>)

    protected abstract fun configureTask(task: TASK)

    fun registerTask(): TaskProvider<out TASK> {
        val taskProvider = getTaskProvider()
        handleProvider(taskProvider)

        taskProvider.configure {
            val taskContainer: MutableTaskContainer = creationConfig.taskContainer
            it.dependsOn(taskContainer.preBuildTask)
            it.extensions.add(PROPERTY_VARIANT_NAME_KEY, creationConfig.name)

            configureTask(it)
        }
        return taskProvider
    }
}

/**
 * Add conventions for KotlinJvmCompile.compilerOptions properties based on [options].
 *
 * TODO(b/341765853) remove this after [MINIMUM_BUILT_IN_KOTLIN_VERSION] >= 2.1.0-Beta2 because the
 *  compiler options are passed to the task registration functions starting with Kotlin 2.1.0-Beta2
 */
internal fun KotlinJvmCompile.applyCompilerOptions(options: KotlinJvmCompilerOptions) {
    compilerOptions {
        jvmTarget.convention(options.jvmTarget)
        javaParameters.convention(options.javaParameters)
        moduleName.convention(options.moduleName)
        noJdk.convention(options.noJdk)

        apiVersion.convention(options.apiVersion)
        languageVersion.convention(options.languageVersion)

        freeCompilerArgs.convention(options.freeCompilerArgs)
        allWarningsAsErrors.convention(options.allWarningsAsErrors)
        suppressWarnings.convention(options.suppressWarnings)
        verbose.convention(options.verbose)
    }
}

