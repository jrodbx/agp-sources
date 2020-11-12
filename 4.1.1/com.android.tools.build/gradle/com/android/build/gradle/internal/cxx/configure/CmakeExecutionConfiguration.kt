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

/**
 * Represents the information needed to invoke CMake. This is used by compiler cache to
 * represent the new flags and CMakeLists.txt that should be used to wrap a user's build.
 */
data class CmakeExecutionConfiguration(
    /*
    This is a path to the folder that contains CMakeLists.txt or to the wrapping CMakeLists.txt
    that calls user's CMakeLists.txt. This flag needs to be separate from args (below) because the
    CMake Server doesn't use -H argument to pass in this location.
     */
    val cmakeListsFolder: File,

    /*
    The command-line or CMake Server arguments.
     */
    val args: List<String>
)