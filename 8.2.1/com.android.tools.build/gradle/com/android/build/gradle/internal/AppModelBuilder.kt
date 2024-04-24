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

package com.android.build.gradle.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.ide.DefaultAppBundleProjectBuildOutput
import com.android.build.gradle.internal.ide.DefaultAppBundleVariantBuildOutput
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.internal.variant.VariantModel
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.google.common.collect.ImmutableList
import org.gradle.api.Project

/**
 * [ModelBuilder] class created by [AppPlugin]. It needs to be put in a separate file to work around
 * https://issuetracker.google.com/73383831.
 */
class AppModelBuilder(
    project: Project,
    private val variantModel: VariantModel,
    config: BaseAppModuleExtension,
    extraModelInfo: ExtraModelInfo,
) : ModelBuilder<BaseAppModuleExtension>(
    project,
    variantModel,
    config,
    extraModelInfo,
) {
    override fun isBaseSplit(): Boolean {
        return true
    }

    override fun canBuild(modelName: String): Boolean {
        return super.canBuild(modelName) || modelName == AppBundleProjectBuildOutput::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): Any {
        return if (modelName == AppBundleProjectBuildOutput::class.java.name) {
            buildMinimalisticModel()
        } else super.buildAll(modelName, project)
    }

    override fun getDynamicFeatures(): Collection<String> {
        return extension.dynamicFeatures.toImmutableSet()
    }

    private fun buildMinimalisticModel(): Any {
        val variantsOutput = ImmutableList.builder<AppBundleVariantBuildOutput>()

        for (component in variantModel.variants) {
            val artifacts = component.artifacts

            // TODO(b/111168382): Remove the namespaced check check once bundle pipeline works with namespaces.
            if (component.componentType.isBaseModule && !component.global.namespacedAndroidResources) {
                val bundleFile = artifacts.get(SingleArtifact.BUNDLE)
                val apkFolder = artifacts.get(InternalArtifactType.EXTRACTED_APKS)
                variantsOutput.add(
                        DefaultAppBundleVariantBuildOutput(
                            component.name, bundleFile.get().asFile, apkFolder.get().asFile))
            }
        }

        return DefaultAppBundleProjectBuildOutput(variantsOutput.build())
    }
}
