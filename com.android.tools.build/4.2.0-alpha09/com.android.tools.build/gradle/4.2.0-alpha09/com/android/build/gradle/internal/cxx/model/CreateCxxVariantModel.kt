/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.AbiConfigurationKey
import com.android.build.gradle.internal.cxx.configure.AbiConfigurator
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.gradle.generator.variantJsonFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantObjFolder
import com.android.build.gradle.internal.cxx.gradle.generator.variantSoFolder
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Construct a [CxxVariantModel], careful to be lazy with module-level fields.
 */
fun createCxxVariantModel(
    configurationModel: CxxConfigurationModel,
    module: CxxModuleModel) : CxxVariantModel {

    return object : CxxVariantModel {
        override val buildTargetSet = configurationModel.nativeVariantConfig.targets
        override val implicitBuildTargetSet = configurationModel.implicitBuildTargetSet
        override val module = module
        override val buildSystemArgumentList = configurationModel.nativeVariantConfig.arguments
        override val cFlagsList = configurationModel.nativeVariantConfig.cFlags
        override val cppFlagsList = configurationModel.nativeVariantConfig.cppFlags
        override val variantName = configurationModel.variantName
        override val cmakeSettingsConfiguration
            // TODO remove this after configuration has been added to DSL
            // If CMakeSettings.json has a configuration with this exact name then
            // it will be used. The point is to delay adding 'configuration' to the
            // DSL.
            get() = "android-gradle-plugin-predetermined-name"
        override val objFolder by lazy { configurationModel.variantObjFolder }
        override val soFolder by lazy { configurationModel.variantSoFolder }
        override val isDebuggableEnabled = configurationModel.isDebuggable
        override val validAbiList by lazy {
            CachingEnvironment(module.cxxFolder).use {
                AbiConfigurator(
                    AbiConfigurationKey(
                        module.ndkSupportedAbiList,
                        module.ndkDefaultAbiList,
                        configurationModel.nativeVariantConfig.externalNativeBuildAbiFilters,
                        configurationModel.nativeVariantConfig.ndkAbiFilters,
                        module.splitsAbiFilterSet,
                        module.project.isBuildOnlyTargetAbiEnabled,
                        module.project.ideBuildTargetAbi
                    )
                ).validAbis.toList()
            }
        }

        override val prefabClassPath = configurationModel.prefabClassPath?.singleFile
        override val prefabPackageDirectoryList get() = configurationModel.prefabPackageDirectoryList?.toList()?:listOf()
        override val prefabDirectory: File = configurationModel.variantJsonFolder.resolve("prefab")
    }
}

/**
 * The gradle build output folder
 *   ex, '$moduleRootFolder/.cxx/cxx/debug'
 */
val CxxVariantModel.gradleBuildOutputFolder
        get() = join(module.cxxFolder, "cxx", variantName)

