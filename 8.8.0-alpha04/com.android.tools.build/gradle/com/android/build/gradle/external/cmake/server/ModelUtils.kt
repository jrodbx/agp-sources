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

package com.android.build.gradle.external.cmake.server

import com.android.build.gradle.internal.cxx.cmake.parseLinkLibraries
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.utils.cxx.CxxDiagnosticCode.CMAKE_SERVER_BUILD_DIRECTORY_MISSING
import java.io.File
import java.nio.file.Paths

/**
 * Given a CMake server [Target], extract the runtime files for it.
 * Return null if there are no [linkLibraries] or if this is an object library.
 */
fun Target.findRuntimeFiles(): List<File>? {

    if (linkLibraries == null) {
        return null
    }

    if (type == "OBJECT_LIBRARY") {
        return null
    }

    val sysroot = Paths.get(sysroot)
    val runtimeFiles = mutableListOf<File>()
    for (library in parseLinkLibraries(linkLibraries)) {
        // Each element here is just an argument to the linker. It might be a full path to a
        // library to be linked or a trivial -l flag. If it's a full path that exists within
        // the prefab directory, it's a library that's needed at runtime.
        if (library.startsWith("-")) {
            continue
        }

        // We don't actually care about the normalization here except that it makes it
        // possible to write a test for https://issuetracker.google.com/158317988. Without
        // it, the runtimeFile is sometimes a path that includes .. that resolves to the
        // same place as the destination, but sometimes isn't (within bazel's sandbox it is,
        // outside it isn't, could be related to the path lengths since CMake tries to keep
        // those short when possible). If the paths passed to Files.copy are equal the
        // operation will throw IllegalArgumentException, but only if they are exactly equal
        // (without normalization). Users were encountering this but it was being hidden
        // from tests because of the lack of normalization.
        val libraryPath = Paths.get(library).let {
            if (!it.isAbsolute) {
                if (buildDirectory == null) {
                    warnln(
                        CMAKE_SERVER_BUILD_DIRECTORY_MISSING,
                        "Could not resolve path to '$it' because CMake did not define build directory")
                    null
                } else Paths.get(buildDirectory).resolve(it)
            } else {
                it
            }
        }?.normalize() ?: continue

        // Note: This used to contain a check for libraryPath.exists() to defend against any
        // items in the linkLibraries that were neither files nor - prefixed arguments. This
        // hasn't been observed and I'm not sure there are any valid inputs to
        // target_link_libraries that would have that problem.
        //
        // Ignoring files that didn't exist was causing different results depending on
        // whether this function was being run before or after a build. If run before a
        // build, any libraries the user is building will not be present yet and would not
        // be added to runtimeFiles. After a build they would. We no longer skip non-present
        // files for the sake of consistency.

        // Anything under the sysroot shouldn't be included in the APK. This isn't strictly
        // true since the STLs live here, but those are handled separately by
        // ExternalNativeBuildTask::buildImpl.
        if (libraryPath.startsWith(sysroot)) {
            continue
        }

        // We could alternatively filter for .so files rather than filtering out .a files,
        // but it's possible that the user has things like libfoo.so.1. It's not common for
        // Android, but possible.
        val pathMatcher = libraryPath.fileSystem.getPathMatcher("glob:*.a")
        if (pathMatcher.matches(libraryPath.fileName)) {
            continue
        }

        runtimeFiles.add(libraryPath.toFile())
    }
    return runtimeFiles
}
