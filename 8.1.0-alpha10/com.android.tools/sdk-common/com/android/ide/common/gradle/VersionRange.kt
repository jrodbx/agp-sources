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
package com.android.ide.common.gradle

import com.google.common.collect.BoundType
import com.google.common.collect.DiscreteDomain
import com.google.common.collect.Range

/**
 * Note: This does not support the entire collection of Gradle rich versions, but does represent:
 * - the range corresponding to a single version specifier, meaning that version or newer;
 * - the range corresponding to a prefix version range, meaning versions matching that prefix;
 * - the range corresponding to a maven-style version range, meaning versions within that range
 *   (modified by prefix matching if the upper bound is exclusive).
 *
 * Note: This is not a [Range] by inheritance, as [Range] is final, while we need to customize
 * printing.  It nevertheless supports the public interface of [Range] with [Version] domain.
 *
 * This is intended to support string specifiers of artifact versions in Gradle build files.  It
 * does not attempt to model directly the strictly/required/preferred hierarchy of specifications.
 */
class VersionRange private constructor(private val range: Range<Version>) {
    fun hasLowerBound() = range.hasLowerBound()
    fun lowerEndpoint() = range.lowerEndpoint()
    fun lowerBoundType() = range.lowerBoundType()
    fun hasUpperBound() = range.hasUpperBound()
    fun upperEndpoint() = range.upperEndpoint()
    fun upperBoundType() = range.upperBoundType()
    fun isEmpty() = range.isEmpty()
    fun contains(version: Version) = range.contains(version)
    fun containsAll(versions: Iterable<Version>) = range.containsAll(versions)
    fun encloses(other: VersionRange) = range.encloses(other.range)
    fun isConnected(other: VersionRange) = range.isConnected(other.range)
    fun intersection(other: VersionRange) = VersionRange(range.intersection(other.range))
    fun gap(other: VersionRange) = VersionRange(range.gap(other.range))
    fun span(other: VersionRange) = VersionRange(range.span(other.range))
    fun canonical(domain: DiscreteDomain<Version>) = VersionRange(range.canonical(domain))

    override fun equals(other: Any?) = other is VersionRange && range == other.range
    override fun hashCode() = range.hashCode()
    override fun toString(): String {
        if (!hasLowerBound() && !hasUpperBound()) return "+"
        if (isPrefixRange()) {
            return "${lowerEndpoint().prefixVersion()}.+"
        }
        if (validLowerBoundForMavenRange() && validUpperBoundForMavenRange()) {
            val sb = StringBuilder()
            if (!hasLowerBound()) {
                sb.append("(")
            }
            else if (lowerBoundType() == BoundType.OPEN) {
                sb.append("(${lowerEndpoint()}")
            }
            else {
                sb.append("[${lowerEndpoint()}")
            }
            sb.append(",")
            if (!hasUpperBound()) {
                sb.append(")")
            }
            else if (upperBoundType() == BoundType.OPEN) {
                sb.append("${upperEndpoint().prefixVersion()})")
            }
            else {
                sb.append("${upperEndpoint()}]")
            }
            return sb.toString()
        }
        // fallback: we shouldn't be able to construct this VersionRange using the factories,
        // but just in case try to provide some sensible printed information.
        return range.toString()
    }

    private fun isPrefixRange() =
        hasLowerBound() && hasUpperBound() && let {
            val lower = lowerEndpoint()
            val upper = upperEndpoint()
            lowerBoundType() == BoundType.CLOSED && lower.isPrefixInfimum &&
                    upperBoundType() == BoundType.OPEN && upper.isPrefixInfimum &&
                    lower.nextPrefix() == upper
        }
    private fun validLowerBoundForMavenRange() =
        !hasLowerBound() || !lowerEndpoint().isPrefixInfimum
    private fun validUpperBoundForMavenRange() =
        !hasUpperBound() ||
                (upperBoundType() == BoundType.CLOSED && !upperEndpoint().isPrefixInfimum) ||
                (upperBoundType() == BoundType.OPEN && upperEndpoint().isPrefixInfimum)

    companion object {
        /**
         * Matches a String consisting entirely an opening range indicator, followed by zero or
         * more non-commas, a comma, zero or more non-commas, and a closing range indicator.
         */
        private val MAVEN_STYLE_REGEX = "^[\\[(\\]][^,]*,[^,]*[\\[)\\]]$".toRegex()
        fun parse(string: String): VersionRange {
            val range = when {
                string == "+" -> Range.all()
                string.matches(MAVEN_STYLE_REGEX) -> parseMavenRange(string)
                string.endsWith("+") -> parsePrefixRange(string)
                else -> parseSingletonRange(string)
            }
            return VersionRange(range)
        }
        private fun parseMavenRange(string: String): Range<Version> {
            val commaIndex = string.indexOf(',')
            val lowerString = string.substring(1, commaIndex)
            val higherString = string.substring(commaIndex + 1, string.length - 1)
            val lowerClosed = string[0] == '['
            val higherClosed = string[string.length-1] == ']'
            if (lowerString == "" && higherString == "") return Range.all()
            if (higherString == "") {
                return when (lowerClosed) {
                    true -> Range.atLeast(Version.parse(lowerString))
                    false -> Range.greaterThan(Version.parse(lowerString))
                }
            }
            if (lowerString == "") {
                return when(higherClosed) {
                    true -> Range.atMost(Version.parse(higherString))
                    false -> Range.lessThan(Version.prefixInfimum(higherString))
                }
            }
            return when {
                lowerClosed && higherClosed -> Range.closed(Version.parse(lowerString), Version.parse(higherString))
                lowerClosed && !higherClosed -> Range.closedOpen(Version.parse(lowerString), Version.prefixInfimum(higherString))
                !lowerClosed && higherClosed -> Range.openClosed(Version.parse(lowerString), Version.parse(higherString))
                // !lowerClosed && !higherClosed
                else -> Range.open(Version.parse(lowerString), Version.prefixInfimum(higherString))
            }
        }
        private fun parsePrefixRange(string: String): Range<Version> {
            // It's not clear from the documentation, but I think implied in it is that the
            // range denoted by `1+` and the range denoted by `1.+` should be the same: in each
            // case matching 1.<x> but not e.g. 10.3.  I reserve the right to change my mind
            // on this later.
            val lower = Version.prefixInfimum(string.replace("[-+_.]?\\+$".toRegex(), ""))
            val upper = lower.nextPrefix()
            return Range.closedOpen(lower, upper)
        }
        private fun parseSingletonRange(string: String): Range<Version> {
            return Range.singleton(Version.parse(string))
        }
    }
}
