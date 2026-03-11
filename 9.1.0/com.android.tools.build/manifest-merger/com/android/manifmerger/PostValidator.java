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

import static com.android.manifmerger.Actions.ActionType;
import static com.android.manifmerger.ManifestMerger2.Invoker.Feature.DISABLE_REPLACE_WARNING;

import com.android.SdkConstants;
import com.android.utils.XmlUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator that runs post merging activities and verifies that all "tools:" instructions
 * triggered an action by the merging tool.
 * <p>
 *
 * This is primarily to catch situations like a user entered a tools:remove="foo" directory on one
 * of its elements and that particular attribute was never removed during the merges possibly
 * indicating an unforeseen change of configuration.
 * <p>
 *
 * Most of the output from this validation should be warnings.
 */
public class PostValidator {

    /**
     * Post validation of the merged document. This will essentially check that all merging
     * instructions were applied at least once.
     *
     * @param xmlDocument merged document to check.
     * @param mergingReport report for errors and warnings.
     */
    public static void validate(
            @NotNull XmlDocument xmlDocument,
            @NotNull MergingReport.Builder mergingReport,
            @NotNull ImmutableList<ManifestMerger2.Invoker.Feature> optionalFeatures) {

        Preconditions.checkNotNull(xmlDocument);
        Preconditions.checkNotNull(mergingReport);
        enforceAndroidNamespaceDeclaration(xmlDocument);
        reOrderElements(xmlDocument.getRootNode());
        validate(
                xmlDocument.getRootNode(),
                mergingReport.getActionRecorder().build(),
                mergingReport,
                optionalFeatures);
        checkOnlyOneUsesSdk(xmlDocument, mergingReport);
    }

    /**
     * Enforces {@link SdkConstants#ANDROID_URI} declaration in the top level element. It is
     * possible that the original manifest file did not contain any attribute declaration, therefore
     * not requiring a xmlns: declaration. Yet the implicit elements handling may have added
     * attributes requiring the namespace declaration.
     */
    private static void enforceAndroidNamespaceDeclaration(@NotNull XmlDocument xmlDocument) {
        xmlDocument
                .getRootNode()
                .enforceNamespaceDeclaration(
                        SdkConstants.ANDROID_URI, SdkConstants.ANDROID_NS_NAME);
    }

    /**
     * Enforces {@link SdkConstants#TOOLS_URI} declaration in the top level element, if necessary.
     * It is possible that the original manifest file did not contain any attribute declaration,
     * therefore not requiring a xmlns: declaration. Yet the implicit elements handling may have
     * added attributes requiring the namespace declaration.
     */
    protected static void enforceToolsNamespaceDeclaration(@NotNull XmlDocument xmlDocument) {
        final Element rootElement = xmlDocument.getRootNode().getXml();
        if (SdkConstants.TOOLS_PREFIX.equals(
                XmlUtils.lookupNamespacePrefix(rootElement, SdkConstants.TOOLS_URI, null, false))) {
            return;
        }
        // if we are here, we did not find the namespace declaration, so we add it if
        // tools namespace is used anywhere in the xml document
        if (xmlDocument.getRootNode().elementUsesNamespacePrefix(SdkConstants.TOOLS_NS_NAME)) {
            XmlUtils.lookupNamespacePrefix(
                    rootElement, SdkConstants.TOOLS_URI, SdkConstants.TOOLS_NS_NAME, true);
        }
    }


    /**
     * Reorder child elements :
     * <ul>
     *     <li>&lt;activity-alias&gt; elements within &lt;application&gt; are moved after the
     *     &lt;activity&gt; they target.</li>
     *     <li>&lt;application&gt; is moved last in the list of children
     *     of the <manifest> element.</li>
     *     <li>uses-sdk is moved first in the list of children of the &lt;manifest&gt; element</li>
     * </ul>
     * @param xmlElement the root element of the manifest document.
     */
    private static void reOrderElements(@NotNull XmlElement xmlElement) {

        reOrderActivityAlias(xmlElement);
        reOrderApplication(xmlElement);
        reOrderUsesSdk(xmlElement);
    }

