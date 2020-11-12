/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_CMAKE
import com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_GRADLE
import com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_NDK_BUILD
import com.android.builder.model.NativeAndroidProject.BUILD_SYSTEM_UNKNOWN

/**
 * Enumeration and descriptive metadata for the different external native build system types.
 * The variable "name" is already taken in kotlin enums
 */
enum class NativeBuildSystem(val tag : String) {
    CMAKE(BUILD_SYSTEM_CMAKE),
    NDK_BUILD(BUILD_SYSTEM_NDK_BUILD);
}
