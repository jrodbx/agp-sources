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
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

final class StringResourceEscaper {
    @NonNull
    private final SAXParserFactory mFactory = StringResourceEscapeUtils.createSaxParserFactory();

    @NonNull
    String escapeCharacterData(@NonNull String xml) {
        if (xml.isEmpty()) {
            return "";
        }

        xml = StringResourceEscapeUtils.escapeCharacterReferences(xml);
        StringBuilder builder = new StringBuilder(xml.length() * 3 / 2);

        if (startsOrEndsWithSpace(xml)) {
            builder.append('"');
        } else if (startsWithQuestionMarkOrAtSign(xml)) {
            builder.append('\\');
        }

        try {
            Escaper escaper = buildEscaper(!startsOrEndsWithSpace(xml), false);
            StringResourceEscapeUtils.parse(xml, mFactory, newContentHandler(builder, escaper));
        } catch (SAXException exception) {
            throw new IllegalArgumentException(xml, exception);
        }

        if (startsOrEndsWithSpace(xml)) {
            builder.append('"');
        }

        xml = builder.toString();
        xml = StringResourceEscapeUtils.unescapeCharacterReferences(xml);

        return xml;
    }

    @NonNull
    private static ContentHandler newContentHandler(
            @NonNull StringBuilder builder, @NonNull Escaper escaper) {
        CharacterHandler handler = new StringResourceEscaperCharacterHandler(escaper);
        return new StringResourceContentHandler(builder, handler);
    }

    @NonNull
    static String escape(@NonNull String string, boolean escapeMarkupDelimiters) {
        if (string.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(string.length() * 3 / 2);

        if (startsOrEndsWithSpace(string)) {
            builder.append('"');
        } else if (startsWithQuestionMarkOrAtSign(string)) {
            builder.append('\\');
        }

        Escaper escaper = buildEscaper(!startsOrEndsWithSpace(string), escapeMarkupDelimiters);
        builder.append(escaper.escape(string));

        if (startsOrEndsWithSpace(string)) {
            builder.append('"');
        }

        return builder.toString();
    }

    @NonNull
    private static Escaper buildEscaper(boolean escapeApostrophes, boolean escapeMarkupDelimiters) {
        @SuppressWarnings("UnstableApiUsage")
        Escapers.Builder builder =
                Escapers.builder()
                        .addEscape('"', "\\\"")
                        .addEscape('\\', "\\\\")
                        .addEscape('\n', "\\n")
                        .addEscape('\t', "\\t");

        if (escapeApostrophes) {
            builder.addEscape('\'', "\\'");
        }

        if (escapeMarkupDelimiters) {
            builder
                    .addEscape('&', "&amp;")
                    .addEscape('<', "&lt;");
        }

        return builder.build();
    }

    private static boolean startsWithQuestionMarkOrAtSign(@NonNull String string) {
        assert !string.isEmpty();
        return string.charAt(0) == '?' || string.charAt(0) == '@';
    }

    private static boolean startsOrEndsWithSpace(@NonNull String string) {
        assert !string.isEmpty();
        return string.charAt(0) == ' ' || string.charAt(string.length() - 1) == ' ';
    }
}
