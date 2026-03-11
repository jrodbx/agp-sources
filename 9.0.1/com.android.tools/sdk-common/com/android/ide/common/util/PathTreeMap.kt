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

import java.net.URI
import java.util.ArrayDeque

/**
 * Returns a path tree map containing the given pairs. All operations on a path tree map run in
 * constant time with respect to the number of elements, but there is a much higher constant
 * overhead than for path hash maps. This conversion method runs in linear time.
 */
fun <T> pathTreeMapOf(vararg pairs: Pair<PathString, T>): MutablePathMap<T> {
    val result = PathTreeMapImpl<T>()

    for (next in pairs) {
        result.put(next.first, next.second)
    }

    return result
}

/**
 * Returns an empty path tree map. All operations on a path tree map run in
 * constant time with respect to the number of elements, but there is a much higher constant
 * overhead than for path hash maps. This conversion method runs in linear time.
 */
fun <T> pathTreeMapOf(): MutablePathMap<T> = PathTreeMapImpl()

/**
 * Converts the given [Map] into a tree path map. All operations on a tree map run in constant
 * time with respect to the number of elements, but there is a much higher constant
 * overhead than for path hash maps. This conversion method runs in linear time.
 */
fun <T> Map<PathString, T>.toPathTreeMap(): MutablePathMap<T> {
    val result = PathTreeMapImpl<T>()

    for (next in entries) {
        result.put(next.key, next.value)
    }

    return result
}

/**
 * Represents a node in the [PathTreeMapImpl]. Parent nodes link to their children, but children
 * do not link back to their parents. Different subclasses will use different mechanisms for
 * identifying and storing their children.
 */
internal abstract class PathMapEntry<T>(
    /**
     * Indicates the segment index that this entry will match against. A [PathString] will only
     * match this entry if its [PathString.nameCount] matches this value exactly. Negative values
     * are permitted, and indicate an entry that can never be considered an exact match for
     * any [PathString]. This is used when creating entries to represent protocols or roots
     * which are just containers for the subtrees that match actual paths.
     */
    val segmentIndex: Int
) {
    /**
     * True iff this entry was explicitly inserted into the map. If false, this entry was just
     * created as a container for other entries but it should not be exposed as an entry in the map
     * via the public interface.
     */
    var explicitlyInserted: Boolean = false
    /**
     * The value that was inserted into the map, if one exists.
     */
    var value: T? = null

    /**
     * Returns the child corresponding to the given path, or creates it if necessary.
     */
    abstract fun getOrCreateChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T>

    /**
     * Returns the child corresponding to the given path, or null if none.
     */
    abstract fun getChild(filesystem: URI, root: String?, segments: List<String>): PathMapEntry<T>?

    /**
     * Returns an iterator for iterating over the immediate children of this entry.
     */
    abstract fun iterator(): Iterator<PathMapEntry<T>>
}

/**
 * Implementation of [PathMapEntry] that indexes its children using the filesystem uri.
 */
internal class ProtocolPathMapEntry<T> : PathMapEntry<T>(-2) {
    val filesystems = HashMap<URI, PathMapEntry<T>>()

    override fun getOrCreateChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T> =
        filesystems.getOrPut(filesystem, { RootPathMapEntry() })

    override fun getChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T>? = filesystems[filesystem]

    override fun iterator(): Iterator<PathMapEntry<T>> =
        filesystems.values.iterator()
}

/**
 * Implementation of [PathMapEntry] that indexes its children using the root string.
 */
internal class RootPathMapEntry<T> : PathMapEntry<T>(-1) {
    val roots = HashMap<String?, PathMapEntry<T>>()

    override fun getOrCreateChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T> =
        roots.getOrPut(root, { SegmentPathMapEntry(0) })

    override fun getChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T>? = roots[root]

    override fun iterator(): Iterator<PathMapEntry<T>> =
        roots.values.iterator()
}

/**
 * Implementation of [PathMapEntry] that indexes its children using a particular segment
 * of the path.
 */
internal class SegmentPathMapEntry<T>(nameCount: Int) : PathMapEntry<T>(nameCount) {
    val children = HashMap<String, PathMapEntry<T>>()

