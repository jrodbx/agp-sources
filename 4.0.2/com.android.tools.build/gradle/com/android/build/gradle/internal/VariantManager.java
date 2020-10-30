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

package com.android.build.gradle.internal;

import static com.android.build.gradle.internal.dependency.DexingOutputSplitTransformKt.registerDexingOutputSplitTransform;
import static com.android.build.gradle.internal.dependency.DexingTransformKt.getDexingArtifactConfigurations;
import static com.android.build.gradle.internal.dependency.L8DexDesugarLibTransformKt.getDesugarLibConfigurations;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.UNFILTERED_PROGUARD_RULES;
import static com.android.build.gradle.internal.utils.DesugarLibUtils.getDesugarLibConfig;
import static com.android.builder.core.VariantTypeImpl.ANDROID_TEST;
import static com.android.builder.core.VariantTypeImpl.UNIT_TEST;
import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.api.artifact.BuildArtifactSpec;
import com.android.build.gradle.internal.core.VariantBuilder;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.crash.ExternalApiUsageException;
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution;
import com.android.build.gradle.internal.dependency.DesugarLibConfiguration;
import com.android.build.gradle.internal.dependency.DexingArtifactConfiguration;
import com.android.build.gradle.internal.dependency.FilterShrinkerRulesTransform;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dependency.VersionedCodeShrinker;
import com.android.build.gradle.internal.dsl.ActionableVariantObjectOperationsExecutor;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.SingleArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScopeImpl;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.DimensionCombination;
import com.android.build.gradle.internal.variant.DimensionCombinator;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SigningOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.errors.IssueReporter;
import com.android.builder.model.CodeShrinker;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.ApiVersion;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

/** Class to create, manage variants. */
public class VariantManager {

    private static final String MULTIDEX_VERSION = "1.0.2";

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:" + MULTIDEX_VERSION;
    protected static final String COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION;

    protected static final String ANDROIDX_MULTIDEX_MULTIDEX =
            AndroidXDependencySubstitution.getAndroidXMappings()
                    .get("com.android.support:multidex");
    protected static final String ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION =
            AndroidXDependencySubstitution.getAndroidXMappings()
                    .get("com.android.support:multidex-instrumentation");

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final BaseExtension extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull private final VariantInputModel variantInputModel;
    @NonNull private final TaskManager taskManager;
    @NonNull private final SourceSetManager sourceSetManager;
    @NonNull private final Recorder recorder;
    @NonNull private final VariantFilter variantFilter;
    @NonNull private final List<VariantScope> variantScopes;
    @NonNull private final Map<File, ManifestAttributeSupplier> manifestParserMap;
    @NonNull protected final GlobalScope globalScope;
    @Nullable private final SigningConfig signingOverride;
    // We cannot use gradle's state of executed as that returns true while inside afterEvalute.
    // Wew want this to only be true after all tasks have been create.
    private boolean hasCreatedTasks = false;
    public static final Attribute<String> SHRINKER_ATTR =
            Attribute.of("codeShrinker", String.class);

