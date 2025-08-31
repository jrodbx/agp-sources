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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for configuring Android resource options.
 *
 * This is accessed via [CommonExtension.androidResources]
 */
interface AndroidResources {
    /**
     * Pattern describing assets to be ignored.
     *
     * This is [ignoreAssetsPatterns] joined by ':'.
     */
    var ignoreAssetsPattern: String?

    /**
     * Patterns describing assets to be ignored.
     *
     * If empty, defaults to `["!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~"]`
     */
    val ignoreAssetsPatterns: MutableCollection<String>

    /**
     * File extensions of Android resources, assets, and Java resources to be stored uncompressed in
     * the APK. Adding an empty extension (e.g., setting `noCompress ''`) will disable compression
     * for all Android resources, assets, and Java resources.
     */
    val noCompress: MutableCollection<String>

    /**
     * Adds a file extension of Android resources, assets, and Java resources to be stored
     * uncompressed in the APK. Adding an empty extension (i.e., `noCompress('')`) will disable
     * compression for all Android resources, assets, and Java resources.
     */
    @Deprecated("Replaced with property noCompress")
    fun noCompress(noCompress: String)

    /**
     * Adds file extensions of Android resources, assets, and Java resources to be stored
     * uncompressed in the APK. Adding an empty extension (e.g., `noCompress('')`) will disable
     * compression for all Android resources, assets, and Java resources.
     */
    @Deprecated("Replaced with property noCompress")
    fun noCompress(vararg noCompress: String)

    /**
     * Forces aapt to return an error if it fails to find an entry for a configuration.
     *
     * See `aapt --help`
     */
    var failOnMissingConfigEntry: Boolean

    /** List of additional parameters to pass to `aapt`. */
    val additionalParameters: MutableList<String>

    /** Adds additional parameters to be passed to `aapt`. */
    @Deprecated("Replaced with property additionalParameters")
    fun additionalParameters(params: String)

    /** Adds additional parameters to be passed to `aapt`. */
    @Deprecated("Replaced with property additionalParameters")
    fun additionalParameters(vararg params: String)

    /**
     * Indicates whether the resources in this sub-project are fully namespaced.
     *
     * This property is incubating and may change in a future release.
     */
    @get:Incubating
    @set:Incubating
    var namespaced: Boolean
}
