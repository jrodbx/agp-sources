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

package com.android.build.gradle.internal.cxx.model
import com.android.build.gradle.internal.cxx.settings.Macro


/**
 * Lighter-weight reflection over the CXX model rooted at [CxxAbiModel]. The reason for not using
 * Kotlin reflection is in b/233948259.
 *
 * This enum is a flattened list representing various fields in the model. Fields may either be
 * actual values in the model or extension properties.
 *
 * The naming pattern is like this:
 * CXX_ABI_MODEL_IS_64_BITS represents CxxAbiModel::is64Bits
 * ^             ^
 * |             +----- is64Bits
 * +------------------- CxxAbiModel
 *
 * There's no need to maintain sorted order.
 */
enum class ModelField {
    CXX_ABI_MODEL_ABI_PLATFORM_VERSION,
    CXX_ABI_MODEL_ALT_CPU_ARCHITECTURE,
    CXX_ABI_MODEL_BITNESS,
    CXX_ABI_MODEL_CONFIGURATION_ARGUMENTS,
    CXX_ABI_MODEL_CONFIGURATION_HASH,
    CXX_ABI_MODEL_CPU_ARCHITECTURE,
    CXX_ABI_MODEL_CXX_BUILD_FOLDER,
    CXX_ABI_MODEL_FULL_CONFIGURATION_HASH,
    CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER,
    CXX_ABI_MODEL_IS_64_BITS,
    CXX_ABI_MODEL_IS_DEFAULT,
    CXX_ABI_MODEL_IS_DEPRECATED,
    CXX_ABI_MODEL_NINJA_BUILD_FILE,
    CXX_ABI_MODEL_NINJA_BUILD_LOCATION_FILE,
    CXX_ABI_MODEL_PLATFORM,
    CXX_ABI_MODEL_PLATFORM_CODE,
    CXX_ABI_MODEL_PREFAB_FOLDER,
    CXX_ABI_MODEL_SO_FOLDER,
    CXX_ABI_MODEL_SO_REPUBLISH_FOLDER,
    CXX_ABI_MODEL_STL_LIBRARY_FILE,
    CXX_ABI_MODEL_TAG,
    CXX_CMAKE_ABI_MODEL_BUILD_COMMAND_ARGS,
    CXX_CMAKE_MODULE_MODEL_CMAKE_EXE,
    CXX_MODULE_MODEL_CMAKE_GENERATOR,
    CXX_MODULE_MODEL_CMAKE_SETTINGS_FILE,
    CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE,
    CXX_MODULE_MODEL_CONFIGURE_SCRIPT,
    CXX_MODULE_MODEL_CXX_FOLDER,
    CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION,
    CXX_MODULE_MODEL_INTERMEDIATES_BASE_FOLDER,
    CXX_MODULE_MODEL_INTERMEDIATES_FOLDER,
    CXX_MODULE_MODEL_MAKE_FILE,
    CXX_MODULE_MODEL_MAKE_FILE_FOLDER,
    CXX_MODULE_MODEL_MODULE_NAME,
    CXX_MODULE_MODEL_MODULE_ROOT_FOLDER,
    CXX_MODULE_MODEL_NDK_FOLDER,
    CXX_MODULE_MODEL_NDK_MAJOR_VERSION,
    CXX_MODULE_MODEL_NDK_MAX_PLATFORM,
    CXX_MODULE_MODEL_NDK_MINOR_VERSION,
    CXX_MODULE_MODEL_NDK_MIN_PLATFORM,
    CXX_MODULE_MODEL_NDK_VERSION,
    CXX_MODULE_MODEL_NINJA_EXE,
    CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER,
    CXX_PROJECT_MODEL_SDK_FOLDER,
    CXX_VARIANT_MODEL_CPP_FLAGS,
    CXX_VARIANT_MODEL_C_FLAGS,
    CXX_VARIANT_MODEL_OPTIMIZATION_TAG,
    CXX_VARIANT_MODEL_STL_TYPE,
    CXX_VARIANT_MODEL_VARIANT_NAME,
    CXX_VARIANT_MODEL_VERBOSE_MAKEFILE,
    ENVIRONMENT_VARIABLE_NAME,
    ENVIRONMENT_VARIABLE_VALUE,
}


/**
 * Lookup up a [ModelField] value from this [CxxAbiModel]. Value is converted to [String].
 * Not all values in [ModelField] have an equivalent here. We only need the ones that are
 * consumed by [Macro].
 * The others defined in [ModelField] are still needed for the rewriting logic in
 * CxxAbiModelSettingsRewriter.kt.
 */
