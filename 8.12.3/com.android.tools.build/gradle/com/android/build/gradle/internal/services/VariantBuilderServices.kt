/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.services

/**
 * Services for the [com.android.build.api.variant.VariantBuilder] API objects.
 *
 * This contains whatever is needed by all the variant objects.
 *
 * This is meant to be used only by the variant api objects. Other stages of the plugin
 * will use different services objects.
 */
interface VariantBuilderServices:
    BaseServices {

    /**
     * Instantiate a [Value] object that wraps a basic type. This offers read/write locking as
     * needed by the lifecycle of Variant API objects:
     * - during API actions, [Value.get] is disabled
     * - afterward, [Value.set] is disabled and [Value.get] is turned on
     *   (so that AGP can read the value).
     */
    fun <T> valueOf(value: T): Value<T>

    /**
     * Locks the [Value] object.
     *
     * This disables [Value.set] while enabling [Value.get]
     */
    fun lockValues()

    val isPostVariantApi: Boolean

    /**
     * A value wrapper
     */
    interface Value<T> {
        fun set(value: T)
        fun get(): T
    }
}
