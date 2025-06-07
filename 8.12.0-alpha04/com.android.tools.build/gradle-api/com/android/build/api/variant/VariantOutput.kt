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

package com.android.build.api.variant

import org.gradle.api.provider.Property

/**
 * Defines a variant output.
 *
 * This only applies to APKs as AARs and Bundles (AABs) do not support multiple outputs.
 *
 * See https://developer.android.com/studio/build/configure-apk-splits.html for more information
 * on multiple APK support.
 *
 * @see com.android.build.api.dsl.Splits
 */
interface VariantOutput: VariantOutputConfiguration {

    /**
     * The version code for this output.

     * This will be initialized with the variant's merged flavor value or read from the manifest
     * file if unset.
     *
     * It is safe to modify it. When using Splits/Multi-APK output, it is generally necessary to
     * change this value per output.
     */
    val versionCode: Property<Int>

    /**
     * The version name for this output.
     *
     * This will be initialized with the variant's merged flavor value, or it will be read from the
     * manifest source file if it's not set via the DSL, or it will be null if it's also not set in
     * the manifest.
     *
     * It is safe to modify it.
     */
    val versionName: Property<String>

    /**
     * Flag controlling whether the output is enabled.
     *
     * It is safe to change the value in case a specific output should be disabled
     */
    val enabled: Property<Boolean>

    /**
     * Flag controlling whether the output is enabled.
     *
     * It is safe to change the value in case a specific output should be disabled
     */
    @Deprecated("Replaced by enabled", ReplaceWith("enabled"))
    val enable: Property<Boolean>
}
