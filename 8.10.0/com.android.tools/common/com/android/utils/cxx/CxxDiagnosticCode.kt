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
    RESERVED_FOR_TESTS(1001),

    // NDK errors
    NDK_IS_AMBIGUOUS(1100),
    NDK_CORRUPTED(1101),
    NDK_IS_INVALID(1102),
    NDK_VERSION_IS_INVALID(1103),
    NDK_VERSION_IS_UNMATCHED(1104),
    NDK_VERSION_UNSUPPORTED(1105),
    NDK_DIR_IS_DEPRECATED(1106),
    NDK_FEATURE_NOT_SUPPORTED_FOR_VERSION(1107),
    NDK_DOES_NOT_SUPPORT_API_LEVEL(1108),
    NDK_SYMLINK_FAILED(1109),
    NDK_MIN_SDK_VERSION_TOO_LOW(1110),
    NDK_SUPPRESS_MIN_SDK_ERROR_NOT_INT(1111),

    // ABI errors
    ABI_IS_INVALID(1200),
    ABI_IS_UNSUPPORTED(1201),
    ABI_HAS_ONLY_32_BIT_SUPPORT(1202),

    // Prefab errors
    // No compatible library found for //pkg1/lib1.
    PREFAB_NO_LIBRARY_FOUND(1210),
    // From Prefab: "Library is a shared library with a statically linked STL and cannot be used
    //               with any library using the STL"
    PREFAB_SINGLE_STL_VIOLATION_LIBRARY_IS_SHARED_WITH_STATIC_STL(1211),
    // From Prefab: "User is using a static STL but library requires a shared STL"
    PREFAB_SINGLE_STL_VIOLATION_LIBRARY_REQUIRES_SHARED_STL(1212),
    // From Prefab: "User requested libstdc++ but library requires libc++"
    PREFAB_MISMATCHED_STL_TYPE(1213),
    // From Prefab: "User has minSdkVersion 5 but library was built for 28"
    PREFAB_MISMATCHED_MIN_SDK_VERSION(1214),
    // From Prefab: "Duplicate module name found (//pkg1/lib1 and //pkg2/lib1). ndk-build does
    //               not support fully qualified module names."
    PREFAB_DUPLICATE_MODULE_NAME(1215),
    // From Prefab: "//pkg1/lib2 contains artifacts for an unsupported platform "windows""
    PREFAB_UNSUPPORTED_PLATFORM(1216),
    // From Prefab: "Only schema_version 1 is supported. pkg1 uses version 2."
    PREFAB_MISMATCHED_SCHEMA(1217),
    // From Prefab: "Unexpected JSON token at offset 87: Encountered an unknown key 'static'."
    PREFAB_JSON_FORMAT_PROBLEM(1218),
    // From Prefab 1.1.3: "Prebuilt directory does not contain lib1.a or lib1.so"
    PREFAB_PREBUILTS_MISSING(1219),
    // Prefab failures that would likely indicate a bug in AGP
    PREFAB_FATAL(1220),
    PREFAB_GRADLE_VERSION_NOT_COMPATIBLE_WITH_PREFAB(1221),

    // CMake errors
    CMAKE_IS_MISSING(1300),
    CMAKE_VERSION_IS_INVALID(1301),
    CMAKE_VERSION_IS_UNSUPPORTED(1302),
    CMAKE_FEATURE_NOT_SUPPORTED_FOR_VERSION(1303),
    CMAKE_PACKAGES_SDK(1304),
    CMAKE_SERVER_BUILD_DIRECTORY_MISSING(1305),
    CMAKE_SERVER_INTERACTIVE_ERROR(1306),
    CMAKE_SERVER_SOURCE_DIRECTORY_MISSING(1307),

    // Build config errors
    INVALID_EXTERNAL_NATIVE_BUILD_CONFIG(1400),
    INVALID_COMPILER_SWITCH(1401),
    EXTRA_OUTPUT(1402),
    NO_STL_FOUND(1403),
    REQUIRED_BUILD_TARGETS_MISSING(1404),
    METADATA_GENERATION_FAILURE(1405),
    OBJECT_FILE_CANT_BE_CONVERTED_TO_TARGET_NAME(1406),
    COULD_NOT_EXTRACT_OUTPUT_FILE_FROM_CLANG_COMMAND(1407),
    COULD_NOT_CANONICALIZE_PATH(1408),
    BUILD_FILE_DID_NOT_EXIST(1409),
    BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST(1410),
    BUILD_TARGET_COMMAND_COMPONENTS_COMMAND_DID_NOT_EXIST(1411),
    LIBRARY_ARTIFACT_NAME_DID_NOT_EXIST(1412),
    LIBRARY_ABI_NAME_DID_NOT_EXIST(1413),
    LIBRARY_HAD_MULTIPLE_ABIS(1414),
    LIBRARY_ABI_NAME_IS_INVALID(1415),
    NINJA_IS_MISSING(1416),
    BUILD_NINJA_NOT_GENERATED(1417),
    NINJA_CONFIGURE_SCRIPT_MISSING(1418),
    NINJA_CONFIGURE_INVALID_ARGUMENTS(1419),
    FINGER_PRINT_FILE_CORRUPTED(1420),
    NINJA_GENERIC_ERROR(1421),
    BUILD_OUTPUT_LEVEL_NOT_SUPPORTED(1422),
    BUILD_SETTINGS_JSON_EMPTY(1423),
    BUILD_SETTINGS_GENERIC(1424),
    BUILD_SETTINGS_PARSE_ERROR(1425),
    BUILD_SETTINGS_MACRO_EXPANSION_DEPTH_LIMIT(1426),
    CONFIGURE_MORE_THAN_ONE_SO_FOLDER(1427),
    METADATA_GENERATION_GRADLE_EXCEPTION(1428),
    METADATA_GENERATION_PROCESS_FAILURE(1429),

    // Build cache config messages
    BUILD_CACHE_DISABLED_ACCESS(1500);

    val warningCode: Int get() = errorCode + 4000
}

/**
 * These are messages issued when a specific bug manifests.
 * The error code should be the bug number and bugNumber should not coincide with any values in
 * [CxxDiagnosticCode].
 */
enum class CxxBugDiagnosticCode(val bugNumber: Int)  {
    CMAKE_SERVER_HANDSHAKE_FAILED(194020297),
    NINJA_BUILD_SCRIPT_AUTHOR_FEEDBACK(213607318),
    CONFIGURE_INVALIDATION_STATE_RACE(255965912);
}
