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

import static com.android.manifmerger.MergingReport.Result.ERROR;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Removes all "tools:" statements from the resulting xml.
 *
 * All attributes belonging to the {@link com.android.SdkConstants#ANDROID_URI} namespace will be
 * removed. If an element contained a "tools:node=\"remove\"" attribute, the element will be
 * deleted. And elements that are themselves in the tools namespace will also be removed.
 */
@Immutable
public class ToolsInstructionsCleaner {

    private static final String REMOVE_OPERATION_XML_MAME =
            NodeOperationType.REMOVE.toCamelCaseName();
    private static final String REMOVE_ALL_OPERATION_XML_MAME =
            NodeOperationType.REMOVE_ALL.toCamelCaseName();

    /**
     * Cleans all attributes belonging to the {@link com.android.SdkConstants#TOOLS_URI} namespace.
     *
     * @param document the document to clean
     * @param logger logger to use in case of errors and warnings.
     * @return the cleaned document or null if an error occurred.
     */
    public static Optional<Document> cleanToolsReferences(
            @NonNull ManifestMerger2.MergeType mergeType,
            @NonNull Document document,
            @NonNull ILogger logger) {

        Preconditions.checkNotNull(document);
        Preconditions.checkNotNull(logger);
        MergingReport.Result result =
                cleanToolsReferences(mergeType, document.getDocumentElement(), logger);
        return result == MergingReport.Result.SUCCESS ? Optional.of(document) : Optional.absent();
    }

    @NonNull
    private static MergingReport.Result cleanToolsReferences(
            @NonNull ManifestMerger2.MergeType mergeType,
            @NonNull Element element,
            @NonNull ILogger logger) {

        if (SdkConstants.TOOLS_URI.equals(element.getNamespaceURI())) {
            // Delete the entire node
            element.getParentNode().removeChild(element);
            return MergingReport.Result.SUCCESS;
        }

        NamedNodeMap namedNodeMap = element.getAttributes();
        if (namedNodeMap != null) {
            // make a copy of the original list of attributes as we will remove some during this
            // process.
            List<Node> attributes = new ArrayList<Node>();
            for (int i = 0; i < namedNodeMap.getLength(); i++) {
                attributes.add(namedNodeMap.item(i));
            }
            for (Node attribute : attributes) {
                if (SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {
                    // we need to special case when the element contained tools:node="remove"
                    // since it also needs to be deleted unless it had a selector.
                    // if this is tools:node="removeAll", we always delete the element whether or
                    // not there is a tools:selector.
                    boolean hasSelector = namedNodeMap.getNamedItemNS(
                            SdkConstants.TOOLS_URI, "selector") != null;
                    if (attribute.getLocalName().equals(NodeOperationType.NODE_LOCAL_NAME)
                            && (attribute.getNodeValue().equals(REMOVE_ALL_OPERATION_XML_MAME)
                                || (attribute.getNodeValue().equals(REMOVE_OPERATION_XML_MAME))
                                    && !hasSelector)) {

                        if (element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                            logger.error(null /* Throwable */,
                                    String.format(
                                        "tools:node=\"%1$s\" not allowed on top level %2$s element",
                                        attribute.getNodeValue(),
                                        XmlNode.unwrapName(element)));
                            return ERROR;
                        } else {
                            // Remove leading comments
                            for (Node comment : XmlElement.getLeadingComments(element)) {
                                comment.getParentNode().removeChild(comment);
                            }

                            element.getParentNode().removeChild(element);
                        }
                    } else {
                        // anything else, we just clean the attribute unless we are merging for
                        // libraries.
                        if (mergeType != ManifestMerger2.MergeType.LIBRARY) {
                            element.removeAttributeNS(
                                    attribute.getNamespaceURI(), attribute.getLocalName());
                        }
                    }
                }
                // this could also be the xmlns:tools declaration.
                if (attribute.getNodeName().startsWith(SdkConstants.XMLNS_PREFIX)
                    && SdkConstants.TOOLS_URI.equals(attribute.getNodeValue())
                        && mergeType != ManifestMerger2.MergeType.LIBRARY) {
                    element.removeAttribute(attribute.getNodeName());
                }
            }
        }
        // make a copy of the element children since we will be removing some during
        // this process, we don't want side effects.
        NodeList childNodes = element.getChildNodes();
        ImmutableList.Builder<Element> childElements = ImmutableList.builder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) node);
            }
        }
        for (Element childElement : childElements.build()) {
            if (cleanToolsReferences(mergeType, childElement, logger) == ERROR) {
                return ERROR;
            }
        }
        return MergingReport.Result.SUCCESS;
    }

}
