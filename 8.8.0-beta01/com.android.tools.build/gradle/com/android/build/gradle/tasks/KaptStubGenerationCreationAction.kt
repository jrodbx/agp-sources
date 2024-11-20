/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.PublishingSpecs
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.KotlinServices
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

class KaptStubGenerationCreationAction(
    creationConfig: ComponentCreationConfig,
    kotlinServices: KotlinServices
) : KotlinTaskCreationAction<KaptGenerateStubs>(creationConfig) {

    private val kotlinJvmFactory = kotlinServices.factory

    init {
        kotlinServices.let {
            kotlinJvmFactory.addCompilerPluginDependency(
                creationConfig.services
                    .provider { "$KOTLIN_GROUP:$KAPT_ARTIFACT:${kotlinServices.kgpVersion}" }
            )
        }
    }

    override val taskName: String = creationConfig.computeTaskNameInternal("kaptGenerateStubs", "Kotlin")

    override fun getTaskProvider(): TaskProvider<out KaptGenerateStubs> {
        return kotlinJvmFactory.registerKaptGenerateStubsTask(taskName)
    }

    override fun handleProvider(task: TaskProvider<out KaptGenerateStubs>) {
        val artifacts = creationConfig.artifacts

        artifacts.setInitialProvider(task) { it.destinationDirectory }
            .on(InternalArtifactType.BUILT_IN_KAPT_STUBS_INCREMENTAL_DATA)
        artifacts.setInitialProvider(task) { it.stubsDir }
            .on(InternalArtifactType.BUILT_IN_KAPT_STUBS)
    }

    override fun configureTask(task: KaptGenerateStubs) {

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
        task.libraries.from(taskClasspath)

        // try/catch because moduleName is deprecated and may be removed in the future
        try {
            task.moduleName.set(creationConfig.name)
        } catch (e: Exception) {
            // do nothing
        }
        // try/catch because ownModuleName was removed in KGP 2.0.0
        // TODO(341765853) Remove this after MINIMUM_BUILT_IN_KOTLIN_VERSION is at least "2.0.0"
        try {
            val ownModuleNameSetter =
                task::class.java.methods.find { it.name == "setOwnModuleName"}
            ownModuleNameSetter?.invoke(task, creationConfig.name)
        } catch (e: Exception) {
            // do nothing
        }
        task.sourceSetName.set(creationConfig.name)
        task.useModuleDetection.set(true)
        task.multiPlatformEnabled.set(false)
        task.pluginClasspath.from(kotlinJvmFactory.getCompilerPlugins())

        task.kaptClasspath.from(
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.JAR
            ),
            creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR,
                AndroidArtifacts.ArtifactScope.EXTERNAL,
                AndroidArtifacts.ArtifactType.PROCESSED_JAR
            )
        )
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

        // TODO(KT-70383) pass KotlinJvmCompilerOptions from corresponding Kotlin compile task
        creationConfig.global
            .kotlinAndroidProjectExtension
            ?.compilerOptions
            ?.let { task.applyCompilerOptions(it) }
    }
}