    public VariantManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull VariantInputModel variantInputModel,
            @NonNull TaskManager taskManager,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.project = project;
        this.projectOptions = projectOptions;
        this.variantFactory = variantFactory;
        this.variantInputModel = variantInputModel;
        this.taskManager = taskManager;
        this.sourceSetManager = sourceSetManager;
        this.recorder = recorder;
        this.signingOverride = createSigningOverride();
        this.variantFilter = new VariantFilter(new ReadOnlyObjectProvider());
        this.variantScopes = Lists.newArrayList();
        this.manifestParserMap = Maps.newHashMap();
    }

    /**
     * Registers a new variant.
     *
     * <p>Unfortunately VariantData and VariantScope are tangled together and are really parts of
     * the same, but we'll try to gradually shift all the immutable state to VariantScope and
     * pretend that there's only an edge from scope to data.
     */
    private void addVariant(@NonNull BaseVariantData variantData) {
        variantScopes.add(variantData.getScope());
    }

    /** Returns a list of all created {@link VariantScope}s. */
    @NonNull
    public List<VariantScope> getVariantScopes() {
        return variantScopes;
    }

    /** Creates the variants and their tasks. */
    public List<VariantScope> createVariantsAndTasks() {
        variantFactory.validateModel(variantInputModel);
        variantFactory.preVariantWork(project);

        if (variantScopes.isEmpty()) {
            computeVariants();
        }

        // Create top level test tasks.
        taskManager.createTopLevelTestTasks(!variantInputModel.getProductFlavors().isEmpty());

        // Create tasks for all variants (main and tests)
        for (final VariantScope variantScope : variantScopes) {
            createTasksForVariant(variantScope);
        }

        taskManager.createReportTasks(variantScopes);

        return variantScopes;
    }

    /** Create tasks for the specified variant. */
    private void createTasksForVariant(final VariantScope variantScope) {
        final BaseVariantData variantData = variantScope.getVariantData();
        final VariantType variantType = variantData.getType();
        final VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();
        final VariantSources variantSources = variantScope.getVariantSources();

        taskManager.createAssembleTask(variantData);
        if (variantType.isBaseModule()) {
            taskManager.createBundleTask(variantData);
        }

        if (variantType.isTestComponent()) {
            final BaseVariantData testedVariantData =
                    (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData();

            // Add the container of dependencies, the order of the libraries is important.
            // In descending order: build type (only for unit test), flavors, defaultConfig.

            // Add the container of dependencies.
            // The order of the libraries is important, in descending order:
            // variant-specific, build type (, multi-flavor, flavor1, flavor2, ..., defaultConfig.
            // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
            // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
            List<ProductFlavor> testProductFlavors = variantDslInfo.getProductFlavorList();
            List<DefaultAndroidSourceSet> testVariantSourceSets =
                    Lists.newArrayListWithExpectedSize(4 + testProductFlavors.size());

            // 1. add the variant-specific if applicable.
            if (!testProductFlavors.isEmpty()) {
                testVariantSourceSets.add(
                        (DefaultAndroidSourceSet) variantSources.getVariantSourceProvider());
            }

            // 2. the build type.
            final BuildTypeData buildTypeData =
                    variantInputModel
                            .getBuildTypes()
                            .get(variantDslInfo.getComponentIdentity().getBuildType());
            DefaultAndroidSourceSet buildTypeConfigurationProvider =
                    buildTypeData.getTestSourceSet(variantType);
            if (buildTypeConfigurationProvider != null) {
                testVariantSourceSets.add(buildTypeConfigurationProvider);
            }

            // 3. the multi-flavor combination
            if (testProductFlavors.size() > 1) {
                testVariantSourceSets.add(
                        (DefaultAndroidSourceSet) variantSources.getMultiFlavorSourceProvider());
            }

            // 4. the flavors.
            for (ProductFlavor productFlavor : testProductFlavors) {
                testVariantSourceSets.add(
                        variantInputModel
                                .getProductFlavors()
                                .get(productFlavor.getName())
                                .getTestSourceSet(variantType));
            }

            // now add the default config
            testVariantSourceSets.add(
                    variantInputModel.getDefaultConfig().getTestSourceSet(variantType));

            // If the variant being tested is a library variant, VariantDependencies must be
            // computed after the tasks for the tested variant is created.  Therefore, the
            // VariantDependencies is computed here instead of when the VariantData was created.
            VariantDependencies.Builder builder =
                    VariantDependencies.builder(
                                    project,
                                    variantScope.getGlobalScope().getDslScope().getIssueReporter(),
                                    variantDslInfo)
                            .addSourceSets(testVariantSourceSets)
                            .setFlavorSelection(getFlavorSelection(variantDslInfo))
                            .setTestedVariantScope(testedVariantData.getScope());

            final VariantDependencies variantDep = builder.build(variantScope);
            variantData.setVariantDependency(variantDep);

            if (testedVariantData.getVariantDslInfo().getRenderscriptSupportModeEnabled()) {
                project.getDependencies()
                        .add(
                                variantDep.getCompileClasspath().getName(),
                                project.files(
                                        globalScope
                                                .getSdkComponents()
                                                .getRenderScriptSupportJarProvider()));
            }

            if (variantType.isApk()) { // ANDROID_TEST
                if (variantDslInfo.isLegacyMultiDexMode()) {
                    String multiDexInstrumentationDep =
                            globalScope.getProjectOptions().get(BooleanOption.USE_ANDROID_X)
                                    ? ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION
                                    : COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION;
                    project.getDependencies()
                            .add(
                                    variantDep.getCompileClasspath().getName(),
                                    multiDexInstrumentationDep);
                    project.getDependencies()
                            .add(
                                    variantDep.getRuntimeClasspath().getName(),
                                    multiDexInstrumentationDep);
                }

                taskManager.createAndroidTestVariantTasks(
                        (TestVariantData) variantData,
                        variantScopes
                                .stream()
                                .filter(TaskManager::isLintVariant)
                                .collect(Collectors.toList()));
            } else { // UNIT_TEST
                taskManager.createUnitTestVariantTasks((TestVariantData) variantData);
            }

        } else {
            taskManager.createTasksForVariantScope(
                    variantScope,
                    variantScopes
                            .stream()
                            .filter(TaskManager::isLintVariant)
                            .collect(Collectors.toList()));
        }
    }

    /** Publish intermediate artifacts in the BuildArtifactsHolder based on PublishingSpecs. */
    public void publishBuildArtifacts(VariantScope variantScope) {
        BuildArtifactsHolder buildArtifactsHolder = variantScope.getArtifacts();
        for (PublishingSpecs.OutputSpec outputSpec :
                variantScope.getPublishingSpec().getOutputs()) {
            SingleArtifactType<? extends FileSystemLocation> buildArtifactType =
                    outputSpec.getOutputType();

            // Gradle only support publishing single file.  Therefore, unless Gradle starts
            // supporting publishing multiple files, PublishingSpecs should not contain any
            // OutputSpec with an appendable ArtifactType.
            if (BuildArtifactSpec.Companion.has(buildArtifactType)
                    && BuildArtifactSpec.Companion.get(buildArtifactType).getAppendable()) {
                throw new RuntimeException(
                        String.format(
                                "Appendable ArtifactType '%1s' cannot be published.",
                                buildArtifactType.name()));
            }

            if (buildArtifactsHolder.hasFinalProduct(buildArtifactType)) {
                Provider<? extends FileSystemLocation> artifact =
                        buildArtifactsHolder.getFinalProduct(buildArtifactType);

                variantScope.publishIntermediateArtifact(
                        artifact,
                        outputSpec.getArtifactType(),
                        outputSpec.getPublishedConfigTypes());
            } else {
                if (buildArtifactType == InternalArtifactType.ALL_CLASSES.INSTANCE) {
                    Provider<FileCollection> allClasses =
                            buildArtifactsHolder.getFinalProductAsFileCollection(
                                    InternalArtifactType.ALL_CLASSES.INSTANCE);
                    Provider<File> file = allClasses.map(FileCollection::getSingleFile);

                    variantScope.publishIntermediateArtifact(
                            file,
                            outputSpec.getArtifactType(),
                            outputSpec.getPublishedConfigTypes());
                }
            }
        }
    }

    @NonNull
    private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorSelection(
            @NonNull VariantDslInfo variantDslInfo) {
        ObjectFactory factory = project.getObjects();

        return variantDslInfo
                .getMissingDimensionStrategies()
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                entry -> Attribute.of(entry.getKey(), ProductFlavorAttr.class),
                                entry ->
                                        factory.named(
                                                ProductFlavorAttr.class,
                                                entry.getValue().getRequested())));

    }

    /** Configure artifact transforms that require variant-specific attribute information. */
    private void configureVariantArtifactTransforms(
            @NonNull Collection<VariantScope> variantScopes) {
        DependencyHandler dependencies = project.getDependencies();

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)) {
            for (DexingArtifactConfiguration artifactConfiguration :
                    getDexingArtifactConfigurations(variantScopes)) {
                artifactConfiguration.registerTransform(
                        globalScope.getProject().getName(),
                        dependencies,
                        globalScope.getBootClasspath(),
                        getDesugarLibConfig(globalScope.getProject()),
                        SyncOptions.getErrorFormatMode(globalScope.getProjectOptions()),
                        projectOptions.get(BooleanOption.ENABLE_INCREMENTAL_DEXING_V2));
            }
        }

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_PROGUARD_RULES_EXTRACTION)) {
            Set<CodeShrinker> shrinkers =
                    variantScopes
                            .stream()
                            .map(VariantScope::getCodeShrinker)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            for (CodeShrinker shrinker : shrinkers) {
                dependencies.registerTransform(
                        FilterShrinkerRulesTransform.class,
                        reg -> {
                            reg.getFrom()
                                    .attribute(
                                            ARTIFACT_FORMAT, UNFILTERED_PROGUARD_RULES.getType());
                            reg.getTo()
                                    .attribute(ARTIFACT_FORMAT, FILTERED_PROGUARD_RULES.getType());

                            reg.getFrom().attribute(SHRINKER_ATTR, shrinker.toString());
                            reg.getTo().attribute(SHRINKER_ATTR, shrinker.toString());

                            reg.parameters(
                                    params -> {
                                        params.getShrinker()
                                                .set(VersionedCodeShrinker.of(shrinker));
                                        params.getProjectName().set(project.getName());
                                    });
                        });
            }
        }

        for (DesugarLibConfiguration configuration : getDesugarLibConfigurations(variantScopes)) {
            configuration.registerTransform(dependencies);
        }

        registerDexingOutputSplitTransform(dependencies);
    }

    /**
     * Returns a modified name.
     *
     * <p>This name is used to request a missing dimension. It is the same name as the flavor that
     * sets up the request, which means it's not going to be matched, and instead it'll go to a
     * custom fallbacks provided by the flavor.
     *
     * <p>We are just modifying the name to avoid collision in case the same name exists in
     * different dimensions
     */
    public static String getModifiedName(@NonNull String name) {
        return "____" + name;
    }

    /** Create all variants. */
    @VisibleForTesting
    public void computeVariants() {
        List<String> flavorDimensionList = extension.getFlavorDimensionList();

        DimensionCombinator computer =
                new DimensionCombinator(
                        variantInputModel,
                        globalScope.getDslScope().getIssueReporter(),
                        variantFactory.getVariantType(),
                        flavorDimensionList);

        List<DimensionCombination> variants = computer.computeVariants();

        // get some info related to testing
        BuildTypeData testBuildTypeData = getTestBuildTypeData();

        // loop on all the new variant objects to create the legacy ones.
        for (DimensionCombination variant : variants) {
            createVariantsFromConfiguration(variant, testBuildTypeData);
        }

        configureVariantArtifactTransforms(variantScopes);
    }

    @Nullable
    private BuildTypeData getTestBuildTypeData() {
        BuildTypeData testBuildTypeData = null;
        if (extension instanceof TestedAndroidConfig) {
            TestedAndroidConfig testedExtension = (TestedAndroidConfig) extension;

            testBuildTypeData =
                    variantInputModel.getBuildTypes().get(testedExtension.getTestBuildType());
            if (testBuildTypeData == null) {
                throw new RuntimeException(
                        String.format(
                                "Test Build Type '%1$s' does not" + " exist.",
                                testedExtension.getTestBuildType()));
            }
        }
        return testBuildTypeData;
    }

    @Nullable
    private BaseVariantData createVariant(
            @NonNull DimensionCombination dimensionCombination,
            @NonNull BuildTypeData buildTypeData,
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorDataList,
            @NonNull VariantType variantType) {
        // entry point for a given buildType/Flavors/VariantType combo.
        // Need to run the new variant API to selectively ignore variants.
        // in order to do this, we need access to the VariantDslInfo, to create a

        final ProductFlavorData<DefaultConfig> defaultConfig = variantInputModel.getDefaultConfig();
        DefaultAndroidSourceSet defaultConfigSourceProvider = defaultConfig.getSourceSet();

        VariantBuilder variantBuilder =
                VariantBuilder.getBuilder(
                        dimensionCombination,
                        variantType,
                        defaultConfig.getProductFlavor(),
                        defaultConfigSourceProvider,
                        buildTypeData.getBuildType(),
                        buildTypeData.getSourceSet(),
                        signingOverride,
                        getParser(
                                defaultConfigSourceProvider.getManifestFile(),
                                variantType.getRequiresManifest()),
                        globalScope.getProjectOptions(),
                        globalScope.getDslScope().getIssueReporter(),
                        this::canParseManifest);

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (ProductFlavorData<ProductFlavor> productFlavorData : productFlavorDataList) {
            variantBuilder.addProductFlavor(
                    productFlavorData.getProductFlavor(), productFlavorData.getSourceSet());
        }

        VariantDslInfoImpl variantDslInfo = variantBuilder.createVariantDslInfo();

        // create the Variant object so that we can run the action which may interrupt the creation
        // (in case of enabled = false)
        VariantImpl variant =
                variantFactory.createVariantObject(
                        variantDslInfo.getComponentIdentity(), variantDslInfo);

        // HACK, we need access to the new type rather than the old. This will go away in the
        // future
        ActionableVariantObjectOperationsExecutor commonExtension =
                (ActionableVariantObjectOperationsExecutor) extension;
        //noinspection unchecked
        commonExtension.executeVariantOperations(variant);

        if (!variant.getEnabled()) {
            return null;
        }

        // now that we have the result of the filter, we can continue configuring the variant

        createCompoundSourceSets(productFlavorDataList, variantBuilder, sourceSetManager);

        VariantSources variantSources = variantBuilder.createVariantSources();

        // Only record release artifacts
        if (!buildTypeData.getBuildType().isDebuggable()
                && variantType.isApk()
                && !variantDslInfo.getVariantType().isForTesting()) {
            ProcessProfileWriter.get().recordApplicationId(variantDslInfo::getApplicationId);
        }

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        final List<DefaultAndroidSourceSet> variantSourceSets =
                Lists.newArrayListWithExpectedSize(productFlavorDataList.size() + 4);

        // 1. add the variant-specific if applicable.
        if (!productFlavorDataList.isEmpty()) {
            variantSourceSets.add(
                    (DefaultAndroidSourceSet) variantSources.getVariantSourceProvider());
        }

        // 2. the build type.
        variantSourceSets.add(buildTypeData.getSourceSet());

        // 3. the multi-flavor combination
        if (productFlavorDataList.size() > 1) {
            variantSourceSets.add(
                    (DefaultAndroidSourceSet) variantSources.getMultiFlavorSourceProvider());
        }

        // 4. the flavors.
        for (ProductFlavorData<ProductFlavor> productFlavor : productFlavorDataList) {
            variantSourceSets.add(productFlavor.getSourceSet());
        }

        // 5. The defaultConfig
        variantSourceSets.add(variantInputModel.getDefaultConfig().getSourceSet());

        // Done. Create the (too) many variant objects

        final Project project = globalScope.getProject();
        VariantScopeImpl variantScope =
                new VariantScopeImpl(
                        globalScope,
                        new TransformManager(
                                globalScope.getProject(),
                                globalScope.getDslScope().getIssueReporter(),
                                recorder),
                        variantDslInfo,
                        variantType);

        VariantPropertiesImpl variantProperties =
                variantFactory.createVariantPropertiesObject(
                        variantDslInfo.getComponentIdentity(), variantScope);

        BaseVariantData variantData =
                variantFactory.createVariantData(
                        variantScope,
                        variantDslInfo,
                        variant,
                        variantProperties,
                        variantSources,
                        taskManager);

        variantScope.setVariantData(variantData);

        VariantDependencies.Builder builder =
                VariantDependencies.builder(
                                project,
                                variantScope.getGlobalScope().getDslScope().getIssueReporter(),
                                variantDslInfo)
                        .setFlavorSelection(getFlavorSelection(variantDslInfo))
                        .addSourceSets(variantSourceSets);

        if (extension instanceof BaseAppModuleExtension) {
            builder.setFeatureList(((BaseAppModuleExtension) extension).getDynamicFeatures());
        }

        final VariantDependencies variantDep = builder.build(variantScope);
        variantData.setVariantDependency(variantDep);

        if (variantDslInfo.isLegacyMultiDexMode() && variantDslInfo.getVariantType().isApk()) {
            String multiDexDependency =
                    globalScope.getProjectOptions().get(BooleanOption.USE_ANDROID_X)
                            ? ANDROIDX_MULTIDEX_MULTIDEX
                            : COM_ANDROID_SUPPORT_MULTIDEX;
            project.getDependencies()
                    .add(variantDep.getCompileClasspath().getName(), multiDexDependency);
            project.getDependencies()
                    .add(variantDep.getRuntimeClasspath().getName(), multiDexDependency);
        }

        if (variantDslInfo.getRenderscriptSupportModeEnabled()) {
            final ConfigurableFileCollection fileCollection =
                    project.files(
                            globalScope.getSdkComponents().getRenderScriptSupportJarProvider());
            project.getDependencies()
                    .add(variantDep.getCompileClasspath().getName(), fileCollection);
            if (variantType.isApk() && !variantType.isForTesting()) {
                project.getDependencies()
                        .add(variantDep.getRuntimeClasspath().getName(), fileCollection);
            }
        }

        // Run the VariantProperties actions
        //noinspection unchecked
        commonExtension.executeVariantPropertiesOperations(variantProperties);

        // also execute the delayed actions registered on the Variant object itself
        //noinspection unchecked
        variant.executePropertiesActions(variantProperties);

        return variantData;
    }

    private static void createCompoundSourceSets(
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorList,
            @NonNull VariantBuilder variantBuilder,
            @NonNull SourceSetManager sourceSetManager) {
        final VariantType variantType = variantBuilder.getVariantType();

        if (!productFlavorList.isEmpty() /* && !variantConfig.getType().isSingleBuildType()*/) {
            DefaultAndroidSourceSet variantSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpSourceSet(
                                    VariantBuilder.computeSourceSetName(
                                            variantBuilder.getName(), variantType),
                                    variantType.isTestComponent());
            variantBuilder.setVariantSourceProvider(variantSourceSet);
        }

        if (productFlavorList.size() > 1) {
            DefaultAndroidSourceSet multiFlavorSourceSet =
                    (DefaultAndroidSourceSet)
                            sourceSetManager.setUpSourceSet(
                                    VariantBuilder.computeSourceSetName(
                                            variantBuilder.getFlavorName(), variantType),
                                    variantType.isTestComponent());
            variantBuilder.setMultiFlavorSourceProvider(multiFlavorSourceSet);
        }
    }

    /** Create a TestVariantData for the specified testedVariantData. */
    @Nullable
    public TestVariantData createTestComponents(
            @NonNull DimensionCombination dimensionCombination,
            @NonNull BuildTypeData buildTypeData,
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorDataList,
            @NonNull BaseVariantData testedVariantData,
            @NonNull VariantType type) {

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        final DefaultAndroidSourceSet testSourceSet =
                variantInputModel.getDefaultConfig().getTestSourceSet(type);
        @SuppressWarnings("ConstantConditions")
        VariantBuilder variantBuilder =
                VariantBuilder.getBuilder(
                        dimensionCombination,
                        type,
                        variantInputModel.getDefaultConfig().getProductFlavor(),
                        testSourceSet,
                        buildTypeData.getBuildType(),
                        buildTypeData.getTestSourceSet(type),
                        signingOverride,
                        testSourceSet != null
                                ? getParser(
                                        testSourceSet.getManifestFile(), type.getRequiresManifest())
                                : null,
                        globalScope.getProjectOptions(),
                        globalScope.getDslScope().getIssueReporter(),
                        this::canParseManifest);

        VariantDslInfoImpl testedVariantDslInfo =
                (VariantDslInfoImpl) testedVariantData.getVariantDslInfo();

        variantBuilder.setTestedVariant(testedVariantDslInfo);

        List<ProductFlavor> productFlavorList = testedVariantDslInfo.getProductFlavorList();

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        final Map<String, ProductFlavorData<ProductFlavor>> productFlavors =
                variantInputModel.getProductFlavors();
        for (ProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<ProductFlavor> data = productFlavors.get(productFlavor.getName());

            //noinspection ConstantConditions
            variantBuilder.addProductFlavor(data.getProductFlavor(), data.getTestSourceSet(type));
        }

        VariantDslInfoImpl variantDslInfo = variantBuilder.createVariantDslInfo();

        // create the Variant object so that we can run the action which may interrupt the creation
        // (in case of enabled = false)
        VariantImpl testedVariant = (VariantImpl) testedVariantData.getPublicVariantApi();

        // this is ANDROID_TEST
        ComponentImpl component;
        if (type.isApk()) {
            AndroidTestImpl androidTestVariant =
                    variantFactory.createAndroidTestObject(
                            variantDslInfo.getComponentIdentity(), variantDslInfo);

            testedVariant.executeAndroidTestActions(androidTestVariant);

            component = androidTestVariant;
        } else {
            // this is UNIT_TEST
            UnitTestImpl unitTestVariant =
                    variantFactory.createUnitTestObject(
                            variantDslInfo.getComponentIdentity(), variantDslInfo);

            testedVariant.executeUnitTestActions(unitTestVariant);

            component = unitTestVariant;
        }

        if (!component.getEnabled()) {
            return null;
        }

        // now that we have the result of the filter, we can continue configuring the variant
        createCompoundSourceSets(productFlavorDataList, variantBuilder, sourceSetManager);

        VariantScopeImpl variantScope =
                new VariantScopeImpl(
                        globalScope,
                        new TransformManager(
                                globalScope.getProject(),
                                globalScope.getDslScope().getIssueReporter(),
                                recorder),
                        variantDslInfo,
                        type);


        // this is ANDROID_TEST
        ComponentPropertiesImpl componentProperties;
        if (type.isApk()) {
            AndroidTestPropertiesImpl androidTestProperties =
                    variantFactory.createAndroidTestProperties(
                            variantDslInfo.getComponentIdentity(), variantScope);

            component.executePropertiesActions(androidTestProperties);

            componentProperties = androidTestProperties;
        } else {
            // this is UNIT_TEST
            UnitTestPropertiesImpl unitTestProperties =
                    variantFactory.createUnitTestProperties(
                            variantDslInfo.getComponentIdentity(), variantScope);

            // also execute the delayed actions registered on the Variant object itself
            component.executePropertiesActions(unitTestProperties);

            componentProperties = unitTestProperties;
        }

        // create the internal storage for this variant.
        TestVariantData testVariantData =
                new TestVariantData(
                        globalScope,
                        taskManager,
                        variantScope,
                        variantDslInfo,
                        component,
                        componentProperties,
                        variantBuilder.createVariantSources(),
                        (TestedVariantData) testedVariantData);
        variantScope.setVariantData(testVariantData);

        // link the testVariant to the tested variant in the other direction
        ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData, type);


        return testVariantData;
    }

    /**
     * Creates Variant objects for a specific {@link ComponentIdentity}
     *
     * <p>This will create both the prod and the androidTest/unitTest variants.
     */
    private void createVariantsFromConfiguration(
            @NonNull DimensionCombination dimensionCombination,
            @Nullable BuildTypeData testBuildTypeData) {
        VariantType variantType = variantFactory.getVariantType();

        // first run the old variantFilter API
        // This acts on buildtype/flavor only, and applies in one pass to prod/tests.
        Action<com.android.build.api.variant.VariantFilter> variantFilterAction =
                extension.getVariantFilter();

        DefaultConfig defaultConfig = variantInputModel.getDefaultConfig().getProductFlavor();

        BuildTypeData buildTypeData =
                variantInputModel.getBuildTypes().get(dimensionCombination.getBuildType());
        BuildType buildType = buildTypeData.getBuildType();

        // get the list of ProductFlavorData from the list of flavor name
        List<ProductFlavorData<ProductFlavor>> productFlavorDataList =
                dimensionCombination
                        .getProductFlavors()
                        .stream()
                        .map(it -> variantInputModel.getProductFlavors().get(it.getSecond()))
                        .collect(Collectors.toList());

        List<ProductFlavor> productFlavorList =
                productFlavorDataList
                        .stream()
                        .map(ProductFlavorData::getProductFlavor)
                        .collect(Collectors.toList());

        boolean ignore = false;

        if (variantFilterAction != null) {
            variantFilter.reset(
                    dimensionCombination, defaultConfig, buildType, variantType, productFlavorList);

            try {
                // variantFilterAction != null always true here.
                variantFilterAction.execute(variantFilter);
            } catch (Throwable t) {
                throw new ExternalApiUsageException(t);
            }
            ignore = variantFilter.getIgnore();
        }

        if (!ignore) {
            // create the prod variant
            BaseVariantData variantData =
                    createVariant(
                            dimensionCombination,
                            buildTypeData,
                            productFlavorDataList,
                            variantType);
            if (variantData != null) {
                addVariant(variantData);

                VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();
                VariantScope variantScope = variantData.getScope();

                int minSdkVersion = variantDslInfo.getMinSdkVersion().getApiLevel();
                int targetSdkVersion = variantDslInfo.getTargetSdkVersion().getApiLevel();
                if (minSdkVersion > 0 && targetSdkVersion > 0 && minSdkVersion > targetSdkVersion) {
                    globalScope
                            .getDslScope()
                            .getIssueReporter()
                            .reportWarning(
                                    IssueReporter.Type.GENERIC,
                                    String.format(
                                            Locale.US,
                                            "minSdkVersion (%d) is greater than targetSdkVersion"
                                                    + " (%d) for variant \"%s\". Please change the"
                                                    + " values such that minSdkVersion is less than or"
                                                    + " equal to targetSdkVersion.",
                                            minSdkVersion,
                                            targetSdkVersion,
                                            variantData.getName()));
                }

                GradleBuildVariant.Builder profileBuilder =
                        ProcessProfileWriter.getOrCreateVariant(
                                        project.getPath(), variantData.getName())
                                .setIsDebug(buildType.isDebuggable())
                                .setMinSdkVersion(
                                        AnalyticsUtil.toProto(variantDslInfo.getMinSdkVersion()))
                                .setMinifyEnabled(variantScope.getCodeShrinker() != null)
                                .setUseMultidex(variantDslInfo.isMultiDexEnabled())
                                .setUseLegacyMultidex(variantDslInfo.isLegacyMultiDexMode())
                                .setVariantType(variantData.getType().getAnalyticsVariantType())
                                .setDexBuilder(AnalyticsUtil.toProto(variantScope.getDexer()))
                                .setDexMerger(AnalyticsUtil.toProto(variantScope.getDexMerger()))
                                .setCoreLibraryDesugaringEnabled(
                                        variantScope.isCoreLibraryDesugaringEnabled())
                                .setTestExecution(
                                        AnalyticsUtil.toProto(
                                                globalScope
                                                        .getExtension()
                                                        .getTestOptions()
                                                        .getExecutionEnum()));

                if (variantScope.getCodeShrinker() != null) {
                    profileBuilder.setCodeShrinker(
                            AnalyticsUtil.toProto(variantScope.getCodeShrinker()));
                }

                if (variantDslInfo.getTargetSdkVersion().getApiLevel() > 0) {
                    profileBuilder.setTargetSdkVersion(
                            AnalyticsUtil.toProto(variantDslInfo.getTargetSdkVersion()));
                }
                if (variantDslInfo.getMaxSdkVersion() != null) {
                    profileBuilder.setMaxSdkVersion(
                            ApiVersion.newBuilder().setApiLevel(variantDslInfo.getMaxSdkVersion()));
                }

                VariantScope.Java8LangSupport supportType =
                        variantData.getScope().getJava8LangSupportType();
                if (supportType != VariantScope.Java8LangSupport.INVALID
                        && supportType != VariantScope.Java8LangSupport.UNUSED) {
                    profileBuilder.setJava8LangSupport(AnalyticsUtil.toProto(supportType));
                }

                if (variantFactory.hasTestScope()) {
                    if (buildTypeData == testBuildTypeData) {
                        TestVariantData androidTestVariantData =
                                createTestComponents(
                                        dimensionCombination,
                                        buildTypeData,
                                        productFlavorDataList,
                                        variantData,
                                        ANDROID_TEST);
                        if (androidTestVariantData != null) {
                            addVariant(androidTestVariantData);
                        }
                    }

                    TestVariantData unitTestVariantData =
                            createTestComponents(
                                    dimensionCombination,
                                    buildTypeData,
                                    productFlavorDataList,
                                    variantData,
                                    UNIT_TEST);
                    if (unitTestVariantData != null) {
                        addVariant(unitTestVariantData);
                    }
                }
            }
        }
    }

    private SigningConfig createSigningOverride() {
        SigningOptions signingOptions = SigningOptions.readSigningOptions(projectOptions);
        if (signingOptions != null) {
            com.android.build.gradle.internal.dsl.SigningConfig signingConfigDsl =
                    new com.android.build.gradle.internal.dsl.SigningConfig("externalOverride");

            signingConfigDsl.setStoreFile(new File(signingOptions.getStoreFile()));
            signingConfigDsl.setStorePassword(signingOptions.getStorePassword());
            signingConfigDsl.setKeyAlias(signingOptions.getKeyAlias());
            signingConfigDsl.setKeyPassword(signingOptions.getKeyPassword());

            if (signingOptions.getStoreType() != null) {
                signingConfigDsl.setStoreType(signingOptions.getStoreType());
            }

            if (signingOptions.getV1Enabled() != null) {
                signingConfigDsl.setV1SigningEnabled(signingOptions.getV1Enabled());
            }

            if (signingOptions.getV2Enabled() != null) {
                signingConfigDsl.setV2SigningEnabled(signingOptions.getV2Enabled());
            }

            return signingConfigDsl;
        }
        return null;
    }

    @NonNull
    private ManifestAttributeSupplier getParser(
            @NonNull File file, boolean isManifestFileRequired) {
        return manifestParserMap.computeIfAbsent(
                file,
                f ->
                        new DefaultManifestParser(
                                f,
                                this::canParseManifest,
                                isManifestFileRequired,
                                globalScope.getDslScope().getIssueReporter()));
    }

    private boolean canParseManifest() {
        return hasCreatedTasks || !projectOptions.get(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING);
    }

    public void setHasCreatedTasks(boolean hasCreatedTasks) {
        this.hasCreatedTasks = hasCreatedTasks;
    }
}
