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

package com.android.ide.common.util

/**
 * Converts the given [Map] into a path hash map. The [PathMap.containsKeyStartingWith] with and
 * [PathMap.findAllStartingWith] methods run in linear time, but all other operations run in
 * constant time with respect to the size of the map and O(n) time with respect to the length
 * of the keys. This conversion is a fast constant-time operation since the resulting object is
 * backed by this map.
 *
 * Path hash maps are generally preferred over path tree maps in situations where the linear time
 * operations mentioned above are not needed.
 */
fun <T> Map<PathString, T>.toPathMap(): PathMap<T> {
    return PathHashMapImpl(this)
}

/**
 * An implementation of [PathMap] backed by a [Map]. [containsPrefixOf] runs in constant time,
 * and both [containsKeyStartingWith] and [findAllStartingWith] run in linear time.
 */
internal class PathHashMapImpl<out T>(private val backingMap: Map<PathString, T>) : PathMap<T> {
    override val values: Sequence<T>
        get() = backingMap.values.asSequence()

    override fun findMostSpecific(key: PathString): T? {
        var next = key.withoutTrailingSeparator()
        while (true) {
            val value = backingMap[next]
            if (value != null) {
                return value
            }
            val parent = next.parent ?: return null
            next = parent
        }
    }

    /**
     * Prefix searches are performed by an exhaustive search of the key
     * list, which is linear both in terms of map size and key length.
     */
    override fun containsKeyStartingWith(possiblePrefix: PathString): Boolean =
        backingMap.keys.asSequence().any { it.startsWith(possiblePrefix) }

    override fun findAllStartingWith(possiblePrefix: PathString): Sequence<T> =
        backingMap.entries.filter { it.key.startsWith(possiblePrefix) }.map { it.value }.asSequence()

    override fun get(key: PathString): T? =
        backingMap[key.withoutTrailingSeparator()]

    /**
     * The most specific key is located by repeatedly shortening the search path and looking up the
     * path in the map, which should run in linear time with respect to path length but constant time
     * with respect to map size.
     */
    override fun containsPrefixOf(key: PathString): Boolean {
        var next = key.withoutTrailingSeparator()
        while (true) {
            if (backingMap.containsKey(next)) {
                return true
            }
            val parent = next.parent ?: return false
            next = parent
        }
    }
}