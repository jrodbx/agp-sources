/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.ViewBindingOptions;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of an {@link AndroidProject}. */
public final class IdeAndroidProjectImpl implements IdeAndroidProject, Serializable {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 10L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<IdeVariant> myVariants;
    @NonNull private final Collection<String> myVariantNames;
    @Nullable private final String myDefaultVariant;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClassPath;
    @NonNull private final Collection<NativeToolchain> myNativeToolchains;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final IdeLintOptions myLintOptions;
    @Nullable private final List<File> myLintRuleJars;
    @NonNull private final Set<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final AaptOptions myAaptOptions;
    @NonNull private final File myBuildFolder;
    @NonNull private final Collection<String> myDynamicFeatures;
    @NonNull private final Collection<IdeVariantBuildInformation> myVariantBuildInformation;
    @Nullable private final ViewBindingOptions myViewBindingOptions;
    @Nullable private final IdeDependenciesInfo myDependenciesInfo;
    @Nullable private final GradleVersion myParsedModelVersion;
    @Nullable private final String myBuildToolsVersion;
    @Nullable private final String myNdkVersion;
    @Nullable private final String myResourcePrefix;
    @Nullable private final String myGroupId;
    private final boolean mySupportsPluginGeneration;
    private final int myApiVersion;
    private final int myProjectType;
    private final boolean myBaseSplit;
    @NonNull private final IdeAndroidGradlePluginProjectFlags myAgpFlags;
    private final int myHashCode;

    public static IdeAndroidProjectImpl create(
            @NonNull AndroidProject project,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable Collection<Variant> variants,
            @NotNull Collection<SyncIssue> syncIssues) {
        return create(project, new ModelCache(), dependenciesFactory, variants, syncIssues);
    }

    @VisibleForTesting
    public static IdeAndroidProjectImpl create(
            @NonNull AndroidProject project,
            @NonNull ModelCache modelCache,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable Collection<Variant> variants,
            @NotNull Collection<SyncIssue> syncIssues) {
        // Old plugin versions do not return model version.
        GradleVersion parsedModelVersion = GradleVersion.tryParse(project.getModelVersion());

        ProductFlavorContainer defaultConfigCopy =
                modelCache.computeIfAbsent(
                        project.getDefaultConfig(),
                        container -> new IdeProductFlavorContainer(container, modelCache));

        Collection<BuildTypeContainer> buildTypesCopy =
                IdeModel.copy(
                        project.getBuildTypes(),
                        modelCache,
                        container -> new IdeBuildTypeContainer(container, modelCache));

        Collection<ProductFlavorContainer> productFlavorCopy =
                IdeModel.copy(
                        project.getProductFlavors(),
                        modelCache,
                        container -> new IdeProductFlavorContainer(container, modelCache));

        Collection<SyncIssue> syncIssuesCopy =
                new ArrayList<>(IdeModel.copy(syncIssues, modelCache, IdeSyncIssue::new));

        Collection<IdeVariant> variantsCopy =
                new ArrayList<IdeVariant>(
                        IdeModel.copy(
                                (variants == null) ? project.getVariants() : variants,
                                modelCache,
                                variant ->
                                        new IdeVariantImpl(
                                                variant,
                                                modelCache,
                                                dependenciesFactory,
                                                parsedModelVersion)));

        Collection<String> variantNamesCopy =
                Objects.requireNonNull(
                        IdeModel.copyNewPropertyWithDefault(
                                () -> ImmutableList.copyOf(project.getVariantNames()),
                                () -> computeVariantNames(variantsCopy)));

        String defaultVariantCopy =
                IdeModel.copyNewPropertyWithDefault(
                        project::getDefaultVariant, () -> getDefaultVariant(variantNamesCopy));

        Collection<String> flavorDimensionCopy =
                IdeModel.copyNewPropertyNonNull(
                        () -> ImmutableList.copyOf(project.getFlavorDimensions()),
                        Collections.emptyList());

        Collection<String> bootClasspathCopy = ImmutableList.copyOf(project.getBootClasspath());

        Collection<NativeToolchain> nativeToolchainsCopy =
                IdeModel.copy(
                        project.getNativeToolchains(),
                        modelCache,
                        toolChain -> new IdeNativeToolchain(toolChain));

        Collection<SigningConfig> signingConfigsCopy =
                IdeModel.copy(
                        project.getSigningConfigs(),
                        modelCache,
                        config -> new IdeSigningConfig(config));

        IdeLintOptions lintOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getLintOptions(),
                        options -> new IdeLintOptions(options, parsedModelVersion));

