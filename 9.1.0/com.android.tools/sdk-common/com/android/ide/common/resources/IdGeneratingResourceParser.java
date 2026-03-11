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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_LAYOUT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.symbols.ResourceExtraXmlParser;
import com.android.resources.ResourceType;
import com.android.utils.XmlUtils;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class is deprecated and it's usages will soon move to a new parser in the Symbols package
 * {@link ResourceExtraXmlParser}.
 *
 * <p>Parser for scanning id-generating XML files (layout or menu).
 *
 * <p>Does not handle data-binding files (throws an exception if parsed).
 */
@Deprecated
class IdGeneratingResourceParser {
    @NonNull private final ResourceMergerItem mFileResourceMergerItem;
    @NonNull private final Set<ResourceMergerItem> mIdResourceMergerItems;
    @NonNull private final ResourceNamespace mNamespace;
    @Nullable private final String mLibraryName;

    /**
     * Parse the file for new IDs, given the source document's name and type. After this completes,
     * the getters can be used to grab the items (the items for the IDs, and the item for the file
     * itself).
     *
     * @param file the file to parse
     * @param sourceName the name of the file-based resource (derived from xml filename)
     * @param sourceType the type of the file-based resource (e.g., menu).
     * @param namespace the namespace of generated {@link ResourceMergerItem} objects.
     * @param libraryName the name of the library the parsed file belongs to, or null if the file
     *     does not belong to a library
     * @throws MergingException if given a data-binding file, or fails to parse.
     */
    IdGeneratingResourceParser(
            @NonNull File file,
            @NonNull String sourceName,
            @NonNull ResourceType sourceType,
            @NonNull ResourceNamespace namespace,
            @Nullable String libraryName)
            throws MergingException {
        mLibraryName = libraryName;
        Document mDocument = readDocument(file);
        if (hasDataBindings(mDocument)) {
            throw MergingException.withMessage("Does not handle data-binding files").build();
        }
        mNamespace = namespace;
        mFileResourceMergerItem =
                new IdResourceMergerItem(sourceName, mNamespace, sourceType, libraryName);
        mIdResourceMergerItems = new LinkedHashSet<>();
        Set<String> pendingResourceIds = new LinkedHashSet<>();
        NodeList nodes = mDocument.getChildNodes();
        for (int i = 0; i < nodes.getLength(); ++i) {
            Node child = nodes.item(i);
            parseIds(mIdResourceMergerItems, child, pendingResourceIds);
        }
        for (String id : pendingResourceIds) {
            ResourceMergerItem resourceItem =
                    new IdResourceMergerItem(id, mNamespace, ResourceType.ID, libraryName);
            mIdResourceMergerItems.add(resourceItem);
        }
    }

    @NonNull
    private static Document readDocument(@NonNull File file) throws MergingException {
        try {
            return XmlUtils.parseUtfXmlFile(file, true /* namespaceAware */);
        }
        catch (SAXException | IOException e) {
            throw MergingException.wrapException(e).withFile(file).build();
        }
    }

    private static boolean hasDataBindings(@NonNull Document document) {
        Node rootNode = document.getDocumentElement();
        if (rootNode != null && TAG_LAYOUT.equals(rootNode.getNodeName())) {
            return true;
        }
        return false;
    }

    @NonNull
    public ResourceMergerItem getFileResourceMergerItem() {
        return mFileResourceMergerItem;
    }

    @NonNull
    public Collection<ResourceMergerItem> getIdResourceMergerItems() {
        return mIdResourceMergerItems;
    }

    private void parseIds(
            @NonNull Set<ResourceMergerItem> items,
            @NonNull Node node,
            @NonNull Set<String> pendingResourceIds) {
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            // For all attributes in the android namespace, check if something has a value of
            // the form "@+id/".
            for (int i = 0; i < attributes.getLength(); ++i) {
                Node attribute = attributes.item(i);
                String value = attribute.getNodeValue();
                if (value == null) {
                    continue;
                }
                if (ANDROID_URI.equals(attribute.getNamespaceURI())
                        && ATTR_ID.equals(attribute.getLocalName())) {
                    // Now process the android:id attribute.
                    String id;
                    if (value.startsWith(ID_PREFIX)) {
                        // If the id is not "@+id/", it may still have been declared as "@+id/"
                        // in a preceding view (eg. layout_above). So, we test if this is such
                        // a pending id.
                        id = value.substring(ID_PREFIX.length());
                        if (!pendingResourceIds.contains(id)) {
                            continue;
                        }
                    } else if (value.startsWith(NEW_ID_PREFIX)) {
                        id = value.substring(NEW_ID_PREFIX.length());
                    } else {
                        continue;
                    }
                    pendingResourceIds.remove(id);
                    if (!id.isEmpty()) {
                        ResourceMergerItem item =
                                new IdResourceMergerItem(
                                        id, mNamespace, ResourceType.ID, mLibraryName);
                        items.add(item);
                    }
                } else if (value.startsWith(NEW_ID_PREFIX)) {
                    // If the attribute is not android:id, and an item for it hasn't been created
                    // yet, add it to the list of pending ids.
                    String id = value.substring(NEW_ID_PREFIX.length());
                    if (!id.isEmpty()) {
                        pendingResourceIds.add(id);
                    }
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node child = children.item(i);
            parseIds(items, child, pendingResourceIds);
        }
    }

    /**
     * A ResourceMergerItem representing an ID item or the source XML file item
     * (ResourceType.LAYOUT, etc), from an ID-generating XML file that supports blob writing without
     * having to link to the source XML.
     */
    public static class IdResourceMergerItem extends ResourceMergerItem {
        /**
         * Constructs the resource with a given name and type. Note that the object is not fully
         * usable as-is. It must be added to a ResourceFile first.
         *
         * @param name the name of the resource
         * @param type the type of the resource (ID, layout, menu).
         * @param libraryName the name of the library the resource belongs to, or null if
         *     the resource does not belong to a library
         */
        public IdResourceMergerItem(
                @NonNull String name,
                @NonNull ResourceNamespace namespace,
                @NonNull ResourceType type,
                @Nullable String libraryName) {
            // Use a null value, since the source XML is something like:
            //     <LinearLayout ... id="@+id/xxx">...</LinearLayout>
            // which is large and inefficient for encoding the resource item, and inefficient to
            // hold on to. Instead synthesize <item name=x type={id/layout/menu} /> as needed.
            super(name, namespace, type, null, null, libraryName);
        }

        @Override
        Node getDetailsXml(Document document) {
            Node newNode = document.createElement(TAG_ITEM);
            NodeUtils.addAttribute(document, newNode, null, ATTR_NAME, getName());
            NodeUtils.addAttribute(document, newNode, null, ATTR_TYPE, getType().getName());
            // Normally layouts are file-based resources and the ResourceValue is the file path.
            // However, we're serializing it as XML and in that case the ResourceValue comes from
            // parsing the XML. So store the file path in the XML to make the ResourceValues equivalent.
            if (isFileBased()) {
                ResourceFile sourceFile = getSourceFile();
                assert sourceFile != null;
                newNode.setTextContent(sourceFile.getFile().getAbsolutePath());
            }
            return newNode;
        }

        @Override
        public boolean isFileBased() {
            return getType() != ResourceType.ID;
        }
    }
}
