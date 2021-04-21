/*
 * Copyright 2011 the original author or authors.
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

package com.android.build.gradle.internal.test.report;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedList;

/**
 * <p>A streaming markup writer. Encodes characters and CDATA. Provides only basic state validation, and some simple indentation.</p>
 *
 * <p>This class also is-a {@link Writer}, and any characters written to this writer will be encoded as appropriate. Note, however, that
 * calling {@link #close()} on this object does not close the backing stream.
 * </p>
 */
public class SimpleMarkupWriter extends Writer {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private enum Context {
        Outside, Text, CData, StartTag, ElementContent
    }

    private final Writer output;
    private final LinkedList<String> elements = new LinkedList<>();
    private Context context = Context.Outside;
    private int squareBrackets;
    private final String indent;

    protected SimpleMarkupWriter(Writer writer, String indent) throws IOException {
        this.indent = indent;
        this.output = writer;
    }

    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
        characters(chars, offset, length);
    }

    @Override
    public void flush() throws IOException {
        output.flush();
    }

    @Override
    public void close() throws IOException {
        // Does nothing
    }

    public SimpleMarkupWriter characters(char[] characters) throws IOException {
        characters(characters, 0, characters.length);
        return this;
    }

    public SimpleMarkupWriter characters(char[] characters, int start, int count) throws IOException {
        if (context == Context.CData) {
            writeCDATA(characters, start, count);
        } else {
            maybeStartText();
            writeXmlEncoded(characters, start, count);
        }
        return this;
    }

    public SimpleMarkupWriter characters(CharSequence characters) throws IOException {
        if (context == Context.CData) {
            writeCDATA(characters);
        } else {
            maybeStartText();
            writeXmlEncoded(characters);
        }
        return this;
    }

    private void maybeStartText() throws IOException {
        if (context == Context.Outside) {
            throw new IllegalStateException("Cannot write text, as there are no started elements.");
        }
        if (context == Context.StartTag) {
            writeRaw(">");
        }
        context = Context.Text;
    }

    private void maybeFinishStartTag() throws IOException {
        if (context == Context.StartTag) {
            writeRaw(">");
            context = Context.ElementContent;
        }
    }

    public SimpleMarkupWriter startElement(String name) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid element name: '%s'", name));
        }
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot start element, as current CDATA node has not been closed.");
        }
        maybeFinishStartTag();
        if (indent != null) {
            writeRaw(LINE_SEPARATOR);
            for (int i = 0; i < elements.size(); i++) {
                writeRaw(indent);
            }
        }
        context = Context.StartTag;
        elements.add(name);
        writeRaw("<");
        writeRaw(name);
        return this;
    }

    public SimpleMarkupWriter endElement() throws IOException {
        if (context == Context.Outside) {
            throw new IllegalStateException("Cannot end element, as there are no started elements.");
        }
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot end element, as current CDATA node has not been closed.");
        }
        if (context == Context.StartTag) {
            writeRaw("/>");
            elements.removeLast();
        } else {
            if (context != Context.Text && indent != null) {
                writeRaw(LINE_SEPARATOR);
                for (int i = 1; i < elements.size(); i++) {
                    writeRaw(indent);
                }
            }
            writeRaw("</");
            writeRaw(elements.removeLast());
            writeRaw(">");
        }
        if (elements.isEmpty()) {
            if (indent != null) {
                writeRaw(LINE_SEPARATOR);
            }
            output.flush();
            context = Context.Outside;
        } else {
            context = Context.ElementContent;
        }
        return this;
    }

    private void writeCDATA(char[] cdata, int offset, int count) throws IOException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            writeCDATA(cdata[i]);
        }
    }

    private void writeCDATA(CharSequence cdata) throws IOException {
        int len = cdata.length();
        for (int i = 0; i < len; i++) {
            writeCDATA(cdata.charAt(i));
        }
    }

    private void writeCDATA(char ch) throws IOException {
        if (needsCDATAEscaping(ch)) {
            writeRaw("]]><![CDATA[>");
        } else if (!isLegalCharacter(ch)) {
            writeRaw('?');
        } else if (isRestrictedCharacter(ch)) {
            writeRaw("]]>");
            writeCharacterReference(ch);
            writeRaw("<![CDATA[");
        } else {
            writeRaw(ch);
        }
    }

    private void writeCharacterReference(char ch) throws IOException {
        writeRaw("&#x");
        writeRaw(Integer.toHexString(ch));
        writeRaw(";");
    }

    private boolean needsCDATAEscaping(char ch) {
        switch (ch) {
            case ']':
                squareBrackets++;
                return false;
            case '>':
                if (squareBrackets >= 2) {
                    squareBrackets = 0;
                    return true;
                }
                return false;
            default:
                squareBrackets = 0;
                return false;
        }
    }

    public SimpleMarkupWriter startCDATA() throws IOException {
        if (context == Context.CData) {
            throw new IllegalStateException("Cannot start CDATA node, as current CDATA node has not been closed.");
        }
        maybeFinishStartTag();
        writeRaw("<![CDATA[");
        context = Context.CData;
        squareBrackets = 0;
        return this;
    }

    public SimpleMarkupWriter endCDATA() throws IOException {
        if (context != Context.CData) {
            throw new IllegalStateException("Cannot end CDATA node, as not currently in a CDATA node.");
        }
        writeRaw("]]>");
        context = Context.Text;
        return this;
    }

    public SimpleMarkupWriter attribute(String name, String value) throws IOException {
        if (!isValidXmlName(name)) {
            throw new IllegalArgumentException(String.format("Invalid attribute name: '%s'", name));
        }
        if (context != Context.StartTag) {
            throw new IllegalStateException("Cannot write attribute [" + name + ":" + value + "]. You should write start element first.");
        }

        writeRaw(" ");
        writeRaw(name);
        writeRaw("=\"");
        writeXmlAttributeEncoded(value);
        writeRaw("\"");
        return this;
    }

    private static boolean isValidXmlName(String name) {
        int length = name.length();
        if (length == 0) {
            return false;
        }
        char ch = name.charAt(0);
        if (!isValidNameStartChar(ch)) {
            return false;
        }
        for (int i = 1; i < length; i++) {
            ch = name.charAt(i);
            if (!isValidNameChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidNameChar(char ch) {
        if (isValidNameStartChar(ch)) {
            return true;
        }
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        if (ch == '-' || ch == '.' || ch == '\u00b7') {
            return true;
        }
        if (ch >= '\u0300' && ch <= '\u036f') {
            return true;
        }
        if (ch >= '\u203f' && ch <= '\u2040') {
            return true;
        }
        return false;
    }

    private static boolean isValidNameStartChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return true;
        }
        if (ch >= 'a' && ch <= 'z') {
            return true;
        }
        if (ch == ':' || ch == '_') {
            return true;
        }
        if (ch >= '\u00c0' && ch <= '\u00d6') {
            return true;
        }
        if (ch >= '\u00d8' && ch <= '\u00f6') {
            return true;
        }
        if (ch >= '\u00f8' && ch <= '\u02ff') {
            return true;
        }
        if (ch >= '\u0370' && ch <= '\u037d') {
            return true;
        }
        if (ch >= '\u037f' && ch <= '\u1fff') {
            return true;
        }
        if (ch >= '\u200c' && ch <= '\u200d') {
            return true;
        }
        if (ch >= '\u2070' && ch <= '\u218f') {
            return true;
        }
        if (ch >= '\u2c00' && ch <= '\u2fef') {
            return true;
        }
        if (ch >= '\u3001' && ch <= '\ud7ff') {
            return true;
        }
        if (ch >= '\uf900' && ch <= '\ufdcf') {
            return true;
        }
        if (ch >= '\ufdf0' && ch <= '\ufffd') {
            return true;
        }
        return false;
    }

    private void writeRaw(char c) throws IOException {
        output.write(c);
    }

    private boolean isLegalCharacter(final char c) {
        if (c == 0) {
            return false;
        } else if (c <= 0xD7FF) {
            return true;
        } else if (c < 0xE000) {
            return false;
        } else if (c <= 0xFFFD) {
            return true;
        }
        return false;
    }

    private boolean isRestrictedCharacter(char c) {
        if (c == 0x9 || c == 0xA || c == 0xD || c == 0x85) {
            return false;
        } else if (c <= 0x1F) {
            return true;
        } else if (c < 0x7F) {
            return false;
        } else if (c <= 0x9F) {
            return true;
        }
        return false;
    }

    protected void writeRaw(String message) throws IOException {
        output.write(message);
    }

    private void writeXmlEncoded(char[] message, int offset, int count) throws IOException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            writeXmlEncoded(message[i]);
        }
    }

    private void writeXmlAttributeEncoded(CharSequence message) throws IOException {
        assert message != null;
        int len = message.length();
        for (int i = 0; i < len; i++) {
            writeXmlAttributeEncoded(message.charAt(i));
        }
    }

    private void writeXmlAttributeEncoded(char ch) throws IOException {
        if (ch == 9) {
            writeRaw("&#9;");
        } else if (ch == 10) {
            writeRaw("&#10;");
        } else if (ch == 13) {
            writeRaw("&#13;");
        } else {
            writeXmlEncoded(ch);
        }
    }

    private void writeXmlEncoded(CharSequence message) throws IOException {
        assert message != null;
        int len = message.length();
        for (int i = 0; i < len; i++) {
            writeXmlEncoded(message.charAt(i));
        }
    }

    private void writeXmlEncoded(char ch) throws IOException {
        if (ch == '<') {
            writeRaw("&lt;");
        } else if (ch == '>') {
            writeRaw("&gt;");
        } else if (ch == '&') {
            writeRaw("&amp;");
        } else if (ch == '"') {
            writeRaw("&quot;");
        } else if (!isLegalCharacter(ch)) {
            writeRaw('?');
        } else if (isRestrictedCharacter(ch)) {
            writeCharacterReference(ch);
        } else {
            writeRaw(ch);
        }
    }
}
