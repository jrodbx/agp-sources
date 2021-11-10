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
import java.io.File
import java.util.Locale

/**
 * Construct a [CxxVariantModel]
 */
fun createCxxVariantModel(
    configurationParameters: CxxConfigurationParameters,
    module: CxxModuleModel) : CxxVariantModel {
    val validAbiList = CachingEnvironment(configurationParameters.cxxCacheFolder).use {
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
        val isDebuggable = configurationParameters.isDebuggable
        val variantName = configurationParameters.variantName

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
                prefabClassPathFileCollection = configurationParameters.prefabClassPath,
                prefabPackageDirectoryListFileCollection = configurationParameters.prefabPackageDirectoryList,
                stlType = determineUsedStl(arguments).argumentName,
                verboseMakefile = null,
                optimizationTag = run {
                    /**
                     * Choose the optimization level to use when the user hasn't specified one
                     * in build.gradle arguments.
                     *
                     * If possible, the name of the variant is used directly. For example, if it
                     * contains "debug" then the optimization level is Debug and so on.
                     *
                     * There is one caveat that variants containing "release" result in
                     * RelWithDebInfo rather than Release. The reason is that, in CMake, Release
                     * means that no -g flag is passed to the C++ toolchain and so no symbols would
                     * be generated. We want to keep those symbols for debug-ability. Symbols are
                     * stripped by AGP before packaging into the APK so RelWithDebInfo is
                     * equivalent to Release for packaging purposes.
                     *
                     * If the user truly wants Release then they can use
                     * -DCMAKE_RELEASE_TYPE=Release in build.gradle to override the default chosen
                     * here.
                     */
                    val lower = variantName.toLowerCase(Locale.ROOT)
                    when {
                        lower.endsWith("release") -> "RelWithDebInfo"
                        lower.endsWith("debug") -> "Debug"
                        lower.endsWith("relwithdebinfo") -> "RelWithDebInfo"
                        lower.endsWith("minsizerel") -> "MinSizeRel"
                        lower.contains("release") -> "RelWithDebInfo"
                        lower.contains("debug") -> "Debug"
                        lower.contains("relwithdebinfo") -> "RelWithDebInfo"
                        lower.contains("minsizerel") -> "MinSizeRel"
                        else ->
                            if (isDebuggable) {
                                "Debug"
                            } else {
                                "RelWithDebInfo"
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


