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
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8_DESUGARING;
import static com.android.build.gradle.options.BooleanOption.ENABLE_R8_DESUGARING;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_APK_CREATOR;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_JAR_CREATOR;
import static com.android.build.gradle.options.OptionalBooleanOption.ENABLE_R8;
import static com.android.builder.core.VariantTypeImpl.UNIT_TEST;
import static com.android.builder.model.CodeShrinker.PROGUARD;
import static com.android.builder.model.CodeShrinker.R8;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.ProguardFileType;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.PostProcessingOptions;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dependency.ProvidedClasspath;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.packaging.JarCreatorType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.builder.model.CodeShrinker;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/** A scope containing data for a specific variant. */
public class VariantScopeImpl implements VariantScope {

    private static final String PUBLISH_ERROR_MSG =
            "Publishing to %1$s with no %1$s configuration object. VariantType: %2$s";

    // Variant specific Data
    @NonNull private final ComponentIdentity componentIdentity;
    @NonNull private final VariantDslInfo variantDslInfo;
    @NonNull private final VariantPathHelper pathHelper;
    @NonNull private final ArtifactsImpl artifacts;
    @NonNull private final VariantDependencies variantDependencies;

    @NonNull private final PublishingSpecs.VariantSpec variantPublishingSpec;
    @Nullable private final VariantPropertiesImpl testedVariantProperties;

    // Global Data
    @NonNull private final GlobalScope globalScope;

    // other

    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    private final Supplier<ConfigurableFileCollection> desugarTryWithResourcesRuntimeJar;

    @NonNull private final PostProcessingOptions postProcessingOptions;

    public VariantScopeImpl(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantPathHelper pathHelper,
            @NonNull ArtifactsImpl artifacts,
            @NonNull GlobalScope globalScope,
            @Nullable VariantPropertiesImpl testedVariantProperties) {
        this.componentIdentity = componentIdentity;
        this.variantDslInfo = variantDslInfo;
        this.variantDependencies = variantDependencies;
        this.pathHelper = pathHelper;
        this.artifacts = artifacts;
        this.globalScope = globalScope;
        this.variantPublishingSpec =
                PublishingSpecs.getVariantSpec(variantDslInfo.getVariantType());
        this.testedVariantProperties = testedVariantProperties;

        if (globalScope.isActive(OptionalCompilationStep.INSTANT_DEV)) {
            throw new RuntimeException("InstantRun mode is not supported");
        }
        Project project = globalScope.getProject();

        this.desugarTryWithResourcesRuntimeJar =
                Suppliers.memoize(
                        () ->
                                project.files(
                                        FileUtils.join(
                                                pathHelper.getIntermediatesDir(),
                                                "processing-tools",
                                                "runtime-deps",
                                                variantDslInfo.getDirName(),
                                                "desugar_try_with_resources.jar")));
        this.postProcessingOptions =
                variantDslInfo.createPostProcessingOptions(project.getLayout().getBuildDirectory());

        configureNdk();
    }

    private void configureNdk() {
        File objFolder =
                new File(
                        pathHelper.getIntermediatesDir(),
                        "ndk/" + variantDslInfo.getDirName() + "/obj");
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
     * @param configTypes the PublishedConfigType. (e.g. api, runtime, etc)
     */
    @Override
    public void publishIntermediateArtifact(
            @NonNull Provider<?> artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<PublishedConfigType> configTypes) {

        Preconditions.checkState(!configTypes.isEmpty());

        for (PublishedConfigType configType : PublishedConfigType.values()) {
            if (configTypes.contains(configType)) {
                Configuration config = variantDependencies.getElements(configType);
                Preconditions.checkNotNull(
                        config,
                        String.format(
                                PUBLISH_ERROR_MSG, configType, variantDslInfo.getVariantType()));
                if (configType.isPublicationConfig()) {
                    String classifier = null;
                    if (configType.isClassifierRequired()) {
                        classifier = componentIdentity.getName();
                    }
                    publishArtifactToDefaultVariant(config, artifact, artifactType, classifier);
                } else {
                    publishArtifactToConfiguration(config, artifact, artifactType);
                }
            }
        }
    }

    @Override
    public boolean useResourceShrinker() {
        VariantType variantType = variantDslInfo.getVariantType();

        if (variantType.isForTesting() || !postProcessingOptions.resourcesShrinkingEnabled()) {
            return false;
        }

        // TODO: support resource shrinking for multi-apk applications http://b/78119690
        if (variantType.isDynamicFeature() || globalScope.hasDynamicFeatures()) {
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "Resource shrinker cannot be used for multi-apk applications");
            return false;
        }

        if (variantType.isAar()) {
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(Type.GENERIC, "Resource shrinker cannot be used for libraries.");
            return false;
        }

        if (getCodeShrinker() == null) {
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "Removing unused resources requires unused code shrinking to be turned on. See "
                                    + "http://d.android.com/r/tools/shrink-resources.html "
                                    + "for more information.");

            return false;
        }

