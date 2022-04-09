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

import com.android.ide.common.repository.GradleVersionRange
import com.google.common.annotations.Beta
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.math.BigInteger.ONE
import java.math.BigInteger.ZERO
import java.util.Locale
import java.util.Objects
import kotlin.math.max

/**
 * This class represents a single version with the semantics of version comparison as
 * specified by Gradle.  It explicitly does not represent:
 * - prefix includes (as designated by +) or prefix excludes (from an exclusive upper bound)
 * - ranges of any kind;
 * - the strictly, require, prefer (and reject) hierarchy of version constraints;
 * - any distinction between separator characters, except preserving them as constructed.
 *
 * Think of [Version] as representing a single point on the version line.
 */
@Beta
class Version: Comparable<Version> {
    // TODO:
    // - restartable parser (for re-use in parsing version ranges etc.)
    // - base version extraction (for conflict resolution)
    private val parts: List<Part>
    private val separators: List<Separator>
    /**
     * this is only non-private in order to provide error-checking in [GradleVersionRange]
     * constructors; it should not be generally used.  If true, this represents the infimum of
     * the set of versions with the specified parts as a prefix ("infimum" because there is no
     * finite representation of such a version).
     */
    val isPrefixInfimum: Boolean

    private constructor(parts: List<Part>, separators: List<Separator>, isPrefixInfimum: Boolean) {
        if (parts.size != separators.size) throw IllegalArgumentException()
        this.parts = parts
        this.separators = separators
        this.isPrefixInfimum = isPrefixInfimum
    }

    private fun extendedParts(atLeast: Int): List<Part> = DEV("dev").let { dev ->
        parts.let {
            if (isPrefixInfimum) it + List(max(0, atLeast - it.size)) { dev } else it
        }
    }

    // This is a reasonably well-defined concept.
    val isPreview
        get() = parts.any { it !is Numeric } || isPrefixInfimum
    // This is not very well-defined:
    // - why is SNAPSHOT special, compared with all the other Special components?
    // - we check only check the last part because this is what the GradleVersion class did
    // (It is used in places to check whether this version should be offered as an upgrade to
    // an existing version; in practice versions with -dev or -snapshot parts anywhere should
    // probably not usually be offered.)
    val isSnapshot
        get() = parts.lastOrNull().let { it is SNAPSHOT || it is DEV }
    // These are reasonably well-defined concepts, and convenient for users of this class wanting
    // to layer e.g. pragmatic semantic versioning on top.
    val major
        get() = (parts.takeIf { it.isNotEmpty() }?.get(0) as? Numeric)?.number.toIntOrNull()
    val minor
        get() = (parts.takeIf { it.size > 1 && it[0] is Numeric }
            ?.get(1) as? Numeric)?.number.toIntOrNull()
    val micro
        get() = (parts.takeIf { it.size > 2 && it[0] is Numeric && it[1] is Numeric }
            ?.get(2) as? Numeric)?.number.toIntOrNull()

    /**
     * Return a Version suitable as an exclusive upper bound from considering the parts
     * of this version up to [prefixSize] as a prefix.
     */
    fun nextPrefix(prefixSize: Int): Version {
        if (parts.size < prefixSize) {
            return Version(
                parts + List(prefixSize - parts.size) { Numeric("0", ZERO) },
                separators.let {
                    it.dropLast(1) + List(prefixSize - parts.size) { Separator.DOT } + it.last()
                },
                true
            )
        }
        else {
            return Version(
                parts.subList(0, prefixSize-1) + parts[prefixSize-1].next(),
                separators.subList(0, prefixSize-1) + Separator.EMPTY,
                true
            )
        }
    }

    /**
     * Return a Version suitable as an exclusive upper bound from considering this version as
     * a prefix.
     */
    fun nextPrefix(): Version = nextPrefix(parts.size)

    /**
     * Return a [Version] corresponding to the prefix of this version: if it is not an infimum,
     * the return value is equal to this version; if it is an infimum, the return value is the
     * specified prefix.
     */
    fun prefixVersion(): Version = Version(parts, separators, false)

    override fun compareTo(other: Version): Int {
        val thisParts = this.extendedParts(other.parts.size + 1)
        val otherParts = other.extendedParts(this.parts.size + 1)
        val partsComparisons = thisParts.zip(otherParts).map { it.first.compareTo(it.second) }
        partsComparisons.firstOrNull { it != 0 }?.let { return it }

        // by extending (above), if both are prefix infima we have compared all the parts of the
        // longer prefix with the implicit DEVs in the shortest; if one is a prefix infimum,
        // we have exhausted the explicit parts of the non-infimum.
        if (this.isPrefixInfimum && other.isPrefixInfimum) return 0
        if (this.isPrefixInfimum) return -1
        if (other.isPrefixInfimum) return 1

        if (parts.size == other.parts.size) return 0
        return if (parts.size > other.parts.size) {
            when (parts[other.parts.size]) {
                is Numeric -> 1
                else -> -1
            }
        }
        else {
            when (other.parts[parts.size]) {
                is Numeric -> -1
                else -> 1
            }
        }
    }
    override fun equals(other: Any?) = when(other) {
        is Version -> (1 + max(this.parts.size, other.parts.size)).let { size ->
            this.extendedParts(size) == other.extendedParts(size)
        }
        else -> false
    }
    override fun hashCode() = Objects.hash(this.parts, this.isPrefixInfimum)

    override fun toString() = parts
        .zip(separators) { part, separator -> "$part$separator" }
        .joinToString(separator = "")
        .let { versionString ->
            when {
                isPrefixInfimum -> "prefix infimum version for \"$versionString\""
                else -> versionString
            }
        }

