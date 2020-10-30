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

import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.errors.SyncIssueReporter
import com.android.build.gradle.internal.ide.DefaultAppBundleProjectBuildOutput
import com.android.build.gradle.internal.ide.DefaultAppBundleVariantBuildOutput
import com.android.build.gradle.internal.ide.ModelBuilder
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
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
    globalScope: GlobalScope,
    private val variantModel: VariantModel,
    taskManager: TaskManager,
    config: BaseAppModuleExtension,
    extraModelInfo: ExtraModelInfo,
    syncIssueReporter: SyncIssueReporter,
    projectType: Int
) : ModelBuilder<BaseAppModuleExtension>(
    globalScope,
    variantModel,
    taskManager,
    config,
    extraModelInfo,
    syncIssueReporter,
    projectType
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

    override fun getDynamicFeatures(): MutableCollection<String> {
        return extension.dynamicFeatures
    }

    private fun buildMinimalisticModel(): Any {
        val variantsOutput = ImmutableList.builder<AppBundleVariantBuildOutput>()

        for (variantScope in variantModel.variants) {
            val artifacts = variantScope.artifacts

            if (artifacts.hasFinalProduct(InternalArtifactType.BUNDLE)) {
                val bundleFile = artifacts.getFinalProduct(InternalArtifactType.BUNDLE)
                val apkFolder = artifacts.getFinalProduct(InternalArtifactType.EXTRACTED_APKS)
                variantsOutput.add(
                        DefaultAppBundleVariantBuildOutput(
                            variantScope.name, bundleFile.get().asFile, apkFolder.get().asFile))
            }
        }

        return DefaultAppBundleProjectBuildOutput(variantsOutput.build())
    }
}
