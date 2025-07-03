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

package com.android.build.api.variant

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Build-time properties for [Component] that have Android resources.
 */
interface HasAndroidResources {

    /**
     * Make a [ResValue.Key] to interact with [resValues]'s [MapProperty]
     */
    fun makeResValueKey(type: String, name: String): ResValue.Key

    /**
     * Variant's [ResValue] which will be generated.
     */
    val resValues: MapProperty<ResValue.Key, ResValue>

    /**
     * Variant's is pseudo locales enabled, initialized by the corresponding DSL elements.
     */
    val pseudoLocalesEnabled: Property<Boolean>
}
