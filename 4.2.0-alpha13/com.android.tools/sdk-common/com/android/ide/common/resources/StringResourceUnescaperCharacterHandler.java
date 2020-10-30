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

import com.android.annotations.NonNull;

final class StringResourceUnescaperCharacterHandler implements CharacterHandler {

    @Override
    public void handle(
            @NonNull StringBuilder builder, @NonNull char[] chars, int offset, int length) {
        String string;

        string = stripUnescapedQuotes(chars, offset, length);
        string = unescape(string);

        builder.append(string);
    }

    @NonNull
    private static String stripUnescapedQuotes(@NonNull char[] chars, int offset, int length) {
        StringBuilder builder = new StringBuilder(length);

        for (int i = offset; i < offset + length; i++) {
            if (chars[i] == '"') {
                if (isEscaped(chars, offset, length, i)) {
                    builder.append('"');
                }
            } else {
                builder.append(chars[i]);
            }
        }

        return builder.toString();
    }

    private static boolean isEscaped(@NonNull char[] chars, int offset, int length, int index) {
        // TODO Optimize this, maybe?
        return ValueXmlHelper.isEscaped(new String(chars, offset, length), index - offset);
    }

    @NonNull
    private static String unescape(@NonNull String string) {
        int length = string.length();
        StringBuilder builder = new StringBuilder(length);

        for (int i = length - 1; i >= 0; i--) {
            if (shouldUnescape(string, i)) {
                builder.append(getReplacement(string.charAt(i)));
                i--;
            } else {
                builder.append(string.charAt(i));
            }
        }

        return builder.reverse().toString();
    }

    private static boolean shouldUnescape(@NonNull String string, int index) {
        switch (string.charAt(index)) {
            case ' ':
            case '"':
            case '\'':
            case '\\':
            case 'n':
            case 't':
                return ValueXmlHelper.isEscaped(string, index);
            default:
                return false;
        }
    }

    private static char getReplacement(char c) {
        switch (c) {
            case ' ':
            case '"':
            case '\'':
            case '\\':
                return c;
            case 'n':
                return '\n';
            case 't':
                return '\t';
            default:
                throw new IllegalArgumentException(String.valueOf(c));
        }
    }
}
