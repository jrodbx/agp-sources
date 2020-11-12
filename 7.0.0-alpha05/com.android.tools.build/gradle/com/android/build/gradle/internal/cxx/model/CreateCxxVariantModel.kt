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
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.utils.FileUtils.join
import java.io.File
import java.util.Locale

/**
 * Construct a [CxxVariantModel]
 */
fun createCxxVariantModel(
    configurationParameters: CxxConfigurationParameters,
    module: CxxModuleModel) : CxxVariantModel {
    val validAbiList = CachingEnvironment(module.cxxFolder).use {
        AbiConfigurator(
                AbiConfigurationKey(
                        module.ndkSupportedAbiList,
                        module.ndkDefaultAbiList,
                        configurationParameters.nativeVariantConfig.externalNativeBuildAbiFilters,
                        configurationParameters.nativeVariantConfig.ndkAbiFilters,
                        configurationParameters.splitsAbiFilterSet,
                        module.project.isBuildOnlyTargetAbiEnabled,
                        module.project.ideBuildTargetAbi
                )
        ).validAbis.toList()
    }
    with(module) {
        val arguments = configurationParameters.nativeVariantConfig.arguments
        val build = ifCMake { "cmake" } ?: "ndkBuild"
        val isDebuggable = configurationParameters.isDebuggable
        val variantName = configurationParameters.variantName
        val intermediates = join(intermediatesFolder, build, variantName)
        val intermediatesBase = join(intermediatesBaseFolder, build, variantName)

        return CxxVariantModel(
                buildTargetSet = configurationParameters.nativeVariantConfig.targets,
                implicitBuildTargetSet = configurationParameters.implicitBuildTargetSet,
                module = this,
                buildSystemArgumentList = arguments,
                cFlagsList = configurationParameters.nativeVariantConfig.cFlags,
                cppFlagsList = configurationParameters.nativeVariantConfig.cppFlags,
                variantName = variantName,
                // TODO remove this after configuration has been added to DSL
                // If CMakeSettings.json has a configuration with this exact name then
                // it will be used. The point is to delay adding 'configuration' to the
                // DSL.
                cmakeSettingsConfiguration = "android-gradle-plugin-predetermined-name",
                isDebuggableEnabled = isDebuggable,
                validAbiList = validAbiList,
                cxxBuildFolder = join(cxxFolder, build, variantName),
                prefabClassPathFileCollection = configurationParameters.prefabClassPath,
                prefabPackageDirectoryListFileCollection = configurationParameters.prefabPackageDirectoryList,
                intermediatesFolder = intermediates,
                soFolder = join(intermediates, ifCMake { "obj" } ?: "obj/local"),
                soRepublishFolder = join(intermediatesBase, ifCMake { "obj" } ?: "obj/local"),
                stlType = determineUsedStl(arguments).argumentName,
                optimizationTag = run {
                    val lower = variantName.toLowerCase(Locale.ROOT)

                    when {
                        lower.endsWith("release") -> "Release"
                        lower.endsWith("debug") -> "Debug"
                        lower.endsWith("relwithdebinfo") -> "RelWithDebInfo"
                        lower.endsWith("minsizerel") -> "MinSizeRel"
                        lower.contains("release") -> "Release"
                        lower.contains("debug") -> "Debug"
                        lower.contains("relwithdebinfo") -> "RelWithDebInfo"
                        lower.contains("minsizerel") -> "MinSizeRel"
                        else ->
                            if (isDebuggable) {
                                "Debug"
                            } else {
                                "Release"
                            }
                    }
                }
        )
    }
}

val CxxVariantModel.prefabClassPath : File?
    get() = prefabClassPathFileCollection?.singleFile

val CxxVariantModel.prefabPackageDirectoryList : List<File>
    get() = prefabPackageDirectoryListFileCollection?.toList()?:listOf()


