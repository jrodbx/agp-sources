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

import com.android.SdkConstants.CMAKE_DIR_PROPERTY
import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.NDK_SYMLINK_DIR
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.CmakeVersionRequirements
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.timing.time
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.prefs.AndroidLocationsProvider
import com.android.utils.FileUtils.join
import java.io.File
import java.io.FileReader

/**
 * Create module-level C/C++ build module ([CxxModuleModel]).
 */
fun createCxxModuleModel(
    sdkComponents : SdkComponentsBuildService,
    androidLocationProvider: AndroidLocationsProvider,
    configurationParameters: CxxConfigurationParameters,
    cmakeLocator: CmakeLocator
) : CxxModuleModel {

    val cxxFolder = configurationParameters.cxxFolder
    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(configurationParameters.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    val ndk = sdkComponents.versionedNdkHandler(
        compileSdkVersion = configurationParameters.compileSdkVersion,
        ndkVersion = configurationParameters.ndkVersion,
        ndkPath = configurationParameters.ndkPath
    ).ndkPlatform.getOrThrow()
    val ndkFolder = trySymlinkNdk(
            ndk.ndkDirectory,
            cxxFolder,
            localPropertyFile(NDK_SYMLINK_DIR))

    val ndkMetaPlatformsFile = NdkMetaPlatforms.jsonFile(ndkFolder)
    val ndkMetaPlatforms = if (ndkMetaPlatformsFile.isFile) {
        FileReader(ndkMetaPlatformsFile).use { reader ->
            NdkMetaPlatforms.fromReader(reader)
        }
    } else {
        null
    }

    // When configuration folding is enabled, the intermediates folder needs to look like:
    //
    //    app1/build/intermediates/cxx
    //
    // rather than:
    //
    //    app1/build/intermediates
    //
    // because the folder segments that are appended to it for different variants don't
    // have "cmake" or "ndk-build" in them. They look like this:
    //
    //    app1/build/intermediates/cxx/Debug/[configuration hash]
    //
    // Without the added "cxx", there's no indication that these intermediates are for C/C++
    // and we risk colliding with a variant named "Debug" in that folder.
    //
    val intermediatesBaseFolder = configurationParameters.intermediatesFolder
    val intermediatesFolder = join(configurationParameters.intermediatesFolder, "cxx")

    val project = time("create-project-model") { createCxxProjectModel(sdkComponents, configurationParameters) }
    val ndkMetaAbiList = time("create-ndk-meta-abi-list") { NdkAbiFile(ndkMetaAbisFile(ndkFolder)).abiInfoList }
    val cmake = time("create-cmake-model") {
        if (configurationParameters.buildSystem == CMAKE) {
            val exe = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) ".exe" else ""
            val cmakeFolder =
                    cmakeLocator.findCmakePath(
                            configurationParameters.cmakeVersion,
                            localPropertyFile(CMAKE_DIR_PROPERTY),
                            androidLocationProvider,
                            sdkComponents.sdkDirectoryProvider.get().asFile
                    ) { sdkComponents.installCmake(it) }
            val cmakeExe =
                    if (cmakeFolder == null) null
                    else join(cmakeFolder, "bin", "cmake$exe")
            val ninjaExe =
                    cmakeExe?.parentFile?.resolve("ninja$exe")
                            ?.takeIf { it.exists() }
            CxxCmakeModuleModel(
                    minimumCmakeVersion =
                    CmakeVersionRequirements(configurationParameters.cmakeVersion).effectiveRequestVersion,
                    isValidCmakeAvailable = cmakeFolder != null,
                    cmakeExe = cmakeExe,
                    ninjaExe = ninjaExe
            )

        } else {
            null
        }
    }

    return CxxModuleModel(
        moduleBuildFile = configurationParameters.buildFile,
        cxxFolder = cxxFolder,
        project = project,
        ndkMetaPlatforms = ndkMetaPlatforms,
        ndkMetaAbiList = ndkMetaAbiList,
        cmakeToolchainFile = join(ndkFolder, "build", "cmake", "android.toolchain.cmake"),
        cmake = cmake,
        ndkFolder = ndkFolder,
        ndkVersion = ndk.revision,
        ndkSupportedAbiList = ndk.supportedAbis,
        ndkDefaultAbiList = ndk.defaultAbis,
        ndkDefaultStl = ndk.ndkInfo.getDefaultStl(configurationParameters.buildSystem),
        makeFile = configurationParameters.makeFile,
        buildSystem = configurationParameters.buildSystem,
        intermediatesBaseFolder = intermediatesBaseFolder,
        intermediatesFolder = intermediatesFolder,
        gradleModulePathName = configurationParameters.gradleModulePathName,
        moduleRootFolder = configurationParameters.moduleRootFolder,
        stlSharedObjectMap =
            ndk.ndkInfo.supportedStls
                .map { stl ->
                    Pair(
                        stl,
                        ndk.ndkInfo.getStlSharedObjectFiles(stl, ndk.ndkInfo.supportedAbis)
                    )
                }
                .toMap(),
        outputOptions = configurationParameters.outputOptions,
    )
}

fun createCxxModuleModel(
    sdkComponents: SdkComponentsBuildService,
    androidLocationProvider: AndroidLocationsProvider,
    configurationParameters: CxxConfigurationParameters
) = createCxxModuleModel(
    sdkComponents,
    androidLocationProvider,
    configurationParameters,
    CmakeLocator()
)

