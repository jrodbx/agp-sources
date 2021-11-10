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

/** DSL object for configuring aapt options. */
interface AndroidResources {
    /**
     * Pattern describing assets to be ignored.
     *
     * See `aapt --help`
     */
    var ignoreAssetsPattern: String?

    /**
     * Extensions of files that will not be stored compressed in the APK. Adding an empty
     * extension, i.e., setting `noCompress ''` will trivially disable compression
     * for all files.
     *
     * Equivalent of the -0 flag. See `aapt --help`
     */
    val noCompress: MutableCollection<String>

    /**
     * Adds extensions of files that will not be stored compressed in the APK.
     *
     * Equivalent of the -0 flag. See `aapt --help`
     */
    @Incubating
    fun noCompress(noCompress: String)

    /**
     * Adds extensions of files that will not be stored compressed in the APK.
     *
     * Equivalent of the -0 flag. See `aapt --help`
     */
    @Incubating
    fun noCompress(vararg noCompress: String)

    /**
     * Forces aapt to return an error if it fails to find an entry for a configuration.
     *
     * See `aapt --help`
     */
    var failOnMissingConfigEntry: Boolean

    /** List of additional parameters to pass to `aapt`. */
    @get:Incubating
    val additionalParameters: MutableList<String>

    /** Adds additional parameters to be passed to `aapt`. */
    @Incubating
    fun additionalParameters(params: String)

    /** Adds additional parameters to be passed to `aapt`. */
    @Incubating
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
