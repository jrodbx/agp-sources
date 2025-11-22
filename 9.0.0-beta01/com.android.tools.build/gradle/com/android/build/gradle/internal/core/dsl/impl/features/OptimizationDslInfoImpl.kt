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

import com.android.build.api.component.impl.features.CommonOptimizationDslInfoImpl
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.core.MergedOptimization
import com.android.build.gradle.internal.core.PostProcessingOptions
import com.android.build.gradle.internal.core.dsl.impl.computeMergedOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.OptimizationImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentType
import com.android.builder.model.BaseConfig
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import java.io.File
import com.android.builder.core.ComponentTypeImpl.BASE_APK

class OptimizationDslInfoImpl(
    private val componentType: ComponentType,
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
    private val services: VariantServices,
    private val buildDirectory: DirectoryProperty,
): CommonOptimizationDslInfoImpl(services) {

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

    override val ignoreFromInKeepRules: Set<String>
        get() = mergedOptimization.ignoreFromInKeepRules

    override val ignoreFromAllExternalDependenciesInKeepRules: Boolean
        get() = mergedOptimization.ignoreFromAllExternalDependenciesInKeepRules

    override val ignoreFromInBaselineProfile: Set<String>
        get() = mergedOptimization.ignoreFromInBaselineProfile

    override val ignoreFromAllExternalDependenciesInBaselineProfile: Boolean
        get() = mergedOptimization.ignoreFromAllExternalDependenciesInBaselineProfile

    override val applicationOptimizationEnabled: Boolean
        get() = mergedOptimization.enable && componentType == BASE_APK

    override val includePackages: Set<String>
        get() = if (componentType == BASE_APK) mergedOptimization.packageScope else setOf()

    override val keepRuleFiles: Set<File>
        get() = mergedOptimization.keepRuleFiles

    override val postProcessingOptions: PostProcessingOptions by lazy {
        object : PostProcessingOptions {
            override fun getProguardFiles(type: ProguardFileType): Collection<File> =
                (buildTypeObj as com.android.build.gradle.internal.dsl.BuildType).getProguardFiles(type)

            override fun getDefaultProguardFiles(): List<File> =
                listOf(
                    ProguardFiles.getDefaultProguardFile(
                        if (services.projectOptions[BooleanOption.R8_PROGUARD_ANDROID_TXT_DISALLOWED]) {
                            ProguardFiles.ProguardFile.OPTIMIZE
                        } else {
                            ProguardFiles.ProguardFile.DONT_OPTIMIZE
                        }.fileName,
                        buildDirectory
                    )
                )

            override fun codeShrinkerEnabled(): Boolean {
                if (componentType.isTestComponent && buildTypeObj is LibraryBuildType) {
                    return buildTypeObj.androidTest.enableMinification
                } else {
                    return buildTypeObj.isMinifyEnabled
                }
            }

            override fun resourcesShrinkingEnabled(): Boolean = buildTypeObj.isShrinkResources
        }
    }

    override fun gatherProguardFiles(
        type: ProguardFileType,
        into: MutableList<RegularFile>
    ) {
        val projectDir = services.projectInfo.projectDirectory
        fun addToList(itemsToAdd: Collection<File>) {
            into.addAll(itemsToAdd.map { projectDir.file(it.path)})
        }

        addToList(defaultConfig.getProguardFiles(type))
        for (flavor in productFlavorList) {
            addToList((flavor as com.android.build.gradle.internal.dsl.ProductFlavor).getProguardFiles(type))
        }
        addToList(postProcessingOptions.getProguardFiles(type))
        if (type == ProguardFileType.EXPLICIT) addToList(keepRuleFiles)
    }

    private fun BaseConfig.getProguardFiles(type: ProguardFileType): Collection<File> = when (type) {
        ProguardFileType.EXPLICIT -> this.proguardFiles
        ProguardFileType.TEST -> this.testProguardFiles
        ProguardFileType.CONSUMER -> this.consumerProguardFiles
    }
}
