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
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.caching.CachingEnvironment
import com.android.build.gradle.internal.cxx.configure.ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION
import com.android.build.gradle.internal.cxx.configure.CXX_DEFAULT_CONFIGURATION_SUBFOLDER
import com.android.build.gradle.internal.cxx.configure.CmakeLocator
import com.android.build.gradle.internal.cxx.configure.NdkAbiFile
import com.android.build.gradle.internal.cxx.configure.NdkMetaPlatforms
import com.android.build.gradle.internal.cxx.configure.findCmakeVersion
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.android.build.gradle.internal.cxx.configure.ndkMetaAbisFile
import com.android.build.gradle.internal.cxx.configure.trySymlinkNdk
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.services.createDefaultServiceRegistry
import com.android.build.gradle.internal.model.CoreExternalNativeBuild
import com.android.build.gradle.internal.ndk.NdkHandler
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.build.gradle.tasks.NativeBuildSystem.CMAKE
import com.android.build.gradle.tasks.NativeBuildSystem.NDK_BUILD
import com.android.utils.FileUtils
import com.android.utils.FileUtils.join
import org.gradle.api.InvalidUserDataException
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
 * module (and so [tryCreateCxxModuleModel] returns null) should be deferred with
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
 *   unittest that verifies that global isn't accessed in [tryCreateCxxModuleModel].
 *
 * Since 'by lazy' is not costly in terms of memory or time it's preferable just
 * to always use it.
 */
fun tryCreateCxxModuleModel(
    global: GlobalScope,
    cmakeLocator: CmakeLocator
) : CxxModuleModel? {

    val (buildSystem, makeFile, buildStagingFolder) =
        getProjectPath(global.extension.externalNativeBuild) ?: return null

    val cxxFolder = findCxxFolder(
        global.project.projectDir,
        buildStagingFolder,
        global.project.buildDir)

    /**
     * Construct an [NdkHandler] and attempt to auto-download an NDK. If auto-download fails then
     * allow valid [errorln]s to pass or throw exception that will trigger download hyperlinks
     * in Android Studio
     */
    fun ndkHandler(): NdkHandler {
        CachingEnvironment(cxxFolder).use {
            val ndkHandler = global.sdkComponents.ndkHandlerSupplier.get()
                if (!ndkHandler.ndkPlatform.isConfigured) {
                    if (ndkHandler.userExplicitlyRequestedNdkVersion) {
                        try {
                            global.sdkComponents.installNdk(ndkHandler)
                        } catch (e: NumberFormatException) {
                            // This is likely a mis-formatted NDK version in build.gradle. Just issue
                            // an infoln because an appropriate errorln will have been emitted by
                            // NdkLocator.
                            infoln(
                                "NDK auto-download failed, possibly due to a malformed NDK version in " +
                                        "build.gradle. If manual download is necessary from SDK manager " +
                                        "then the preferred NDK version is " +
                                        "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION'."
                            )
                            return ndkHandler
                        }
                    } else {
                        // Don't auto-download if the user has not explicitly specified an NDK
                        // version in build.gradle. The default version may not be the one that
                        // the user prefers but he hasn't had a chance yet to set
                        // android.ndkVersion. We don't want to auto-download a massive NDK without
                        // confirmation that it's the right one.
                        infoln(
                            "NDK auto-download is disabled. To enable auto-download, " +
                                    "set an explicit version in build.gradle by setting " +
                                    "android.ndkVersion. The preferred NDK version is " +
                                    "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION'."
                        )
                    }
                    if (!ndkHandler.ndkPlatform.isConfigured) {
                        throw InvalidUserDataException(
                            "NDK not configured. Download " +
                                    "it with SDK manager. Preferred NDK version is " +
                                    "'$ANDROID_GRADLE_PLUGIN_FIXED_DEFAULT_NDK_VERSION'. "
                        )
                    }
                }
            return ndkHandler
        }
    }
    val ndkHandler = ndkHandler()
    if (!ndkHandler.ndkPlatform.isConfigured) {
        infoln("Not creating C/C++ model because NDK could not be configured.")
        return null
    }

    fun localPropertyFile(property : String) : File? {
        val path = gradleLocalProperties(global.project.rootDir)
            .getProperty(property) ?: return null
        return File(path)
    }
    return object : CxxModuleModel {
        override val moduleBuildFile by lazy { global.project.buildFile }
        override val cxxFolder get() = cxxFolder
        override val project by lazy { createCxxProjectModel(global) }
        override val services by lazy { createDefaultServiceRegistry(global) }
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
                            findCmakeVersion(
                                global.extension.externalNativeBuild.cmake.version)
                        }
                        private val cmakeFolder by lazy {
                            cmakeLocator.findCmakePath(
                                global.extension.externalNativeBuild.cmake.version,
                                localPropertyFile(CMAKE_DIR_PROPERTY),
                                global.sdkComponents.getSdkDirectory(),
                                Consumer { global.sdkComponents.installCmake(it) })!!
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
                ndkHandler.ndkPlatform.getOrThrow().ndkDirectory,
                cxxFolder,
                localPropertyFile(NDK_SYMLINK_DIR))
        }
        override val ndkVersion by lazy {
            ndkHandler.ndkPlatform.getOrThrow().revision
        }
        override val ndkSupportedAbiList by lazy {
            ndkHandler.ndkPlatform.getOrThrow().supportedAbis
        }
        override val ndkDefaultAbiList by lazy {
            ndkHandler.ndkPlatform.getOrThrow().defaultAbis
        }
        override val ndkDefaultStl: Stl by lazy {
            ndkHandler.ndkPlatform.getOrThrow().ndkInfo.getDefaultStl(buildSystem)
        }
        override val makeFile = makeFile
        override val buildSystem = buildSystem
        override val splitsAbiFilterSet by lazy {
            global.extension.splits.abiFilters
        }
        override val intermediatesFolder by lazy {
            global.intermediatesDir
        }
        override val gradleModulePathName by lazy {
            global.project.path
        }
        override val moduleRootFolder by lazy {
            global.project.projectDir
        }
        override val buildStagingFolder get() = buildStagingFolder
        override val stlSharedObjectMap by lazy {
            val map: MutableMap<Stl, Map<Abi, File>> = mutableMapOf()
            val ndkInfo = ndkHandler.ndkPlatform.getOrThrow().ndkInfo
            for (stl in ndkInfo.supportedStls) {
                map[stl] = ndkInfo.getStlSharedObjectFiles(stl, ndkInfo.supportedAbis)
            }
            map.toMap()
        }
    }
}

