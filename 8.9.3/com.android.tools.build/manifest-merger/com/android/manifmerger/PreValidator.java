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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.manifmerger.ManifestMerger2.COMPATIBLE_SCREENS_SUB_MANIFEST;
import static com.android.manifmerger.ManifestMerger2.WEAR_APP_SUB_MANIFEST;
import static com.android.manifmerger.MergingReport.Record.Severity.ERROR;
import static com.android.manifmerger.MergingReport.Record.Severity.WARNING;
import static com.android.manifmerger.XmlNode.NodeKey;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.xml.AndroidManifest;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Validates a loaded {@link XmlDocument} and check for potential inconsistencies in the model due
 * to user error or omission.
 *
 * This is implemented as a separate class so it can be invoked by tools independently from the
 * merging process.
 *
 * This validator will check the state of the loaded xml document before any merging activity is
 * attempted. It verifies things like a "tools:replace="foo" attribute has a "android:foo"
 * attribute also declared on the same element (since we want to replace its value).
 */
public class PreValidator {

    private PreValidator(){
    }

    /**
     * Validates a loaded {@link XmlDocument} and return a status of the merging model.
     *
     * <p>Will return one the following status :
     *
     * <ul>
     *   <li>{@link MergingReport.Result#SUCCESS} : the merging model is correct, merging should be
     *       attempted
     *   <li>{@link MergingReport.Result#WARNING} : the merging model contains non fatal error, user
     *       should be notified, merging can be attempted
     *   <li>{@link MergingReport.Result#ERROR} : the merging model contains errors, user must be
     *       notified, merging should not be attempted
     * </ul>
     *
     * A successful validation does not mean that the merging will be successful.
     *
     * @param mergingReport report to log warnings and errors.
     * @param xmlDocument the loaded xml part.
     * @param validateExtractNativeLibsFromSources whether to warn if the {@link
     *     SdkConstants#ATTR_EXTRACT_NATIVE_LIBS} attribute is set in a source (MAIN or OVERLAY)
     *     manifest.
     * @param validateExtractNativeLibsFromDependencies whether to warn if the {@link
     *     SdkConstants#ATTR_EXTRACT_NATIVE_LIBS} attribute is set to true in a dependency (LIBRARY)
     *     manifest.
     * @return one the {@link MergingReport.Result} value.
     */
    @NonNull
    public static MergingReport.Result validate(
            @NonNull MergingReport.Builder mergingReport,
            @NonNull XmlDocument xmlDocument,
            boolean validateExtractNativeLibsFromSources,
            boolean validateExtractNativeLibsFromDependencies) {

        validatePackageAttribute(
                mergingReport, xmlDocument.getRootNode(), xmlDocument.getFileType());
        if (validateExtractNativeLibsFromSources) {
            validateExtractNativeLibsFromSources(mergingReport, xmlDocument);
        }
        if (validateExtractNativeLibsFromDependencies) {
            validateExtractNativeLibsFromDependencies(mergingReport, xmlDocument);
        }
        return validate(mergingReport, xmlDocument.getRootNode());
    }

    @NonNull
    private static MergingReport.Result validate(@NonNull MergingReport.Builder mergingReport,
            @NonNull XmlElement xmlElement) {

        validateAttributeInstructions(mergingReport, xmlElement);

        validateAndroidAttributes(mergingReport, xmlElement);

        checkSelectorPresence(mergingReport, xmlElement);

        // create a temporary hash map of children indexed by key to ensure key uniqueness.
        Map<NodeKey, XmlElement> childrenKeys = new HashMap<>();
        for (XmlElement childElement : xmlElement.getMergeableElements()) {

            // if this element is tagged with 'tools:node=removeAll', ensure it has no other
            // attributes.
            if (childElement.getOperationType() == NodeOperationType.REMOVE_ALL) {
                validateRemoveAllOperation(mergingReport, childElement);
            } else {
                if (checkKeyPresence(mergingReport, childElement)) {
                    XmlElement twin = childrenKeys.get(childElement.getId());
                    if (twin != null && !childElement.getType().areMultipleDeclarationAllowed()) {
                        // we have 2 elements with the same identity, if they are equals,
                        // issue a warning, if not, issue an error.
                        String message =
                                String.format(
                                        "Element %1$s at %2$s duplicated with element declared at"
                                                + " %3$s",
                                        childElement.getId(),
                                        childElement.printPosition(),
                                        childrenKeys.get(childElement.getId()).printPosition());
                        if (twin.compareTo(childElement).isPresent()) {
                            mergingReport.addMessage(childElement, ERROR, message);
                        } else {
                            mergingReport.addMessage(childElement, WARNING, message);
                        }
                    }
                    childrenKeys.put(childElement.getId(), childElement);
                }
                validate(mergingReport, childElement);
            }
        }
        return mergingReport.hasErrors()
                ? MergingReport.Result.ERROR : MergingReport.Result.SUCCESS;
    }

