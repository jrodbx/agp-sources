/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.settings.Token.*
import com.android.build.gradle.internal.cxx.settings.State.*

/**
 * Tokenize a CMakeSettings.json string, for example:
 *
 *      "${gradle.sdkDir}/ndk/${ndk.version}"
 *
 * Tokens are:
 *  [LiteralToken] - A literal string value. "/ndk/" in the example above
 *  [MacroToken] - A susbsitution macro. "gradle.sdkdir" and "ndk.version"
 *    above.
 */
fun tokenizeMacroString(value : String, receive: (Token) -> Unit ) {
    var index = 0
    val sb = StringBuilder()
    var state = PARSING_LITERAL
    while(index < value.length) {
        val c = value[index]
        state = when(state) {
            PARSING_LITERAL -> {
                when(c) {
                    '$' -> PARSING_LITERAL_SAW_DOLLAR
                    else -> {
                        sb.append(c)
                        PARSING_LITERAL
                    }
                }
            }
            PARSING_LITERAL_SAW_DOLLAR -> {
                when(c) {
                    '{' -> {
                        if (sb.isNotEmpty()) {
                            receive(LiteralToken(sb.toString()))
                            sb.setLength(0)
                        }
                        PARSING_MACRO
                    }
                    '$' -> {
                        sb.append("$")
                        PARSING_LITERAL_SAW_DOLLAR
                    }
                    else -> {
                        sb.append("$$c")
                        PARSING_LITERAL
                    }
                }
            }
            PARSING_MACRO -> {
                when(c) {
                    '}' -> {
                        receive(MacroToken(sb.toString()))
                        sb.setLength(0)
                        PARSING_LITERAL
                    }
                    else -> {
                        sb.append(c)
                        PARSING_MACRO
                    }
                }
            }
        }
        ++index
    }
    when(state) {
        PARSING_LITERAL_SAW_DOLLAR -> receive(LiteralToken("$sb$"))
        PARSING_MACRO -> receive(LiteralToken("\${$sb"))
        else ->
            if (sb.isNotEmpty()) {
                receive(LiteralToken("$sb"))
            }
    }
}

/**
 * The tokens that may be returned by [tokenizeMacroString].
 */
sealed class Token {
    data class LiteralToken(val literal : String) : Token() {
        override fun toString() = literal
    }
    data class MacroToken(val macro : String) : Token() {
        override fun toString() = macro
    }
}

/**
 * Internal state of the tokenizer.
 */
private enum class State {
    PARSING_LITERAL,
    PARSING_LITERAL_SAW_DOLLAR,
    PARSING_MACRO
}
