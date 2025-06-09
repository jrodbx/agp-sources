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

package com.android.build.gradle.internal.cxx.configure

/**
 * A subset of possible ndk-build command-line properties.
 */
enum class NdkBuildProperty {
    APP_ABI,
    APP_BUILD_SCRIPT,
    APP_CFLAGS,
    APP_CPPFLAGS,
    APP_PLATFORM,
    APP_SHORT_COMMANDS,
    APP_STL,
    LOCAL_SHORT_COMMANDS,
    NDK_ALL_ABIS,
    NDK_APPLICATION_MK,
    NDK_DEBUG,
    NDK_GRADLE_INJECTED_IMPORT_PATH,
    NDK_LIBS_OUT,
    NDK_OUT,
    NDK_PROJECT_PATH
}
