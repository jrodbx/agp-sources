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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.BuiltInKotlinServices
import com.android.build.gradle.internal.utils.KgpVersion
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KaptExtensionConfig
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KaptStubGenerationCreationAction(
    creationConfig: ComponentCreationConfig,
    private val kotlinServices: BuiltInKotlinServices,
    private val kotlinCompileTaskProvider: TaskProvider<out KotlinJvmCompile>,
    private val kaptExtension: KaptExtensionConfig
) : KotlinTaskCreationAction<KaptGenerateStubs>(creationConfig) {

    private val kotlinJvmFactory = kotlinServices.kotlinBaseApiPlugin

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
        if (kotlinServices.kgpVersion >= KgpVersion.KGP_2_1_0) {
            return kotlinJvmFactory.registerKaptGenerateStubsTask(
                taskName,
                kotlinCompileTaskProvider,
                kaptExtension,
                creationConfig.explicitApiModeProvider
            )
        }
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
        task.configureKotlinJvmCompile(creationConfig)
        task.kaptClasspath.from(creationConfig.getAnnotationProcessorJars())
    }
}
