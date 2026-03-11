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
 * Packaging options for java resource files in the Android DSL
 *
 * ```kotlin
 * android {
 *     packaging {
 *         resources {
 *             excludes += "/..."
 *         }
 *     }
 * }
 * ```
 */
interface ResourcesPackaging {
    /**
     * The set of excluded patterns. Java resources matching any of these patterns do not get
     * packaged in the APK.
     *
     * Example: `android.packagingOptions.resources.excludes += "**`/`*.exclude"`
     */
    val excludes: MutableSet<String>

    /**
     * The set of patterns for which the first occurrence is packaged in the APK. For each java
     * resource APK entry path matching one of these patterns, only the first java resource found
     * with that path gets packaged in the APK.
     *
     * Example: `android.packagingOptions.resources.pickFirsts += "**`/`*.pickFirst"`
     */
    val pickFirsts: MutableSet<String>

    /**
     * The set of patterns for which matching java resources are merged. For each java resource
     * APK entry path matching one of these patterns, all java resources with that path are
     * concatenated and packaged as a single entry in the APK.
     *
     * Example: `android.packagingOptions.resources.merges += "**`/`*.merge"`
     */
    val merges: MutableSet<String>
}
