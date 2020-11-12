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
import com.android.build.gradle.internal.cxx.gradle.generator.*
import com.android.build.gradle.internal.cxx.logging.ThreadLoggingEnvironment.Companion.requireExplicitLogger
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils.join
import java.io.File

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
                        module.splitsAbiFilterSet,
                        module.project.isBuildOnlyTargetAbiEnabled,
                        module.project.ideBuildTargetAbi
                )
        ).validAbis.toList()
    }
    val variantIntermediatesFolder = join(
            configurationParameters.intermediatesFolder,
            configurationParameters.buildSystem.tag,
            configurationParameters.variantName
    )
    return CxxVariantModel(
        buildTargetSet = configurationParameters.nativeVariantConfig.targets,
        implicitBuildTargetSet = configurationParameters.implicitBuildTargetSet,
        module = module,
        buildSystemArgumentList = configurationParameters.nativeVariantConfig.arguments,
        cFlagsList = configurationParameters.nativeVariantConfig.cFlags,
        cppFlagsList = configurationParameters.nativeVariantConfig.cppFlags,
        variantName = configurationParameters.variantName,
        // TODO remove this after configuration has been added to DSL
        // If CMakeSettings.json has a configuration with this exact name then
        // it will be used. The point is to delay adding 'configuration' to the
        // DSL.
        cmakeSettingsConfiguration = "android-gradle-plugin-predetermined-name",
        objFolder = if (configurationParameters.buildSystem == NativeBuildSystem.NDK_BUILD) {
            // ndkPlatform-build create libraries in a "local" subfolder.
            join(variantIntermediatesFolder, "obj", "local")
        } else {
            join(variantIntermediatesFolder, "obj")
        },
        soFolder = join(variantIntermediatesFolder, "lib"),
        isDebuggableEnabled = configurationParameters.isDebuggable,
        validAbiList = validAbiList,
        prefabClassPathFileCollection = configurationParameters.prefabClassPath,
        prefabPackageDirectoryListFileCollection = configurationParameters.prefabPackageDirectoryList,
        prefabDirectory = join(
            configurationParameters.cxxFolder,
            configurationParameters.buildSystem.tag,
            configurationParameters.variantName,
            "prefab")
    )
}

val CxxVariantModel.prefabClassPath : File?
    get() = prefabClassPathFileCollection?.singleFile

val CxxVariantModel.prefabPackageDirectoryList : List<File>
    get() = prefabPackageDirectoryListFileCollection?.toList()?:listOf()

/**
 * The gradle build output folder
 *   ex, '$moduleRootFolder/.cxx/cxx/debug'
 */
val CxxVariantModel.gradleBuildOutputFolder
        get() = join(module.cxxFolder, "cxx", variantName)