    companion object {
        sealed interface ParseState {
            fun createPart(sb: StringBuffer): Part
            object EMPTY: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    return NONNUMERIC.createPart(sb)
                }
            }
            object NUMERIC: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    val string = sb.toString()
                    return Numeric(string, BigInteger(string))
                }
            }
            object NONNUMERIC: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    val string = sb.toString()
                    return when (string.lowercase(Locale.US)) {
                        "dev" -> DEV(string)
                        "rc" -> RC(string)
                        "snapshot" -> SNAPSHOT(string)
                        "final" -> FINAL(string)
                        "ga" -> GA(string)
                        "release" -> RELEASE(string)
                        "sp" -> SP(string)
                        else -> NonNumeric(string)
                    }
                }
            }
        }

        private fun doParse(string: String, prefixInfimum: Boolean): Version {
            val sb = StringBuffer()
            val parts = mutableListOf<Part>()
            val separators = mutableListOf<Separator>()
            var parseState: ParseState = ParseState.EMPTY
            for (char in string) {
                val nextParseState = when {
                    char in '0'..'9' -> ParseState.NUMERIC
                    Separator.values().mapNotNull { it.char }.contains(char) -> ParseState.EMPTY
                    else -> ParseState.NONNUMERIC
                }
                if (nextParseState == ParseState.EMPTY) {
                    parts.add(parseState.createPart(sb))
                    separators.add(Separator.values().first { it.char == char })
                    sb.setLength(0)
                }
                else {
                    if (parseState == nextParseState || parseState == ParseState.EMPTY) {
                        sb.append(char)
                    }
                    else {
                        parts.add(parseState.createPart(sb))
                        separators.add(Separator.EMPTY)
                        sb.setLength(0)
                        sb.append(char)
                    }
                }
                parseState = nextParseState
            }
            parts.add(parseState.createPart(sb))
            separators.add(Separator.EMPTY)
            return Version(parts, separators, prefixInfimum)
        }

        /**
         * Parse a string corresponding to an exact version (as defined by Gradle).  The result
         * is Comparable, implementing the ordering described in the Gradle user guide under
         * "Version ordering", compatible with determining whether a particular version is
         * included in a range (but not, directly, implementing the concept of "base version"
         * used in conflict resolution).
         */
        fun parse(string: String): Version = doParse(string, false)

        /**
         * Parse a string corresponding to a version, and return a [Version] which represents
         * the least possible version with the given string as a prefix (including an implicit
         * separator).  Conceptually, this is the same as the version denoted by [string] followed
         * by an infinite sequence of `dev` parts.
         */
        fun prefixInfimum(string: String): Version = doParse(string, true)
    }
}

fun BigInteger?.toIntOrNull() = when {
    this == null -> null
    this > BigInteger.valueOf(Int.MAX_VALUE.toLong()) -> null
    this < BigInteger.valueOf(Int.MIN_VALUE.toLong()) -> null
    else -> this.intValueExact()
}

enum class Separator(val char: Char?) {
    EMPTY(null),
    DOT('.'),
    DASH('-'),
    UNDERSCORE('_'),
    PLUS('+'),

    ;
    override fun toString() = char?.let { "$it" } ?: ""
}

sealed class Part(protected val string: String) : Comparable<Part> {
    abstract fun next(): Part
    override fun toString() = string
}

class DEV(string: String) : Part(string) {
    override fun next() = NonNumeric("")
    override fun compareTo(other: Part) = if (other is DEV) 0 else -1
    override fun equals(other: Any?) = other is DEV
    override fun hashCode() = Objects.hashCode("dev")
}

class NonNumeric(string: String) : Part(string) {
    override fun next() = NonNumeric("$string\u0000")
    override fun compareTo(other: Part) = when (other) {
        is DEV -> 1
        is Special, is Numeric -> -1
        is NonNumeric -> this.string.compareTo(other.string)
    }
    override fun equals(other: Any?) = when(other) {
        is NonNumeric -> this.string == other.string
        else -> false
    }
    override fun hashCode() = Objects.hash(this.string)
}

sealed class Special(string: String, val ordinal: Int) : Part(string) {
    override fun compareTo(other: Part) = when (other) {
        is Special -> this.ordinal.compareTo(other.ordinal)
        is Numeric -> -1
        is DEV, is NonNumeric -> 1
    }
    override fun equals(other: Any?) = when(other) {
        is Special -> this.ordinal == other.ordinal
        else -> false
    }
    override fun hashCode() = Objects.hash(this.ordinal)
}

class RC(string: String): Special(string, 0) {
    override fun next() = SNAPSHOT("snapshot")
}
class SNAPSHOT(string: String): Special(string, 1) {
    override fun next() = FINAL("final")
}
class FINAL(string: String): Special(string, 2) {
    override fun next() = GA("ga")
}
class GA(string: String): Special(string, 3) {
    override fun next() = RELEASE("release")
}
class RELEASE(string: String): Special(string, 4) {
    override fun next() = SP("sp")
}
class SP(string: String): Special(string, 5) {
    override fun next() = Numeric("0", ZERO)
}

class Numeric(string: String, val number: BigInteger) : Part(string) {
    override fun next() = Numeric("${number + ONE}", number + ONE)
    override fun compareTo(other: Part) = when (other) {
        is Numeric -> this.number.compareTo(other.number)
        is DEV, is NonNumeric, is Special -> 1
    }
    override fun equals(other: Any?) = when (other) {
        is Numeric -> this.number == other.number
        else -> false
    }
    override fun hashCode() = Objects.hashCode(this.number)
}
