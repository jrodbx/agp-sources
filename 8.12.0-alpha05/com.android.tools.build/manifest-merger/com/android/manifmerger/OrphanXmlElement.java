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

import static com.android.manifmerger.FeatureFlag.ATTRIBUTE_NAME;
import static com.android.manifmerger.FeatureFlag.NAMESPACE_URI;
import static com.android.manifmerger.ManifestModel.NodeTypes;

import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.XmlUtils;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Optional;

/**
 * An xml element that does not belong to a {@link com.android.manifmerger.XmlDocument}
 */
public class OrphanXmlElement extends XmlNode {

    @NotNull
    private final Element mXml;

    @NotNull
    private final NodeTypes mType;

    public OrphanXmlElement(@NotNull Element xml, @NotNull DocumentModel<NodeTypes> model) {
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

    @NotNull
    @Override
    public Element getXml() {
        return mXml;
    }

    @NotNull
    public String getNamespaceURI() {
        return mXml.getNamespaceURI();
    }

    @NotNull
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

    @NotNull
    public String lookupNamespacePrefix(@NotNull String nsUri, boolean create) {
        return XmlUtils.lookupNamespacePrefix(getXml(), nsUri, create);
    }

    @NotNull
    public String lookupNamespacePrefix(
            @NotNull String nsUri, @NotNull String defaultPrefix, boolean create) {
        return XmlUtils.lookupNamespacePrefix(getXml(), nsUri, defaultPrefix, create);
    }

    @Nullable
    public Attr getAttributeNode(String name) {
        return mXml.getAttributeNode(name);
    }

    @Nullable
    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        return mXml.getAttributeNodeNS(namespaceURI, localName);
    }

    /** Retrieves the feature flag from the XML element's featureFlag attribute. */
    @Nullable
    public FeatureFlag featureFlag() {
        Attr featureFlagAttribute = getAttributeNodeNS(NAMESPACE_URI, ATTRIBUTE_NAME);
        if (featureFlagAttribute != null) {
            return FeatureFlag.Companion.from(featureFlagAttribute.getValue());
        }
        return null;
    }

    public boolean hasFeatureFlag() {
        return featureFlag() != null;
    }

    @NotNull
    @Override
    public NodeKey getId() {
        String featureFlagSuffix =
                Optional.ofNullable(featureFlag())
                        .map(flag -> "#" + flag.getAttributeValue())
                        .orElse("");
        String idPrefix =
                (getKey() == null || getKey().isEmpty())
                        ? getName().toString()
                        : getName() + "#" + getKey();
        return new NodeKey(idPrefix + featureFlagSuffix);
    }

    @NotNull
    @Override
    public NodeName getName() {
        return XmlNode.unwrapName(mXml);
    }

    /**
     * Returns this xml element {@link NodeTypes}
     */
    @NotNull
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

    @NotNull
    @Override
    public SourcePosition getPosition() {
        return SourcePosition.UNKNOWN;
    }

    @Override
    @NotNull
    public SourceFile getSourceFile() {
        return SourceFile.UNKNOWN;
    }
}

