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

import static com.android.manifmerger.ManifestModel.NodeTypes.USES_PERMISSION;
import static com.android.manifmerger.ManifestModel.NodeTypes.USES_SDK;
import static com.android.manifmerger.PlaceholderHandler.KeyBasedValueResolver;
import static com.android.manifmerger.PlaceholderHandler.PACKAGE_NAME;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.Pair;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Represents a loaded xml document.
 *
 * <p>Has pointers to the root {@link XmlElement} element and provides services to persist the
 * document to an external format. Also provides abilities to be merged with other {@link
 * XmlDocument} as well as access to the line numbers for all document's xml elements and
 * attributes.
 */
public class XmlDocument {

    private static final String DEFAULT_SDK_VERSION = "1";
    private static final int INVALID_SDK_VERSION = -1;

    /**
     * Clones and transforms an XML document.
     *
     * @return a pair of document and flag on whether new document is differ from original one.
     */
    @NonNull
    public Pair<Document, Boolean> cloneAndTransform(
            Predicate<Node> transform, Predicate<Node> shouldRemove)
            throws ManifestMerger2.MergeFailureException {
        return DomMergeUtils.cloneAndTransform(getXml(), transform, shouldRemove);
    }

    /**
     * The document type.
     */
    public enum Type {
        /**
         * A manifest overlay as found in the build types and variants.
         */
        OVERLAY,
        /**
         * The main android manifest file.
         */
        MAIN,
        /**
         * A library manifest that is imported in the application.
         */
        LIBRARY
    }

    private final Element mRootElement;
    // this is initialized lazily to avoid un-necessary early parsing.
    @NonNull private final AtomicReference<XmlElement> mRootNode = new AtomicReference<>(null);
    @NonNull
    private final SourceFile mSourceFile;
    @NonNull
    private final KeyResolver<String> mSelectors;

    @NonNull
    private final KeyBasedValueResolver<ManifestSystemProperty>
            mSystemPropertyResolver;

    @NonNull
    private final Type mType;
    @Nullable private final String mNamespace;
    @NonNull private final DocumentModel<ManifestModel.NodeTypes> mModel;
    @NonNull public Map<Element, NodeOperationType> originalNodeOperation = new HashMap<>();

    public XmlDocument(
            @NonNull SourceFile sourceLocation,
            @NonNull KeyResolver<String> selectors,
            @NonNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertyResolver,
            @NonNull Element element,
            @NonNull Type type,
            @Nullable String namespace,
            @NonNull DocumentModel<ManifestModel.NodeTypes> model) {
        this.mSourceFile = Preconditions.checkNotNull(sourceLocation);
        this.mRootElement = Preconditions.checkNotNull(element);
        this.mSelectors = Preconditions.checkNotNull(selectors);
        this.mSystemPropertyResolver = Preconditions.checkNotNull(systemPropertyResolver);
        this.mType = type;
        this.mNamespace = namespace;
        this.mModel = model;
    }

    @NonNull
    public Type getFileType() {
        return mType;
    }

    @NonNull
    public DocumentModel<ManifestModel.NodeTypes> getModel() {
        return mModel;
    }

    /**
     * Returns a pretty string representation of this document.
     */
    @NonNull
    public String prettyPrint() {
        return prettyPrint(getXml());
    }

    /** Returns a pretty string representation of this document. */
    @NonNull
    public static String prettyPrint(Document document) {
        return XmlPrettyPrinter.prettyPrint(
                document,
                XmlFormatPreferences.defaults(),
                XmlFormatStyle.get(document),
                null, /* endOfLineSeparator */
                false /* endWithNewLine */);
    }

    /**
     * merge this higher priority document with a higher priority document.
     *
     * @param lowerPriorityDocument the lower priority document to merge in.
     * @param mergingReportBuilder the merging report to record errors and actions.
     * @param processCancellationChecker checks if the request to merge is cancelled.
     * @return a new merged {@link XmlDocument} or {@link Optional#empty()} if there were errors
     *     during the merging activities.
     */
    @NonNull
    public Optional<XmlDocument> merge(
            @NonNull XmlDocument lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReportBuilder,
            ManifestMerger2.ProcessCancellationChecker processCancellationChecker) {
        return merge(
                lowerPriorityDocument,
                mergingReportBuilder,
                true /* addImplicitPermissions */,
                false /* disableMinSdkVersionCheck */,
                false /* keepGoingOnErrors */,
                processCancellationChecker);
    }

