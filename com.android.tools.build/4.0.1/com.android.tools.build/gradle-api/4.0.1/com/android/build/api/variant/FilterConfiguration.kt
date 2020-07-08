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

import org.gradle.api.Incubating
import org.gradle.api.tasks.Input

/**
 * Immutable filter configuration.
 */
@Incubating
data class FilterConfiguration(

    /**
     * Returns the [FilterType] for this filter configuration.
     */
    @get:Input
    val filterType: FilterType,

    /**
     * Returns the identifier for this filter. The filter identifier is
     * dependent on the [FilterConfiguration.filterType].
     */
    @get:Input
    val identifier: String) {

    /** Split dimension type  */
    @Incubating
    enum class FilterType {
        DENSITY,
        ABI,
        LANGUAGE
    }
}