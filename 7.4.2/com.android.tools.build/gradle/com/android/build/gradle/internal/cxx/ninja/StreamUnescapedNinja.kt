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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.AFTER_COMMENT_HASH
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.AFTER_FIRST_DOLLAR
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.IN_DOLLAR_CURLY_VARIABLE
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.IN_DOLLAR_VARIABLE
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.START
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeState.START_AFTER_NON_WHITESPACE
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.LiteralType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableWithCurliesType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.CommentType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedSpaceType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedDollarType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedColonType
import java.io.Reader

/**
 * Implements Ninja string lexical syntax according to the spec at:
 *   https://github.com/ninja-build/ninja/blob/master/doc/manual.asciidoc#lexical-syntax
 *
 * There is only one escape character, $, and it has the following behaviors:
 * $ followed by a newline
 *   escape the newline (continue the current line across a line break).
 * $ followed by text
 *   a variable reference.
 * ${varname}
 *  alternate syntax for $varname.
 * $ followed by space
 *   a space. (This is only necessary in lists of paths, where a space would otherwise separate filenames.)
 * $:
 *   a colon. (This is only necessary in build lines, where a colon would otherwise terminate the list of outputs.)
 * $$
 *  a literal $.
 */
fun Reader.streamUnescapedNinja(
    action : (
        // The type of token
        type : NinjaUnescapeTokenType,
        // The string content of the token. Must be consumed or copied inside 'action'.
        token : CharSequence) -> Unit) {
    var state = START
    val sb = StringBuilder()
    var peeked : Int? = null
    fun read() : Int {
        val result = peeked ?: this.read()
        peeked = null
        return result
    }
    fun peek() : Int {
        peeked = peeked ?: this.read()
        return peeked!!
    }
    fun sendExisting() {
        if (sb.isNotEmpty()) {
            action(LiteralType, sb)
            sb.clear()
        }
    }
    while(true) {
        val next = read()
        if (next == -1) {
            if (sb.isNotEmpty()) {
                when(state) {
                    START, START_AFTER_NON_WHITESPACE ->
                        action(LiteralType, sb)
                    IN_DOLLAR_VARIABLE ->
                        action(VariableType, sb)
                    IN_DOLLAR_CURLY_VARIABLE ->
                        action(VariableWithCurliesType, sb)
                    AFTER_COMMENT_HASH ->
                        action(CommentType, sb)
                    else -> error("$state")
                }
            }
            return
        }
        val ch = next.toChar()
        var done = false
        while(!done) {
            done = true
            state = when (state) {
                START -> when (ch) {
                    '$' -> {
                        sendExisting()
                        AFTER_FIRST_DOLLAR
                    }
                    '#' -> {
                        sendExisting()
                        AFTER_COMMENT_HASH
                    }
                    else -> {
                        sb.append(ch)
                        if (ch.isWhitespace()) START else START_AFTER_NON_WHITESPACE
                    }
                }
                START_AFTER_NON_WHITESPACE -> when (ch) {
                    '$' -> {
                        sendExisting()
                        AFTER_FIRST_DOLLAR
                    }
                    '\r', '\n' -> {
                        sb.append(ch)
                        START
                    }
                    else -> {
                        sb.append(ch)
                        START_AFTER_NON_WHITESPACE
                    }
                }
                ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION ->
                    when(ch) {
                        ' ' -> ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
                        else -> {
                            done = false
                            START
                        }
                    }
                AFTER_FIRST_DOLLAR -> when (ch) {
                    '$' -> {
                        action(EscapedDollarType, "$$")
                        START_AFTER_NON_WHITESPACE
                    }
                    ':' -> {
                        action(EscapedColonType, "$:")
                        START_AFTER_NON_WHITESPACE
                    }
                    ' ' -> {
                        action(EscapedSpaceType, "$ ")
                        START_AFTER_NON_WHITESPACE
                    }
                    '{' ->
                        IN_DOLLAR_CURLY_VARIABLE
                    '\r' -> {
                        if (peek() != -1 && peek().toChar() == '\n') {
                            read() // Skip the peeked char
                        }
                        ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
                    }
                    '\n' -> ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
                    else -> {
                        sb.append(ch)
                        IN_DOLLAR_VARIABLE
                    }
                }
                IN_DOLLAR_VARIABLE -> when (ch) {
                    ':' -> {
                        action(VariableType, sb)
                        sb.clear()
                        sb.append(":")
                        START_AFTER_NON_WHITESPACE
                    }
                    '$' -> {
                        action(VariableType, sb)
                        sb.clear()
                        AFTER_FIRST_DOLLAR
                    }
                    '#' -> {
                        action(VariableType, sb)
                        sb.clear()
                        AFTER_COMMENT_HASH
                    }
                    '\r', '\n', ' '-> {
                        action(VariableType, sb)
                        sb.clear()
                        sb.append(ch)
                        START
                    }
                    else -> {
                        sb.append(ch)
                        IN_DOLLAR_VARIABLE
                    }
                }
                IN_DOLLAR_CURLY_VARIABLE -> when (ch) {
                    '}' -> {
                        action(VariableWithCurliesType, sb)
                        sb.clear()
                        START_AFTER_NON_WHITESPACE
                    }
                    else -> {
                        sb.append(ch)
                        IN_DOLLAR_CURLY_VARIABLE
                    }
                }
                AFTER_COMMENT_HASH -> when (ch) {
                    '\r', '\n' -> {
                        if (sb.isNotEmpty()) {
                            action(CommentType, sb)
                            sb.clear()
                        }
                        sb.append(ch)
                        START
                    }
                    else -> {
                        sb.append(ch)
                        AFTER_COMMENT_HASH
                    }
                }
                else -> error("$ch")
            }
        }
    }
}

/**
 * The type of token resulting from streaming a build.ninja file.
 */
enum class NinjaUnescapeTokenType {
    VariableType,
    VariableWithCurliesType,
    LiteralType,
    CommentType,
    EscapedDollarType,
    EscapedColonType,
    EscapedSpaceType;
    fun size(value : CharSequence) = when(this) {
        VariableType -> value.length + 1
        VariableWithCurliesType -> value.length + 3
        LiteralType -> value.length
        CommentType -> value.length + 1
        EscapedDollarType -> 2
        EscapedColonType -> 2
        EscapedSpaceType -> 2
    }
    fun charAt(value : CharSequence, index : Int) = when(this) {
        VariableType ->
            when (index) {
                0 -> '$'
                else -> value[index - 1]
            }
        VariableWithCurliesType ->
            when (index) {
                0 -> '$'
                1 -> '{'
                value.length + 2 -> '}'
                else -> value[index - 2]
            }
        LiteralType -> value[index]
        CommentType -> if(index == 0) '#' else value[index - 1]
        EscapedDollarType -> '$'
        EscapedColonType -> if (index == 0) '$' else ':'
        EscapedSpaceType -> if (index == 0) '$' else ' '
    }
}

/**
 * Private enum to track the current state of the unescape parser.
 */
private enum class NinjaUnescapeState {
    START,
    START_AFTER_NON_WHITESPACE,
    AFTER_FIRST_DOLLAR,
    IN_DOLLAR_CURLY_VARIABLE,
    IN_DOLLAR_VARIABLE,
    AFTER_COMMENT_HASH,
    ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
}
