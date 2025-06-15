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

/**
 * A subset of possible CMake properties.
 */
enum class CmakeProperty {
    ANDROID_ABI,
    ANDROID_NDK,
    ANDROID_PLATFORM,
    ANDROID_STL,
    C_TEST_WAS_RUN,
    CMAKE_ANDROID_ARCH_ABI,
    CMAKE_ANDROID_NDK,
    CMAKE_BUILD_TYPE,
    CMAKE_C11_COMPILE_FEATURES,
    CMAKE_C90_COMPILE_FEATURES,
    CMAKE_C99_COMPILE_FEATURES,
    CMAKE_C_ABI_COMPILED,
    CMAKE_C_COMPILE_FEATURES,
    CMAKE_C_COMPILER_FORCED,
    CMAKE_C_FLAGS,
    CMAKE_C_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_C_IMPLICIT_LINK_LIBRARIES,
    CMAKE_C_SIZEOF_DATA_PTR,
    CMAKE_C_STANDARD_DEFAULT,
    CMAKE_CXX11_COMPILE_FEATURES,
    CMAKE_CXX14_COMPILE_FEATURES,
    CMAKE_CXX17_COMPILE_FEATURES,
    CMAKE_CXX98_COMPILE_FEATURES,
    CMAKE_CXX_ABI_COMPILED,
    CMAKE_CXX_COMPILE_FEATURES,
    CMAKE_CXX_COMPILER_ABI,
    CMAKE_CXX_COMPILER_FORCED,
    CMAKE_CXX_FLAGS,
    CMAKE_CXX_IMPLICIT_LINK_DIRECTORIES,
    CMAKE_CXX_IMPLICIT_LINK_LIBRARIES,
    CMAKE_CXX_SIZEOF_DATA_PTR,
    CMAKE_CXX_STANDARD_DEFAULT,
    CMAKE_EXPORT_COMPILE_COMMANDS,
    CMAKE_FIND_ROOT_PATH,
    CMAKE_LIBRARY_OUTPUT_DIRECTORY,
    CMAKE_LINKER,
    CMAKE_MAKE_PROGRAM,
    CMAKE_RUNTIME_OUTPUT_DIRECTORY,
    CMAKE_SIZEOF_VOID_P,
    CMAKE_SYSTEM_NAME,
    CMAKE_SYSTEM_VERSION,
    CMAKE_TOOLCHAIN_FILE,
    CMAKE_VERBOSE_MAKEFILE
}
