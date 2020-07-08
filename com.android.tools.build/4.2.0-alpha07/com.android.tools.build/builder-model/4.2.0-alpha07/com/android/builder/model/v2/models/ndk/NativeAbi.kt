/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.builder.model.v2.models.ndk

import java.io.File
import java.io.Serializable

/** 
 * Response returned by Gradle to Android Studio containing information about a native ABI under a
 * module and variant. 
 */
interface NativeAbi : Serializable {
    /**
     * The ABI name. This value aligns with [com.android.build.gradle.internal.core.Abi.tag]. For
     * example, "x86_64", "arm64-v8a".
     */
    val name: String

    /**
     * Standard compile command file in JSON format that contains the compiler commands.
     *
     * See https://clang.llvm.org/docs/JSONCompilationDatabase.html for details of the format.
     *
     * This file is generated if requested in [NativeModelBuilderParameter].
     */
    val compileCommandsJsonFile: File

    /**
     * Text file containing a list of folders that contains shared libraries used by the APK.
     *
     * This file is generated if requested in [NativeModelBuilderParameter].
     */
    val symbolFolderIndexFile: File

    /**
     * Text file containing a list of build files for the native build system used by this projects.
     *
     * For example, if CMake is used, this file contains a list of CMakeLists.txt used by the
     * project.
     *
     * This file is generated if requested in [NativeModelBuilderParameter].
     */
    val buildFileIndexFile: File
}
