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

/**
 * Configuration for a given build output.
 *
 * This only applies to APKs as AARs and Bundles (AABs) do not support multiple outputs.
 *
 * See https://developer.android.com/studio/build/configure-apk-splits.html for more information
 * on multiple APK support.
 *
 * @see com.android.build.api.dsl.Splits
 */
interface VariantOutputConfiguration {
    /**
     * The type of an output.
     *
     * When support for Splits/Multi-APKs is turned on, the output will change from one
     * [SINGLE] output to multiple [ONE_OF_MANY] outputs, each with different [filters] values.
     *
     * In addition, if universal mode is on, then an additional [OutputType.UNIVERSAL] output can be
     * found in the list.
     */
    enum class OutputType {
        /**
         * Type for single output. This is used when splits/multi-APK support is not turned on
         */
        SINGLE,

        /**
         * Type of multiple output. This type will be used by multiple outputs and each can be
         * differentiated with [VariantOutputConfiguration.filters]
         */
        ONE_OF_MANY,

        /**
         * Output for Universal APK. This is only valid if Splits/Multi-APK is on and universal
         * mode was turned on (see [com.android.build.api.dsl.AbiSplit.isUniversalApk])
         */
        UNIVERSAL
    }

    /**
     * The [OutputType] for this particular output
     */
    val outputType: OutputType

    /**
     * The list of [FilterConfiguration] for this output.
     *
     * If the list is empty this means there is no filter associated to this output. This is
     * typically the case for [OutputType.SINGLE]
     */
    val filters: Collection<FilterConfiguration>
}
