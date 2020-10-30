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
 * POSIX specific StringHelper that applies the following tokenization rules:
 *
 * <p>http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html
 *
 * <ul>
 *   <li>A backslash that is not quoted shall preserve the literal value of the following character
 *   <li>Enclosing characters in single-quotes ( '' ) shall preserve the literal value of each
 *       character within the single-quotes.
 *   <li>Enclosing characters in double-quotes ( "" ) shall preserve the literal value of all
 *       characters within the double-quotes, with the exception of the characters dollar sign,
 *       backquote, and backslash
 * </ul>
 */
public class StringHelperPOSIX {

    /**
     * Split a single command line into individual commands with POSIX rules.
     *
     * @param commandLine the command line to be split
     * @return the list of individual commands
     */
    @NonNull
    public static List<String> splitCommandLine(@NonNull String commandLine) {
        List<String> commands = Lists.newArrayList();
        boolean quoting = false;
        char quote = '\0';
        boolean escaping = false;

        int commandStart = 0;

        for (int i = 0; i < commandLine.length(); ++i) {
            final char c = commandLine.charAt(i);

            if (escaping) {
                escaping = false;
                continue;
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true;
                continue;
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true;
                quote = c;
                continue;
            } else if (quoting && c == quote) {
                quoting = false;
                quote = '\0';
                continue;
            }

            if (!quoting) {
                // Match either && or ; separator
                int matched = 0;
                if (commandLine.length() > i + 1
                        && commandLine.charAt(i) == '&'
                        && commandLine.charAt(i + 1) == '&') {
                    matched = 2;
                } else if (commandLine.charAt(i) == ';') {
                    matched = 1;
                }
                if (matched > 0) {
                    commands.add(commandLine.substring(commandStart, i));
                    i += matched;
                    commandStart = i;
                }
            }
        }

        if (commandStart < commandLine.length()) {
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
     * Tokenize a string with POSIX rules. This function should operate in the same manner as the
     * bash command-line.
     *
     * <p>For escaped tokens, this can be validated with a script like this:
     *
     * <p>echo 1=[$1]
     *
     * <p>echo 2=[$2]
     *
     * <p>echo 3=[$3]
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
        char quote = '\0';
        boolean escaping = false;
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

            if (escaping) {
                escaping = false;
                if (c != '\n') {
                    if (returnEscaped) {
                        token.append(c);
                    }
                }
                continue;
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true;
                continue;
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true;
                quote = c;
                continue;
            } else if (quoting && c == quote) {
                quoting = false;
                quote = '\0';
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
