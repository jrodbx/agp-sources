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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.resources.MergingException;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Xml {@link Element} which is mergeable.
 *
 * <p>A mergeable element can contains 3 types of children :
 *
 * <ul>
 *   <li>a child element, which itself may or may not be mergeable.
 *   <li>xml attributes which are related to the element.
 *   <li>tools oriented attributes to trigger specific behaviors from the merging tool
 * </ul>
 *
 * The two main responsibilities of this class is to be capable of comparing itself against another
 * instance of the same type as well as providing XML element merging capabilities.
 */
public class XmlElement extends OrphanXmlElement {

    @NonNull
    private final XmlDocument mDocument;

    @NonNull private ElementOperationsAndMergeRuleMarkers mSelectorsAndMergeRuleMarkers;

    // list of non tools related attributes.
    @NonNull private ImmutableList<XmlAttribute> mAttributes;

    // list of mergeable children elements.
    @NonNull private ImmutableList<XmlElement> mMergeableChildren = ImmutableList.of();

    public XmlElement(@NonNull Element xml, @NonNull XmlDocument document) {
        super(xml, document.getModel());

        mDocument = Preconditions.checkNotNull(document);
        NamedNodeMap namedNodeMap = getXml().getAttributes();
        mSelectorsAndMergeRuleMarkers = extractOperationAndSelectors(namedNodeMap);
        mAttributes = buildXmlAttributes(namedNodeMap);
        mMergeableChildren = initMergeableChildren();
    }

    /**
     * Inspect all child elements to find the first node of given type
     *
     * @param nodeType nodeType of the child.
     * @return First child of given nodeType or {@code Optional.empty()} if no child of this type.
     */
    @NonNull
    private Optional<XmlElement> getFirstChildElementOfType(ManifestModel.NodeTypes nodeType) {
        for (XmlElement childElement : getMergeableElements()) {
            if (childElement.getType().equals(nodeType)) {
                return Optional.of(childElement);
            }
        }
        return Optional.empty();
    }

    /**
     * Applies the provided consumer on the first child element of the provided node type
     *
     * @param nodeType
     * @param nodeConsumer
     */
    public void applyToFirstChildElementOfType(
            @NonNull ManifestModel.NodeTypes nodeType, Consumer<XmlElement> nodeConsumer) {
        Optional<XmlElement> childElementByType = getFirstChildElementOfType(nodeType);
        childElementByType.ifPresent(nodeConsumer);
    }

    /**
     * Check whether this element or any of its descendants have an attribute with the given
     * namespace
     *
     * @param prefix the namespace prefix under consideration
     * @return true if element or any of its descendants have an attribute with the given namespace,
     *     false otherwise.
     */
    public boolean elementUsesNamespacePrefix(@NonNull String prefix) {
        return elementUsesNamespacePrefix(getXml(), prefix);
    }

