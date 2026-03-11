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

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.profile.PROPERTY_VARIANT_NAME_KEY
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
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
                creationConfig.services.provider { creationConfig.getExplicitApiMode() }
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
        task.configureKotlinJvmCompile(creationConfig)

        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_GENERATED_JAVA_SOURCES)?.let {
            task.source(it)
        }
        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_GENERATED_KOTLIN_SOURCES)?.let {
            task.source(it)
        }
        creationConfig.getBuiltInKaptArtifact(BUILT_IN_KAPT_CLASSES_DIR)?.let {
            task.libraries.from(it)
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

internal fun ComponentCreationConfig.getExplicitApiMode(): ExplicitApiMode? {
    return if (componentType.isForTesting) {
         ExplicitApiMode.Disabled
    } else {
        services.builtInKotlinServices.kotlinAndroidProjectExtension.explicitApi
    }
}

internal fun KotlinJvmCompile.configureKotlinJvmCompile(creationConfig: ComponentCreationConfig) {
    creationConfig.sources.kotlin {
        source(it.getAsFileTrees())
    }
    creationConfig.sources.java {
        source(it.getAsFileTrees())
    }

    libraries.from(creationConfig.global.bootClasspath)
    libraries.from(creationConfig.artifacts.getAll(MultipleArtifact.PRE_COMPILATION_CLASSES))
    libraries.from(creationConfig.getJavaClasspath(COMPILE_CLASSPATH, CLASSES_JAR))

    // Set friendPaths to allow tests/test fixtures to access internal functions/properties of the
    // main component
    if (creationConfig is NestedComponentCreationConfig) {
        val mainComponent = creationConfig.mainVariant
        val mainComponentClassesJar =
            PublishingSpecs.getVariantPublishingSpec(mainComponent.componentType)
                .getSpec(CLASSES_JAR, COMPILE_CLASSPATH.publishedTo)!!.outputType
        friendPaths.from(mainComponent.artifacts.get(mainComponentClassesJar))
    }

    // Set friendPaths to allow tests to access internal functions/properties of test fixtures
    if (creationConfig is TestComponentCreationConfig) {
        val testFixturesComponent = creationConfig.mainVariant.nestedComponents
            .filterIsInstance<TestFixturesCreationConfig>().firstOrNull()
        if (testFixturesComponent != null) {
            val testFixturesClassesJar =
                PublishingSpecs.getVariantPublishingSpec(testFixturesComponent.componentType)
                    .getSpec(CLASSES_JAR, COMPILE_CLASSPATH.publishedTo)!!.outputType
            friendPaths.from(testFixturesComponent.artifacts.get(testFixturesClassesJar))
        }
    }

    sourceSetName.set(creationConfig.name)
    useModuleDetection.set(true)
    multiPlatformEnabled.set(false)
    pluginClasspath.from(creationConfig.services.builtInKotlinServices.kotlinBaseApiPlugin.getCompilerPlugins())
}
