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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.ANDROID_STL
import com.android.build.gradle.internal.cxx.configure.CommandLineArgument
import com.android.build.gradle.internal.cxx.configure.NdkBuildProperty.APP_STL
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.configure.getNdkBuildProperty
import com.android.build.gradle.internal.cxx.configure.isCmakeForkVersion
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.configure.toNdkBuildArguments
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.VERBOSE
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.BUILD_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.CLEAN_STDOUT
import com.android.build.gradle.internal.cxx.gradle.generator.NativeBuildOutputOptions.CONFIGURE_STDOUT
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.ndk.AbiInfo
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.repository.Revision
import com.android.utils.FileUtils.join
import java.io.File

/**
 * Holds immutable module-level information for C/C++ build and sync, see README.md
 */
data class CxxModuleModel(

    val cxxFolder : File,

    /**
     * Folder for intermediates (not just C++)
     * ex, source-root/Source/Android/app/build/intermediates
     */
    val intermediatesBaseFolder: File,

    /**
     * cxx subfolder for intermediates
     * ex, source-root/Source/Android/app/build/intermediates/cxx
     */
    val intermediatesFolder: File,

    /**
     * The colon-delimited gradle path to this module
     *   ex, ':app' in ./gradlew :app:externalNativeBuildDebug
     */
    val gradleModulePathName: String,

        /**
     * Dir of the project
     *   ex, source-root/Source/Android/app
     */
    val moduleRootFolder: File,

        /**
     * The build.gradle file
     */
    val moduleBuildFile: File,

        /**
     * The makefile
     *   ex, android.externalNativeBuild.cmake.path 'CMakeLists.txt'
     */
    val makeFile: File,

        /**
     * The type of native build system
     *   ex, CMAKE
     */
    val buildSystem: NativeBuildSystem,

        /**
     * Folder path to the NDK
     *   ex, /Android/sdk/ndk/20.0.5344622
     */
    val ndkFolder: File,

        /**
     * The version of the NDK
     *   ex, 20.0.5344622-rc1
     */
    val ndkVersion: Revision,

        /**
     * ABIs supported by this NDK
     *   ex, x86, x86_64
     */
    val ndkSupportedAbiList: List<Abi>,

        /**
     * ABIs that are default for this NDK
     *   ex, x86_64
     */
    val ndkDefaultAbiList: List<Abi>,

        /**
     * The default STL that will be used by the given NDK version if the user does not select one.
     */
    val ndkDefaultStl: Stl,

        /**
     * Information about minimum and maximum platform along with mapping between platform
     * and platform code. Will be null if the NDK is so old it doesn't have meta/platforms.json.
     */
    val ndkMetaPlatforms: NdkMetaPlatforms?,

        /**
     * Information about all ABIs
     */
    val ndkMetaAbiList: List<AbiInfo>,

        /**
     * Path to the CMake toolchain in NDK after wrapping (if necessary). For NDK 15 and above,
     * this is equal to the originalCmakeToolchainFile.
     * ex, /path/to/ndk/android.toolchain.cmake
     */
    val cmakeToolchainFile: File,

        /**
     * CMake-specific settings for this Module.
     */
    val cmake: CxxCmakeModuleModel?,

        /**
     * Map describing the locations of STL shared objects for each STL/ABI pair.
     *
     * Note that no entry will be present for STLs that do not support packaging (static STLs, the
     * system STL, and the "none" STL) or for STLs that are not supported by the given NDK. ABIs not
     * supported by the given NDK will also not be present in the map.
     */
    val stlSharedObjectMap: Map<Stl, Map<Abi, File>>,

        /**
     * The project for this module
     */
    val project: CxxProjectModel,

    /** Output logging levels */
    val outputOptions: Set<NativeBuildOutputOptions>,
)

/** The user's CMakeSettings.json file next to CMakeLists.txt */
val CxxModuleModel.cmakeSettingsFile: File
    get() = join(makeFile.parentFile, "CMakeSettings.json")

/** The user's BuildSettings.json file next to CMakeLists.txt */
val CxxModuleModel.buildSettingsFile : File
    get() = join(makeFile.parentFile, "BuildSettings.json")

/** The folder of the make file (CMakeLists.txt or Android.mk */
val CxxModuleModel.makeFileFolder : File
    get() = makeFile.parentFile

/** Human-readable name of this module */
val CxxModuleModel.moduleName : String
    get() = gradleModulePathName.substringAfterLast(":")

/** The minimum platform for the NDK */
val CxxModuleModel.ndkMinPlatform : String
    get() = ndkMetaPlatforms?.min?.toString() ?: ""

/** The maximum platform for the NDK */
val CxxModuleModel.ndkMaxPlatform : String
    get() = ndkMetaPlatforms?.max?.toString() ?: ""

/** The major version of the NDK*/
val CxxModuleModel.ndkMajorVersion : String
    get() = ndkVersion.major.toString()

/** The minor version of the NDK*/
val CxxModuleModel.ndkMinorVersion : String
    get() = ndkVersion.minor.toString()

