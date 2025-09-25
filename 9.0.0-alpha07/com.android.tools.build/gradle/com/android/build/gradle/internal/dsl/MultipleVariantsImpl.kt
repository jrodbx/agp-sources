/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.api.dsl.MultipleVariants
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

abstract class MultipleVariantsImpl @Inject constructor(
    dslServices: DslServices,
    val componentName: String,
) : MultipleVariants, PublishingOptionsImpl() {

    internal abstract var allVariants: Boolean
    internal abstract var includedBuildTypes: MutableSet<String>
    internal val includedFlavorDimensionAndValues: MutableMap<String, Set<String>> = mutableMapOf()

    override fun allVariants() {
        allVariants = true
    }

    override fun includeBuildTypeValues(vararg buildTypes: String) {
        this.includedBuildTypes.addAll(buildTypes)
    }

    override fun includeFlavorDimensionAndValues(dimension: String, vararg values: String) {
        this.includedFlavorDimensionAndValues[dimension] = mutableSetOf<String>().also { it.addAll(values) }
    }
}
