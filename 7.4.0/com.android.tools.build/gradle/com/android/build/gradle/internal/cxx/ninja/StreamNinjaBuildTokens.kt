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

import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.DoublePipeType
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.EOFType
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.EOLType
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.IndentType
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.PipeType
import com.android.build.gradle.internal.cxx.ninja.NinjaBuildTokenType.TextType
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.AFTER_EOL
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_PIPE
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_INDENT
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_EXPRESSION
import com.android.build.gradle.internal.cxx.ninja.TokenizerState.IN_ASSIGNMENT_EXPRESSION
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.LiteralType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.CommentType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedSpaceType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedDollarType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedColonType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableWithCurliesType
import com.google.common.annotations.VisibleForTesting
import java.io.Reader

/**
 * This function builds on and is one level higher than [streamUnescapedNinja]. Its purpose is to
 * create a stream of Ninja build tokens (called [NinjaBuildToken]) that are literals or special
 * characters recognized by ninja like '|', '||', and ':'.
 */
@VisibleForTesting
fun Reader.streamNinjaBuildTokens(action: (NinjaBuildTokenType, CharSequence) -> Unit) {
    val sb = StringBuilder()
    var state = AFTER_EOL
    fun sendExisting() {
        val trimmed = sb.trim()
        if (trimmed.isNotEmpty()) {
            action(TextType, trimmed)
        }
        sb.clear()
    }
    streamUnescapedNinja { type, value ->
        state = when(type) {
            CommentType -> when(state) {
                IN_EXPRESSION -> {
                    sendExisting()
                    IN_EXPRESSION
                }
                IN_INDENT -> {
                    action(IndentType, INDENT_TOKEN)
                    IN_EXPRESSION
                }
                IN_PIPE -> {
                    action(PipeType, "|")
                    IN_EXPRESSION
                }
                AFTER_EOL -> AFTER_EOL
                IN_ASSIGNMENT_EXPRESSION -> {
                    sb.append("#")
                    sb.append(value)
                    IN_ASSIGNMENT_EXPRESSION
                }
                else -> error("$state")
            }
            EscapedDollarType,
            EscapedSpaceType,
            EscapedColonType,
            VariableType,
            VariableWithCurliesType,
            LiteralType -> {
                val size = type.size(value)
                for(i in 0 until size) {
                    state = when(val ch = type.charAt(value, i)) {
                        ' ' -> when(state) {
                            IN_PIPE -> {
                                action(PipeType, "|")
                                IN_EXPRESSION
                            }
                            IN_INDENT -> IN_INDENT
                            AFTER_EOL -> IN_INDENT
                            IN_EXPRESSION -> {
                                if (type == EscapedSpaceType) {
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
                                action(DoublePipeType, "||")
                                IN_EXPRESSION
                            }
                            IN_INDENT -> {
                                action(IndentType, INDENT_TOKEN)
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
                                action(PipeType, "|")
                                action(EOLType, END_OF_LINE_TOKEN)
                                AFTER_EOL
                            }
                            AFTER_EOL -> AFTER_EOL
                            IN_INDENT -> {
                                sendExisting()
                                action(EOLType, END_OF_LINE_TOKEN)
                                IN_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION,
                            IN_EXPRESSION -> {
                                sendExisting()
                                action(EOLType, END_OF_LINE_TOKEN)
                                AFTER_EOL
                            }
                            else -> error("$state")
                        }
                        '=' -> when(state) {
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                sendExisting()
                                action(TextType, ch.toString())
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            IN_ASSIGNMENT_EXPRESSION -> {
                                sb.append("=")
                                IN_ASSIGNMENT_EXPRESSION
                            }
                            IN_INDENT -> {
                                action(IndentType, INDENT_TOKEN)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(PipeType, "|")
                                IN_EXPRESSION
                            }
                            else -> error("$state")
                        }
                        ':' -> when(state) {
                            AFTER_EOL,
                            IN_EXPRESSION -> {
                                if (type == EscapedColonType) {
                                    sb.append(":")
                                    IN_EXPRESSION
                                } else {
                                    sendExisting()
                                    action(TextType, ch.toString())
                                    IN_EXPRESSION
                                }
                            }
                            IN_INDENT -> {
                                action(IndentType, INDENT_TOKEN)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(PipeType, "|")
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
                                action(IndentType, INDENT_TOKEN)
                                sb.append(ch)
                                IN_EXPRESSION
                            }
                            IN_PIPE -> {
                                action(PipeType, "|")
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
        action(EOLType, END_OF_LINE_TOKEN)
    }
    action(EOFType, END_OF_FILE_TOKEN)
}

/**
 * A Ninja build token like 'build', 'rule', or special characters that have syntactic meaning.
 */
enum class NinjaBuildTokenType {
    TextType,
    PipeType,
    DoublePipeType,
    IndentType,
    EOLType,
    EOFType;
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
