/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.cxx.settings
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.cmake.cmakeBoolean
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_EXPORT_COMPILE_COMMANDS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_FIND_ROOT_PATH
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_SYSTEM_NAME
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.NDK_DEBUG
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.getNdkBuildProperty
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.buildIsPrefabCapable
import com.android.build.gradle.internal.cxx.model.buildSystemTag
import com.android.build.gradle.internal.cxx.model.determineUsedStlFromArguments
import com.android.build.gradle.internal.cxx.model.intermediatesParentDirSuffix
import com.android.build.gradle.internal.cxx.settings.Environment.GRADLE
import com.android.build.gradle.internal.cxx.settings.Environment.MICROSOFT_BUILT_IN
import com.android.build.gradle.internal.cxx.settings.Environment.NDK
import com.android.build.gradle.internal.cxx.settings.Environment.NDK_EXPOSED_BY_HOST
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_BITNESS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_64_BITS
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_DEFAULT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI_IS_DEPRECATED
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CMAKE_TOOLCHAIN
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_INTERMEDIATES_PARENT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MAX_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MIN_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_BUILD_INTERMEDIATES_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_BUILD_INTERMEDIATES_BASE_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_BUILD_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_EXECUTABLE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_GENERATOR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM_CODE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM_SYSTEM_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PREFAB_PATH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_SO_OUTPUT_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_SO_REPUBLISH_DIR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_STL_LIBRARY_FILE
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_NAME
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_OPTIMIZATION_TAG
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_VARIANT_STL_TYPE
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.CUSTOM
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.utils.FileUtils.join

const val TRADITIONAL_CONFIGURATION_NAME = "traditional-android-studio-cmake-environment"

/**
 * This is a CMakeSettings.json file that is equivalent to the environment CMakeServerJsonGenerator
 * traditionally has run.
 */
fun getCmakeDefaultEnvironment(buildIsPrefabCapable: Boolean): Settings {
    val variables = mutableListOf(
            SettingsConfigurationVariable(CMAKE_SYSTEM_NAME.name, "Android"),
            SettingsConfigurationVariable(CMAKE_EXPORT_COMPILE_COMMANDS.name, "ON")
    )
    variables.addAll(Macro.values().toList().flatMap { macro ->
            macro.cmakeProperties.map { cmake ->
                SettingsConfigurationVariable(cmake.name, macro.ref)
            }
        })
    if (buildIsPrefabCapable) {
        // This can be passed a few different ways:
        // https://cmake.org/cmake/help/latest/command/find_package.html#search-procedure
        //
        // <PACKAGE_NAME>_ROOT would probably be best, but it's not supported until 3.12, and we support
        // CMake 3.6.
        variables.add(SettingsConfigurationVariable(CMAKE_FIND_ROOT_PATH.name, join(NDK_PREFAB_PATH.ref, "prefab")))
    }
    return Settings(
            configurations = listOf(
                    SettingsConfiguration(
                            name = TRADITIONAL_CONFIGURATION_NAME,
                            description = "Configuration generated by Android Gradle Plugin",
                            inheritEnvironments = listOf("ndk"),
                            generator = NDK_MODULE_CMAKE_GENERATOR.ref,
                            buildRoot = NDK_BUILD_ROOT.ref,
                            cmakeExecutable = NDK_MODULE_CMAKE_EXECUTABLE.ref,
                            cmakeToolchain = NDK_CMAKE_TOOLCHAIN.ref,
                            configurationType = NDK_VARIANT_OPTIMIZATION_TAG.ref,
                            variables = variables
                    )
            )
    )
}

/**
 * A placeholder environment for ndk-build. It doesn't do anything except declare the name of
 * the inheritted environment "ndk"
 */
fun getNdkBuildDefaultEnvironment(): Settings {
    return Settings(
            configurations = listOf(
                    SettingsConfiguration(
                            name = TRADITIONAL_CONFIGURATION_NAME,
                            description = "Configuration generated by Android Gradle Plugin",
                            inheritEnvironments = listOf("ndk"),
                    )
            )
    )
}

/**
 * Information that would naturally come from the NDK.
 */
fun CxxAbiModel.getNdkMetaSettingsJson() : Settings {
    fun lookup(macro: Macro) = macro to resolveMacroValue(macro)
    val environments = mutableMapOf<String, NameTable>()
    environments[NDK.environment] = NameTable(
            lookup(NDK_MIN_PLATFORM),
            lookup(NDK_MAX_PLATFORM),
            lookup(NDK_CMAKE_TOOLCHAIN))
    // Per-ABI environments
    for(abiValue in Abi.values()) {
        val abiInfo = variant.module.ndkMetaAbiList.singleOrNull { it.abi == abiValue } ?: continue
        environments[Environment.NDK_ABI.environment.replace(NDK_ABI.ref, abiValue.tag)] = NameTable(
                NDK_ABI_BITNESS to abiInfo.bitness.toString(),
                NDK_ABI_IS_64_BITS to cmakeBoolean(abiInfo.bitness == 64),
                NDK_ABI_IS_DEPRECATED to cmakeBoolean(abiInfo.isDeprecated),
                NDK_ABI_IS_DEFAULT to cmakeBoolean(abiInfo.isDefault))
    }

    // Per-platform environments. In order to be lazy, promise future platform versions and return
    // blank for PLATFORM_CODE when they are evaluated and don't exist.
    val metaPlatformAliases = variant.module.ndkMetaPlatforms?.aliases?.toList()
    for(potentialPlatform in NdkMetaPlatforms.potentialPlatforms) {
        val environmentName =
                Environment.NDK_PLATFORM.environment.replace(NDK_PLATFORM_SYSTEM_VERSION.ref, potentialPlatform.toString())
        environments[environmentName] = NameTable(
                NDK_PLATFORM_SYSTEM_VERSION to "$potentialPlatform",
                NDK_PLATFORM to "android-$potentialPlatform",
                NDK_PLATFORM_CODE to (metaPlatformAliases?.lastOrNull { (_, platform) ->
                    platform == potentialPlatform
                }?.first ?: "")
        )
    }
    val settingsEnvironments =
            environments.map { (name, nameTable) ->
                nameTable.environments().single().copy(environment = name)
            }
    return Settings(
            environments = settingsEnvironments,
            configurations = listOf()
    )
}

