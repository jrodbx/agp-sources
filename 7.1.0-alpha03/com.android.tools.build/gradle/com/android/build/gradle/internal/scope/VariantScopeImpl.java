/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToConfiguration;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToDefaultVariant;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_APK_CREATOR;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_JAR_CREATOR;
import static com.android.builder.core.VariantTypeImpl.UNIT_TEST;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.ProguardFileType;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.PostProcessingOptions;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dependency.AndroidAttributes;
import com.android.build.gradle.internal.dependency.ProvidedClasspath;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.packaging.JarCreatorType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.PublishedConfigSpec;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.testFixtures.TestFixturesUtil;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.VariantType;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

/** A scope containing data for a specific variant. */
public class VariantScopeImpl implements VariantScope {

    // Variant specific Data
    @NonNull private final ComponentIdentity componentIdentity;
    @NonNull private final VariantDslInfo variantDslInfo;
    @NonNull private final VariantPathHelper pathHelper;
    @NonNull private final ArtifactsImpl artifacts;
    @NonNull private final VariantDependencies variantDependencies;

    @NonNull private final PublishingSpecs.VariantSpec variantPublishingSpec;
    @Nullable private final VariantImpl testedVariantProperties;

    // Global Data
    @NonNull private final GlobalScope globalScope;

    // other

    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @NonNull private final PostProcessingOptions postProcessingOptions;
    @NonNull private final BaseServices baseServices;

    public VariantScopeImpl(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo<?> variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantPathHelper pathHelper,
            @NonNull ArtifactsImpl artifacts,
            @NonNull BaseServices baseServices,
            @NonNull GlobalScope globalScope,
            @Nullable VariantImpl testedVariantProperties) {
        this.componentIdentity = componentIdentity;
        this.variantDslInfo = variantDslInfo;
        this.variantDependencies = variantDependencies;
        this.pathHelper = pathHelper;
        this.artifacts = artifacts;
        this.baseServices = baseServices;
        this.globalScope = globalScope;
        this.variantPublishingSpec =
                PublishingSpecs.getVariantSpec(variantDslInfo.getVariantType());
        this.testedVariantProperties = testedVariantProperties;

        if (globalScope.isActive(OptionalCompilationStep.INSTANT_DEV)) {
            throw new RuntimeException("InstantRun mode is not supported");
        }
        this.postProcessingOptions = variantDslInfo.getPostProcessingOptions();

        configureNdk();
    }

    private void configureNdk() {
        File objFolder =
                pathHelper
                        .intermediatesDir("ndk", variantDslInfo.getDirName(), "obj")
                        .get()
                        .getAsFile();

        for (Abi abi : Abi.values()) {
            addNdkDebuggableLibraryFolders(abi, new File(objFolder, "local/" + abi.getTag()));
        }
    }

    @Override
    @NonNull
    public PublishingSpecs.VariantSpec getPublishingSpec() {
        return variantPublishingSpec;
    }

    /**
     * Publish an intermediate artifact.
     *
     * @param artifact Provider of File or FileSystemLocation to be published.
     * @param artifactType the artifact type.
     * @param configSpecs the PublishedConfigSpec.
     * @param libraryElements the artifact's library elements
     * @param isTestFixturesArtifact whether the artifact is from a test fixtures component
     */
    @Override
    public void publishIntermediateArtifact(
            @NonNull Provider<?> artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Set<PublishedConfigSpec> configSpecs,
            @Nullable LibraryElements libraryElements,
            boolean isTestFixturesArtifact) {

        Preconditions.checkState(!configSpecs.isEmpty());

        for (PublishedConfigSpec configSpec : configSpecs) {
            Configuration config = variantDependencies.getElements(configSpec);
            PublishedConfigType configType = configSpec.getConfigType();
            if (config != null) {
                if (configType.isPublicationConfig()) {
                    String classifier = null;
                    if (configSpec.isClassifierRequired()) {
                        classifier = componentIdentity.getName();
                    } else if (isTestFixturesArtifact) {
                        classifier = TestFixturesUtil.testFixturesClassifier;
                    }
                    publishArtifactToDefaultVariant(config, artifact, artifactType, classifier);
                } else {
                    publishArtifactToConfiguration(
                            config,
                            artifact,
                            artifactType,
                            new AndroidAttributes(null, libraryElements));
                }
            }
        }
    }

