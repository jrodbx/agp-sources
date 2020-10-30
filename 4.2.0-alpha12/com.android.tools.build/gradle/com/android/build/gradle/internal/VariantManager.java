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

import static com.android.builder.core.VariantTypeImpl.ANDROID_TEST;
import static com.android.builder.core.VariantTypeImpl.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.analytics.AnalyticsEnabledAndroidTest;
import com.android.build.api.component.analytics.AnalyticsEnabledUnitTest;
import com.android.build.api.component.analytics.AnalyticsEnabledVariant;
import com.android.build.api.component.analytics.AnalyticsEnabledVariantBuilder;
import com.android.build.api.component.impl.AndroidTestBuilderImpl;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.TestComponentBuilderImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.UnitTestBuilderImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.extension.impl.OperationsRegistrar;
import com.android.build.api.variant.Variant;
import com.android.build.api.variant.impl.VariantBuilderImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoBuilder;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.crash.ExternalApiUsageException;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dependency.VariantDependenciesBuilder;
import com.android.build.gradle.internal.dsl.ActionableVariantObjectOperationsExecutor;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.manifest.LazyManifestParser;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScopeImpl;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.TaskCreationServicesImpl;
import com.android.build.gradle.internal.services.VariantApiServices;
import com.android.build.gradle.internal.services.VariantApiServicesImpl;
import com.android.build.gradle.internal.services.VariantPropertiesApiServicesImpl;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.DimensionCombination;
import com.android.build.gradle.internal.variant.DimensionCombinator;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantInputModel;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SigningOptions;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexingTypeKt;
import com.android.builder.errors.IssueReporter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.ApiVersion;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.model.ObjectFactory;