fun tryCreateCxxModuleModel(global : GlobalScope) = tryCreateCxxModuleModel(
    global,
    CmakeLocator()
)

/**
 * Resolve the CMake or ndk-build path and buildStagingDirectory of native build project.
 * - If there is exactly 1 path in the DSL, then use it.
 * - If there are more than 1, then that is an error. The user has specified both cmake and
 *   ndkBuild in the same project.
 */
private fun getProjectPath(config: CoreExternalNativeBuild)
        : Triple<NativeBuildSystem, File, File?>? {
    val externalProjectPaths = listOfNotNull(
        config.cmake.path?.let { Triple(CMAKE, it, config.cmake.buildStagingDirectory)},
        config.ndkBuild.path?.let { Triple(NDK_BUILD, it, config.ndkBuild.buildStagingDirectory) })

    return when {
        externalProjectPaths.size > 1 -> {
            errorln("More than one externalNativeBuild path specified")
            null
        }
        externalProjectPaths.isEmpty() -> {
            // No external projects present.
            null
        }
        else -> externalProjectPaths[0]
    }
}

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
            errorln("""
            The build staging directory you specified ('${buildStagingDirectory.absolutePath}')
            is a subdirectory of your project's temporary build directory (
            '${buildFolder.absolutePath}'). Files in this directory do not persist through clean
            builds. Either use the default build staging directory ('$defaultCxxFolder'), or
            specify a path outside the temporary build directory.""".trimIndent())
            defaultCxxFolder
        }
        else -> buildStagingDirectory
    }
}
