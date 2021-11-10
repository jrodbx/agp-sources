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
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.manifmerger.AttributeModel.Hexadecimal32BitsWithMinimumValue;
import static com.android.manifmerger.AttributeModel.OR_MERGING_POLICY;
import static com.android.manifmerger.AttributeModel.SeparatedValuesValidator;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.manifmerger.XmlDocument.Type;
import com.android.utils.SdkUtils;
import com.android.xml.AndroidManifest;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Model for the manifest file merging activities.
 *
 * <p>This model will describe each element that is eligible for merging and associated merging
 * policies. It is not reusable as most of its interfaces are private but a future enhancement could
 * easily make this more generic/reusable if we need to merge more than manifest files.
 */
@Immutable
public class ManifestModel implements DocumentModel<ManifestModel.NodeTypes> {

    private final boolean autoReject;

    /** Creates a DocumentModel to be used for merging Android manifest documents */
    public ManifestModel() {
        this(false);
    }

    /**
     * Creates a DocumentModel to be used for merging Android manifest documents
     *
     * @param autoReject specifies whether model can ignore conflicts in attribute values when
     *     merging manifest documents and simply reject value from the lower priority document
     */
    public ManifestModel(boolean autoReject) {
        this.autoReject = autoReject;
    }

    /**
     * Implementation of {@link NodeKeyResolver} that do not provide any key (the element has to be
     * unique in the xml document).
     */
    private static class NoKeyNodeResolver implements NodeKeyResolver {

        @Override
        @Nullable
        public String getKey(@NonNull Element element) {
            return null;
        }

        @NonNull
        @Override
        public ImmutableList<String> getKeyAttributesNames() {
            return ImmutableList.of();
        }
    }

    /**
     * Implementation of {@link NodeKeyResolver} that uses an attribute to resolve the key value.
     */
    private static class AttributeBasedNodeKeyResolver implements NodeKeyResolver {

        @Nullable private final String mNamespaceUri;
        private final String mAttributeName;

        /**
         * Build a new instance capable of resolving an xml element key from the passed attribute
         * namespace and local name.
         * @param namespaceUri optional namespace for the attribute name.
         * @param attributeName attribute name
         */
        private AttributeBasedNodeKeyResolver(@Nullable String namespaceUri,
                @NonNull String attributeName) {
            this.mNamespaceUri = namespaceUri;
            this.mAttributeName = Preconditions.checkNotNull(attributeName);
        }

        @Override
        @Nullable
        public String getKey(@NonNull Element element) {
            String key =
                    mNamespaceUri == null
                            ? element.getAttribute(mAttributeName)
                            : element.getAttributeNS(mNamespaceUri, mAttributeName);
            if (Strings.isNullOrEmpty(key)) return null;

            // Resolve unqualified names
            if (key.startsWith(".") && ATTR_NAME.equals(mAttributeName) &&
                    ANDROID_URI.equals(mNamespaceUri)) {
                Document document = element.getOwnerDocument();
                if (document != null) {
                    Element root = document.getDocumentElement();
                    if (root != null) {
                        String pkg = root.getAttribute(ATTR_PACKAGE);
                        if (!pkg.isEmpty()) {
                            key = pkg + key;
                        }
                    }
                }
            }

            return key;
        }

        @NonNull
        @Override
        public ImmutableList<String> getKeyAttributesNames() {
            return ImmutableList.of(mAttributeName);
        }
    }

    /**
     * Subclass of {@link com.android.manifmerger.ManifestModel.AttributeBasedNodeKeyResolver} that
     * uses "android:name" as the attribute.
     */
    private static final NodeKeyResolver DEFAULT_NAME_ATTRIBUTE_RESOLVER =
            new AttributeBasedNodeKeyResolver(ANDROID_URI, SdkConstants.ATTR_NAME);

    private static final NoKeyNodeResolver DEFAULT_NO_KEY_NODE_RESOLVER = new NoKeyNodeResolver();

