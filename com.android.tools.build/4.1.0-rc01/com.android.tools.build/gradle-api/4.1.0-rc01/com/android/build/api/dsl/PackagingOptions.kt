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

import org.gradle.api.Incubating

@Incubating
interface PackagingOptions {
    /** The list of excluded paths.*/
    val excludes: MutableSet<String>

    /**
     * The list of patterns where the first occurrence is packaged in the APK. First pick patterns
     * do get packaged in the APK, but only the first occurrence found gets packaged.
     */
    val pickFirsts: MutableSet<String>

    /** The list of patterns where all occurrences are concatenated and packaged in the APK. */
    val merges: MutableSet<String>

    /**
     * The list of patterns for native library that should not be stripped of debug symbols.
     *
     * Example: `packagingOptions.doNotStrip "*`/`armeabi-v7a/libhello-jni.so"`
     */
    val doNotStrip: MutableSet<String>

    /**
     * Adds an excluded pattern.
     *
     * @param pattern the pattern
     */
    fun exclude(pattern: String)

    /**
     * Adds a first-pick pattern.
     *
     * @param pattern the path to add.
     */
    fun pickFirst(pattern: String)

    /**
     * Adds a merge pattern.
     *
     * @param pattern the pattern, as packaged in the APK
     */
    fun merge(pattern: String)

    /**
     * Adds a doNotStrip pattern.
     *
     * @param pattern the pattern, as packaged in the APK
     */
    fun doNotStrip(pattern: String)
}