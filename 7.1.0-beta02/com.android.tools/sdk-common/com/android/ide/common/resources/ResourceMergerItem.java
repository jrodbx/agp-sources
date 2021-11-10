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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX_LEN;
import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INDEX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ArrayResourceValueImpl;
import com.android.ide.common.rendering.api.AttrResourceValueImpl;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValueImpl;
import com.android.ide.common.rendering.api.PluralsResourceValueImpl;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.rendering.api.StyleResourceValueImpl;
import com.android.ide.common.rendering.api.StyleableResourceValueImpl;
import com.android.ide.common.rendering.api.TextResourceValueImpl;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.utils.HashCodes;
import com.android.utils.XmlUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A resource.
 *
 * <p>Includes the name, type, source file as a {@link ResourceFile} and an optional {@link Node}
 * in case of a resource coming from a value file.
 */
public class ResourceMergerItem extends DataItem<ResourceFile>
        implements Comparable<ResourceMergerItem>, ResourceItem {
    /** The name of the synthetic XML attribute used for storing ATTR description. */
    public static final String ATTR_DESCRIPTION = "_description";
    /** The name of the synthetic XML attribute used for storing ATTR group name. */
    public static final String ATTR_GROUP_NAME = "_groupName";

    @NonNull private final ResourceType mType;

    /**
     * Namespace of the library this item came from.
     *
     * <p>This plays no part in the merging process, because the whole point of merging is that we
     * merge items from different namespaces. Unfortunately we use {@link ResourceMergerItem} and
     * {@link ResourceValue} almost interchangeably, so for now this needs to be here.
     *
     * <p>TODO: Make {@link ResourceValue} {@link Configurable} and switch the whole repository
     * system to deal only with {@link ResourceValue} instances.
     */
    @NonNull private final ResourceNamespace mNamespace;

    @Nullable private Node mValue;

    /**
     * Whether the resource comes from a dependency or from the current subproject, or `null` if
     * this information is not available.
     */
    @Nullable Boolean mIsFromDependency;

    @Nullable private String mLibraryName;

    @Nullable protected ResourceValue mResourceValue;

    /**
     * Constructs the object with a name, type and optional value.
     *
     * <p>Note that the object is not fully usable as-is. It must be added to a ResourceFile first.
     *
     * @param name the name of the resource
     * @param namespace the namespace of the resource
     * @param type the type of the resource
     * @param value an optional Node that represents the resource value.
     * @param isFromDependency whether the resource comes from a dependency or from the current
     *     subproject, or `null` if this information is not available
     * @param libraryName the name of the library the resource belongs to, or null if the resource
     *     does not belong to a library
     */
    public ResourceMergerItem(
            @NonNull String name,
            @NonNull ResourceNamespace namespace,
            @NonNull ResourceType type,
            @Nullable Node value,
            @Nullable Boolean isFromDependency,
            @Nullable String libraryName) {
        super(name);

        // The only exception is the empty "<public />" tag which means that all resources are
        // private.
        Preconditions.checkArgument(
                type == ResourceType.PUBLIC || !name.isEmpty(), "Resource name cannot be empty.");

        mNamespace = namespace;
        mType = type;
        mValue = value;
        mIsFromDependency = isFromDependency;
        mLibraryName = libraryName;
    }

    @Override
    @NonNull
    public ResourceType getType() {
        return mType;
    }

    /**
     * Returns the optional value of the resource. Can be null
     *
     * @return the value or null.
     */
    @Nullable
    public Node getValue() {
        return mValue;
    }

    /**
     * Returns the optional string value of the resource.
     *
     * @return the value or null.
     */
    @Nullable
    public String getValueText() {
        if (mValue == null) {
            return null;
        }
        synchronized (mValue.getOwnerDocument()) {
            return mValue.getTextContent();
        }
    }

    @Override
    @Nullable
    public String getLibraryName() {
        return mLibraryName;
    }

    @Override
    @NonNull
    public ResourceNamespace getNamespace() {
        return mNamespace;
    }

    /**
     * Returns the resource item qualifiers.
     *
     * @return the qualifiers
     */
    @NonNull
    public String getQualifiers() {
        ResourceFile resourceFile = getSourceFile();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getQualifier on " + toString());
        }

        return resourceFile.getQualifiers();
    }

    @NonNull
    public DataFile.FileType getSourceType() {
        ResourceFile resourceFile = getSourceFile();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getSourceType on " + toString());
        }

        return resourceFile.getType();
    }

    @Override
    @NonNull
    public ResourceReference getReferenceToSelf() {
        return new ResourceReference(mNamespace, mType, getName());
    }

    /**
     * Sets the value of the resource and set its state to TOUCHED.
     *
     * @param from the resource to copy the value from.
     */
    void setValue(@NonNull ResourceMergerItem from) {
        mValue = from.mValue;
        setTouched();
    }

    @Override
    @NonNull
    public FolderConfiguration getConfiguration() {
        ResourceFile resourceFile = getSourceFile();
        if (resourceFile == null) {
            throw new RuntimeException("Cannot call getConfiguration on " + toString());
        }
        return resourceFile.getFolderConfiguration();
    }

    /**
     * Returns a key for this resource. They key uniquely identifies this resource by combining
     * resource type, qualifiers, namespace and name.
     *
     * <p>If the resource has not been added to a {@link ResourceFile}, this will throw an {@link
     * IllegalStateException}.
     *
     * @return the key for this resource.
     * @throws IllegalStateException if the resource is not added to a ResourceFile
     */
    @Override
    @NonNull
    public String getKey() {
        if (getSourceFile() == null) {
            throw new IllegalStateException(
                    "ResourceItem.getKey called on object with no ResourceFile: " + this);
        }
        String qualifiers = getQualifiers();

        String typeName = mType.getName();
        if (mType == ResourceType.PUBLIC && mValue != null) {
            String typeAttribute;
            synchronized (mValue.getOwnerDocument()) {
                typeAttribute = ((Element) mValue).getAttribute(ATTR_TYPE);
            }
            if (typeAttribute != null) {
                typeName += "_" + typeAttribute;
            }
        }

        if (!qualifiers.isEmpty()) {
            return typeName + "-" + qualifiers + "/" + getName();
        }

        return typeName + "/" + getName();
    }

    @Override
    protected void wasTouched() {
        mResourceValue = null;
    }

    @Override
    @NonNull
    public ResourceValue getResourceValue() {
        if (mResourceValue == null) {
            //noinspection VariableNotUsedInsideIf
            if (mValue == null) {
                ResourceFile source = getSourceFile();
                assert source != null;

                // Density based resource value?
                Density density =
                        DensityBasedResourceValue.isDensityBasedResourceType(mType)
                                ? getFolderDensity()
                                : null;

                if (density != null) {
                    mResourceValue =
                            new DensityBasedResourceValueImpl(
                                    mNamespace,
                                    mType,
                                    getName(),
                                    source.getFile().getAbsolutePath(),
                                    density,
                                    mLibraryName);
                } else {
                    mResourceValue =
                            new ResourceValueImpl(
                                    mNamespace,
                                    mType,
                                    getName(),
                                    source.getFile().getAbsolutePath(),
                                    mLibraryName);
                }
            } else {
                mResourceValue = parseXmlToResourceValue();
            }
        }

        return mResourceValue;
    }

    @Override
    @Nullable
    public PathString getSource() {
        File file = getFile();
        return file == null ? null : new PathString(file);
    }

    @Override
    public boolean isFileBased() {
        return getSourceType() != DataFile.FileType.XML_VALUES;
    }

    // TODO: We should be storing shared FolderConfiguration instances on the ResourceFiles
    // instead. This is a temporary fix to make rendering work properly again.
    @Nullable
    private Density getFolderDensity() {
        String qualifiers = getQualifiers();
        if (!qualifiers.isEmpty() && qualifiers.contains("dpi")) {
            Iterable<String> segments = Splitter.on('-').split(qualifiers);
            FolderConfiguration config = FolderConfiguration.getConfigFromQualifiers(segments);
            if (config != null) {
                DensityQualifier densityQualifier = config.getDensityQualifier();
                if (densityQualifier != null) {
                    return densityQualifier.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns a formatted string usable in an XML to use for the {@link ResourceMergerItem}.
     *
     * @param system Whether this is a system resource or a project resource.
     * @return a string in the format @[type]/[name]
     */
    public String getXmlString(ResourceType type, boolean system) {
        return (system ? ANDROID_PREFIX : PREFIX_RESOURCE_REF) + type.getName() + "/" + getName();
    }

    /**
     * Compares the ResourceItem {@link #getValue()} together and returns true if they are the same.
     *
     * @param resource The ResourceItem object to compare to.
     * @return true if equal
     */
    public boolean compareValueWith(ResourceMergerItem resource) {
        if (mValue != null && resource.mValue != null) {
            return NodeUtils.compareElementNode(mValue, resource.mValue, true);
        }

        return mValue == resource.mValue;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("namespace", getNamespace())
                .add("type", getType())
                .add("status", getStatus())
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false;
        }

        ResourceMergerItem other = (ResourceMergerItem) o;

        return mType == other.mType && mNamespace.equals(other.mNamespace);
    }

    @Override
    public int hashCode() {
        return HashCodes.mix(super.hashCode(), mType.hashCode(), mNamespace.hashCode());
    }

    @NonNull
    private ResourceValue parseXmlToResourceValue() {
        assert mValue != null;

        ResourceValueImpl value;

        Document document = mValue.getOwnerDocument();
        // XML nodes used by FrameworkResourceRepository don't maintain document references
        // but they are thread-safe.
        if (document == null) {
            value = doParseXmlToResourceValue();
        } else {
            // Apache Xerces XML DOM is not thread-safe even for reading.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (document) {
                value = doParseXmlToResourceValue();
            }
        }

        value.setNamespaceResolver(getNamespaceResolver(this.mValue));
        return value;
    }

    @NonNull
    private ResourceValueImpl doParseXmlToResourceValue() {
        NamedNodeMap attributes = mValue.getAttributes();

        switch (mType) {
            case STYLE:
                String parent = getAttributeValue(attributes, ATTR_PARENT);
                return parseStyleValue(
                        new StyleResourceValueImpl(mNamespace, getName(), parent, mLibraryName));

            case STYLEABLE:
                return parseDeclareStyleable(
                        new StyleableResourceValueImpl(mNamespace, getName(), null, mLibraryName));

            case ARRAY:
                ArrayResourceValueImpl arrayValue =
                        new ArrayResourceValueImpl(mNamespace, getName(), mLibraryName) {
                            @Override
                            protected int getDefaultIndex() {
                                // Allow the user to specify a specific element to use via tools:index
                                String toolsDefaultIndex =
                                        getAttributeValueNS(attributes, TOOLS_URI, ATTR_INDEX);
                                if (toolsDefaultIndex != null) {
                                    try {
                                        return Integer.parseInt(toolsDefaultIndex);
                                    } catch (NumberFormatException e) {
                                        return super.getDefaultIndex();
                                    }
                                }
                                return super.getDefaultIndex();
                            }
                        };
                return parseArrayValue(arrayValue);

            case PLURALS:
                PluralsResourceValueImpl pluralsResourceValue =
                        new PluralsResourceValueImpl(mNamespace, getName(), null, mLibraryName) {
                            @Override
                            public String getValue() {
                                // Allow the user to specify tools:quantity.
                                String quantity =
                                        getAttributeValueNS(attributes, TOOLS_URI, ATTR_QUANTITY);
                                if (quantity != null) {
                                    String value = getValue(quantity);
                                    if (value != null) {
                                        return value;
                                    }
                                }
                                return super.getValue();
                            }
                        };
                return parsePluralsValue(pluralsResourceValue);

            case ATTR:
                return parseAttrValue(
                        new AttrResourceValueImpl(mNamespace, getName(), mLibraryName));

            case STRING:
                return parseTextValue(
                        new TextResourceValueImpl(mNamespace, getName(), null, null, mLibraryName));

            case ANIMATOR:
            case DRAWABLE:
            case INTERPOLATOR:
            case LAYOUT:
            case MENU:
            case MIPMAP:
            case TRANSITION:
                return parseFileName(
                        new ResourceValueImpl(mNamespace, mType, getName(), null, mLibraryName));

            default:
                return parseValue(
                        new ResourceValueImpl(mNamespace, mType, getName(), null, mLibraryName));
        }
    }

    @NonNull
    private static ResourceNamespace.Resolver getNamespaceResolver(@NonNull Node node) {
        // TODO(namespaces): precompute this?
        return new ResourceNamespace.Resolver() {
            @Nullable
            @Override
            public String uriToPrefix(@NonNull String namespaceUri) {
                Document document = node.getOwnerDocument();
                // XML nodes used by FrameworkResourceRepository don't maintain document references
                // but they are thread-safe.
                if (document == null) {
                    return node.lookupPrefix(namespaceUri);
                } else {
                    // Apache Xerces XML DOM is not thread-safe even for reading.
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (document) {
                        return node.lookupPrefix(namespaceUri);
                    }
                }
            }

            @Nullable
            @Override
            public String prefixToUri(@NonNull String namespacePrefix) {
                Document document = node.getOwnerDocument();
                // XML nodes used by FrameworkResourceRepository don't maintain document references
                // but they are thread-safe.
                if (document == null) {
                    return node.lookupNamespaceURI(namespacePrefix);
                } else {
                    // Apache Xerces XML DOM is not thread-safe even for reading.
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (document) {
                        return node.lookupNamespaceURI(namespacePrefix);
                    }
                }
            }
        };
    }

    @Nullable
    private static String getAttributeValue(NamedNodeMap attributes, String attributeName) {
        Attr attribute = (Attr) attributes.getNamedItem(attributeName);
        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    @Nullable
    private static String getAttributeValueNS(
            @NonNull NamedNodeMap attributes,
            @Nullable String namespaceUri,
            @NonNull String attributeName) {
        Attr attribute = (Attr) attributes.getNamedItemNS(namespaceUri, attributeName);
        if (attribute != null) {
            return attribute.getValue();
        }

        return null;
    }

    @NonNull
    private StyleResourceValueImpl parseStyleValue(@NonNull StyleResourceValueImpl styleValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String attributeUrl = getAttributeValue(attributes, ATTR_NAME);
                if (!Strings.isNullOrEmpty(attributeUrl)) {
                    StyleItemResourceValueImpl resValue =
                            new StyleItemResourceValueImpl(
                                    styleValue.getNamespace(),
                                    attributeUrl,
                                    ValueXmlHelper.unescapeResourceString(
                                            getTextNode(child.getChildNodes()), false, true),
                                    styleValue.getLibraryName());
                    resValue.setNamespaceResolver(getNamespaceResolver(child));
                    styleValue.addItem(resValue);
                }
            }
        }

        return styleValue;
    }

    @NonNull
    private AttrResourceValueImpl parseAttrValue(@NonNull AttrResourceValueImpl attrValue) {
        return parseAttrValue(mValue, attrValue);
    }

    @NonNull
    private static AttrResourceValueImpl parseAttrValue(
            @NonNull Node valueNode, @NonNull AttrResourceValueImpl attrValue) {
        NamedNodeMap attributes = valueNode.getAttributes();
        // FrameworkResourceRepository stores "attr" description in the synthetic "_description"
        // attribute. For other repositories we need to get it from the preceding comment.
        String description = getDescription(valueNode);
        attrValue.setDescription(description);

        // "attr" resource grouping is available only for framework resources, which store group
        // name as the value of the synthetic "_groupName" attribute.
        String groupName = getAttributeValue(attributes, ATTR_GROUP_NAME);
        if (groupName != null) {
            attrValue.setGroupName(groupName);
        }

        Set<AttributeFormat> formats = EnumSet.noneOf(AttributeFormat.class);
        String formatString = getAttributeValue(attributes, ATTR_FORMAT);
        if (formatString != null) {
            formats.addAll(AttributeFormat.parse(formatString));
        }

        NodeList children = valueNode.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = ((Element)child).getTagName();
                if (TAG_ENUM.equals(tagName)) {
                    formats.add(AttributeFormat.ENUM);
                } else if (TAG_FLAG.equals(tagName)) {
                    formats.add(AttributeFormat.FLAGS);
                }

                NamedNodeMap childAttributes = child.getAttributes();
                String name = getAttributeValue(childAttributes, ATTR_NAME);
                if (name != null) {
                    description = getDescription(child);
                    Integer numericValue = null;
                    String value = getAttributeValue(childAttributes, ATTR_VALUE);
                    if (value != null) {
                        try {
                            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
                            // use Long.decode instead.
                            numericValue = Long.decode(value).intValue();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    attrValue.addValue(name, numericValue, description);
                }
            }
        }

        attrValue.setFormats(formats);

        return attrValue;
    }

    private static String getDescription(@NonNull Node node) {
        // FrameworkResourceRepository stores description in the synthetic "description"
        // attribute. For other repositories we need to get it from the preceding comment.
        String description = getAttributeValue(node.getAttributes(), ATTR_DESCRIPTION);
        if (description == null) {
            description = XmlUtils.getPreviousCommentText(node);
        }
        return description;
    }

    private ArrayResourceValueImpl parseArrayValue(ArrayResourceValueImpl arrayValue) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String text = getTextNode(child.getChildNodes());
                text = ValueXmlHelper.unescapeResourceString(text, false, true);
                if (text != null) {
                    arrayValue.addElement(text);
                }
            }
        }

        return arrayValue;
    }

    private PluralsResourceValueImpl parsePluralsValue(PluralsResourceValueImpl value) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String quantity = getAttributeValue(attributes, ATTR_QUANTITY);
                if (quantity != null) {
                    String text = getTextNode(child.getChildNodes());
                    text = ValueXmlHelper.unescapeResourceString(text, false, true);
                    value.addPlural(quantity, text);
                }
            }
        }

        return value;
    }

    @NonNull
    private StyleableResourceValueImpl parseDeclareStyleable(
            @NonNull StyleableResourceValueImpl declareStyleable) {
        NodeList children = mValue.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attributes = child.getAttributes();
                String name = getAttributeValue(attributes, ATTR_NAME);
                if (name != null) {
                    // is the attribute in the android namespace?
                    ResourceNamespace namespace = declareStyleable.getNamespace();
                    if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                        name = name.substring(ANDROID_NS_NAME_PREFIX_LEN);
                        namespace = ResourceNamespace.ANDROID;
                    }

                    AttrResourceValueImpl attr =
                            parseAttrValue(
                                    child,
                                    new AttrResourceValueImpl(namespace, name, mLibraryName));
                    attr.setNamespaceResolver(getNamespaceResolver(child));
                    declareStyleable.addValue(attr);
                }
            }
        }

        return declareStyleable;
    }

    @NonNull
    private ResourceValueImpl parseValue(@NonNull ResourceValueImpl value) {
        String text = getTextNode(mValue.getChildNodes());
        value.setValue(ValueXmlHelper.unescapeResourceString(text, false, true));

        return value;
    }

    @NonNull
    private ResourceValueImpl parseFileName(@NonNull ResourceValueImpl value) {
        String text = getTextNode(mValue.getChildNodes()).trim();
        if (!text.isEmpty()
                && !text.startsWith(PREFIX_RESOURCE_REF)
                && !text.startsWith(PREFIX_THEME_REF)) {
            text = Paths.get(text).toString();
        }

        value.setValue(text);
        return value;
    }

    @NonNull
    private static String getTextNode(@NonNull NodeList children) {
        int n = children.getLength();
        if (n == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < n; i++) {
            Node child = children.item(i);

            short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.ELEMENT_NODE:
                    {
                        Element element = (Element) child;
                        if (XLIFF_G_TAG.equals(element.getLocalName())
                                && element.getNamespaceURI() != null
                                && element.getNamespaceURI().startsWith(XLIFF_NAMESPACE_PREFIX)) {
                            if (element.hasAttribute(ATTR_EXAMPLE)) {
                                // <xliff:g id="number" example="7">%d</xliff:g> minutes
                                // => "(7) minutes"
                                String example = element.getAttribute(ATTR_EXAMPLE);
                                sb.append('(').append(example).append(')');
                                continue;
                            } else if (element.hasAttribute(ATTR_ID)) {
                                // Step <xliff:g id="step_number">%1$d</xliff:g>
                                // => Step ${step_number}
                                String id = element.getAttribute(ATTR_ID);
                                sb.append('$').append('{').append(id).append('}');
                                continue;
                            }
                        }

                        NodeList childNodes = child.getChildNodes();
                        sb.append(getTextNode(childNodes));
                        break;
                    }
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue());
                    break;
                case Node.CDATA_SECTION_NODE:
                    sb.append(child.getNodeValue());
                    break;
            }
        }

        return sb.toString();
    }

    @NonNull
    private TextResourceValueImpl parseTextValue(@NonNull TextResourceValueImpl value) {
        NodeList children = mValue.getChildNodes();
        String text = getTextNode(children);
        value.setValue(ValueXmlHelper.unescapeResourceString(text, false, true));

        int length = children.getLength();

        if (length >= 1) {
            boolean haveElementChildrenOrCdata = false;
            for (int i = 0; i < length; i++) {
                if (children.item(i).getNodeType() == Node.ELEMENT_NODE
                        || children.item(i).getNodeType() == Node.CDATA_SECTION_NODE) {
                    haveElementChildrenOrCdata = true;
                    break;
                }
            }

            if (haveElementChildrenOrCdata) {
                String markupText = getMarkupText(children);
                value.setRawXmlValue(markupText);
            }
        }

        return value;
    }

    @NonNull
    private static String getMarkupText(@NonNull NodeList children) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);

            short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.ELEMENT_NODE:
                    {
                        Element element = (Element) child;
                        String tagName = element.getTagName();
                        sb.append('<');
                        sb.append(tagName);

                        NamedNodeMap attributes = element.getAttributes();
                        int attributeCount = attributes.getLength();
                        if (attributeCount > 0) {
                            for (int j = 0; j < attributeCount; j++) {
                                Node attribute = attributes.item(j);
                                sb.append(' ');
                                sb.append(attribute.getNodeName());
                                sb.append('=').append('"');
                                XmlUtils.appendXmlAttributeValue(sb, attribute.getNodeValue());
                                sb.append('"');
                            }
                        }
                        sb.append('>');

                        NodeList childNodes = child.getChildNodes();
                        if (childNodes.getLength() > 0) {
                            sb.append(getMarkupText(childNodes));
                        }

                        sb.append('<');
                        sb.append('/');
                        sb.append(tagName);
                        sb.append('>');

                        break;
                    }
                case Node.TEXT_NODE:
                    sb.append(child.getNodeValue());
                    break;
                case Node.CDATA_SECTION_NODE:
                    sb.append("<![CDATA[");
                    sb.append(child.getNodeValue());
                    sb.append("]]>");
                    break;
            }
        }

        return sb.toString();
    }

    @Override
    public int compareTo(@NonNull ResourceMergerItem other) {
        int comp = mType.compareTo(other.mType);
        if (comp != 0) {
            return comp;
        }
        comp = mNamespace.compareTo(other.mNamespace);
        if (comp != 0) {
            return comp;
        }
        return getName().compareTo(other.getName());
    }

    private boolean mIgnoredFromDiskMerge = false;

    public void setIgnoredFromDiskMerge(boolean ignored) {
        mIgnoredFromDiskMerge = ignored;
    }

    public boolean getIgnoredFromDiskMerge() {
        return mIgnoredFromDiskMerge;
    }

    // Used for the blob writing.
    // TODO: move this to ResourceMerger/Set.

    @Override
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        NodeUtils.addAttribute(document, node, null, ATTR_TYPE, mType.getName());
    }

    @Override
    Node getDetailsXml(Document document) {
        return NodeUtils.duplicateAndAdoptNode(document, mValue);
    }
}
