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
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_GENERATED_JAVA_SOURCES
import com.android.build.gradle.internal.scope.InternalArtifactType.BUILT_IN_KAPT_GENERATED_KOTLIN_SOURCES
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.BuiltInKotlinServices
import com.android.build.gradle.internal.utils.KgpVersion
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KotlinCompileCreationAction(
    creationConfig: ComponentCreationConfig,
    private val kotlinServices: BuiltInKotlinServices
) : KotlinTaskCreationAction<KotlinJvmCompile>(creationConfig) {

    private val kotlinJvmFactory = kotlinServices.kotlinBaseApiPlugin

    override val taskName: String = creationConfig.computeTaskNameInternal("compile", "Kotlin")

    override fun getTaskProvider(): TaskProvider<out KotlinJvmCompile> {
        if (kotlinServices.kgpVersion >= KgpVersion.KGP_2_1_0) {
            val kotlinAndroidProjectExtension = kotlinServices.kotlinAndroidProjectExtension
            return kotlinJvmFactory.registerKotlinJvmCompileTask(
                taskName,
                kotlinAndroidProjectExtension.compilerOptions,
                creationConfig.explicitApiModeProvider
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

        if (kotlinServices.kgpVersion < KgpVersion.KGP_2_1_0) {
            task.applyCompilerOptions(kotlinServices.kotlinAndroidProjectExtension.compilerOptions)
        }

        task.ensureConsistentJvmTargetWithJavaCompileTask()
    }

    private fun KotlinJvmCompile.ensureConsistentJvmTargetWithJavaCompileTask() {
        val javaCompileJvmTarget = creationConfig.global.compileOptions.targetCompatibility.toJvmTarget()

        // Set `javaCompileJvmTarget` as the default JVM target for Kotlin compile tasks
        // (see b/408242956)
        creationConfig.services.builtInKotlinServices.kotlinAndroidProjectExtension.compilerOptions
            .jvmTarget.convention(javaCompileJvmTarget)

        // Also ensure that the user doesn't set a different JVM target for Kotlin compile tasks.
        // This check needs to run at execution time as `kotlinCompileJvmTarget` may not be finalized yet.
        inputs.property("javaCompileJvmTarget", javaCompileJvmTarget)
        doFirst {
            val kotlinCompileJvmTarget = compilerOptions.jvmTarget.get()
            check(javaCompileJvmTarget == kotlinCompileJvmTarget) {
                """
                Inconsistent JVM targets between Java and Kotlin compile tasks: ${javaCompileJvmTarget.target} and ${kotlinCompileJvmTarget.target}.
                To fix this issue, use the same JVM target for both tasks.
                For more details, see https://issuetracker.google.com/408242956.
                """.trimIndent()
            }
        }
    }

    private fun JavaVersion.toJvmTarget(): JvmTarget {
        // JvmTarget.fromTarget() can recognize "1.8" but not "8" so we need to special-case it
        return JvmTarget.fromTarget(if (majorVersion == "8") "1.8" else majorVersion)
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
