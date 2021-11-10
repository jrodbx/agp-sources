/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import java.util.regex.Matcher;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * encode all non resolved placeholders key names.
 */
public class PlaceholderEncoder {

    /**
     * Visits a document's entire tree and check each attribute for a placeholder existence. If one
     * is found, encode its name so tools like aapt will not object invalid characters and such.
     *
     * <p>
     *
     * @param document the document to visit
     */
    public static void visit(@NonNull Document document) {
        visit(document.getDocumentElement());
    }

    /**
     * Visits an element's entire tree and checks each attribute for a placeholder existence. If one
     * is found, encode its name so tools like aapt will not object invalid characters and such.
     *
     * <p>
     *
     * @param element the element to visit
     */
    private static void visit(@NonNull Element element) {
        NamedNodeMap elementAttributes = element.getAttributes();
        for (int i = 0; i < elementAttributes.getLength(); i++) {
            Node attribute = elementAttributes.item(i);
            handleAttribute((Attr) attribute);
        }
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                visit((Element) childNode);
            }
        }
    }

    /**
     * Handles an XML attribute, by subsituting placeholders to an AAPT friendly encoding.
     *
     * @param attr attribute potentially containing a placeholder.
     */
    private static void handleAttribute(Attr attr) {
        Matcher matcher = PlaceholderHandler.PATTERN.matcher(attr.getValue());
        if (matcher.matches()) {
            String encodedValue =
                    matcher.group(1)
                            + "dollar_openBracket_"
                            + matcher.group(2)
                            + "_closeBracket"
                            + matcher.group(3);
            attr.setValue(encodedValue);
        }
    }
}
