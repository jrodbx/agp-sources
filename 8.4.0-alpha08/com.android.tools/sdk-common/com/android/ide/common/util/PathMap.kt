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
 * A collection that maps [PathString] instances onto arbitrary data. Provides efficient
 * algorithms for locating values based on a key prefix and locating the value matching the
 * longest prefix of a given path.
 */
interface PathMap<out T> {
    /**
     * Returns the sequence of values in this [PathMap].
     */
    val values: Sequence<T>

    /**
     * Returns true iff any key in the map starts with the given prefix.
     */
    fun containsKeyStartingWith(possiblePrefix: PathString): Boolean

    /**
     * Returns all entries whose keys start with the given prefix.
     */
    fun findAllStartingWith(possiblePrefix: PathString): Sequence<T>

    /**
     * Looks up an exact match for the given key in the map.
     */
    operator fun get(key: PathString): T?

    /**
     * Returns true of this map contains a prefix of the given key or the key itself.
     */
    fun containsPrefixOf(key: PathString): Boolean

    /**
     * Returns the value associated with the longest prefix of the given key. Returns null if either
     * the value is null or the map contains no values for the given key.  Runs in O(1) with respect
     * to the number of entries in the map.
     */
    fun findMostSpecific(key: PathString): T?
}
