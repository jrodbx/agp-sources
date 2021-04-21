/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:/** www.apache.org/licenses/LICENSE-2.0 */
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.cmake.cmakeBoolean
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.utils.FileUtils.join
import com.android.utils.tokenizeCommandLineToEscaped
import java.io.File

/**
 * Holds immutable ABI-level information for C/C++ build and sync, see README.md
 */
data class CxxAbiModel(
    /**
     * The target ABI
     */
    val abi: Abi,

    /**
     * Metadata about the ABI
     */
    val info: AbiInfo,

    /**
     * The .cxx build folder
     *   ex, $moduleRootFolder/.cxx/Debug/{hashcode}/x86_64
     */
    val cxxBuildFolder: File,

    /**
     * Folder for .so files
     *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/obj/x86_64
     */
    val soFolder: File,

    /**
     * An extra .cxx folder where build outputs are copied or symlinked too. This is also the
     * old location where actual builds happened before configuration folding was implemented.
     *   ex, $moduleRootFolder/build/intermediates/cmake/debug/obj/x86_64
     */
    val soRepublishFolder: File,

    /**
     * The final platform version for this ABI (ex 28)
     */
    val abiPlatformVersion: Int,

    /**
     * CMake-specific settings for this ABI. Return null if this isn't CMake.
     */
    val cmake: CxxCmakeAbiModel?,

    /**
     * The variant for this ABI
     */
    val variant: CxxVariantModel,

    /**
     * Ninja/gnu make build settings specified by BuildSettings.json. Returns an empty
     * model if the file is absent.
     */
    val buildSettings: BuildSettingsConfiguration,

    /**
     * The directory containing generated Prefab imports, if any.
     */
    val prefabFolder: File,

    /**
     * Whether or not this abi is active in the build or not.
     */
    val isActiveAbi: Boolean,

    /**
     * A SHA-256 of the configuration parameters for CMake or ndk-build.
     * This value is invariant of ABI, variant name, and output locations.
     * The purpose is to allow coalescing of multiple variants.
     */
    val fullConfigurationHash: String,

    /**
     * The inputs used to compute [fullConfigurationHash]
     */
    val configurationArguments: List<String>,

    /**
     * If present, the STL .so file that needs to be distributed with the libraries built.
     */
    val stlLibraryFile: File?,
) {
    override fun toString() = "${abi.tag}:${variant.variantName}${variant.module.gradleModulePathName}"
}

/**
 * The model json
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/android_gradle_build.json
 */
val CxxAbiModel.jsonFile: File
    get() = join(cxxBuildFolder, "android_gradle_build.json")

/**
 * The ninja log file
 *   ex, $moduleRootFolder/.cxx/cmake/debug/x86/.ninja_log
 */
val CxxAbiModel.ninjaLogFile: File
    get() = join(cxxBuildFolder, ".ninja_log")

/**
 * Folder for .o files
 *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/objs-debug
 */
val CxxAbiModel.objFolder: File
    get() = when(variant.module.buildSystem) {
        CMAKE -> join(cxxBuildFolder, "CMakeFiles")
        NDK_BUILD -> join(variant.soFolder, abi.tag)
    }

/**
 * Location of model generation metadata
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta
 */
private val CxxAbiModel.modelMetadataFolder: File
    get() = join(variant.intermediatesFolder, "meta", abi.tag)

/**
 * The command that is executed to build or generate projects
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/build_command.txt
 */
val CxxAbiModel.buildCommandFile: File
    get() = join(modelMetadataFolder, "build_command.txt")

/**
 * Output of the build
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/build_output.txt
 */
val CxxAbiModel.buildOutputFile: File
    get() = join(modelMetadataFolder, "build_output.txt")

/**
 * Output file of the Cxx*Model structure
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/build_model.json
 */
val CxxAbiModel.modelOutputFile: File
    get() = join(modelMetadataFolder, "build_model.json")

/**
 * Json Generation logging record
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/metadata_generation_record.json
 */
val CxxAbiModel.jsonGenerationLoggingRecordFile: File
    get() = join(modelMetadataFolder, "metadata_generation_record.json")

/**
 * Text file containing command run to generate C/C++ metadata.
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/metadata_generation_command.txt
 */
val CxxAbiModel.metadataGenerationCommandFile: File
    get() = join(modelMetadataFolder, "metadata_generation_command.txt")

/**
 * Text file containing STDOUT for the process run to generate C/C++ metadata.
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/metadata_generation_stdout.txt
 */
val CxxAbiModel.metadataGenerationStdoutFile: File
    get() = join(modelMetadataFolder, "metadata_generation_stdout.txt")

/**
 * Text file containing STDERR for the process run to generate C/C++ metadata.
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/metadata_generation_stderr.txt
 */
