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
import com.android.utils.XmlUtils;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

final class StringResourceEscapeUtils {

    static final String STRING_ELEMENT_NAME = "string";

    private static final Pattern DECIMAL_REFERENCE = Pattern.compile("&#(\\p{Digit}+);");
    private static final String DECIMAL_ESCAPE = "___D";

    private static final Pattern HEXADECIMAL_REFERENCE = Pattern.compile("&#x(\\p{XDigit}+);");
    private static final String HEXADECIMAL_ESCAPE = "___X";

    private static final Pattern ESCAPED_DECIMAL_REFERENCE =
            Pattern.compile(DECIMAL_ESCAPE + "(\\p{Digit}+);");

    private static final Pattern ESCAPED_HEXADECIMAL_REFERENCE =
            Pattern.compile(HEXADECIMAL_ESCAPE + "(\\p{XDigit}+);");

    @NonNull
    static SAXParserFactory createSaxParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        XmlUtils.configureSaxFactory(factory, false, false);

        return factory;
    }

    static void parse(@NonNull String string, @NonNull SAXParserFactory factory, @NonNull ContentHandler handler) throws SAXException {
        XMLReader reader;

        try {
            SAXParser parser = XmlUtils.createSaxParser(factory);
            reader = parser.getXMLReader();
            reader.setContentHandler(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new RuntimeException(exception);
        }

        string = '<' + STRING_ELEMENT_NAME + '>' + string + "</" + STRING_ELEMENT_NAME + '>';

        try {
            reader.parse(new InputSource(new StringReader(string)));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @NonNull
    static String escapeCharacterReferences(@NonNull String xml) {
        xml = DECIMAL_REFERENCE.matcher(xml).replaceAll(DECIMAL_ESCAPE + "$1;");
        xml = HEXADECIMAL_REFERENCE.matcher(xml).replaceAll(HEXADECIMAL_ESCAPE + "$1;");

        return xml;
    }

    @NonNull
    static String unescapeCharacterReferences(@NonNull String xml) {
        xml = ESCAPED_DECIMAL_REFERENCE.matcher(xml).replaceAll("&#$1;");
        xml = ESCAPED_HEXADECIMAL_REFERENCE.matcher(xml).replaceAll("&#x$1;");

        return xml;
    }
}
