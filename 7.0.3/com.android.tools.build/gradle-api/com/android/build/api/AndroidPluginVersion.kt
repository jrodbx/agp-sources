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
 * Designed for plugin authors to compare the currently running plugin version
 */
@Incubating
class AndroidPluginVersion private constructor(
    val major: Int,
    val minor: Int,
    val micro: Int,
    private val _previewType: PreviewType,
    val preview: Int,
) : Comparable<AndroidPluginVersion> {

    init {
        require(major >= 0 && minor >= 0 && micro >= 0) { "Versions of the Android Gradle Plugin must not be negative" }
    }

    val previewType: String? get() = _previewType.publicName

    constructor(major: Int, minor: Int) : this(major, minor, 0, PreviewType.FINAL, 0)
    constructor(major: Int, minor: Int, micro: Int) : this (major, minor, micro, PreviewType.FINAL, 0)

    private enum class PreviewType(val publicName: String?) {
        ALPHA("alpha"),
        BETA("beta"),
        RC("rc"),
        DEV("dev"),
        FINAL(null),
    }

    fun alpha(alpha: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "alpha(int) only expected to be called on final versions" }
        require(alpha >= 1) { "Alpha version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.ALPHA, preview=alpha)
    }
    fun beta(beta: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "beta(int) only expected to be called on final versions" }
        require(beta >= 1) { "Beta version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.BETA, preview=beta)
    }
    fun rc(rc: Int): AndroidPluginVersion {
        require(_previewType == PreviewType.FINAL) { "rc(int) only expected to be called on final versions" }
        require(rc >= 1) { "Release candidate version must be at least 1" }
        return AndroidPluginVersion(major, minor, micro, PreviewType.RC, preview=rc)
    }
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
        "AndroidPluginVersion $major.$minor.$micro" +
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
