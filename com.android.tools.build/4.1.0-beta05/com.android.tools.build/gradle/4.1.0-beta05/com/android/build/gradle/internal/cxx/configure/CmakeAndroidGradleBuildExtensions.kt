/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.CmakeListsPath
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument.DefineProperty
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.buildGenerationStateFile
import com.android.build.gradle.internal.cxx.model.cacheKeyFile
import com.android.build.gradle.internal.cxx.model.cmakeListsWrapperFile
import com.android.build.gradle.internal.cxx.model.compilerCacheUseFile
import com.android.build.gradle.internal.cxx.model.compilerCacheWriteFile
import com.android.build.gradle.internal.cxx.model.toolchainSettingsFromCacheFile
import com.android.build.gradle.internal.cxx.model.toolchainWrapperFile
import java.io.File

/**
 * Functions for updating the CMake invocation to use wrapping toolchain and CMakeLists.txt.
 * There are two distinct phases that occur.
 *
 * Before Ninja Project Generation
 * -------------------------------
 * - If user has defined ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED=false then return
 *   immediately and don't do any caching or any other modification to the CMake invocation.
 *
 * - Replace toolchain with a generated toolchain that will use cached compiler settings if
 *   they exist. It will also write a Json file called compiler_cache_use.json that has a
 *   boolean that indicates whether the cache was used:
 *
 *     { isCacheUsed: false }
 *
 * - Replace CMakeLists.txt with a version that will write all build variables to a Json file
 *   called build_generation_state.json.
 *
 *     { properties: [
 *         {name : "ANDROID", value : "TRUE"},
 *         {name : "ANDROID_ABI", value : "arm64-v8a"},
 *         etc.
 *         ] }
 *
 * - Save the compiler cache key to compiler_cache_key.json. These are the fields that are
 *   relevant to deciding what the compiler flags will be.
 *
 *     { gradlePluginVersion: "3.4.0-dev",
 *       ndkInstallationFolder: {
 *         path: "/Users/jomof/Library/Android/sdk/ndk-bundle"
 *       },
 *       ndkSourceProperties: {
 *         map: {
 *           "Pkg.Desc": "Android NDK",
 *           "Pkg.Revision": "18.0.5002713"
 *       } },
 *       args: [
 *         "-DANDROID_ABI\u003darm64-v8a",
 *         "-DANDROID_PLATFORM\u003dandroid-16",
 *         "-DCMAKE_CXX_FLAGS\u003d"
 *       ] }
 *
 * After Ninja Project Generation
 * ------------------------------
 * - If compiler_cache_use.json indicates that the cache was used in this project generation then
 *   return immediately so we don't re-cache the same values.
 *
 * - Read build_generation_state.json.
 *
 * - Copy compiler_cache_key.json to cache as the key in a file named {hashcode}_key.json.
 *
 * - Using content of build_generation_state.json, create a value file in the cache called
 *   {hashcode)_value.cmake with the compiler settings:
 *
 *     set(CMAKE_CXX11_COMPILE_FEATURES cxx_alias_templates;cxx_alignas;cxx_alignof;...)
 *     set(CMAKE_CXX14_COMPILE_FEATURES cxx_aggregate_default_initializers;...)
 *     set(CMAKE_CXX98_COMPILE_FEATURES cxx_template_template_parameters)
 *     set(CMAKE_CXX_COMPILER_ABI ELF)
 *     set(CMAKE_CXX_SIZEOF_DATA_PTR 4)
 *     etc.
 *
 *   This is the file that will be used by the toolchain wrapper the next time the project is
 *   generated.
 */

/**
 * This is the Before Ninja Project Generation from above.
 */
