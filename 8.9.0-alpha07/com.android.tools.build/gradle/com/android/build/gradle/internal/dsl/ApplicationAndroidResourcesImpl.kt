/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.gradle.internal.services.DslServices
import com.google.common.collect.Iterables
import javax.inject.Inject

abstract class ApplicationAndroidResourcesImpl @Inject constructor(
    dslServices: DslServices
) : ApplicationAndroidResources, AaptOptions(dslServices) {

    // TODO (b/269504885): set to true after testing the feature, add AUA details
    override var generateLocaleConfig: Boolean = false
    override val localeFilters: MutableSet<String> = mutableSetOf()

    fun setLocaleFilters(newContents: Iterable<String>) {
        val newArray = Iterables.toArray(newContents, String::class.java)
        localeFilters.clear()
        localeFilters.addAll(newArray)
    }
}
