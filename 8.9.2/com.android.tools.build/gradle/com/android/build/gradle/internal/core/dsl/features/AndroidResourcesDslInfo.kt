/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.features

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.variant.ResValue
import com.android.builder.model.VectorDrawablesOptions
import com.google.common.collect.ImmutableSet

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by components that support AndroidResources.
 */
interface AndroidResourcesDslInfo {
    val androidResources: AndroidResources

    val resourceConfigurations: ImmutableSet<String>

    val vectorDrawables: VectorDrawablesOptions

    val isPseudoLocalesEnabled: Boolean

    val isCrunchPngs: Boolean?

    @Deprecated("Can be removed once the AaptOptions crunch method is removed.")
    val isCrunchPngsDefault: Boolean

    /**
     * Returns a list of generated resource values.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    fun getResValues(): Map<ResValue.Key, ResValue>
}
