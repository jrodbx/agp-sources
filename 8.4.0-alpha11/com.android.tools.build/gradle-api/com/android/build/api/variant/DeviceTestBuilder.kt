/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating

@Incubating
interface DeviceTestBuilder {

    /**
     * Set to `true` if the variant's has any device tests, false otherwise.
     * Value is [Boolean#True] by default.
     */
    @get: Incubating
    @set: Incubating
    var enable: Boolean

    /**
     * Sets whether multi-dex is enabled for this device test configuration.
     *
     * To get the final value, use the [AndroidComponentsExtension.onVariants] API :
     * ```kotlin
     * onVariants { variant ->
     *   variant.dexing.isMultiDexEnabled
     * }
     * ```
     * @param value set to `true` to enable multidex for this device test configuration, `false`
     * otherwise.{
     */
    @Incubating
    fun setEnableMultiDex(value: Boolean)
}
