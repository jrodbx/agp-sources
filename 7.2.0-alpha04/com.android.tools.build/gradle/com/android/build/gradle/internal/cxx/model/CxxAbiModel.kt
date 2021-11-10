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
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils.join
import com.android.utils.tokenizeCommandLineToEscaped
import java.io.File
import kotlin.math.max

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

    /**
     * This ABI's intermediates folder but without the ABI at the end. This can't be stored
     * on the variant because {hashcode} may be different between two ABIs in the same variant.
     *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}
     */
    val intermediatesParentFolder: File
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
 * The json mini-config file contains a subset of the regular json file that is much smaller and
 * less memory-intensive to read.
 */
val CxxAbiModel.miniConfigFile: File
    get() = join(modelMetadataFolder, "android_gradle_build_mini.json")

/**
 * Location of model generation metadata
 *   ex, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta
 */
private val CxxAbiModel.modelMetadataFolder: File
    get() = join(intermediatesParentFolder, "meta", abi.tag)


/**
 * Pull up the app's minSdkVersion to be within the bounds for the ABI and NDK.
 */
val CxxAbiModel.minSdkVersion : Int get() {
    val ndkVersion = variant.module.ndkVersion.major
    val metaPlatforms = variant.module.ndkMetaPlatforms
    val minVersionForAbi = when {
        abi.supports64Bits() -> AndroidVersion.SUPPORTS_64_BIT.apiLevel
        else -> 1
    }
    val minVersionForNdk = when {
        // Newer NDKs expose the minimum supported version via meta/platforms.json
        metaPlatforms != null -> metaPlatforms.min
        // Older NDKs did not expose this, but the information is in the change logs
        ndkVersion < 12 -> 1
        ndkVersion < 15 -> 9
        ndkVersion < 18 -> 14
        else -> 16
    }
    return max(abiPlatformVersion, max(minVersionForAbi, minVersionForNdk))
}

/**
 * The ninja log file
 *   ex, $moduleRootFolder/.cxx/cmake/debug/x86/.ninja_log
 */
val CxxAbiModel.ninjaLogFile: File
    get() = join(cxxBuildFolder, ".ninja_log")


/**
 * .ninja_deps file for this ABI. Only applies to CMake builds.
 * This file contains the source -> header dependencies discovered during the last build.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/.ninja_deps
 */
val CxxAbiModel.ninjaDepsFile: File
    get() = join(cxxBuildFolder, ".ninja_deps")

/**
 * Folder for .o files
 *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a/objs-debug
 */
val CxxAbiModel.objFolder: File
    get() = when(variant.module.buildSystem) {
        CMAKE -> join(cxxBuildFolder, "CMakeFiles")
        NDK_BUILD -> soFolder
        else -> error("${variant.module.buildSystem}")
    }

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
 *   needs to have the same clean semantics as compile_commands.json, so place in that folder.
 */
val CxxAbiModel.metadataGenerationCommandFile: File
    get() = compileCommandsJsonFile.resolveSibling("metadata_generation_command.txt")

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
 * Folder used to hold metadata generation performance timings.
 */
val CxxAbiModel.metadataGenerationTimingFolder: File
    get() = modelMetadataFolder

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
 * Note: Should be in the same folder as compile_commands.json because it is derived from that file.
 */
val CxxAbiModel.compileCommandsJsonBinFile: File
    get() = compileCommandsJsonFile.resolveSibling("compile_commands.json.bin")

/**
 * additional_project_files.txt file for this ABI. This file contains a newline separated list of
 * filenames that are known by the build system and considered to be part of the project.
 */
val CxxAbiModel.additionalProjectFilesIndexFile: File
    get() = join(modelMetadataFolder, "additional_project_files.txt")

/**
 * Text file containing absolute paths to folders containing the generated symbols, one per line.
 * For example, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/armeabi-v7a/symbol_folder_index.txt
 */
val CxxAbiModel.symbolFolderIndexFile: File
    get() = join(modelMetadataFolder, "symbol_folder_index.txt")

/**
 * Text file containing absolute paths to native build files (For example, CMakeLists.txt for
 * CMake). One per line.
 * For example, $moduleRootFolder/build/intermediates/cxx/Debug/{hashcode}/meta/armeabi-v7a/build_file_index.txt
 */
val CxxAbiModel.buildFileIndexFile: File
    get() = join(modelMetadataFolder, "build_file_index.txt")

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

// True if the build is capable of handling prefab packages. Does not indicate that prefab will
// actually run. Separate from shouldGeneratePrefabPackages because whether or not prefab actually
// runs depends on whether or not prefab has any inputs, which cannot be known during configuration
// time.
fun CxxAbiModel.buildIsPrefabCapable(): Boolean = variant.module.project.isPrefabEnabled
        // Prefab will fail if we try to create ARMv5/MIPS/MIPS64 modules. r17 was the first NDK
        // version that we can guarantee will not be used to use those ABIs.
        && variant.module.ndkVersion.major >= 17

fun CxxAbiModel.shouldGeneratePrefabPackages(): Boolean = buildIsPrefabCapable()
        && variant.prefabPackageDirectoryListFileCollection != null
        && !variant.prefabPackageDirectoryListFileCollection.isEmpty

/**
 * Call [compute] if logging native configure to lifecycle
 */
fun <T> CxxAbiModel.ifLogNativeConfigureToLifecycle(compute : () -> T?) =
    variant.ifLogNativeConfigureToLifecycle(compute)

/**
 * Call [compute] if logging native build to lifecycle
 */
fun <T> CxxAbiModel.ifLogNativeBuildToLifecycle(compute : () -> T?) =
    variant.ifLogNativeBuildToLifecycle(compute)

/**
 * Returns the Ninja build commands from CMakeSettings.json.
 * Returns an empty string if it does not exist.
 */
fun CxxAbiModel.getBuildCommandArguments(): List<String> {
    val fromBuildCommandArgs = cmake?.buildCommandArgs?.tokenizeCommandLineToEscaped() ?: emptyList()
    if (variant.verboseMakefile == true) {
        return listOf("-v") + fromBuildCommandArgs
    }
    return fromBuildCommandArgs
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
 * The CPU architecture name
 */
val CxxAbiModel.cpuArchitecture
    get() = abi.architecture

/**
 * The name of the CPU architecture (like "arm") for use in
 * android triplet naming that is compatible with vcpkg.
 * The difference is that X86_64 is represented by "x64" not
 * "x86_64"
 */
val CxxAbiModel.altCpuArchitecture
    get() = when(abi) {
        Abi.X86_64 -> "x64"
        else -> abi.architecture
    }

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
                .minByOrNull { (alias, _) -> alias.length }
                ?.first
    } ?: ""


