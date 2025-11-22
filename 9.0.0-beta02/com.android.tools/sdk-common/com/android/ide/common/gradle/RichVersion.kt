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
package com.android.ide.common.gradle

import com.google.common.collect.BoundType
import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet

/**
 * Represents a rich version in the sense of Gradle's dependency specifier concept, documented
 * in the [Gradle manual](https://docs.gradle.org/current/userguide/rich_versions.html)
 */
data class RichVersion(
    private val declaration: Declaration,
    val prefer: Version? = null,
    val exclude: List<VersionRange> = listOf()
) {
    val strictly get() = declaration.takeIf { it.kind == Kind.STRICTLY }?.range
    val require get() = declaration.takeIf { it.kind == Kind.REQUIRE }?.range

    /**
     * Return a String identifying this [RichVersion], if one exists, or `null` otherwise.  If
     * this returns a String, it is guaranteed that [parse] of that String returns an equivalent
     * [RichVersion].
     */
    fun toIdentifier(): String? =
        when {
            exclude.isNotEmpty() -> null
            declaration.kind == Kind.REQUIRE && prefer != null -> null
            declaration.kind == Kind.REQUIRE ->
                require!!.toIdentifier()?.takeIf { it.indexOf("!!") == -1 }
            // declaration.kind == Kind.STRICTLY
            else -> strictly!!.let { range ->
                if (prefer?.isPrefixInfimum == true) {
                    null
                }
                else {
                    range.toIdentifier()?.let { rangeId ->
                        when {
                            rangeId.indexOf("!!") != -1 -> null
                            prefer?.equals(Version.parse("")) == true -> null
                            else -> "${rangeId}${prefer?.let { "!!$it" } ?: "!!"}"
                        }
                    }
                }
            }
        }
    override fun toString() = when(val id = toIdentifier()) {
        is String -> id
        else -> "RichVersion(declaration=$declaration, prefer=$prefer, exclude=$exclude)"
    }

    /**
     * Return true if: both this [RichVersion]'s declaration, interpreted strictly, contains
     * [version]; and none of the excluded ranges contains [version].
     */
    fun contains(version: Version) =
        declaration.range.contains(version) && exclude.none { it.contains(version) }

    /**
     * Return true if: this [RichVersion]'s declaration contains [version] or, if the declaration
     * is not a strict one, if [version] is higher than the declaration's upper bound; and none of
     * the excluded ranges contains [version].  This approximates a part of Gradle's dependency
     * resolution semantics; only [Version]s which a given [RichVersion] [accepts] should be
     * considered.
     */
    fun accepts(version: Version): Boolean {
        val range = when (declaration.kind) {
            Kind.STRICTLY -> declaration.range
            Kind.REQUIRE -> declaration.range.withoutUpperBound()
        }
        return range.contains(version) && exclude.none { it.contains(version) }
    }

    /**
     * Is true if the RichVersion explicitly encodes a range of a single version.
     *
     * In particular, this is `true` for manifest RichVersion identifiers such as `1.0` or
     * `[1.0,1.0]`.  If the RichVersion has excludes, we test to see if a declaration of an
     * explicit singleton is not excluded by any exclude specifier, so that a RichVersion with
     * e.g. a `require` value of `[1.0,1.0]` and an `excludes` of `2.+` is treated as encoding
     * an explicit singleton, but a `require` of `[1.0,1.0] and `excludes` of `1.+` is not.
     *
     * Note that although it might seem that you could encode a singleton in disguise with something
     * like a `require` of [1.0,2] and an `excludes` of [1.0,2), the semantics of maven-like open
     * bounds actually mean that this encodes all versions beginning with `2` up to and including
     * `2` itself, including `2.dev` and other versions with non-numeric suffixes.  It would be
     * theoretically possible to construct singleton ranges in disguise with hand-crafted
     * RichVersion construction, but such artificial constructs are treated as non-explicit
     * singletons.
     */
    val isExplicitSingleton
        get() = declaration.range.singletonVersion?.let {
            if (prefer != null && it != prefer) return@let false
            else return@let exclude.none { e -> e.contains(it) }
        } ?: false

    /**
     * If the RichVersion explicitly encodes a range of a single version, return it.
     */
    val explicitSingletonVersion
        get() = if (isExplicitSingleton) declaration.range.singletonVersion else null

    /**
     * Return the lower bound [Version] of this [RichVersion]'s [declaration], treating the
     * prefixInfimum of "dev" as the least possible [Version] for [RichVersion]s with no explicit
     * lower bound.  The return value might be explicitly excluded by an [exclude] entry.
     */
    val lowerBound: Version
        get() = when {
            declaration.range.hasLowerBound() -> declaration.range.lowerEndpoint()
            else -> Version.prefixInfimum("dev")
        }

    companion object {
        /**
         * Parse a string as a [RichVersion].  All strings are valid rich versions; the first
         * substring of `!!` in the string separates a strict version range declaration from an
         * optional preferred version; if there is no such `!!` substring, the whole string denotes
         * a required version range (which handles its upper bound more permissively).
         */
        @JvmStatic
        fun parse(string: String): RichVersion {
            return when (val bangs = string.indexOf("!!")) {
                // Gradle refuses to parse a RichVersion identifier with !! at index 0.  We do the
                // following (treat it as following an empty version) but we could equally
                // interpret it as part of an unusual required version.  The chances of this
                // mattering in practice are low.
                -1 -> RichVersion(Declaration(Kind.REQUIRE, VersionRange.parse(string)))
                else -> {
                    val strict = VersionRange.parse(string.substring(0, bangs))
                    val prefer = string.substring(bangs + 2)
                        .takeIf { it.isNotEmpty() }
                        ?.let { Version.parse(it) }
                    RichVersion(Declaration(Kind.STRICTLY, strict), prefer)
                }
            }
        }

        /**
         * Parse a string in Maven POM Dependency Requirement Format, returning a [RichVersion]
         * expressing the requirements.  The consequences are undefined if the given string is
         * malformed.  The semantics differ for excluded end ranges between Gradle and Maven; if
         * this matters to you, please file a bug.
         */
        @JvmStatic
        fun fromPomVersion(string: String): RichVersion {
            fun whitespace(char: Char) = char.code <= 0x20
            fun constituent(char: Char) = !whitespace(char)
            fun String.indexOf(predicate: (Char) -> Boolean, startIndex: Int = 0): Int =
                (startIndex..<length).firstOrNull { predicate(this[it]) } ?: -1
            fun parseRangeSet(string: String): TreeRangeSet<Version> {
                // precondition: string[start] is '[' or '('; comma is the next comma, and close is
                // the next ']' or ')' after comma.
                fun parseOneRange(string: String, start: Int, comma: Int, close: Int): Range<Version> {
                    val fallback = Range.singleton(Version.parse(string.substring(start)))
                    val lowerType = when (string[start]) {
                        '(' -> BoundType.OPEN
                        '[' -> BoundType.CLOSED
                        else -> return fallback
                    }
                    val upperType = when (string[close]) {
                        ')' -> BoundType.OPEN
                        ']' -> BoundType.CLOSED
                        else -> return fallback
                    }
                    val lower = string.substring(start+1, comma).trim(::whitespace).ifEmpty { null }?.let(Version::parse)
                    val upper = string.substring(comma+1, close).trim(::whitespace).ifEmpty { null }?.let {
                        // Strictly speaking this is not correct for Maven
                        if (upperType == BoundType.OPEN) Version.prefixInfimum(it) else Version.parse(it)
                    }
                    return when {
                        lower == null && upper == null -> Range.all()
                        lower == null && upper != null -> Range.upTo(upper, upperType)
                        lower != null && upper == null -> Range.downTo(lower, lowerType)
                        lower != null && upper != null -> {
                            when {
                                lower > upper -> fallback
                                lowerType == BoundType.OPEN && upperType == BoundType.OPEN && lower == upper -> fallback
                                else -> Range.range(lower, lowerType, upper, upperType)
                            }
                        }
                        else -> throw IllegalStateException("Something is neither null nor not-null")
                    }
                }
                val result = TreeRangeSet.create<Version>()
                var start = 0
                var fallbackPos = start
                fun fallback() = Range.singleton(Version.parse(string.substring(fallbackPos)))
                while (start < string.length) {
                    val comma = string.indexOf(',', startIndex = start)
                    if (comma == -1) { result.add(fallback()); break }
                    val close = string.indexOfAny(charArrayOf(')', ']'), startIndex = comma)
                    if (close == -1) { result.add(fallback()); break }
                    result.add(parseOneRange(string, start, comma, close))
                    start = close + 1
                    start = string.indexOf(::constituent, startIndex = start)
                    if (start == -1) { break } // no more non-empty space
                    fallbackPos = start
                    if (string[start] == ',') {
                        start = string.indexOf(::constituent, startIndex = 1 + start)
                    }
                    if (start == -1) { result.add(fallback()); break }
                    when (string[start]) {
                        '(', '[' -> fallbackPos = start
                        else -> { result.add(fallback()); break }
                    }
                }
                return result
            }
            fun TreeRangeSet<Version>.toSingleRangeAndExcludes(): Pair<VersionRange, List<VersionRange>> {
                if (isEmpty) return Version.parse("").let { arb -> VersionRange(Range.closedOpen(arb, arb)) } to listOf()
                val span = span()
                val excludes = this.complement().asRanges().map {
                    span.intersection(it)
                }.toList().let { TreeRangeSet.create(it) }
                return VersionRange(span) to excludes.asRanges().map { VersionRange(it) }
            }

            return when {
                string.isEmpty() -> parse(string)
                !setOf('[','(').contains(string[0]) -> parse(string.trimEnd(::whitespace))
                string[0] == '[' && string.indexOfLast(::constituent) == string.indexOf(']') && !string.contains(',') ->
                    strictly(Version.parse(string.substring(1, string.indexOf(']')).trim(::whitespace)))
                else -> parseRangeSet(string).toSingleRangeAndExcludes().let { (r, e) ->
                    RichVersion(Declaration(Kind.STRICTLY, r), exclude = e)
                }
            }
        }

        /**
         * Return a [RichVersion] with a [Kind.REQUIRE] declaration on the singleton (given)
         * version.
         */
        @JvmStatic
        fun require(version: Version): RichVersion =
            RichVersion(Declaration(Kind.REQUIRE, VersionRange(Range.singleton(version))))

        @JvmStatic
        fun require(versionRange: VersionRange): RichVersion =
            RichVersion(Declaration(Kind.REQUIRE, versionRange))

        @JvmStatic
        fun strictly(version: Version): RichVersion =
            RichVersion(Declaration(Kind.STRICTLY, VersionRange(Range.singleton(version))))

        @JvmStatic
        fun strictly(versionRange: VersionRange): RichVersion =
            RichVersion(Declaration(Kind.STRICTLY, versionRange))
    }

    enum class Kind {
        STRICTLY,
        REQUIRE,
    }
    data class Declaration(val kind: Kind, val range: VersionRange)
}
