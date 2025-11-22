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
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * encode all non resolved placeholders key names.
 */
public class PlaceholderEncoder {

    /**
     * Iterate through each attribute for a placeholder existence. If one is found, encode its name
     * so tools like aapt will not object invalid characters and such.
     *
     * @param node node to visit attributes on
     * @return true if node was changed. False otherwise.
     */
    public static boolean encode(@NonNull Node node) {
        if (node instanceof Element) {
            boolean changeFlag = false;
            Element element = (Element) node;
            NamedNodeMap elementAttributes = element.getAttributes();
            for (int i = 0; i < elementAttributes.getLength(); i++) {
                Node attribute = elementAttributes.item(i);
                changeFlag |= handleAttribute((Attr) attribute);
            }
            return changeFlag;
        }
        return false;
    }

    /**
     * Handles an XML attribute, by subsituting placeholders to an AAPT friendly encoding.
     *
     * @param attr attribute potentially containing a placeholder.
     * @return true if attribute was changed.
     */
    private static boolean handleAttribute(Attr attr) {
        Matcher matcher = PlaceholderHandler.PATTERN.matcher(attr.getValue());
        if (matcher.matches()) {
            String maybeSlash = "";
            // Ensure path attribute values start with "/" (b/316057932)
            if (matcher.group(1).isEmpty() && "path".equals(attr.getLocalName())) {
                maybeSlash = "/";
            }
            String encodedValue =
                    matcher.group(1)
                            + maybeSlash
                            + "dollar_openBracket_"
                            + matcher.group(2)
                            + "_closeBracket"
                            + matcher.group(3);
            attr.setValue(encodedValue);
            return true;
        }
        return false;
    }
}
