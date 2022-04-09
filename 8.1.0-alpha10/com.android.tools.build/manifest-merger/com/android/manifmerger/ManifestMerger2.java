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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_SPLIT;
import static com.android.manifmerger.ManifestModel.NodeTypes.USES_SDK;
import static com.android.manifmerger.MergingReport.MergedManifestKind.AAPT_SAFE;
import static com.android.manifmerger.PlaceholderHandler.APPLICATION_ID;
import static com.android.manifmerger.PlaceholderHandler.KeyBasedValueResolver;
import static com.android.manifmerger.PlaceholderHandler.PACKAGE_NAME;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * merges android manifest files, idempotent.
 */
@Immutable
public class ManifestMerger2 {

    public static final String COMPATIBLE_SCREENS_SUB_MANIFEST = "Compatible-Screens sub-manifest";
    public static final String WEAR_APP_SUB_MANIFEST = "Wear App sub-manifest";

    private static final String SPLIT_IN_DYNAMIC_FEATURE =
            "https://d.android.com/r/studio-ui/dynamic-delivery/dynamic-feature-manifest";

    @NonNull
    private final File mManifestFile;

    @NonNull
    private final Map<String, Object> mPlaceHolderValues;

    @NonNull
    private final KeyBasedValueResolver<ManifestSystemProperty>
            mSystemPropertyResolver;

    @NonNull
    private final ILogger mLogger;
    @NonNull
    private final ImmutableList<Pair<String, File>> mLibraryFiles;
    @NonNull
    private final ImmutableList<File> mFlavorsAndBuildTypeFiles;
    @NonNull
    private final ImmutableList<Invoker.Feature> mOptionalFeatures;
    @NonNull
    private final MergeType mMergeType;
    @NonNull
    private final XmlDocument.Type mDocumentType;
    @NonNull
    private final Optional<File> mReportFile;
    @NonNull private final String mFeatureName;
    @Nullable private final String mNamespace;
    @NonNull private final FileStreamProvider mFileStreamProvider;
    @Nullable private final ManifestDocumentProvider mManifestDocumentProvider;
    @NonNull private final ProcessCancellationChecker mProcessCancellationChecker;
    @NonNull private final ImmutableList<File> mNavigationFiles;
    @NonNull private final ImmutableList<File> mNavigationJsons;
    @NonNull private final DocumentModel<ManifestModel.NodeTypes> mModel;
    @NonNull private final ImmutableList<String> mDependencyFeatureNames;
    @NonNull private final ImmutableList<String> mAllowedNonUniqueNamespaces;
    @Nullable private final String mGeneratedLocaleConfigAttribute;

    private ManifestMerger2(
            @NonNull ILogger logger,
            @NonNull File mainManifestFile,
            @NonNull ImmutableList<Pair<String, File>> libraryFiles,
            @NonNull ImmutableList<File> flavorsAndBuildTypeFiles,
            @NonNull ImmutableList<Invoker.Feature> optionalFeatures,
            @NonNull Map<String, Object> placeHolderValues,
            @NonNull KeyBasedValueResolver<ManifestSystemProperty> systemPropertiesResolver,
            @NonNull MergeType mergeType,
            @NonNull XmlDocument.Type documentType,
            @NonNull Optional<File> reportFile,
            @NonNull String featureName,
            @Nullable String namespace,
            @NonNull FileStreamProvider fileStreamProvider,
            @Nullable ManifestDocumentProvider manifestDocumentProvider,
            @NonNull ProcessCancellationChecker processCancellationChecker,
            @NonNull ImmutableList<File> navigationFiles,
            @NonNull ImmutableList<File> navigationJsons,
            @NonNull ImmutableList<String> dependencyFeatureNames,
            @NonNull ImmutableList<String> allowedNonUniqueNamespaces,
            @Nullable String generatedLocaleConfigAttribute) {
        this.mSystemPropertyResolver = systemPropertiesResolver;
        this.mPlaceHolderValues = placeHolderValues;
        this.mManifestFile = mainManifestFile;
        this.mLogger = logger;
        this.mLibraryFiles = libraryFiles;
        this.mFlavorsAndBuildTypeFiles = flavorsAndBuildTypeFiles;
        this.mOptionalFeatures = optionalFeatures;
        this.mMergeType = mergeType;
        this.mDocumentType = documentType;
        this.mReportFile = reportFile;
        this.mFeatureName = featureName;
        this.mNamespace = namespace;
        this.mFileStreamProvider = fileStreamProvider;
        this.mManifestDocumentProvider = manifestDocumentProvider;
        this.mProcessCancellationChecker = processCancellationChecker;
        this.mNavigationFiles = navigationFiles;
        this.mNavigationJsons = navigationJsons;
        this.mDependencyFeatureNames = dependencyFeatureNames;
        this.mModel =
                new ManifestModel(
                        mOptionalFeatures.contains(
                                Invoker.Feature.HANDLE_VALUE_CONFLICTS_AUTOMATICALLY));
        this.mAllowedNonUniqueNamespaces = allowedNonUniqueNamespaces;
        this.mGeneratedLocaleConfigAttribute = generatedLocaleConfigAttribute;
    }

    /**
     * Perform high level ordering of files merging and delegates actual merging to {@link
     * XmlDocument#merge}
     *
     * @return the merging activity report.
     * @throws MergeFailureException if the merging cannot be completed (for instance, if xml files
     *     cannot be loaded).
     */
    @NonNull
    private MergingReport merge() throws MergeFailureException {
        // initiate a new merging report
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger);

        SelectorResolver selectors = new SelectorResolver();

        // load the main manifest file to do some checking along the way.
        LoadedManifestInfo loadedMainManifestInfo =
                load(
                        new ManifestInfo(mManifestFile.getName(), mManifestFile, mDocumentType),
                        selectors,
                        mergingReportBuilder,
                        mNamespace);

        mProcessCancellationChecker.check();

        // perform all top-level verifications.
        if (!loadedMainManifestInfo
                        .getXmlDocument()
                        .checkTopLevelDeclarations(
                                mPlaceHolderValues, mergingReportBuilder, mDocumentType)
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            return mergingReportBuilder.build();
        }

        if (!mFeatureName.isEmpty()) {
            loadedMainManifestInfo =
                    removeDynamicFeatureManifestSplitAttributeIfSpecified(
                            loadedMainManifestInfo, mergingReportBuilder);
        }

        Optional<XmlAttribute> mainPackageAttribute =
                loadedMainManifestInfo.getXmlDocument().getPackage();
        final String originalMainManifestPackageName =
                mainPackageAttribute.map(XmlAttribute::getValue).orElse(null);

        if (mOptionalFeatures.contains(Invoker.Feature.CHECK_IF_PACKAGE_IN_MAIN_MANIFEST)
                && originalMainManifestPackageName != null) {
            if (originalMainManifestPackageName.equals(mNamespace)) {
                String message =
                        String.format(
                                "package=\"%1$s\" found in source AndroidManifest.xml: %2$s.\n"
                                        + "Setting the namespace via the package attribute in the "
                                        + "source AndroidManifest.xml is no longer supported, "
                                        + "and the value is ignored.\n"
                                        + "Recommendation: remove package=\"%1$s\" from the source "
                                        + "AndroidManifest.xml: %2$s.",
                                originalMainManifestPackageName,
                                loadedMainManifestInfo.getLocation().getAbsolutePath());
                mLogger.warning(message);
            } else {
                String message =
                        String.format(
                                "Incorrect package=\"%1$s\" found in source AndroidManifest.xml: "
                                        + "%2$s.\n"
                                        + "Setting the namespace via the package attribute in the "
                                        + "source AndroidManifest.xml is no longer supported.\n"
                                        + "Recommendation: remove package=\"%1$s\" from the source "
                                        + "AndroidManifest.xml: %2$s.",
                                originalMainManifestPackageName,
                                loadedMainManifestInfo.getLocation().getAbsolutePath());
                mLogger.error(null, message);
                throw new RuntimeException(message);
            }
        }

        mProcessCancellationChecker.check();

        // load all the libraries xml files early to have a list of all possible node:selector
        // values.
        List<LoadedManifestInfo> loadedLibraryDocuments =
                loadLibraries(selectors, mergingReportBuilder, originalMainManifestPackageName);

        // make sure each module/library has a unique namespace
        boolean enforceUniquePackageName =
                mOptionalFeatures.contains(Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME);
        boolean disablePackageUniquenessCheck =
                mOptionalFeatures.contains(Invoker.Feature.DISABLE_PACKAGE_NAME_UNIQUENESS_CHECK);
        if (enforceUniquePackageName && disablePackageUniquenessCheck) {
            mLogger.warning(
                    "Both flags ENFORCE_UNIQUE_PACKAGE_NAME and"
                            + " DISABLE_PACKAGE_NAME_UNIQUENESS_CHECK are found to be true."
                            + " Flag ENFORCE_UNIQUE_PACKAGE_NAME will be ignored because package"
                            + " name uniqueness check is disabled.");
        }
        if (!disablePackageUniquenessCheck) {
            checkUniqueNamespaces(
                    loadedMainManifestInfo,
                    loadedLibraryDocuments,
                    mAllowedNonUniqueNamespaces,
                    mergingReportBuilder,
                    enforceUniquePackageName);
        }
        // perform system property injection
        performSystemPropertiesInjection(mergingReportBuilder,
                loadedMainManifestInfo.getXmlDocument());