    /**
     * Reorder activity-alias elements to after the activity they reference
     *
     * @param xmlElement the root element of the manifest document.
     */
    private static void reOrderActivityAlias(@NotNull XmlElement xmlElement) {

        // look up application element.
        Optional<XmlElement> element = xmlElement
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        if (!element.isPresent()) {
            return;
        }
        XmlElement applicationElement = element.get();

        List<XmlElement> activityAliasElements = applicationElement
                .getAllNodesByType(ManifestModel.NodeTypes.ACTIVITY_ALIAS);
        for (XmlElement activityAlias : activityAliasElements) {
            // get targetActivity attribute
            Optional<XmlAttribute> attribute = activityAlias.getAttribute(
                    XmlNode.fromNSName(SdkConstants.ANDROID_URI, "android", "targetActivity"));
            if (!attribute.isPresent()) {
                continue;
            }
            String targetActivity = attribute.get().getValue();

            // look up target activity element
            element = applicationElement
                    .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY, targetActivity);
            if (!element.isPresent()) {
                continue;
            }
            XmlElement activity = element.get();

            // move the activity-alias to after the activity
            Node nextSibling = activity.getXml().getNextSibling();

            // move the activity-alias's comments if any.
            List<Node> comments = XmlElement.getLeadingComments(activityAlias.getXml());

            if (!comments.isEmpty() && !comments.get(0).equals(nextSibling)) {
                for (Node comment : comments) {
                    applicationElement.removeChild(comment);
                    applicationElement.insertBefore(comment, nextSibling);
                }
            }

            // move the activity-alias element if neither it or its comments immediately follow the
            // target activity.
            if (!activityAlias.getXml().equals(nextSibling)
                    && !(!comments.isEmpty() && comments.get(0).equals(nextSibling))) {
                applicationElement.removeChild(activityAlias);
                applicationElement.insertBefore(activityAlias, nextSibling);
            }
        }
    }

    /**
     * Reorder application element
     *
     * @param xmlElement the root element of the manifest document.
     */
    private static void reOrderApplication(@NotNull XmlElement xmlElement) {

        // look up application element.
        Optional<XmlElement> element = xmlElement
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        if (!element.isPresent()) {
            return;
        }
        XmlElement applicationElement = element.get();

        List<Node> comments = XmlElement.getLeadingComments(applicationElement.getXml());

        // move the application's comments if any.
        for (Node comment : comments) {
            xmlElement.removeChild(comment);
            xmlElement.appendChild(comment);
        }
        // remove the application element and add it back, it will be automatically placed last.
        xmlElement.removeChild(applicationElement);
        xmlElement.appendChild(applicationElement);
    }

    /**
     * Reorder uses-sdk element
     *
     * @param xmlElement the root element of the manifest document.
     */
    private static void reOrderUsesSdk(@NotNull XmlElement xmlElement) {

        // look up uses-sdk element.
        Optional<XmlElement> element = xmlElement
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.USES_SDK, null);
        if (!element.isPresent()) {
            return;
        }

        XmlElement usesSdk = element.get();
        Node firstChild = xmlElement.getXml().getFirstChild();
        // already the first element ?
        if (firstChild == usesSdk.getXml()) {
            return;
        }

        List<Node> comments = XmlElement.getLeadingComments(usesSdk.getXml());

        // move the application's comments if any.
        for (Node comment : comments) {
            xmlElement.removeChild(comment);
            xmlElement.insertBefore(comment, firstChild);
        }
        // remove the application element and add it back, it will be automatically placed last.
        xmlElement.removeChild(usesSdk);
        xmlElement.insertBefore(usesSdk, firstChild);
    }

    /**
     * Validate an xml element and recursively its children elements, ensuring that all merging
     * instructions were applied.
     *
     * @param xmlElement xml element to validate.
     * @param actions the actions recorded during the merging activities.
     * @param mergingReport report for errors and warnings. instructions were applied once or {@link
     *     MergingReport.Result#WARNING} otherwise.
     */
    private static void validate(
            @NotNull XmlElement xmlElement,
            @NotNull Actions actions,
            @NotNull MergingReport.Builder mergingReport,
            @NotNull ImmutableList<ManifestMerger2.Invoker.Feature> optionalFeatures) {

        NodeOperationType operationType = xmlElement.getOperationType();
        boolean ignoreWarning = checkIgnoreWarning(xmlElement);
        switch (operationType) {
            case REPLACE:
                // we should find at least one rejected twin.
                if (!ignoreWarning
                        && !optionalFeatures.contains(DISABLE_REPLACE_WARNING)
                        && !isNodeOperationPresent(xmlElement, actions, ActionType.REJECTED)) {
                    mergingReport.addMessage(
                            xmlElement,
                            MergingReport.Record.Severity.WARNING,
                            String.format(
                                    "%1$s was tagged at %2$s:%3$d to replace another declaration "
                                            + "but no other declaration present",
                                    xmlElement.getId(),
                                    xmlElement.getDocument().getSourceFile().print(true),
                                    xmlElement.getPosition().getStartLine() + 1));
                }
                break;
            case REMOVE:
            case REMOVE_ALL:
                // we should find at least one rejected twin.
                if (!ignoreWarning
                        && !isNodeOperationPresent(xmlElement, actions, ActionType.REJECTED)) {
                    mergingReport.addMessage(
                            xmlElement,
                            MergingReport.Record.Severity.WARNING,
                            String.format(
                                    "%1$s was tagged at %2$s:%3$d to remove other declarations "
                                            + "but no other declaration present",
                                    xmlElement.getId(),
                                    xmlElement.getDocument().getSourceFile().print(true),
                                    xmlElement.getPosition().getStartLine() + 1));
                }
                break;
        }
        validateAttributes(xmlElement, actions, mergingReport, optionalFeatures, ignoreWarning);
        validateAndroidAttributes(xmlElement, mergingReport);
        for (XmlElement child : xmlElement.getMergeableElements()) {
            validate(child, actions, mergingReport, optionalFeatures);
        }
    }

    /** Verifies that all merging attributes on a passed xml element were applied. */
    private static void validateAttributes(
            @NotNull XmlElement xmlElement,
            @NotNull Actions actions,
            @NotNull MergingReport.Builder mergingReport,
            @NotNull ImmutableList<ManifestMerger2.Invoker.Feature> optionalFeatures,
            boolean ignoreWarning) {

        @NotNull Collection<Map.Entry<XmlNode.NodeName, AttributeOperationType>> attributeOperations
                = xmlElement.getAttributeOperations();
        for (Map.Entry<XmlNode.NodeName, AttributeOperationType> attributeOperation :
                attributeOperations) {
            switch (attributeOperation.getValue()) {
                case REMOVE:
                    if (!ignoreWarning
                            && !isAttributeOperationPresent(
                                    xmlElement, attributeOperation, actions, ActionType.REJECTED)) {
                        mergingReport.addMessage(
                                xmlElement,
                                MergingReport.Record.Severity.WARNING,
                                String.format(
                                        "%1$s@%2$s was tagged at %3$s:%4$d to remove other"
                                                + " declarations but no other declaration present",
                                        xmlElement.getId(),
                                        attributeOperation.getKey(),
                                        xmlElement.getDocument().getSourceFile().print(true),
                                        xmlElement.getPosition().getStartLine() + 1));
                    }
                    break;
                case REPLACE:
                    if (!ignoreWarning
                            && !optionalFeatures.contains(DISABLE_REPLACE_WARNING)
                            && !isAttributeOperationPresent(
                                    xmlElement, attributeOperation, actions, ActionType.REJECTED)) {
                        mergingReport.addMessage(
                                xmlElement,
                                MergingReport.Record.Severity.WARNING,
                                String.format(
                                        "%1$s@%2$s was tagged at %3$s:%4$d to replace other"
                                                + " declarations but no other declaration present",
                                        xmlElement.getId(),
                                        attributeOperation.getKey(),
                                        xmlElement.getDocument().getSourceFile().print(true),
                                        xmlElement.getPosition().getStartLine() + 1));
                    }
                    break;
            }
        }

    }

    /**
     * Check in our list of applied actions that a particular {@link ActionType} action was recorded
     * on the passed element.
     *
     * @return true if it was applied, false otherwise.
     */
    private static boolean isNodeOperationPresent(
            @NotNull XmlElement xmlElement, @NotNull Actions actions, ActionType action) {

        for (Actions.NodeRecord nodeRecord : actions.getNodeRecords(xmlElement.getId())) {
            if (nodeRecord.getActionType() == action) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check in our list of attribute actions that a particular {@link ActionType} action was
     * recorded on the passed element.
     *
     * @return true if it was applied, false otherwise.
     */
    private static boolean isAttributeOperationPresent(
            @NotNull XmlElement xmlElement,
            @NotNull Map.Entry<XmlNode.NodeName, AttributeOperationType> attributeOperation,
            @NotNull Actions actions,
            ActionType action) {

        for (Actions.AttributeRecord attributeRecord : actions.getAttributeRecords(
                xmlElement.getId(), attributeOperation.getKey())) {
            if (attributeRecord.getActionType() == action) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates all {@link XmlElement} attributes belonging to the {@link SdkConstants#ANDROID_URI}
     * namespace.
     *
     * @param xmlElement xml element to check the attributes from.
     * @param mergingReport report for errors and warnings.
     */
    private static void validateAndroidAttributes(
            @NotNull XmlElement xmlElement, @NotNull MergingReport.Builder mergingReport) {

        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            if (xmlAttribute.getModel() != null) {
                AttributeModel.Validator onWriteValidator = xmlAttribute.getModel()
                        .getOnWriteValidator();
                if (onWriteValidator != null) {
                    onWriteValidator.validates(
                            mergingReport, xmlAttribute, xmlAttribute.getValue());
                }
            }
        }
    }
    /**
     * check if the tools:ignore_warning is set
     *
     * @param xmlElement the current XmlElement
     * @return whether the ignoreWarning flag is set
     */
    @VisibleForTesting
    static boolean checkIgnoreWarning(@NotNull XmlElement xmlElement) {
        @NotNull
        Collection<Map.Entry<XmlNode.NodeName, AttributeOperationType>> attributeOperations =
                xmlElement.getAttributeOperations();
        for (Map.Entry<XmlNode.NodeName, AttributeOperationType> attributeOperation :
                attributeOperations) {
            if (attributeOperation.getValue() == AttributeOperationType.IGNORE_WARNING) {
                if (attributeOperation.getKey().toString().equals("tools:true")) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private static void checkOnlyOneUsesSdk(
            @NotNull XmlDocument manifest, @NotNull MergingReport.Builder mergingReport) {
        XmlElement root = manifest.getRootNode();
        Preconditions.checkNotNull(root);
        var actions = mergingReport.build().getActions();
        // Ignore uses-sdk elements with feature flag attribute. This may result in multiple
        // definitions of uses-sdk when feature flags are resolved which should be validated
        // by AAPT and not manifest-merger.
        List<XmlElement> list = root.getAllNodesByType(ManifestModel.NodeTypes.USES_SDK);
        Map<String, Set<String>> duplicates =
                list.stream()
                        .collect(Collectors.groupingBy(XmlElement::getId, Collectors.toList()))
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .flatMap(
                                entry ->
                                        entry.getValue().stream()
                                                .map(
                                                        element ->
                                                                Map.entry(
                                                                        entry.getKey(),
                                                                        getElementLocation(
                                                                                element, actions))))
                        .collect(
                                Collectors.groupingBy(
                                        Map.Entry::getValue,
                                        Collectors.mapping(
                                                entry -> entry.getKey().toString(),
                                                Collectors.toSet())));
        if (!duplicates.isEmpty()) {
            mergingReport.addMessage(
                    manifest.getSourceFile(),
                    MergingReport.Record.Severity.ERROR,
                    String.format(
                            "Multiple <uses-sdk>s cannot be present in the merged"
                                    + " AndroidManifest.xml. Found duplicates in these manifest"
                                    + " files:\n"
                                    + "    %1$s",
                            duplicates.entrySet().stream()
                                    .map(
                                            entry ->
                                                    String.format(
                                                            "%1$s(%2$s)",
                                                            entry.getKey(), entry.getValue()))
                                    .collect(Collectors.joining(System.lineSeparator() + "    "))));
            mergingReport.build();
        }
    }

    private static String getElementLocation(XmlElement xmlElement, Actions actions) {
        if (xmlElement.getFeatureFlagAttribute() == null) {
            var nodeRecord = actions.findNodeRecord(xmlElement.getId());
            return nodeRecord != null ? nodeRecord.mActionLocation.getFile().print(true) : "";
        }
        var attributeRecord =
                actions.findAttributeRecord(
                        xmlElement.getId(), xmlElement.getFeatureFlagAttribute().getName());
        return attributeRecord != null ? attributeRecord.mActionLocation.getFile().print(true) : "";
    }
}