        // We need to use the unresolved dependencies to support older versions of the Android
        // Gradle Plugin.
        //noinspection deprecation
        Set<String> unresolvedDependenciesCopy =
                ImmutableSet.copyOf(project.getUnresolvedDependencies());

        IdeJavaCompileOptions javaCompileOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getJavaCompileOptions(),
                        options -> new IdeJavaCompileOptions(options));

        IdeAaptOptions aaptOptionsCopy =
                modelCache.computeIfAbsent(
                        project.getAaptOptions(), options -> new IdeAaptOptions(options));

        Collection<String> dynamicFeaturesCopy =
                ImmutableList.copyOf(
                        IdeModel.copyNewPropertyNonNull(
                                project::getDynamicFeatures, ImmutableList.of()));

        Collection<IdeVariantBuildInformation> variantBuildInformation =
                createVariantBuildInformation(project, parsedModelVersion);

        IdeViewBindingOptions viewBindingOptionsCopy =
                IdeModel.copyNewProperty(
                        () -> new IdeViewBindingOptions(project.getViewBindingOptions()), null);

        IdeDependenciesInfo dependenciesInfoCopy =
                IdeModel.copyNewProperty(
                        () -> IdeDependenciesInfo.createOrNull(project.getDependenciesInfo()),
                        null);

        String buildToolsVersionCopy =
                IdeModel.copyNewProperty(project::getBuildToolsVersion, null);

        String ndkVersionCopy = IdeModel.copyNewProperty(project::getNdkVersion, null);

        String groupId = null;
        if (parsedModelVersion != null
                && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) {
            groupId = project.getGroupId();
        }

        List<File> lintRuleJarsCopy =
                IdeModel.copyNewProperty(
                        () -> ImmutableList.copyOf(project.getLintRuleJars()), null);

        // AndroidProject#isBaseSplit is always non null.
        //noinspection ConstantConditions
        boolean isBaseSplit = IdeModel.copyNewProperty(project::isBaseSplit, false);

        IdeAndroidGradlePluginProjectFlags agpFlags =
                Objects.requireNonNull(
                        IdeModel.copyNewProperty(
                                modelCache,
                                project::getFlags,
                                IdeAndroidGradlePluginProjectFlags::new,
                                new IdeAndroidGradlePluginProjectFlags()));

        return new IdeAndroidProjectImpl(
                project.getModelVersion(),
                parsedModelVersion,
                project.getName(),
                defaultConfigCopy,
                buildTypesCopy,
                productFlavorCopy,
                syncIssuesCopy,
                variantsCopy,
                variantNamesCopy,
                defaultVariantCopy,
                flavorDimensionCopy,
                project.getCompileTarget(),
                bootClasspathCopy,
                nativeToolchainsCopy,
                signingConfigsCopy,
                lintOptionsCopy,
                lintRuleJarsCopy,
                unresolvedDependenciesCopy,
                javaCompileOptionsCopy,
                aaptOptionsCopy,
                project.getBuildFolder(),
                dynamicFeaturesCopy,
                variantBuildInformation,
                viewBindingOptionsCopy,
                dependenciesInfoCopy,
                buildToolsVersionCopy,
                ndkVersionCopy,
                project.getResourcePrefix(),
                groupId,
                IdeModel.copyNewProperty(project::getPluginGeneration, null) != null,
                project.getApiVersion(),
                getProjectType(project, parsedModelVersion),
                isBaseSplit,
                agpFlags);
    }

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    private IdeAndroidProjectImpl() {
        myModelVersion = "";
        myParsedModelVersion = null;
        myName = "";
        myDefaultConfig = new IdeProductFlavorContainer();
        myBuildTypes = Collections.emptyList();
        myProductFlavors = Collections.emptyList();
        mySyncIssues = Collections.emptyList();
        myVariants = Collections.emptyList();
        myVariantNames = Collections.emptyList();
        myDefaultVariant = null;
        myFlavorDimensions = Collections.emptyList();
        myCompileTarget = "";
        myBootClassPath = Collections.emptyList();
        myNativeToolchains = Collections.emptyList();
        mySigningConfigs = Collections.emptyList();
        myLintOptions = new IdeLintOptions();
        myLintRuleJars = Collections.emptyList();
        myUnresolvedDependencies = Collections.emptySet();
        myJavaCompileOptions = new IdeJavaCompileOptions();
        myAaptOptions = new IdeAaptOptions();
        //noinspection ConstantConditions
        myBuildFolder = null;
        myDynamicFeatures = Collections.emptyList();
        myVariantBuildInformation = Collections.emptyList();
        myViewBindingOptions = new IdeViewBindingOptions();
        myDependenciesInfo = new IdeDependenciesInfo();
        myBuildToolsVersion = null;
        myNdkVersion = null;
        myResourcePrefix = null;
        myGroupId = null;
        mySupportsPluginGeneration = false;
        myApiVersion = 0;
        myProjectType = 0;
        myBaseSplit = false;
        myAgpFlags = new IdeAndroidGradlePluginProjectFlags();
        myHashCode = 0;
    }

    private IdeAndroidProjectImpl(
            @NonNull String modelVersion,
            @Nullable GradleVersion parsedModelVersion,
            @NonNull String name,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<BuildTypeContainer> buildTypes,
            @NonNull Collection<ProductFlavorContainer> productFlavors,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull Collection<IdeVariant> variants,
            @NonNull Collection<String> variantNames,
            @Nullable String defaultVariant,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClassPath,
            @NonNull Collection<NativeToolchain> nativeToolchains,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull IdeLintOptions lintOptions,
            @Nullable List<File> lintRuleJars,
            @NonNull Set<String> unresolvedDependencies,
            @NonNull JavaCompileOptions javaCompileOptions,
            @NonNull AaptOptions aaptOptions,
            @NonNull File buildFolder,
            @NonNull Collection<String> dynamicFeatures,
            @NonNull Collection<IdeVariantBuildInformation> variantBuildInformation,
            @Nullable ViewBindingOptions viewBindingOptions,
            @Nullable IdeDependenciesInfo dependenciesInfo,
            @Nullable String buildToolsVersion,
            @Nullable String ndkVersion,
            @Nullable String resourcePrefix,
            @Nullable String groupId,
            boolean supportsPluginGeneration,
            int apiVersion,
            int projectType,
            boolean baseSplit,
            @NonNull IdeAndroidGradlePluginProjectFlags agpFlags) {
        myModelVersion = modelVersion;
        myParsedModelVersion = parsedModelVersion;
        myName = name;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myVariantNames = variantNames;
        myDefaultVariant = defaultVariant;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClassPath = bootClassPath;
        myNativeToolchains = nativeToolchains;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myLintRuleJars = lintRuleJars;
        myUnresolvedDependencies = unresolvedDependencies;
        myJavaCompileOptions = javaCompileOptions;
        myAaptOptions = aaptOptions;
        myBuildFolder = buildFolder;
        myDynamicFeatures = dynamicFeatures;
        myVariantBuildInformation = variantBuildInformation;
        myViewBindingOptions = viewBindingOptions;
        myDependenciesInfo = dependenciesInfo;
        myBuildToolsVersion = buildToolsVersion;
        myNdkVersion = ndkVersion;
        myResourcePrefix = resourcePrefix;
        myGroupId = groupId;
        mySupportsPluginGeneration = supportsPluginGeneration;
        myApiVersion = apiVersion;
        myProjectType = projectType;
        myBaseSplit = baseSplit;
        myAgpFlags = agpFlags;
        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Collection<IdeVariantBuildInformation> createVariantBuildInformation(
            @NonNull AndroidProject project, @Nullable GradleVersion agpVersion) {
        if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.1.0") >= 0) {
            // make deep copy of VariantBuildInformation.
            return project.getVariantsBuildInformation().stream()
                    .map(it -> new IdeVariantBuildInformation(it))
                    .collect(ImmutableList.toImmutableList());
        }
        // VariantBuildInformation is not available.
        return Collections.emptyList();
    }

    @NonNull
    private static ImmutableList<String> computeVariantNames(Collection<IdeVariant> variants) {
        return variants.stream().map(Variant::getName).collect(ImmutableList.toImmutableList());
    }

    private static int getProjectType(
            @NonNull AndroidProject project, @Nullable GradleVersion modelVersion) {
        if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
            return project.getProjectType();
        }
        // Support for old Android Gradle Plugins must be maintained.
        //noinspection deprecation
        return project.isLibrary() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;
    }

    /** For older AGP versions pick a variant name based on a heuristic */
    @VisibleForTesting
    @Nullable
    static String getDefaultVariant(Collection<String> variantNames) {
        // Corner case of variant filter accidentally removing all variants.
        if (variantNames.isEmpty()) {
            return null;
        }

        // Favor the debug variant
        if (variantNames.contains("debug")) {
            return "debug";
        }
        // Otherwise the first alphabetically that has debug as a build type.
        ImmutableSortedSet<String> sortedNames = ImmutableSortedSet.copyOf(variantNames);
        for (String variantName : sortedNames) {
            if (variantName.endsWith("Debug")) {
                return variantName;
            }
        }
        // Otherwise fall back to the first alphabetically
        return sortedNames.first();
    }

    @Override
    @Nullable
    public GradleVersion getParsedModelVersion() {
        return myParsedModelVersion;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
        return myDefaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return myBuildTypes;
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public String getBuildToolsVersion() {
        if (myBuildToolsVersion != null) {
            return myBuildToolsVersion;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: AndroidProject.getBuildToolsVersion()");
    }

    @Override
    @NonNull
    public String getNdkVersion() {
        return myNdkVersion;
    }

    @Override
    @NonNull
    public Collection<SyncIssue> getSyncIssues() {
        return ImmutableList.copyOf(mySyncIssues);
    }

    @Override
    @NonNull
    public Collection<IdeVariant> getVariants() {
        return ImmutableList.copyOf(myVariants);
    }

    @Override
    @NonNull
    public Collection<String> getVariantNames() {
        return myVariantNames;
    }

    @Nullable
    @Override
    public String getDefaultVariant() {
        return myDefaultVariant;
    }

    @Override
    @NonNull
    public Collection<String> getFlavorDimensions() {
        return myFlavorDimensions;
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return myCompileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return myBootClassPath;
    }

    @Override
    @NonNull
    public AaptOptions getAaptOptions() {
        return myAaptOptions;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return mySigningConfigs;
    }

    @Override
    @NonNull
    public IdeLintOptions getLintOptions() {
        return myLintOptions;
    }

    @Deprecated
    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return myUnresolvedDependencies;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return myJavaCompileOptions;
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return myBuildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return myResourcePrefix;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @NonNull
    @Override
    public Collection<String> getDynamicFeatures() {
        return myDynamicFeatures;
    }

    @Nullable
    @Override
    public ViewBindingOptions getViewBindingOptions() {
        return myViewBindingOptions;
    }

    @Nullable
    @Override
    public IdeDependenciesInfo getDependenciesInfo() {
        return myDependenciesInfo;
    }

    @Nullable
    @Override
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    public IdeAndroidGradlePluginProjectFlags getAgpFlags() {
        return myAgpFlags;
    }

    @Override
    public void forEachVariant(@NonNull Consumer<IdeVariant> action) {
        for (Variant variant : myVariants) {
            action.accept((IdeVariant) variant);
        }
    }

    @Override
    public void addVariants(@NonNull Collection<IdeVariant> variants) {
        Set<String> variantNames =
                myVariants.stream().map(variant -> variant.getName()).collect(Collectors.toSet());
        for (IdeVariant variant : variants) {
            // Add cached IdeVariant only if it is not contained in the current model.
            if (!variantNames.contains(variant.getName())) {
                myVariants.add(variant);
            }
        }
    }

    @Override
    public void addSyncIssues(@NonNull Collection<SyncIssue> syncIssues) {
        Set<SyncIssue> currentSyncIssues = new HashSet<>(mySyncIssues);
        for (SyncIssue issue : syncIssues) {
            // Only add the sync issues that are not seen from previous sync.
            IdeSyncIssue newSyncIssue = new IdeSyncIssue(issue);
            if (!currentSyncIssues.contains(newSyncIssue)) {
                mySyncIssues.add(newSyncIssue);
            }
        }
    }

    @NonNull
    @Override
    public Collection<IdeVariantBuildInformation> getVariantsBuildInformation() {
        return myVariantBuildInformation;
    }

    @Nullable
    @Override
    public List<File> getLintRuleJars() {
        return myLintRuleJars;
    }

    @Nullable private transient Map<String, Object> clientProperties;

    @Nullable
    @Override
    public Object putClientProperty(@NonNull String key, @Nullable Object value) {
        if (value == null) {
            if (clientProperties != null) {
                clientProperties.remove(key);
            }
        } else {
            if (clientProperties == null) {
                clientProperties = new HashMap<>();
            }
            clientProperties.put(key, value);
        }

        return value;
    }

    @Nullable
    @Override
    public Object getClientProperty(@NonNull String key) {
        if (clientProperties == null) {
            return null;
        } else {
            return clientProperties.get(key);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidProjectImpl)) {
            return false;
        }
        IdeAndroidProjectImpl project = (IdeAndroidProjectImpl) o;
        return myApiVersion == project.myApiVersion
                && myProjectType == project.myProjectType
                && myBaseSplit == project.myBaseSplit
                && mySupportsPluginGeneration == project.mySupportsPluginGeneration
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myParsedModelVersion, project.myParsedModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myDefaultConfig, project.myDefaultConfig)
                && Objects.equals(myBuildTypes, project.myBuildTypes)
                && Objects.equals(myProductFlavors, project.myProductFlavors)
                && Objects.equals(myBuildToolsVersion, project.myBuildToolsVersion)
                && Objects.equals(myNdkVersion, project.myNdkVersion)
                && Objects.equals(mySyncIssues, project.mySyncIssues)
                && Objects.equals(myVariants, project.myVariants)
                && Objects.equals(myVariantNames, project.myVariantNames)
                && Objects.equals(myDefaultVariant, project.myDefaultVariant)
                && Objects.equals(myFlavorDimensions, project.myFlavorDimensions)
                && Objects.equals(myCompileTarget, project.myCompileTarget)
                && Objects.equals(myBootClassPath, project.myBootClassPath)
                && Objects.equals(myNativeToolchains, project.myNativeToolchains)
                && Objects.equals(mySigningConfigs, project.mySigningConfigs)
                && Objects.equals(myLintOptions, project.myLintOptions)
                && Objects.equals(myLintRuleJars, project.myLintRuleJars)
                && Objects.equals(myUnresolvedDependencies, project.myUnresolvedDependencies)
                && Objects.equals(myJavaCompileOptions, project.myJavaCompileOptions)
                && Objects.equals(myAaptOptions, project.myAaptOptions)
                && Objects.equals(myBuildFolder, project.myBuildFolder)
                && Objects.equals(myResourcePrefix, project.myResourcePrefix)
                && Objects.equals(myDynamicFeatures, project.myDynamicFeatures)
                && Objects.equals(myViewBindingOptions, project.myViewBindingOptions)
                && Objects.equals(myDependenciesInfo, project.myDependenciesInfo)
                && Objects.equals(myGroupId, project.myGroupId)
                && Objects.equals(myAgpFlags, project.myAgpFlags)
                && Objects.equals(myVariantBuildInformation, project.myVariantBuildInformation);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myParsedModelVersion,
                myName,
                myDefaultConfig,
                myBuildTypes,
                myProductFlavors,
                myBuildToolsVersion,
                myNdkVersion,
                mySyncIssues,
                myVariants,
                myVariantNames,
                myDefaultVariant,
                myFlavorDimensions,
                myCompileTarget,
                myBootClassPath,
                myNativeToolchains,
                mySigningConfigs,
                myLintOptions,
                myLintRuleJars,
                myUnresolvedDependencies,
                myJavaCompileOptions,
                myBuildFolder,
                myResourcePrefix,
                myApiVersion,
                myProjectType,
                mySupportsPluginGeneration,
                myAaptOptions,
                myBaseSplit,
                myDynamicFeatures,
                myViewBindingOptions,
                myDependenciesInfo,
                myGroupId,
                myAgpFlags,
                myVariantBuildInformation);
    }

    @Override
    public String toString() {
        return "IdeAndroidProject{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myDefaultConfig="
                + myDefaultConfig
                + ", myBuildTypes="
                + myBuildTypes
                + ", myProductFlavors="
                + myProductFlavors
                + ", myBuildToolsVersion='"
                + myBuildToolsVersion
                + ", myNdkVersion='"
                + myNdkVersion
                + '\''
                + ", mySyncIssues="
                + mySyncIssues
                + ", myVariants="
                + myVariants
                + ", myVariantNames="
                + myVariantNames
                + ", myDefaultVariant="
                + myDefaultVariant
                + ", myFlavorDimensions="
                + myFlavorDimensions
                + ", myCompileTarget='"
                + myCompileTarget
                + '\''
                + ", myBootClassPath="
                + myBootClassPath
                + ", myNativeToolchains="
                + myNativeToolchains
                + ", mySigningConfigs="
                + mySigningConfigs
                + ", myLintOptions="
                + myLintOptions
                + ", myUnresolvedDependencies="
                + myUnresolvedDependencies
                + ", myJavaCompileOptions="
                + myJavaCompileOptions
                + ", myBuildFolder="
                + myBuildFolder
                + ", myResourcePrefix='"
                + myResourcePrefix
                + '\''
                + ", myApiVersion="
                + myApiVersion
                + ", myProjectType="
                + myProjectType
                + ", mySupportsPluginGeneration="
                + mySupportsPluginGeneration
                + ", myAaptOptions="
                + myAaptOptions
                + ", myBaseSplit="
                + myBaseSplit
                + ", myDynamicFeatures="
                + myDynamicFeatures
                + ", myViewBindingOptions="
                + myViewBindingOptions
                + ", myDependenciesInfo="
                + myDependenciesInfo
                + ", myGroupId="
                + myGroupId
                + ", myAgpFlags="
                + myAgpFlags
                + ", myVariantBuildInformation="
                + myVariantBuildInformation
                + "}";
    }
}
