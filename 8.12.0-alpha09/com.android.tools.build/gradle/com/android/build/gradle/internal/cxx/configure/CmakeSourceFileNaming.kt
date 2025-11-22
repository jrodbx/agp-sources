/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.io.File
import java.util.Locale

// The file extensions CMake recognizes as header files.
private val cmakeHeaderFileExtensions = setOf("h", "hh", "h++", "hm", "hpp")

/**
 * Return true if the given file has an extension recognized by CMake as being a header file.
 */
fun hasCmakeHeaderFileExtensions(file : File) : Boolean {
    return cmakeHeaderFileExtensions.contains(
        file.path.substringAfterLast(".").lowercase(Locale.US)
    )
}