    /**
     * Validate an xml declaration with 'tools:node="removeAll" annotation. There should not
     * be any other attribute declaration on this element.
     */
    private static void validateRemoveAllOperation(@NonNull MergingReport.Builder mergingReport,
            @NonNull XmlElement element) {

        if (element.getAttributeCount() > 1) {
            var extraAttributeNames =
                    element.getAttributeNames(
                            item ->
                                    !(SdkConstants.TOOLS_URI.equals(item.getNamespaceURI())
                                            && NodeOperationType.NODE_LOCAL_NAME.equals(
                                                    item.getLocalName())));
            String message = String.format(
                    "Element %1$s at %2$s annotated with 'tools:node=\"removeAll\"' cannot "
                            + "have other attributes : %3$s",
                    element.getId(),
                    element.printPosition(),
                    Joiner.on(',').join(extraAttributeNames)
            );
            mergingReport.addMessage(element, ERROR, message);
        }
    }

    private static void checkSelectorPresence(@NonNull MergingReport.Builder mergingReport,
            @NonNull XmlElement element) {

        Attr selectorAttribute =
                element.getAttributeNodeNS(SdkConstants.TOOLS_URI, Selector.SELECTOR_LOCAL_NAME);
        if (selectorAttribute!=null && !element.supportsSelector()) {
            String message = String.format(
                    "Unsupported tools:selector=\"%1$s\" found on node %2$s at %3$s",
                    selectorAttribute.getValue(),
                    element.getId(),
                    element.printPosition());
            mergingReport.addMessage(element, ERROR, message);
        }
    }

    private static void validatePackageAttribute(
            @NonNull MergingReport.Builder mergingReport,
            @NonNull XmlElement manifest,
            XmlDocument.Type fileType) {
        Attr attributeNode = manifest.getAttributeNode(AndroidManifest.ATTRIBUTE_PACKAGE);
        // it's ok for other manifest types to have no package name, but it's an error for
        // library manifest types.
        if ((attributeNode == null || attributeNode.getValue().isEmpty())
                && fileType == XmlDocument.Type.LIBRARY
                && !isSubManifest(manifest)) {
            mergingReport.addMessage(
                    manifest,
                    ERROR,
                    String.format(
                            "Missing 'package' declaration in manifest at %1$s",
                            manifest.printPosition()));
        }
    }

    /** Warn if android:extractNativeLibs is set in a MAIN or OVERLAY manifest. */
    private static void validateExtractNativeLibsFromSources(
            @NonNull MergingReport.Builder mergingReport, @NonNull XmlDocument xmlDocument) {
        // Ignore manifests coming from dependencies.
        if (xmlDocument.getFileType() == XmlDocument.Type.LIBRARY) {
            return;
        }
        final Boolean extractNativeLibsValue = getExtractNativeLibsValue(xmlDocument);
        if (extractNativeLibsValue != null) {
            String warning =
                    String.format(
                            "android:%1$s should not be specified in this "
                                    + "source AndroidManifest.xml file. See "
                                    + "%2$s for more information.\n"
                                    + "The AGP Upgrade Assistant can remove "
                                    + "the attribute from the "
                                    + "AndroidManifest.xml file and update the "
                                    + "build file accordingly. See %3$s for "
                                    + "more information.",
                            SdkConstants.ATTR_EXTRACT_NATIVE_LIBS,
                            "https://d.android.com/guide/topics/manifest/application-element#extractNativeLibs",
                            "https://d.android.com/studio/build/agp-upgrade-assistant");
            getExtractNativeLibsAttribute(xmlDocument)
                    .ifPresent(it -> mergingReport.addMessage(it, WARNING, warning));
        }
    }

    /** Warn if android:extractNativeLibs is set to true in LIBRARY manifest. */
    private static void validateExtractNativeLibsFromDependencies(
            @NonNull MergingReport.Builder mergingReport, @NonNull XmlDocument xmlDocument) {
        // Ignore MAIN and OVERLAY manifests.
        if (xmlDocument.getFileType() != XmlDocument.Type.LIBRARY) {
            return;
        }
        final Boolean extractNativeLibsValue = getExtractNativeLibsValue(xmlDocument);
        if (Boolean.TRUE.equals(extractNativeLibsValue)) {
            String warning =
                    String.format(
                            "android:%1$s is set to true in a dependency's "
                                    + "AndroidManifest.xml, but not in the "
                                    + "app's merged manifest. If the "
                                    + "dependency truly requires its native "
                                    + "libraries to be extracted, the app can "
                                    + "be configured to do so by setting the "
                                    + "jniLibs.useLegacyPackaging DSL to "
                                    + "true.\n"
                                    + "Otherwise, you can silence this type of "
                                    + "warning by adding %2$s=true to your "
                                    + "gradle.properties file.",
                            SdkConstants.ATTR_EXTRACT_NATIVE_LIBS,
                            "android.experimental.suppressExtractNativeLibsWarnings");
            getExtractNativeLibsAttribute(xmlDocument)
                    .ifPresent(it -> mergingReport.addMessage(it, WARNING, warning));
        }
    }

