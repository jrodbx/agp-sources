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
 */
interface VariantOutput: VariantOutputConfiguration {

    /**
     * Returns a modifiable [Property] representing the variant output version code.
     *
     * This will be initialized with the variant's merged flavor value or read from the manifest
     * file if unset.
     */
    val versionCode: Property<Int?>

    /**
     * Returns a modifiable [Property] representing the variant output version name.
     *
     * This will be initialized with the variant's merged flavor value, or it will be read from the
     * manifest source file if it's not set via the DSL, or it will be null if it's also not set in
     * the manifest.
     */
    val versionName: Property<String?>

    /**
     * Returns a modifiable [Property] to enable or disable the production of this [VariantOutput]
     *
     * @return a [Property] to enable or disable this output.
     */
    val enabled: Property<Boolean>

    /**
     * Returns a modifiable [Property] to enable or disable the production of this [VariantOutput]
     *
     * @return a [Property] to enable or disable this output.
     */
    @Deprecated("Replaced by enabled", ReplaceWith("enabled"))
    val enable: Property<Boolean>
}