    /**
     * merge this higher priority document with a higher priority document.
     *
     * @param lowerPriorityDocument the lower priority document to merge in.
     * @param mergingReportBuilder the merging report to record errors and actions.
     * @param addImplicitPermissions whether to perform implicit permission addition.
     * @param processCancellationChecker checks if the request to merge is cancelled.
     * @return a new merged {@link XmlDocument} or {@link Optional#empty()} if there were errors
     *     during the merging activities.
     */
    @NonNull
    public Optional<XmlDocument> merge(
            @NonNull XmlDocument lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReportBuilder,
            boolean addImplicitPermissions,
            boolean disableMinSdkVersionCheck,
            boolean keepGoingOnErrors,
            ManifestMerger2.ProcessCancellationChecker processCancellationChecker) {
        if (getFileType() == Type.MAIN) {
            mergingReportBuilder.getActionRecorder().recordAddedNodeAction(getRootNode(), false);
        }
        ImmutableList<KeyAndReason> implicitElements =
                getImplicitElementsToAdd(
                        lowerPriorityDocument,
                        mergingReportBuilder,
                        addImplicitPermissions,
                        disableMinSdkVersionCheck);

        getRootNode()
                .mergeWithLowerPriorityNode(
                        lowerPriorityDocument.getRootNode(),
                        mergingReportBuilder,
                        processCancellationChecker);
        addImplicitElements(mergingReportBuilder.getActionRecorder(), implicitElements);

        return mergingReportBuilder.hasErrors() && !keepGoingOnErrors
                ? Optional.empty()
                : Optional.of(this);
    }

    /** Returns a {@link KeyResolver} capable of resolving all selectors types */
    @NonNull
    public KeyResolver<String> getSelectors() {
        return mSelectors;
    }

    /**
     * Returns the {@link KeyBasedValueResolver} capable of resolving all injected
     * {@link ManifestSystemProperty}
     */
    @NonNull
    public KeyBasedValueResolver<ManifestSystemProperty>
            getSystemPropertyResolver() {
        return mSystemPropertyResolver;
    }

    /**
     * Compares this document to another {@link XmlDocument} ignoring all attributes belonging to
     * the {@link SdkConstants#TOOLS_URI} namespace.
     *
     * @param other the other document to compare against.
     * @return a {@link String} describing the differences between the two XML elements or {@link
     *     Optional#empty()} if they are equals.
     */
    @SuppressWarnings("CovariantCompareTo")
    public Optional<String> compareTo(@NonNull XmlDocument other) {
        return getRootNode().compareTo(other.getRootNode());
    }

    /**
     * Returns the position of the specified {@link XmlNode}.
     */
    @NonNull
    static SourcePosition getNodePosition(@NonNull XmlNode node) {
        return getNodePosition(node.getXml());
    }

    /** Returns the position of the specified {@link Node}. */
    @NonNull
    static SourcePosition getNodePosition(@NonNull Node xml) {
        return PositionXmlParser.getPosition(xml);
    }

    /**
     * Returns the {@link SourceFile} associated with this XML document.
     * <p>
     * NOTE: You should <b>not</b> read the contents of the file directly; if you need to
     * access the content, use {@link ManifestMerger2#getFileStreamProvider()} instead.
     *
     * @return the source file
     */
    @NonNull
    public SourceFile getSourceFile() {
        return mSourceFile;
    }

    public synchronized void resetRootNode() {
        if (mRootNode.get() != null) {
            this.mRootNode.set(new XmlElement(mRootElement, this));
        }
    }

    public synchronized XmlElement getRootNode() {
        if (mRootNode.get() == null) {
            this.mRootNode.set(new XmlElement(mRootElement, this));
        }
        return mRootNode.get();
    }

    public Optional<XmlElement> getByTypeAndKey(
            ManifestModel.NodeTypes type,
            @Nullable String keyValue) {

        return getRootNode().getNodeByTypeAndKey(type, keyValue);
    }

    /**
     * Namespace for this android manifest which will be used to resolve partial paths.
     *
     * @return the namespace to do partial class names resolution.
     */
    @Nullable
    public String getNamespace() {
        return mNamespace;
    }

    /**
     * Returns the split name if this manifest file has one.
     *
     * @return the split name or empty string.
     */
    public String getSplitName() {
        return mRootElement.getAttribute("split");
    }

