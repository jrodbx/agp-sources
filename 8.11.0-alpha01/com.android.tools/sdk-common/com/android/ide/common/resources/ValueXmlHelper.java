/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources;

import static com.android.SdkConstants.AMP_ENTITY;
import static com.android.SdkConstants.APOS_ENTITY;
import static com.android.SdkConstants.GT_ENTITY;
import static com.android.SdkConstants.LT_ENTITY;
import static com.android.SdkConstants.QUOT_ENTITY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.escape.string.StringResourceEscaper;
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.Contract;

/**
 * Helper class to help with XML values resource file.
 */
public class ValueXmlHelper {

    /**
     * Replaces escapes in an XML resource string with the actual characters, performing unicode
     * substitutions (replacing any {@code \\uNNNN} references in the given string with the
     * corresponding unicode characters), etc.
     *
     * @param s              the string to unescape
     * @param escapeEntities XML entities
     * @param trim           whether surrounding space and quotes should be trimmed
     * @return the string with the escape characters removed and expanded
     */
    @SuppressWarnings("UnnecessaryContinue")
    @Contract("!null, _, _ -> !null")
    @Nullable
    public static String unescapeResourceString(
            @Nullable String s,
            boolean escapeEntities, boolean trim) {
        if (s == null) {
            return null;
        }

        // Trim space surrounding optional quotes
        int i = 0;
        int n = s.length();
        if (trim) {
            while (i < n) {
                char c = s.charAt(i);
                if (!Character.isWhitespace(c)) {
                    break;
                }
                i++;
            }
            while (n > i) {
                char c = s.charAt(n - 1);
                if (!Character.isWhitespace(c)) {
                    //See if this was a \, and if so, see whether it was escaped
                    if (n < s.length() && isEscaped(s, n)) {
                        n++;
                    }
                    break;
                }
                n--;
            }
        }

        // Perform a single pass over the string and see if it contains
        // (1) spaces that should be converted (e.g. repeated spaces or a newline which
        // should be converted to a space)
        // (2) escape characters (\ and &) which will require expansions
        // (3) quotes that need to be removed
        // If we find neither of these, we can simply return the string
        boolean rewriteWhitespace = false;
        // See if we need to fold adjacent spaces
        boolean prevSpace = false;
        boolean hasEscape = false;
        boolean hasQuotes = false;
        for (int curr = i; curr < n; curr++) {
            char c = s.charAt(curr);
            if (c == '\\' || c == '&') {
                hasEscape = true;
            }
            if (c == '"') {
                hasQuotes = true;
            }
            boolean isSpace = Character.isWhitespace(c);
            if (c == '\n' || (isSpace && prevSpace)) {
                // rewrite newlines as spaces
                // fold adjacent spaces
                rewriteWhitespace = true;
            }
            prevSpace = isSpace;
        }

        if (!trim) {
            rewriteWhitespace = false;
            hasQuotes = false;
        }

        // If no surrounding whitespace and no escape characters, no need to do any
        // more work
        if (!rewriteWhitespace && !hasEscape && !hasQuotes && i == 0 && n == s.length()) {
            return s;
        }

        boolean quoted = false;
        StringBuilder sb = new StringBuilder(n - i);
        prevSpace = false;
        for (; i < n; i++) {
            char c = s.charAt(i);
            while (c == '"' && trim) {
                quoted = !quoted;
                i++;
                if (i == n) {
                    break;
                }
                c = s.charAt(i);
            }
            if (i == n) {
                break;
            }

            if (c == '\\' && i < n - 1) {
                prevSpace = false;
                char next = s.charAt(i + 1);
                // Unicode escapes
                if (next == 'u' && i < n - 5) { // case sensitive
                    String hex = s.substring(i + 2, i + 6);
                    try {
                        int unicodeValue = Integer.parseInt(hex, 16);
                        sb.append((char) unicodeValue);
                        i += 5;
                        continue;
                    } catch (NumberFormatException e) {
                        // Invalid escape: Just proceed to literally transcribe it
                        sb.append(c);
                    }
                } else if (next == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                } else if (next == 't') {
                    sb.append('\t');
                    i++;
                    continue;
                } else {
                    sb.append(next);
                    i++;
                    continue;
                }
            } else {
                if (c == '&' && escapeEntities) {
                    prevSpace = false;
                    if (s.regionMatches(true, i, LT_ENTITY, 0, LT_ENTITY.length())) {
                        sb.append('<');
                        i += LT_ENTITY.length() - 1;
                        continue;
                    } else if (s.regionMatches(true, i, AMP_ENTITY, 0, AMP_ENTITY.length())) {
                        sb.append('&');
                        i += AMP_ENTITY.length() - 1;
                        continue;
                    } else if (s.regionMatches(true, i, QUOT_ENTITY, 0, QUOT_ENTITY.length())) {
                        sb.append('"');
                        i += QUOT_ENTITY.length() - 1;
                        continue;
                    } else if (s.regionMatches(true, i, APOS_ENTITY, 0, APOS_ENTITY.length())) {
                        sb.append('\'');
                        i += APOS_ENTITY.length() - 1;
                        continue;
                    } else if (s.regionMatches(true, i, GT_ENTITY, 0, GT_ENTITY.length())) {
                        sb.append('>');
                        i += GT_ENTITY.length() - 1;
                        continue;
                    } else if (i < n - 2 && s.charAt(i + 1) == '#') {
                        int end = s.indexOf(';', i + 1);
                        if (end != -1) {
                            char first = s.charAt(i + 2);
                            boolean hex = first == 'x' || first == 'X';
                            String number = s.substring(i + (hex ? 3 : 2), end);
                            try {
                                int unicodeValue = Integer.parseInt(number, hex ? 16 : 10);
                                if (unicodeValue <= Character.MAX_VALUE) {
                                    sb.append((char)unicodeValue);
                                } else {
                                    sb.append(Character.highSurrogate(unicodeValue));
                                    sb.append(Character.lowSurrogate(unicodeValue));
                                }
                                i = end;
                                continue;
                            } catch (NumberFormatException e) {
                                // Invalid escape: Just proceed to literally transcribe it
                                sb.append(c);
                            }
                        } else {
                            // Invalid escape: Just proceed to literally transcribe it
                            sb.append(c);
                        }
                    }
                }

                if (trim && !quoted) {
                    boolean isSpace = Character.isWhitespace(c);
                    if (isSpace) {
                        if (!prevSpace) {
                            sb.append(' '); // replace newlines etc with a plain space
                        }
                    } else {
                        sb.append(c);
                    }
                    prevSpace = isSpace;
                } else {
                    sb.append(c);
                }
            }
        }
        s = sb.toString();

        return s;
    }

    /**
     * Returns true if the character at the given offset in the string is escaped (the previous
     * character is a \, and that character isn't itself an escaped \)
     *
     * @param s     the string
     * @param index the index of the character in the string to check
     * @return true if the character is escaped
     */
    @VisibleForTesting
    static boolean isEscaped(String s, int index) {
        if (index == 0 || index == s.length()) {
            return false;
        }
        // Count how many backslashes come before the character.
        int consecutivePrecedingBackslashes = 0;
        for (int j = index - 1; j >= 0 && s.charAt(j) == '\\'; --j) {
            ++consecutivePrecedingBackslashes;
        }
        // If we passed an odd number of \'s, the character is escaped
        return consecutivePrecedingBackslashes % 2 == 1;
    }
}
