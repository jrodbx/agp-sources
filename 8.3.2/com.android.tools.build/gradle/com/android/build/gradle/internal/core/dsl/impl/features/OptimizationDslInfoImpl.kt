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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.MergedOptimization
import com.android.build.gradle.internal.core.PostProcessingBlockOptions
import com.android.build.gradle.internal.core.PostProcessingOptions
import com.android.build.gradle.internal.core.dsl.features.OptimizationDslInfo
import com.android.build.gradle.internal.core.dsl.impl.computeMergedOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.OptimizationImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.builder.core.ComponentType
import com.android.builder.model.BaseConfig
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import java.io.File

class OptimizationDslInfoImpl(
    private val componentType: ComponentType,
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
    private val services: VariantServices,
    private val buildDirectory: DirectoryProperty,
): OptimizationDslInfo {

    private val mergedOptimization = MergedOptimization()

    init {
        mergeOptions()
    }

    private fun mergeOptions() {
        computeMergedOptions(
            defaultConfig,
            buildTypeObj,
            productFlavorList,
            mergedOptimization,
            { optimization as OptimizationImpl },
            { optimization as OptimizationImpl }
        )
    }

    override val ignoredLibraryKeepRules: Set<String>
        get() = mergedOptimization.ignoredLibraryKeepRules

    override val ignoreAllLibraryKeepRules: Boolean
        get() = mergedOptimization.ignoreAllLibraryKeepRules

    override val ignoreFromInBaselineProfile: Set<String>
        get() = mergedOptimization.ignoreFromInBaselineProfile

    override val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean
        get() = mergedOptimization.ignoreFromAllExternalDependenciesInBaselineProfile

    override fun getProguardFiles(into: ListProperty<RegularFile>) {
        val result: MutableList<File> = ArrayList(gatherProguardFiles(ProguardFileType.EXPLICIT))
        if (result.isEmpty()) {
            result.addAll(postProcessingOptions.getDefaultProguardFiles())
        }

        val projectDir = services.projectInfo.projectDirectory
        result.forEach { file ->
            into.add(projectDir.file(file.absolutePath))
        }
    }

    override val postProcessingOptions: PostProcessingOptions by lazy {
        if ((buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).postProcessingBlockUsed) {
            PostProcessingBlockOptions(
                buildTypeObj._postProcessing, componentType.isTestComponent,
            )
        } else object : PostProcessingOptions {
            override fun getProguardFiles(type: ProguardFileType): Collection<File> =
                buildTypeObj.getProguardFiles(type)

            override fun getDefaultProguardFiles(): List<File> =
                listOf(
                    ProguardFiles.getDefaultProguardFile(
                        ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName,
                        buildDirectory
                    )
                )

            override fun getPostprocessingFeatures(): PostprocessingFeatures? = null

            override fun codeShrinkerEnabled(): Boolean {
                if (componentType.isTestComponent && buildTypeObj is LibraryBuildType) {
                    return buildTypeObj.androidTest.enableMinification
                } else {
                    return buildTypeObj.isMinifyEnabled
                }
            }

            override fun resourcesShrinkingEnabled(): Boolean = buildTypeObj.isShrinkResources

            override fun hasPostProcessingConfiguration() = false
        }
    }

    override fun gatherProguardFiles(type: ProguardFileType): Collection<File> {
        val result: MutableList<File> = ArrayList(defaultConfig.getProguardFiles(type))
        for (flavor in productFlavorList) {
            result.addAll((flavor as com.android.build.gradle.internal.dsl.ProductFlavor).getProguardFiles(type))
        }
        result.addAll(postProcessingOptions.getProguardFiles(type))
        return result
    }

    private fun BaseConfig.getProguardFiles(type: ProguardFileType): Collection<File> = when (type) {
        ProguardFileType.EXPLICIT -> this.proguardFiles
        ProguardFileType.TEST -> this.testProguardFiles
        ProguardFileType.CONSUMER -> this.consumerProguardFiles
    }
}