    @Override
    public boolean isCrunchPngs() {
        // If set for this build type, respect that.
        Boolean buildTypeOverride = variantDslInfo.isCrunchPngs();
        if (buildTypeOverride != null) {
            return buildTypeOverride;
        }
        // Otherwise, if set globally, respect that.
        Boolean globalOverride =
                baseServices
                        .getProjectInfo()
                        .getExtension()
                        .getAaptOptions()
                        .getCruncherEnabledOverride();
        if (globalOverride != null) {
            return globalOverride;
        }
        // If not overridden, use the default from the build type.
        //noinspection deprecation TODO: Remove once the global cruncher enabled flag goes away.
        return variantDslInfo.isCrunchPngsDefault();
    }

    @Override
    public boolean consumesFeatureJars() {
        return variantDslInfo.getVariantType().isBaseModule()
                && variantDslInfo.getPostProcessingOptions().codeShrinkerEnabled()
                && globalScope.hasDynamicFeatures();
    }

    @Override
    public boolean getNeedsJavaResStreams() {
        // We need to create original java resource stream only if we're in a library module with
        // custom transforms.
        return variantDslInfo.getVariantType().isAar()
                && !globalScope.getExtension().getTransforms().isEmpty();
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return gatherProguardFiles(ProguardFileType.CONSUMER);
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFilesForFeatures() {
        // We include proguardFiles if we're in a dynamic-feature module.
        final boolean includeProguardFiles = variantDslInfo.getVariantType().isDynamicFeature();
        final Collection<File> consumerProguardFiles = getConsumerProguardFiles();
        if (includeProguardFiles) {
            consumerProguardFiles.addAll(gatherProguardFiles(ProguardFileType.EXPLICIT));
        }

        return ImmutableList.copyOf(consumerProguardFiles);
    }

    @NonNull
    private List<File> gatherProguardFiles(ProguardFileType type) {
        ListProperty<RegularFile> regularFiles =
                baseServices
                        .getProjectInfo()
                        .getProject()
                        .getObjects()
                        .listProperty(RegularFile.class);
        variantDslInfo.gatherProguardFiles(type, regularFiles);
        return regularFiles.get().stream().map(RegularFile::getAsFile).collect(Collectors.toList());
    }

    @Override
    @Nullable
    public PostprocessingFeatures getPostprocessingFeatures() {
        return postProcessingOptions.getPostprocessingFeatures();
    }

    /**
     * Determine if the final output should be marked as testOnly to prevent uploading to Play
     * store.
     *
     * <p>Uploading to Play store is disallowed if:
     *
     * <ul>
     *   <li>An injected option is set (usually by the IDE for testing purposes).
     *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
     * </ul>
     *
     * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
     *
     * @param variant {@link VariantImpl} for this variant scope.
     */
    @Override
    public boolean isTestOnly(VariantImpl variant) {
        ProjectOptions projectOptions = baseServices.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY))
                || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                || isPreviewTargetPlatform()
                || variant.getMinSdkVersion().getCodename() != null
                || variant.getTargetSdkVersion().getCodename() != null;
    }

    private boolean isPreviewTargetPlatform() {
        AndroidVersion version =
                AndroidTargetHash.getVersionFromHash(
                        globalScope.getExtension().getCompileSdkVersion());
        return version != null && version.isPreview();
    }

    /**
     * Returns if core library desugaring is enabled.
     *
     * <p>Java language desugaring and multidex are required for enabling core library desugaring.
     */
    @Override
    public boolean isCoreLibraryDesugaringEnabled(ConsumableCreationConfig creationConfig) {
        BaseExtension extension = globalScope.getExtension();

        boolean libDesugarEnabled =
                extension.getCompileOptions().getCoreLibraryDesugaringEnabled() != null
                        && extension.getCompileOptions().getCoreLibraryDesugaringEnabled();

        boolean multidexEnabled = creationConfig.isMultiDexEnabled();

        Java8LangSupport langSupportType = creationConfig.getJava8LangSupportType();
        boolean langDesugarEnabled =
                langSupportType == Java8LangSupport.D8 || langSupportType == Java8LangSupport.R8;

        if (libDesugarEnabled && !langDesugarEnabled) {
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "In order to use core library desugaring, "
                                    + "please enable java 8 language desugaring with D8 or R8.");
        }

        if (libDesugarEnabled && !multidexEnabled) {
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "In order to use core library desugaring, "
                                    + "please enable multidex.");
        }
        return libDesugarEnabled;
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    // Precomputed file paths.

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    @Override
    public FileCollection getLocalPackagedJars() {
        return getLocalFileDependencies(
                (file) -> file.getName().toLowerCase(Locale.US).endsWith(DOT_JAR));
    }

    /**
     * Returns the direct (i.e., non-transitive) local file dependencies matching the given
     * predicate
     *
     * @return a non null, but possibly empty FileCollection
     * @param filePredicate the file predicate used to filter the local file dependencies
     */
    @NonNull
    @Override
    public FileCollection getLocalFileDependencies(Predicate<File> filePredicate) {
        Configuration configuration = variantDependencies.getRuntimeClasspath();

        // Get a list of local file dependencies. There is currently no API to filter the
        // files here, so we need to filter it in the return statement below. That means that if,
        // for example, filePredicate filters out all files but jars in the return statement, but an
        // AarProducerTask produces an aar, then the returned FileCollection contains only jars but
        // still has AarProducerTask as a dependency.
        Callable<Collection<SelfResolvingDependency>> dependencies =
                () ->
                        configuration
                                .getAllDependencies()
                                .stream()
                                .filter((it) -> it instanceof SelfResolvingDependency)
                                .filter((it) -> !(it instanceof ProjectDependency))
                                .map((it) -> (SelfResolvingDependency) it)
                                .collect(ImmutableList.toImmutableList());

        // Create a file collection builtBy the dependencies.  The files are resolved later.
        return baseServices
                .getProjectInfo()
                .getProject()
                .files(
                        (Callable<Collection<File>>)
                                () ->
                                        dependencies.call().stream()
                                                .flatMap((it) -> it.resolve().stream())
                                                .filter(filePredicate)
                                                .collect(Collectors.toList()))
                .builtBy(dependencies);
    }

    @NonNull
    @Override
    public FileCollection getProvidedOnlyClasspath() {
        ArtifactCollection compile =
                variantDependencies.getArtifactCollection(COMPILE_CLASSPATH, ALL, CLASSES_JAR);
        ArtifactCollection runtime =
                variantDependencies.getArtifactCollection(RUNTIME_CLASSPATH, ALL, CLASSES_JAR);

        return ProvidedClasspath.getProvidedClasspath(compile, runtime);
    }

    @NonNull
    @Override
    public Provider<RegularFile> getRJarForUnitTests() {
        VariantType variantType = variantDslInfo.getVariantType();
        checkNotNull(
                testedVariantProperties,
                "Variant type does not have a tested variant: " + variantType);
        checkState(variantType == UNIT_TEST, "Expected unit test type but found: " + variantType);

        if (testedVariantProperties.getVariantType().isAar()) {
            return artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR.INSTANCE);
        } else {
            checkState(
                    testedVariantProperties.getVariantType().isApk(),
                    "Expected APK type but found: " + testedVariantProperties.getVariantType());
            return testedVariantProperties
                    .getArtifacts()
                    .get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR.INSTANCE);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(componentIdentity.getName()).toString();
    }

    @NonNull
    @Override
    public FileCollection getBootClasspath() {
        return baseServices.getProjectInfo().getProject().files(globalScope.getBootClasspath());
    }

    @NonNull
    @Override
    public JarCreatorType getJarCreatorType() {
        if (baseServices.getProjectOptions().get(USE_NEW_JAR_CREATOR)) {
            return JarCreatorType.JAR_FLINGER;
        } else {
            return JarCreatorType.JAR_MERGER;
        }
    }

    @NonNull
    @Override
    public ApkCreatorType getApkCreatorType() {
        if (baseServices.getProjectOptions().get(USE_NEW_APK_CREATOR)) {
            return ApkCreatorType.APK_FLINGER;
        } else {
            return ApkCreatorType.APK_Z_FILE_CREATOR;
        }
    }
}
