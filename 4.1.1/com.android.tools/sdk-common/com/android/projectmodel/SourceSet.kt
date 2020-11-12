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

package com.android.projectmodel

import com.android.ide.common.util.PathString
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimaps

/**
 * Describes the filesystem for a given [Config]. It holds all source locations associated with a [Config].
 */
data class SourceSet(private val paths: Map<AndroidPathType, List<PathString>> = emptyMap()) {
    /**
     * Returns the list of sources of the given type.
     */
    operator fun get(type: AndroidPathType) = paths[type] ?: emptyList()

    /**
     * Appends the paths in [other] to the paths in this [SourceSet].
     */
    operator fun plus(other: SourceSet): SourceSet {
        if (other.isEmpty()) {
            return this
        }
        if (isEmpty()) {
            return other
        }
        val resultPaths = HashMap<AndroidPathType, List<PathString>>()
        for ((pathType, locations) in paths.entries) {
            resultPaths[pathType] = fastAppend(locations, other[pathType])
        }

        for ((pathType, locations) in other.paths.entries) {
            resultPaths.putIfAbsent(pathType, locations)
        }
        return SourceSet(resultPaths)
    }

    private fun fastAppend(first: List<PathString>, second: List<PathString>): List<PathString> {
        if (first.isEmpty()) {
            return second
        }
        if (second.isEmpty()) {
            return first
        }
        return first + second
    }

    /**
     * Returns true if there are no paths in this [SourceSet].
     */
    fun isEmpty(): Boolean {
        paths.values.forEach {
            if (!it.isEmpty()) {
                return false
            }
        }
        return true
    }

    /**
     * Converts this object into a map of [AndroidPathType] onto lists of [PathString].
     */
    val asMap get() = paths

    /**
     * The list of manifests.
     */
    val manifests get() = get(AndroidPathType.MANIFEST)

    /**
     * The list of Java roots.
     */
    val javaDirectories get() = get(AndroidPathType.JAVA)

    /**
     * The list of resource folders.
     */
    val resDirectories get() = get(AndroidPathType.RES)

    /**
     * The list of android assets folders.
     */
    val assetsDirectories get() = get(AndroidPathType.ASSETS)

    /**
     * Builder for [SourceSet] instances. Intended primarily for constructing [SourceSet] instances
     * from Java. For Kotlin, it is normally simpler to use the constructor directly.
     */
    class Builder {
        private val contents = ArrayListMultimap.create<AndroidPathType, PathString>()

        /**
         * Adds the given [path] if and only if it is not null. Returns this.
         */
        fun addIfNotNull(type: AndroidPathType, path: PathString?): Builder =
            if (path != null) add(type, path) else this

        /**
         * Adds the given [path]. Returns this.
         */
        fun add(type: AndroidPathType, path: PathString): Builder {
            contents.put(type, path); return this
        }

        /**
         * Adds the given collection of [paths]. Returns this.
         */
        fun add(type: AndroidPathType, paths: Collection<PathString>): Builder {
            contents.putAll(type, paths); return this
        }

        /**
         * Adds the given [paths]. Returns this.
         */
        fun add(type: AndroidPathType, vararg paths: PathString): Builder =
            add(type, paths.asList())

        /**
         * Builds and returns the [SourceSet] described by this builder.
         */
        fun build() = SourceSet(Multimaps.asMap(contents))
    }
}

val emptySourceSet = SourceSet()
