/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.NINJA_IS_MISSING
import com.android.utils.cxx.os.exe
import org.jetbrains.kotlin.com.google.common.annotations.VisibleForTesting
import java.io.File
import com.android.utils.cxx.os.getEnvironmentPaths

/**
 * Method for locating ninja.exe. The search order is:
 * 1) Folder of CMake.exe, if present
 * 2) Other CMake SDK folders, if present
 * 3) Environment PATH
 */
@VisibleForTesting
fun findNinjaPathLogic(
    cmakePath: File?,
    getSdkCmakeFolders: () -> List<File>,
    getEnvironmentPaths: () -> List<File>,
    getNinjaPathIfExists: (folder: File) -> File?
): File? {
    if (cmakePath != null) {
        getNinjaPathIfExists(cmakePath)?.let {
            return it
        }
    }

    for (sdkFolder in getSdkCmakeFolders()) {
        getNinjaPathIfExists(sdkFolder)?.let {
            return it
        }
    }

    for (environmentPath in getEnvironmentPaths()) {
        getNinjaPathIfExists(environmentPath)?.let {
            return it
        }
    }

    // Error if there is no match
    errorln(
        NINJA_IS_MISSING,
        "Could not find Ninja on PATH or in SDK CMake bin folders."
    )
    return null
}

/**
 * @return path to ninja.exe if it exists in the
 * given folder. Otherwise, null.
 */
private fun getNinjaIfPathExists(folder : File) : File? {
    val ninjaExe = folder.resolve("ninja$exe")
    return if (ninjaExe.isFile) ninjaExe else null
}

class NinjaLocator {
    fun findNinjaPath(
        cmakePath : File?,
        sdkFolder: File?
    ): File? {
        PassThroughRecordingLoggingEnvironment().use {
            return findNinjaPathLogic(
                cmakePath,
                { getSdkCmakeFolders(sdkFolder) },
                ::getEnvironmentPaths,
                ::getNinjaIfPathExists
            )
        }
    }
}