    /**
     * Check whether element or any of its descendants have an attribute with the given namespace
     *
     * @param element the element under consideration
     * @param prefix the namespace prefix under consideration
     * @return true if element or any of its descendants have an attribute with the given namespace,
     *     false otherwise.
     */
    private static boolean elementUsesNamespacePrefix(
            @NonNull Element element, @NonNull String prefix) {
        NamedNodeMap namedNodeMap = element.getAttributes();
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node attribute = namedNodeMap.item(i);
            if (prefix.equals(attribute.getPrefix())) {
                return true;
            }
        }
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element) {
                if (elementUsesNamespacePrefix((Element) childNode, prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Inspect the provided attributes({@code namedNodeMap}) and extract both node and attribute
     * operations along with the merge rule markers that help with resolving merge conflict during
     * manifest merger.
     *
     * @param namedNodeMap Attributes on wrapped DOM element.
     * @return Node/Attribute operations and merge rule markers for this element
     */
    @NonNull
    private ElementOperationsAndMergeRuleMarkers extractOperationAndSelectors(
            NamedNodeMap namedNodeMap) {
        Selector selector = null;
        List<Selector> overrideUsesSdkLibrarySelectors = ImmutableList.of();

        ImmutableMap.Builder<NodeName, AttributeOperationType> attributeOperationTypeBuilder =
                ImmutableMap.builder();
        NodeOperationType lastNodeOperationType = null;
        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Node attribute = namedNodeMap.item(i);
            if (SdkConstants.TOOLS_URI.equals(attribute.getNamespaceURI())) {
                String instruction = attribute.getLocalName();
                if (instruction.equals(NodeOperationType.NODE_LOCAL_NAME)) {
                    // should we flag an error when there are more than one operation type on a node ?
                    lastNodeOperationType = NodeOperationType.valueOf(
                            SdkUtils.camelCaseToConstantName(
                                    attribute.getNodeValue()));
                } else if (instruction.equals(
                        NodeOperationType.REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME)) {
                    continue;
                } else if (instruction.equals(Selector.SELECTOR_LOCAL_NAME)) {
                    selector = new Selector(attribute.getNodeValue());
                } else if (instruction.equals(NodeOperationType.OVERRIDE_USES_SDK)) {
                    String nodeValue = attribute.getNodeValue();
                    ImmutableList.Builder<Selector> builder = ImmutableList.builder();
                    for (String selectorValue : Splitter.on(',').split(nodeValue)) {
                        builder.add(new Selector(selectorValue.trim()));
                    }
                    overrideUsesSdkLibrarySelectors = builder.build();
                } else {
                    AttributeOperationType attributeOperationType;
                    try {
                        attributeOperationType =
                                AttributeOperationType.valueOf(
                                        SdkUtils.xmlNameToConstantName(instruction));
                    } catch (IllegalArgumentException e) {
                        try {
                            // is this another tool's operation type that we do not care about.
                            OtherOperationType.valueOf(instruction.toLowerCase(Locale.ROOT));
                            continue;
                        } catch (IllegalArgumentException e1) {

                            String errorMessage =
                                    String.format("Invalid instruction '%1$s', "
                                                    + "valid instructions are : %2$s",
                                            instruction,
                                            Joiner.on(',').join(AttributeOperationType.values())
                                    );
                            throw new RuntimeException(
                                    MergingException.wrapException(e)
                                            .withMessage(errorMessage)
                                            .withFile(mDocument.getSourceFile())
                                            .withPosition(XmlDocument.getNodePosition(getXml()))
                                            .build());
                        }
                    }
                    for (String attributeName : Splitter.on(',').trimResults()
                            .split(attribute.getNodeValue())) {
                        if (attributeName.indexOf(XmlUtils.NS_SEPARATOR) == -1) {
                            String toolsPrefix = XmlUtils
                                    .lookupNamespacePrefix(getXml(), SdkConstants.TOOLS_URI,
                                            SdkConstants.ANDROID_NS_NAME, false);
                            // automatically provide the prefix.
                            attributeName = toolsPrefix + XmlUtils.NS_SEPARATOR + attributeName;
                        }
                        NodeName nodeName = XmlNode.fromXmlName(attributeName);
                        attributeOperationTypeBuilder.put(nodeName, attributeOperationType);
                    }
                }
            }
        }
        return new ElementOperationsAndMergeRuleMarkers(
                lastNodeOperationType,
                attributeOperationTypeBuilder.build(),
                selector,
                overrideUsesSdkLibrarySelectors);
    }

    /**
     * Build a list of {@link XmlAttribute} from attributes on DOM Element.
     *
     * @param namedNodeMap attributes on DOM Element
     * @return list of {@link XmlAttribute}s
     */
    @NonNull
    private ImmutableList<XmlAttribute> buildXmlAttributes(NamedNodeMap namedNodeMap) {
        ImmutableList.Builder<XmlAttribute> attributesListBuilder = ImmutableList.builder();

        for (int i = 0; i < namedNodeMap.getLength(); i++) {
            Attr attribute = (Attr) namedNodeMap.item(i);
            attributesListBuilder.add(XmlAttribute.createXmlAttribute(this, attribute));
        }
        return attributesListBuilder.build();
    }

    /** Returns the owning {@link XmlDocument} */
    @NonNull
    public XmlDocument getDocument() {
        return mDocument;
    }

    /** Returns the list of attributes for this xml element. */
    @NonNull
    public List<XmlAttribute> getAttributes() {
        return mAttributes;
    }

    /**
     * Removes given child from DOM and from this list of {@code mMergeableChildren}
     *
     * @param oldChild Child element to be deleted.
     * @return Child element we deleted, null if the child could not be deleted.
     */
    @Nullable
    public Node removeChild(Node oldChild) {
        Node nodeBeingDeleted = getXml().removeChild(oldChild);
        if (oldChild instanceof Element) {
            mMergeableChildren = initMergeableChildren();
        }
        return nodeBeingDeleted;
    }

    @Nullable
    public Node removeChild(XmlElement oldChild) {
        return removeChild(oldChild.getXml());
    }

    public int getAttributeCount() {
        return getXml().getAttributes().getLength();
    }

    @NonNull
    public ImmutableList<String> getAttributeNames(Predicate<Node> nodePredicate) {
        NamedNodeMap attributes = getXml().getAttributes();
        var extraAttributeNames = new ImmutableList.Builder<String>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            if (nodePredicate.test(item)) {
                extraAttributeNames.add(item.getNodeName());
            }
        }
        return extraAttributeNames.build();
    }

    /**
     * Inserts the node newChild before the existing child node refChild.
     *
     * @param newChild The node to insert.
     * @param refChild The node before which the new node must be inserted.
     * @return The inserted node.
     */
    @Nullable
    public Node insertBefore(Node newChild, Node refChild) {
        Node nodeBeingInserted = getXml().insertBefore(newChild, refChild);
        if (nodeBeingInserted instanceof Element) {
            mMergeableChildren = initMergeableChildren();
        }
        return nodeBeingInserted;
    }

    @Nullable
    public Node insertBefore(XmlElement newChild, Node refChild) {
        return insertBefore(newChild.getXml(), refChild);
    }

    @Nullable
    public Node appendChild(XmlElement newChild) {
        return appendChild(newChild.getXml());
    }

    @Nullable
    public Node appendChild(Node newChild) {
        Node nodeBeingAppended = getXml().appendChild(newChild);
        if (nodeBeingAppended instanceof Element) {
            mMergeableChildren = initMergeableChildren();
        }
        return nodeBeingAppended;
    }

    /**
     * Add a new Child Element with no Attributes.
     *
     * @param childTagName tag name of the child added to the element
     * @throws RuntimeException if we fail to add child element
     * @return child {@link XmlElement} object.
     */
    @NonNull
    public XmlElement addChildElement(String childTagName) {
        var document = getDocument();
        var childElement = document.getXml().createElement(childTagName);
        Node appendedChild = appendChild(childElement);
        if (!(appendedChild instanceof Element)) {
            throw new RuntimeException(
                    String.format(
                            "Unable to add %s element to %s element.", childTagName, getTagName()));
        }
        return findMergeableChild((Element) appendedChild).orElseThrow();
    }

    /** Add a new Element with a single specified Attribute to parentElement */
    public void addChildElementWithSingleAttribute(
            String childTagName, String nsUri, String attrName, String attrValue) {
        var childXmlElement = addChildElement(childTagName);
        var prefix = XmlUtils.lookupNamespacePrefix(childXmlElement.getXml(), nsUri, true);
        childXmlElement.setAttributeNS(nsUri, prefix + XmlUtils.NS_SEPARATOR + attrName, attrValue);
    }

    @NonNull
    public Optional<XmlElement> findMergeableChild(Element childElement) {
        return mMergeableChildren.stream()
                .filter(xmlElement -> xmlElement.getXml().isSameNode(childElement))
                .findAny();
    }

    @NonNull
    public XmlElement createOrGetElementOfType(
            XmlDocument document,
            ManifestModel.NodeTypes nodeType,
            String namespaceUri,
            Consumer<XmlElement> postCreationAction) {
        var elementName = document.getModel().toXmlName(nodeType);
        var nodes = getXml().getElementsByTagName(elementName);
        if (nodes.getLength() == 0) {
            nodes = getXml().getElementsByTagNameNS(namespaceUri, elementName);
        }
        if (nodes.getLength() == 0) {
            var node = getXml().getOwnerDocument().createElement(elementName);
            appendChild(node);
            var xmlElement = new XmlElement(node, document);
            postCreationAction.accept(xmlElement);
            return xmlElement;
        } else {
            return new XmlElement((Element) nodes.item(0), document);
        }
    }

    public void setAttribute(String name, String value) {
        getXml().setAttribute(name, value);
        Attr attribute = getXml().getAttributeNode(name);
        checkAndUpdateXmlAttributesAndMergeRuleMarkers(attribute.getNamespaceURI());
    }

    public void setAttribute(XmlAttribute attribute, String value) {
        attribute.getXml().setValue(value);
        if (SdkConstants.TOOLS_URI.equals(attribute.getXml().getNamespaceURI())) {
            mSelectorsAndMergeRuleMarkers = extractOperationAndSelectors(getXml().getAttributes());
        }
    }

    public void addAttribute(XmlAttribute attribute, String value) {
        attribute.getName().addToNode(getXml(), value);
        checkAndUpdateXmlAttributesAndMergeRuleMarkers(attribute.getXml().getNamespaceURI());
    }

    public void addAttribute(String nsUri, String attrName, String attrValue) {
        var prefix = XmlUtils.lookupNamespacePrefix(getXml(), nsUri, true);
        setAttributeNS(nsUri, prefix + XmlUtils.NS_SEPARATOR + attrName, attrValue);
    }

    private void checkAndUpdateXmlAttributesAndMergeRuleMarkers(String affectedAttributeNamespace) {
        NamedNodeMap namedNodeMap = getXml().getAttributes();
        if (SdkConstants.TOOLS_URI.equals(affectedAttributeNamespace)) {
            mSelectorsAndMergeRuleMarkers = extractOperationAndSelectors(namedNodeMap);
        }
        mAttributes = buildXmlAttributes(namedNodeMap);
    }

    public void removeAttributeNS(String namespaceURI, String localName) {
        getXml().removeAttributeNS(namespaceURI, localName);
        checkAndUpdateXmlAttributesAndMergeRuleMarkers(namespaceURI);
    }

    public void removeAttribute(String name) {
        String attributeNamespaceUri =
                Optional.ofNullable(getXml().getAttributeNode(name))
                        .map(Attr::getNamespaceURI)
                        .orElse(null);
        getXml().removeAttribute(name);
        checkAndUpdateXmlAttributesAndMergeRuleMarkers(attributeNamespaceUri);
    }

    public void setAttributeNS(String namespaceURI, String qualifiedName, String value) {
        getXml().setAttributeNS(namespaceURI, qualifiedName, value);
        checkAndUpdateXmlAttributesAndMergeRuleMarkers(namespaceURI);
    }

    /**
     * Returns the {@link XmlAttribute} for an attribute present on this xml element, or {@link
     * Optional#empty()} if not present.
     *
     * @param attributeName the attribute name.
     */
    public Optional<XmlAttribute> getAttribute(NodeName attributeName) {
        for (XmlAttribute xmlAttribute : mAttributes) {
            if (xmlAttribute.getName().equals(attributeName)) {
                return Optional.of(xmlAttribute);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the node operation type as optionally specified by the user. If the user did not
     * explicitly specify how conflicting elements should be handled, a {@link
     * NodeOperationType#MERGE} will be returned.
     */
    @NonNull
    public NodeOperationType getOperationType() {
        return mSelectorsAndMergeRuleMarkers.getNodeOperationType() != null
                ? mSelectorsAndMergeRuleMarkers.getNodeOperationType()
                : NodeOperationType.MERGE;
    }

    /**
     * Get the attribute operation type as optionally specified by the user. If the user did not
     * explicitly specify how conflicting attributes should be handled, a
     * {@link AttributeOperationType#STRICT} will be returned.
     */
    @NonNull
    public AttributeOperationType getAttributeOperationType(NodeName attributeName) {
        return mSelectorsAndMergeRuleMarkers
                .getAttributesOperationTypes()
                .getOrDefault(attributeName, AttributeOperationType.STRICT);
    }

    @NonNull
    public Collection<Map.Entry<NodeName, AttributeOperationType>> getAttributeOperations() {
        return mSelectorsAndMergeRuleMarkers.getAttributesOperationTypes().entrySet();
    }

    @NonNull
    public List<Selector> getOverrideUsesSdkLibrarySelectors() {
        return mSelectorsAndMergeRuleMarkers.getOverrideUsesSdkLibrarySelectors();
    }


    @NonNull
    @Override
    public SourcePosition getPosition() {
        return XmlDocument.getNodePosition(this);
    }

    @NonNull
    @Override
    public SourceFile getSourceFile() {
        return mDocument.getSourceFile();
    }


    /**
     * Merge this xml element with a lower priority node.
     *
     * For now, attributes will be merged. If present on both xml elements, a warning will be
     * issued and the attribute merge will be rejected.
     *
     * @param lowerPriorityNode lower priority Xml element to merge with.
     * @param mergingReport the merging report to log errors and actions.
     */
    public void mergeWithLowerPriorityNode(
            @NonNull XmlElement lowerPriorityNode,
            @NonNull MergingReport.Builder mergingReport) {

        if (mSelectorsAndMergeRuleMarkers.getSelector() != null
                && !mSelectorsAndMergeRuleMarkers
                        .getSelector()
                        .isResolvable(getDocument().getSelectors())) {
            mergingReport.addMessage(
                    getSourceFilePosition(),
                    MergingReport.Record.Severity.ERROR,
                    String.format(
                            "'tools:selector=\"%1$s\"' is not a valid library identifier, "
                                    + "valid identifiers are : %2$s",
                            mSelectorsAndMergeRuleMarkers.getSelector().toString(),
                            Joiner.on(',').join(mDocument.getSelectors().getKeys())));
            return;

        }
        mergingReport.getLogger().verbose("Merging " + getId()
                + " with lower " + lowerPriorityNode.printPosition());

        // workaround for 0.12 release and overlay treatment of manifest entries. This will
        // need to be expressed in the model instead.
        MergeType mergeType = getType().getMergeType();
        // if element we are merging in is not a library (an overlay or an application),  we should
        // always merge the <manifest> attributes otherwise, we do not merge the libraries
        // <manifest> attributes.
        if (isA(ManifestModel.NodeTypes.MANIFEST)
                && lowerPriorityNode.getDocument().getFileType() != XmlDocument.Type.LIBRARY) {
            mergeType = MergeType.MERGE;
        }

        // record the fact the lower priority element is merged into this one.
        mergingReport
                .getActionRecorder()
                .recordNodeAction(lowerPriorityNode, Actions.ActionType.MERGED);

        if (mergeType != MergeType.MERGE_CHILDREN_ONLY) {
            // make a copy of all the attributes metadata, it will eliminate elements from this
            // list as it finds them explicitly defined in the lower priority node.
            // At the end of the explicit attributes processing, the remaining elements of this
            // list will need to be checked for default value that may clash with a locally
            // defined attribute.
            List<AttributeModel> attributeModels =
                    new ArrayList<>(lowerPriorityNode.getType().getAttributeModels());

            // merge explicit attributes from lower priority node.
            for (XmlAttribute lowerPriorityAttribute : lowerPriorityNode.getAttributes()) {
                lowerPriorityAttribute.mergeInHigherPriorityElement(this, mergingReport);
                if (lowerPriorityAttribute.getModel() != null) {
                    attributeModels.remove(lowerPriorityAttribute.getModel());
                }
            }
            // merge implicit default values from lower priority node when we have an explicit
            // attribute declared on this node.
            for (AttributeModel attributeModel : attributeModels) {
                if (attributeModel.getDefaultValue() != null) {
                    Optional<XmlAttribute> myAttribute = getAttribute(attributeModel.getName());
                    myAttribute.ifPresent(
                            xmlAttribute ->
                                    xmlAttribute.mergeWithLowerPriorityDefaultValue(
                                            mergingReport, lowerPriorityNode));
                }
            }
        }
        // are we supposed to merge children ?
        if (mSelectorsAndMergeRuleMarkers.getNodeOperationType()
                != NodeOperationType.MERGE_ONLY_ATTRIBUTES) {
            mergeChildren(lowerPriorityNode, mergingReport);
        } else {
            // record rejection of the lower priority node's children .
            for (XmlElement lowerPriorityChild : lowerPriorityNode.getMergeableElements()) {
                mergingReport.getActionRecorder().recordNodeAction(this,
                        Actions.ActionType.REJECTED,
                        lowerPriorityChild);
            }
        }
    }

    @NonNull
    public ImmutableList<XmlElement> getMergeableElements() {
        return mMergeableChildren;
    }

    /**
     * Returns a child of a particular type and a particular key.
     *
     * @param type the requested child type.
     * @param keyValue the requested child key.
     * @return the child of {@link Optional#empty()} ()} if no child of this type and key exist.
     */
    @NonNull
    public Optional<XmlElement> getNodeByTypeAndKey(
            ManifestModel.NodeTypes type, @Nullable String keyValue) {

        for (XmlElement xmlElement : mMergeableChildren) {
            if (xmlElement.isA(type) &&
                    (keyValue == null || keyValue.equals(xmlElement.getKey()))) {
                return Optional.of(xmlElement);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns all immediate children of this node for a particular type, irrespective of their
     * key.
     * @param type the type of children element requested.
     * @return the list (potentially empty) of children.
     */
    @NonNull
    public ImmutableList<XmlElement> getAllNodesByType(ManifestModel.NodeTypes type) {
        ImmutableList.Builder<XmlElement> listBuilder = ImmutableList.builder();
        for (XmlElement mergeableChild : mMergeableChildren) {
            if (mergeableChild.isA(type)) {
                listBuilder.add(mergeableChild);
            }
        }
        return listBuilder.build();
    }

    // merge this higher priority node with a lower priority node.
    public void mergeChildren(@NonNull XmlElement lowerPriorityNode,
            @NonNull MergingReport.Builder mergingReport) {

        // find all the child nodes that matches with the lower priority node's children
        Map<XmlElement, Optional<XmlElement>> matchingChildNodes =
                lowerPriorityNode.getMergeableElements().stream()
                        .collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        node ->
                                                getNodeByTypeAndKey(
                                                        node.getType(), node.getKey())));
        // read all lower priority mergeable nodes.
        // if the same node is not defined in this document merge it in.
        // if the same is defined, so far, give an error message.
        for (XmlElement lowerPriorityChild : lowerPriorityNode.getMergeableElements()) {

            if (shouldIgnore(lowerPriorityChild, mergingReport)) {
                continue;
            }
            mergeChild(
                    lowerPriorityChild, mergingReport, matchingChildNodes.get(lowerPriorityChild));
        }
    }

    /**
     * Returns true if this element supports having a tools:selector decoration, false otherwise.
     */
    public boolean supportsSelector() {
        return getOperationType().isSelectable();
    }

    /**
     * Check that given namespace is declared on the element and create one if not.
     *
     * @see XmlUtils#lookupNamespacePrefix(Node, String, boolean)
     * @param nsUri The namespace URI of which the prefix is to be found
     * @param defaultPrefix The default prefix (root) to use if the namespace is not found. If null,
     *     do not create a new namespace if this URI is not defined for the document.
     */
    public void enforceNamespaceDeclaration(String nsUri, String defaultPrefix) {
        XmlUtils.lookupNamespacePrefix(getXml(), nsUri, defaultPrefix, true);
    }

    /**
     * Merge a child of a lower priority node into the given child of a higher priority node.
     *
     * @param lowerPriorityChild Child of a low priority xml element
     * @param mergingReport Merge report to record changes
     * @param thisChildOptional Child of this xml element into which the {@code lowPriorityChild} is
     *     merged.
     */
    private void mergeChild(
            @NonNull XmlElement lowerPriorityChild,
            @NonNull MergingReport.Builder mergingReport,
            Optional<XmlElement> thisChildOptional) {

        ILogger logger = mergingReport.getLogger();

        // If this a custom element, we just blindly merge it in.
        if (lowerPriorityChild.getType() == ManifestModel.NodeTypes.CUSTOM) {
            handleCustomElement(lowerPriorityChild, mergingReport);
            return;
        }

        // only in the lower priority document ?
        if (thisChildOptional.isEmpty()) {
            addElement(lowerPriorityChild, mergingReport);
            return;
        }
        // it's defined in both files.
        logger.verbose(lowerPriorityChild.getId() + " defined in both files...");

        XmlElement thisChild = thisChildOptional.get();
        switch (thisChild.getType().getMergeType()) {
            case CONFLICT:
                mergingReport.addMessage(
                        this,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Node %1$s cannot be present in more than one input file and it's "
                                        + "present at %2$s and %3$s",
                                thisChild.getType(),
                                thisChild.printPosition(),
                                lowerPriorityChild.printPosition()));
                break;
            case ALWAYS:

                // no merging, we consume the lower priority node unmodified.
                // if the two elements are equal, just skip it.

                // but check first that we are not supposed to replace or remove it.
                @NonNull
                NodeOperationType operationType =
                        calculateNodeOperationType(thisChild, lowerPriorityChild);
                if (operationType == NodeOperationType.REMOVE
                        || operationType == NodeOperationType.REPLACE) {
                    mergingReport
                            .getActionRecorder()
                            .recordNodeAction(
                                    thisChild, Actions.ActionType.REJECTED, lowerPriorityChild);
                    break;
                }

                if (thisChild.getType().areMultipleDeclarationAllowed()) {
                    mergeChildrenWithMultipleDeclarations(lowerPriorityChild, mergingReport);
                } else {
                    if (!thisChild.isEquals(lowerPriorityChild)) {
                        addElement(lowerPriorityChild, mergingReport);
                    }
                }
                break;
            default:
                // 2 nodes exist, some merging need to happen
                handleTwoElementsExistence(thisChild, lowerPriorityChild, mergingReport);
                break;
        }
    }

    /**
     * Handles presence of custom elements (elements not part of the android or tools
     * namespaces). Such elements are merged unchanged into the resulting document, and
     * optionally, the namespace definition is added to the merged document root element.
     * @param customElement the custom element present in the lower priority document.
     * @param mergingReport the merging report to log errors and actions.
     */
    private void handleCustomElement(@NonNull XmlElement customElement,
            @NonNull MergingReport.Builder mergingReport) {
        addElement(customElement, mergingReport);

        // add the custom namespace to the document generation.
        String nodeName = customElement.getXml().getNodeName();
        if (!nodeName.contains(":")) {
            return;
        }
        @NonNull String prefix = nodeName.substring(0, nodeName.indexOf(':'));
        String namespace = customElement.getDocument().getRootNode()
                .getXml().getAttribute(SdkConstants.XMLNS_PREFIX + prefix);

        if (namespace != null) {
            getDocument()
                    .getRootNode()
                    .setAttributeNS(
                            SdkConstants.XMLNS_URI, SdkConstants.XMLNS_PREFIX + prefix, namespace);
        }
    }

    /**
     * Merges two children when this children's type allow multiple elements declaration with the
     * same key value. In that case, we only merge the lower priority child if there is not already
     * an element with the same key value that is equal to the lower priority child. Two children
     * are equals if they have the same attributes and children declared irrespective of the
     * declaration order.
     *
     * @param lowerPriorityChild the lower priority element's child.
     * @param mergingReport the merging report to log errors and actions.
     */
    private void mergeChildrenWithMultipleDeclarations(
            @NonNull XmlElement lowerPriorityChild,
            @NonNull MergingReport.Builder mergingReport) {

        Preconditions.checkArgument(lowerPriorityChild.getType().areMultipleDeclarationAllowed());
        if (lowerPriorityChild.getType().areMultipleDeclarationAllowed()) {
            for (XmlElement sameTypeChild : getAllNodesByType(lowerPriorityChild.getType())) {
                if (sameTypeChild.getId().equals(lowerPriorityChild.getId()) &&
                        sameTypeChild.isEquals(lowerPriorityChild)) {
                    return;
                }
            }
        }
        // if we end up here, we never found a child of this element with the same key and strictly
        // equals to the lowerPriorityChild so we should merge it in.
        addElement(lowerPriorityChild, mergingReport);
    }

    /**
     * Determine if we should completely ignore a child from any merging activity. There are 2
     * situations where we should ignore a lower priority child :
     *
     * <p>
     *
     * <ul>
     *   <li>The associate {@link ManifestModel.NodeTypes} is annotated with {@link
     *       MergeType#IGNORE}
     *   <li>This element has a child of the same type with no key that has a '
     *       tools:node="removeAll' attribute.
     * </ul>
     *
     * @param lowerPriorityChild the lower priority child we should determine eligibility for
     *     merging.
     * @return true if the element should be ignored, false otherwise.
     */
    private boolean shouldIgnore(
            @NonNull XmlElement lowerPriorityChild, @NonNull MergingReport.Builder mergingReport) {

        if (lowerPriorityChild.getType().getMergeType() == MergeType.IGNORE) {
            return true;
        }

        // See if the lowerPriorityChild's XmlDocument.Type is mergeable. For example, <dist:module>
        // elements are only mergeable from OVERLAY or MAIN types, not LIBRARY types.
        if (!lowerPriorityChild.getType().canMergeWithLowerPriority(lowerPriorityChild)) {
            return true;
        }

        // do we have an element of the same type of that child with no key ?
        Optional<XmlElement> thisChildElementOptional =
                getNodeByTypeAndKey(lowerPriorityChild.getType(), null /* keyValue */);
        if (!thisChildElementOptional.isPresent()) {
            return false;
        }
        XmlElement thisChild = thisChildElementOptional.get();

        // are we supposed to delete all occurrences and if yes, is there a selector defined to
        // filter which elements should be deleted.
        boolean shouldDelete =
                thisChild.mSelectorsAndMergeRuleMarkers.getNodeOperationType()
                                == NodeOperationType.REMOVE_ALL
                        && (thisChild.mSelectorsAndMergeRuleMarkers.getSelector() == null
                                || thisChild
                                        .mSelectorsAndMergeRuleMarkers
                                        .getSelector()
                                        .appliesTo(lowerPriorityChild));
        // if we should discard this child element, record the action.
        if (shouldDelete) {
            mergingReport
                    .getActionRecorder()
                    .recordNodeAction(thisChild, Actions.ActionType.REJECTED, lowerPriorityChild);
        }
        return shouldDelete;
    }

    /**
     * Handle 2 elements (of same identity) merging.
     * higher priority one has a tools:node="remove", remove the low priority one
     * higher priority one has a tools:node="replace", replace the low priority one
     * higher priority one has a tools:node="strict", flag the error if not equals.
     * default or tools:node="merge", merge the two elements.
     * @param higherPriority the higher priority node.
     * @param lowerPriority the lower priority element.
     * @param mergingReport the merging report to log errors and actions.
     */
    private void handleTwoElementsExistence(
            @NonNull XmlElement higherPriority,
            @NonNull XmlElement lowerPriority,
            @NonNull MergingReport.Builder mergingReport) {

        @NonNull NodeOperationType operationType = calculateNodeOperationType(higherPriority, lowerPriority);
        // 2 nodes exist, 3 possibilities :
        //  higher priority one has a tools:node="remove", remove the low priority one
        //  higher priority one has a tools:node="replace", replace the low priority one
        //  higher priority one has a tools:node="strict", flag the error if not equals.
        switch (operationType) {
            case MERGE:
            case MERGE_ONLY_ATTRIBUTES:
                // record the action
                mergingReport.getActionRecorder().recordNodeAction(higherPriority,
                        Actions.ActionType.MERGED, lowerPriority);
                // and perform the merge
                higherPriority.mergeWithLowerPriorityNode(lowerPriority, mergingReport);
                break;
            case REMOVE:
            case REPLACE:
                // so far remove and replace and similar, the post validation will take
                // care of removing this node in the case of REMOVE.

                // just don't import the lower priority node and record the action.
                mergingReport.getActionRecorder().recordNodeAction(higherPriority,
                        Actions.ActionType.REJECTED, lowerPriority);
                break;
            case STRICT:
                Optional<String> compareMessage = higherPriority.compareTo(lowerPriority);
                // flag error.
                compareMessage.ifPresent(
                        s ->
                                mergingReport.addMessage(
                                        this,
                                        MergingReport.Record.Severity.ERROR,
                                        String.format(
                                                "Node %1$s at %2$s is tagged with tools:node=\"strict\", yet "
                                                        + "%3$s at %4$s is different : %5$s",
                                                higherPriority.getId(),
                                                higherPriority.printPosition(),
                                                lowerPriority.getId(),
                                                lowerPriority.printPosition(),
                                                s)));
                break;
            default:
                mergingReport.getLogger().error(null /* throwable */,
                        "Unhandled node operation type %s", higherPriority.getOperationType());
                break;
        }
    }

    /**
     * Calculate the effective node operation type for a higher priority node when a lower priority
     * node is queried for merge.
     *
     * @param higherPriority the higher priority node which may have a {@link NodeOperationType}
     *     declaration and may also have a {@link Selector} declaration.
     * @param lowerPriority the lower priority node that is elected for merging with the higher
     *     priority node.
     * @return the effective {@link NodeOperationType} that should be used to affect higher and
     *     lower priority nodes merging.
     */

    /**
     * higherPriority will always dominate lowerPriority if they differ, so returning the
     * higherPriority node operation is sufficient except when
     * ((highPriority.mMergeRuleMarkers.getNodeOperationType() == null || MERGE) &&
     * lowerPriority.mNodeOperation == REMOVE || REMOVE_ALL)). Because of the actual merging merges
     * from highest priority manifest to the lowest priority manifest, the node operation in the
     * lowerPriority is needed in the next round, therefore override the node operation in
     * higherPriority with the one in the lowerPriority and record the original node operation in
     * the higherPriority (it will be used later in Post Validator)
     *
     * <p>when the node operation in higherPriority is null or MERGE and lowerPriority's node
     * operation is REMOVE or REMOVE_ALL, the returned node operation type can not be MERGE
     * otherwise the lowerPriority itself will be merged instead of being removed, change the
     * operation node to REPLACE will make sure that the lowerPriority itself won't be merged
     */
    @NonNull
    private static NodeOperationType calculateNodeOperationType(
            @NonNull XmlElement higherPriority, @NonNull XmlElement lowerPriority) {

        @NonNull NodeOperationType operationType = higherPriority.getOperationType();
        if (lowerPriority.mSelectorsAndMergeRuleMarkers.getNodeOperationType() != null) {
            // two special cases where operationType can't equal to
            // higherPriority.getOperationType()
            if (higherPriority.getOperationType() == NodeOperationType.MERGE
                    && (lowerPriority.mSelectorsAndMergeRuleMarkers.getNodeOperationType()
                                    == NodeOperationType.REMOVE
                            || lowerPriority.mSelectorsAndMergeRuleMarkers.getNodeOperationType()
                                    == NodeOperationType.REMOVE_ALL)) {
                operationType = NodeOperationType.REPLACE;
            }
            // record the original node operation in the higherPriority
            if (higherPriority.getDocument().originalNodeOperation.get(higherPriority.getXml())
                    == null) {
                higherPriority
                        .getDocument()
                        .originalNodeOperation
                        .put(higherPriority.getXml(), higherPriority.getOperationType());
            }
            // overwrite the node operation in higherPriority with the one in the lowerPriority
            higherPriority.setAttributeNS(
                    SdkConstants.TOOLS_URI,
                    "tools:node",
                    lowerPriority
                            .mSelectorsAndMergeRuleMarkers
                            .getNodeOperationType()
                            .toString()
                            .toLowerCase(Locale.US));
        }
        // if the operation's selector exists and the lower priority node is not selected,
        // we revert to default operation type which is merge.
        if (higherPriority.supportsSelector()
                && higherPriority.mSelectorsAndMergeRuleMarkers.getSelector() != null
                && !higherPriority
                        .mSelectorsAndMergeRuleMarkers
                        .getSelector()
                        .appliesTo(lowerPriority)) {
            operationType = NodeOperationType.MERGE;
        }
        return operationType;
    }

    /**
     * Add an element and its leading comments as the last sub-element of the current element.
     * @param elementToBeAdded xml element to be added to the current element.
     * @param mergingReport the merging report to log errors and actions.
     */
    private void addElement(
            @NonNull XmlElement elementToBeAdded, @NonNull MergingReport.Builder mergingReport) {

        List<Node> comments = getLeadingComments(elementToBeAdded.getXml());
        // record all the actions before the node is moved from the library document to the main
        // merged document.
        mergingReport.getActionRecorder().recordAddedNodeAction(elementToBeAdded, false);

        // only in the new file, just import it.
        Node node = getXml().getOwnerDocument().importNode(elementToBeAdded.getXml(), true);
        appendChild(node);

        // also adopt the child's comments if any.
        for (Node comment : comments) {
            Node newComment = getXml().getOwnerDocument().adoptNode(comment);
            insertBefore(newComment, node);
        }

        mergingReport.getLogger().verbose("Adopted " + node);
    }

    public boolean isEquals(XmlElement otherNode) {
        return !compareTo(otherNode).isPresent();
    }

    /**
     * Returns a potentially null (if not present) selector decoration on this element.
     */
    @Nullable
    public Selector getSelector() {
        return mSelectorsAndMergeRuleMarkers.getSelector();
    }

    /**
     * Compares this element with another {@link XmlElement} ignoring all attributes belonging to
     * the {@link SdkConstants#TOOLS_URI} namespace.
     *
     * @param other the other element to compare against.
     * @return a {@link String} describing the differences between the two XML elements or {@link
     *     Optional#empty()} ()} if they are equals.
     */
    @NonNull
    public Optional<String> compareTo(Object other) {

        if (!(other instanceof XmlElement)) {
            return Optional.of("Wrong type");
        }
        XmlElement otherNode = (XmlElement) other;

        // compare element names
        if (getXml().getNamespaceURI() != null) {
            if (!getXml().getLocalName().equals(otherNode.getXml().getLocalName())) {
                return Optional.of(
                        String.format("Element names do not match: %1$s versus %2$s",
                                getXml().getLocalName(),
                                otherNode.getXml().getLocalName()));
            }
            // compare element ns
            String thisNS = getXml().getNamespaceURI();
            String otherNS = otherNode.getXml().getNamespaceURI();
            if ((thisNS == null && otherNS != null)
                    || (thisNS != null && !thisNS.equals(otherNS))) {
                return Optional.of(
                        String.format("Element namespaces names do not match: %1$s versus %2$s",
                                thisNS, otherNS));
            }
        } else {
            if (!getXml().getNodeName().equals(otherNode.getXml().getNodeName())) {
                return Optional.of(String.format("Element names do not match: %1$s versus %2$s",
                        getXml().getNodeName(),
                        otherNode.getXml().getNodeName()));
            }
        }

        // compare attributes, we do it twice to identify added/missing elements in both lists.
        Optional<String> message = checkAttributes(this, otherNode);
        if (message.isPresent()) {
            return message;
        }
        message = checkAttributes(otherNode, this);
        if (message.isPresent()) {
            return message;
        }

        // compare children
        @NonNull List<Node> expectedChildren = filterUninterestingNodes(getXml().getChildNodes());
        @NonNull List<Node> actualChildren = filterUninterestingNodes(otherNode.getXml().getChildNodes());
        int actualChildrenSize = actualChildren.size();
        int expectedChildrenSize = expectedChildren.size();
        if (expectedChildrenSize != actualChildrenSize) {

            if (expectedChildrenSize > actualChildren.size()) {
                // missing some.
                @NonNull List<String> missingChildrenNames =
                        Lists.transform(expectedChildren, NODE_TO_NAME);
                Lists.transform(actualChildren, NODE_TO_NAME).forEach(missingChildrenNames::remove);
                return Optional.of(
                        String.format(
                                "%1$s: Number of children do not match up: "
                                        + "expected %2$d versus %3$d at %4$s, missing %5$s",
                                getId(),
                                expectedChildrenSize,
                                actualChildren.size(),
                                otherNode.printPosition(),
                                Joiner.on(",").join(missingChildrenNames)));
            } else {
                // extra ones.
                @NonNull
                List<String> extraChildrenNames = Lists.transform(actualChildren, NODE_TO_NAME);
                Lists.transform(expectedChildren, NODE_TO_NAME).forEach(extraChildrenNames::remove);
                return Optional.of(
                        String.format(
                                "%1$s: Number of children do not match up: "
                                        + "expected %2$d versus %3$d at %4$s, extra elements found : %5$s",
                                getId(),
                                expectedChildrenSize,
                                actualChildrenSize,
                                otherNode.printPosition(),
                                Joiner.on(",").join(extraChildrenNames)));
            }
        }
        for (Node expectedChild : expectedChildren) {
            if (expectedChild.getNodeType() == Node.ELEMENT_NODE) {
                @NonNull XmlElement expectedChildNode = new XmlElement((Element) expectedChild, mDocument);
                message = findAndCompareNode(otherNode, actualChildren, expectedChildNode);
                if (message.isPresent()) {
                    return message;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> findAndCompareNode(
            @NonNull XmlElement otherElement,
            @NonNull List<Node> otherElementChildren,
            @NonNull XmlElement childNode) {

        Optional<String> message = Optional.empty();
        for (Node potentialNode : otherElementChildren) {
            if (potentialNode.getNodeType() == Node.ELEMENT_NODE) {
                @NonNull XmlElement otherChildNode = new XmlElement((Element) potentialNode, mDocument);
                if (childNode.getType() == otherChildNode.getType()) {
                    // check if this element uses a key.
                    if (childNode.getType().areMultipleDeclarationAllowed()
                            || childNode
                                    .getType()
                                    .getNodeKeyResolver()
                                    .getKeyAttributesNames()
                                    .isEmpty()) {
                        // no key, or multiple nodes with same key allowed...
                        // try all the other elements, if we find one equal, we are done.
                        message = childNode.compareTo(otherChildNode);
                        if (!message.isPresent()) {
                            return Optional.empty();
                        }
                    } else {
                        // key...
                        if (childNode.getKey() == null) {
                            // other key MUST also be null.
                            if (otherChildNode.getKey() == null) {
                                return childNode.compareTo(otherChildNode);
                            }
                        } else {
                            if (childNode.getKey().equals(otherChildNode.getKey())) {
                                return childNode.compareTo(otherChildNode);
                            }
                        }
                    }
                }
            }
        }
        return message.isPresent()
                ? message
                : Optional.of(String.format("Child %1$s not found in document %2$s",
                        childNode.getId(),
                        otherElement.printPosition()));
    }

    @NonNull
    private static List<Node> filterUninterestingNodes(@NonNull NodeList nodeList) {
        List<Node> interestingNodes = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                Text t = (Text) node;
                if (!t.getData().trim().isEmpty()) {
                    interestingNodes.add(node);
                }
            } else if (node.getNodeType() != Node.COMMENT_NODE) {
                interestingNodes.add(node);
            }

        }
        return interestingNodes;
    }

    private static Optional<String> checkAttributes(
            @NonNull XmlElement expected,
            @NonNull XmlElement actual) {

        for (XmlAttribute expectedAttr : expected.getAttributes()) {
            NodeName attributeName = expectedAttr.getName();
            if (attributeName.isInNamespace(SdkConstants.TOOLS_URI)) {
                continue;
            }
            Optional<XmlAttribute> actualAttr = actual.getAttribute(attributeName);
            if (actualAttr.isPresent()) {
                if (!expectedAttr.getValue().equals(actualAttr.get().getValue())) {
                    return Optional.of(
                            String.format("Attribute %1$s do not match: %2$s versus %3$s at %4$s",
                                    expectedAttr.getId(),
                                    expectedAttr.getValue(),
                                    actualAttr.get().getValue(),
                                    actual.printPosition()));
                }
            } else {
                return Optional.of(String.format("Attribute %1$s not found at %2$s",
                        expectedAttr.getId(), actual.printPosition()));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("SpellCheckingInspection")
    private ImmutableList<XmlElement> initMergeableChildren() {
        ImmutableList.Builder<XmlElement> mergeableNodes = new ImmutableList.Builder<>();
        NodeList nodeList = getXml().getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                Optional<XmlElement> maybeExistingNode =
                        mMergeableChildren.stream()
                                .filter(existingNode -> existingNode.getXml().isSameNode(node))
                                .findAny();
                if (maybeExistingNode.isEmpty()) {
                    XmlElement xmlElement = new XmlElement((Element) node, mDocument);
                    mergeableNodes.add(xmlElement);
                } else {
                    mergeableNodes.add(maybeExistingNode.get());
                }
            }
        }
        return mergeableNodes.build();
    }

    /**
     * Returns all leading comments in the source xml before the node to be adopted.
     * @param nodeToBeAdopted node that will be added as a child to this node.
     */
    static List<Node> getLeadingComments(@NonNull Node nodeToBeAdopted) {
        @NonNull ImmutableList.Builder<Node> nodesToAdopt = new ImmutableList.Builder<>();
        Node previousSibling = nodeToBeAdopted.getPreviousSibling();
        while (previousSibling != null
                && (previousSibling.getNodeType() == Node.COMMENT_NODE
                || previousSibling.getNodeType() == Node.TEXT_NODE)) {
            // we really only care about comments.
            if (previousSibling.getNodeType() == Node.COMMENT_NODE) {
                nodesToAdopt.add(previousSibling);
            }
            previousSibling = previousSibling.getPreviousSibling();
        }
        return nodesToAdopt.build().reverse();
    }

    static class ElementOperationsAndMergeRuleMarkers {

        @Nullable
        public NodeOperationType getNodeOperationType() {
            return mNodeOperationType;
        }

        @NonNull
        public Map<NodeName, AttributeOperationType> getAttributesOperationTypes() {
            return mAttributesOperationTypes;
        }

        @Nullable
        public Selector getSelector() {
            return mSelector;
        }

        @NonNull
        public List<Selector> getOverrideUsesSdkLibrarySelectors() {
            return mOverrideUsesSdkLibrarySelectors;
        }

        @Nullable private final NodeOperationType mNodeOperationType;

        // map of all tools related attributes keyed by target attribute name
        @NonNull private final Map<NodeName, AttributeOperationType> mAttributesOperationTypes;

        // optional selector declared on this xml element.
        @Nullable private final Selector mSelector;

        // optional list of libraries that we should ignore the minSdk version
        @NonNull private final List<Selector> mOverrideUsesSdkLibrarySelectors;

        public ElementOperationsAndMergeRuleMarkers(
                @Nullable NodeOperationType mNodeOperationType,
                @NonNull Map<NodeName, AttributeOperationType> mAttributesOperationTypes,
                @Nullable Selector mSelector,
                @NonNull List<Selector> mOverrideUsesSdkLibrarySelectors) {
            this.mNodeOperationType = mNodeOperationType;
            this.mAttributesOperationTypes = mAttributesOperationTypes;
            this.mSelector = mSelector;
            this.mOverrideUsesSdkLibrarySelectors = mOverrideUsesSdkLibrarySelectors;
        }
    }
}
