/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ide.common.repository

import com.google.common.base.Objects
import java.util.Locale

/**
 * This class is deliberately non-comparable with [GradleVersion], for two reasons: firstly,
 * the ordering semantics we depend on for `-dev` versions are incompatible with the specified
 * ordering of Gradle version specifiers; secondly, we use Android Gradle Plugin versions as version
 * identifiers in various places in the IDE (particularly in sync-related areas) and it would be a
 * semantic error to compare such a version with a generic Gradle version.
 */
class AgpVersion private constructor(
    val major: Int,
    val minor: Int,
    val micro: Int,
    val previewKind: PreviewKind,
    val preview: Int?
) : Comparable<AgpVersion> {

    enum class PreviewKind { // order is important for comparison
    ALPHA, BETA, RC, DEV, NONE;

        override fun toString(): String {
            return when (this) {
                ALPHA -> "-alpha"
                BETA -> "-beta"
                RC -> "-rc"
                DEV -> "-dev"
                NONE -> ""
            }
        }

        fun toPreviewType(): String? {
            return when (this) {
                ALPHA -> "alpha"
                BETA -> "beta"
                RC -> "rc"
                DEV, NONE -> null
            }
        }

        fun toIsSnapshot(): Boolean {
            return when (this) {
                ALPHA, BETA, RC, NONE -> false
                DEV -> true
            }
        }

        companion object {

            fun fromPreviewTypeAndIsSnapshot(value: String?, isSnapshot: Boolean): PreviewKind =
                when {
                    "alpha" == value -> ALPHA
                    "beta" == value -> BETA
                    "rc" == value -> RC
                    value == null && isSnapshot -> DEV
                    value == null && !isSnapshot -> NONE
                    else -> throw IllegalArgumentException("$value is not a PreviewKind")
                }
        }
    }

    val previewType: String?
        get() = previewKind.toPreviewType()

    val isPreview: Boolean
        get() = PreviewKind.NONE != previewKind

    val isSnapshot: Boolean
        get() = previewKind.toIsSnapshot()

    @JvmOverloads
    constructor(major: Int, minor: Int, micro: Int = 0) : this(
        major, minor, micro, PreviewKind.NONE, null
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AgpVersion
        return this.compareTo(that) == 0
    }

    override fun hashCode(): Int {
        return Objects.hashCode(major, minor, micro, previewKind, preview)
    }

    override fun compareTo(other: AgpVersion): Int = when {
        major != other.major -> major - other.major
        minor != other.minor -> minor - other.minor
        micro != other.micro -> micro - other.micro
        previewKind != other.previewKind -> previewKind.ordinal - other.previewKind.ordinal
        preview != null && other.preview != null -> preview - other.preview
        // if either is null, both must be null (both DEV or NONE versions).
        else -> 0
    }

    operator fun compareTo(value: String): Int {
        return compareTo(parse(value))
    }

    fun compareIgnoringQualifiers(other: AgpVersion): Int {
        val thisWithoutQualifiers = AgpVersion(major, minor, micro)
        val otherWithoutQualifiers = AgpVersion(other.major, other.minor, other.micro)
        return thisWithoutQualifiers.compareTo(otherWithoutQualifiers)
    }

    fun compareIgnoringQualifiers(value: String): Int {
        return compareIgnoringQualifiers(parse(value))
    }

    fun isAtLeast(major: Int, minor: Int, micro: Int): Boolean {
        return this >= AgpVersion(major, minor, micro)
    }

    fun isAtLeastIncludingPreviews(major: Int, minor: Int, micro: Int): Boolean {
        val thisWithoutQualifiers = AgpVersion(this.major, this.minor, this.micro)
        return thisWithoutQualifiers >= AgpVersion(major, minor, micro)
    }

    fun isAtLeast(
        major: Int,
        minor: Int,
        micro: Int,
        previewType: String?,
        previewVersion: Int,
        isSnapshot: Boolean
    ): Boolean {
        val previewKind = PreviewKind.fromPreviewTypeAndIsSnapshot(previewType, isSnapshot)
        val other = AgpVersion(major, minor, micro, previewKind, previewVersion)
        return this >= other
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "%d.%d.%d%s", major, minor, micro, previewKind))
        if (preview != null) {
            if (isTwoDigitPreviewFormat(major, minor, micro, previewKind)) {
                sb.append(String.format(Locale.US, "%02d", preview))
            } else {
                sb.append(String.format(Locale.US, "%d", preview))
            }
        }
        return sb.toString()
    }

    companion object {

        private val VERSION_REGEX: Regex

        init {
            val digit = "[0-9]"
            val num = "(?:0|[1-9]$digit*)"
            val previewKind = "-(?:alpha|beta|rc)"
            val dot = Regex.escape(".")
            val dev = "-dev"
            val pattern = "($num)$dot($num)$dot($num)(?:($previewKind)($digit$digit?)|($dev))?"
            VERSION_REGEX = Regex(pattern)
        }

        /** precondition: PreviewKind should be ALPHA, BETA or RC  */
        private fun isTwoDigitPreviewFormat(
            major: Int, minor: Int, micro: Int, previewKind: PreviewKind
        ): Boolean {
            return major > 3 ||
                    major == 3 && minor > 1 ||
                    major == 3 && minor == 1 && micro == 0 && previewKind != PreviewKind.BETA
        }

        private fun parsePreviewString(
            major: Int, minor: Int, micro: Int, previewKind: PreviewKind, previewString: String
        ): Int {
            if (isTwoDigitPreviewFormat(
                    major,
                    minor,
                    micro,
                    previewKind
                ) && previewString.length != 2
            ) {
                throw NumberFormatException(
                    "AgpVersion $major.$minor.$micro$previewKind requires" +
                            " a two digit preview, but received \"$previewString\"."
                )
            }
            if (!isTwoDigitPreviewFormat(
                    major,
                    minor,
                    micro,
                    previewKind
                ) && previewString.startsWith("0")
            ) {
                throw NumberFormatException(
                    "AgpVersion $major.$minor.$micro$previewKind requires" +
                            " no zero-padding, but received \"$previewString\"."
                )
            }
            return previewString.toInt()
        }

        /**
         * Attempt to parse {@param value} as a String corresponding to a valid AGP version. The
         * (regular) language recognized is:
         *
         * NUM "." NUM "." NUM (PREVIEW-KIND digit digit? | "-dev")? where NUM = "0" | [1-9] digit*
         * and PREVIEW-KIND = "-" ("alpha" | "beta" | "rc")
         *
         * After the regular language is recognized, we additionally impose a constraint on the
         * numeric preview value, corresponding to the versions actually released: in the 3.0.0
         * series and earlier, and in the 3.1.0-beta series, numeric preview versions were used with
         * no padding; in 3.1.0-alpha and 3.1.0-rc and all later versions, numeric preview versions
         * were zero-padded with a field width of 2.
         *
         * (See also `AndroidGradlePluginVersion` in the `gradle-dsl` module).
         *
         * @param value a String
         * @return an AgpVersion object corresponding to {@param value}, or null
         */
        @JvmStatic
        fun tryParse(value: String): AgpVersion? {
            val matchResult = VERSION_REGEX.matchEntire(value) ?: return null
            val matchList = matchResult.destructured.toList()
            return try {
                val major = matchList[0].toInt()
                val minor = matchList[1].toInt()
                val micro = matchList[2].toInt()
                val previewKind: PreviewKind = when {
                    "-alpha" == matchList[3] -> PreviewKind.ALPHA
                    "-beta" == matchList[3] -> PreviewKind.BETA
                    "-rc" == matchList[3] -> PreviewKind.RC
                    "-dev" == matchList[5] -> PreviewKind.DEV
                    else -> PreviewKind.NONE
                }
                val preview: Int? = when (previewKind) {
                    PreviewKind.ALPHA, PreviewKind.BETA, PreviewKind.RC -> parsePreviewString(
                        major,
                        minor,
                        micro,
                        previewKind,
                        matchList[4]
                    )

                    else -> null
                }
                AgpVersion(major, minor, micro, previewKind, preview)
            } catch (e: NumberFormatException) {
                null
            }
        }

        /**
         * Parse [value] as a String corresponding to a valid AGP version. See [tryParse] for
         * details on the version string format recognized.
         *
         * @param value a String
         * @return an AgpVersion object corresponding to [value].
         * @throws IllegalArgumentException if [value] is not a valid AGP version string.
         */
        @JvmStatic
        fun parse(value: String): AgpVersion {
            return tryParse(value)
                ?: throw IllegalArgumentException("$value is not a valid AGP version string.")
        }

        private val STABLE_VERSION_REGEX: Regex

        init {
            val digit = "[0-9]"
            val num = "(?:0|[1-9]$digit*)"
            val dot = Regex.escape(".")
            val pattern = "($num)$dot($num)$dot($num)"
            STABLE_VERSION_REGEX = Regex(pattern)
        }

        /**
         * Attempt to parse [value] as a String corresponding to a valid stable AGP version. The
         * (regular) language recognized is:
         *
         *
         * NUM "." NUM "." NUM where NUM = "0" | [1-9] digit*
         *
         * @param value a String
         * @return an AgpVersion object corresponding to [value], or null
         */
        @JvmStatic
        fun tryParseStable(value: String): AgpVersion? {
            val matchResult = STABLE_VERSION_REGEX.matchEntire(value) ?: return null
            val matchList = matchResult.destructured.toList()
            return try {
                val major = matchList[0].toInt()
                val minor = matchList[1].toInt()
                val micro = matchList[2].toInt()
                AgpVersion(major, minor, micro)
            } catch (e: NumberFormatException) {
                null
            }
        }

        /**
         * Parse [value] as a String corresponding to a valid stable AGP version.  See
         * [tryParseStable] for details on the version string format recognized.
         *
         * @param value a String
         * @return an AgpVersion object corresponding to [value].
         * @throws IllegalArgumentException if [value] is not a valid stable AGP version string.
         */
        @JvmStatic
        fun parseStable(value: String): AgpVersion {
            return tryParseStable(value)
                ?: throw IllegalArgumentException(
                    "$value is not a valid stable AGP version string.")
        }
    }
}
