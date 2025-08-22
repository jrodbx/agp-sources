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

package com.android.build.gradle.internal.cxx.caching

/**
 * This method creates a brief human readable hint about what sort of build
 * produced a cache result. This should be in forward slash (/) folder-like
 * format because it will actually become part of the cache folder name in
 * Gradle's build cache directory.
 *
 * Example of a full cache subdirectory in Gradle cache folder:
 *
 *   cxx/hello-jni.o/object/armv7-androideabi16/-O0/995113ff
 *   ^   ^           ^      ^                   ^   ^
 *   |   |           |      |                   |   |
 *   |   |           |      |                   |   + Hash of clang flags
 *   |   |           |      |                   + Optimization level
 *   |   |           |      + Target ABI
 *   |   |           + Compiling source to object
 *   |   + Base name of the resulting object file
 *   + Caching for C/C++
 *
 * The part this function computes is 'armv7-androideabi16/-O0'
 *
 * The actual content of the result doesn't matter for the behavior of
 * caching as long as only information from clang [flags] is used. This is
 * because the caching logic will also append a full hash of [flags].
 */
fun computeClangKeySegment(flags : List<String>) : String {
    var target : String? = null
    var optimization : String? = null
    for(flag in flags) {
        if (flag.startsWith("--target=")) {
            target = flag
                .substringAfter("--target=")
                .replace("-none-linux-", "-")
        }
        if (flag.startsWith("-O")) {
            optimization = flag
        }
    }
    val sb = StringBuilder()
    if (target != null) sb.append("$target/")
    if (optimization != null) sb.append("$optimization/")
    return sb.toString()
}