        // invariant : xmlDocumentOptional holds the higher priority document and we try to
        // merge in lower priority documents.
        @Nullable XmlDocument xmlDocumentOptional = null;
        for (File inputFile : mFlavorsAndBuildTypeFiles) {
            mProcessCancellationChecker.check();
            mLogger.verbose("Merging flavors and build manifest %s \n", inputFile.getPath());
            LoadedManifestInfo overlayDocument =
                    load(
                            new ManifestInfo(null, inputFile, XmlDocument.Type.OVERLAY),
                            selectors,
                            mergingReportBuilder,
                            mNamespace);

            if (!mFeatureName.isEmpty()) {
                overlayDocument =
                        removeDynamicFeatureManifestSplitAttributeIfSpecified(
                                overlayDocument, mergingReportBuilder);
            }

            // check package declaration.
            Optional<XmlAttribute> packageAttribute =
                    overlayDocument.getXmlDocument().getPackage();
            // if overlay file declares a package name, it should be the same as the namespace
            if (!Strings.isNullOrEmpty(loadedMainManifestInfo.getNamespace())
                    && packageAttribute.isPresent()
                    && !loadedMainManifestInfo
                            .getNamespace()
                            .equals(packageAttribute.get().getValue())) {
                // no suggestion for library since this is actually forbidden to change the
                // the package name per flavor.
                String message =
                        mMergeType == MergeType.APPLICATION
                                ? String.format(
                                        "Overlay manifest:package attribute declared at %1$s value=(%2$s)\n"
                                                + "\tdoes not match the module's namespace (%3$s).\n"
                                                + "\tSuggestion: remove the package=\"%2$s\" declaration at %4$s.\n"
                                                + "\tIf you want to customize the applicationId for a "
                                                + "buildType or productFlavor, consider setting "
                                                + "applicationIdSuffix or using the Variant API.\n"
                                                + "\tFor more information, see "
                                                + "https://developer.android.com/studio/build/build-variants",
                                        packageAttribute.get().printPosition(),
                                        packageAttribute.get().getValue(),
                                        loadedMainManifestInfo.getNamespace(),
                                        packageAttribute.get().getSourceFile().print(false))
                                : String.format(
                                        "Overlay manifest:package attribute declared at %1$s value=(%2$s)\n"
                                                + "\tdoes not match the module's namespace (%3$s).\n"
                                                + "\tSuggestion: remove the package=\"%2$s\" declaration at %4$s.",
                                        packageAttribute.get().printPosition(),
                                        packageAttribute.get().getValue(),
                                        loadedMainManifestInfo.getNamespace(),
                                        packageAttribute.get().getSourceFile().print(false));
                mergingReportBuilder.addMessage(
                        overlayDocument.getXmlDocument().getSourceFile(),
                        MergingReport.Record.Severity.ERROR,
                        message);
                if (!mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
                    return mergingReportBuilder.build();
                }
            }

            if (mainPackageAttribute.isPresent()) {
                overlayDocument
                        .getXmlDocument()
                        .getRootNode()
                        .setAttribute("package", mainPackageAttribute.get().getValue());
            }
            Optional<XmlDocument> newMergedDocument =
                    merge(xmlDocumentOptional, overlayDocument, mergingReportBuilder);

            xmlDocumentOptional = newMergedDocument.orElse(null);

            if (!newMergedDocument.isPresent()
                    && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
                return mergingReportBuilder.build();
            }
        }

        mProcessCancellationChecker.check();

        mLogger.verbose("Merging main manifest %s\n", mManifestFile.getPath());
        Optional<XmlDocument> newMergedDocument =
                merge(xmlDocumentOptional, loadedMainManifestInfo, mergingReportBuilder);

        if (!newMergedDocument.isPresent()
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            return mergingReportBuilder.build();
        }
        xmlDocumentOptional = newMergedDocument.get();