/** The minor version of the NDK*/
val CxxModuleModel.cmakeGenerator : String
    get() = when {
        cmake == null -> ""
        cmake.minimumCmakeVersion.isCmakeForkVersion() -> "Android Gradle - Ninja"
        else -> "Ninja"
    }

/**
 * Call [compute] if logging native configure to lifecycle
 */
fun <T> CxxModuleModel.ifLogNativeConfigureToLifecycle(compute : () -> T?) =
    if (outputOptions.contains(VERBOSE) ||
        outputOptions.contains(CONFIGURE_STDOUT)) compute() else null

/**
 * Call [compute] if logging native build to lifecycle
 */
fun <T> CxxModuleModel.ifLogNativeBuildToLifecycle(compute : () -> T?) =
    if (outputOptions.contains(VERBOSE) ||
        outputOptions.contains(BUILD_STDOUT)) compute() else null

/**
 * Return true if we should log native clean to lifecycle log
 */
val CxxModuleModel.logNativeCleanToLifecycle : Boolean get() =
    outputOptions.contains(VERBOSE) || outputOptions.contains(CLEAN_STDOUT)

/**
 * Determine, for CMake, which STL is used based on command-line arguments from the user.
 */
fun CxxModuleModel.determineUsedStlForCmake(arguments: List<CommandLineArgument>): Stl {
    val stlFromArgument = arguments.getCmakeProperty(ANDROID_STL)
    if (stlFromArgument != null) {
        val result = Stl.fromArgumentName(stlFromArgument)
        if (result != null) return result
        warnln("Unable to parse STL from build.gradle arguments: $stlFromArgument")
    }
    return ndkDefaultStl
}

/**
 * Determine, for ndk-build, which STL is used based on command-line arguments from the user.
 */
fun CxxModuleModel.determineUsedStlForNdkBuild(arguments: List<CommandLineArgument>): Stl {
    val stlFromArgument = arguments.getNdkBuildProperty(APP_STL)
    if (stlFromArgument != null) {
        val result = Stl.fromArgumentName(stlFromArgument)
        if (result != null) return result
        warnln("Unable to parse STL from build.gradle arguments: $stlFromArgument")
    }

    // For ndk-build, the STL may also be specified in the project's Application.mk.
    // Try parsing the user's STL from their Application.mk, and emit an error if we can't. If
    // we can't parse it the user will need to take some action (alter their Application.mk such
    // that APP_STL becomes trivially parsable, or define it in their build.gradle instead.
    var appStl: String? = null
    val applicationMk = makeFile.resolveSibling("Application.mk")
    if (applicationMk.exists()) {
        for (line in applicationMk.readText().lines()) {
            val match = Regex("^APP_STL\\s*:?=\\s*(.*)$").find(line.trim()) ?: continue
            val appStlMatch = match.groups[1]
            require(appStlMatch != null) // Should be impossible.
            appStl = appStlMatch.value.takeIf { it.isNotEmpty() }
        }

        if (appStl != null) {
            val result = Stl.fromArgumentName(appStl)
            if (result != null) return result
            warnln("Unable to parse APP_STL from $applicationMk: $appStl")
        }
    }

    // Otherwise the default it used.
    return ndkDefaultStl
}

/**
 * Determine which STL is used based on command-line arguments from the user.
 */
fun CxxModuleModel.determineUsedStlFromArguments(arguments: List<CommandLineArgument>): Stl {
    return when(buildSystem) {
        CMAKE -> determineUsedStlForCmake(arguments)
        NDK_BUILD -> determineUsedStlForNdkBuild(arguments)
        else -> error("$buildSystem")
    }
}

/**
 * Determine which STL is used based on command-line arguments from the user.
 */
fun CxxModuleModel.determineUsedStl(arguments: List<String>): Stl {
    return when(buildSystem) {
        CMAKE -> determineUsedStlForCmake(arguments.toCmakeArguments())
        NDK_BUILD -> determineUsedStlForNdkBuild(arguments.toNdkBuildArguments())
        else -> error("$buildSystem")
    }
}

/**
 * Return a descriptive string for the build system to use in log messages.
 */
val CxxModuleModel.buildSystemTag : String get() = when (buildSystem) {
    CMAKE -> "cmake"
    NativeBuildSystem.CUSTOM -> "custom"
    NDK_BUILD -> "ndkBuild"
}

/**
 * Return a descriptive string for the build system to name tasks.
 */
val CxxModuleModel.buildSystemNameForTasks : String get() = when (buildSystem) {
    CMAKE -> "CMake"
    NativeBuildSystem.CUSTOM -> "Custom"
    NDK_BUILD -> "NdkBuild"
}


/**
 * Folder name suffix for particular build systems.
 */
val CxxModuleModel.intermediatesParentDirSuffix : String get() = when(buildSystem) {
    CMAKE -> "obj"
    NDK_BUILD -> "obj/local"
    else -> error("$buildSystem")
}


