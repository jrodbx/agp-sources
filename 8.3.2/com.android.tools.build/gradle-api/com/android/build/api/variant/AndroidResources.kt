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
import org.gradle.api.provider.ListProperty

/**
 * Build-time properties for Android Resources inside a [Component].
 *
 * This is accessed via [GeneratesApk.androidResources]
 */
interface AndroidResources {

    /**
     * The list of patterns describing assets to be ignored.
     *
     * See aapt's --ignore-assets flag via `aapt --help`. Note: the --ignore-assets flag accepts a
     * single string of colon-delimited patterns, whereas this property is a list of patterns.
     *
     * This property will be initialized using the corresponding DSL value.
     */
    val ignoreAssetsPatterns: ListProperty<String>

    /**
     * The list of additional parameters to pass to aapt.
     *
     * This property will be initialized using the corresponding DSL value.
     */
    val aaptAdditionalParameters: ListProperty<String>

    /**
     * File extensions of Android resources, assets, and Java resources to be stored uncompressed in
     * the APK. Adding an empty extension (e.g., `noCompress.add("")`) will disable compression for
     * all Android resources, assets, and Java resources.
     */
    @get:Incubating
    val noCompress: ListProperty<String>
}
