/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.gradle.internal.cxx.collections

import java.util.BitSet

/**
 * Methods for dealing with directed graphs of the form Map<Int, IntSet>.
 * Where,
 * - Key is the node
 * - Value is the children
 * IntArray is used because it uses JVM's native Integer array and avoids boxing and unboxing.
 */

/**
 * Helper function to construct a graph.
 */
fun graphOf(vararg nodes : Pair<Int, Set<Int>>) : Map<Int, IntArray> {
    return nodes.associate { it.first to it.second.toIntArray() }
}

/**
 * Traverse a graph breadth-first starting with [ancestor].
 *
 * When used for interpreting build.ninja, this function returns the inputs needed to produce
 * a given output. So, for example:
 *
 *   build source.o : COMPILE source.cpp
 *   build libfoo.so : LINK source.o
 *   build foo : phony libfoo.so
 *
 * When called with graph.breadthFirst("libfoo.so"), the result is:
 *
 *   ibfoo.so, source.o, source.cpp
 *
 * That is, all the targets that contribute to "libfoo.so" directly or indirectly.
 */
fun Map<Int, IntArray>.breadthFirst(ancestor : Int) : Sequence<Int> = sequence {
    val seen = BitSet(keys.size) // Use Bits because 'seen' is dense.
    val stack = mutableListOf(ancestor)
    while (stack.isNotEmpty()) {
        val current = stack[0]
        stack.removeAt(0)
        if (seen[current]) continue
        seen.set(current)
        yield(current)
        if (!contains(current)) continue
        stack.addAll(getValue(current).asIterable())
    }
}

/**
 * Yield each ancestor of any [descendants] along with all of its [descendants]..
 * A node is considered an ancestor and descendant of itself.
 *
 * When used for interpreting build.ninja, this function finds all the targets that eventually
 * lead to a given output file. For example,
 *
 *   build libfoo.so : LINK source.o
 *   build foo : phony libfoo.so
 *
 * When called with graph.ancestors("libfoo.so"), the result is:
 *
 *   libfoo.so -> libfoo.so
 *   foo -> libfoo.so
 *
 * That is, all the targets that produce "libfoo.so" directly or indirectly.
 */
fun Map<Int, IntArray>.ancestors(descendants : Set<Int>)
    : Sequence<Pair<Int, IntArray>> = sequence {
    val seen = mutableMapOf<Int, IntArray>()
    for (terminal in descendants) {
        seen[terminal] = intArrayOf(terminal)
        yield(terminal to intArrayOf(terminal))
    }
    var more = true
    while (more) {
        more = false
        for ((node, children) in asIterable()) {
            // Skip if we've seen it before
            if (seen.containsKey(node)) continue
            // Skip if there are any children that have not been visited yet
            if (children.any { !seen.containsKey(it) && containsKey(it) }) continue
            val expanded = children
                .filter { seen.containsKey(it) }
                .flatMap { seen.getValue(it).asIterable() }
                .toSortedSet()
                .toIntArray()
            seen[node] = expanded
            if (expanded.isNotEmpty()) {
                yield(node to expanded)
            }
            more = true
        }
    }
}
