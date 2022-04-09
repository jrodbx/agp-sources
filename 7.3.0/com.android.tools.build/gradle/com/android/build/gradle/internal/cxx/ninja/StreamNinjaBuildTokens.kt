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

import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.DoublePipe
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.EOF
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.EOL
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Indent
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Pipe
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildToken.Text
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedColon
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Comment
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedDollar
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Literal
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedSpace
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Variable
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.AFTER_EOL
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_PIPE
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_INDENT
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_EXPRESSION
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_ASSIGNMENT_EXPRESSION
import java.io.Reader

/**
 * This function builds on and is one level higher than [streamUnescapedNinja]. Its purpose is to
 * create a stream of Ninja build tokens (called [NinjaBuildToken]) that are literals or special
 * characters recognized by ninja like '|', '||', and ':'.
 */
fun Reader.streamNinjaBuildTokens(action: (NinjaBuildToken) -> Unit) {
    val sb = StringBuilder()
    var state = AFTER_EOL
    fun sendExisting() {
        if (sb.isNotBlank()) {
            action(Text(sb.toString().trim()))
        }
        sb.clear()
    }

    streamUnescapedNinja { token ->
        state = when(token) {
            is Comment -> when(state) {
                IN_EXPRESSION -> {
                    sendExisting()
                    IN_EXPRESSION
                }
                IN_INDENT -> {
                    action(Indent)
                    IN_EXPRESSION
                }
                IN_PIPE -> {
                    action(Pipe)
                    IN_EXPRESSION
                }
                AFTER_EOL -> AFTER_EOL
                IN_ASSIGNMENT_EXPRESSION -> {
                    sb.append("#")
                    sb.append(token.text)
                    IN_ASSIGNMENT_EXPRESSION
                }
                else -> error("$state")
            }
            is EscapedDollar,
            is EscapedSpace,
            is EscapedColon,
            is Variable,
            is Literal -> {
                for(ch in token.toString()) {
                    state = when(ch) {
                        ' ' -> when(state) {
                            IN_PIPE -> {
                                action(Pipe)
                                IN_EXPRESSION
                            }
                            IN_INDENT -> IN_INDENT
                            AFTER_EOL -> IN_INDENT
                            IN_EXPRESSION -> {
                                if (token is EscapedSpace) {
                                    sb.append(" ")
                                } else {
                                    sendExisting()
                                }
                                IN_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append(ch)
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            else -> error("$state")
                        }
                        '|' -> when(state) {
                            IN_PIPE -> {
                                action(DoublePipe)
                                IN_EXPRESSION
                            }
                            IN_INDENT -> {
                                action(Indent)
                                IN_EXPRESSION
                            }
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                sendExisting()
                                IN_PIPE
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append('|')
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            else -> error("$state")
                        }
                        '\r', '\n' -> when(state) {
                            IN_PIPE -> {
                                action(Pipe)
                                action(EOL)
                                AFTER_EOL
                            }
                            AFTER_EOL -> AFTER_EOL
                            IN_INDENT -> {
                                sendExisting()
                                action(EOL)
                                IN_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION,
                            IN_EXPRESSION -> {
                                sendExisting()
                                action(EOL)
                                AFTER_EOL
                            }
                            else -> error("$state")
                        }
                        '=' -> when(state) {
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                sendExisting()
                                action(Text(ch.toString()))
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append("=")
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            IN_INDENT -> {
                                action(Indent)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(Pipe)
                                IN_EXPRESSION
                            }
                            else -> error("$state")
                        }
                        ':' -> when(state) {
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                if (token is EscapedColon) {
                                    sb.append(":")
                                    IN_EXPRESSION
                                } else {
                                    sendExisting()
                                    action(Text(ch.toString()))
                                    IN_EXPRESSION
                                }
                            }
                            IN_INDENT -> {
                                action(Indent)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(Pipe)
                                IN_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append(":")
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            else -> error("$state")
                        }
                        else -> when(state) {
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                sb.append(ch)
                                IN_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append(ch)
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            IN_INDENT -> {
                                action(Indent)
                                sb.append(ch)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(Pipe)
                                sb.append(ch)
                                IN_EXPRESSION
                            }
                            else -> error("$state")
                        }
                    }
                }
                state
            }
        }
    }
    sendExisting()
    if (state != AFTER_EOL) {
        action(EOL)
    }
    action(EOF)
}

/**
 * A Ninja build token like 'build', 'rule', or special characters that have syntactic meaning.
 */
sealed class NinjaBuildToken(val text : String) {
    override fun toString() = text
    class Text(text : String) : NinjaBuildToken(text)
    object Pipe : NinjaBuildToken("|")
    object DoublePipe : NinjaBuildToken("||")
    object Indent : NinjaBuildToken(INDENT_TOKEN)
    object EOL : NinjaBuildToken(END_OF_LINE_TOKEN)
    object EOF : NinjaBuildToken(END_OF_FILE_TOKEN)
}

private const val END_OF_LINE_TOKEN = "::END_OF_LINE_TOKEN::"
private const val END_OF_FILE_TOKEN = "::END_OF_FILE_TOKEN::"
private const val INDENT_TOKEN = "::INDENT_TOKEN::"

/**
 * Private enum to track the current state of the tokenizer.
 */
private enum class TokenizerState {
    IN_EXPRESSION,
    IN_ASSIGNMENT_EXPRESSION,
    IN_INDENT,
    IN_PIPE,
    AFTER_EOL
}
