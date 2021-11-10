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

interface VariantOutputConfiguration {
    /**
     * Type of package file, either the main APK or a full split APK file containing resources for a
     * particular split dimension.
     */
    enum class OutputType {
        SINGLE,
        ONE_OF_MANY,
        UNIVERSAL
    }

    /**
     * Returns the output type of the referenced APK.
     *
     * @return the [OutputType] for this APK
     */
    val outputType: OutputType

    /**
     * Returns a possibly empty list of [FilterConfiguration] for this output. If the list is empty,
     * this means there is no filter associated to this output.
     *
     * @return list of [FilterConfiguration] for this output.
     */
    val filters: Collection<FilterConfiguration>
}