    public Optional<XmlAttribute> getPackage() {
        Optional<XmlAttribute> packageAttribute =
                getRootNode().getAttribute(XmlNode.fromXmlName("package"));
        return packageAttribute.isPresent()
                ? packageAttribute
                : getRootNode().getAttribute(XmlNode.fromNSName(
                        SdkConstants.ANDROID_URI, "android", "package"));
    }

    public Document getXml() {
        return mRootElement.getOwnerDocument();
    }

    /**
     * Returns the minSdk version specified in the uses_sdk element if present or the default value.
     */
    @NonNull
    private String getExplicitMinSdkVersionOrDefault(@NonNull MergingReport.Builder mergingReport) {
        String value = getExplicitMinSdkVersion(mergingReport);
        return value != null ? value : DEFAULT_SDK_VERSION;
    }

    /**
     * Returns the minSdk version for this manifest file. It can be injected from the outer
     * build.gradle or can be expressed in the uses_sdk element (which is now ignored, generates a
     * warning and will be an error in 8.0).
     */
    @NonNull
    public String getMinSdkVersion(@NonNull MergingReport.Builder mergingReport) {
        // check for system properties.
        String injectedMinSdk =
                mSystemPropertyResolver.getValue(ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION);
        if (injectedMinSdk != null) {
            return injectedMinSdk;
        }
        return getExplicitMinSdkVersionOrDefault(mergingReport);
    }

    /**
     * Returns the targetSdk version specified in the uses_sdk element if present in the
     * AndroidManifest.xml file, or null if not explicitly specified.
     *
     * <p>Note that specifying this value in the AndroidManifest.xml file is now ignored, generates
     * a warning and will be an error in 8.0).
     */
    @Nullable
    private String getExplicitTargetSdkVersion(@NonNull MergingReport.Builder mergingReport) {
        return getExplicitVersionAttribute("android:targetSdkVersion", mergingReport);
    }

    /**
     * Returns the maxSdk version specified in the uses_sdk element if present in the
     * AndroidManifest.xml file, or null if not explicitly specified.
     *
     * <p>Note that specifying this value in the AndroidManifest.xml file is now ignored, generates
     * a warning and will be an error in 8.0).
     */
    @Nullable
    private String getExplicitMaxSdkVersion(@NonNull MergingReport.Builder mergingReport) {
        return getExplicitVersionAttribute("android:maxSdkVersion", mergingReport);
    }

    /**
     * Returns the minSdk version specified in the uses_sdk element if present in the
     * AndroidManifest.xml file, or null if not explicitly specified.
     *
     * <p>Note that specifying this value in the AndroidManifest.xml file is now ignored, generates
     * a warning and will be an error in 8.0).
     */
    @Nullable
    private String getExplicitMinSdkVersion(@NonNull MergingReport.Builder mergingReport) {
        return getExplicitVersionAttribute("android:minSdkVersion", mergingReport);
    }

    /**
     * Returns a version attribute from the uses-sdk xml element.
     *
     * @param attributeName the attribute name
     * @return the value or null if not specified.
     */
    private String getExplicitVersionAttribute(
            String attributeName, @NonNull MergingReport.Builder mergingReport) {
        Optional<XmlElement> usesSdk = getByTypeAndKey(USES_SDK, null);
        if (usesSdk.isPresent()) {
            Optional<XmlAttribute> specifiedVersion =
                    usesSdk.get().getAttribute(XmlNode.fromXmlName(attributeName));
            if (specifiedVersion.isPresent()) {
                String stringValue = specifiedVersion.get().getValue();
                if (getApiLevelFromAttribute(stringValue) == INVALID_SDK_VERSION) {
                    String message =
                            String.format(
                                    "Invalid value for attribute:%1$s, value:%2$s",
                                    attributeName, stringValue);
                    mergingReport.addMessage(
                            usesSdk.get(), MergingReport.Record.Severity.ERROR, message);
                }
                return stringValue;
            }
        }
        return null;
    }

    /**
     * Returns the targetSdk version specified in the uses_sdk element if present or the default
     * value.
     */
    @NonNull
    private String getRawTargetSdkVersion(@NonNull MergingReport.Builder mergingReport) {
        String explicitTargetSdkVersion = getExplicitTargetSdkVersion(mergingReport);
        if (explicitTargetSdkVersion != null) {
            return explicitTargetSdkVersion;
        }
        return getExplicitMinSdkVersionOrDefault(mergingReport);
    }