    override fun getOrCreateChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T> {
        val segment = segments[segmentIndex]
        return children.getOrPut(segment, { SegmentPathMapEntry(segmentIndex + 1) })
    }

    override fun getChild(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T>? = children[segments[segmentIndex]]

    override fun iterator(): Iterator<PathMapEntry<T>> {
        return children.values.iterator()
    }
}

internal class PathTreeMapIterator<T>(startNode: PathMapEntry<T>?) : Iterator<T> {
    private val iteratorStack = ArrayDeque<Iterator<PathMapEntry<T>>>()
    var next: PathMapEntry<T>? = null

    init {
        if (startNode != null) {
            iteratorStack.addLast(startNode.iterator())
            if (startNode.explicitlyInserted) {
                next = startNode
            }
            advance()
        }
    }

    override fun hasNext(): Boolean {
        return next != null
    }

    private fun advance() {
        // If there's no parent iterators to fall back to, then we've iterated over
        // everything and there is no next element
        while (next == null && !iteratorStack.isEmpty()) {
            val iterator = iteratorStack.last()

            if (!iterator.hasNext()) {
                iteratorStack.removeLast()
            } else {
                val nextChild = iterator.next()
                if (nextChild.explicitlyInserted) {
                    next = nextChild
                }
                iteratorStack.addLast(nextChild.iterator())
            }
        }
    }

    override fun next(): T {
        val result = next ?: throw NoSuchElementException()
        next = null
        advance()

        return result.value!!
    }
}

/**
 * An implementation of [MutablePathMap] based on a tree. All operations run in constant time, but
 * there is a higher coefficient than with hashmap-based implementations.
 */
internal class PathTreeMapImpl<T> : MutablePathMap<T>, Sequence<T> {
    private val tree = ProtocolPathMapEntry<T>()

    private fun findMostSpecificEntry(key: PathString): PathMapEntry<T>? {
        val filesystem = key.filesystemUri
        val root = key.root?.rawPath
        val segments = key.segments

        var mostSpecific: PathMapEntry<T>? = null
        var current: PathMapEntry<T> = tree
        while (current.segmentIndex < segments.size) {
            val child = current.getChild(filesystem, root, segments) ?: break

            if (child.explicitlyInserted) {
                mostSpecific = child
            }
            current = child
        }
        return mostSpecific
    }

    override val values = this

    override fun iterator(): Iterator<T> = PathTreeMapIterator(tree)

    override fun containsKeyStartingWith(key: PathString): Boolean =
        findEntry(key) != null

    override fun containsPrefixOf(key: PathString): Boolean =
        findMostSpecificEntry(key) != null

    override fun findMostSpecific(key: PathString): T? =
        findMostSpecificEntry(key)?.value

    override operator fun get(key: PathString): T? =
        findMostSpecificEntry(key)?.let { if (it.segmentIndex == key.nameCount) it.value else null }

    override fun put(key: PathString, value: T): T? {
        val filesystem = key.filesystemUri
        val root = key.root?.rawPath
        val segments = key.segments

        val entry = getOrCreateEntry(filesystem, root, segments)
        val result = entry.value
        entry.explicitlyInserted = true
        entry.value = value
        return result
    }

    private fun findEntry(key: PathString): PathMapEntry<T>? {
        val filesystem = key.filesystemUri
        val root = key.root?.rawPath
        val segments = key.segments

        return findEntry(filesystem, root, segments)
    }

    private fun findEntry(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T>? {
        var current: PathMapEntry<T> = tree
        while (current.segmentIndex < segments.size) {
            current = current.getChild(filesystem, root, segments) ?: return null
        }
        return current
    }

    private fun getOrCreateEntry(
        filesystem: URI,
        root: String?,
        segments: List<String>
    ): PathMapEntry<T> {
        var current: PathMapEntry<T> = tree
        while (current.segmentIndex < segments.size) {
            current = current.getOrCreateChild(filesystem, root, segments)
        }
        return current
    }

    override fun findAllStartingWith(possiblePrefix: PathString): Sequence<T> =
        object : Sequence<T> {
            override fun iterator(): Iterator<T> = PathTreeMapIterator(findEntry(possiblePrefix))
        }
}
