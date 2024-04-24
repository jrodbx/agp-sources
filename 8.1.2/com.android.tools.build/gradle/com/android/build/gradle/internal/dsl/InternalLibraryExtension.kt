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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.LibraryAndroidResources
import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.LibraryProductFlavor
import com.android.build.api.dsl.LibraryPublishing
import org.gradle.api.Action

/** See [InternalCommonExtension] */
interface InternalLibraryExtension :
    LibraryExtension,
    InternalTestedExtension<
            LibraryBuildFeatures,
            LibraryBuildType,
            LibraryDefaultConfig,
            LibraryProductFlavor,
            LibraryAndroidResources> {

    override var aidlPackagedList: MutableCollection<String>
    fun publishing(action: Action<LibraryPublishing>)
}