    private static final NodeKeyResolver PROVIDER_KEY_RESOLVER =
            new NodeKeyResolver() {
                @Override
                public ImmutableList<String> getKeyAttributesNames() {
                    return ImmutableList.of();
                }

                @Override
                public String getKey(Element element) {
                    // if the provider is a sub-element from queries, we are not expecting any key.
                    if (element.getParentNode()
                            .getNodeName()
                            .equals(ManifestModel.NodeTypes.QUERIES.name())) {
                        return null;
                    }
                    return DEFAULT_NAME_ATTRIBUTE_RESOLVER.getKey(element);
                }
            };

    /**
     * A {@link NodeKeyResolver} capable of extracting the element key first in an "android:name"
     * attribute and if not value found there, in the "android:glEsVersion" attribute.
     */
    @Nullable
    private static final NodeKeyResolver NAME_AND_GLESVERSION_KEY_RESOLVER =
            new NodeKeyResolver() {
                private final NodeKeyResolver nameAttrResolver = DEFAULT_NAME_ATTRIBUTE_RESOLVER;
                private final NodeKeyResolver glEsVersionResolver =
                        new AttributeBasedNodeKeyResolver(
                                ANDROID_URI, AndroidManifest.ATTRIBUTE_GLESVERSION);

                @Nullable
                @Override
                public String getKey(@NonNull Element element) {
                    @Nullable String key = nameAttrResolver.getKey(element);
                    return Strings.isNullOrEmpty(key) ? glEsVersionResolver.getKey(element) : key;
                }

                @NonNull
                @Override
                public ImmutableList<String> getKeyAttributesNames() {
                    return ImmutableList.of(
                            SdkConstants.ATTR_NAME, AndroidManifest.ATTRIBUTE_GLESVERSION);
                }
            };
;

    /**
     * Implementation of {@link NodeKeyResolver} that combined two attributes values to create the
     * key value.
     */
    private static final class TwoAttributesBasedKeyResolver implements NodeKeyResolver {
        private final NodeKeyResolver firstAttributeKeyResolver;
        private final NodeKeyResolver secondAttributeKeyResolver;

        private TwoAttributesBasedKeyResolver(NodeKeyResolver firstAttributeKeyResolver,
                NodeKeyResolver secondAttributeKeyResolver) {
            this.firstAttributeKeyResolver = firstAttributeKeyResolver;
            this.secondAttributeKeyResolver = secondAttributeKeyResolver;
        }

        @Nullable
        @Override
        public String getKey(@NonNull Element element) {
            @Nullable String firstKey = firstAttributeKeyResolver.getKey(element);
            @Nullable String secondKey = secondAttributeKeyResolver.getKey(element);

            return Strings.isNullOrEmpty(firstKey)
                    ? secondKey
                    : Strings.isNullOrEmpty(secondKey)
                            ? firstKey
                            : firstKey + "+" + secondKey;
        }

        @NonNull
        @Override
        public ImmutableList<String> getKeyAttributesNames() {
            return ImmutableList.of(firstAttributeKeyResolver.getKeyAttributesNames().get(0),
                    secondAttributeKeyResolver.getKeyAttributesNames().get(0));
        }
    }

    private static final AttributeModel.BooleanValidator BOOLEAN_VALIDATOR =
            new AttributeModel.BooleanValidator();

    private static final boolean MULTIPLE_DECLARATION_FOR_SAME_KEY_ALLOWED = true;

