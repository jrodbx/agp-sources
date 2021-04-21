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

package com.android.build.api.dsl

import org.gradle.api.Incubating

@Incubating
interface ApplicationBuildType<SigningConfigT : ApkSigningConfig> :
    BuildType,
    ApplicationVariantDimension<SigningConfigT> {
    /** Whether this build type should generate a debuggable apk. */
    var isDebuggable: Boolean

    /**
     * Whether a linked Android Wear app should be embedded in variant using this build type.
     *
     * Wear apps can be linked with the following code:
     *
     * ```
     * dependencies {
     *     freeWearApp project(:wear:free') // applies to variant using the free flavor
     *     wearApp project(':wear:base') // applies to all other variants
     * }
     * ```
     */
    var isEmbedMicroApp: Boolean

    /**
     * Whether to crunch PNGs.
     *
     * Setting this property to `true` reduces of PNG resources that are not already
     * optimally compressed. However, this process increases build times.
     *
     * PNG crunching is enabled by default in the release build type and disabled by default in
     * the debug build type.
     */
    var isCrunchPngs: Boolean?

    /** Whether this product flavor should be selected in Studio by default  */
    var isDefault: Boolean
}
