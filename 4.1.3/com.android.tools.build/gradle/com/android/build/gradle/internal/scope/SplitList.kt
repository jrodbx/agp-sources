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

package com.android.build.gradle.internal.scope

import com.android.build.VariantOutput.FilterType
import com.android.build.gradle.internal.variant.MultiOutputPolicy
import com.google.common.collect.ImmutableSet
import org.gradle.api.tasks.Input

/**
 * Singleton object per variant that holds the list of splits declared by the DSL.
 */
data class SplitList(
    @get:Input val densityFilters: ImmutableSet<String> = ImmutableSet.of(),
    @get:Input val languageFilters: ImmutableSet<String> = ImmutableSet.of(),
    @get:Input val abiFilters: ImmutableSet<String> = ImmutableSet.of(),
    @get:Input val resourceConfigs: ImmutableSet<String> = ImmutableSet.of()) {

    interface SplitAction {
        fun apply(filterType: FilterType , filters: ImmutableSet<String>)
    }

    fun forEach(action: SplitAction) {
        action.apply(FilterType.DENSITY, densityFilters)
        action.apply(FilterType.LANGUAGE, languageFilters)
        action.apply(FilterType.ABI, abiFilters)
    }

    fun getSplits(multiOutputPolicy: MultiOutputPolicy): Set<String> {
        return emptySet()
    }
}