    /**
     * Definitions of the support node types in the Android Manifest file. {@link <a
     * href=http://developer.android.com/guide/topics/manifest/manifest-intro.html>} for more
     * details about the xml format.
     *
     * <p>There is no DTD or schema associated with the file type so this is best effort in
     * providing some metadata on the elements of the Android's xml file.
     *
     * <p>Each xml element is defined as an enum value and for each node, extra metadata is added
     *
     * <ul>
     *   <li>{@link com.android.manifmerger.MergeType} to identify how the merging engine should
     *       process this element.
     *   <li>{@link NodeKeyResolver} to resolve the element's key. Elements can have an attribute
     *       like "android:name", others can use a sub-element, and finally some do not have a key
     *       and are meant to be unique.
     *   <li>List of attributes models with special behaviors :
     *       <ul>
     *         <li>Smart substitution of class names to fully qualified class names using the
     *             document's package declaration. The list's size can be 0..n
     *         <li>Implicit default value when no defined on the xml element.
     *         <li>{@link AttributeModel.Validator} to validate attribute value against.
     *       </ul>
     * </ul>
     *
     * It is of the outermost importance to keep this model correct as it is used by the merging
     * engine to make all its decisions. There should not be special casing in the engine, all
     * decisions must be represented here.
     *
     * <p>If you find yourself needing to extend the model to support future requirements, do it
     * here and modify the engine to make proper decision based on the added metadata.
     */
    enum NodeTypes {

        /**
         * Action (contained in intent-filter, intent) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/action-element.html>Action Xml
         * documentation</a>}
         */
        ACTION(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Activity (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-element.html>
         *     Activity Xml documentation</a>}
         */
        ACTIVITY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel("parentActivityName").setIsPackageDependent(),
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent()),

        /**
         * Activity-alias (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/activity-alias-element.html>
         *     Activity-alias Xml documentation</a>}
         */
        ACTIVITY_ALIAS(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel("targetActivity").setIsPackageDependent(),
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent()),

        /**
         * Application (contained in manifest) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/application-element.html>
         * Application Xml documentation</a>}
         */
        APPLICATION(
                MergeType.MERGE,
                DEFAULT_NO_KEY_NODE_RESOLVER,
                AttributeModel.newModel("backupAgent").setIsPackageDependent(),
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent(),
                AttributeModel.newModel(SdkConstants.ATTR_HAS_CODE)
                        .setMergingPolicy(
                                new AttributeModel.MergingPolicy() {
                                    @Override
                                    public boolean shouldMergeDefaultValues() {
                                        return false;
                                    }

                                    @Override
                                    public boolean canMergeWithLowerPriority(
                                            @NonNull XmlDocument document) {
                                        return EnumSet.of(Type.MAIN, Type.OVERLAY)
                                                .contains(document.getFileType());
                                    }

                                    @Nullable
                                    @Override
                                    public String merge(
                                            @NonNull String higherPriority,
                                            @NonNull String lowerPriority) {
                                        return OR_MERGING_POLICY.merge(
                                                higherPriority, lowerPriority);
                                    }
                                })),

