/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.utils

import com.android.SdkConstants

/**
 * Safely set up a session for tokenizing command-lines in bulk and mapping them to some
 * result value.
 *
 * The [indexes] buffer is private, is shared between multiple tokenization calls, and is
 * grown on demand for larger command-lines.
 *
 */
class TokenizedCommandLineMap<T>(
    val raw: Boolean,
    val platform: Int = SdkConstants.currentPlatform(),
    val normalize: (tokens: TokenizedCommandLine, sourceFile: String) -> Unit = { _, _ -> }
) {
    private var indexes = intArrayOf()
    var hashFunction =
        { tokens: TokenizedCommandLine -> tokens.computeNormalizedCommandLineHashCode() }

    // The first level of this map is the hashCode from TokenizedCommandLine.
    // TokenizedCommandLine is mutable so it's not a good idea to use it as a
    // direct key into the map.
    // The second level is a map (usually with one element) that maps the
    // normalized command-line to user's computed value.
    private val map = mutableMapOf<Int, MutableMap<String, T>>()

    // Reverse map from normalized command-line string to the original hashcode
    // computed by TokenizedCommandLine
    private val hashCodeMap = mutableMapOf<String, Int>()

    fun computeIfAbsent(
        commandLine: String,
        sourceFile: String,
        compute: (TokenizedCommandLine) -> T
    ): T {
        if (indexes.size <= minimumSizeOfTokenizeCommandLineBuffer(commandLine)) {
            indexes = allocateTokenizeCommandLineBuffer(commandLine)
        }
        val tokens = TokenizedCommandLine(
            commandLine = commandLine,
            raw = raw,
            platform = platform,
            indexes = indexes
        )
        normalize(tokens, sourceFile)
        val hashCode = hashFunction(tokens)
        val submap = map.computeIfAbsent(hashCode) { mutableMapOf() }

        // Since TokenizedCommandLine is not the key, we have to check each element
        // for equality directly. This should be a small number of elements since 
        // they are hash collisions.
        for((key, value) in submap) {
            if (tokens.normalizedCommandLineEquals(key)) {
                return value
            }
        }

        // Our value was not found in the map, so add it now. It's okay to allocate memory
        // since we know we'll need it for the new map key.
        val normalizedCommandLine = tokens.toString()
        assert(!submap.containsKey(normalizedCommandLine))
        val value = compute(tokens)
        submap[normalizedCommandLine] = value
        hashCodeMap[normalizedCommandLine] = hashCode
        return value
    }
    
    fun size() = map.map { it.value.size }.sum()
}