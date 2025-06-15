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

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.computeNdkSymLinkFolder
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationParameters
import com.android.build.gradle.internal.cxx.settings.Macro
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_CMAKE_EXECUTABLE
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NINJA
import com.android.utils.FileUtils.join
import java.io.File
import java.io.FileReader

/**
 * Create module-level C/C++ build module ([CxxModuleModel]).
 */
fun createCxxModuleModel(
    sdkComponents : SdkComponentsBuildService,
    configurationParameters: CxxConfigurationParameters,
) : CxxModuleModel {
    val cxxFolder = configurationParameters.cxxFolder
    val ndk = sdkComponents.versionedNdkHandler(
        ndkVersion = configurationParameters.ndkVersion,
        ndkPathFromDsl = configurationParameters.ndkPathFromDsl,
    ).ndkPlatform.getOrThrow()
    val ndkSymlinkFolder = computeNdkSymLinkFolder(
            ndk.ndkDirectory,
            cxxFolder,
            sdkComponents.ndkSymlinkDirFromProperties?.let { File(it) })
    val finalNdkFolder = ndkSymlinkFolder ?: ndk.ndkDirectory
    val ndkMetaPlatformsFile = NdkMetaPlatforms.jsonFile(ndk.ndkDirectory)
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

    val project = createCxxProjectModel(sdkComponents, configurationParameters)
    val ndkMetaAbiList = NdkAbiFile(ndkMetaAbisFile(ndk.ndkDirectory)).abiInfoList
    val cmake = if (configurationParameters.buildSystem == CMAKE) {
        CxxCmakeModuleModel(
            cmakeDirFromPropertiesFile = sdkComponents.cmakeDirFromProperties?.let { File(it) },
            cmakeVersionFromDsl = configurationParameters.cmakeVersion,
            cmakeExe = File(NDK_MODULE_CMAKE_EXECUTABLE.configurationPlaceholder)
        )
    } else {
        null
    }

    return CxxModuleModel(
        moduleBuildFile = configurationParameters.buildFile,
        cxxFolder = cxxFolder,
        project = project,
        ndkMetaPlatforms = ndkMetaPlatforms,
        ndkMetaAbiList = ndkMetaAbiList,
        cmakeToolchainFile = join(finalNdkFolder, "build", "cmake", "android.toolchain.cmake"),
        cmake = cmake,
        ndkFolder = finalNdkFolder,
        ndkFolderBeforeSymLinking = ndk.ndkDirectory,
        ndkFolderAfterSymLinking = ndkSymlinkFolder,
        ndkVersion = ndk.revision,
        ndkSupportedAbiList = ndk.supportedAbis,
        ndkDefaultAbiList = ndk.defaultAbis,
        ndkDefaultStl = ndk.ndkInfo.getDefaultStl(configurationParameters.buildSystem),
        makeFile = configurationParameters.moduleRootFolder.resolve(configurationParameters.makeFile).normalize(),
        configureScript = configurationParameters.configureScript?.let { configureScript ->
            configurationParameters.moduleRootFolder.resolve(configureScript).normalize()
        },
        buildSystem = configurationParameters.buildSystem,
        intermediatesBaseFolder = intermediatesBaseFolder,
        intermediatesFolder = intermediatesFolder,
        gradleModulePathName = configurationParameters.gradleModulePathName,
        moduleRootFolder = configurationParameters.moduleRootFolder,
        stlSharedObjectMap =
        ndk.ndkInfo.supportedStls.associateWith { stl ->
            ndk.ndkInfo.getStlSharedObjectFiles(stl, ndk.ndkInfo.supportedAbis)
        },
        outputOptions = configurationParameters.outputOptions,
        ninjaExe = when(configurationParameters.buildSystem) {
            NINJA, CMAKE -> File(Macro.NDK_MODULE_NINJA_EXECUTABLE.configurationPlaceholder)
            else -> null
        },
        hasBuildTimeInformation = false
    )
}