        /**
         * Category (contained in intent-filter, intent) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/category-element.html>Category
         * Xml documentation</a>}
         */
        CATEGORY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Compatible-screens (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/compatible-screens-element.html>
         *     Category Xml documentation</a>}
         */
        COMPATIBLE_SCREENS(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Data (contained in intent-filter, intent) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/data-element.html>Category Xml
         * documentation</a>}
         */
        DATA(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Grant-uri-permission (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/grant-uri-permission-element.html>
         *     Category Xml documentation</a>}
         */
        GRANT_URI_PERMISSION(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Instrumentation (contained in intent-filter)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/instrumentation-element.html>
         *     Instrunentation Xml documentation</a>}
         */
        INSTRUMENTATION(
                MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER,
                AttributeModel.newModel("name").setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                AttributeModel.newModel("targetPackage")
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                AttributeModel.newModel("functionalTest")
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                AttributeModel.newModel("handleProfiling")
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                AttributeModel.newModel("label").setMergingPolicy(AttributeModel.NO_MERGING_POLICY)
        ),

        /**
         * Intent (contained in queries) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/intent.html>Intent Xml
         * documentation</a>}
         */
        INTENT(
                MergeType.ALWAYS,
                IntentNodeKeyResolver.INSTANCE,
                MULTIPLE_DECLARATION_FOR_SAME_KEY_ALLOWED),

        /**
         * Intent-filter (contained in activity, activity-alias, service, receiver) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/intent-filter-element.html>
         * Intent-filter Xml documentation</a>}
         */
        INTENT_FILTER(
                MergeType.ALWAYS,
                IntentFilterNodeKeyResolver.INSTANCE,
                MULTIPLE_DECLARATION_FOR_SAME_KEY_ALLOWED),

        /**
         * Manifest (top level node)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/manifest-element.html>
         *     Manifest Xml documentation</a>}
         */
        MANIFEST(MergeType.MERGE_CHILDREN_ONLY, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Meta-data (contained in activity, activity-alias, application, provider, receiver)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/meta-data-element.html>
         *     Meta-data Xml documentation</a>}
         */
        META_DATA(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /** Module node for bundle */
        MODULE(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER, EnumSet.of(Type.MAIN, Type.OVERLAY)),

        /** Nav-graph (contained in activity), expanded into intent-filter by manifest merger */
        NAV_GRAPH(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /** App enumeration tags declaration (contained in manifest) */
        PACKAGE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Path-permission (contained in provider) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/path-permission-element.html>
         * Path-permission Xml documentation</a>}
         */
        PATH_PERMISSION(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Permission-group (contained in manifest).
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/permission-group-element.html>
         *     Permission-group Xml documentation</a>}
         *
         */
        PERMISSION_GROUP(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME)),

        /**
         * Permission (contained in manifest). <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/permission-element.html>
         * Permission Xml documentation</a>}
         */
        PERMISSION(
                MergeType.MERGE,
                DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME),
                AttributeModel.newModel("protectionLevel")
                        .setDefaultValue("normal")
                        .setOnReadValidator(
                                new SeparatedValuesValidator(
                                        SdkConstants.VALUE_DELIMITER_PIPE,
                                        "normal",
                                        "dangerous",
                                        "signature",
                                        "signatureOrSystem",
                                        "privileged",
                                        "system",
                                        "development",
                                        "appop",
                                        "pre23",
                                        "installer",
                                        "verifier",
                                        "preinstalled",
                                        "setup",
                                        "ephemeral",
                                        "instant",
                                        "runtime",
                                        "oem",
                                        "vendorPrivileged",
                                        "textClassifier",
                                        "wellbeing",
                                        "documenter",
                                        "configurator",
                                        "incidentReportApprover",
                                        "appPredictor",
                                        "companion",
                                        "retailDemo"))),