val CxxAbiModel.metadataGenerationStderrFile: File
    get() = join(modelMetadataFolder, "metadata_generation_stderr.txt")

/**
 * When CMake server is used, this is the log of the interaction with it.
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/x86_64/cmake_server_log.txt
 */
val CxxAbiModel.cmakeServerLogFile: File
    get() = join(modelMetadataFolder, "cmake_server_log.txt")

/**
 * The prefab configuration used when building this project
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/prefab_config.json
 */
val CxxAbiModel.prefabConfigFile: File
    get() = join(cxxBuildFolder, "prefab_config.json")

/**
 * compile_commands.json file for this ABI.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json
 */
val CxxAbiModel.compileCommandsJsonFile: File
    get() = join(cxxBuildFolder, "compile_commands.json")

/**
 * compile_commands.json.bin file for this ABI. This is equivalent to a compile_commands.json file
 * but more compact.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin
 */
val CxxAbiModel.compileCommandsJsonBinFile: File
    get() = join(cxxBuildFolder, "compile_commands.json.bin")

/**
 * additional_project_files.txt file for this ABI. This file contains a newline separated list of
 * filenames that are known by the build system and considered to be part of the project.
 */
val CxxAbiModel.additionalProjectFilesIndexFile: File
    get() = join(cxxBuildFolder, "additional_project_files.txt")

/**
 * Text file containing absolute paths to folders containing the generated symbols, one per line.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt
 */
val CxxAbiModel.symbolFolderIndexFile: File
    get() = join(cxxBuildFolder, "symbol_folder_index.txt")

/**
 * Text file containing absolute paths to native build files (For example, CMakeLists.txt for
 * CMake). One per line.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt
 */
val CxxAbiModel.buildFileIndexFile: File
    get() = join(cxxBuildFolder, "build_file_index.txt")

/**
 * The CMake file API query folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/query/client-agp
 */
val CxxAbiModel.clientQueryFolder: File
    get() = join(cxxBuildFolder, ".cmake/api/v1/query/client-agp")

/**
 * The CMake file API reply folder.
 *   ex, $moduleRootFolder/.cxx/cmake/debug/armeabi-v7a/.cmake/api/v1/reply
 */
val CxxAbiModel.clientReplyFolder: File
    get() = join(cxxBuildFolder, ".cmake/api/v1/reply")

fun CxxAbiModel.shouldGeneratePrefabPackages(): Boolean {
    // Prefab will fail if we try to create ARMv5/MIPS/MIPS64 modules. r17 was the first NDK version
    // that we can guarantee will not be used to use those ABIs.
    return (variant.module.project.isPrefabEnabled
            && (variant.prefabPackageDirectoryListFileCollection != null)
            && variant.module.ndkVersion.major >= 17)
}

/**
 * Call [compute] if this is a CMake build.
 */
fun <T> CxxAbiModel.ifCMake(compute : () -> T?) =
    if (variant.module.buildSystem == CMAKE) compute() else null

/**
 * Call [compute] if this is an nndk-build build.
 */
fun <T> CxxAbiModel.ifNdkBuild(compute : () -> T?) =
        if (variant.module.buildSystem == NDK_BUILD) compute() else null

/**
 * Returns the Ninja build commands from CMakeSettings.json.
 * Returns an empty string if it does not exist.
 */
fun CxxAbiModel.getBuildCommandArguments(): List<String> {
    return cmake?.buildCommandArgs?.tokenizeCommandLineToEscaped() ?: emptyList()
}

/**
 * 32 or 64 bits
 */
val CxxAbiModel.bitness
    get() = info.bitness

/**
 * Short form of the full configuration hash.
 */
val CxxAbiModel.configurationHash
    get() = fullConfigurationHash.substring(0, 8)

/**
 * Lowercase string form of ABI name (like "x86").
 */
val CxxAbiModel.tag
    get() = abi.tag

/**
 * True if this ABI is 64 bits.
 */
val CxxAbiModel.is64Bits
    get() = cmakeBoolean(bitness == 64)

/**
 * True if this ABI is a default ABI.
 */
val CxxAbiModel.isDefault
    get() = cmakeBoolean(info.isDefault)

/**
 * True if this ABI is deprecated.
 */
val CxxAbiModel.isDeprecated
    get() = cmakeBoolean(info.isDeprecated)

/**
 * A platform tag for Android (ex android-19)
 */
val CxxAbiModel.platform
    get() = "android-$abiPlatformVersion"

/**
 * Get the platform codename (like 'Q')
 */
val CxxAbiModel.platformCode
    get() = variant.module.ndkMetaPlatforms?.let {
        it.aliases
                .toList()
                .filter { (_, code) -> code == abiPlatformVersion }
                .minBy { (alias, _) -> alias.length }
                ?.first
    } ?: ""


