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
 * Packaging option entry point for the Android DSL.
 *
 * ```kotlin
 * android {
 *     packaging {
 *     }
 * }
 * ```
 */
interface Packaging {
    /** The set of excluded paths.*/
    @Deprecated(
        "This property is deprecated. Use resources.excludes or jniLibs.excludes instead. Use "
                + "jniLibs.excludes for .so file patterns, and use resources.excludes for all "
                + "other file patterns."
    )
    val excludes: MutableSet<String>

    /**
     * The set of patterns where the first occurrence is packaged in the APK. First pick patterns
     * do get packaged in the APK, but only the first occurrence found gets packaged.
     */
    @Deprecated(
        "This property is deprecated. Use resources.pickFirsts or jniLibs.pickFirsts instead. "
                + "Use jniLibs.pickFirsts for .so file patterns, and use resources.pickFirsts for "
                + "all other file patterns."
    )
    val pickFirsts: MutableSet<String>

    /** The set of patterns where all occurrences are concatenated and packaged in the APK. */
    @Deprecated(
        "This property is deprecated. Use resources.merges instead.",
        replaceWith = ReplaceWith("resources.merges")
    )
    val merges: MutableSet<String>

    /**
     * The set of patterns for native library that should not be stripped of debug symbols.
     */
    @Deprecated(
        "This property is deprecated. Use jniLibs.keepDebugSymbols instead.",
        replaceWith = ReplaceWith("jniLibs.keepDebugSymbols")
    )
    val doNotStrip: MutableSet<String>

    /**
     * Adds an excluded pattern.
     *
     * @param pattern the pattern
     */
    @Deprecated(
        "This method is deprecated. Use resources.excludes.add() or "
                + "jniLibs.excludes.add() instead. Use jniLibs.excludes.add() for .so file "
                + "patterns, and use resources.excludes.add() for all other file patterns."
    )
    fun exclude(pattern: String)

    /**
     * Adds a first-pick pattern.
     *
     * @param pattern the path to add.
     */
    @Deprecated(
        "This method is deprecated. Use resources.pickFirsts.add() or "
                + "jniLibs.pickFirsts.add() instead. Use jniLibs.pickFirsts.add() for .so file "
                + "patterns, and use resources.pickFirsts.add() for all other file patterns."
    )
    fun pickFirst(pattern: String)

    /**
     * Adds a merge pattern.
     *
     * @param pattern the pattern, as packaged in the APK
     */
    @Deprecated(
        "This method is deprecated. Use resources.merges.add() instead.",
        replaceWith = ReplaceWith("resources.merges.add(pattern)")
    )
    fun merge(pattern: String)

    /**
     * Adds a doNotStrip pattern.
     *
     * @param pattern the pattern, as packaged in the APK
     */
    @Deprecated(
        "This method is deprecated. Use jniLibs.keepDebugSymbols.add() instead.",
        replaceWith = ReplaceWith("jniLibs.keepDebugSymbols.add(pattern)")
    )
    fun doNotStrip(pattern: String)

    /** Packaging options for dex files */
    val dex: DexPackaging

    /**
     * Method to configure the packaging options for dex files via a lambda
     *
     * ```kotlin
     * android {
     *     packaging {
     *         dex {
     *             useLegacyPackaging = false
     *         }
     *     }
     * }
     * ```
     */
    fun dex(action: DexPackaging.() -> Unit)

    /** Packaging options for JNI library files */
    val jniLibs: JniLibsPackaging

    /**
     * Method to configure the packaging options for JNI library files via a lambda
     *
     * ```kotlin
     * android {
     *     packaging {
     *         jniLibs {
     *             excludes += "/..."
     *         }
     *     }
     * }
     * ```
     */
    fun jniLibs(action: JniLibsPackaging.() -> Unit)

    /** Packaging options for java resources */
    val resources: ResourcesPackaging

    /**
     * Method to configure the packaging options for Java resources via a lambda
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
    fun resources(action: ResourcesPackaging.() -> Unit)
}
