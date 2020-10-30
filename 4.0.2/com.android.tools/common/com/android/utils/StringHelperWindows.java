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

package com.android.utils;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Windows specific StringHelper that applies the following tokenization rules:
 *
 * <p>https://msdn.microsoft.com/en-us/library/17w5ykft.aspx
 *
 * <ul>
 *   <li>A string surrounded by double quotation marks ("string") is interpreted as a single
 *       argument, regardless of white space contained within. A quoted string can be embedded in an
 *       argument.
 *   <li>A double quotation mark preceded by a backslash (\") is interpreted as a literal double
 *       quotation mark character (").
 *   <li>Backslashes are interpreted literally, unless they immediately precede a double quotation
 *       mark.
 *   <li>If an even number of backslashes is followed by a double quotation mark, one backslash is
 *       placed in the argv array for every pair of backslashes, and the double quotation mark is
 *       interpreted as a string delimiter.
 *   <li>If an odd number of backslashes is followed by a double quotation mark, one backslash is
 *       placed in the argv array for every pair of backslashes, and the double quotation mark is
 *       "escaped" by the remaining backslash
 * </ul>
 */
public class StringHelperWindows {

    /**
     * Split a single command line into individual commands with Windows rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @NonNull
    public static List<String> splitCommandLine(@NonNull String commandLine) {
        List<String> commands = Lists.newArrayList();
        boolean quoting = false;
        boolean escapingQuotes = false;
        boolean escapingOthers = false;

        int commandStart = 0;

        int length = commandLine.length();
        for (int i = 0; i < length; ++i) {
            final char c = commandLine.charAt(i);

            if (c == '"' && !escapingQuotes) {
                quoting = !quoting;
                continue;
            }

            if (escapingQuotes) {
                escapingQuotes = false;
            } else if (c == '\\') {
                escapingQuotes = true;
                continue;
            }

            if (escapingOthers) {
                escapingOthers = false;
                continue;
            } else if (c == '^') {
                escapingOthers = true;
                continue;
            }

            if (!quoting) {
                // Check for separators & and &&
                if (commandLine.charAt(i) == '&') {
                    commands.add(commandLine.substring(commandStart, i));
                    i++;
                    if (commandLine.length() > i && commandLine.charAt(i) == '&') {
                        i++;
                    }
                    commandStart = i;
                }
            }
        }

        if (commandStart < length) {
            commands.add(commandLine.substring(commandStart));
        }

        return commands;
    }

    public static List<String> tokenizeCommandLineToEscaped(@NonNull String commandLine) {
        return tokenizeCommandLine(commandLine, true);
    }

    public static List<String> tokenizeCommandLineToRaw(@NonNull String commandLine) {
        return tokenizeCommandLine(commandLine, false);
    }

    /**
     * Tokenize a string with Windows rules.
     *
     * @param commandLine the string to be tokenized
     * @param returnEscaped if true then return escaped, otherwise return original
     * @return the list of tokens
     */
    @NonNull
    private static List<String> tokenizeCommandLine(
            @NonNull String commandLine, boolean returnEscaped) {
        List<String> tokens = Lists.newArrayList();
        StringBuilder token = new StringBuilder();
        boolean quoting = false;
        boolean escapingQuotes = false;
        boolean escapingOthers = false;
        boolean skipping = true;
        for (int i = 0; i < commandLine.length(); ++i) {
            char c = commandLine.charAt(i);
            if (skipping) {
                if (Character.isWhitespace(c)) {
                    continue;
                } else {
                    skipping = false;
                }
            }

            if (quoting || !Character.isWhitespace(c)) {
                if (!returnEscaped) {
                    token.append(c);
                }
            }

            if (c == '"') {
                // delete one slash for every pair of preceding slashes
                if (returnEscaped) {
                    for (int j = token.length() - 2;
                            j >= 0 && token.charAt(j) == '\\' && token.charAt(j + 1) == '\\';
                            j -= 2) {
                        token.deleteCharAt(j);
                    }
                }
                if (escapingQuotes) {
                    if (returnEscaped) {
                        token.deleteCharAt(token.length() - 1);
                    }
                } else {
                    quoting = !quoting;
                    continue;
                }
            }

            if (escapingQuotes) {
                escapingQuotes = false;
            } else if (c == '\\') {
                escapingQuotes = true;
            }

            if (escapingOthers) {
                escapingOthers = false;
                if (c == '\n') {
                    continue;
                }
            } else if (!quoting && c == '^') {
                escapingOthers = true;
                continue;
            }

            if (!quoting && Character.isWhitespace(c)) {
                skipping = true;
                if (token.length() > 0) {
                    tokens.add(token.toString());
                }
                token.setLength(0);
                continue;
            }

            if (returnEscaped) {
                token.append(c);
            }
        }

        if (token.length() > 0) {
            tokens.add(token.toString());
        }

        return tokens;
    }
}