fun CxxAbiModel.lookup(field : ModelField) : String? {
    return when(field) {
        ModelField.CXX_ABI_MODEL_ABI_PLATFORM_VERSION -> abiPlatformVersion.toString()
        ModelField.CXX_ABI_MODEL_ALT_CPU_ARCHITECTURE -> altCpuArchitecture
        ModelField.CXX_ABI_MODEL_BITNESS -> bitness.toString()
        ModelField.CXX_ABI_MODEL_CONFIGURATION_HASH -> configurationHash
        ModelField.CXX_ABI_MODEL_CPU_ARCHITECTURE -> cpuArchitecture
        ModelField.CXX_ABI_MODEL_CXX_BUILD_FOLDER -> cxxBuildFolder.toString()
        ModelField.CXX_ABI_MODEL_FULL_CONFIGURATION_HASH -> fullConfigurationHash
        ModelField.CXX_ABI_MODEL_INTERMEDIATES_PARENT_FOLDER -> intermediatesParentFolder.toString()
        ModelField.CXX_ABI_MODEL_IS_64_BITS -> is64Bits
        ModelField.CXX_ABI_MODEL_IS_DEFAULT -> isDefault
        ModelField.CXX_ABI_MODEL_IS_DEPRECATED -> isDeprecated
        ModelField.CXX_ABI_MODEL_NINJA_BUILD_FILE -> ninjaBuildFile.toString()
        ModelField.CXX_ABI_MODEL_NINJA_BUILD_LOCATION_FILE -> ninjaBuildLocationFile.toString()
        ModelField.CXX_ABI_MODEL_PLATFORM -> platform
        ModelField.CXX_ABI_MODEL_PLATFORM_CODE -> platformCode
        ModelField.CXX_ABI_MODEL_PREFAB_FOLDER -> prefabFolder.toString()
        ModelField.CXX_ABI_MODEL_SO_FOLDER -> soFolder.toString()
        ModelField.CXX_ABI_MODEL_SO_REPUBLISH_FOLDER -> soRepublishFolder.toString()
        ModelField.CXX_ABI_MODEL_STL_LIBRARY_FILE -> stlLibraryFile?.toString()
        ModelField.CXX_ABI_MODEL_TAG -> name
        ModelField.CXX_CMAKE_MODULE_MODEL_CMAKE_EXE -> variant.module.cmake?.cmakeExe?.toString()
        ModelField.CXX_MODULE_MODEL_CMAKE_GENERATOR -> variant.module.cmakeGenerator
        ModelField.CXX_MODULE_MODEL_CMAKE_SETTINGS_FILE -> variant.module.cmakeSettingsFile.toString()
        ModelField.CXX_MODULE_MODEL_CMAKE_TOOLCHAIN_FILE -> variant.module.cmakeToolchainFile.toString()
        ModelField.CXX_MODULE_MODEL_CXX_FOLDER -> variant.module.cxxFolder.toString()
        ModelField.CXX_MODULE_MODEL_HAS_BUILD_TIME_INFORMATION -> variant.module.hasBuildTimeInformation.toString()
        ModelField.CXX_MODULE_MODEL_INTERMEDIATES_BASE_FOLDER -> variant.module.intermediatesBaseFolder.toString()
        ModelField.CXX_MODULE_MODEL_INTERMEDIATES_FOLDER -> variant.module.intermediatesFolder.toString()
        ModelField.CXX_MODULE_MODEL_MAKE_FILE -> variant.module.makeFile.toString()
        ModelField.CXX_MODULE_MODEL_MAKE_FILE_FOLDER -> variant.module.makeFileFolder.toString()
        ModelField.CXX_MODULE_MODEL_MODULE_NAME -> variant.module.moduleName
        ModelField.CXX_MODULE_MODEL_MODULE_ROOT_FOLDER -> variant.module.moduleRootFolder.toString()
        ModelField.CXX_MODULE_MODEL_NDK_FOLDER -> variant.module.ndkFolder.toString()
        ModelField.CXX_MODULE_MODEL_NDK_MAJOR_VERSION -> variant.module.ndkMajorVersion
        ModelField.CXX_MODULE_MODEL_NDK_MAX_PLATFORM -> variant.module.ndkMaxPlatform
        ModelField.CXX_MODULE_MODEL_NDK_MINOR_VERSION -> variant.module.ndkMinorVersion
        ModelField.CXX_MODULE_MODEL_NDK_MIN_PLATFORM -> variant.module.ndkMinPlatform
        ModelField.CXX_MODULE_MODEL_NDK_VERSION -> variant.module.ndkVersion.toString()
        ModelField.CXX_MODULE_MODEL_NINJA_EXE -> variant.module.ninjaExe?.toString()
        ModelField.CXX_PROJECT_MODEL_ROOT_BUILD_GRADLE_FOLDER -> variant.module.project.rootBuildGradleFolder.toString()
        ModelField.CXX_PROJECT_MODEL_SDK_FOLDER -> variant.module.project.sdkFolder.toString()
        ModelField.CXX_VARIANT_MODEL_CPP_FLAGS -> variant.cppFlags
        ModelField.CXX_VARIANT_MODEL_C_FLAGS -> variant.cFlags
        ModelField.CXX_VARIANT_MODEL_OPTIMIZATION_TAG -> variant.optimizationTag
        ModelField.CXX_VARIANT_MODEL_STL_TYPE -> variant.stlType
        ModelField.CXX_VARIANT_MODEL_VARIANT_NAME -> variant.variantName
        ModelField.CXX_VARIANT_MODEL_VERBOSE_MAKEFILE -> variant.verboseMakefile?.toString()
        else -> error(field)
    }
}


