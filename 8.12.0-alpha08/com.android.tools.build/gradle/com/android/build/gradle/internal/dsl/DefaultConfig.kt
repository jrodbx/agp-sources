/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.DynamicFeatureDefaultConfig
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.TestDefaultConfig
import com.android.build.gradle.internal.services.DslServices
import com.android.resources.Density
import com.google.common.collect.Sets
import javax.inject.Inject

/** DSL object for the defaultConfig object.  */
// Exposed in the DSL.
abstract class DefaultConfig @Inject constructor(name: String, dslServices: DslServices) :
    BaseFlavor(name, dslServices),
    ApplicationDefaultConfig,
    DynamicFeatureDefaultConfig,
    LibraryDefaultConfig,
    TestDefaultConfig {

    init {
        val densities = Density.getRecommendedValuesForDevice()
        val strings: MutableSet<String> =
            Sets.newHashSetWithExpectedSize(densities.size)
        for (density in densities) {
            strings.add(density.resourceValue)
        }
        vectorDrawables.setGeneratedDensities(strings)
        vectorDrawables.useSupportLibrary = false
    }
}