    /**
     * @param xmlDocument the XmlDocument to check for the value of the android:extractNativeLibs
     *     attribute
     * @return the Boolean value of the android:extractNativeLibs attribute, or null if it's not set
     */
    @Nullable
    static Boolean getExtractNativeLibsValue(XmlDocument xmlDocument) {
        final XmlAttribute extractNativeLibsAttribute =
                getExtractNativeLibsAttribute(xmlDocument).orElse(null);
        if (extractNativeLibsAttribute == null) {
            return null;
        }
        return Boolean.valueOf(extractNativeLibsAttribute.getValue());
    }

    private static Optional<XmlAttribute> getExtractNativeLibsAttribute(XmlDocument xmlDocument) {
        if (xmlDocument == null) {
            return Optional.empty();
        }
        final XmlElement applicationElement =
                xmlDocument.getByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null).orElse(null);
        if (applicationElement == null) {
            return Optional.empty();
        }
        return applicationElement.getAttribute(
                XmlNode.fromNSName(ANDROID_URI, "android", SdkConstants.ATTR_EXTRACT_NATIVE_LIBS));
    }

    private static boolean isSubManifest(@NonNull XmlElement manifest) {
        String description = manifest.getSourceFile().getDescription();
        if (description == null) {
            return false;
        }
        return description.equals(WEAR_APP_SUB_MANIFEST)
                || description.equals(COMPATIBLE_SCREENS_SUB_MANIFEST)
                || description.endsWith(
                        SdkConstants.PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX);
    }

    /**
     * Checks that an element which is supposed to have a key does have one.
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check for key presence.
     * @return true if the element has a valid key or false it does not need one or it is invalid.
     */
    private static boolean checkKeyPresence(
            @NonNull MergingReport.Builder mergingReport,
            @NonNull XmlElement xmlElement) {
        NodeKeyResolver nodeKeyResolver = xmlElement.getType().getNodeKeyResolver();
        ImmutableList<String> keyAttributesNames = nodeKeyResolver.getKeyAttributesNames();
        if (keyAttributesNames.isEmpty()) {
            return false;
        }
        if (Strings.isNullOrEmpty(xmlElement.getKey())) {
            // we should have a key but we don't.
            String message = keyAttributesNames.size() > 1
                    ? String.format(
                            "Missing one of the key attributes '%1$s' on element %2$s at %3$s",
                            Joiner.on(',').join(keyAttributesNames),
                            xmlElement.getId(),
                            xmlElement.printPosition())
                    : String.format(
                            "Missing '%1$s' key attribute on element %2$s at %3$s",
                            keyAttributesNames.get(0),
                            xmlElement.getId(),
                            xmlElement.printPosition());
            mergingReport.addMessage(xmlElement, ERROR, message);
            return false;
        }
        return true;
    }

    /**
     * Validate attributes part of the {@link SdkConstants#ANDROID_URI}
     *
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check its attributes.
     */
    private static void validateAndroidAttributes(
            @NonNull MergingReport.Builder mergingReport, @NonNull XmlElement xmlElement) {
        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            AttributeModel model = xmlAttribute.getModel();
            if (model != null && model.getOnReadValidator() != null) {
                model.getOnReadValidator().validates(
                        mergingReport, xmlAttribute, xmlAttribute.getValue());
            }
        }
    }

    /**
     * Validates attributes part of the {@link SdkConstants#TOOLS_URI}
     *
     * @param mergingReport report to log warnings and errors.
     * @param xmlElement xml element to check its attributes.
     */
    private static void validateAttributeInstructions(
            @NonNull MergingReport.Builder mergingReport, @NonNull XmlElement xmlElement) {

        for (Map.Entry<XmlNode.NodeName, AttributeOperationType> attributeOperationTypeEntry :
                xmlElement.getAttributeOperations()) {

            Optional<XmlAttribute> attribute = xmlElement
                    .getAttribute(attributeOperationTypeEntry.getKey());
            switch(attributeOperationTypeEntry.getValue()) {
                case STRICT:
                case IGNORE_WARNING:
                    break;
                case REMOVE:
                    // check we are not provided a new value.
                    if (attribute.isPresent()) {
                        // Add one to startLine so the first line is displayed as 1.
                        mergingReport.addMessage(
                                xmlElement,
                                ERROR,
                                String.format(
                                        "tools:remove specified at line:%d for attribute %s, but "
                                                + "attribute also declared at line:%d, "
                                                + "do you want to use tools:replace instead ?",
                                        xmlElement.getPosition().getStartLine() + 1,
                                        attributeOperationTypeEntry.getKey(),
                                        attribute.get().getPosition().getStartLine() + 1));
                    }
                    break;
                case REPLACE:
                    // check we are provided a new value
                    if (!attribute.isPresent()) {
                        // Add one to startLine so the first line is displayed as 1.
                        mergingReport.addMessage(
                                xmlElement,
                                ERROR,
                                String.format(
                                        "tools:replace specified at line:%d for attribute %s, but "
                                                + "no new value specified",
                                        xmlElement.getPosition().getStartLine() + 1,
                                        attributeOperationTypeEntry.getKey()));
                    }
                    break;
                default:
                    throw new IllegalStateException("Unhandled AttributeOperationType " +
                            attributeOperationTypeEntry.getValue());
            }
        }
    }
}