fun wrapCmakeListsForCompilerSettingsCaching(
    abi: CxxAbiModel,
    args: List<String>
): CmakeExecutionConfiguration {
    val commandLine = parseCmakeArguments(args)
    if (commandLine.getCmakeBooleanProperty(
            ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED) == false) {
        infoln("Not using cached compiler settings because " +
                "$ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED was set to false")
        // Remove ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED so user doesn't get warning
        // about unused property
        return CmakeExecutionConfiguration(abi.variant.module.makeFile.parentFile,
            commandLine.removeProperty(ANDROID_GRADLE_BUILD_COMPILER_SETTINGS_CACHE_ENABLED)
                .map { it.sourceArgument })
    }
    val cache = CmakeCompilerSettingsCache(abi.variant.module.project.compilerSettingsCacheFolder)
    val fileWriter = IdempotentFileWriter()
    val cacheKey = makeCmakeCompilerCacheKey(commandLine)

    if (cacheKey == null) {
        infoln("Not wrapping toolchain because couldn't construct cache key")
        return CmakeExecutionConfiguration(abi.variant.module.makeFile.parentFile, args)
    }

    // Write the cache files to .cxx so they can be used after the build.
    fileWriter.addFile(abi.cmake!!.cacheKeyFile.path, cacheKey.toJsonString())

    val cachedProperties = cache.tryGetValue(cacheKey)

    val toolchainReplaced = if (cachedProperties == null) {
        infoln("Not wrapping toolchain because compiler settings have not been cached before")
        commandLine
    } else {
        // Replace the original toolchain with a wrapper
        replaceToolchainWithWrapper(
            commandLine = commandLine,
            cachedProperties = cachedProperties,
            config = abi,
            fileWriter = fileWriter
        )
    }

    // Note that if we have cached compiler settings we don't strictly need to wrap CMakeLists.txt
    // because the purpose of wrapping is to save build variables so that they can be cached.
    // However, it's very helpful diagnostic information and will be useful when people send us
    // a zip of .cxx folder. Also, there may be other future purposes for wrapping
    // CMakeLists.txt such as helping with CCACHE-like scenarios.
    val result = replaceCmakeListsWithWrapper(
        commandLine = toolchainReplaced,
        abi = abi,
        fileWriter = fileWriter
    )

    // Commit file changes to disk
    fileWriter.write()
    return result
}

/**
 * Function that modifies a CMake invocation to wrap the toolchain.
 */
private fun replaceToolchainWithWrapper(
    commandLine: List<CommandLineArgument>,
    cachedProperties: String,
    config: CxxAbiModel,
    fileWriter: IdempotentFileWriter
): List<CommandLineArgument> {
    with(config.cmake!!) {
        return commandLine.map { arg ->
            when (arg) {
                is DefineProperty -> {
                    if (arg.propertyName == CMAKE_TOOLCHAIN_FILE.name) {
                        fileWriter.addFile(toolchainSettingsFromCacheFile.path, cachedProperties)
                        fileWriter.addFile(
                            toolchainWrapperFile.path,
                            wrapCmakeToolchain(
                                originalToolchainFile = File(arg.propertyValue),
                                cacheFile = toolchainSettingsFromCacheFile.absoluteFile,
                                cacheUseSignalFile = compilerCacheUseFile
                            )
                        )
                        DefineProperty.from(CMAKE_TOOLCHAIN_FILE, toolchainWrapperFile.path)
                    } else {
                        arg
                    }
                }
                else -> arg
            }
        }
    }
}

/**
 * Function that replaces CMakeLists.txt with a wrapper within a CMake invocation.
 */
private fun replaceCmakeListsWithWrapper(
    commandLine: List<CommandLineArgument>,
    abi: CxxAbiModel,
    fileWriter: IdempotentFileWriter
): CmakeExecutionConfiguration {
    with(abi.cmake!!) {
        return CmakeExecutionConfiguration(
            cmakeListsWrapperFile.parentFile,
            commandLine.asSequence().map { arg ->
                when (arg) {
                    is CmakeListsPath -> {
                        // Write the wrapping CMakeLists.txt file
                        fileWriter.addFile(
                            cmakeListsWrapperFile.path,
                            wrapCmakeLists(
                                originalCmakeListsFolder = File(arg.path),
                                gradleBuildOutputFolder = cmakeWrappingBaseFolder,
                                buildGenerationStateFile = buildGenerationStateFile
                            )
                        )
                        val path = cmakeListsWrapperFile.parent
                        CmakeListsPath.from(path)
                    }
                    else -> arg
                }
            }.map { it.sourceArgument }.toList()
        )
    }
}

