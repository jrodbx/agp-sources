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
 * The name of the default configuration subfolder leaf. It should start with dot (.) to indicate
 * that the folder contents shouldn't be checked in to source control.
 */
const val CXX_DEFAULT_CONFIGURATION_SUBFOLDER = ".cxx"

/**
 * The name of the property in local.settings to control the C++ build caching folder.
 */
const val CXX_LOCAL_PROPERTIES_CACHE_DIR = "cxx.cache.dir"

/**
 * The name of the compiler settings cache leaf folder.
 */
const val CXX_CMAKE_COMPILER_SETTINGS_CACHE_SUBFOLDER = "cmake-compiler-settings"