/** Class to create, manage variants. */
public class VariantManager<
        VariantBuilderT extends VariantBuilderImpl, VariantT extends VariantImpl> {

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final BaseExtension extension;

    @NonNull
    private final OperationsRegistrar<com.android.build.api.variant.VariantBuilder>
            variantBuilderOperationsRegistrar;

    @NonNull
    private final OperationsRegistrar<com.android.build.api.variant.Variant>
            variantOperationsRegistrar;

    @NonNull private final VariantFactory<VariantBuilderT, VariantT> variantFactory;

    @NonNull
    private final VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
            variantInputModel;

    @NonNull private final VariantApiServices variantApiServices;
    @NonNull private final VariantPropertiesApiServicesImpl variantPropertiesApiServices;
    @NonNull private final TaskCreationServices taskCreationServices;
    @NonNull private final ProjectServices projectServices;

    @NonNull private final VariantFilter variantFilter;

    @NonNull
    private final List<ComponentInfo<VariantBuilderT, VariantT>> variants = Lists.newArrayList();

    @NonNull
    private final List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> testComponents =
            Lists.newArrayList();

    @NonNull
    private final Map<File, LazyManifestParser> lazyManifestParserMap =
            Maps.newHashMapWithExpectedSize(3);

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
            @NonNull
                    OperationsRegistrar<com.android.build.api.variant.VariantBuilder>
                            variantBuilderOperationsRegistrar,
            @NonNull
                    OperationsRegistrar<com.android.build.api.variant.Variant>
                            variantOperationsRegistrar,
            @NonNull VariantFactory<VariantBuilderT, VariantT> variantFactory,
            @NonNull VariantInputModel variantInputModel,
            @NonNull ProjectServices projectServices) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.variantBuilderOperationsRegistrar = variantBuilderOperationsRegistrar;
        this.variantOperationsRegistrar = variantOperationsRegistrar;
        this.project = project;
        this.projectOptions = projectOptions;
        this.variantFactory = variantFactory;
        this.variantInputModel = variantInputModel;
        this.projectServices = projectServices;
        this.signingOverride = createSigningOverride();
        this.variantFilter = new VariantFilter(new ReadOnlyObjectProvider());

        variantApiServices = new VariantApiServicesImpl(projectServices);
        variantPropertiesApiServices = new VariantPropertiesApiServicesImpl(projectServices);
        taskCreationServices =
                new TaskCreationServicesImpl(variantPropertiesApiServices, projectServices);
    }

    /**
     * Returns a list of all main components.
     *
     * @see #createVariants(BuildFeatureValues)
     */
    @NonNull
    public List<ComponentInfo<VariantBuilderT, VariantT>> getMainComponents() {
        return variants;
    }

    /**
     * Returns a list of all test components.
     *
     * @see #createVariants(BuildFeatureValues)
     */
    @NonNull
    public List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> getTestComponents() {
        return testComponents;
    }

    /**
     * Creates the variants.
     *
     * @param buildFeatureValues the build feature value instance
     */
    public void createVariants(@NonNull BuildFeatureValues buildFeatureValues) {
        variantFactory.validateModel(variantInputModel);
        variantFactory.preVariantWork(project);

        computeVariants(buildFeatureValues);
    }

    @NonNull
    private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorSelection(
            @NonNull VariantDslInfo variantDslInfo) {
        ObjectFactory factory = project.getObjects();

        return variantDslInfo.getMissingDimensionStrategies().entrySet().stream()
                .collect(
                        Collectors.toMap(
                                entry -> Attribute.of(entry.getKey(), ProductFlavorAttr.class),
                                entry ->
                                        factory.named(
                                                ProductFlavorAttr.class,
                                                entry.getValue().getRequested())));
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

    /**
     * Create all variants.
     *
     * @param buildFeatureValues the build feature value instance
     */
    private void computeVariants(@NonNull BuildFeatureValues buildFeatureValues) {
        List<String> flavorDimensionList = extension.getFlavorDimensionList();

        DimensionCombinator computer =
                new DimensionCombinator(
                        variantInputModel,
                        projectServices.getIssueReporter(),
                        flavorDimensionList);

        List<DimensionCombination> variants = computer.computeVariants();

        // get some info related to testing
        BuildTypeData<BuildType> testBuildTypeData = getTestBuildTypeData();

        // loop on all the new variant objects to create the legacy ones.
        for (DimensionCombination variant : variants) {
            createVariantsFromCombination(variant, testBuildTypeData, buildFeatureValues);
        }

        // FIXME we should lock the variant API properties after all the onVariants, and
        // before any onVariantProperties to avoid cross access between the two.
        // This means changing the way to run onVariants vs onVariantProperties.
        variantApiServices.lockValues();
    }

    @Nullable
    private BuildTypeData<BuildType> getTestBuildTypeData() {
        BuildTypeData<BuildType> testBuildTypeData = null;
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
    private ComponentInfo<VariantBuilderT, VariantT> createVariant(
            @NonNull String projectPath,
            @NonNull DimensionCombination dimensionCombination,
            @NonNull BuildTypeData<BuildType> buildTypeData,
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorDataList,
            @NonNull VariantType variantType,
            @NonNull BuildFeatureValues buildFeatureValues) {
        // entry point for a given buildType/Flavors/VariantType combo.
        // Need to run the new variant API to selectively ignore variants.
        // in order to do this, we need access to the VariantDslInfo, to create a
        DslServices dslServices = globalScope.getDslServices();

        final DefaultConfigData<DefaultConfig> defaultConfig =
                variantInputModel.getDefaultConfigData();
        DefaultAndroidSourceSet defaultConfigSourceProvider = defaultConfig.getSourceSet();

        VariantDslInfoBuilder variantDslInfoBuilder =
                VariantDslInfoBuilder.getBuilder(
                        dimensionCombination,
                        variantType,
                        defaultConfig.getDefaultConfig(),
                        defaultConfigSourceProvider,
                        buildTypeData.getBuildType(),
                        buildTypeData.getSourceSet(),
                        signingOverride,
                        getLazyManifestParser(
                                defaultConfigSourceProvider.getManifestFile(),
                                variantType.getRequiresManifest(),
                                this::canParseManifest),
                        dslServices,
                        variantPropertiesApiServices);

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (ProductFlavorData<ProductFlavor> productFlavorData : productFlavorDataList) {
            variantDslInfoBuilder.addProductFlavor(
                    productFlavorData.getProductFlavor(), productFlavorData.getSourceSet());
        }

        VariantDslInfoImpl variantDslInfo =
                variantDslInfoBuilder.createVariantDslInfo(project.getLayout().getBuildDirectory());

        ComponentIdentity componentIdentity = variantDslInfo.getComponentIdentity();

        // create the Variant object so that we can run the action which may interrupt the creation
        // (in case of enabled = false)
        VariantBuilderT variantBuilder =
                variantFactory.createVariantObject(
                        componentIdentity, variantDslInfo, variantApiServices);

        // now that we have the variant, create the analytics object,
        AnalyticsConfiguratorService configuratorService =
                BuildServicesKt.getBuildService(
                                project.getGradle().getSharedServices(),
                                AnalyticsConfiguratorService.class)
                        .get();

        GradleBuildVariant.Builder profileBuilder =
                configuratorService.getVariantBuilder(project.getPath(), variantBuilder.getName());

        // HACK, we need access to the new type rather than the old. This will go away in the
        // future
        //noinspection unchecked
        ActionableVariantObjectOperationsExecutor<
                        com.android.build.api.variant.VariantBuilder, Variant>
                commonExtension =
                        (ActionableVariantObjectOperationsExecutor<
                                        com.android.build.api.variant.VariantBuilder, Variant>)
                                extension;

        AnalyticsEnabledVariantBuilder userVisibleVariantBuilderObject =
                variantBuilder.createUserVisibleVariantObject(projectServices, profileBuilder);
        commonExtension.executeVariantOperations(userVisibleVariantBuilderObject);

        // execute the new API
        variantBuilderOperationsRegistrar.executeOperations(userVisibleVariantBuilderObject);

        if (!variantBuilder.getEnabled()) {
            return null;
        }

        // now that we have the result of the filter, we can continue configuring the variant

        createCompoundSourceSets(productFlavorDataList, variantDslInfoBuilder);

        VariantSources variantSources = variantDslInfoBuilder.createVariantSources();

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
        variantSourceSets.add(variantInputModel.getDefaultConfigData().getSourceSet());

        // Create VariantDependencies
        VariantDependenciesBuilder builder =
                VariantDependenciesBuilder.builder(
                                project,
                                projectOptions,
                                projectServices.getIssueReporter(),
                                variantDslInfo)
                        .setFlavorSelection(getFlavorSelection(variantDslInfo))
                        .addSourceSets(variantSourceSets);

        if (extension instanceof BaseAppModuleExtension) {
            builder.setFeatureList(((BaseAppModuleExtension) extension).getDynamicFeatures());
        }

        final VariantDependencies variantDependencies = builder.build();

        // Done. Create the (too) many variant objects

        VariantPathHelper pathHelper = new VariantPathHelper(project, variantDslInfo, dslServices);

        ArtifactsImpl artifacts = new ArtifactsImpl(project, componentIdentity.getName());

        MutableTaskContainer taskContainer = new MutableTaskContainer();
        TransformManager transformManager =
                new TransformManager(project, dslServices.getIssueReporter());

        // create the obsolete VariantScope
        VariantScopeImpl variantScope =
                new VariantScopeImpl(
                        componentIdentity,
                        variantDslInfo,
                        variantDependencies,
                        pathHelper,
                        artifacts,
                        globalScope,
                        null /* testedVariantProperties*/);

        // and the obsolete variant data
        BaseVariantData variantData =
                variantFactory.createVariantData(
                        componentIdentity,
                        variantDslInfo,
                        variantDependencies,
                        variantSources,
                        pathHelper,
                        artifacts,
                        variantPropertiesApiServices,
                        globalScope,
                        taskContainer);

        // then the new VariantProperties which will contain the 2 old objects.
        VariantT variantApiObject =
                variantFactory.createVariantPropertiesObject(
                        variantBuilder,
                        componentIdentity,
                        buildFeatureValues,
                        variantDslInfo,
                        variantDependencies,
                        variantSources,
                        pathHelper,
                        artifacts,
                        variantScope,
                        variantData,
                        transformManager,
                        variantPropertiesApiServices,
                        taskCreationServices);

        // Run the VariantProperties actions
        AnalyticsEnabledVariant userVisibleVariantPropertiesObject =
                ((VariantImpl) variantApiObject)
                        .createUserVisibleVariantPropertiesObject(projectServices, profileBuilder);
        commonExtension.executeVariantPropertiesOperations(userVisibleVariantPropertiesObject);
        variantOperationsRegistrar.executeOperations(userVisibleVariantPropertiesObject);

        return new ComponentInfo(
                variantBuilder, variantApiObject, profileBuilder, userVisibleVariantBuilderObject);
    }

    private void createCompoundSourceSets(
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorList,
            @NonNull VariantDslInfoBuilder variantDslInfoBuilder) {
        final VariantType variantType = variantDslInfoBuilder.getVariantType();

        if (!productFlavorList.isEmpty() /* && !variantConfig.getType().isSingleBuildType()*/) {
            DefaultAndroidSourceSet variantSourceSet =
                    (DefaultAndroidSourceSet)
                            variantInputModel
                                    .getSourceSetManager()
                                    .setUpSourceSet(
                                            VariantDslInfoBuilder.computeSourceSetName(
                                                    variantDslInfoBuilder.getName(), variantType),
                                            variantType.isTestComponent());
            variantDslInfoBuilder.setVariantSourceProvider(variantSourceSet);
        }

        if (productFlavorList.size() > 1) {
            DefaultAndroidSourceSet multiFlavorSourceSet =
                    (DefaultAndroidSourceSet)
                            variantInputModel
                                    .getSourceSetManager()
                                    .setUpSourceSet(
                                            VariantDslInfoBuilder.computeSourceSetName(
                                                    variantDslInfoBuilder.getFlavorName(),
                                                    variantType),
                                            variantType.isTestComponent());
            variantDslInfoBuilder.setMultiFlavorSourceProvider(multiFlavorSourceSet);
        }
    }

    /** Create a TestVariantData for the specified testedVariantData. */
    @Nullable
    public ComponentInfo<TestComponentBuilderImpl, TestComponentImpl> createTestComponents(
            @NonNull DimensionCombination dimensionCombination,
            @NonNull BuildTypeData<BuildType> buildTypeData,
            @NonNull List<ProductFlavorData<ProductFlavor>> productFlavorDataList,
            @NonNull ComponentInfo<VariantBuilderT, VariantT> testedComponentInfo,
            @NonNull VariantType variantType) {

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        final DefaultAndroidSourceSet testSourceSet =
                variantInputModel.getDefaultConfigData().getTestSourceSet(variantType);
        DslServices dslServices = globalScope.getDslServices();

        @SuppressWarnings("ConstantConditions")
        VariantDslInfoBuilder variantDslInfoBuilder =
                VariantDslInfoBuilder.getBuilder(
                        dimensionCombination,
                        variantType,
                        variantInputModel.getDefaultConfigData().getDefaultConfig(),
                        testSourceSet,
                        buildTypeData.getBuildType(),
                        buildTypeData.getTestSourceSet(variantType),
                        signingOverride,
                        getLazyManifestParser(
                                testSourceSet.getManifestFile(),
                                variantType.getRequiresManifest(),
                                this::canParseManifest),
                        dslServices,
                        variantPropertiesApiServices);

        variantDslInfoBuilder.setTestedVariant(
                (VariantDslInfoImpl) testedComponentInfo.getProperties().getVariantDslInfo());

        List<ProductFlavor> productFlavorList =
                testedComponentInfo.getProperties().getVariantDslInfo().getProductFlavorList();

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        final Map<String, ProductFlavorData<ProductFlavor>> productFlavors =
                variantInputModel.getProductFlavors();
        for (ProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<ProductFlavor> data = productFlavors.get(productFlavor.getName());

            //noinspection ConstantConditions
            variantDslInfoBuilder.addProductFlavor(
                    data.getProductFlavor(), data.getTestSourceSet(variantType));
        }

        VariantDslInfoImpl variantDslInfo =
                variantDslInfoBuilder.createVariantDslInfo(project.getLayout().getBuildDirectory());

        TestComponentBuilderImpl component;

        GradleBuildVariant.Builder apiAccessStats = testedComponentInfo.getStats();
        // this is ANDROID_TEST
        if (variantType.isApk()) {
            AndroidTestBuilderImpl androidTestVariant =
                    variantFactory.createAndroidTestObject(
                            variantDslInfo.getComponentIdentity(),
                            variantDslInfo,
                            variantApiServices);

            // run the action registered on the tested variant via androidTest {}
            if (testedComponentInfo.getUserVisibleVariant() != null)
                testedComponentInfo
                        .getUserVisibleVariant()
                        .executeAndroidTestActions(androidTestVariant);

            component = androidTestVariant;
        } else {
            // this is UNIT_TEST
            UnitTestBuilderImpl unitTestVariant =
                    variantFactory.createUnitTestObject(
                            variantDslInfo.getComponentIdentity(),
                            variantDslInfo,
                            variantApiServices);

            // run the action registered on the tested variant via unitTest {}
            if (testedComponentInfo.getUserVisibleVariant() != null)
                testedComponentInfo.getUserVisibleVariant().executeUnitTestActions(unitTestVariant);

            component = unitTestVariant;
        }

        if (!component.getEnabled()) {
            return null;
        }

        // now that we have the result of the filter, we can continue configuring the variant
        createCompoundSourceSets(productFlavorDataList, variantDslInfoBuilder);

        VariantSources variantSources = variantDslInfoBuilder.createVariantSources();

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
                variantInputModel.getDefaultConfigData().getTestSourceSet(variantType));

        // If the variant being tested is a library variant, VariantDependencies must be
        // computed after the tasks for the tested variant is created.  Therefore, the
        // VariantDependencies is computed here instead of when the VariantData was created.
        VariantDependenciesBuilder builder =
                VariantDependenciesBuilder.builder(
                                project,
                                projectOptions,
                                projectServices.getIssueReporter(),
                                variantDslInfo)
                        .addSourceSets(testVariantSourceSets)
                        .setFlavorSelection(getFlavorSelection(variantDslInfo))
                        .setTestedVariant(testedComponentInfo.getProperties());

        final VariantDependencies variantDependencies = builder.build();

        VariantPathHelper pathHelper = new VariantPathHelper(project, variantDslInfo, dslServices);
        ComponentIdentity componentIdentity = variantDslInfo.getComponentIdentity();

        ArtifactsImpl artifacts = new ArtifactsImpl(project, componentIdentity.getName());

        MutableTaskContainer taskContainer = new MutableTaskContainer();
        TransformManager transformManager =
                new TransformManager(project, dslServices.getIssueReporter());

        VariantScopeImpl variantScope =
                new VariantScopeImpl(
                        componentIdentity,
                        variantDslInfo,
                        variantDependencies,
                        pathHelper,
                        artifacts,
                        globalScope,
                        testedComponentInfo.getProperties());

        // create the internal storage for this variant.
        TestVariantData testVariantData =
                new TestVariantData(
                        componentIdentity,
                        variantDslInfo,
                        variantDependencies,
                        variantSources,
                        pathHelper,
                        artifacts,
                        (TestedVariantData) testedComponentInfo.getProperties().getVariantData(),
                        variantPropertiesApiServices,
                        globalScope,
                        taskContainer);

        TestComponentImpl componentProperties;

        BuildFeatureValues buildFeatureValues =
                variantFactory.createTestBuildFeatureValues(
                        extension.getBuildFeatures(), extension.getDataBinding(), projectOptions);

        // this is ANDROID_TEST
        if (variantType.isApk()) {
            AndroidTestImpl androidTestProperties =
                    variantFactory.createAndroidTestProperties(
                            (AndroidTestBuilderImpl) component,
                            buildFeatureValues,
                            variantDslInfo,
                            variantDependencies,
                            variantSources,
                            pathHelper,
                            artifacts,
                            variantScope,
                            testVariantData,
                            testedComponentInfo.getProperties(),
                            transformManager,
                            variantPropertiesApiServices,
                            taskCreationServices);

            // also execute the delayed actions registered on the Component via
            // androidTest { onProperties {} }
            AnalyticsEnabledAndroidTest userVisibleVariantPropertiesObject =
                    androidTestProperties.createUserVisibleVariantPropertiesObject(
                            projectServices, apiAccessStats);
            // or on the tested variant via unitTestProperties {}
            if (testedComponentInfo.getUserVisibleVariant() != null)
                testedComponentInfo
                        .getUserVisibleVariant()
                        .executeAndroidTestPropertiesActions(userVisibleVariantPropertiesObject);

            componentProperties = androidTestProperties;
        } else {
            // this is UNIT_TEST
            UnitTestImpl unitTestProperties =
                    variantFactory.createUnitTestProperties(
                            (UnitTestBuilderImpl) component,
                            buildFeatureValues,
                            variantDslInfo,
                            variantDependencies,
                            variantSources,
                            pathHelper,
                            artifacts,
                            variantScope,
                            testVariantData,
                            testedComponentInfo.getProperties(),
                            transformManager,
                            variantPropertiesApiServices,
                            taskCreationServices);

            // execute the delayed actions registered on the Component via
            // unitTest { onProperties {} }
            AnalyticsEnabledUnitTest userVisibleVariantPropertiesObject =
                    unitTestProperties.createUserVisibleVariantPropertiesObject(
                            projectServices, apiAccessStats);
            // or on the tested variant via unitTestProperties {}
            if (testedComponentInfo.getUserVisibleVariant() != null)
                testedComponentInfo
                        .getUserVisibleVariant()
                        .executeUnitTestPropertiesActions(userVisibleVariantPropertiesObject);

            componentProperties = unitTestProperties;
        }

        // register
        testedComponentInfo
                .getProperties()
                .getTestComponents()
                .put(variantDslInfo.getVariantType(), componentProperties);

        return new ComponentInfo<>(
                component, componentProperties, testedComponentInfo.getStats(), null);
    }

    /**
     * Creates Variant objects for a specific {@link ComponentIdentity}
     *
     * <p>This will create both the prod and the androidTest/unitTest variants.
     */
    private void createVariantsFromCombination(
            @NonNull DimensionCombination dimensionCombination,
            @Nullable BuildTypeData<BuildType> testBuildTypeData,
            @NonNull BuildFeatureValues buildFeatureValues) {
        VariantType variantType = variantFactory.getVariantType();

        // first run the old variantFilter API
        // This acts on buildtype/flavor only, and applies in one pass to prod/tests.
        Action<com.android.build.api.variant.VariantFilter> variantFilterAction =
                extension.getVariantFilter();

        DefaultConfig defaultConfig = variantInputModel.getDefaultConfigData().getDefaultConfig();

        BuildTypeData<BuildType> buildTypeData =
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
            ComponentInfo<VariantBuilderT, VariantT> variantInfo =
                    createVariant(
                            project.getPath(),
                            dimensionCombination,
                            buildTypeData,
                            productFlavorDataList,
                            variantType,
                            buildFeatureValues);
            if (variantInfo != null) {
                addVariant(variantInfo);

                VariantT variant = variantInfo.getProperties();

                VariantDslInfo variantDslInfo = variant.getVariantDslInfo();
                VariantScope variantScope = variant.getVariantScope();

                int minSdkVersion = variant.getMinSdkVersion().getApiLevel();
                int targetSdkVersion = variantDslInfo.getTargetSdkVersion().getApiLevel();
                if (minSdkVersion > 0 && targetSdkVersion > 0 && minSdkVersion > targetSdkVersion) {
                    projectServices
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
                                            variant.getName()));
                }

                GradleBuildVariant.Builder variantBuilder = variantInfo.getStats();
                variantBuilder
                        .setIsDebug(buildType.isDebuggable())
                        .setMinSdkVersion(AnalyticsUtil.toProto(variant.getMinSdkVersion()))
                        .setMinifyEnabled(variant.getCodeShrinker() != null)
                        .setUseMultidex(variant.isMultiDexEnabled())
                        .setUseLegacyMultidex(
                                DexingTypeKt.isLegacyMultiDexMode(variant.getDexingType()))
                        .setVariantType(variant.getVariantType().getAnalyticsVariantType())
                        .setDexBuilder(AnalyticsUtil.toProto(variantScope.getDexer()))
                        .setDexMerger(AnalyticsUtil.toProto(variantScope.getDexMerger()))
                        .setCoreLibraryDesugaringEnabled(variant.isCoreLibraryDesugaringEnabled())
                        .setTestExecution(
                                AnalyticsUtil.toProto(
                                        globalScope
                                                .getExtension()
                                                .getTestOptions()
                                                .getExecutionEnum()));

                if (variant.getCodeShrinker() != null) {
                    variantBuilder.setCodeShrinker(
                            AnalyticsUtil.toProto(variant.getCodeShrinker()));
                }

                if (variantDslInfo.getTargetSdkVersion().getApiLevel() > 0) {
                    variantBuilder.setTargetSdkVersion(
                            AnalyticsUtil.toProto(variantDslInfo.getTargetSdkVersion()));
                }
                if (variant.getMaxSdkVersion() != null) {
                    variantBuilder.setMaxSdkVersion(
                            ApiVersion.newBuilder().setApiLevel(variant.getMaxSdkVersion()));
                }

                VariantScope.Java8LangSupport supportType = variant.getJava8LangSupportType();
                if (supportType != VariantScope.Java8LangSupport.INVALID
                        && supportType != VariantScope.Java8LangSupport.UNUSED) {
                    variantBuilder.setJava8LangSupport(AnalyticsUtil.toProto(supportType));
                }

                if (variantFactory.getVariantType().getHasTestComponents()) {
                    if (buildTypeData == testBuildTypeData) {
                        ComponentInfo<TestComponentBuilderImpl, TestComponentImpl> androidTest =
                                createTestComponents(
                                        dimensionCombination,
                                        buildTypeData,
                                        productFlavorDataList,
                                        variantInfo,
                                        ANDROID_TEST);
                        if (androidTest != null) {
                            addTestComponent(androidTest);
                        }
                    }

                    ComponentInfo<TestComponentBuilderImpl, TestComponentImpl> unitTest =
                            createTestComponents(
                                    dimensionCombination,
                                    buildTypeData,
                                    productFlavorDataList,
                                    variantInfo,
                                    UNIT_TEST);
                    if (unitTest != null) {
                        addTestComponent(unitTest);
                    }
                }
            }
        }
    }

    private void addVariant(@NonNull ComponentInfo<VariantBuilderT, VariantT> variant) {
        variants.add(variant);
    }

    private void addTestComponent(
            @NonNull ComponentInfo<TestComponentBuilderImpl, TestComponentImpl> testComponent) {
        testComponents.add(testComponent);
    }

    private SigningConfig createSigningOverride() {
        SigningOptions signingOptions = SigningOptions.readSigningOptions(projectOptions);
        if (signingOptions != null) {
            SigningConfig signingConfigDsl = new SigningConfig("externalOverride");

            signingConfigDsl.storeFile(new File(signingOptions.getStoreFile()));
            signingConfigDsl.storePassword(signingOptions.getStorePassword());
            signingConfigDsl.keyAlias(signingOptions.getKeyAlias());
            signingConfigDsl.keyPassword(signingOptions.getKeyPassword());

            if (signingOptions.getStoreType() != null) {
                signingConfigDsl.storeType(signingOptions.getStoreType());
            }

            if (signingOptions.getV1Enabled() != null) {
                signingConfigDsl.setV1SigningEnabled(signingOptions.getV1Enabled());
            }

            if (signingOptions.getV2Enabled() != null) {
                signingConfigDsl.setV2SigningEnabled(signingOptions.getV2Enabled());
            }

            signingConfigDsl.setEnableV3Signing(signingOptions.getEnableV3Signing());
            signingConfigDsl.setEnableV4Signing(signingOptions.getEnableV4Signing());

            return signingConfigDsl;
        }
        return null;
    }

    @NonNull
    private LazyManifestParser getLazyManifestParser(
            @NonNull File file,
            boolean isManifestFileRequired,
            @NonNull BooleanSupplier isInExecutionPhase) {
        return lazyManifestParserMap.computeIfAbsent(
                file,
                f ->
                        new LazyManifestParser(
                                projectServices.getObjectFactory().fileProperty().fileValue(f),
                                isManifestFileRequired,
                                projectServices,
                                isInExecutionPhase));
    }

    private boolean canParseManifest() {
        return hasCreatedTasks || !projectOptions.get(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING);
    }

    public void setHasCreatedTasks(boolean hasCreatedTasks) {
        this.hasCreatedTasks = hasCreatedTasks;
    }

    public void lockVariantProperties() {
        variantPropertiesApiServices.lockProperties();
    }
}
