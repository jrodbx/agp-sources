/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.kmp.resolvers

import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.ide.kmp.KotlinModelBuildingConfigurator
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.ide.IdeAdditionalArtifactResolver

/**
 * An [IdeAdditionalArtifactResolver] that serves as a hook to register models as kotlin extras so
 * that we can run during the model building phase.
 *
 * This is a workaround until the kotlin plugin provides an API with that functionality.
 */
@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinModelBuildingHook(
    private val project: Project,
    private val mainVariant: Lazy<KmpVariantImpl>,
    private val androidTarget: Lazy<KotlinMultiplatformAndroidTarget>,
    private val androidExtension: KotlinMultiplatformAndroidExtensionImpl
): IdeAdditionalArtifactResolver {
    private var registered = false

    override fun resolve(sourceSet: KotlinSourceSet, dependencies: Set<IdeaKotlinDependency>) {
        if (!registered) {
            registered = true

            KotlinModelBuildingConfigurator.setupAndroidTargetModels(
                project,
                mainVariant.value,
                androidTarget.value,
                mainVariant.value.services.projectOptions,
                mainVariant.value.services.issueReporter
            )

            KotlinModelBuildingConfigurator.setupAndroidCompilations(
                components = listOfNotNull(mainVariant.value, mainVariant.value.androidDeviceTest, mainVariant.value.unitTest),
                androidExtension.androidTestOnDeviceOptions?.instrumentationRunner,
                androidExtension.androidTestOnDeviceOptions?.instrumentationRunnerArguments ?: emptyMap(),
            )
        }
    }
}
