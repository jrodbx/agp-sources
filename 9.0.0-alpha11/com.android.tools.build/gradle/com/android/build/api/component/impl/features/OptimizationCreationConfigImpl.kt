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
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.features.OptimizationCreationConfig
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.tasks.ModuleMetadata
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import java.io.File
import kotlin.collections.forEach

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
                val testProguardFiles = mutableListOf< RegularFile>()
                dslInfo.gatherProguardFiles(ProguardFileType.TEST, testProguardFiles)
                it.addAll(testProguardFiles)
            } else {
                dslInfo.getProguardFiles(it)
            }
        }
    }

    override val consumerProguardFiles: ListProperty<RegularFile> by lazy(LazyThreadSafetyMode.NONE) {
        val consumerProguardFilePaths: List<File> = consumerProguardFilePaths
        val consumerProguardFilesProperty: ListProperty<RegularFile> =
            internalServices.listPropertyOf(RegularFile::class.java) { list ->
                consumerProguardFilePaths.forEach {
                    list.add(internalServices.toRegularFileProvider(it))
                }
            }

        val isBaseModule = component.componentType.isBaseModule
        val isDynamicFeature = component.componentType.isDynamicFeature

        val buildDirectory = internalServices.projectInfo.buildDirectory
        val defaultProguardFiles: Map<File, String> = ProguardFiles.KNOWN_FILE_NAMES.associateBy {
            ProguardFiles.getDefaultProguardFile(it, buildDirectory)
        }

        // check for the default files location and potentially issue an error
        if (!isBaseModule) {
            consumerProguardFilePaths.forEach {
                defaultProguardFiles[it]?.let { fileName ->
                    val errorMessage = if (isDynamicFeature) {
                        "Default file $fileName should not be specified in this module. It can be specified in the base module instead."
                    } else {
                        "Default file $fileName should not be used as a consumer configuration file."
                    }

                    internalServices.issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        errorMessage
                    )
                }
            }
        }

        // If default Proguard files are used, we need to attach task dependencies (see b/295666695)
        val result: Provider<List<RegularFile>> = if (consumerProguardFilePaths.any { it in defaultProguardFiles }) {
            component.global.globalArtifacts.get(InternalArtifactType.DEFAULT_PROGUARD_FILES)
                .zip(consumerProguardFilesProperty) { _, right -> right }
        } else {
            consumerProguardFilesProperty
        }
        internalServices.listPropertyOf(RegularFile::class.java) {
            it.set(result)
        }
    }

    private val consumerProguardFilePaths: List<File> by lazy(LazyThreadSafetyMode.NONE) {
        val consumerProguardFiles = mutableListOf<RegularFile>()
        dslInfo.gatherProguardFiles(ProguardFileType.CONSUMER, consumerProguardFiles)
        if (component.componentType.isDynamicFeature) {
            dslInfo.gatherProguardFiles(ProguardFileType.EXPLICIT, consumerProguardFiles)
        }
        consumerProguardFiles.map { it.asFile }
    }

    override val ignoreFromInKeepRules: Provider<List<String>> =
        baseModuleMetadata?.map { it.ignoreFromInKeepRules }
            ?: internalServices.listPropertyOf(
                String::class.java,
                dslInfo.ignoreFromInKeepRules
            )

    override val ignoreFromAllExternalDependenciesInKeepRules: Boolean =
        dslInfo.ignoreFromAllExternalDependenciesInKeepRules

    override val ignoreFromInBaselineProfile: Provider<Set<String>> =
        internalServices.setPropertyOf(
            String::class.java,
            dslInfo.ignoreFromInBaselineProfile
        )

    override val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean =
        dslInfo.ignoreFromAllExternalDependenciesInBaselineProfile

    override val minifiedEnabled: Boolean
        get() {
            val minify = minifyCodeBuilder?.isMinifyEnabled ?: false
            return if (component is DeviceTestCreationConfig) {
                when {
                    component.mainVariant.componentType.isAar ->
                                dslInfo.postProcessingOptions.codeShrinkerEnabled()
                    else -> component.mainVariant.optimizationCreationConfig.minifiedEnabled
                }
            } else if (component is ApplicationCreationConfig) {
                minify || dslInfo.applicationOptimizationEnabled
            } else {
                minify
            }
        }

    override val resourcesShrink: Boolean
        get() {
            val minify = minifyAndroidResourcesBuilder?.shrinkResources ?: false
            return when (component) {
                is DeviceTestCreationConfig -> {
                    when {
                        component.mainVariant.componentType.isAar -> false
                        else -> component.mainVariant.optimizationCreationConfig.resourcesShrink
                    }
                }

                is LibraryCreationConfig -> {
                    dslInfo.postProcessingOptions.let {
                        it.resourcesShrinkingEnabled()
                    }
                }

                is ApplicationCreationConfig -> {
                    minify || dslInfo.applicationOptimizationEnabled
                }
                else -> minify
            }
        }

    override val applicationOptimizationEnabled: Boolean
        get() = dslInfo.applicationOptimizationEnabled

    override val includePackages: Provider<Set<String>> =
        internalServices.setPropertyOf(
            String::class.java,
            dslInfo.includePackages
        )
}
