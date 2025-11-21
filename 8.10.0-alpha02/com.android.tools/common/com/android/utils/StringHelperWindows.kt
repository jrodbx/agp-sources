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
 * Windows specific StringHelper.
 */
object StringHelperWindows {
    /**
     * Split a single command line into individual commands with Windows rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @JvmStatic
    fun splitCommandLine(commandLine: String): List<String> {
        val commands: MutableList<String> =
            Lists.newArrayList()
        var quoting = false
        var escapingQuotes = false
        var escapingOthers = false
        var commandStart = 0
        val length = commandLine.length
        var i = 0
        while (i < length) {
            val c = commandLine[i]
            if (c == '"' && !escapingQuotes) {
                quoting = !quoting
                ++i
                continue
            }
            if (escapingQuotes) {
                escapingQuotes = false
            } else if (c == '\\') {
                escapingQuotes = true
                ++i
                continue
            }
            if (escapingOthers) {
                escapingOthers = false
                ++i
                continue
            } else if (c == '^') {
                escapingOthers = true
                ++i
                continue
            }
            if (!quoting) {
                // Check for separators & and &&
                if (commandLine[i] == '&') {
                    commands.add(commandLine.substring(commandStart, i))
                    i++
                    if (commandLine.length > i && commandLine[i] == '&') {
                        i++
                    }
                    commandStart = i
                }
            }
            ++i
        }
        if (commandStart < length) {
            commands.add(commandLine.substring(commandStart))
        }
        return commands
    }

    @JvmStatic
    fun tokenizeCommandLineToEscaped(commandLine: String) =
        TokenizedCommandLine(commandLine, false, SdkConstants.PLATFORM_WINDOWS)
            .toTokenList()


    @JvmStatic
    fun tokenizeCommandLineToRaw(commandLine: String) =
        TokenizedCommandLine(commandLine, true, SdkConstants.PLATFORM_WINDOWS)
            .toTokenList()
}