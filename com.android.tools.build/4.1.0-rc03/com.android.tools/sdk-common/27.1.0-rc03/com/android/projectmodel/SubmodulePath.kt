/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("SubmodulePathUtil")
package com.android.projectmodel

/**
 * Holds a path that uniquely identifies an [Artifact] or [Variant] within an [AndroidSubmodule].
 * [SubmodulePath] instances must conform to the [ConfigTableSchema] for their [AndroidSubmodule].
 * [Artifact] instances are always identified by the longest-possible paths in the schema. The
 * parent of an [Artifact] is always the path to its [Variant].
 */
data class SubmodulePath internal constructor(
    val segments: List<String> = emptyList()
) {
    /**
     * Adds a segment to this [SubmodulePath].
     */
    operator fun plus(nextSegment: String): SubmodulePath {
        return submodulePathOf(segments + nextSegment)
    }

    /**
     * Returns the parent of this [SubmodulePath]. That is, it returns the prefix containing everything
     * except the last segment. Returns this if the path has no parent.
     */
    fun parent(): SubmodulePath {
        return if (segments.isEmpty()) this else {
            submodulePathOf(segments.subList(0, segments.size - 1))
        }
    }

    /**
     * The last segment of this path, or the empty string if the path is empty.
     */
    val lastSegment get() = if (segments.isEmpty()) "" else segments.last()

    /**
     * Returns the simple name for this path, which is a string in the same format that Gradle
     * uses to name Variants. See [ConfigPath.simpleName] for details.
     */
    val simpleName get() = toSimpleName(segments)

    override fun toString(): String {
        return segments.joinToString("/")
    }
}

val emptySubmodulePath = SubmodulePath(listOf())

/**
 * Returns a [SubmodulePath] given a string with segments separated by slashes.
 */
fun submodulePathForString(pathString: String) =
    if (pathString.isEmpty()) emptySubmodulePath else SubmodulePath(pathString.split('/'))

/**
 * Returns a [SubmodulePath] given a list of segments.
 */
fun submodulePathOf(segments: List<String>) =
    if (segments.isEmpty()) emptySubmodulePath else SubmodulePath(segments)

/**
 * Returns a [SubmodulePath] given a list of segments.
 */
fun submodulePathOf(vararg segments: String) = submodulePathOf(segments.toList())

internal fun toSimpleName(input: List<String>) = if (input.isEmpty()) {
    "main"
} else toCamelCase(input)

internal fun toCamelCase(input: List<String>) =
    input.mapIndexed { index, s -> if (index == 0) s.toLowerCase() else s.toLowerCase().capitalize() }.joinToString(
        ""
    )