        return true;
    }

    @Override
    public boolean isPrecompileDependenciesResourcesEnabled() {
        // Resource shrinker expects MergeResources task to have all the resources merged and with
        // overlay rules applied, so we have to go through the MergeResources pipeline in case it's
        // enabled, see b/134766811.
        return globalScope.getProjectOptions().get(BooleanOption.PRECOMPILE_DEPENDENCIES_RESOURCES)
                && !useResourceShrinker();
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
                globalScope.getExtension().getAaptOptions().getCruncherEnabledOverride();
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
                && variantDslInfo.isMinifyEnabled()
                && globalScope.hasDynamicFeatures();
    }

    @Override
    public boolean getNeedsJavaResStreams() {
        // We need to create original java resource stream only if we're in a library module with
        // custom transforms.
        return variantDslInfo.getVariantType().isAar()
                && !globalScope.getExtension().getTransforms().isEmpty();
    }

    @Override
    public boolean getNeedsMergedJavaResStream() {
        // We need to create a stream from the merged java resources if we're in a library module,
        // or if we're in an app/feature module which uses the transform pipeline.
        return variantDslInfo.getVariantType().isAar()
                || !globalScope.getExtension().getTransforms().isEmpty()
                || getCodeShrinker() != null;
    }

    @Override
    public boolean getNeedsMainDexListForBundle() {
        return variantDslInfo.getVariantType().isBaseModule()
                && globalScope.hasDynamicFeatures()
                && variantDslInfo.getDexingType().getNeedsMainDexList();
    }

    @Nullable
    @Override
    public CodeShrinker getCodeShrinker() {
        boolean isTestComponent = variantDslInfo.getVariantType().isTestComponent();

        //noinspection ConstantConditions - getType() will not return null for a testing variant.
        if (isTestComponent
                && testedVariantProperties != null
                && testedVariantProperties.getVariantType().isAar()) {
            // For now we seem to include the production library code as both program and library
            // input to the test ProGuard run, which confuses it.
            return null;
        }

        CodeShrinker codeShrinker = postProcessingOptions.getCodeShrinker();
        if (codeShrinker == null) {
            return null;
        }

        Boolean enableR8 = globalScope.getProjectOptions().get(ENABLE_R8);
        if (variantDslInfo.getVariantType().isAar()
                && !globalScope.getProjectOptions().get(BooleanOption.ENABLE_R8_LIBRARIES)) {
            // R8 is disabled for libraries
            enableR8 = false;
        }

        if (enableR8 == null) {
            return codeShrinker;
        } else if (enableR8) {
            return R8;
        } else {
            return PROGUARD;
        }
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        List<File> result = getExplicitProguardFiles();

        // For backwards compatibility, we keep the old behavior: if there are no files
        // specified, use a default one.
        if (result.isEmpty()) {
            return postProcessingOptions.getDefaultProguardFiles();
        }

        return result;
    }

    @NonNull
    @Override
    public List<File> getExplicitProguardFiles() {
        return gatherProguardFiles(ProguardFileType.EXPLICIT);
    }

    @NonNull
    @Override
    public List<File> getTestProguardFiles() {
        return gatherProguardFiles(ProguardFileType.TEST);
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
            consumerProguardFiles.addAll(getExplicitProguardFiles());
        }

        return ImmutableList.copyOf(consumerProguardFiles);
    }

    @NonNull
    private List<File> gatherProguardFiles(ProguardFileType type) {
        List<File> result = variantDslInfo.gatherProguardFiles(type);
        result.addAll(postProcessingOptions.getProguardFiles(type));

        return result;
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
     */
    @Override
    public boolean isTestOnly() {
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY))
                || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                || isPreviewTargetPlatform()
                || variantDslInfo.getMinSdkVersion().getCodename() != null
                || variantDslInfo.getTargetSdkVersion().getCodename() != null;
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
    public boolean isCoreLibraryDesugaringEnabled() {
        BaseExtension extension = globalScope.getExtension();

        boolean libDesugarEnabled =
                extension.getCompileOptions().getCoreLibraryDesugaringEnabled() != null
                        && extension.getCompileOptions().getCoreLibraryDesugaringEnabled();

        boolean multidexEnabled = variantDslInfo.isMultiDexEnabled();

        Java8LangSupport langSupportType = getJava8LangSupportType();
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
    public boolean getNeedsShrinkDesugarLibrary() {
        if (!isCoreLibraryDesugaringEnabled()) {
            return false;
        }
        // Assume Java8LangSupport is either D8 or R8 as we checked that in
        // isCoreLibraryDesugaringEnabled()
        if (getJava8LangSupportType() == Java8LangSupport.D8 && variantDslInfo.isDebuggable()) {
            return false;
        }
        return true;
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
        return globalScope
                .getProject()
                .files(
                        (Callable<Collection<File>>)
                                () ->
                                        dependencies
                                                .call()
                                                .stream()
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

    @NonNull
    @Override
    public Java8LangSupport getJava8LangSupportType() {
        // in order of precedence
        if (!globalScope
                .getExtension()
                .getCompileOptions()
                .getTargetCompatibility()
                .isJava8Compatible()) {
            return Java8LangSupport.UNUSED;
        }

        if (globalScope.getProject().getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            return Java8LangSupport.RETROLAMBDA;
        }

        CodeShrinker shrinker = getCodeShrinker();
        if (shrinker == R8) {
            if (globalScope.getProjectOptions().get(ENABLE_R8_DESUGARING)) {
                return Java8LangSupport.R8;
            }
        } else {
            // D8 cannot be used if R8 is used
            if (globalScope.getProjectOptions().get(ENABLE_D8_DESUGARING)
                    && isValidJava8Flag(ENABLE_D8_DESUGARING, ENABLE_D8)) {
                return Java8LangSupport.D8;
            }
        }

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_DESUGAR)) {
            return Java8LangSupport.DESUGAR;
        }

        BooleanOption missingFlag = shrinker == R8 ? ENABLE_R8_DESUGARING : ENABLE_D8_DESUGARING;
        globalScope
                .getDslServices()
                .getIssueReporter()
                .reportError(
                        Type.GENERIC,
                        String.format(
                                "Please add '%s=true' to your "
                                        + "gradle.properties file to enable Java 8 "
                                        + "language support.",
                                missingFlag.name()),
                        variantDslInfo.getComponentIdentity().getName());
        return Java8LangSupport.INVALID;
    }

    private boolean isValidJava8Flag(
            @NonNull BooleanOption flag, @NonNull BooleanOption... dependsOn) {
        List<String> invalid = null;
        for (BooleanOption requiredFlag : dependsOn) {
            if (!globalScope.getProjectOptions().get(requiredFlag)) {
                if (invalid == null) {
                    invalid = Lists.newArrayList();
                }
                invalid.add("'" + requiredFlag.getPropertyName() + "= false'");
            }
        }

        if (invalid == null) {
            return true;
        } else {
            String template =
                    "Java 8 language support, as requested by '%s= true' in your "
                            + "gradle.properties file, is not supported when %s.";
            String msg = String.format(template, flag.getPropertyName(), String.join(",", invalid));
            globalScope
                    .getDslServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC, msg, variantDslInfo.getComponentIdentity().getName());
            return false;
        }
    }

    @NonNull
    @Override
    public ConfigurableFileCollection getTryWithResourceRuntimeSupportJar() {
        return desugarTryWithResourcesRuntimeJar.get();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(componentIdentity.getName()).toString();
    }

    @NonNull
    @Override
    public DexerTool getDexer() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexerTool.D8;
        } else {
            return DexerTool.DX;
        }
    }

    @NonNull
    @Override
    public DexMergerTool getDexMerger() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexMergerTool.D8;
        } else {
            return DexMergerTool.DX;
        }
    }

    @NonNull
    @Override
    public FileCollection getBootClasspath() {
        return globalScope.getProject().files(globalScope.getBootClasspath());
    }

    @NonNull
    @Override
    public JarCreatorType getJarCreatorType() {
        if (globalScope.getProjectOptions().get(USE_NEW_JAR_CREATOR)) {
            return JarCreatorType.JAR_FLINGER;
        } else {
            return JarCreatorType.JAR_MERGER;
        }
    }

    @NonNull
    @Override
    public ApkCreatorType getApkCreatorType() {
        if (globalScope.getProjectOptions().get(USE_NEW_APK_CREATOR)) {
            return ApkCreatorType.APK_FLINGER;
        } else {
            return ApkCreatorType.APK_Z_FILE_CREATOR;
        }
    }
}
