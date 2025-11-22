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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for configuring Android resource options for Application plugins.
 *
 * This is accessed via [ApplicationExtension.androidResources]
 */
interface ApplicationAndroidResources : AndroidResources {
    /**
     * Property that automatically generates locale config when enabled.
     */
    @get:Incubating
    @set:Incubating
    var generateLocaleConfig: Boolean

    /**
     * Specifies a list of locales that resources will be kept for.
     *
     * For example, if you are using a library that includes locale-specific resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `localeFilters` property, as shown in the sample
     * below. Any resources for locales not specified are not included in the build.
     *
     * ````
     * android {
     *     androidResources {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         localeFilters += listOf("en-rGB", "fr")
     *     }
     * }
     * ````
     *
     * The locale must be specified either as (1) a two-letter ISO 639-1 language code, with the
     * option to add a two-letter ISO 3166-1-alpha-2 region code preceded by "-r" (e.g. en-rUS), or
     * (2) a BCP-47 language tag, which additionally allows you to specify a script subtag
     * (e.g. b+sr+Latn+RS).
     *
     * For more information on formulating locale qualifiers, see
     * "Language, script (optional), and region (optional)" in the
     * [alternative resources](https://d.android.com/r/tools/alternative-resources) table.
     */
    @get:Incubating
    val localeFilters: MutableSet<String>
}