    /**
     * Returns the targetSdk version for this manifest file. It can be injected from the outer
     * build.gradle or can be expressed in the uses_sdk element.
     */
    @NonNull
    public String getTargetSdkVersion(@NonNull MergingReport.Builder mergingReport) {

        // check for system properties.
        String injectedTargetVersion =
                mSystemPropertyResolver.getValue(ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION);
        if (injectedTargetVersion != null) {
            return injectedTargetVersion;
        }
        return getRawTargetSdkVersion(mergingReport);
    }

    /**
     * Returns the maxSdkVersion version for this manifest file. It can be injected from the outer
     * build.gradle or can be expressed in the uses_sdk element.
     */
    @Nullable
    public String getMaxSdkVersion(@NonNull MergingReport.Builder mergingReport) {

        // check for system properties.
        String injectedMaxVersion =
                mSystemPropertyResolver.getValue(ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION);
        if (injectedMaxVersion != null) {
            return injectedMaxVersion;
        }
        return getExplicitMaxSdkVersion(mergingReport);
    }

    boolean checkTopLevelDeclarations(
            Map<String, Object> placeHolderValues,
            MergingReport.Builder mergingReportBuilder,
            XmlDocument.Type documentType) {
        // first do we have a package declaration in the main manifest ?
        Optional<XmlAttribute> mainPackageAttribute = getPackage();
        if (!placeHolderValues.containsKey(PACKAGE_NAME)
                && documentType != XmlDocument.Type.OVERLAY
                && !mainPackageAttribute.isPresent()) {
            mergingReportBuilder.addMessage(
                    getSourceFile(),
                    MergingReport.Record.Severity.ERROR,
                    String.format(
                            "Main AndroidManifest.xml at %1$s manifest:package attribute "
                                    + "is not declared",
                            getSourceFile().print(true)));
            return false;
        }

        // the version from uses-sdk is ignore, issue a warning if is a different version than
        // the injected one as it would lead to confusion.
        Optional<XmlElement> usesSdk = getByTypeAndKey(ManifestModel.NodeTypes.USES_SDK, null);
        if (usesSdk.isPresent()) {
            verifyVersion(
                    usesSdk.get(),
                    () -> getExplicitMinSdkVersion(mergingReportBuilder),
                    () -> getMinSdkVersion(mergingReportBuilder),
                    "minSdkVersion",
                    mergingReportBuilder);

            verifyVersion(
                    usesSdk.get(),
                    () -> getExplicitTargetSdkVersion(mergingReportBuilder),
                    () -> getTargetSdkVersion(mergingReportBuilder),
                    "targetSdkVersion",
                    mergingReportBuilder);

            verifyVersion(
                    usesSdk.get(),
                    () -> getExplicitMaxSdkVersion(mergingReportBuilder),
                    () -> getMaxSdkVersion(mergingReportBuilder),
                    "maxSdkVersion",
                    mergingReportBuilder);
        }
        return true;
    }

    private void verifyVersion(
            XmlElement usesSdk,
            Supplier<String> rawValueSupplier,
            Supplier<String> usedValueSupplier,
            String propertyName,
            MergingReport.Builder mergingReportBuilder) {
        String rawValue = rawValueSupplier.get();
        if (rawValue != null && !rawValue.equals(usedValueSupplier.get())) {
            String warning =
                    String.format(
                            "uses-sdk:%1$s value (%2$s) specified in the manifest file is ignored. "
                                    + "It is overridden by the value declared in the DSL or the variant API, or 1 if not declared/present. "
                                    + "Current value is (%3$s).",
                            propertyName, rawValueSupplier.get(), usedValueSupplier.get());
            mergingReportBuilder.addMessage(
                    new SourceFilePosition(getSourceFile(), usesSdk.getPosition()),
                    MergingReport.Record.Severity.WARNING,
                    warning);
        }
    }

