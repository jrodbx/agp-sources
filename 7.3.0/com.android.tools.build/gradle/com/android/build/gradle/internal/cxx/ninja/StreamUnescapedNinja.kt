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
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedColon
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Comment
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedDollar
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Literal
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedSpace
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Variable
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
fun Reader.streamUnescapedNinja(action : (NinjaUnescapedToken) -> Unit) {
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
            action(Literal(sb.toString()))
            sb.clear()
        }
    }
    while(true) {
        val next = read()
        if (next == -1) {
            if (sb.isNotEmpty()) {
                when(state) {
                    START ->
                        action(Literal(sb.toString()))
                    IN_DOLLAR_VARIABLE ->
                        action(Variable(sb.toString(), false))
                    IN_DOLLAR_CURLY_VARIABLE ->
                        action(Variable(sb.toString(), true))
                    AFTER_COMMENT_HASH ->
                        action(Comment(sb.toString()))
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
                        START
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
                        action(EscapedDollar)
                        START
                    }
                    ':' -> {
                        action(EscapedColon)
                        START
                    }
                    ' ' -> {
                        action(EscapedSpace)
                        START
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
                        action(Variable(sb.toString(), false))
                        sb.clear()
                        sb.append(":")
                        START
                    }
                    '$' -> {
                        action(Variable(sb.toString(), false))
                        sb.clear()
                        AFTER_FIRST_DOLLAR
                    }
                    '#' -> {
                        action(Variable(sb.toString(), false))
                        sb.clear()
                        AFTER_COMMENT_HASH
                    }
                    '\r', '\n',' '-> {
                        action(Variable(sb.toString(), false))
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
                        action(Variable(sb.toString(), true))
                        sb.clear()
                        START
                    }
                    else -> {
                        sb.append(ch)
                        IN_DOLLAR_CURLY_VARIABLE
                    }
                }
                AFTER_COMMENT_HASH -> when (ch) {
                    '\r', '\n' -> {
                        if (sb.isNotEmpty()) {
                            action(Comment(sb.toString()))
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
 * A token resulting from streaming a build.ninja file.
 * The content of the class, for example [Variable] name, will be unescaped.
 * The toString() will return the original escaped value.
 */
sealed class NinjaUnescapedToken {
    data class Variable(val name : String, val curlies : Boolean) : NinjaUnescapedToken() {
        override fun toString(): String {
            val sb = StringBuilder()
            sb.append('$')
            if (curlies) sb.append('{')
            sb.append(name)
            if (curlies) sb.append('}')
            return sb.toString()
        }
    }
    open class Literal(val value : String) : NinjaUnescapedToken() {
        override fun toString() = value
    }
    open class Comment(val text : String) : NinjaUnescapedToken() {
        override fun toString() = "#$text"
    }
    object EscapedDollar :NinjaUnescapedToken() {
        override fun toString() = "$$"
    }
    object EscapedColon : NinjaUnescapedToken() {
        override fun toString() = "$:"
    }
    object EscapedSpace : NinjaUnescapedToken() {
        override fun toString() = "$ "
    }
}

/**
 * Private enum to track the current state of the unescape parser.
 */
private enum class NinjaUnescapeState {
    START,
    AFTER_FIRST_DOLLAR,
    IN_DOLLAR_CURLY_VARIABLE,
    IN_DOLLAR_VARIABLE,
    AFTER_COMMENT_HASH,
    ABSORB_WHITESPACE_AFTER_LINE_CONTINUATION
}

