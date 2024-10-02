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
import com.android.build.gradle.internal.scope.MutableTaskContainer
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KotlinCompileCreationAction(private val creationConfig: ComponentCreationConfig) {

    val taskName: String = creationConfig.computeTaskNameInternal("compile", "Kotlin")

    private fun getTaskFactory(): TaskProvider<out KotlinJvmCompile> {
        return creationConfig.services
            .kotlinServices!!
            .factory
            .registerKotlinJvmCompileTask(
                taskName,
                creationConfig.name
            )
    }

    private fun handleProvider(task: TaskProvider<out KotlinJvmCompile>) {
        val artifacts = creationConfig.artifacts
        artifacts.setInitialProvider(task) { it.destinationDirectory }
            .withName("classes")
            .on(InternalArtifactType.KOTLINC)
    }

    private fun configureTask(task: KotlinJvmCompile) {
        creationConfig.sources.kotlin {
            task.source(it.getAsFileTrees())
        }
        creationConfig.sources.java {
            task.source(it.getAsFileTrees())
        }

        val taskClasspath =
            creationConfig.services.fileCollection().from(
                creationConfig.getJavaClasspath(
                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR,
                    null
                ),
                creationConfig.global.bootClasspath
            )
        task.libraries.setFrom(taskClasspath)

        task.sourceSetName.set(creationConfig.name)
        task.useModuleDetection.set(true)
        task.multiPlatformEnabled.set(false)

        task.pluginClasspath
            .from(creationConfig.services.kotlinServices!!.factory.getCompilerPlugins())
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

        creationConfig.global.kotlinOptions?.let { task.applyJvmOptions(it) }
    }

    fun registerTask() {
        val taskProvider = getTaskFactory()
        handleProvider(taskProvider)

        taskProvider.configure {
            val taskContainer: MutableTaskContainer = creationConfig.taskContainer
            it.dependsOn(taskContainer.preBuildTask)
            it.extensions.add(PROPERTY_VARIANT_NAME_KEY, creationConfig.name)

            configureTask(it)
        }
    }
}

private fun KotlinJvmCompile.applyJvmOptions(options: KotlinJvmOptions) {
    kotlinOptions {
        // TODO(b/259523353): Initialize task options from the DSL object, add API in KGP for
        //  automatic copying
        jvmTarget = options.jvmTarget
        javaParameters = options.javaParameters
        moduleName = options.moduleName
        noJdk = options.noJdk

        apiVersion = options.apiVersion
        languageVersion = options.languageVersion
        useK2 = options.useK2

        freeCompilerArgs = options.freeCompilerArgs
        allWarningsAsErrors = options.allWarningsAsErrors
        suppressWarnings = options.suppressWarnings
        verbose = options.verbose
    }
}
