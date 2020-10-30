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
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.findCmakeVersion
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import java.io.File
import java.io.FileReader
import java.util.function.Consumer

/**
 * Examine the build.gradle DSL and determine whether the user has requested C/C++.
 * If so, then return a [CxxModuleModel] that describes the build.
 * If not, then return null.
 * This function and the [CxxModuleModel] are intended to be initiated in sync
 * configuration.
 * To make sync faster, as little work as possible is done directly in this
 * function. Instead, most vals are deferred until later with 'by lazy'.
 *
 * A note about laziness:
 * ----------------------
 * Everything except for information needed to determine whether this is a C/C++
 * module (and so [createCxxModuleModel] returns null) should be deferred with
 * 'by lazy' or some other means.
 *
 * The primary reason for this is that the configuration phase of sync should be
 * as as fast as possible.
 *
 * Some fields, like global.project.projectDir, would probably be harmless to
 * call immediately. However, it is generally hard to know what is harmless or
 * not. Additionally, fields and function call costs can change later.
 *
 * In addition to the primary reason, there are several side benefits:
 *  - We don't have to reason about whether accessing some global fields are
 *   costly or not.
 * - It insures the fields are immutable (which is a requirement of model
 *   interfaces).
 * - It insures that possibly costly function results are computed once.
 * - Some fields have side effects (like logging errors or warnings). These
 *   shouldn't be logged multiple times.
 * - If all fields that access global are lazy then it is trivial to write a
 *   unittest that verifies that global isn't accessed in [createCxxModuleModel].
 *
 * Since 'by lazy' is not costly in terms of memory or time it's preferable just
 * to always use it.
 */
fun createCxxModuleModel(
    sdkComponents : SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel,
    cmakeLocator: CmakeLocator
) : CxxModuleModel {

    val cxxFolder = findCxxFolder(
        configurationModel.moduleRootFolder,
        configurationModel.buildStagingFolder,
        configurationModel.buildDir)

    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(configurationModel.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    val ndk by lazy { sdkComponents.ndkHandler.ndkPlatform.getOrThrow() }
    return object : CxxModuleModel {
        override val moduleBuildFile = configurationModel.buildFile
        override val cxxFolder = cxxFolder
        override val project by lazy { createCxxProjectModel(sdkComponents, configurationModel) }
        override val ndkMetaPlatforms by lazy {
            val ndkMetaPlatformsFile = NdkMetaPlatforms.jsonFile(ndkFolder)
            if (ndkMetaPlatformsFile.isFile) {
                NdkMetaPlatforms.fromReader(FileReader(ndkMetaPlatformsFile))
            } else {
                null
            }
        }
        override val ndkMetaAbiList by lazy {
            NdkAbiFile(ndkMetaAbisFile(ndkFolder)).abiInfoList
        }
        override val cmake
            get() =
                if (buildSystem == CMAKE) {
                    val exe = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) ".exe" else ""
                    object: CxxCmakeModuleModel {
                        override val minimumCmakeVersion by lazy {
                            findCmakeVersion(configurationModel.cmakeVersion)
                        }
                        private val cmakeFolder by lazy {
                            cmakeLocator.findCmakePath(
                                configurationModel.cmakeVersion,
                                localPropertyFile(CMAKE_DIR_PROPERTY),
                                sdkComponents.sdkDirectoryProvider.get().asFile,
                                Consumer { sdkComponents.installCmake(it) })!!
                        }
                        override val cmakeExe by lazy {
                            join(cmakeFolder, "bin", "cmake$exe")
                        }
                        override val ninjaExe by lazy {
                            join(cmakeFolder, "bin", "ninja$exe").takeIf { it.exists() }
                        }
                    }

                } else {
                    null
                }
        override val ndkFolder by lazy {
            trySymlinkNdk(
                ndk.ndkDirectory,
                cxxFolder,
                localPropertyFile(NDK_SYMLINK_DIR))
        }
        override val ndkVersion by lazy { ndk.revision }
        override val ndkSupportedAbiList by lazy { ndk.supportedAbis }
        override val ndkDefaultAbiList by lazy { ndk.defaultAbis }
        override val ndkDefaultStl by lazy { ndk.ndkInfo.getDefaultStl(configurationModel.buildSystem) }
        override val makeFile get() = configurationModel.makeFile
        override val buildSystem get() = configurationModel.buildSystem
        override val splitsAbiFilterSet get() = configurationModel.splitsAbiFilterSet
        override val intermediatesFolder get() = configurationModel.intermediatesFolder
        override val gradleModulePathName get() = configurationModel.gradleModulePathName
        override val moduleRootFolder get() = configurationModel.moduleRootFolder
        override val buildStagingFolder get() = configurationModel.buildStagingFolder
        override val stlSharedObjectMap by lazy {
            ndk.ndkInfo.supportedStls
                .map { stl ->
                    Pair(
                        stl,
                        ndk.ndkInfo.getStlSharedObjectFiles(stl, ndk.ndkInfo.supportedAbis)
                    )
                }
                .toMap()
        }
    }
}

fun createCxxModuleModel(
    sdkComponents: SdkComponentsBuildService,
    configurationModel: CxxConfigurationModel
) = createCxxModuleModel(
    sdkComponents,
    configurationModel,
    CmakeLocator()
)

/**
 * Finds the location of the build-system output folder. For example, .cxx/cmake/debug/x86/
 *
 * If user specific externalNativeBuild.cmake.buildStagingFolder = 'xyz' then that folder
 * will be used instead of the default of moduleRoot/.cxx.
 *
 * If the resulting build output folder would be inside of moduleRoot/build then issue an error
 * because moduleRoot/build will be deleted when the user does clean and that will lead to
 * undefined behavior.
 */
internal fun findCxxFolder(
    moduleRootFolder : File,
    buildStagingDirectory: File?,
    buildFolder: File): File {
    val defaultCxxFolder =
        join(
            moduleRootFolder,
            CXX_DEFAULT_CONFIGURATION_SUBFOLDER
        )
    return when {
        buildStagingDirectory == null -> defaultCxxFolder
        FileUtils.isFileInDirectory(buildStagingDirectory, buildFolder) -> {
            warnln("""
            The build staging directory you specified ('${buildStagingDirectory.absolutePath}')
            is a subdirectory of your project's temporary build directory (
            '${buildFolder.absolutePath}'). Files in this directory do not persist through clean
            builds. It is recommended to either use the default build staging directory 
            ('$defaultCxxFolder'), or specify a path outside the temporary build directory.
            """.trimIndent())
            buildStagingDirectory
        }
        else -> buildStagingDirectory
    }
}
