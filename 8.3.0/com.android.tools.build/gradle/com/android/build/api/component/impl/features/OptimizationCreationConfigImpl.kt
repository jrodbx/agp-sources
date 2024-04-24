/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl.features

import com.android.build.api.variant.CanMinifyAndroidResourcesBuilder
import com.android.build.api.variant.CanMinifyCodeBuilder
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.build.gradle.internal.utils.immutableListBuilder
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import java.io.File

class OptimizationCreationConfigImpl(
    private val component: ConsumableCreationConfig,
    private val dslInfo: OptimizationDslInfo,
    private val minifyCodeBuilder: CanMinifyCodeBuilder?,
    private val minifyAndroidResourcesBuilder: CanMinifyAndroidResourcesBuilder?,
    internalServices: VariantServices,
    baseModuleMetadata: Provider<ModuleMetadata>? = null
): OptimizationCreationConfig {

    override val proguardFiles: ListProperty<RegularFile> by lazy(LazyThreadSafetyMode.NONE) {
        internalServices.listPropertyOf(RegularFile::class.java) {
            if (component is TestCreationConfig) {
                val projectDir = internalServices.projectInfo.projectDirectory
                it.addAll(
                    dslInfo.gatherProguardFiles(ProguardFileType.TEST).map { file ->
                        projectDir.file(file.absolutePath)
                    }
                )
            } else {
                dslInfo.getProguardFiles(it)
            }
        }
    }

    override val consumerProguardFiles: List<File> by lazy(LazyThreadSafetyMode.NONE) {
        immutableListBuilder<File> {
            addAll(dslInfo.gatherProguardFiles(ProguardFileType.CONSUMER))
            // We include proguardFiles if we're in a dynamic-feature module.
            if (component.componentType.isDynamicFeature) {
                addAll(dslInfo.gatherProguardFiles(ProguardFileType.EXPLICIT))
            }
        }
    }

    override val ignoredLibraryKeepRules: Provider<Set<String>> =
        baseModuleMetadata?.map { it.ignoredLibraryKeepRules }
            ?: internalServices.setPropertyOf(
                String::class.java,
                dslInfo.ignoredLibraryKeepRules
            )

    override val ignoreAllLibraryKeepRules: Boolean = dslInfo.ignoreAllLibraryKeepRules

    override val ignoreFromInBaselineProfile: Provider<Set<String>> =
        internalServices.setPropertyOf(
            String::class.java,
            dslInfo.ignoreFromInBaselineProfile
        )

    override val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean =
        dslInfo.ignoreFromAllExternalDependenciesInBaselineProfile

    override val postProcessingFeatures: PostprocessingFeatures?
        get() = dslInfo.postProcessingOptions.getPostprocessingFeatures()

    override val minifiedEnabled: Boolean
        get() {
            return if (component is AndroidTestCreationConfig) {
                val hasPostprocessingOptions =
                    dslInfo.postProcessingOptions.hasPostProcessingConfiguration()
                when {
                    // Enable minify for android test is not supported via the postProcessing block
                    component.mainVariant.componentType.isAar ->
                        !hasPostprocessingOptions &&
                                dslInfo.postProcessingOptions.codeShrinkerEnabled()
                    !hasPostprocessingOptions ->
                        component.mainVariant.optimizationCreationConfig.minifiedEnabled
                    else -> dslInfo.postProcessingOptions.codeShrinkerEnabled()
                }
            } else {
                minifyCodeBuilder?.isMinifyEnabled ?: false
            }
        }

    override val resourcesShrink: Boolean
        get() {
            return when (component) {
                is AndroidTestCreationConfig -> {
                    when {
                        component.mainVariant.componentType.isAar -> false
                        !dslInfo.postProcessingOptions.hasPostProcessingConfiguration() ->
                            component.mainVariant.optimizationCreationConfig.resourcesShrink

                        else -> dslInfo.postProcessingOptions.resourcesShrinkingEnabled()
                    }
                }

                // need to return shrink flag for PostProcessing as this API has the flag for
                // libraries return false otherwise
                is LibraryCreationConfig -> {
                    dslInfo.postProcessingOptions.let {
                        it.hasPostProcessingConfiguration() && it.resourcesShrinkingEnabled()
                    }
                }

                else -> {
                    minifyAndroidResourcesBuilder?.shrinkResources ?: false
                }
            }
        }
}