        // force main manifest package into resulting merged file when creating a library manifest.
        if (mMergeType == MergeType.LIBRARY) {
            // extract the package name...
            String mainManifestPackageName = loadedMainManifestInfo.getXmlDocument().getRootNode()
                    .getXml().getAttribute("package");
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(mainManifestPackageName)) {
                xmlDocumentOptional
                        .getRootNode()
                        .setAttribute("package", mainManifestPackageName);
            }
        }
        for (LoadedManifestInfo libraryDocument : loadedLibraryDocuments) {
            mLogger.verbose("Merging library manifest " + libraryDocument.getLocation());
            newMergedDocument = merge(xmlDocumentOptional, libraryDocument, mergingReportBuilder);
            if (!newMergedDocument.isPresent()
                    && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
                return mergingReportBuilder.build();
            }
            xmlDocumentOptional = newMergedDocument.get();

            mProcessCancellationChecker.check();
        }

        // done with proper merging phase, now we need to expand <nav-graph> elements, trim unwanted
        // elements, perform placeholder substitution and system properties injection.

        if (mMergeType == MergeType.APPLICATION) {
            Map<String, NavigationXmlDocument> loadedNavigationMap = createNavigationMap();
            xmlDocumentOptional =
                    NavGraphExpander.INSTANCE.expandNavGraphs(
                            xmlDocumentOptional, loadedNavigationMap, mergingReportBuilder);
        }

        if (mergingReportBuilder.hasErrors()
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            return mergingReportBuilder.build();
        }

        ElementsTrimmer.trim(xmlDocumentOptional, mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            return mergingReportBuilder.build();
        }

        if (!mOptionalFeatures.contains(Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)) {
            // do one last placeholder substitution, this is useful as we don't stop the build
            // when a library failed a placeholder substitution, but the element might have
            // been overridden so the problem was transient. However, with the final document
            // ready, all placeholders values must have been provided.
            MergingReport.Record.Severity severity =
                    !mMergeType.isFullPlaceholderSubstitutionRequired()
                            ? MergingReport.Record.Severity.INFO
                            : MergingReport.Record.Severity.ERROR;
            performPlaceHolderSubstitution(
                    xmlDocumentOptional,
                    originalMainManifestPackageName,
                    mergingReportBuilder,
                    severity);
            if (mergingReportBuilder.hasErrors()
                    && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
                return mergingReportBuilder.build();
            }
        }

        mProcessCancellationChecker.check();

        // perform system property injection again.
        performSystemPropertiesInjection(mergingReportBuilder, xmlDocumentOptional);

        // if it's a library of any kind - need to remove targetSdk
        if (!mOptionalFeatures.contains(Invoker.Feature.DISABLE_STRIP_LIBRARY_TARGET_SDK)
                && mMergeType != MergeType.APPLICATION) {
            stripTargetSdk(xmlDocumentOptional);
        }

        XmlDocument finalMergedDocument = xmlDocumentOptional;

        Optional<XmlAttribute> packageAttr = finalMergedDocument.getPackage();
        // We allow single word package name for library and fused libraries, the platform does
        // not allow for single word application package names.
        if (mMergeType == MergeType.APPLICATION && packageAttr.isPresent()) {
            XmlAttribute packageNameAttribute = packageAttr.get();
            String packageName = packageNameAttribute.getValue();
            // We accept absence of dot only if NO_PLACEHOLDER_REPLACEMENT is true and packageName
            // is a placeholder
            if (!(mOptionalFeatures.contains(Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                            && PlaceholderHandler.isPlaceHolder(packageName))
                    && !packageName.contains(".")) {
                mergingReportBuilder.addMessage(
                        loadedMainManifestInfo.getXmlDocument().getSourceFile(),
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "Package name '%1$s' at position %2$s should contain at "
                                        + "least one '.' (dot) character",
                                packageName, packageNameAttribute.printPosition()));
                if (!mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
                    return mergingReportBuilder.build();
                }
            }
        }

        if (!mOptionalFeatures.contains(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)) {
            PostValidator.enforceToolsNamespaceDeclaration(finalMergedDocument);
        }

        mProcessCancellationChecker.check();

        // reset the node operations to their original ones if they get changed
        finalMergedDocument.originalNodeOperation.forEach(
                (k, v) -> {
                    k.setAttributeNS(SdkConstants.TOOLS_URI, "tools:node", v.toXmlName());
                });

        PostValidator.validate(finalMergedDocument, mergingReportBuilder);
        if (mergingReportBuilder.hasErrors()
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            mergingReportBuilder.addMessage(
                    finalMergedDocument.getRootNode(),
                    MergingReport.Record.Severity.WARNING,
                    "Post merge validation failed");
        }

        finalMergedDocument.clearNodeNamespaces();

        // extract fully qualified class names before handling other optional features.
        if (mOptionalFeatures.contains(Invoker.Feature.EXTRACT_FQCNS)) {
            @NonNull final String namespace;
            if (mNamespace != null) {
                namespace = mNamespace;
            } else if (originalMainManifestPackageName != null) {
                namespace = originalMainManifestPackageName;
            } else {
                namespace = "";
            }
            extractFqcns(namespace, finalMergedDocument.getRootNode());
        }

        mProcessCancellationChecker.check();

        // handle optional features which don't need access to XmlDocument layer.
        processOptionalFeatures(finalMergedDocument, mergingReportBuilder);

        // android:exported should have an explicit value for S and above with <intent-filter>,
        // output an error message to the user if android:exported is not explicitly specified
        checkExportedDeclaration(finalMergedDocument, mergingReportBuilder);

        if (mergingReportBuilder.hasErrors()
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            return mergingReportBuilder.build();
        }

        mergingReportBuilder.setMergedDocument(
                MergingReport.MergedManifestKind.MERGED, prettyPrint(finalMergedDocument.getXml()));

        mProcessCancellationChecker.check();

        // call blame after other optional features handled.
        if (!mOptionalFeatures.contains(Invoker.Feature.SKIP_BLAME)) {
            try {
                mergingReportBuilder.setMergedDocument(
                        MergingReport.MergedManifestKind.BLAME,
                        mergingReportBuilder.blame(finalMergedDocument));
            } catch (Exception e) {
                mLogger.error(e, "Error while saving blame file, build will continue");
            }
        }

        mergingReportBuilder.setMergedXmlDocument(finalMergedDocument);

        MergingReport mergingReport = mergingReportBuilder.build();

        if (mReportFile.isPresent()) {
            writeReport(mergingReport);
        }

        return mergingReport;
    }

    private Map<String, NavigationXmlDocument> createNavigationMap() throws MergeFailureException {
        Map<String, NavigationXmlDocument> loadedNavigationMap = new HashMap<>();
        for (File navigationFile : mNavigationFiles) {
            String navigationId = navigationFile.getName().replaceAll("\\.xml$", "");
            if (loadedNavigationMap.get(navigationId) != null) {
                continue;
            }
            try (InputStream inputStream = mFileStreamProvider.getInputStream(navigationFile)) {
                loadedNavigationMap.put(
                        navigationId,
                        NavigationXmlLoader.INSTANCE.load(
                                navigationId, navigationFile, inputStream));
            } catch (Exception e) {
                throw new MergeFailureException(e);
            }
        }
        Gson gson = new GsonBuilder().create();
        for (File navigationJson : mNavigationJsons) {
            try {
                String jsonText = FileUtils.loadFileWithUnixLineSeparators(navigationJson);
                NavigationXmlDocumentData[] navDatas =
                        gson.fromJson(jsonText, NavigationXmlDocumentData[].class);
                for (NavigationXmlDocumentData navData : navDatas) {
                    String navigationId = navData.getName();
                    if (loadedNavigationMap.get(navigationId) != null) {
                        mLogger.info(
                                "Navigation file %s from %s is ignored (skipped).",
                                navigationId, navigationJson);
                        continue;
                    }
                    loadedNavigationMap.put(navigationId, new NavigationXmlDocument(navData));
                }
            } catch (IOException e) {
                throw new MergeFailureException(e);
            }
        }
        return loadedNavigationMap;
    }

    private static LoadedManifestInfo removeDynamicFeatureManifestSplitAttributeIfSpecified(
            @NonNull LoadedManifestInfo dynamicFeatureManifest,
            @NonNull MergingReport.Builder mergingReportBuilder) {
        Optional<XmlAttribute> splitAttribute =
                dynamicFeatureManifest
                        .getXmlDocument()
                        .getRootNode()
                        .getAttribute(XmlNode.fromXmlName(ATTR_SPLIT));
        if (splitAttribute.isPresent()) {
            String message =
                    String.format(
                            "Attribute '%1$s' was removed from %2$s.\n"
                                    + "The Android Gradle plugin includes it for you "
                                    + "when building your project.\n"
                                    + "See %3$s for details.",
                            ATTR_SPLIT,
                            splitAttribute.get().printPosition(),
                            SPLIT_IN_DYNAMIC_FEATURE);
            mergingReportBuilder.addMessage(
                    dynamicFeatureManifest.getXmlDocument().getSourceFile(),
                    MergingReport.Record.Severity.WARNING,
                    message);
            dynamicFeatureManifest.getXmlDocument().getRootNode().removeAttribute(ATTR_SPLIT);
            return new LoadedManifestInfo(
                    dynamicFeatureManifest, dynamicFeatureManifest.getXmlDocument());
        }

        return dynamicFeatureManifest;
    }

    /**
     * Processes optional features which are not already handled in merge()
     *
     * @param xmlDocument the resulting document after merging
     * @param mergingReport the merging report builder
     */
    private void processOptionalFeatures(
            @Nullable XmlDocument xmlDocument, @NonNull MergingReport.Builder mergingReport)
            throws MergeFailureException {
        Document document = Optional.ofNullable(xmlDocument).map(XmlDocument::getXml).orElse(null);
        if (document == null) {
            return;
        }

        // perform tools: annotations removal if requested.
        if (mMergeType != MergeType.FUSED_LIBRARY
                && mOptionalFeatures.contains(Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)) {
            ToolsInstructionsCleaner.cleanToolsReferences(mMergeType, xmlDocument, mLogger);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.ADVANCED_PROFILING)) {
            addInternetPermission(xmlDocument);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.TEST_ONLY)) {
            addTestOnlyAttribute(xmlDocument);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.DEBUGGABLE)) {
            addDebuggableAttribute(xmlDocument);
        }

        if (mMergeType == MergeType.APPLICATION) {
            optionalAddApplicationTagIfMissing(xmlDocument);
        }

        if (mMergeType == MergeType.APPLICATION
                && mOptionalFeatures.contains(Invoker.Feature.DO_NOT_EXTRACT_NATIVE_LIBS)) {
            maybeAddExtractNativeLibAttribute(xmlDocument);
        }

        if (mOptionalFeatures.contains(
                Invoker.Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME)) {
            addMultiDexApplicationIfNoName(
                    xmlDocument, AndroidXConstants.MULTI_DEX_APPLICATION.newName());
        } else if (mOptionalFeatures.contains(
                Invoker.Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME)) {
            addMultiDexApplicationIfNoName(
                    xmlDocument, AndroidXConstants.MULTI_DEX_APPLICATION.oldName());
        }

        if (mOptionalFeatures.contains(Invoker.Feature.ADD_DYNAMIC_FEATURE_ATTRIBUTES)) {
            addFeatureSplitAttribute(xmlDocument, mFeatureName);
            adjustInstantAppFeatureSplitInfo(xmlDocument, mFeatureName);
            addUsesSplitTagsForDependencies(xmlDocument, mDependencyFeatureNames);
        }

        if (mOptionalFeatures.contains(Invoker.Feature.MAKE_AAPT_SAFE)) {
            createAaptSafeManifest(xmlDocument, mergingReport);
        }

        // If generated locale config is present set it if user locale config is not present.
        if (mMergeType == MergeType.APPLICATION && mGeneratedLocaleConfigAttribute != null) {
            addLocaleConfig(xmlDocument, mGeneratedLocaleConfigAttribute);
        }
    }

    /**
     * Creates a manifest suitable for use with AAPT by (1) substituting placeholders to an AAPT
     * friendly encoding and (2) removing any <nav-graph> tags. Saves the modified manifest as part
     * of the merging report. Does not mutate the passed in document.
     */
    @VisibleForTesting
    static void createAaptSafeManifest(
            @NonNull XmlDocument document, @NonNull MergingReport.Builder mergingReport)
            throws MergeFailureException {
        Pair<Document, Boolean> clonedDocument =
                document.cloneAndTransform(
                        PlaceholderEncoder::encode, ManifestMerger2::isNavGraphs);
        boolean isUpdated = clonedDocument.getSecond();
        mergingReport.setAaptSafeManifestUnchanged(!isUpdated);
        if (isUpdated) {
            mergingReport.setMergedDocument(AAPT_SAFE, prettyPrint(clonedDocument.getFirst()));
        }
    }

    /**
     * Function checks whether the current node is {@link SdkConstants#TAG_NAV_GRAPH}.
     *
     * @param node the element to check
     */
    private static boolean isNavGraphs(@NonNull Node node) {
        return (node instanceof Element)
                && SdkConstants.TAG_NAV_GRAPH.equals(((Element) node).getTagName());
    }

    /**
     * Set android:testOnly="true" to ensure APK will be rejected by the Play store.
     *
     * @param document the document for which the testOnly attribute should be set to true.
     */
    private static void addTestOnlyAttribute(@NonNull XmlDocument document) {
        XmlElement manifest = document.getRootNode();
        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application ->
                        setAndroidAttribute(
                                application, SdkConstants.ATTR_TEST_ONLY, SdkConstants.VALUE_TRUE));
    }

    /**
     * Set android:debuggable="true"
     *
     * @param document the document for which the debuggable attribute should be set to true.
     */
    private static void addDebuggableAttribute(@NonNull XmlDocument document) {
        XmlElement manifest = document.getRootNode();
        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application ->
                        setAndroidAttribute(
                                application,
                                SdkConstants.ATTR_DEBUGGABLE,
                                SdkConstants.VALUE_TRUE));
    }

    /**
     * Adds android:name="{multiDexApplicationName}" if there is no value specified for that field.
     *
     * @param document the document for which the name attribute might be set.
     * @param multiDexApplicationName the FQCN of MultiDexApplication
     */
    private static void addMultiDexApplicationIfNoName(
            @NonNull XmlDocument document, @NonNull String multiDexApplicationName) {
        XmlElement manifest = document.getRootNode();
        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application ->
                        setAndroidAttributeIfMissing(
                                application, ATTR_NAME, multiDexApplicationName));
    }

    /**
     * Set android:extractNativeLibs="false" unless it's already explicitly set.
     *
     * @param document the document for which the extractNativeLibs attribute should be set to
     *     false.
     */
    private static void maybeAddExtractNativeLibAttribute(@NonNull XmlDocument document) {
        XmlElement manifest = document.getRootNode();
        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application ->
                        setAndroidAttributeIfMissing(
                                application,
                                SdkConstants.ATTR_EXTRACT_NATIVE_LIBS,
                                SdkConstants.VALUE_FALSE));
    }

    /**
     * Set the {@code featureSplit} attribute to {@code featureName} for the manifest element.
     *
     * @param document the document whose attributes are changed
     * @param featureName the feature name of this feature subproject.
     */
    private static void addFeatureSplitAttribute(
            @NonNull XmlDocument document, @NonNull String featureName) {
        XmlElement manifest = document.getRootNode();
        if (manifest == null) {
            return;
        }

        String attributeName = SdkConstants.ATTR_FEATURE_SPLIT;
        manifest.setAttribute(attributeName, featureName);
    }

    /**
     * Set android:localeConfig to a generated resource. Produces an error if a user set locale
     * config is present.
     *
     * @param document the document for which the localeConfig attribute should be set.
     * @param configLocation the string to set the locale config to.
     */
    private void addLocaleConfig(@NonNull XmlDocument document, @NonNull String configLocation) {
        XmlElement manifest = document.getRootNode();
        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application -> {
                    if (application
                            .getXml()
                            .hasAttributeNS(
                                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_LOCALE_CONFIG)) {
                        String message =
                                "Locale config generation was requested but user locale config is present in manifest";
                        mLogger.error(null, message);
                        throw new RuntimeException(message);
                    } else {
                        setAndroidAttribute(
                                application, SdkConstants.ATTR_LOCALE_CONFIG, configLocation);
                    }
                });
    }

    /**
     * Set the "android:splitName" attribute to {@code featureName} for every {@code activity},
     * {@code service} and {@code provider} element.
     *
     * @param document the document whose attributes are changed
     * @param featureName the value all of the changed attributes are set to
     */
    private static void adjustInstantAppFeatureSplitInfo(
            @NonNull XmlDocument document, @NonNull String featureName) {
        XmlElement manifest = document.getRootNode();
        if (manifest == null) {
            return;
        }

        manifest.applyToFirstChildElementOfType(
                ManifestModel.NodeTypes.APPLICATION,
                application -> {
                    List<ManifestModel.NodeTypes> elementNamesToUpdate =
                            Arrays.asList(
                                    ManifestModel.NodeTypes.ACTIVITY,
                                    ManifestModel.NodeTypes.SERVICE,
                                    ManifestModel.NodeTypes.PROVIDER);
                    for (ManifestModel.NodeTypes nodeType : elementNamesToUpdate) {
                        for (XmlElement elementToUpdate : application.getAllNodesByType(nodeType)) {
                            setAndroidAttribute(
                                    elementToUpdate, SdkConstants.ATTR_SPLIT_NAME, featureName);
                        }
                    }
                });
    }

    /**
     * Set an android namespaced attribute for the manifest element.
     *
     * @param document the document whose attributes will be modified
     * @param attribute the new attribute to be set
     * @param value the new value of the attribute
     * @return the previous value of the attribute or null if the attribute was not set.
     */
    public static String setManifestAndroidAttribute(
            @NonNull XmlDocument document, @NonNull String attribute, @NonNull String value) {
        XmlElement manifest = document.getRootNode();
        if (manifest == null) {
            return null;
        }
        String previousValue =
                manifest.getXml().hasAttributeNS(SdkConstants.ANDROID_URI, attribute)
                        ? manifest.getXml().getAttributeNS(SdkConstants.ANDROID_URI, attribute)
                        : null;
        setAndroidAttribute(manifest, attribute, value);
        return previousValue;
    }

    /**
     * Adds internet permission to document if not already present.
     *
     * @param document the document which gets edited if necessary.
     */
    private static void addInternetPermission(@NonNull XmlDocument document) {
        String permission = "android.permission.INTERNET";
        XmlElement manifest = document.getRootNode();
        ImmutableList<Element> usesPermissions =
                getChildElementsByName(manifest.getXml(), SdkConstants.TAG_USES_PERMISSION);
        for (Element usesPermission : usesPermissions) {
            if (permission.equals(
                    usesPermission.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME))) {
                return;
            }
        }
        XmlElement uses =
                new XmlElement(
                        document.getXml().createElement(SdkConstants.TAG_USES_PERMISSION),
                        document);
        // Add the node to the document before setting the attribute to make sure
        // the namespace prefix is found correctly.
        document.getRootNode().appendChild(uses);
        setAndroidAttribute(uses, ATTR_NAME, permission);
    }

    /**
     * Adds <uses-split> tags for feature-on-feature dependencies.
     *
     * @param dependencyFeatureNames the names of feature modules on which this depends, if any.
     */
    private static void addUsesSplitTagsForDependencies(
            @NonNull XmlDocument document, ImmutableList<String> dependencyFeatureNames) {
        XmlElement manifest = document.getRootNode();

        for (String usedSplitName : dependencyFeatureNames) {
            XmlElement usesSplit =
                    new XmlElement(
                            document.getXml().createElement(SdkConstants.TAG_USES_SPLIT), document);
            setAndroidAttribute(usesSplit, ATTR_NAME, usedSplitName);
            manifest.appendChild(usesSplit.getXml());
        }
    }

    /**
     * Adds <application> tag if missing as it required by package manager in R and above..
     *
     * @param document the loaded manifest file
     */
    private static void optionalAddApplicationTagIfMissing(@NonNull XmlDocument document) {
        XmlElement manifest = document.getRootNode();

        if (manifest.getXml().getElementsByTagName(SdkConstants.TAG_APPLICATION).getLength() > 0)
            return;

        Element application = document.getXml().createElement(SdkConstants.TAG_APPLICATION);
        manifest.appendChild(application);
    }

    /**
     * Set an Android-namespaced XML attribute on the given node.
     *
     * @param node Node in which to set the attribute; must be part of a document
     * @param localName Non-prefixed attribute name
     * @param value value of the attribute
     */
    public static void setAndroidAttribute(XmlElement node, String localName, String value) {
        String prefix =
                XmlUtils.lookupNamespacePrefix(
                        node.getXml(),
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ANDROID_NS_NAME,
                        true);
        node.setAttributeNS(SdkConstants.ANDROID_URI, prefix + ":" + localName, value);
    }

    /**
     * Set an Android-namespaced XML attribute on the given node, if that attribute is missing.
     *
     * @param node Node in which to set the attribute; must be part of a document
     * @param localName Non-prefixed attribute name
     * @param value value of the attribute
     */
    private static void setAndroidAttributeIfMissing(
            XmlElement node, String localName, String value) {
        if (!node.getXml().hasAttributeNS(SdkConstants.ANDROID_URI, localName)) {
            setAndroidAttribute(node, localName, value);
        }
    }

    /**
     * Returns a list of elements which are the immediate children of the given element and have the
     * given name.
     *
     * @param element the immediate parent of any elements in the returned list
     * @param name the name of any elements in the returned list
     * @return the list (possibly empty) of children elements with the given name
     */
    @NonNull
    public static ImmutableList<Element> getChildElementsByName(
            @NonNull Element element, @NonNull String name) {
        ImmutableList.Builder<Element> childListBuilder = ImmutableList.builder();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode instanceof Element && name.equals(childNode.getNodeName())) {
                childListBuilder.add((Element) childNode);
            }
        }
        return childListBuilder.build();
    }

    /** Returns a pretty string representation of the document. */
    @NonNull
    private static String prettyPrint(Document document) {
        return XmlPrettyPrinter.prettyPrint(
                document,
                XmlFormatPreferences.defaults(),
                XmlFormatStyle.get(document.getDocumentElement()),
                null, /* endOfLineSeparator */
                false /* endWithNewLine */);
    }

    /**
     * Returns the {@link FileStreamProvider} used by this manifest merger. Use this to read files
     * if you need to access the content of a {@link XmlDocument}.
     */
    @SuppressWarnings("unused") // Allow future library usage, if necessary
    @NonNull
    public FileStreamProvider getFileStreamProvider() {
        return mFileStreamProvider;
    }

    /**
     * Creates the merging report file.
     * @param mergingReport the merging activities report to serialize.
     */
    private void writeReport(@NonNull MergingReport mergingReport) {
        FileWriter fileWriter = null;
        try {
            if (!mReportFile.isPresent()
                    || !mReportFile.get().getParentFile().exists()
                            && !mReportFile.get().getParentFile().mkdirs()) {
                mLogger.warning(String.format(
                        "Cannot create %1$s manifest merger report file,"
                                + "build will continue but merging activities "
                                + "will not be documented",
                        mReportFile.get().getAbsolutePath()));
            } else {
                fileWriter = new FileWriter(mReportFile.get());
                mergingReport.getActions().log(fileWriter);
            }
        } catch (IOException e) {
            mLogger.warning(String.format(
                    "Error '%1$s' while writing the merger report file, "
                            + "build can continue but merging activities "
                            + "will not be documented ",
                    e.getMessage()));
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    mLogger.warning(String.format(
                            "Error '%1$s' while closing the merger report file, "
                                    + "build can continue but merging activities "
                                    + "will not be documented ",
                            e.getMessage()));
                }
            }
        }
    }

    /**
     * shorten recursively all attributes that are package dependent of the passed nodes and all its
     * child nodes.
     *
     * @param namespace the namespace to search for and extract.
     * @param xmlElement the xml element to process recursively.
     */
    private static void extractFqcns(@NonNull String namespace, @NonNull XmlElement xmlElement) {
        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {
            if (xmlAttribute.getModel() != null && xmlAttribute.getModel().isPackageDependent()) {
                String value = xmlAttribute.getValue();
                if (value.startsWith(namespace + ".")) {
                    xmlAttribute.setValue(value.substring(namespace.length()));
                }
            }
        }
        for (XmlElement child : xmlElement.getMergeableElements()) {
            extractFqcns(namespace, child);
        }
    }

    /**
     * Load an xml file and perform placeholder substitution
     *
     * @param manifestInfo the android manifest information like if it is a library, an overlay or a
     *     main manifest file.
     * @param selectors all the libraries selectors
     * @param mergingReportBuilder the merging report to store events and errors.
     * @param namespace the namespace to use to create or shorten fully qualified class names in the
     *     manifest; if null, we use the manifest's original package attribute, if present.
     * @return a loaded manifest info.
     * @throws MergeFailureException if the merging cannot be completed successfully.
     */
    @NonNull
    private LoadedManifestInfo load(
            @NonNull ManifestInfo manifestInfo,
            @NonNull KeyResolver<String> selectors,
            @NonNull MergingReport.Builder mergingReportBuilder,
            @Nullable String namespace)
            throws MergeFailureException {

        boolean rewriteNamespaces =
                mOptionalFeatures.contains(Invoker.Feature.FULLY_NAMESPACE_LOCAL_RESOURCES);

        File xmlFile = manifestInfo.mLocation;
        XmlDocument xmlDocument;
        try {
            InputStream inputStream = mFileStreamProvider.getInputStream(xmlFile);
            xmlDocument =
                    XmlLoader.load(
                            selectors,
                            mSystemPropertyResolver,
                            manifestInfo.mName,
                            xmlFile,
                            inputStream,
                            manifestInfo.getType(),
                            namespace,
                            mModel,
                            rewriteNamespaces);
        } catch (Exception e) {
            throw new MergeFailureException("Error parsing " + xmlFile.getAbsolutePath(), e);
        }

        MergingReport.Builder builder =
                manifestInfo.getType() == XmlDocument.Type.MAIN
                        ? mergingReportBuilder
                        : new MergingReport.Builder(mergingReportBuilder.getLogger());

        // perform place holder substitution, this is necessary to do so early in case placeholders
        // are used in key attributes.
        MergingReport.Record.Severity severity =
                !mMergeType.isFullPlaceholderSubstitutionRequired()
                        ? MergingReport.Record.Severity.INFO
                        : MergingReport.Record.Severity.ERROR;
        performPlaceHolderSubstitution(
                xmlDocument,
                xmlDocument.getPackage().map(XmlAttribute::getValue).orElse(null),
                builder,
                severity);

        builder.getActionRecorder().recordAddedNodeAction(xmlDocument.getRootNode(), false);

        mProcessCancellationChecker.check();

        return new LoadedManifestInfo(manifestInfo, xmlDocument);
    }

    private void performPlaceHolderSubstitution(
            @NonNull XmlDocument xmlDocument,
            @Nullable String originalMainManifestPackageName,
            @NonNull MergingReport.Builder mergingReportBuilder,
            @NonNull MergingReport.Record.Severity severity) {

        if (mOptionalFeatures.contains(Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)) {
            return;
        }

        // check for placeholders presence, switch first the packageName and applicationId if
        // it is not explicitly set, unless dealing with a LIBRARY MergeType.
        // In case of a LIBRARY MergeType, we don't replace packageName or applicationId,
        // unless they're already specified in mPlaceHolderValues.
        Map<String, Object> finalPlaceHolderValues = mPlaceHolderValues;
        if (!mPlaceHolderValues.containsKey(APPLICATION_ID)
                && mMergeType != MergeType.LIBRARY
                && originalMainManifestPackageName != null) {
            // add all existing placeholders except package name that will be swapped.
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            for (Map.Entry<String, Object> entry : mPlaceHolderValues.entrySet()) {
                if (!entry.getKey().equals(PACKAGE_NAME)) {
                    builder.put(entry);
                }
            }
            builder.put(PACKAGE_NAME, originalMainManifestPackageName);
            builder.put(APPLICATION_ID, originalMainManifestPackageName);
            finalPlaceHolderValues = builder.build();
        }

        KeyBasedValueResolver<String> placeHolderValueResolver =
                new MapBasedKeyBasedValueResolver<>(finalPlaceHolderValues);
        PlaceholderHandler.visit(
                severity, xmlDocument, placeHolderValueResolver, mergingReportBuilder);
    }

    // merge the optionally existing xmlDocument with a lower priority xml file.
    private Optional<XmlDocument> merge(
            @Nullable XmlDocument xmlDocument,
            @NonNull LoadedManifestInfo lowerPriorityDocument,
            @NonNull MergingReport.Builder mergingReportBuilder) {

        MergingReport.Result validationResult =
                PreValidator.validate(
                        mergingReportBuilder,
                        lowerPriorityDocument.getXmlDocument(),
                        mMergeType,
                        mOptionalFeatures.contains(
                                Invoker.Feature.VALIDATE_APPLICATION_ELEMENT_ATTRIBUTES));

        if (validationResult == MergingReport.Result.ERROR
                && !mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS)) {
            mergingReportBuilder.addMessage(
                    lowerPriorityDocument.getXmlDocument().getSourceFile(),
                    MergingReport.Record.Severity.ERROR,
                    "Validation failed, exiting");
            return Optional.empty();
        }

        Optional<XmlDocument> result;
        if (xmlDocument != null) {
            result =
                    xmlDocument.merge(
                            lowerPriorityDocument.getXmlDocument(),
                            mergingReportBuilder,
                            !mOptionalFeatures.contains(
                                    Invoker.Feature.NO_IMPLICIT_PERMISSION_ADDITION),
                            mOptionalFeatures.contains(Invoker.Feature.DISABLE_MINSDKLIBRARY_CHECK),
                            mOptionalFeatures.contains(Invoker.Feature.KEEP_GOING_AFTER_ERRORS));
        } else {
            // exhaustiveSearch is true in recordAddedNodeAction() below because some of this
            // manifest's nodes might have already been recorded from the loading of
            // the main manifest, but we want to record any unrecorded descendants.
            // e.g., if the main manifest did not contain any meta-data nodes below its
            // application node, we still want to record the addition of any such
            // meta-data nodes this manifest contains.
            mergingReportBuilder
                    .getActionRecorder()
                    .recordAddedNodeAction(
                            lowerPriorityDocument.getXmlDocument().getRootNode(), true);
            result = Optional.of(lowerPriorityDocument.getXmlDocument());
        }

        // if requested, dump each intermediary merging stage into the report.
        if (mOptionalFeatures.contains(Invoker.Feature.KEEP_INTERMEDIARY_STAGES)
                && result.isPresent()) {
            mergingReportBuilder.addMergingStage(result.get().prettyPrint());
        }

        return result;
    }

    private List<LoadedManifestInfo> loadLibraries(
            @NonNull SelectorResolver selectors,
            @NonNull MergingReport.Builder mergingReportBuilder,
            @Nullable String originalMainManifestPackageName)
            throws MergeFailureException {

        ImmutableList.Builder<LoadedManifestInfo> loadedLibraryDocuments = ImmutableList.builder();

        for (Pair<String, File> libraryFile : Sets.newLinkedHashSet(mLibraryFiles)) {
            mLogger.verbose("Loading library manifest " + libraryFile.getSecond().getPath());
            ManifestInfo manifestInfo =
                    new ManifestInfo(
                            libraryFile.getFirst(),
                            libraryFile.getSecond(),
                            XmlDocument.Type.LIBRARY);
            File xmlFile = manifestInfo.mLocation;
            XmlDocument libraryDocument;
            try {
                Optional<Document> document =
                        Optional.ofNullable(mManifestDocumentProvider)
                                .flatMap(provider -> provider.getManifestDocument(xmlFile));
                if (document.isPresent()) {
                    libraryDocument =
                            XmlLoader.load(
                                    document.get(),
                                    selectors,
                                    mSystemPropertyResolver,
                                    manifestInfo.mName,
                                    xmlFile,
                                    XmlDocument.Type.LIBRARY,
                                    null, /* namespace */
                                    mModel,
                                    false);
                } else {
                    libraryDocument =
                            XmlLoader.load(
                                    selectors,
                                    mSystemPropertyResolver,
                                    manifestInfo.mName,
                                    xmlFile,
                                    mFileStreamProvider.getInputStream(xmlFile),
                                    XmlDocument.Type.LIBRARY,
                                    null, /* namespace */
                                    mModel,
                                    false);
                }
            } catch (Exception e) {
                throw new MergeFailureException(e);
            }
            // extract the package name...
            String libraryNamespace = libraryDocument.getNamespace();
            // save it in the selector instance.
            if (!Strings.isNullOrEmpty(libraryNamespace)) {
                selectors.addSelector(libraryNamespace, libraryFile.getFirst());
            }

            // perform placeholder substitution, this is useful when the library is using
            // a placeholder in a key element, we however do not need to record these
            // substitutions so feed it with a fake merging report.
            MergingReport.Builder builder =
                    new MergingReport.Builder(mergingReportBuilder.getLogger());
            builder.getActionRecorder().recordAddedNodeAction(libraryDocument.getRootNode(), false);
            performPlaceHolderSubstitution(
                    libraryDocument,
                    originalMainManifestPackageName,
                    builder,
                    MergingReport.Record.Severity.INFO);
            if (builder.hasErrors()) {
                // we log the errors but continue, in case the error is of no consequence
                // to the application consuming the library.
                builder.build().log(mLogger);
            }

            LoadedManifestInfo info = new LoadedManifestInfo(manifestInfo, libraryDocument);

            loadedLibraryDocuments.add(info);

            mProcessCancellationChecker.check();
        }

        return loadedLibraryDocuments.build();
    }

    /**
     * Checks whether all manifests have unique namespaces. If the strict mode is enabled it will
     * result in an error for namespace collisions, otherwise it will result in a warning.
     */
    private static void checkUniqueNamespaces(
            @NonNull LoadedManifestInfo loadedMainManifestInfo,
            @NonNull List<LoadedManifestInfo> libraries,
            @NonNull List<String> allowedNonUniqueNamespaces,
            @NonNull MergingReport.Builder mergingReportBuilder,
            boolean strictUniqueNamespaceCheck) {
        Multimap<String, LoadedManifestInfo> uniqueNamespaceMap = ArrayListMultimap.create();

        if (!Strings.isNullOrEmpty(loadedMainManifestInfo.getNamespace())) {
            uniqueNamespaceMap.put(loadedMainManifestInfo.getNamespace(), loadedMainManifestInfo);
        }

        libraries.stream()
                .filter(l -> !Strings.isNullOrEmpty(l.getNamespace()))
                .forEach(l -> uniqueNamespaceMap.put(l.getNamespace(), l));

        uniqueNamespaceMap.asMap().entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(
                        e -> {
                            Collection<String> offendingTargets =
                                    e.getValue().stream()
                                            .map(ManifestInfo::getName)
                                            .collect(Collectors.toList());
                            String repeatedNamespaceMessage =
                                    "Namespace '"
                                            + e.getKey()
                                            + "' is used in multiple modules and/or libraries: "
                                            + Joiner.on(", ").join(offendingTargets)
                                            + ". Please ensure that all modules and libraries have a "
                                            + "unique namespace."
                                            + " For more information, See https://developer.android.com/studio/build/configure-app-module#set-namespace";
                            // We know that there is at least one because of the
                            // filter check.
                            LoadedManifestInfo info = e.getValue().stream().findFirst().get();
                            // Report only once per error, since the error message contain the path
                            // to all manifests with the repeated namespace.

                            mergingReportBuilder.addMessage(
                                    info.getXmlDocument().getSourceFile(),
                                    getNonUniqueNamespaceSeverity(
                                            allowedNonUniqueNamespaces,
                                            e.getKey(),
                                            strictUniqueNamespaceCheck),
                                    repeatedNamespaceMessage);
                        });
    }

    /** Returns the correct logging severity for a clashing namespace. */
    private static MergingReport.Record.Severity getNonUniqueNamespaceSeverity(
            @NonNull List<String> allowedNonUniqueNamespaces,
            String namespace,
            boolean strictMode) {
        // If we've allowed a library namespace to be non-unique, only report in info.
        if (allowedNonUniqueNamespaces.contains(namespace))
            return MergingReport.Record.Severity.INFO;

        return strictMode
                ? MergingReport.Record.Severity.ERROR
                : MergingReport.Record.Severity.WARNING;
    }

    /**
     * Creates a new {@link Invoker} instance to invoke the merging tool to merge manifest files for
     * an application.
     *
     * @param mainManifestFile application main manifest file.
     * @param logger the logger interface to use.
     * @return an {@link Invoker} instance that will allow further customization and trigger the
     *     merging tool.
     */
    @NonNull
    public static Invoker newMerger(
            @NonNull File mainManifestFile, @NonNull ILogger logger, @NonNull MergeType mergeType) {
        return new Invoker(mainManifestFile, logger, mergeType, XmlDocument.Type.MAIN);
    }

    /**
     * Defines the merging type expected from the tool.
     */
    public enum MergeType {
        /**
         * Application merging type is used when packaging an application with a set of imported
         * libraries. The resulting merged android manifest is final and is not expected to be
         * imported in another application.
         */
        APPLICATION(true, true),

        /**
         * Library merging type is used when packaging a library. The resulting android manifest
         * file will not merge in all the imported libraries this library depends on. Also the tools
         * annotations will not be removed as they can be useful when later importing the resulting
         * merged android manifest into an application.
         */
        LIBRARY(false, false),

        /**
         * Fused library merging type is similar to application manifest merging as library
         * manifests are merged, however tools annotations are kept for application merging,
         * placeholder replacements are non-exhaustive and final validation is not performed.
         * merging.
         */
        FUSED_LIBRARY(false, false),

        /**
         * Privacy sandbox library merging similar to fused library merging except that resulting
         * manifest is expected to be processed by aapt2 and shipped into an .asb file to the Play
         * Store.
         */
        PRIVACY_SANDOX_LIBRARY(true, true);

        private final boolean isKeepToolsAttributeRequired;
        private final boolean isFullPlaceholderSubstitutionRequired;

        /**
         * Return true if the localName attribute should be removed from the node declaring it.
         *
         * @param localName the XML no namespace local name
         */
        public boolean isKeepToolsAttributeRequired(String localName, String value) {
            return isKeepToolsAttributeRequired
                    && !(NodeOperationType.REQUIRED_BY_PRIVACY_SANDBOX_SDK_ATTRIBUTE_NAME.equals(
                                    localName)
                            && value.equals(SdkConstants.VALUE_TRUE));
        }

        public boolean isFullPlaceholderSubstitutionRequired() {
            return isFullPlaceholderSubstitutionRequired;
        }

        MergeType(boolean isKeepToolsAttributeRequired, boolean isFullPlaceholderSubstitutionRequired) {
            this.isKeepToolsAttributeRequired = isKeepToolsAttributeRequired;
            this.isFullPlaceholderSubstitutionRequired = isFullPlaceholderSubstitutionRequired;
        }
    }

    /**
     * Defines a property that can add or override itself into an XML document.
     */
    public interface AutoAddingProperty {

        /**
         * Add itself (possibly just override the current value) with the passed value
         * @param actionRecorder to record actions.
         * @param document the xml document to add itself to.
         * @param value the value to set of this property.
         */
        void addTo(@NonNull ActionRecorder actionRecorder,
                @NonNull XmlDocument document,
                @NonNull String value);
    }

    /**
     * Perform {@link ManifestSystemProperty} injection.
     *
     * @param mergingReport to log actions and errors.
     * @param xmlDocument the xml document to inject into.
     */
    protected void performSystemPropertiesInjection(
            @NonNull MergingReport.Builder mergingReport, @NonNull XmlDocument xmlDocument) {
        for (ManifestSystemProperty manifestSystemProperty : ManifestSystemProperty.getValues()) {
            String propertyOverride = mSystemPropertyResolver.getValue(manifestSystemProperty);
            if (propertyOverride != null) {
                manifestSystemProperty.addTo(
                        mergingReport.getActionRecorder(), xmlDocument, propertyOverride);
            }
        }
    }

    /**
     * A {@linkplain ManifestDocumentProvider} provides the merged manifest XML {@link Document}
     * instance for the project a given {@link File} belongs to.
     */
    public interface ManifestDocumentProvider {
        /**
         * Gets a Merged manifest XML document for the given file's project. Returns
         * Optional.empty() when the document is unavailable. ManifestMerger process falls back to
         * the {@link FileStreamProvider} when the document is not available.
         *
         * @param file the file handle
         * @return the contents of the file
         */
        Optional<Document> getManifestDocument(@NonNull File file);
    }

    /**
     * A {@linkplain FileStreamProvider} provides (buffered, if necessary) {@link InputStream}
     * instances for a given {@link File} handle. Consider providing a {@link
     * ManifestDocumentProvider} when manifest DOM is available.
     */
    public static class FileStreamProvider {
        /**
         * Creates a reader for the given file -- which may not necessarily read the contents of the
         * file on disk. For example, in the IDE, the client will map the file handle to a document in
         * the editor, and read the current contents of that editor whether or not it has been saved.
         * <p>
         * This method is responsible for providing its own buffering, if necessary (e.g. when
         * reading from disk, make sure you wrap the file stream in a buffering input stream.)
         *
         * @param file the file handle
         * @return the contents of the file
         * @throws FileNotFoundException if the file handle is invalid
         */
        protected InputStream getInputStream(@NonNull File file) throws IOException {
            return new BufferedInputStream(new FileInputStream(file));
        }
    }

    public interface ProcessCancellationChecker {
        /**
         * @throws com.intellij.openapi.progress.ProcessCanceledException if the progress indicator
         * associated with the current thread has been canceled.
         * @see com.intellij.openapi.progress.ProgressManager#checkCanceled()
         */
        void check();
    }

    private void checkExportedDeclaration(
            XmlDocument finalMergedDocument, MergingReport.Builder mergingReportBuilder) {
        String targetSdkVersion = finalMergedDocument.getTargetSdkVersion(mergingReportBuilder);
        int targetSdkApi =
                Character.isDigit(targetSdkVersion.charAt(0))
                        ? Integer.parseInt(targetSdkVersion)
                        : SdkVersionInfo.getApiByPreviewName(targetSdkVersion, true);
        if (targetSdkApi > 30) {
            Optional<XmlElement> element =
                    finalMergedDocument
                            .getRootNode()
                            .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
            if (!element.isPresent()) {
                return;
            }
            XmlElement applicationElement = element.get();

            checkIfExportedIsNeeded(
                    applicationElement.getAllNodesByType(ManifestModel.NodeTypes.ACTIVITY),
                    mergingReportBuilder);

            checkIfExportedIsNeeded(
                    applicationElement.getAllNodesByType(ManifestModel.NodeTypes.SERVICE),
                    mergingReportBuilder);

            checkIfExportedIsNeeded(
                    applicationElement.getAllNodesByType(ManifestModel.NodeTypes.RECEIVER),
                    mergingReportBuilder);
        }
    }

    private void checkIfExportedIsNeeded(
            List<XmlElement> list, MergingReport.Builder mergingReportBuilder) {
        for (XmlElement element : list) {
            if (element.getAllNodesByType(ManifestModel.NodeTypes.INTENT_FILTER).size() > 0
                    && !element.getXml()
                            .hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED)) {
                mergingReportBuilder.addMessage(
                        element,
                        MergingReport.Record.Severity.ERROR,
                        String.format(
                                "android:exported needs to be explicitly specified for element <%s>. Apps targeting Android 12 and higher are required to specify an explicit value "
                                        + "for `android:exported` when the corresponding component has an intent filter defined. "
                                        + "See https://developer.android.com/guide/topics/manifest/activity-element#exported for details.",
                                element.getId()));
            }
        }
    }

    private void stripTargetSdk(@NonNull XmlDocument xmlDocument) {
        Optional<XmlElement> usesSdk = xmlDocument.getByTypeAndKey(USES_SDK, null);
        usesSdk.ifPresent(
                xmlElement ->
                        xmlElement
                                .removeAttributeNS(
                                        SdkConstants.ANDROID_URI,
                                        SdkConstants.ATTR_TARGET_SDK_VERSION));
    }

    /**
     * This class will hold all invocation parameters for the manifest merging tool.
     *
     * <p>There are broadly three types of input to the merging tool :
     *
     * <ul>
     *   <li>Build types and flavors overriding manifests
     *   <li>Application main manifest
     *   <li>Library manifest files
     * </ul>
     *
     * Only the main manifest file is a mandatory parameter.
     *
     * <p>High level description of the merging will be as follow :
     *
     * <ol>
     *   <li>Build type and flavors will be merged first in the order they were added. Highest
     *       priority file added first, lowest added last.
     *   <li>Resulting document is merged with lower priority application main manifest file.
     *   <li>Resulting document is merged with each library file manifest file in the order they
     *       were added. Highest priority added first, lowest added last.
     *   <li>Resulting document is returned as results of the merging process.
     * </ol>
     */
    public static class Invoker {

        protected final File mMainManifestFile;

        protected final ImmutableMap.Builder<ManifestSystemProperty, Object>
                mSystemProperties = new ImmutableMap.Builder<>();

        @NonNull
        protected final ILogger mLogger;

        @NonNull
        protected final ImmutableMap.Builder<String, Object> mPlaceholders =
                new ImmutableMap.Builder<>();

        @NonNull
        private final ImmutableList.Builder<Pair<String, File>> mLibraryFilesBuilder =
                new ImmutableList.Builder<>();

        @NonNull
        private final ImmutableList.Builder<File> mFlavorsAndBuildTypeFiles =
                new ImmutableList.Builder<>();

        @NonNull
        private final ImmutableList.Builder<Feature> mFeaturesBuilder =
                new ImmutableList.Builder<>();

        @NonNull
        private final MergeType mMergeType;
        @NonNull private  XmlDocument.Type mDocumentType;
        @Nullable private File mReportFile;

        @Nullable
        private FileStreamProvider mFileStreamProvider;

        @Nullable private ManifestDocumentProvider mManifestDocumentProvider;

        @Nullable
        private ProcessCancellationChecker mProcessCancellationChecker;

        @NonNull private String mFeatureName;

        @Nullable private String mNamespace;

        @NonNull
        private final ImmutableList.Builder<File> mNavigationFilesBuilder =
                new ImmutableList.Builder<>();

        @NonNull
        private final ImmutableList.Builder<File> mNavigationJsonsBuilder =
                new ImmutableList.Builder<>();

        @NonNull
        private final ImmutableList.Builder<String> mDependencyFetureNamesBuilder =
                new ImmutableList.Builder<>();

        @NonNull
        private final ImmutableList.Builder<String> mAllowedNonUniqueNamespaces =
                new ImmutableList.Builder<>();

        @Nullable private String mGeneratedLocaleConfigAttribute = null;

        /**
         * Sets a value for a {@link ManifestSystemProperty}
         *
         * @param override the property to set
         * @param value the value for the property
         * @return itself.
         */
        @NonNull
        public Invoker setOverride(
                @NonNull ManifestSystemProperty override, @NonNull String value) {
            mSystemProperties.put(override, value);
            return this;
        }

        /**
         * Adds placeholders names and associated values for substitution.
         * @return itself.
         */
        @NonNull
        public Invoker setPlaceHolderValues(@NonNull Map<String, Object> keyValuePairs) {
            mPlaceholders.putAll(keyValuePairs);
            return this;
        }

        /**
         * Adds a new placeholder name and value for substitution.
         * @return itself.
         */
        @NonNull
        public Invoker setPlaceHolderValue(@NonNull String placeHolderName, @NonNull String value) {
            mPlaceholders.put(placeHolderName, value);
            return this;
        }

        /**
         * Optional behavior of the merging tool can be turned on by setting these Feature.
         */
        public enum Feature {

            /**
             * Keep all intermediary merged files during the merging process. This is particularly
             * useful for debugging/tracing purposes.
             */
            KEEP_INTERMEDIARY_STAGES,

            /**
             * When logging file names, use {@link File#getName()} rather than {@link
             * File#getPath()}
             */
            PRINT_SIMPLE_FILENAMES,

            /**
             * Perform a sweep after all merging activities to remove all fully qualified class
             * names and replace them with the equivalent short version.
             */
            EXTRACT_FQCNS,

            /**
             * Perform a sweep after all merging activities to remove all tools: decorations.
             */
            REMOVE_TOOLS_DECLARATIONS,

            /**
             * Do no perform placeholders replacement.
             */
            NO_PLACEHOLDER_REPLACEMENT,

            /**
             * Encode unresolved placeholders to be AAPT friendly.
             */
            MAKE_AAPT_SAFE,

            /**
             * Clients will not request the blame history
             */
            SKIP_BLAME,

            /**
             * Clients will only request the merged XML documents, not XML pretty printed documents
             */
            SKIP_XML_STRING,

            /**
             * Add android:testOnly="true" attribute to prevent APK from being uploaded to Play
             * store.
             */
            TEST_ONLY,

            /**
             * Do not perform implicit permission addition.
             */
            NO_IMPLICIT_PERMISSION_ADDITION,

            /** Perform Studio advanced profiling manifest modifications */
            ADVANCED_PROFILING,

            /** Mark this application as a feature split */
            ADD_DYNAMIC_FEATURE_ATTRIBUTES,

            /** Set the android:debuggable flag to the application. */
            DEBUGGABLE,

            /**
             * When there are attribute value conflicts, automatically pick the higher priority
             * value.
             *
             * <p>This is for example used in the IDE when we need to merge a new manifest template
             * into an existing one and we don't want to abort the merge.
             *
             * <p>(This will log a warning.)
             */
            HANDLE_VALUE_CONFLICTS_AUTOMATICALLY,

            /**
             * Adds the AndroidX name of {@link AndroidXConstants#MULTI_DEX_APPLICATION} as
             * application name if none is specified. Used for legacy multidex.
             */
            ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME,

            /**
             * Adds the pre-AndroidX name of {@link AndroidXConstants#MULTI_DEX_APPLICATION} as
             * application name if none is specified. Used for legacy multidex.
             */
            ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME,

            /** Rewrite local resource references with fully qualified namespace */
            FULLY_NAMESPACE_LOCAL_RESOURCES,

            /* Disables check for uniqueness of package names in dependencies manifests */
            DISABLE_PACKAGE_NAME_UNIQUENESS_CHECK,

            /** Enforce that dependencies manifests don't have duplicated package names. */
            ENFORCE_UNIQUE_PACKAGE_NAME,

            /**
             * Sets the application's android:extractNativeLibs attribute to false, unless it's
             * already explicitly set to true.
             */
            DO_NOT_EXTRACT_NATIVE_LIBS,

            /** Unsafely disables minSdkVersion check in libraries. */
            DISABLE_MINSDKLIBRARY_CHECK,

            /**
             * Warn if the package attribute is present in the main manifest, or throw an exception
             * if it's present and not equal to the component's namespace.
             *
             * <p>This is used in AGP because users must migrate to the new namespace DSL.
             */
            CHECK_IF_PACKAGE_IN_MAIN_MANIFEST,

            /** Removes target SDK for library manifest */
            DISABLE_STRIP_LIBRARY_TARGET_SDK,

            /**
             * If set, merger will continue merging after any errors, allowing to surface errors in
             * the "merged manifest" editor view.
             */
            KEEP_GOING_AFTER_ERRORS,

            /**
             * Warn if the {@link SdkConstants#ATTR_EXTRACT_NATIVE_LIBS} or {@link
             * SdkConstants#ATTR_USE_EMBEDDED_DEX} attribute is present in a source manifest.
             *
             * <p>This is used in AGP because users must migrate to the new useLegacyPackaging APIs.
             */
            VALIDATE_APPLICATION_ELEMENT_ATTRIBUTES
        }

        /**
         * Creates a new builder with the mandatory main manifest file.
         * @param mainManifestFile application main manifest file.
         * @param logger the logger interface to use.
         */
        private Invoker(
                @NonNull File mainManifestFile,
                @NonNull ILogger logger,
                @NonNull MergeType mergeType,
                @NonNull XmlDocument.Type documentType) {
            this.mMainManifestFile = Preconditions.checkNotNull(mainManifestFile);
            this.mLogger = logger;
            this.mMergeType = mergeType;
            this.mDocumentType = documentType;
            this.mFeatureName = "";
        }

        /**
         * Sets the file to use to write the merging report. If not called,
         * the merging process will not write a report.
         * @param mergeReport the file to write the report in.
         * @return itself.
         */
        @NonNull
        public Invoker setMergeReportFile(@Nullable File mergeReport) {
            mReportFile = mergeReport;
            return this;
        }

        /**
         * Add one library file manifest, will be added last in the list of library files which will
         * make the parameter the lowest priority library manifest file.
         * @param file the library manifest file to add.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifest(@NonNull File file) {
            addLibraryManifest(file.getName(), file);
            return this;
        }

        /**
         * Add one library file manifest, will be added last in the list of library files which will
         * make the parameter the lowest priority library manifest file.
         * @param file the library manifest file to add.
         * @param name the library name.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifest(@NonNull String name, @NonNull File file) {
            if (mMergeType == MergeType.LIBRARY) {
                throw new IllegalStateException(
                  "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.add(Pair.of(name, file));
            return this;
        }

        /**
         * Sets library dependencies for this merging activity.
         * @param namesAndFiles the list of library dependencies.
         * @return itself.
         *
         * @deprecated use addLibraryManifest or addAndroidBundleManifests
         */
        @NonNull
        @Deprecated
        public Invoker addBundleManifests(@NonNull List<Pair<String, File>> namesAndFiles) {
            if (mMergeType == MergeType.LIBRARY && !namesAndFiles.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot add library dependencies manifests when creating a library");
            }
            mLibraryFilesBuilder.addAll(namesAndFiles);
            return this;
        }

        /**
         * Sets manifest providers for this merging activity.
         * @param providers the list of manifest providers.
         * @return itself.
         */
        @NonNull
        public Invoker addManifestProviders(@NonNull Iterable<? extends ManifestProvider> providers) {
            for (ManifestProvider provider : providers) {
                mLibraryFilesBuilder.add(Pair.of(provider.getName(), provider.getManifest()));
            }
            return this;
        }

        /**
         * Add several library file manifests at then end of the list which will make them the
         * lowest priority manifest files. The relative priority between all the files passed as
         * parameters will be respected.
         * @param files library manifest files to add last.
         * @return itself.
         */
        @NonNull
        public Invoker addLibraryManifests(@NonNull File... files) {
            for (File file : files) {
                addLibraryManifest(file);
            }
            return this;
        }

        /**
         * Add a flavor or build type manifest file last in the list.
         * @param file build type or flavor manifest file
         * @return itself.
         */
        @NonNull
        public Invoker addFlavorAndBuildTypeManifest(@NonNull File file) {
            this.mFlavorsAndBuildTypeFiles.add(file);
            return this;
        }

        /**
         * Add several flavor or build type manifest files last in the list. Relative priorities
         * between the passed files as parameters will be respected.
         * @param files build type of flavor manifest files to add.
         * @return itself.
         */
        @NonNull
        public Invoker addFlavorAndBuildTypeManifests(File... files) {
            this.mFlavorsAndBuildTypeFiles.add(files);
            return this;
        }

        /**
         * Sets some optional features for the merge tool.
         *
         * @param features one to many features to set.
         * @return itself.
         */
        @NonNull
        public Invoker withFeatures(Feature...features) {
            mFeaturesBuilder.add(features);
            return this;
        }

        /**
         * Sets a file stream provider which allows the client of the manifest merger to provide
         * arbitrary content lookup for files. <p> NOTE: There should only be one.
         *
         * @param provider the provider to use
         * @return itself.
         */
        @NonNull
        public Invoker withFileStreamProvider(@Nullable FileStreamProvider provider) {
            assert mFileStreamProvider == null || provider == null;
            mFileStreamProvider = provider;
            return this;
        }

        /**
         * Sets a manifest XML document provider which allows the client of the manifest merger to
         * provide manifest DOM Document object lookup for given files.
         *
         * <p>NOTE: There should only be one.
         *
         * @param provider the provider to use
         * @return itself.
         */
        @NonNull
        public Invoker withManifestDocumentProvider(@Nullable ManifestDocumentProvider provider) {
            assert mManifestDocumentProvider == null || provider == null;
            mManifestDocumentProvider = provider;
            return this;
        }

        @NonNull
        public Invoker withProcessCancellationChecker(@NonNull ProcessCancellationChecker checker) {
            assert mProcessCancellationChecker == null;
            mProcessCancellationChecker = checker;
            return this;
        }

        /** Regular expression defining legal feature split name. */
        private static final Pattern FEATURE_NAME_PATTERN =
                Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_]*");

        /**
         * Specify the feature name for feature merging.
         *
         * @param featureName the feature name to use.
         * @return itself.
         */
        @NonNull
        public Invoker setFeatureName(@Nullable String featureName) {
            if (featureName != null) {
                mFeatureName = featureName;
                if (!FEATURE_NAME_PATTERN.matcher(mFeatureName).matches()) {
                    throw new IllegalArgumentException(
                            "FeatureName must follow "
                                    + FEATURE_NAME_PATTERN.pattern()
                                    + " regex, found "
                                    + featureName);
                }
            }
            return this;
        }

        /**
         * Specify the module namespace.
         *
         * @param namespace the namespace, used to create or shorten fully qualified class names. If
         *     specified, this namespace will override the package name in the source manifest.
         * @return itself.
         */
        @NonNull
        public Invoker setNamespace(@NonNull String namespace) {
            mNamespace = namespace;
            return this;
        }

        /**
         * Add several navigation files last in the list. Relative priorities between the passed
         * files as parameters will be respected.
         *
         * @param files the navigation files to add.
         * @return itself.
         */
        @NonNull
        public Invoker addNavigationFiles(@NonNull Iterable<File> files) {
            this.mNavigationFilesBuilder.addAll(files);
            return this;
        }

        /**
         * Add several navigation.json files in the list.
         *
         * @param files the navigation.json files to add.
         * @return itself.
         */
        @NonNull
        public Invoker addNavigationJsons(@NonNull Iterable<File> files) {
            this.mNavigationJsonsBuilder.addAll(files);
            return this;
        }

        /**
         * Specify if the file being merged is an overlay (flavor). If not called, the merging
         * process will assume a master manifest merge. The master manifest needs to have a package
         * and some other mandatory fields like "uses-sdk", etc.
         *
         * @return itself.
         */
        @NonNull
        public Invoker asType(XmlDocument.Type type) {
            mDocumentType = type;
            return this;
        }

        /**
         * Specifies a list of feature modules on which this module will depend. This is only valid
         * for feature manifests.
         *
         * @param names the names of the dynamic features.
         * @return itself.
         */
        public Invoker addDependencyFeatureNames(@NonNull Iterable<String> names) {
            this.mDependencyFetureNamesBuilder.addAll(names);
            return this;
        }

        /**
         * Add a namespace that is allowed to appear in more than one library.
         *
         * @param namespace the namespace
         * @return itself
         */
        public Invoker addAllowedNonUniqueNamespace(String namespace) {
            this.mAllowedNonUniqueNamespaces.add(namespace);
            return this;
        }

        /**
         * Set the location of the generated locale config to add to the manifest.
         *
         * @param generatedLocaleConfigAttribute the attribute pointing to the generated locale
         *     config.
         * @return itself
         */
        public Invoker setGeneratedLocaleConfigAttribute(String generatedLocaleConfigAttribute) {
            this.mGeneratedLocaleConfigAttribute = generatedLocaleConfigAttribute;
            return this;
        }

        /**
         * Perform the merging and return the result.
         *
         * @return an instance of {@link MergingReport} that will give access to all the logging and
         *     merging records.
         *     <p>This method can be invoked several time and will re-do the file merges.
         * @throws MergeFailureException if the merging cannot be completed successfully.
         */
        @NonNull
        public MergingReport merge() throws MergeFailureException {

            // provide some free placeholders values.
            ImmutableMap<ManifestSystemProperty, Object> systemProperties =
                    mSystemProperties.build();
            if (systemProperties.containsKey(ManifestSystemProperty.Document.PACKAGE)) {
                // if the package is provided, make it available for placeholder replacement.
                mPlaceholders.put(
                        PACKAGE_NAME,
                        systemProperties.get(ManifestSystemProperty.Document.PACKAGE));
                // as well as applicationId since package system property overrides everything
                // but not when output is a library since only the final (application)
                // application Id should be used to replace libraries "applicationId" placeholders.
                if (mMergeType != MergeType.LIBRARY) {
                    mPlaceholders.put(
                            APPLICATION_ID,
                            systemProperties.get(ManifestSystemProperty.Document.PACKAGE));
                }
            }

            FileStreamProvider fileStreamProvider = mFileStreamProvider != null
                    ? mFileStreamProvider : new FileStreamProvider();
            ProcessCancellationChecker processCancellationChecker =
                    mProcessCancellationChecker != null ? mProcessCancellationChecker : () -> {};
            addAllowedNonUniqueNamespace("androidx.test"); // TODO(b/151171905)
            ManifestMerger2 manifestMerger =
                    new ManifestMerger2(
                            mLogger,
                            mMainManifestFile,
                            mLibraryFilesBuilder.build(),
                            mFlavorsAndBuildTypeFiles.build(),
                            mFeaturesBuilder.build(),
                            mPlaceholders.build(),
                            new MapBasedKeyBasedValueResolver<>(systemProperties),
                            mMergeType,
                            mDocumentType,
                            Optional.ofNullable(mReportFile),
                            mFeatureName,
                            mNamespace,
                            fileStreamProvider,
                            mManifestDocumentProvider,
                            processCancellationChecker,
                            mNavigationFilesBuilder.build(),
                            mNavigationJsonsBuilder.build(),
                            mDependencyFetureNamesBuilder.build(),
                            mAllowedNonUniqueNamespaces.build(),
                            mGeneratedLocaleConfigAttribute);

            return manifestMerger.merge();
        }
    }

    /**
     * Helper class for map based placeholders key value pairs.
     */
    public static class MapBasedKeyBasedValueResolver<T> implements KeyBasedValueResolver<T> {

        private final ImmutableMap<T, Object> keyValues;

        public MapBasedKeyBasedValueResolver(@NonNull Map<T, Object> keyValues) {
            this.keyValues = ImmutableMap.copyOf(keyValues);
        }

        @Nullable
        @Override
        public String getValue(@NonNull T key) {
            Object value = keyValues.get(key);
            return value == null ? null : value.toString();
        }
    }

    private static class ManifestInfo {

        private ManifestInfo(String name, File location, XmlDocument.Type type) {
            mName = name;
            mLocation = location;
            mType = type;
        }

        private final String mName;
        private final File mLocation;
        private final XmlDocument.Type mType;

        String getName() {
            return mName;
        }

        File getLocation() {
            return mLocation;
        }

        XmlDocument.Type getType() {
            return mType;
        }
    }

    private static class LoadedManifestInfo extends ManifestInfo {

        @NonNull private final XmlDocument mXmlDocument;

        private LoadedManifestInfo(@NonNull ManifestInfo manifestInfo,
                @NonNull XmlDocument xmlDocument) {
            super(manifestInfo.mName, manifestInfo.mLocation, manifestInfo.mType);
            mXmlDocument = xmlDocument;
        }

        @NonNull
        public XmlDocument getXmlDocument() {
            return mXmlDocument;
        }

        @Nullable
        public String getNamespace() {
            return mXmlDocument.getNamespace();
        }
    }

    /**
     * Implementation a {@link KeyResolver} capable of resolving all selectors value in the context
     * of the passed libraries to this merging activities.
     */
    public static class SelectorResolver implements KeyResolver<String> {

        private final Map<String, String> mSelectors = new HashMap<>();

        protected void addSelector(String key, String value) {
            mSelectors.put(key, value);
        }

        @Nullable
        @Override
        public String resolve(String key) {
            return mSelectors.get(key);
        }

        @NonNull
        @Override
        public Iterable<String> getKeys() {
            return mSelectors.keySet();
        }
    }

    // a wrapper exception to all sorts of failure exceptions that can be thrown during merging.
    public static class MergeFailureException extends Exception {

        protected MergeFailureException(String msg, Exception cause) {
            super(msg, cause);
        }

        protected MergeFailureException(Exception cause) {
            super(cause);
        }
    }
}
