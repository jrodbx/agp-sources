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

package com.android.build.gradle.internal.ndk

enum class Stl(val argumentName: String, val requiresPackaging: Boolean, val libraryName: String?) {
    GNUSTL_SHARED(
        argumentName = "gnustl_shared",
        requiresPackaging = true,
        libraryName = "libgnustl_shared.so"
    ),

    GNUSTL_STATIC(
        argumentName = "gnustl_static",
        requiresPackaging = false,
        libraryName = "libgnustl_static.a"
    ),

    LIBCXX_SHARED(
        argumentName = "c++_shared",
        requiresPackaging = true,
        libraryName = "libc++_shared.so"
    ),

    LIBCXX_STATIC(
        argumentName = "c++_static",
        requiresPackaging = false,
        libraryName = "libc++_static.a"
    ),

    NONE(
        argumentName = "none",
        requiresPackaging = false,
        libraryName = null
    ),

    STLPORT_SHARED(
        argumentName = "stlport_shared",
        requiresPackaging = true,
        libraryName = "libstlport_shared.so"
    ),

    STLPORT_STATIC(
        argumentName = "stlport_static",
        requiresPackaging = false,
        libraryName = "libstlport_static.a"
    ),

    SYSTEM(
        argumentName = "system",
        requiresPackaging = false,
        libraryName = "libstdc++.so"
    ),

    // UNKNOWN is for cases where the NDK does not know or decide which STL is used.
    UNKNOWN(
        argumentName = "unknown",
        requiresPackaging = false,
        libraryName = null
    );

    companion object {
        @JvmStatic fun fromArgumentName(name: String) = values().find { it.argumentName == name }
    }
}
