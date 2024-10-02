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

import com.android.build.gradle.tasks.NativeBuildSystem
import java.io.File

/**
 * NdkInfo for r17.
 */
open class NdkR17Info(root: File) : NdkR14Info(root) {
    override fun getDefaultStl(buildSystem: NativeBuildSystem): Stl = when (buildSystem) {
        NativeBuildSystem.CMAKE -> Stl.LIBCXX_STATIC
        NativeBuildSystem.NDK_BUILD -> Stl.SYSTEM
        else -> error("$buildSystem")
    }
}
