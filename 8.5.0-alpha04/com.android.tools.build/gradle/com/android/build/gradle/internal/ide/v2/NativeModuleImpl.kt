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

package com.android.build.gradle.internal.ide.v2

import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeVariant
import java.io.File
import java.io.Serializable

data class NativeModuleImpl(
    override val name: String,
    override val variants: List<NativeVariant>,
    override val nativeBuildSystem: NativeBuildSystem,
    override val ndkVersion: String,
    override val externalNativeBuildFile: File
) : NativeModule, Serializable {
    override val defaultNdkVersion: String get() = NDK_DEFAULT_VERSION
}
