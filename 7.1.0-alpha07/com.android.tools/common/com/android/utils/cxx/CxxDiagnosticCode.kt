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
package com.android.utils.cxx

enum class CxxDiagnosticCode(val errorCode: Int) {
    GENERIC(1000),

    // NDK errors
    NDK_IS_AMBIGUOUS(1100),
    NDK_CORRUPTED(1101),
    NDK_IS_INVALID(1102),
    NDK_VERSION_IS_INVALID(1103),
    NDK_VERSION_IS_UNMATCHED(1104),
    NDK_VERSION_UNSUPPORTED(1105),
    NDK_DIR_IS_DEPRECATED(1106),
    NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION(1107),

    // ABI errors
    ABI_IS_INVALID(1200),
    ABI_IS_UNSUPPORTED(1201),
    ABI_HAS_ONLY_32_BIT_SUPPORT(1202),

    // CMake errors
    CMAKE_IS_MISSING(1300),
    CMAKE_VERSION_IS_INVALID(1301),
    CMAKE_VERSION_IS_UNSUPPORTED(1302),
    CMAKE_FEATURE_NOT_SUPPORTED_FOR_VERSION(1303),
    CMAKE_PACKAGES_SDK(1304),
    CMAKE_SERVER_BUILD_DIRECTORY_MISSING(1305),

    // Build config errors
    INVALID_EXTERNAL_NATIVE_BUILD_CONFIG(1400),
    INVALID_COMPILER_SWITCH(1401),
    EXTRA_OUTPUT(1402),
    NO_STL_FOUND(1403),
    REQUIRED_BUILD_TARGETS_MISSING(1404),
    METADATA_GENERATION_FAILURE(1405),
    OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME(1406),
    COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND(1407),

    // Build cache config messages
    BUILD_CACHE_DISABLED_ACCESS(1500);

    val warningCode: Int get() = errorCode + 4000
}