/**
 * This is the After Ninja Project Generation from above.
 * Also handles cases like expected files missing and provides diagnostic information about why
 * cache wasn't saved. Generally, it should do as much as possible to check the validity of
 * the settings before writing to the cache.
 */
fun writeCompilerSettingsToCache(
    abi: CxxAbiModel
) {
    with(abi.cmake!!) {
        var cacheWriteStatus = "Exception while writing cache, check the log."
        var cacheUseStatus = false
        try {

            if (abi.variant.module.project.compilerSettingsCacheFolder.isDirectory && compilerCacheUseFile.isFile) {
                // We check for cacheRootFolder existence just in case it is removed manually
                // by user after we record the cache as being used, in which case we take that to
                // mean user might want to not use the cache.
                // If cacheRootFolder and the compilerCacheUseFile are present then it means the
                // toolchain was wrapped so there's a chance a cache was used. If a cache was used
                // then we don't want to write the same cache values again.
                val compilerCacheUse = CmakeCompilerCacheUse.fromFile(compilerCacheUseFile)

                // If the cache was used then there is no purpose in re-recording the cache output.
                if (compilerCacheUse.isCacheUsed) {
                    cacheUseStatus = true
                    cacheWriteStatus = "Cache was used in the build"
                    return
                }
            }

            val key = CmakeCompilerCacheKey.fromFile(cacheKeyFile)

            // If the buildGenerationStateFile wasn't generated then something went wrong
            // in the project generation phase.
            if (!buildGenerationStateFile.isFile) {
                cacheWriteStatus = "${buildGenerationStateFile.name} was not written by " +
                        "CMakeLists.txt"
                return
            }

            // If the cacheKeyFile wasn't generated then something went wrong in the project
            // generation phase.
            if (!cacheKeyFile.isFile) {
                cacheWriteStatus = "${cacheKeyFile.name} was not not found"
                return
            }

            val buildVariables = CmakeBuildGenerationState.fromFile(buildGenerationStateFile)

            // In order to be adequate compiler settings  ensure that the build produced all
            // required variables. If not, then don't save
            val propertiesDefinedInBuild = buildVariables.properties.map { it.name }
            val requiredPropertiesMissing =
                CMAKE_COMPILER_CHECK_CACHE_VALUE_REQUIRED_STRINGS subtract propertiesDefinedInBuild
            if (requiredPropertiesMissing.isNotEmpty()) {
                cacheWriteStatus = "Build didn't define all required properties. Missing: " +
                        requiredPropertiesMissing.joinToString()
                return
            }

            val compilerCheckVariables = buildVariables.properties
                .asSequence()
                .filter { property ->
                    CMAKE_COMPILER_CHECK_CACHE_VALUE_WHITELIST_STRINGS.contains(property.name)
                }
                .map { property ->
                    // Substitute literal NDK home for ${ANDROID_NDK} CMake variable
                    property.copy(
                        value = substituteCmakePaths(property.value, key.ndkInstallationFolder)
                    )
                }
                .map { property ->
                    "set(${property.name} ${property.value})"
                }
                .sorted()
                .joinToString("\n")

            val cache = CmakeCompilerSettingsCache(abi.variant.module.project.compilerSettingsCacheFolder)
            cache.saveKeyValue(key, compilerCheckVariables)
            cacheWriteStatus = ""
        } finally {
            CmakeCompilerCacheWrite(
                cacheWriteStatus.isEmpty(),
                cacheWriteStatus
            ).toFile(compilerCacheWriteFile)
            CmakeCompilerCacheUse(cacheUseStatus).toFile(compilerCacheUseFile)
        }
    }
}