    /**
     * Decodes a sdk version from either its decimal representation or from a platform code name.
     * @param attributeVersion the sdk version attribute as specified by users.
     * @return the integer representation of the platform level.
     */
    private static int getApiLevelFromAttribute(@NonNull String attributeVersion) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(attributeVersion));
        if (Character.isDigit(attributeVersion.charAt(0))) {
            try {
                return Integer.parseInt(attributeVersion);
            } catch (NumberFormatException e) {
                return INVALID_SDK_VERSION;
            }
        }
        return SdkVersionInfo.getApiByPreviewName(attributeVersion, true);
    }

    private void addImplicitElements(
            ActionRecorder actionRecorder, ImmutableList<KeyAndReason> implicitElements) {
        implicitElements.forEach(
                implicitElement -> {
                    addIfAbsent(
                            actionRecorder, implicitElement.getKey(), implicitElement.getReason());
                });
    }

    /**
     * Compute and get all implicit elements from the passed lower priority document that are
     * required in the target SDK.
     */
    private ImmutableList<KeyAndReason> getImplicitElementsToAdd(
            @NonNull XmlDocument lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReport,
            boolean addImplicitPermissions,
            boolean disableMinSdkVersionCheck) {
        var implicitElementKeys = new ImmutableList.Builder<KeyAndReason>();
        // if this document is an overlay, tolerate the absence of uses-sdk and do not
        // assume implicit minimum versions.
        Optional<XmlElement> usesSdk = getByTypeAndKey(USES_SDK, null);
        if (mType == Type.OVERLAY && usesSdk.isEmpty()) {
            return implicitElementKeys.build();
        }

        // check that the uses-sdk element does not have any tools:node instruction.
        if (usesSdk.isPresent()) {
            XmlElement usesSdkElement = usesSdk.get();
            if (usesSdkElement.getOperationType() != NodeOperationType.MERGE) {
                mergingReport
                        .addMessage(
                                new SourceFilePosition(
                                        getSourceFile(),
                                        usesSdkElement.getPosition()),
                                MergingReport.Record.Severity.ERROR,
                                "uses-sdk element cannot have a \"tools:node\" attribute");
                return implicitElementKeys.build();
            }
        }
        int thisTargetSdk = getApiLevelFromAttribute(getTargetSdkVersion(mergingReport));

        // when we are importing a library, we should never use the build.gradle injected
        // values (only valid for overlay, main manifest) so use the raw versions coming from
        // the AndroidManifest.xml
        int libraryTargetSdk =
                getApiLevelFromAttribute(
                        lowerPriorityDocument.getFileType() == Type.LIBRARY
                                ? lowerPriorityDocument.getRawTargetSdkVersion(mergingReport)
                                : lowerPriorityDocument.getTargetSdkVersion(mergingReport));

        if (thisTargetSdk == INVALID_SDK_VERSION || libraryTargetSdk == INVALID_SDK_VERSION) {
            return implicitElementKeys.build();
        }

        // if library is using a code name rather than an API level, make sure this document target
        // sdk version is using the same code name.
        String libraryTargetSdkVersion = lowerPriorityDocument.getTargetSdkVersion(mergingReport);
        if (!Character.isDigit(libraryTargetSdkVersion.charAt(0))) {
            // this is a code name, ensure this document uses the same code name, unless the
            // targetSdkVersion is not explicitly specified by the library... in that case, there's
            // no need to check here because we'll be doing a similar check for the minSdkVersion.
            if (!libraryTargetSdkVersion.equals(getTargetSdkVersion(mergingReport))
                    && lowerPriorityDocument.getExplicitTargetSdkVersion(mergingReport) != null) {
                mergingReport.addMessage(
                        getSourceFile(),
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "uses-sdk:targetSdkVersion %1$s cannot be different than version "
                                        + "%2$s declared in library %3$s",
                                getTargetSdkVersion(mergingReport),
                                libraryTargetSdkVersion,
                                lowerPriorityDocument.getSourceFile().print(false)));
                return implicitElementKeys.build();
            }
        }
        // same for minSdkVersion, if the library is using a code name, the application must
        // also be using the same code name.
        String libraryMinSdkVersion =
                lowerPriorityDocument.getExplicitMinSdkVersionOrDefault(mergingReport);
        if (!Character.isDigit(libraryMinSdkVersion.charAt(0))) {
            // this is a code name, ensure this document uses the same code name.
            if (!libraryMinSdkVersion.equals(getMinSdkVersion(mergingReport))) {
                mergingReport.addMessage(
                        getSourceFile(),
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "uses-sdk:minSdkVersion %1$s cannot be different than version "
                                        + "%2$s declared in library %3$s",
                                getMinSdkVersion(mergingReport),
                                libraryMinSdkVersion,
                                lowerPriorityDocument.getSourceFile().print(false)));
                return implicitElementKeys.build();
            }
        }

        if (!disableMinSdkVersionCheck
                && !checkUsesSdkMinVersion(lowerPriorityDocument, mergingReport)) {
            String error =
                    String.format(
                            "uses-sdk:minSdkVersion %1$s cannot be smaller than version "
                                    + "%2$s declared in library %3$s as the library might be using APIs not available in %1$s\n"
                                    + "\tSuggestion: use a compatible library with a minSdk of at most %1$s,\n"
                                    + "\t\tor increase this project's minSdk version to at least %2$s,\n"
                                    + "\t\tor use tools:overrideLibrary=\"%4$s\" to force usage (may lead to runtime failures)",
                            getMinSdkVersion(mergingReport),
                            lowerPriorityDocument.getExplicitMinSdkVersionOrDefault(mergingReport),
                            lowerPriorityDocument.getSourceFile().print(false),
                            lowerPriorityDocument.getNamespace());
            if (usesSdk.isPresent()) {
                mergingReport.addMessage(
                        new SourceFilePosition(getSourceFile(), usesSdk.get().getPosition()),
                        MergingReport.Record.Severity.ERROR,
                        error);
            } else {
                mergingReport.addMessage(
                        getSourceFile(), MergingReport.Record.Severity.ERROR, error);
            }
            return implicitElementKeys.build();
        }

        // if the merged document target SDK is equal or smaller than the library's, nothing to do.
        if (thisTargetSdk <= libraryTargetSdk) {
            return implicitElementKeys.build();
        }

        // There is no need to add any implied permissions when targeting an old runtime.
        if (thisTargetSdk < 4) {
            return implicitElementKeys.build();
        }

        if (!addImplicitPermissions) {
            return implicitElementKeys.build();
        }

        boolean hasWriteToExternalStoragePermission =
                lowerPriorityDocument.getByTypeAndKey(
                        USES_PERMISSION, permission("WRITE_EXTERNAL_STORAGE")).isPresent();

        if (libraryTargetSdk < 4) {
            implicitElementKeys.add(
                    KeyAndReason.of(
                            permission("WRITE_EXTERNAL_STORAGE"),
                            lowerPriorityDocument.getNamespace() + " has a targetSdkVersion < 4"));

            hasWriteToExternalStoragePermission = true;

            implicitElementKeys.add(
                    KeyAndReason.of(
                            permission("READ_PHONE_STATE"),
                            lowerPriorityDocument.getNamespace() + " has a targetSdkVersion < 4"));
        }

        // If the application has requested WRITE_EXTERNAL_STORAGE, we will
        // force them to always take READ_EXTERNAL_STORAGE as well.  We always
        // do this (regardless of target API version) because we can't have
        // an app with write permission but not read permission.
        if (hasWriteToExternalStoragePermission) {
            implicitElementKeys.add(
                    KeyAndReason.of(
                            permission("READ_EXTERNAL_STORAGE"),
                            lowerPriorityDocument.getNamespace()
                                    + " requested WRITE_EXTERNAL_STORAGE"));
        }

        // Pre-JellyBean call log permission compatibility.
        if (thisTargetSdk >= 16 && libraryTargetSdk < 16) {
            if (lowerPriorityDocument.getByTypeAndKey(
                    USES_PERMISSION, permission("READ_CONTACTS")).isPresent()) {
                implicitElementKeys.add(
                        KeyAndReason.of(
                                permission("READ_CALL_LOG"),
                                lowerPriorityDocument.getNamespace()
                                        + " has targetSdkVersion < 16 and requested READ_CONTACTS"));
            }
            if (lowerPriorityDocument.getByTypeAndKey(
                    USES_PERMISSION, permission("WRITE_CONTACTS")).isPresent()) {
                implicitElementKeys.add(
                        KeyAndReason.of(
                                permission("WRITE_CALL_LOG"),
                                lowerPriorityDocument.getNamespace()
                                        + " has targetSdkVersion < 16 and requested WRITE_CONTACTS"));
            }
        }
        return implicitElementKeys.build();
    }

    /**
     * Returns true if the minSdkVersion of the application and the library are compatible, false
     * otherwise.
     */
    private boolean checkUsesSdkMinVersion(
            @NonNull XmlDocument lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReport) {

        int thisMinSdk = getApiLevelFromAttribute(getMinSdkVersion(mergingReport));
        int libraryMinSdk =
                getApiLevelFromAttribute(
                        lowerPriorityDocument.getExplicitMinSdkVersionOrDefault(mergingReport));

        // the merged document minSdk cannot be lower than a library
        if (thisMinSdk < libraryMinSdk) {

            // check if this higher priority document has any tools instructions for the node
            Optional<XmlElement> xmlElementOptional = getByTypeAndKey(USES_SDK, null);
            if (!xmlElementOptional.isPresent()) {
                return false;
            }
            XmlElement xmlElement = xmlElementOptional.get();

            // if we find a selector that applies to this library. the users wants to explicitly
            // allow this higher version library to be allowed.
            for (OverrideLibrarySelector selector :
                    xmlElement.getOverrideUsesSdkLibrarySelectors()) {
                if (selector.appliesTo(lowerPriorityDocument.getRootNode())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @NonNull
    private static String permission(String permissionName) {
        return "android.permission." + permissionName;
    }

    /**
     * Adds a new element of type nodeType with a specific keyValue if the element is absent in this
     * document. Will also add attributes expressed through key value pairs.
     *
     * @param actionRecorder to records creation actions.
     * @param keyValue the optional key for the element.
     * @param attributes the optional array of key value pairs for extra element attribute.
     */
    @SafeVarargs
    private final void addIfAbsent(
            @NonNull ActionRecorder actionRecorder,
            @Nullable String keyValue,
            @Nullable String reason,
            @Nullable Pair<String, String>... attributes) {

        Optional<XmlElement> xmlElementOptional =
                getByTypeAndKey(ManifestModel.NodeTypes.USES_PERMISSION, keyValue);
        if (xmlElementOptional.isPresent()) {
            return;
        }
        Element elementNS =
                getXml().createElement(mModel.toXmlName(ManifestModel.NodeTypes.USES_PERMISSION));

        ImmutableList<String> keyAttributesNames =
                ManifestModel.NodeTypes.USES_PERMISSION
                        .getNodeKeyResolver()
                        .getKeyAttributesNames();
        if (keyAttributesNames.size() == 1) {
            elementNS.setAttributeNS(
                    SdkConstants.ANDROID_URI, "android:" + keyAttributesNames.get(0), keyValue);
        }
        if (attributes != null) {
            for (Pair<String, String> attribute : attributes) {
                elementNS.setAttributeNS(
                        SdkConstants.ANDROID_URI, "android:" + attribute.getFirst(),
                        attribute.getSecond());
            }
        }

        // record creation.
        XmlElement xmlElement = new XmlElement(elementNS, this);
        actionRecorder.recordImpliedNodeAction(xmlElement, reason);

        getRootNode().appendChild(elementNS);
    }

    /**
     * Removes the android namespace from all nodes.
     */
    public void clearNodeNamespaces() {
        if (clearNodeNamespaces(getRootNode().getXml())) {
            resetRootNode();
        }
    }

    /**
     * Removes the android namespace from an element recursively.
     *
     * @param element the element
     */
    private boolean clearNodeNamespaces(Element element) {
        boolean nodeRenamed = false;
        String androidPrefix = XmlUtils.lookupNamespacePrefix(element, SdkConstants.ANDROID_URI);

        String name = element.getNodeName();
        int colonIdx = name.indexOf(':');
        if (colonIdx != -1) {
            String prefix = name.substring(0, colonIdx);
            if (prefix.equals(androidPrefix)) {
                String newName = name.substring(colonIdx + 1);
                getXml().renameNode(element, null, newName);
                nodeRenamed = true;
            }
        }

        NodeList childrenNodeList = element.getChildNodes();
        for (int i = 0; i < childrenNodeList.getLength(); i++) {
            Node n = childrenNodeList.item(i);
            if (n instanceof Element) {
                nodeRenamed = nodeRenamed || clearNodeNamespaces((Element) n);
            }
        }
        return nodeRenamed;
    }

    private static class KeyAndReason {

        private final String mKey;
        private final String mReason;

        private KeyAndReason(String key, String reason) {
            this.mKey = key;
            this.mReason = reason;
        }

        public static KeyAndReason of(String key, String reason) {
            return new KeyAndReason(key, reason);
        }

        public String getKey() {
            return mKey;
        }

        public String getReason() {
            return mReason;
        }
    }
}
