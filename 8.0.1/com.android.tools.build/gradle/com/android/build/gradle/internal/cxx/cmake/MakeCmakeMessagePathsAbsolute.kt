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

package com.android.build.gradle.internal.cxx.cmake

import com.android.utils.FileUtils.join
import java.io.File
import java.io.StringReader

private val pattern = "^(.*CMake (Error|Warning).* at\\s+)([^:]+)(:.*)$".toRegex()

/**
 * CMake output contains paths relative to the CMakeLists.txt folder. However, this is
 * not the folder that Android Studio users need to see. They need an absolute path or
 * a path relative to the project root.
 *
 * This function remaps path names so that they are absolute.
 *
 * An example error message from CMake:
 *
 *   CMake Error at CMakeLists.txt:123:456. We had a reactor leak here now. Give us a
 *     few minutes to lock it down. Large leak, very dangerous.
 *
 * This should be corrected to:
 *
 *   CMake Error at /path/to/CMakeLists.txt:123:456. We had a reactor leak...
 *
 * TODO(jomof) this string could be very large. This function should accept and return a sequence of lines
 */
fun makeCmakeMessagePathsAbsolute(cmakeOutput: String, makeFileDirectory: File): String {
    return StringReader(cmakeOutput).readLines().joinToString(System.lineSeparator()) { line ->
        val match = pattern.matchEntire(line)
        if (match == null) {
            line
        } else {
            val type = match.groupValues[1]
            val makeFileName = match.groupValues[3]
            val message = match.groupValues[4]
            if (File(makeFileName).isAbsolute) {
                // No need to update absolute paths.
                line
            } else {
                val resolved = join(makeFileDirectory, makeFileName)
                if (!resolved.isFile) {
                    line
                } else {
                    "$type${resolved.absolutePath}$message"
                }
            }
        }
    }
}
