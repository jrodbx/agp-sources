/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api

import org.gradle.api.Incubating
import java.util.Objects

/**
 * Represents a version of the Android Gradle Plugin
 *
 * Designed for plugin authors to compare the currently running plugin version, so implements
 * comparable.
 *
 * For example `AndroidPluginVersion(7, 0)` refers to Android Gradle Plugin version 7.0.0,
 * and `AndroidPluginVersion(7, 0, 1)` refers to Android Gradle Plugin version 7.0.1.
 *
 * The internal representation is normalized, so that `AndroidPluginVersion(7, 0)` equals
 * `AndroidPluginVersion(7, 0, 0)`
 *
 * Also supports preview versions through methods [alpha], [beta] and [rc] that return the more
 * specific version. For example `AndroidPluginVersion(7, 0).alpha(5)` refers to Android Gradle
 * Plugin version 7.0.0-alpha05. This is for use when developing using incubating APIs that have
 * changed between preview releases of the Android Gradle Plugin. Once those APIs are stable in a
 * stable version of Android Gradle Plugin, it's recommended to drop support for the preview versions.
 * For example, if a new API was introduced in 7.0.0-alpha05, you can test for that using
 *
 * ```if (androidComponents.pluginVersion >= AndroidPluginVersion(7.0).alpha(5)) { ... }```
 *
 * If that API is marked as stable in 7.0.0, drop support for the preview versions before it by
 * updating your condition to:
 *
 * ```if (androidComponents.pluginVersion >= AndroidPluginVersion(7.0)) { ... }```
 */
class AndroidPluginVersion private constructor(
    /**
     * The major version.
     *
     * e.g. 7 for Android Gradle Plugin Version 7.0.1
     */
    val major: Int,
    /**
     * The minor version.
     *
     * e.g. 0 for Android Gradle Plugin Version 7.0.1
     */
    val minor: Int,
    /**
     * The micro, or patch version.
     *
     * e.g. 1 for Android Gradle Plugin Version 7.0.1
     */
    val micro: Int,
    private val _previewType: PreviewType,

    /**
     * The preview version.
     *
     * e.g. 5 for Android Gradle Plugin Version 7.0.0-alpha05
     */
    val preview: Int,
) : Comparable<AndroidPluginVersion> {

    init {
        require(major >= 0 && minor >= 0 && micro >= 0) { "Versions of the Android Gradle Plugin must not be negative" }
    }

    /**
     * The type of preview version.
     *
     * Null in the case of a stable version. One of 'alpha', 'beta', 'rc', 'dev' for preview versions.
     *
     * e.g. 'alpha' for Android Gradle Plugin Version 7.0.0-alpha05
     */
    val previewType: String? get() = _previewType.publicName

    /**
     * Create an AndroidPluginVersion with the given major and minor version.
     *
     * For example `AndroidPluginVersion(7, 0)` refers to Android Gradle Plugin version 7.0.0.
     */
    constructor(major: Int, minor: Int) : this(major, minor, 0, PreviewType.FINAL, 0)
    /**
     * Create an AndroidPluginVersion with the given major, minor and micro version.
     *
     * For example `AndroidPluginVersion(7, 0, 1)` refers to Android Gradle Plugin version 7.0.1.
     */
    constructor(major: Int, minor: Int, micro: Int) : this (major, minor, micro, PreviewType.FINAL, 0)

    private enum class PreviewType(val publicName: String?) {
        ALPHA("alpha"),
        BETA("beta"),
        RC("rc"),
        DEV("dev"),
        FINAL(null),
    }

    /**
     * From a stable [AndroidPluginVersion] returns an alpha version.
     *
     * For example `AndroidPluginVersion(7, 0).alpha(5)` refers to Android Gradle Plugin version 7.0.0-alpha05.
     */
    fun alpha(alpha: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "alpha(int) only expected to be called on final versions" }
        require(alpha >= 1) { "Alpha version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.ALPHA, preview=alpha)
    }
    /**
     * From a stable [AndroidPluginVersion] returns a beta version.
     *
     * For example `AndroidPluginVersion(7, 0).beta(2)` refers to Android Gradle Plugin version 7.0.0-beta02.
     */
    fun beta(beta: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "beta(int) only expected to be called on final versions" }
        require(beta >= 1) { "Beta version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.BETA, preview=beta)
    }
    /**
     * From a stable [AndroidPluginVersion] returns a release candidate version.
     *
     * For example `AndroidPluginVersion(7, 0).rc(1)` refers to Android Gradle Plugin version 7.0.0-rc01.
     */
    fun rc(rc: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "rc(int) only expected to be called on final versions" }
        require(rc >= 1) { "Release candidate version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.RC, preview=rc)
    }
    /**
     * From a stable [AndroidPluginVersion] specify an internal development version.
     *
     * `-dev` versions are never publicly released, but this can be useful if you are
     * building the Android Gradle Plugin from source.
     *
     * For example `AndroidPluginVersion(7, 0).dev()` refers to Android Gradle Plugin version 7.0.0-dev.
     */
    @Incubating
    fun dev() : AndroidPluginVersion {
        return AndroidPluginVersion(major, minor, micro, PreviewType.DEV, preview=0)
    }

    override fun compareTo(other: AndroidPluginVersion): Int {
        return comparator.compare(this, other)
    }

    override fun equals(other: Any?): Boolean {
        return other is AndroidPluginVersion &&
                major == other.major &&
                minor == other.minor &&
                micro == other.micro &&
                _previewType == other._previewType &&
                preview == other.preview
    }

    override fun hashCode(): Int {
        return Objects.hash(major, minor, micro, _previewType, preview)
    }

    override fun toString(): String =
        "Android Gradle Plugin version $major.$minor.$micro" +
                (if (previewType != null) "-$previewType" else "") +
                (if (preview > 0) preview else "")

    private companion object {
        val comparator: Comparator<AndroidPluginVersion> =
            Comparator.comparingInt<AndroidPluginVersion> { it.major }
                .thenComparingInt { it.minor }
                .thenComparingInt { it.micro }
                .thenComparingInt { it._previewType.ordinal }
                .thenComparingInt { it.preview }
    }
}