/**
 * Builds the default android hosting environment.
 */
fun CxxAbiModel.getAndroidGradleSettings() : Settings {
    val nameTable = NameTable()
    nameTable.addAll(
            Macro.values()
                    .filter { it.environment == GRADLE ||
                              it.environment == MICROSOFT_BUILT_IN ||
                              it.environment == NDK_EXPOSED_BY_HOST
                    }
                    .map { macro -> macro to resolveMacroValue(macro) }
    )

    // This is the main switch point which defines the group of output folders used for
    // configuration and build.
    val legacyConfigurationSegment = join(variant.module.buildSystemTag, NDK_VARIANT_NAME.ref)
    val configurationSegment = join(NDK_VARIANT_OPTIMIZATION_TAG.ref, NDK_CONFIGURATION_HASH.ref)

    nameTable.addAll(
        NDK_INTERMEDIATES_PARENT_DIR to join(NDK_MODULE_BUILD_INTERMEDIATES_DIR.ref, configurationSegment),
        NDK_PREFAB_PATH to join(NDK_MODULE_BUILD_ROOT.ref, configurationSegment, "prefab", NDK_ABI.ref),
        NDK_BUILD_ROOT to join(NDK_MODULE_BUILD_ROOT.ref, configurationSegment, NDK_ABI.ref),
        NDK_SO_OUTPUT_DIR to join(NDK_INTERMEDIATES_PARENT_DIR.ref, variant.module.intermediatesParentDirSuffix, NDK_ABI.ref),
        NDK_SO_REPUBLISH_DIR to join(NDK_MODULE_BUILD_INTERMEDIATES_BASE_DIR.ref, legacyConfigurationSegment, variant.module.intermediatesParentDirSuffix, NDK_ABI.ref),
    )

    return Settings(
            environments = nameTable.environments(),
            configurations = listOf()
    )
}

/**
 * Builds an environment from CMake command-line arguments.
 */
fun CxxAbiModel.getSettingsFromCommandLine(arguments: List<CommandLineArgument>): Settings {
    val nameTable = NameTable()

    when(variant.module.buildSystem) {
        CMAKE -> arguments.forEach { argument ->
            when (argument) {
                is CmakeBinaryOutputPath -> nameTable[NDK_BUILD_ROOT] = argument.path
                is DefineProperty ->
                    Macro.withCMakeProperty(argument.propertyName).forEach { macro ->
                        nameTable[macro] = argument.propertyValue
                    }
                else -> {
                }
            }
        }
        NDK_BUILD -> nameTable.addAll(
            NDK_VARIANT_OPTIMIZATION_TAG to
                    when(arguments.getNdkBuildProperty(NDK_DEBUG)) {
                        "0" -> "Release"
                        else -> "Debug"
                    }
        )
        else -> error("${variant.module.buildSystem}")
    }

    val stl =  variant.module.determineUsedStlFromArguments(arguments)

    val stlLibraryFile =
            variant.module.stlSharedObjectMap.getValue(stl)[abi]?.toString() ?: ""

    nameTable.addAll(
        NDK_VARIANT_STL_TYPE to stl.argumentName,
        NDK_STL_LIBRARY_FILE to stlLibraryFile
    )

    return Settings(
            environments = nameTable.environments(),
            configurations = listOf()
    )
}

/**
 * Gather CMake settings from different locations.
 */
fun CxxAbiModel.gatherSettingsFromAllLocations() : Settings {
    val settings = mutableListOf<Settings>()

    when(variant.module.buildSystem) {
        CMAKE -> {
            // Load the user's CMakeSettings.json if there is one.
            val userSettings = join(variant.module.makeFile.parentFile, "CMakeSettings.json")
            if (userSettings.isFile) {
                settings += createSettingsFromJsonFile(userSettings)
            }
            // TODO this needs to include environment variables as well.
            // Add the synthetic traditional environment.
            settings += getCmakeDefaultEnvironment(buildIsPrefabCapable())
        }
        NDK_BUILD -> settings += getNdkBuildDefaultEnvironment()
        else -> error("${variant.module.buildSystem}")
    }

    // Construct settings for gradle hosting environment.
    settings += getAndroidGradleSettings()

    // Construct synthetic settings for the NDK
    settings += getNdkMetaSettingsJson()

    return mergeSettings(*settings.toTypedArray()).expandInheritEnvironmentMacros(this)
}
