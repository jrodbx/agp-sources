/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("ConfigPathUtil")
package com.android.projectmodel

/**
 * [ConfigPath] is a lot like [SubmodulePath] except that any of its segments can be a wildcard.
 * It is a pattern that can match one or more build artifacts or variants. [ConfigPath]
 * instances are also the main identifier for [Config] instances within a [ConfigTable].
 * [ConfigPath] instances are a lot like variant names in Gradle. A path consists of a list of
 * strings, where the Nth string corresponds to a value in the Nth dimension of the [ConfigTable].
 *
 * For example, the path to the lowdpiProductionRelease variant's test artifact would be ("lowdpi",
 * "production", "release", "test"). The (null, null, "test") filter would match the test artifact
 * in all variants of the same [ConfigTable].
 *
 * [ConfigPath] instances are also built from lists of strings. A null value matches any string
 * at that path segment. A non-null value must match exactly. If the path is longer than the filter
 * segments, the filter only tests the prefix of the path.
 *
 * The [ConfigPath] with a null segments list matches nothing. The [ConfigPath] with an empty
 * segments list matches everything.
 *
 * [ConfigPath] segments are ordered, where the nth element in the path matches the nth dimension
 * of the [ConfigTableSchema].
 */
data class ConfigPath internal constructor(
        /**
         * List of segments to be tested by the filter. Any non-null value must match exactly. Null
         * entries match anything. If the list itself is null, the filter matches nothing. If the
         * list is empty, the filter matches everything. Must not end in a null.
         */
        val segments: List<String?>?
) {
    fun init() {
        if (segments != null && segments.isNotEmpty()) {
            if (segments.last() == null) {
                throw IllegalArgumentException("The segments list '$segments' must not end with a null")
            }
        }
    }

    /**
     * Returns a new filter that matches the set of artifacts that would be matched by both this
     * filter and [other].
     */
    fun intersect(other: ConfigPath): ConfigPath {
        if (segments == null || other.segments == null) {
            return matchNoArtifacts()
        }
        val commonSize = Math.min(segments.size, other.segments.size)
        return ConfigPath((0 until commonSize).map {
            if (segments[it] == null) {
                other.segments[it]
            } else if (other.segments[it] == null || segments[it] == other.segments[it]) {
                segments[it]
            } else {
                return matchNoArtifacts()
            }
        } + segments.subListFrom(commonSize) + other.segments.subListFrom(commonSize))
    }

    /**
     * Returns true iff this path and [other] have a non-empty intersection.
     */
    fun intersects(other: ConfigPath): Boolean {
        if (segments == null || other.segments == null) {
            return false
        }
        for (i in 0 until Math.min(other.segments.size, segments.size)) {
            if (segments[i] != null && other.segments[i] != null && segments[i] != other.segments[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true if this [ConfigPath] contains the artifact or variant identified by the given [SubmodulePath].
     */
    fun contains(other: SubmodulePath): Boolean = contains(other.toConfigPath())

    /**
     * Returns true if this path completely contains the region of the [ConfigTable] matched
     * by [other].
     */
    fun contains(other: ConfigPath): Boolean {
        other.segments ?: return true
        segments ?: return false

        if (segments.size > other.segments.size) {
            return false
        }

        for (i in 0 until Math.min(other.segments.size, segments.size)) {
            if (segments[i] != null) {
                if (other.segments[i] == null || segments[i] != other.segments[i]) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Returns the parent of this [ConfigPath]. That is, it returns the prefix containing everything
     * except the last segment. Returns this if the path has no parent.
     */
    fun parent(): ConfigPath {
        segments ?: return this
        return if (segments.isEmpty()) this else {
            ConfigPath(segments.subList(0, segments.size - 1))
        }
    }

    /**
     * Returns true iff this filter matches all artifacts in the [ConfigTable].
     */
    @get:JvmName("matchesEverything")
    val matchesEverything: Boolean = segments == emptyList<String?>()

    /**
     * Returns true if this object identifies a non-empty region in the [ConfigTable].
     */
    @get:JvmName("matchesAnything")
    val matchesAnything: Boolean = segments != null

    /**
     * Returns a string using the same naming convention used for Android variants and source
     * providers. Null segments are omitted, and the remaining segments are concatenated in
     * camelCase with the leading segment lowercase. If the path matches everything, it is called
     * "main".
     */
    val simpleName: String get() {
        segments ?: return ""
        return toSimpleName(segments.filterNotNull())
    }

    override fun toString(): String {
        segments ?: return ""
        if (segments.isEmpty()) return "*"
        return segments.mapNotNull { it ?: "*" }.joinToString("/")
    }
}

/**
 * Returns the [ConfigPath] that matches nothing.
 */
fun matchNoArtifacts(): ConfigPath = matchNoneFilter

/**
 * Returns the [ConfigPath] that matches everything.
 */
fun matchAllArtifacts(): ConfigPath = matchAllFilter

/**
 * Returns a [ConfigPath] that matches the given [SubmodulePath] and its subpaths.
 */
fun SubmodulePath.toConfigPath() = ConfigPath(segments)

/**
 * Returns a [ConfigPath] that matches artifact paths with the given segments. Nulls are treated
 * as a wildcard that matches any string. Any non-null segments must match exactly.

 * For example, listOf(null, null, "test") would match the "test" artifact in all variants
 * (in a 2D [ConfigTable]).
 */
fun matchArtifactsWith(filterPath: List<String?>?): ConfigPath =
        ConfigPath(filterPath?.subList(0, indexOfLastNonNull(filterPath) + 1))

/**
 * Returns a [ConfigPath] that matches the given value in the given dimension. All other segments
 * are the "match everything" wildcard. For example, if passed 3, "myApp", it would return
 * the path [null, null, "myApp"].
 */
fun matchDimension(dimensionNumber: Int, value: String): ConfigPath =
        matchArtifactsWith((0 until dimensionNumber).map { null } + value)

/**
 * Returns a [ConfigPath] that matches artifact paths with the given segments. Segments are
 * separated with a forward slash. A segment containing a single asterisk is treated a wildcard
 * that matches any string. The empty string is treated as a path that matches nothing. A string
 * containing only a single asterisk is a path that matches everything. This accepts the same
 * sort of strings that are returned from the [ConfigPath.toString] method.
 */
fun matchArtifactsWith(filterPath: String): ConfigPath {
    return if (filterPath.isEmpty())
        matchNoArtifacts()
    else matchArtifactsWith(
            filterPath.split('/').map { if (it == "*") null else it }.let {
                if (it.isEmpty()) {
                    null
                } else it
            })
}

private fun <T> List<T>.subListFrom(index: Int): List<T> {
    return if (index < size) {
        subList(index, size)
    } else {
        emptyList()
    }
}

private val matchNoneFilter = ConfigPath(null)
private val matchAllFilter = ConfigPath(emptyList())

private fun indexOfLastNonNull(toTest: List<String?>): Int =
        toTest.indices.indexOfLast { toTest[it] != null }
