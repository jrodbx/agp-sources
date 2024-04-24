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

package com.android.build.api.dsl

/**
 * DSL object for configuring per-density splits options.
 *
 * See [APK Splits](https://developer.android.com/studio/build/configure-apk-splits.html).
 */
@Deprecated(
    "Density-based apk split feature is deprecated and will be removed in AGP 9.0." +
            "Use Android App Bundle (https://developer.android.com/guide/app-bundle)" +
            "to generate optimized APKs."
)
interface DensitySplit : Split {
    /** TODO: Document. */
    var isStrict: Boolean

    /**
     * A list of compatible screens.
     *
     * This will inject a matching `<compatible-screens><screen ...>` node in the manifest.
     * This is optional.
     */
    val compatibleScreens: MutableSet<String>

    /** Adds a new compatible screen. */
    fun compatibleScreens(vararg sizes: String)
}
