/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ide.common.resources.usage;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Tokenizers for HTML, CSS, JS files to find resource references inside web content.
 *
 * <p>Detects html attributes, css urls and string constants inside javascript code.
 */
public class WebTokenizers {

    private final WebTokensCallback callback;

    public WebTokenizers(WebTokensCallback callback) {
        this.callback = callback;
    }

    public void tokenizeHtml(@NonNull String html) {
        // Look for
        //    (1) URLs of the form /android_res/drawable/foo.ext
        //        which we will use to keep R.drawable.foo
        // and
        //    (2) Filenames. If the web content is loaded with something like
        //        WebView.loadDataWithBaseURL("file:///android_res/drawable/", ...)
        //        this is similar to Resources#getIdentifier handling where all
        //        *potentially* aliased filenames are kept to play it safe.

        // Simple HTML tokenizer
        int length = html.length();
        final int STATE_TEXT = 1;
        final int STATE_SLASH = 2;
        final int STATE_ATTRIBUTE_NAME = 3;
        final int STATE_BEFORE_TAG = 4;
        final int STATE_IN_TAG = 5;
        final int STATE_BEFORE_ATTRIBUTE = 6;
        final int STATE_ATTRIBUTE_BEFORE_EQUALS = 7;
        final int STATE_ATTRIBUTE_AFTER_EQUALS = 8;
        final int STATE_ATTRIBUTE_VALUE_NONE = 9;
        final int STATE_ATTRIBUTE_VALUE_SINGLE = 10;
        final int STATE_ATTRIBUTE_VALUE_DOUBLE = 11;
        final int STATE_CLOSE_TAG = 12;
        final int STATE_ENDING_TAG = 13;

        int state = STATE_TEXT;
        int offset = 0;
        int valueStart = 0;
        int tagStart = 0;
        String tag = null;
        String attribute = null;
        int attributeStart = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;


            char c = html.charAt(offset);

            // MAke sure I handle doctypes properly.
            // Make sure I handle cdata properly.
            // Oh and what about <style> tags? tokenize everything inside as CSS!
            // ANd <script> tag content as js!
            switch (state) {
                case STATE_TEXT: {
                    if (c == '<') {
                        state = STATE_SLASH;
                        offset++;
                        continue;
                    }

                    // Other text is just ignored
                    offset++;
                    break;
                }

                case STATE_SLASH: {
                    if (c == '!') {
                        if (html.startsWith("!--", offset)) {
                            // Comment
                            int end = html.indexOf("-->", offset + 3);
                            if (end == -1) {
                                offset = length;
                                break;
                            }
                            state = STATE_TEXT;
                            offset = end + 3;
                            continue;
                        } else if (html.startsWith("![CDATA[", offset)) {
                            // Skip CDATA text content; HTML text is irrelevant to this tokenizer
                            // anyway
                            int end = html.indexOf("]]>", offset + 8);
                            if (end == -1) {
                                offset = length;
                                break;
                            }
                            state = STATE_TEXT;
                            offset = end + 3;
                            continue;
                        }
                    } else if (c == '/') {
                        state = STATE_CLOSE_TAG;
                        offset++;
                        continue;
                    } else if (c == '?') {
                        // XML Prologue
                        int end = html.indexOf('>', offset + 2);
                        if (end == -1) {
                            offset = length;
                            state = STATE_TEXT;
                            break;
                        }
                        offset = end + 1;
                        state = STATE_TEXT;
                        continue;
                    }
                    state = STATE_IN_TAG;
                    tagStart = offset;
                    break;
                }

                case STATE_CLOSE_TAG: {
                    if (c == '>') {
                        state = STATE_TEXT;
                    }
                    offset++;
                    break;
                }

                case STATE_BEFORE_TAG: {
                    if (!Character.isWhitespace(c)) {
                        state = STATE_IN_TAG;
                        tagStart = offset;
                    }
                    // (For an end tag we'll include / in the tag name here)
                    offset++;
                    break;
                }
                case STATE_IN_TAG: {
                    if (Character.isWhitespace(c)) {
                        state = STATE_BEFORE_ATTRIBUTE;
                        tag = html.substring(tagStart, offset).trim();
                    } else if (c == '>') {
                        tag = html.substring(tagStart, offset).trim();
                        endHtmlTag(html, offset, tag);
                        state = STATE_TEXT;
                    } else if (c == '/') {
                        tag = html.substring(tagStart, offset).trim();
                        endHtmlTag(html, offset, tag);
                        state = STATE_ENDING_TAG;
                    }
                    offset++;
                    break;
                }

                case STATE_ENDING_TAG: {
                    if (c == '>') {
                        offset++;
                        state = STATE_TEXT;
                    }
                    break;
                }

                case STATE_BEFORE_ATTRIBUTE: {
                    if (c == '>') {
                        endHtmlTag(html, offset, tag);
                        state = STATE_TEXT;
                    } else //noinspection StatementWithEmptyBody
                        if (c == '/') {
                            // we expect an '>' next to close the tag
                        } else if (!Character.isWhitespace(c)) {
                            state = STATE_ATTRIBUTE_NAME;
                            attributeStart = offset;
                        }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_NAME: {
                    if (c == '>') {
                        endHtmlTag(html, offset, tag);
                        state = STATE_TEXT;
                    } else if (c == '=') {
                        attribute = html.substring(attributeStart, offset);
                        state = STATE_ATTRIBUTE_AFTER_EQUALS;
                    } else if (Character.isWhitespace(c)) {
                        attribute = html.substring(attributeStart, offset);
                        state = STATE_ATTRIBUTE_BEFORE_EQUALS;
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_BEFORE_EQUALS: {
                    if (c == '=') {
                        state = STATE_ATTRIBUTE_AFTER_EQUALS;
                    } else if (c == '>') {
                        endHtmlTag(html, offset, tag);
                        state = STATE_TEXT;
                    } else if (!Character.isWhitespace(c)) {
                        // Attribute value not specified (used for some boolean attributes)
                        state = STATE_ATTRIBUTE_NAME;
                        attributeStart = offset;
                    }
                    offset++;
                    break;
                }

                case STATE_ATTRIBUTE_AFTER_EQUALS: {
                    if (c == '\'') {
                        // a='b'
                        state = STATE_ATTRIBUTE_VALUE_SINGLE;
                        valueStart = offset + 1;
                    } else if (c == '"') {
                        // a="b"
                        state = STATE_ATTRIBUTE_VALUE_DOUBLE;
                        valueStart = offset + 1;
                    } else if (!Character.isWhitespace(c)) {
                        // a=b
                        state = STATE_ATTRIBUTE_VALUE_NONE;
                        valueStart = offset + 1;
                    }
                    offset++;
                    break;
                }

                case STATE_ATTRIBUTE_VALUE_SINGLE: {
                    if (c == '\'') {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(
                                tag, attribute, html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_VALUE_DOUBLE: {
                    if (c == '"') {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(
                                tag, attribute, html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                case STATE_ATTRIBUTE_VALUE_NONE: {
                    if (c == '>') {
                        recordHtmlAttributeValue(
                                tag, attribute, html.substring(valueStart, offset));
                        endHtmlTag(html, offset, tag);
                        state = STATE_TEXT;
                    } else if (Character.isWhitespace(c)) {
                        state = STATE_BEFORE_ATTRIBUTE;
                        recordHtmlAttributeValue(
                                tag, attribute, html.substring(valueStart, offset));
                    }
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    private void endHtmlTag(@NonNull String html, int offset, @Nullable String tag) {
        if ("script".equals(tag)) {
            int end = html.indexOf("</script>", offset + 1);
            if (end != -1) {
                // Attempt to tokenize the text as JavaScript
                String js = html.substring(offset + 1, end);
                tokenizeJs(js);
            }
        } else if ("style".equals(tag)) {
            int end = html.indexOf("</style>", offset + 1);
            if (end != -1) {
                // Attempt to tokenize the text as CSS
                String css = html.substring(offset + 1, end);
                tokenizeCss(css);
            }
        }
    }

    private void recordHtmlAttributeValue(
            @Nullable String tagName, @Nullable String attribute, @NonNull String value) {
        callback.referencedHtmlAttribute(tagName, attribute, value);
    }


    public void tokenizeJs(@NonNull String js) {
        // Simple JavaScript tokenizer: only looks for literal strings,
        // and records those as string references
        int length = js.length();
        final int STATE_INIT = 1;
        final int STATE_SLASH = 2;
        final int STATE_STRING_DOUBLE = 3;
        final int STATE_STRING_DOUBLE_QUOTED = 4;
        final int STATE_STRING_SINGLE = 5;
        final int STATE_STRING_SINGLE_QUOTED = 6;

        int state = STATE_INIT;
        int offset = 0;
        int stringStart = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;

            char c = js.charAt(offset);
            switch (state) {
                case STATE_INIT: {
                    if (c == '/') {
                        state = STATE_SLASH;
                    } else if (c == '"') {
                        stringStart = offset + 1;
                        state = STATE_STRING_DOUBLE;
                    } else if (c == '\'') {
                        stringStart = offset + 1;
                        state = STATE_STRING_SINGLE;
                    }
                    offset++;
                    break;
                }
                case STATE_SLASH: {
                    if (c == '*') {
                        // Comment block
                        state = STATE_INIT;
                        int end = js.indexOf("*/", offset + 1);
                        if (end == -1) {
                            offset = length; // unterminated
                            break;
                        }
                        offset = end + 2;
                        continue;
                    } else if (c == '/') {
                        // Line comment
                        state = STATE_INIT;
                        int end = js.indexOf('\n', offset + 1);
                        if (end == -1) {
                            offset = length;
                            break;
                        }
                        offset = end + 1;
                        continue;
                    } else {
                        // division - just continue
                        state = STATE_INIT;
                        offset++;
                        break;
                    }
                }
                case STATE_STRING_DOUBLE: {
                    if (c == '"') {
                        callback.referencedJsString(js.substring(stringStart, offset));
                        state = STATE_INIT;
                    } else if (c == '\\') {
                        state = STATE_STRING_DOUBLE_QUOTED;
                    }
                    offset++;
                    break;
                }
                case STATE_STRING_DOUBLE_QUOTED: {
                    state = STATE_STRING_DOUBLE;
                    offset++;
                    break;
                }
                case STATE_STRING_SINGLE: {
                    if (c == '\'') {
                        callback.referencedJsString(js.substring(stringStart, offset));
                        state = STATE_INIT;
                    } else if (c == '\\') {
                        state = STATE_STRING_SINGLE_QUOTED;
                    }
                    offset++;
                    break;
                }
                case STATE_STRING_SINGLE_QUOTED: {
                    state = STATE_STRING_SINGLE;
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    public void tokenizeCss(@NonNull String css) {
        // Simple CSS tokenizer: Only looks for URL references, and records those
        // filenames. Skips everything else (unrelated to images).
        int length = css.length();
        final int STATE_INIT = 1;
        final int STATE_SLASH = 2;
        int state = STATE_INIT;
        int offset = 0;
        int prev = -1;
        while (offset < length) {
            if (offset == prev) {
                // Purely here to prevent potential bugs in the state machine from looping
                // infinitely
                offset++;
                if (offset == length) {
                    break;
                }
            }
            prev = offset;

            char c = css.charAt(offset);
            switch (state) {
                case STATE_INIT: {
                    if (c == '/') {
                        state = STATE_SLASH;
                    } else if (c == 'u' && css.startsWith("url(", offset) && offset > 0) {
                        char prevChar = css.charAt(offset-1);
                        if (Character.isWhitespace(prevChar) || prevChar == ':') {
                            int end = css.indexOf(')', offset);
                            offset += 4; // skip url(
                            while (offset < length && Character.isWhitespace(css.charAt(offset))) {
                                offset++;
                            }
                            if (end != -1 && end > offset + 1) {
                                while (end > offset
                                        && Character.isWhitespace(css.charAt(end - 1))) {
                                    end--;
                                }
                                if ((css.charAt(offset) == '"'
                                        && css.charAt(end - 1) == '"')
                                        || (css.charAt(offset) == '\''
                                        && css.charAt(end - 1) == '\'')) {
                                    // Strip " or '
                                    offset++;
                                    end--;
                                }
                                callback.referencedCssUrl(css.substring(offset, end).trim());
                            }
                            offset = end + 1;
                            continue;
                        }

                    }
                    offset++;
                    break;
                }
                case STATE_SLASH: {
                    if (c == '*') {
                        // CSS comment? Skip the whole block rather than staying within the
                        // character tokenizer.
                        int end = css.indexOf("*/", offset + 1);
                        if (end == -1) {
                            offset = length;
                            break;
                        }
                        offset = end + 2;
                        continue;
                    }
                    state = STATE_INIT;
                    offset++;
                    break;
                }
                default:
                    assert false : state;
            }
        }
    }

    public interface WebTokensCallback {
        void referencedHtmlAttribute(
                @Nullable String tag, @Nullable String attribute, @NonNull String value);

        void referencedJsString(@NonNull String jsString);

        void referencedCssUrl(@NonNull String url);
    }
}
