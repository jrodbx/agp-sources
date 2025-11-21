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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.provider.SetProperty

/**
 * Build-time properties for Android Resources inside a [Component].
 * Specialization of [AndroidResources] for modules that applied the `com.android.application` plugin.
 *
 * This is accessed via [GeneratesApk.androidResources]
 */
@Incubating
interface ApplicationAndroidResources: AndroidResources {
    /**
     * Read-only property that automatically generates locale config when enabled.
     *
     * To set it, use [ApplicationAndroidResourcesBuilder.generateLocaleConfig] in a
     * [AndroidComponentsExtension.beforeVariants] callback.
     */
    @get:Incubating
    val generateLocaleConfig: Boolean

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
     * androidComponents {
     *     onVariants(selector().withName("release")) { variant ->
     *         // Keeps language resources for the locales specified below.
     *         variant.androidResources.localeFilters.addAll("en-rGB", "fr")
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
    val localeFilters: SetProperty<String>
}