        /**
         * Permission-tree (contained in manifest).
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/permission-tree-element.html>
         *     Permission-tree Xml documentation</a>}
         *
         */
        PERMISSION_TREE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME)),

        /**
         * Provider (contained in application or queries) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/provider-element.html>Provider
         * Xml documentation</a>}
         */
        PROVIDER(
                MergeType.MERGE,
                PROVIDER_KEY_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent()),

        /**
         * Queries <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/queries-element.html>Queries Xml
         * documentation</a>}
         */
        QUERIES(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Receiver (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/receiver-element.html>
         *     Receiver Xml documentation</a>}
         */
        RECEIVER(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent()),

        /**
         * Screen (contained in compatible-screens)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/compatible-screens-element.html>
         *     Receiver Xml documentation</a>}
         */
        SCREEN(MergeType.MERGE, new TwoAttributesBasedKeyResolver(
                new AttributeBasedNodeKeyResolver(ANDROID_URI, "screenSize"),
                new AttributeBasedNodeKeyResolver(ANDROID_URI, "screenDensity"))),

        /**
         * Service (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/application-element.html>
         *     Service Xml documentation</a>}
         */
        SERVICE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(SdkConstants.ATTR_NAME).setIsPackageDependent()),

        /**
         * Supports-gl-texture (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/supports-gl-texture-element.html>
         *     Support-screens Xml documentation</a>}
         */
        SUPPORTS_GL_TEXTURE(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Support-screens (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/supports-screens-element.html>
         *     Support-screens Xml documentation</a>}
         */
        SUPPORTS_SCREENS(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Uses-configuration (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-configuration-element.html>
         *     Support-screens Xml documentation</a>}
         */
        USES_CONFIGURATION(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER),

        /**
         * Uses-feature (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-feature-element.html>
         *     Uses-feature Xml documentation</a>}
         */
        USES_FEATURE(MergeType.MERGE, NAME_AND_GLESVERSION_KEY_RESOLVER,
                AttributeModel.newModel(AndroidManifest.ATTRIBUTE_REQUIRED)
                        .setDefaultValue(SdkConstants.VALUE_TRUE)
                        .setOnReadValidator(BOOLEAN_VALIDATOR)
                        .setMergingPolicy(AttributeModel.OR_MERGING_POLICY),
                AttributeModel.newModel(AndroidManifest.ATTRIBUTE_GLESVERSION)
                        .setDefaultValue("0x00010000")
                        .setOnReadValidator(new Hexadecimal32BitsWithMinimumValue(0x00010000))),

        /**
         * Use-library (contained in application)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-library-element.html>
         *     Use-library Xml documentation</a>}
         */
        USES_LIBRARY(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER,
                AttributeModel.newModel(AndroidManifest.ATTRIBUTE_REQUIRED)
                        .setDefaultValue(SdkConstants.VALUE_TRUE)
                        .setOnReadValidator(BOOLEAN_VALIDATOR)
                        .setMergingPolicy(AttributeModel.OR_MERGING_POLICY)),

        /**
         * Uses-permission (contained in manifest) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/uses-permission-element.html>
         * Uses-permission Xml documentation</a>}
         */
        USES_PERMISSION(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Uses-permission-sdk-23 (contained in manifest) <br>
         * <b>See also : </b> {@link <a
         * href=http://developer.android.com/guide/topics/manifest/uses-permission-sdk-23-element.html>
         * Uses-permission Xml documentation</a>}
         */
        USES_PERMISSION_SDK_23(MergeType.MERGE, DEFAULT_NAME_ATTRIBUTE_RESOLVER),

        /**
         * Uses-sdk (contained in manifest)
         * <br>
         * <b>See also : </b>
         * {@link <a href=http://developer.android.com/guide/topics/manifest/uses-sdk-element.html>
         *     Uses-sdk Xml documentation</a>}
         */
        USES_SDK(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER,
                AttributeModel.newModel("minSdkVersion")
                        .setDefaultValue(SdkConstants.VALUE_1)
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                AttributeModel.newModel("maxSdkVersion")
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY),
                // TODO : model target's default value is minSdkVersion value.
                AttributeModel.newModel("targetSdkVersion")
                        .setMergingPolicy(AttributeModel.NO_MERGING_POLICY)
        ),

        /**
         * Custom tag for any application specific element
         */
        CUSTOM(MergeType.MERGE, DEFAULT_NO_KEY_NODE_RESOLVER);

        private final MergeType mMergeType;
        private final NodeKeyResolver mNodeKeyResolver;
        private final ImmutableList<AttributeModel> mAttributeModels;
        private final boolean mMultipleDeclarationAllowed;
        private final EnumSet<XmlDocument.Type> mMergeableLowerPriorityTypes;

        NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                @Nullable AttributeModel.Builder... attributeModelBuilders) {
            this(
                    mergeType,
                    nodeKeyResolver,
                    false,
                    EnumSet.allOf(XmlDocument.Type.class),
                    attributeModelBuilders);
        }

        NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                boolean multipleDeclarationAllowed,
                @Nullable AttributeModel.Builder... attributeModelBuilders) {
            this(
                    mergeType,
                    nodeKeyResolver,
                    multipleDeclarationAllowed,
                    EnumSet.allOf(XmlDocument.Type.class),
                    attributeModelBuilders);
        }

        NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                @NonNull EnumSet<XmlDocument.Type> mergeableLowerPriorityTypes,
                @Nullable AttributeModel.Builder... attributeModelBuilders) {
            this(
                    mergeType,
                    nodeKeyResolver,
                    false,
                    mergeableLowerPriorityTypes,
                    attributeModelBuilders);
        }

        NodeTypes(
                @NonNull MergeType mergeType,
                @NonNull NodeKeyResolver nodeKeyResolver,
                boolean mutipleDeclarationAllowed,
                @NonNull EnumSet<XmlDocument.Type> mergeableLowerPriorityTypes,
                @Nullable AttributeModel.Builder... attributeModelBuilders) {
            this.mMergeType = Preconditions.checkNotNull(mergeType);
            this.mNodeKeyResolver = Preconditions.checkNotNull(nodeKeyResolver);
            @NonNull ImmutableList.Builder<AttributeModel> attributeModels =
                    new ImmutableList.Builder<AttributeModel>();
            if (attributeModelBuilders != null) {
                for (AttributeModel.Builder attributeModelBuilder : attributeModelBuilders) {
                    attributeModels.add(attributeModelBuilder.build());
                }
            }
            this.mAttributeModels = attributeModels.build();
            this.mMultipleDeclarationAllowed = mutipleDeclarationAllowed;
            this.mMergeableLowerPriorityTypes = mergeableLowerPriorityTypes;
        }

        @NonNull
        NodeKeyResolver getNodeKeyResolver() {
            return mNodeKeyResolver;
        }

        ImmutableList<AttributeModel> getAttributeModels() {
            return mAttributeModels.asList();
        }

        @Nullable
        AttributeModel getAttributeModel(XmlNode.NodeName attributeName) {
            // mAttributeModels could be replaced with a Map if the number of models grows.
            for (AttributeModel attributeModel : mAttributeModels) {
                if (attributeModel.getName().equals(attributeName)) {
                    return attributeModel;
                }
            }
            return null;
        }

        MergeType getMergeType() {
            return mMergeType;
        }

        /**
         * Returns true if multiple declaration for the same type and key are allowed or false if
         * there must be only one declaration of this element for a particular key value.
         */
        boolean areMultipleDeclarationAllowed() {
            return mMultipleDeclarationAllowed;
        }

        /**
         * Returns if XmlElement with this NodeTypes can be merged from lower priority XmlElement
         */
        boolean canMergeWithLowerPriority(@NonNull XmlElement xmlElement) {
            return mMergeableLowerPriorityTypes.contains(xmlElement.getDocument().getFileType());
        }

    }

    /** Returns the Xml name for this node type */
    @Override
    public String toXmlName(@NonNull NodeTypes type) {
        return SdkUtils.constantNameToXmlName(type.name());
    }

    /**
     * Returns the {@link NodeTypes} instance from an xml element name (without namespace
     * decoration). For instance, an xml element
     *
     * <pre>{@code
     * <activity android:name="foo">
     *     ...
     * </activity>
     * }</pre>
     *
     * has a xml simple name of "activity" which will resolve to {@link NodeTypes#ACTIVITY} value.
     *
     * <p>Note : a runtime exception will be generated if no mapping from the simple name to a
     * {@link com.android.manifmerger.ManifestModel.NodeTypes} exists.
     *
     * @param xmlSimpleName the xml (lower-hyphen separated words) simple name.
     * @return the {@link NodeTypes} associated with that element name.
     */
    @Override
    public NodeTypes fromXmlSimpleName(String xmlSimpleName) {
        String constantName = SdkUtils.xmlNameToConstantName(xmlSimpleName);

        try {
            return NodeTypes.valueOf(constantName);
        } catch (IllegalArgumentException e) {
            // if this element name is not a known tag, we categorize it as 'custom' which will
            // be simply merged. It will prevent us from catching simple spelling mistakes but
            // extensibility is a must have feature.
            return NodeTypes.CUSTOM;
        }
    }

    @Override
    public boolean autoRejectConflicts() {
        return autoReject;
    }
}
