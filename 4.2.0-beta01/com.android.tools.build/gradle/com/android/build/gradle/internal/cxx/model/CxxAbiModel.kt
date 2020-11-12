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
import com.android.build.gradle.internal.cxx.settings.BuildSettingsConfiguration
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils
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
     * The gradle-controlled .cxx build folder. By default, the same as [cxxBuildFolder] but this
     * can be overridden by CMakeSettings.json.
     *
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a
     */
    val originalCxxBuildFolder: File,

    /**
     * The .cxx build folder
     *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a
     */
    val cxxBuildFolder: File,

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
    val prefabFolder: File
)

/**
 * The model json
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/android_gradle_build.json
 */
val CxxAbiModel.jsonFile: File
    get() = FileUtils.join(cxxBuildFolder, "android_gradle_build.json")

/**
 * The ninja log file
 *   ex, $moduleRootFolder/.cxx/cmake/debug/x86/.ninja_log
 */
val CxxAbiModel.ninjaLogFile: File
    get() = FileUtils.join(cxxBuildFolder, ".ninja_log")

/**
 * Folder for .o files
 *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a
 */
val CxxAbiModel.objFolder: File
    get() = when(variant.module.buildSystem) {
        NativeBuildSystem.CMAKE -> FileUtils.join(cxxBuildFolder, "CMakeFiles")
        NativeBuildSystem.NDK_BUILD -> FileUtils.join(variant.objFolder, abi.tag)
    }

/**
 * Folder for .so files
 *   ex, $moduleRootFolder/build/intermediates/ndkBuild/debug/obj/local/armeabi-v7a
 */
val CxxAbiModel.soFolder: File
    get() = FileUtils.join(variant.objFolder, abi.tag)

/**
 * The command that is executed to build or generate projects
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_command.txt
 */
val CxxAbiModel.buildCommandFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "build_command.txt")

val CxxAbiModel.androidGradleBuildVersion: File
    get() = FileUtils.join(originalCxxBuildFolder, "android_gradle_build_version.txt")

/**
 * Output of the build
 *   ex $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_output.txt
 */
val CxxAbiModel.buildOutputFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "build_output.txt")

/**
 * Output file of the Cxx*Model structure
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_model.json
 */
val CxxAbiModel.modelOutputFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "build_model.json")

/**
 * Json Generation logging record
 */
val CxxAbiModel.jsonGenerationLoggingRecordFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "json_generation_record.json")

/**
 * The prefab configuration used when building this project
 *   ex, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/prefab_config.json
 */
val CxxAbiModel.prefabConfigFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "prefab_config.json")

/**
 * compile_commands.json file for this ABI.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json
 */
val CxxAbiModel.compileCommandsJsonFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "compile_commands.json")

/**
 * compile_commands.json.bin file for this ABI. This is equivalent to a compile_commands.json file
 * but more compact.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/compile_commands.json.bin
 */
val CxxAbiModel.compileCommandsJsonBinFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "compile_commands.json.bin")

/**
 * Text file containing absolute paths to folders containing the generated symbols, one per line.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/symbol_folder_index.txt
 */
val CxxAbiModel.symbolFolderIndexFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "symbol_folder_index.txt")

/**
 * Text file containing absolute paths to native build files (For example, CMakeLists.txt for
 * CMake). One per line.
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/build_file_index.txt
 */
val CxxAbiModel.buildFileIndexFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "build_file_index.txt")

/**
 * Text file containing command run to generate C/C++ metadata.
 *
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/metadata_generation_command.txt
 */
val CxxAbiModel.metadataGenerationCommandFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "metadata_generation_command.txt")

/**
 * Text file containing STDOUT for the process run to generate C/C++ metadata.
 *
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/metadata_generation_stdout.txt
 */
val CxxAbiModel.metadataGenerationStdoutFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "metadata_generation_stdout.txt")

/**
 * Text file containing STDERR for the process run to generate C/C++ metadata.
 *
 * For example, $moduleRootFolder/.cxx/ndkBuild/debug/armeabi-v7a/metadata_generation_stderr.txt
 */
val CxxAbiModel.metadataGenerationStderrFile: File
    get() = FileUtils.join(originalCxxBuildFolder, "metadata_generation_stderr.txt")

fun CxxAbiModel.shouldGeneratePrefabPackages(): Boolean {
    // Prefab will fail if we try to create ARMv5/MIPS/MIPS64 modules. r17 was the first NDK version
    // that we can guarantee will not be used to use those ABIs.
    return (variant.module.project.isPrefabEnabled
            && (variant.prefabPackageDirectoryListFileCollection != null)
            && variant.module.ndkVersion.major >= 17)
}
