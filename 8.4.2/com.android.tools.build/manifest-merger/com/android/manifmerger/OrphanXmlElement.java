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

import static com.android.manifmerger.ManifestModel.NodeTypes;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Optional;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * An xml element that does not belong to a {@link com.android.manifmerger.XmlDocument}
 */
public class OrphanXmlElement extends XmlNode {

    @NonNull
    private final Element mXml;

    @NonNull
    private final NodeTypes mType;

    public OrphanXmlElement(@NonNull Element xml, @NonNull DocumentModel<NodeTypes> model) {
        mXml = Preconditions.checkNotNull(xml);
        String elementName = mXml.getNodeName();
        // if there's a namespace prefix, just strip it. The DocumentModel does not look at
        // namespaces right now.
        mType = model.fromXmlSimpleName(elementName.substring(elementName.indexOf(':') + 1));
    }

    /**
     * Returns true if this xml element's {@link NodeTypes} is
     * the passed one.
     */
    public boolean isA(NodeTypes type) {
        return this.mType == type;
    }

    @NonNull
    @Override
    public Element getXml() {
        return mXml;
    }

    @NonNull
    public String getNamespaceURI() {
        return mXml.getNamespaceURI();
    }

    @NonNull
    public String getTagName() {
        return mXml.getTagName();
    }

    @Nullable
    public String getAttributeValue(String namespaceUri, String localName) {
        var namedNodeMap = getXml().getAttributes();
        return Optional.ofNullable(namedNodeMap.getNamedItemNS(namespaceUri, localName))
                .map(Node::getNodeValue)
                .orElse(null);
    }

    @Nullable
    public String getAttributeInfo(String namespaceUri, String attributeName) {
        var element = getXml();
        var attr = element.getAttributeNodeNS(namespaceUri, attributeName);
        if (attr == null) {
            return null;
        }
        return element.getTagName() + ":" + attributeName + ":" + attr.getValue();
    }

    @NonNull
    public String lookupNamespacePrefix(@NonNull String nsUri, boolean create) {
        return XmlUtils.lookupNamespacePrefix(getXml(), nsUri, create);
    }

    @NonNull
    public String lookupNamespacePrefix(
            @NonNull String nsUri, @NonNull String defaultPrefix, boolean create) {
        return XmlUtils.lookupNamespacePrefix(getXml(), nsUri, defaultPrefix, create);
    }

    @NonNull
    public Attr getAttributeNode(String name) {
        return mXml.getAttributeNode(name);
    }

    @Nullable
    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        return mXml.getAttributeNodeNS(namespaceURI, localName);
    }

    @NonNull
    @Override
    public NodeKey getId() {
        return new NodeKey(Strings.isNullOrEmpty(getKey())
                ? getName().toString()
                : getName().toString() + "#" + getKey());
    }

    @NonNull
    @Override
    public NodeName getName() {
        return XmlNode.unwrapName(mXml);
    }

    /**
     * Returns this xml element {@link NodeTypes}
     */
    @NonNull
    public NodeTypes getType() {
        return mType;
    }

    /**
     * Returns the unique key for this xml element within the xml file or null if there can be only
     * one element of this type.
     */
    @Nullable
    public String getKey() {
        return mType.getNodeKeyResolver().getKey(mXml);
    }

    @NonNull
    @Override
    public SourcePosition getPosition() {
        return SourcePosition.UNKNOWN;
    }

    @Override
    @NonNull
    public SourceFile getSourceFile() {
        return SourceFile.UNKNOWN;
    }
}

