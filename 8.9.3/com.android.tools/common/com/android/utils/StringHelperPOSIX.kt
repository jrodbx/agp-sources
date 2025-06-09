/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.google.common.collect.Lists

/**
 * POSIX specific StringHelper that applies the following tokenization rules:
 */
object StringHelperPOSIX {
    /**
     * Split a single command line into individual commands with POSIX rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @JvmStatic
    fun splitCommandLine(commandLine: String): List<String> {
        val commands: MutableList<String> =
            Lists.newArrayList()
        var quoting = false
        var quote = '\u0000'
        var escaping = false
        var commandStart = 0
        var i = 0
        while (i < commandLine.length) {
            val c = commandLine[i]
            if (escaping) {
                escaping = false
                ++i
                continue
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true
                ++i
                continue
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true
                quote = c
                ++i
                continue
            } else if (quoting && c == quote) {
                quoting = false
                quote = '\u0000'
                ++i
                continue
            }
            if (!quoting) {
                // Match either && or ; separator
                var matched = 0
                if (commandLine.length > i + 1 && commandLine[i] == '&' && commandLine[i + 1] == '&'
                ) {
                    matched = 2
                } else if (commandLine[i] == ';') {
                    matched = 1
                }
                if (matched > 0) {
                    commands.add(commandLine.substring(commandStart, i))
                    i += matched
                    commandStart = i
                }
            }
            ++i
        }
        if (commandStart < commandLine.length) {
            commands.add(commandLine.substring(commandStart))
        }
        return commands
    }

    @JvmStatic
    fun tokenizeCommandLineToEscaped(commandLine: String) =
        TokenizedCommandLine(commandLine, false, SdkConstants.PLATFORM_LINUX)
            .toTokenList()

    @JvmStatic
    fun tokenizeCommandLineToRaw(commandLine: String) =
        TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_LINUX)
            .toTokenList()
}