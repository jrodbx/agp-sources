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
package com.android.build.gradle.internal

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.impl.DeviceTestImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.TestedExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.HasDeviceTests
import com.android.build.api.variant.HasDeviceTestsBuilder
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HasTestFixturesBuilder
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.api.variant.impl.ArtifactMetadataProcessor
import com.android.build.api.variant.impl.DeviceTestBuilderImpl
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig
import com.android.build.api.variant.impl.GlobalVariantBuilderConfigImpl
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.api.variant.impl.HostTestBuilderImpl
import com.android.build.api.variant.impl.HasDeviceTestsCreationConfig
import com.android.build.api.variant.impl.HasHostTestsCreationConfig
import com.android.build.api.variant.impl.InternalVariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider
import com.android.build.gradle.internal.api.VariantFilter
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo
import com.android.build.gradle.internal.core.dsl.TestedVariantDslInfo
import com.android.build.gradle.internal.core.dsl.HostTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.core.dsl.impl.DslInfoBuilder
import com.android.build.gradle.internal.core.dsl.impl.DslInfoBuilder.Companion.getBuilder
import com.android.build.gradle.internal.core.dsl.impl.computeSourceSetName
import com.android.build.gradle.internal.crash.ExternalApiUsageException
import com.android.build.gradle.internal.dependency.VariantDependenciesBuilder
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.LazyManifestParser
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.internal.services.VariantBuilderServicesImpl
import com.android.build.gradle.internal.services.VariantServicesImpl
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.SigningConfigUtils.Companion.createSigningOverride
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl.Companion.toExecutionEnum
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.internal.variant.DimensionCombinator
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.VariantComponentInfo
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.AbstractProductFlavor.DimensionRequest
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.TestOptions
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.ApiVersion
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.GeneratedSubclass
import java.io.File
import java.util.Locale
import java.util.stream.Collectors

/** Class to create, manage variants.  */
class VariantManager<
        CommonExtensionT: CommonExtension<*, *, *, *, *, *>,
        VariantBuilderT : VariantBuilder,
        VariantDslInfoT: VariantDslInfo,
        VariantT : VariantCreationConfig>(
    private val project: Project,
    private val dslServices: DslServices,
    @Deprecated("Use dslExtension")  private val oldExtension: BaseExtension,
    private val dslExtension: CommonExtensionT,
    val variantApiOperationsRegistrar: VariantApiOperationsRegistrar<CommonExtensionT, VariantBuilder, Variant>,
    private val variantFactory: VariantFactory<VariantBuilderT, VariantDslInfoT, VariantT>,
    private val variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
    val globalTaskCreationConfig: GlobalTaskCreationConfig,
    private val projectServices: ProjectServices
) {

    private val variantBuilderServices: VariantBuilderServices
    private val variantPropertiesApiServices: VariantServicesImpl
    private val taskCreationServices: TaskCreationServices
    private val variantFilter: VariantFilter
    private val variants: MutableList<ComponentInfo<VariantBuilderT, VariantT>> =
            Lists.newArrayList()
    private val lazyManifestParserMap: MutableMap<File, LazyManifestParser> =
            Maps.newHashMapWithExpectedSize(3)
    private val signingOverride: SigningConfig?

    // We cannot use gradle's state of executed as that returns true while inside afterEvalute.
    // Wew want this to only be true after all tasks have been create.
    private var hasCreatedTasks = false

    /**
     * Returns a list of all main components.
     *
     * @see .createVariants
     */
    val mainComponents: List<ComponentInfo<VariantBuilderT, VariantT>>
        get() = variants

    /**
     * Returns a list of all nested components.
     */
    val nestedComponents: MutableList<NestedComponentCreationConfig> = Lists.newArrayList()

    /**
     * Returns a list of all test components.
     *
     * @see .createVariants
     */
    val testComponents: MutableList<TestComponentCreationConfig> =
            Lists.newArrayList()

    /**
     * Returns a list of all test fixtures components.
     */
    val testFixturesComponents: MutableList<TestFixturesCreationConfig> = Lists.newArrayList()

    val buildFeatureValues: BuildFeatureValues
        get() = _buildFeatureValues

    private lateinit var _buildFeatureValues: BuildFeatureValues

    /**
     * Creates the variants.
     *
     * @param buildFeatureValues the build feature value instance
     */
    fun createVariants(
        buildFeatureValues: BuildFeatureValues,
    ) {
        _buildFeatureValues = buildFeatureValues
        variantFactory.preVariantCallback(project, dslExtension, variantInputModel)
        computeVariants()
    }

    private fun getFlavorSelection(
            variantDslInfo: ComponentDslInfo): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
        val factory = project.objects
        return variantDslInfo.missingDimensionStrategies.entries.stream()
                .collect(
                        Collectors.toMap(
                                { entry: Map.Entry<String, DimensionRequest> ->
                                    ProductFlavorAttr.of(entry.key)
                                }
                        ) { entry: Map.Entry<String, DimensionRequest> ->
                            factory.named(
                                    ProductFlavorAttr::class.java,
                                    entry.value.requested)
                        })
    }

    /**
     * Create all variants.
     */
    private fun computeVariants() {
        val flavorDimensionList: List<String> = dslExtension.flavorDimensions
        val computer = DimensionCombinator(
                variantInputModel,
                projectServices.issueReporter,
                flavorDimensionList)
        val variants = computer.computeVariants()

        // get some info related to testing
        val testBuildTypeData = testBuildTypeData

        val globalConfig = GlobalVariantBuilderConfigImpl(dslExtension)

        // loop on all the new variant objects to create the public instances (both legacy and new
        // API). Store the finalization blocks to invoke them later synchronously.
        val finalizationBlocks = mutableListOf<() -> Unit>()
        for (variant in variants) {
            val block = createVariantsFromCombination(
                    variant,
                    testBuildTypeData,
                    globalConfig
            )
            finalizationBlocks.add(block)
        }

        // all variant builders and variants have been created, leading to all beforeVariants being
        // called before any onVariants block started being called so now can invoke the onVariants
        // API and finalize the variant instance.
        finalizationBlocks.forEach { it.invoke() }


        // FIXME we should lock the variant API properties after all the beforeVariants, and
        // before any onVariants to avoid cross access between the two.
        // This means changing the way to run beforeVariants vs onVariants.
        variantBuilderServices.lockValues()
    }

    private val testBuildTypeData: BuildTypeData<BuildType>?
        get() {
            var testBuildTypeData: BuildTypeData<BuildType>? = null
            if (dslExtension is TestedExtension) {
                testBuildTypeData = variantInputModel.buildTypes[dslExtension.testBuildType]
                if (testBuildTypeData == null) {
                    throw RuntimeException(String.format(
                            "Test Build Type '%1\$s' does not" + " exist.",
                            dslExtension.testBuildType))
                }
            }
            return testBuildTypeData
        }

    private fun createVariant(
        dimensionCombination: DimensionCombination,
        buildTypeData: BuildTypeData<BuildType>,
        productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
        componentType: ComponentType,
        globalConfig: GlobalVariantBuilderConfig,
    ): VariantComponentInfo<VariantBuilderT, VariantDslInfoT, VariantT>? {
        // entry point for a given buildType/Flavors/VariantType combo.
        // Need to run the new variant API to selectively ignore variants.
        // in order to do this, we need access to the VariantDslInfo, to create a
        val defaultConfig = variantInputModel.defaultConfigData
        val defaultConfigSourceProvider = defaultConfig.sourceSet
        val variantDslInfoBuilder = getBuilder<CommonExtensionT, VariantDslInfoT>(
                dimensionCombination,
                componentType,
                defaultConfig.defaultConfig,
                defaultConfigSourceProvider,
                buildTypeData.buildType,
                buildTypeData.sourceSet,
                signingOverride,
                getLazyManifestParser(
                    defaultConfigSourceProvider.manifestFile,
                    componentType.requiresManifest,
                ),
                variantPropertiesApiServices,
                dslExtension,
                project.layout.buildDirectory,
                dslServices
        )
        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (productFlavorData in productFlavorDataList) {
            variantDslInfoBuilder.addProductFlavor(
                    productFlavorData.productFlavor, productFlavorData.sourceSet)
        }
        val variantDslInfo = variantDslInfoBuilder.createDslInfo()
        val componentIdentity = variantDslInfo.componentIdentity

        // create the Variant object so that we can run the action which may interrupt the creation
        // (in case of enabled = false)
        val variantBuilder = variantFactory.createVariantBuilder(
            globalConfig, componentIdentity, variantDslInfo, variantBuilderServices,
        )

        // now that we have the variant, create the analytics object,
        val configuratorService = getBuildService(
                project.gradle.sharedServices,
                AnalyticsConfiguratorService::class.java)
                .get()
        val profileEnabledVariantBuilder = configuratorService.getVariantBuilder(
                project.path, variantBuilder.name)

        val userVisibleVariantBuilder =
            (variantBuilder as InternalVariantBuilder).createUserVisibleVariantObject<VariantBuilder>(
                        projectServices,
                        profileEnabledVariantBuilder,
                )

        // execute the beforeVariants Variant API
        variantApiOperationsRegistrar.variantBuilderOperations.executeOperations(userVisibleVariantBuilder)
        if (!variantBuilder.enable) {
            return null
        }

        // now that we have the result of the filter, we can continue configuring the variant
        createCompoundSourceSets(productFlavorDataList, variantDslInfoBuilder)
        val variantSources = variantDslInfoBuilder.createVariantSources()

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        val variantSourceSets: MutableList<DefaultAndroidSourceSet?> =
                Lists.newArrayListWithExpectedSize(productFlavorDataList.size + 4)

        // 1. add the variant-specific if applicable.
        if (productFlavorDataList.isNotEmpty()) {
            variantSourceSets.add(variantSources.variantSourceProvider)
        }

        // 2. the build type.
        variantSourceSets.add(buildTypeData.sourceSet)

        // 3. the multi-flavor combination
        if (productFlavorDataList.size > 1) {
            variantSourceSets.add(variantSources.multiFlavorSourceProvider)
        }

        // 4. the flavors.
        for (productFlavor in productFlavorDataList) {
            variantSourceSets.add(productFlavor.sourceSet)
        }

        // 5. The defaultConfig
        variantSourceSets.add(variantInputModel.defaultConfigData.sourceSet)

        // Create VariantDependencies
        val builder = VariantDependenciesBuilder.builder(
                project,
                dslServices.projectOptions,
                projectServices.issueReporter,
                variantDslInfo)
                .setFlavorSelection(getFlavorSelection(variantDslInfo))
                .addSourceSets(variantSourceSets)
        if (dslExtension is ApplicationExtension) {
            builder.setFeatureList(dslExtension.dynamicFeatures)
        }

        val variantDependencies = builder.build()

        // Done. Create the (too) many variant objects
        val pathHelper =
            VariantPathHelper(
                project.layout.buildDirectory,
                variantDslInfo,
                dslServices
            )

        val mappingScopePolicy: (ScopedArtifacts.Scope) -> ScopedArtifacts.Scope =
            if (componentType.isAar) {
                // Scope.ALL does not make a lot of sense or libraries, so we treat it like
                // it's a Scope.Project
                { scope -> if (scope == ScopedArtifacts.Scope.ALL) ScopedArtifacts.Scope.PROJECT else scope }
            } else { x -> x }

        val artifacts = ArtifactsImpl(project, componentIdentity.name, mappingScopePolicy)
        val taskContainer = MutableTaskContainer()

        // and the obsolete variant data
        val variantData = variantFactory.createVariantData(
            componentIdentity,
            artifacts,
            variantPropertiesApiServices,
            taskContainer
        )

        // then the new Variant which will contain the 2 old objects.
        val variantApiObject = variantFactory.createVariant(
            variantBuilder,
            componentIdentity,
            buildFeatureValues,
            variantDslInfo,
            variantDependencies,
            variantSources,
            pathHelper,
            artifacts,
            variantData,
            taskContainer,
            variantPropertiesApiServices,
            taskCreationServices,
            globalTaskCreationConfig,
        )

        return VariantComponentInfo(
            variantBuilder,
            variantApiObject,
            profileEnabledVariantBuilder,
            variantDslInfo
        )
    }

    private fun<DslInfoT: ComponentDslInfo> createCompoundSourceSets(
            productFlavorList: List<ProductFlavorData<ProductFlavor>>,
            dslInfoBuilder: DslInfoBuilder<CommonExtensionT, DslInfoT>) {
        val componentType = dslInfoBuilder.componentType
        if (productFlavorList.isNotEmpty() /* && !variantConfig.getType().isSingleBuildType()*/) {
            val variantSourceSet =
                variantInputModel
                    .sourceSetManager
                    .setUpSourceSet(
                        computeSourceSetName(dslInfoBuilder.name, componentType),
                        componentType
                    )
                    .get()
            addExtraSourceSets(variantSourceSet)
            dslInfoBuilder.variantSourceProvider = variantSourceSet
        }
        if (productFlavorList.size > 1) {
            val multiFlavorSourceSet =
                variantInputModel
                    .sourceSetManager
                    .setUpSourceSet(
                        computeSourceSetName(dslInfoBuilder.flavorName, componentType),
                        componentType
                    )
                    .get()
            addExtraSourceSets(multiFlavorSourceSet)
            dslInfoBuilder.multiFlavorSourceProvider = multiFlavorSourceSet
        }
    }

    private fun addExtraSourceSets(sourceSet: DefaultAndroidSourceSet) {
        variantApiOperationsRegistrar.onEachSourceSetExtensions { name: String ->
            sourceSet.extras.create(name)
        }
    }

    /** Create a test fixtures component for the specified main component.  */
    private fun createTestFixturesComponent(
        dimensionCombination: DimensionCombination,
        buildTypeData: BuildTypeData<BuildType>,
        productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
        mainComponentInfo: VariantComponentInfo<VariantBuilderT, VariantDslInfoT, VariantT>
    ): TestFixturesCreationConfig {
        val testFixturesComponentType = ComponentTypeImpl.TEST_FIXTURES
        val testFixturesSourceSet = variantInputModel.defaultConfigData.getSourceSet(
            ComponentTypeImpl.TEST_FIXTURES
        )!!
        val variantDslInfoBuilder = getBuilder<CommonExtensionT, TestFixturesComponentDslInfo>(
            dimensionCombination,
            testFixturesComponentType,
            variantInputModel.defaultConfigData.defaultConfig,
            testFixturesSourceSet,
            buildTypeData.buildType,
            buildTypeData.getSourceSet(ComponentTypeImpl.TEST_FIXTURES),
            signingOverride,
            getLazyManifestParser(
                testFixturesSourceSet.manifestFile,
                testFixturesComponentType.requiresManifest
            ),
            variantPropertiesApiServices,
            extension = dslExtension,
            buildDirectory = project.layout.buildDirectory,
            dslServices = dslServices
        )

        variantDslInfoBuilder.productionVariant =
            mainComponentInfo.variantDslInfo as TestedVariantDslInfo

        val productFlavorList = (mainComponentInfo.variantDslInfo as MultiVariantComponentDslInfo).productFlavorList

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        val productFlavors = variantInputModel.productFlavors
        for (productFlavor in productFlavorList) {
            productFlavors[productFlavor.name]?.let {
                variantDslInfoBuilder.addProductFlavor(
                    it.productFlavor,
                    it.getSourceSet(ComponentTypeImpl.TEST_FIXTURES)!!
                )
            }
        }
        val testFixturesComponentDslInfo = variantDslInfoBuilder.createDslInfo()

        // now that we have the result of the filter, we can continue configuring the variant
        createCompoundSourceSets(productFlavorDataList, variantDslInfoBuilder)
        val variantSources = variantDslInfoBuilder.createVariantSources()

        // Add the container of dependencies, the order of the libraries is important.
        // In descending order: build type (only for unit test), flavors, defaultConfig.

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type (, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        val testFixturesVariantSourceSets: MutableList<DefaultAndroidSourceSet?> =
            Lists.newArrayListWithExpectedSize(4 + productFlavorList.size)

        // 1. add the variant-specific if applicable.
        if (productFlavorList.isNotEmpty()) {
            testFixturesVariantSourceSets.add(variantSources.variantSourceProvider)
        }

        // 2. the build type.
        val buildTypeConfigurationProvider = buildTypeData.getSourceSet(ComponentTypeImpl.TEST_FIXTURES)
        buildTypeConfigurationProvider?.let {
            testFixturesVariantSourceSets.add(it)
        }

        // 3. the multi-flavor combination
        if (productFlavorList.size > 1) {
            testFixturesVariantSourceSets.add(variantSources.multiFlavorSourceProvider)
        }

        // 4. the flavors.
        for (productFlavor in productFlavorList) {
            variantInputModel.productFlavors[productFlavor.name]?.let {
                testFixturesVariantSourceSets.add(it.getSourceSet(ComponentTypeImpl.TEST_FIXTURES))
            }
        }

        // now add the default config
        testFixturesVariantSourceSets.add(
            variantInputModel.defaultConfigData.getSourceSet(ComponentTypeImpl.TEST_FIXTURES)
        )

        // If the variant being tested is a library variant, VariantDependencies must be
        // computed after the tasks for the tested variant is created.  Therefore, the
        // VariantDependencies is computed here instead of when the VariantData was created.
        val variantDependencies = VariantDependenciesBuilder.builder(
            project,
            dslServices.projectOptions,
            projectServices.issueReporter,
            testFixturesComponentDslInfo
        )
            .addSourceSets(testFixturesVariantSourceSets)
            .setFlavorSelection(getFlavorSelection(testFixturesComponentDslInfo))
            .overrideVariantNameAttribute(mainComponentInfo.variant.name)
            .setMainVariant(mainComponentInfo.variant)
            .build()
        val pathHelper =
            VariantPathHelper(
                project.layout.buildDirectory,
                testFixturesComponentDslInfo,
                dslServices
            )
        val componentIdentity = testFixturesComponentDslInfo.componentIdentity
        val artifacts = ArtifactsImpl(project, componentIdentity.name)
        val taskContainer = MutableTaskContainer()
        val testFixturesBuildFeatureValues = variantFactory.createTestFixturesBuildFeatureValues(
            dslExtension.buildFeatures,
            projectServices,
            testFixturesComponentDslInfo.testFixturesAndroidResourcesEnabled
        )

        return variantFactory.createTestFixtures(
            testFixturesComponentDslInfo.componentIdentity,
            testFixturesBuildFeatureValues,
            testFixturesComponentDslInfo,
            variantDependencies,
            variantSources,
            pathHelper,
            artifacts,
            taskContainer,
            mainComponentInfo.variant,
            variantPropertiesApiServices,
            taskCreationServices,
            globalTaskCreationConfig
        )
    }

    /** Create a TestVariantData for the specified testedVariantData.  */
    fun<TestDslInfoT: TestComponentDslInfo> createTestComponents(
        dimensionCombination: DimensionCombination,
        buildTypeData: BuildTypeData<BuildType>,
        productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
        testedComponentInfo: VariantComponentInfo<VariantBuilderT, VariantDslInfoT, VariantT>,
        componentType: ComponentType,
        testFixturesEnabled: Boolean,
        testBuilder: Any,
    ): TestComponentCreationConfig {

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        val testSourceSet = variantInputModel.defaultConfigData.getSourceSet(componentType)
        val variantDslInfoBuilder = getBuilder<CommonExtensionT, TestDslInfoT> (
                dimensionCombination,
                componentType,
                variantInputModel.defaultConfigData.defaultConfig,
                testSourceSet!!,
                buildTypeData.buildType,
                buildTypeData.getSourceSet(componentType),
                signingOverride,
                getLazyManifestParser(
                    testSourceSet.manifestFile,
                    componentType.requiresManifest
                ),
                variantPropertiesApiServices,
                extension = dslExtension,
                buildDirectory = project.layout.buildDirectory,
                dslServices = dslServices
        )
        variantDslInfoBuilder.productionVariant =
                testedComponentInfo.variantDslInfo as TestedVariantDslInfo

        val productFlavorList = (testedComponentInfo.variantDslInfo as MultiVariantComponentDslInfo).productFlavorList

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        val productFlavors = variantInputModel.productFlavors
        for (productFlavor in productFlavorList) {
            productFlavors[productFlavor.name]?.let {
            variantDslInfoBuilder.addProductFlavor(
                    it.productFlavor,
                    it.getSourceSet(componentType)!!)
            }
        }

        val testComponentDslInfo = when(testBuilder) {
            is HostTestBuilder -> variantDslInfoBuilder.createHostTestComponentDslInfo()
            is DeviceTestBuilder -> variantDslInfoBuilder.createAndroidTestComponentDslInfo()
            else -> throw RuntimeException("Unknown test builder instance ${testBuilder.javaClass.name}")
        }

        createCompoundSourceSets(productFlavorDataList, variantDslInfoBuilder)
        val variantSources = variantDslInfoBuilder.createVariantSources()

        // Add the container of dependencies, the order of the libraries is important.
        // In descending order: build type (only for unit test), flavors, defaultConfig.

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type (, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        val testVariantSourceSets: MutableList<DefaultAndroidSourceSet?> =
                Lists.newArrayListWithExpectedSize(4 + productFlavorList.size)

        // 1. add the variant-specific if applicable.
        if (productFlavorList.isNotEmpty()) {
            testVariantSourceSets.add(variantSources.variantSourceProvider)
        }

        // 2. the build type.
        val buildTypeConfigurationProvider = buildTypeData.getSourceSet(componentType)
        buildTypeConfigurationProvider?.let {
            testVariantSourceSets.add(it)
        }

        // 3. the multi-flavor combination
        if (productFlavorList.size > 1) {
            testVariantSourceSets.add(variantSources.multiFlavorSourceProvider)
        }

        // 4. the flavors.
        for (productFlavor in productFlavorList) {
            variantInputModel.productFlavors[productFlavor.name]?.let {
                testVariantSourceSets.add(it.getSourceSet(componentType))
            }
        }

        // now add the default config
        testVariantSourceSets.add(
                variantInputModel.defaultConfigData.getSourceSet(componentType))

        // If the variant being tested is a library variant, VariantDependencies must be
        // computed after the tasks for the tested variant is created.  Therefore, the
        // VariantDependencies is computed here instead of when the VariantData was created.
        val builder = VariantDependenciesBuilder.builder(
                project,
                dslServices.projectOptions,
                projectServices.issueReporter,
                testComponentDslInfo)
                .addSourceSets(testVariantSourceSets)
                .setFlavorSelection(getFlavorSelection(testComponentDslInfo))
                .setTestedVariant(testedComponentInfo.variant)
               .setTestFixturesEnabled(testFixturesEnabled)
        val variantDependencies = builder.build()
        val pathHelper =
            VariantPathHelper(
                project.layout.buildDirectory,
                testComponentDslInfo,
                dslServices
            )
        val componentIdentity = testComponentDslInfo.componentIdentity
        val artifacts = ArtifactsImpl(project, componentIdentity.name)
        val taskContainer = MutableTaskContainer()

        // create the internal storage for this variant.
        val testVariantData = TestVariantData(
            componentIdentity,
            artifacts,
            variantPropertiesApiServices,
            taskContainer
        )

        val testComponent = when(componentType) {
            // this is ANDROID_TEST
            ComponentTypeImpl.ANDROID_TEST ->
                variantFactory.createAndroidTest(
                    testComponentDslInfo.componentIdentity,
                    variantFactory.createAndroidTestBuildFeatureValues(
                        dslExtension.buildFeatures,
                        dslExtension.dataBinding,
                        projectServices,
                    ),
                    testComponentDslInfo as AndroidTestComponentDslInfo,
                    variantDependencies,
                    variantSources,
                    pathHelper,
                    artifacts,
                    testVariantData,
                    taskContainer,
                    testedComponentInfo.variant,
                    variantPropertiesApiServices,
                    taskCreationServices,
                    globalTaskCreationConfig,
                    testBuilder as DeviceTestBuilderImpl,
                )
            // this is UNIT_TEST
            ComponentTypeImpl.UNIT_TEST ->
                variantFactory.createUnitTest(
                    testComponentDslInfo.componentIdentity,
                    variantFactory.createHostTestBuildFeatureValues(
                        dslExtension.buildFeatures,
                        dslExtension.dataBinding,
                        projectServices,
                        globalTaskCreationConfig.unitTestOptions.isIncludeAndroidResources,
                        ComponentTypeImpl.UNIT_TEST
                    ),
                    testComponentDslInfo as HostTestComponentDslInfo,
                    variantDependencies,
                    variantSources,
                    pathHelper,
                    artifacts,
                    testVariantData,
                    taskContainer,
                    testedComponentInfo.variant,
                    variantPropertiesApiServices,
                    taskCreationServices,
                    globalTaskCreationConfig,
                    testBuilder as HostTestBuilderImpl,
                )
            ComponentTypeImpl.SCREENSHOT_TEST ->
                variantFactory.createHostTest(
                    testComponentDslInfo.componentIdentity,
                    variantFactory.createHostTestBuildFeatureValues(
                        dslExtension.buildFeatures,
                        dslExtension.dataBinding,
                        projectServices,
                        includeAndroidResources = true,
                        ComponentTypeImpl.SCREENSHOT_TEST
                    ),
                    testComponentDslInfo as HostTestComponentDslInfo,
                    variantDependencies,
                    variantSources,
                    pathHelper,
                    artifacts,
                    testVariantData,
                    taskContainer,
                    testedComponentInfo.variant,
                    variantPropertiesApiServices,
                    taskCreationServices,
                    globalTaskCreationConfig,
                    testBuilder as HostTestBuilderImpl,
                )
            else -> throw IllegalStateException("Expected a test component type, but ${componentIdentity.name} has type $componentType")
        }

        return testComponent
    }

    /**
     * Creates Variant objects for a specific [ComponentIdentity]
     *
     * Return the variant object finalization block.
     *
     * This will create both the prod and the androidTest/unitTest variants.
     */
    private fun createVariantsFromCombination(
        dimensionCombination: DimensionCombination,
        testBuildTypeData: BuildTypeData<BuildType>?,
        globalConfig: GlobalVariantBuilderConfig,
    ): () -> Unit {
        val componentType = variantFactory.componentType

        // first run the old variantFilter API
        // This acts on buildtype/flavor only, and applies in one pass to prod/tests.
        val defaultConfig = variantInputModel.defaultConfigData.defaultConfig
        val buildTypeData = variantInputModel.buildTypes[dimensionCombination.buildType]
        val buildType = buildTypeData!!.buildType

        // get the list of ProductFlavorData from the list of flavor name
        val productFlavorDataList: List<ProductFlavorData<ProductFlavor>> = dimensionCombination
                .productFlavors
                .mapNotNull { (_, second) -> variantInputModel.productFlavors[second] }
        val productFlavorList: List<ProductFlavor> = productFlavorDataList
                .map { it.productFlavor }
        var ignore = false
        oldExtension.variantFilter?.let {
            variantFilter.reset(
                    dimensionCombination, defaultConfig, buildType, componentType, productFlavorList)
            try {
                // variantFilterAction != null always true here.
                it.execute(variantFilter)
            } catch (t: Throwable) {
                throw ExternalApiUsageException(t)
            }
            ignore = variantFilter.ignore
        }

        if (ignore) {
            return { }
        }

        // create the prod variant
        val variantInfo = createVariant(
                dimensionCombination,
                buildTypeData,
                productFlavorDataList,
                componentType,
                globalConfig
        ) ?: return { }

        addVariant(variantInfo)
        val variant = variantInfo.variant
        val variantBuilder = variantInfo.variantBuilder
        val minSdkVersion = variant.minSdk
        val targetSdkVersion = when (variant) {
            is ApkCreationConfig -> variant.targetSdk
            is LibraryCreationConfig -> variant.targetSdk
            else -> minSdkVersion
        }
        if (buildTypeData.buildType.isDebuggable && buildTypeData.buildType.isMinifyEnabled) {
            val warningMsg = """BuildType '${buildType.name}' is both debuggable and has 'isMinifyEnabled' set to true.
            |All code optimizations and obfuscation are disabled for debuggable builds.
        """.trimMargin()
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                warningMsg
            )
        }
        if (minSdkVersion.apiLevel > targetSdkVersion.apiLevel) {
            projectServices
                .issueReporter
                .reportWarning(
                    IssueReporter.Type.GENERIC, String.format(
                        Locale.US,
                        "minSdkVersion (%d) is greater than targetSdkVersion"
                                + " (%d) for variant \"%s\". Please change the"
                                + " values such that minSdkVersion is less than or"
                                + " equal to targetSdkVersion.",
                        minSdkVersion.apiLevel,
                        targetSdkVersion.apiLevel,
                        variant.name))
        }

        val testFixturesEnabledForVariant =
            variantBuilder is HasTestFixturesBuilder &&
                    (variantBuilder as HasTestFixturesBuilder)
                    .enableTestFixtures

        if (testFixturesEnabledForVariant) {
            val testFixtures = createTestFixturesComponent(
                dimensionCombination,
                buildTypeData,
                productFlavorDataList,
                variantInfo
            )
            addTestFixturesComponent(testFixtures)
            (variant as HasTestFixtures).testFixtures = testFixtures as TestFixturesImpl
        }

        if (variantFactory.componentType.hasTestComponents) {
            (variantBuilder as? HasDeviceTestsBuilder)?.deviceTests?.values
                ?.filter { it.enable && buildTypeData == testBuildTypeData }
                ?.forEach { deviceTestBuilder ->
                    val deviceTest = createTestComponents<AndroidTestComponentDslInfo>(
                        dimensionCombination,
                        buildTypeData,
                        productFlavorDataList,
                        variantInfo,
                        ComponentTypeImpl.ANDROID_TEST,
                        testFixturesEnabledForVariant,
                        deviceTestBuilder,
                    )
                    addTestComponent(deviceTest)
                    (variant as HasDeviceTestsCreationConfig).addDeviceTest(
                        DeviceTestBuilder.ANDROID_TEST_TYPE,
                        deviceTest as DeviceTestImpl
                    )
            }

            (variantBuilder as? HasHostTestsBuilder)?.hostTests
                ?.filterValues { it.enable }
                ?.forEach { (_, hostTestBuilder) ->
                    val testComponent = createTestComponents<HostTestComponentDslInfo>(
                        dimensionCombination,
                        buildTypeData,
                        productFlavorDataList,
                        variantInfo,
                        (hostTestBuilder as HostTestBuilderImpl).componentType,
                        testFixturesEnabledForVariant,
                        hostTestBuilder
                    )
                    addTestComponent(testComponent)
                    (variant as HasHostTestsCreationConfig)
                        .addTestComponent(hostTestBuilder.type, testComponent as HostTestCreationConfig)
            }
        }

        // Now that unitTest and/or androidTest have been created and added to the main
        // user visible variant object, we can run the onVariants() actions
        val userVisibleVariant = variant.createUserVisibleVariantObject<Variant>(
            variantInfo.stats
        )

        // The variant object is created, let's create the user extension variant scoped objects
        // and store them in our newly created variant object.
        val variantExtensionConfig = object: VariantExtensionConfig<Variant> {
            override val variant: Variant
                get() = userVisibleVariant

            override fun <T> projectExtension(extensionType: Class<T>): T {
                // we need to make DefaultConfig or CommonExtension implement ExtensionAware.
                throw RuntimeException("No global extension DSL element implements ExtensionAware.")
            }

            override fun <T> buildTypeExtension(extensionType: Class<T>): T =
                buildTypeData.buildType.extensions.getByType(extensionType)

            override fun <T> productFlavorsExtensions(extensionType: Class<T>): List<T> =
                productFlavorDataList.map { productFlavorData ->
                    productFlavorData.productFlavor.extensions.getByType(extensionType)
                }
        }

        variantApiOperationsRegistrar.dslExtensions.forEach { registeredExtension ->
            registeredExtension.configurator.invoke(variantExtensionConfig).let {
                variantBuilder.registerExtension(
                    if (it is GeneratedSubclass) it.publicType() else it.javaClass,
                    it
                )
            }
        }

        // return the finalization block which will call the onVariants API for this variant
        // and finalize instances once all the public APIs have run.
        return {

            // call the onVariants API.
            variantApiOperationsRegistrar.variantOperations.executeOperations(userVisibleVariant)

            // all the variant public APIs have run, we can now safely fill the analytics with
            // the final values that will be used throughout the task creation and execution.
            val variantAnalytics = variantInfo.stats
            variantAnalytics?.let {
                it
                    .setIsDebug(buildType.isDebuggable)
                    .setMinSdkVersion(AnalyticsUtil.toProto(variant.minSdk))
                    .setMinifyEnabled(variant.optimizationCreationConfig.minifiedEnabled)
                    .setVariantType(variant.componentType.analyticsVariantType)
                    .setDexBuilder(GradleBuildVariant.DexBuilderTool.D8_DEXER)
                    .setDexMerger(GradleBuildVariant.DexMergerTool.D8_MERGER)
                    .setHasUnitTest(
                        (variant as? HasHostTests)?.hostTests
                            ?.containsKey(HostTestBuilder.UNIT_TEST_TYPE) ?: false
                    )
                    // TODO(karimai): Add tracking for ScreenshotTests
                    .setHasAndroidTest(
                        (variant as? HasDeviceTests)?.deviceTests?.isNotEmpty() ?: false
                    )
                    .setHasTestFixtures((variant as? HasTestFixtures)?.testFixtures != null)

                it.testExecution =
                    AnalyticsUtil.toProto(
                        dslExtension.testOptions.execution.toExecutionEnum()
                            ?: TestOptions.Execution.HOST
                    )

                if (variant is ApkCreationConfig) {
                    it.useLegacyMultidex = variant.dexing.dexingType.isLegacyMultiDex
                    it.coreLibraryDesugaringEnabled =
                        variant.dexing.isCoreLibraryDesugaringEnabled
                    it.useMultidex = variant.dexing.dexingType.isMultiDex

                    val supportType = variant.dexing.java8LangSupportType
                    if (supportType != Java8LangSupport.INVALID
                        && supportType != Java8LangSupport.UNUSED
                    ) {
                        variantAnalytics.java8LangSupport = AnalyticsUtil.toProto(supportType)
                    }
                    variantAnalytics.targetSdkVersion = AnalyticsUtil.toProto(
                        variant.targetSdk
                    )
                } else if (variant is LibraryCreationConfig) {
                    // Report the targetSdkVersion in libraries so that we can track the usage
                    // of the deprecated API.
                    variantAnalytics.targetSdkVersion = AnalyticsUtil.toProto(
                        variant.targetSdk
                    )
                }

                if (variant.optimizationCreationConfig.minifiedEnabled) {
                    // If code shrinker is used, it can only be R8
                    variantAnalytics.codeShrinker = GradleBuildVariant.CodeShrinkerTool.R8
                }
                variant.maxSdk?.let { version ->
                    variantAnalytics.setMaxSdkVersion(
                        ApiVersion.newBuilder().setApiLevel(version.toLong())
                    )
                }
            }
        }
    }

    private fun addVariant(variant: ComponentInfo<VariantBuilderT, VariantT>) {
        variants.add(variant)
    }

    private fun addTestComponent(testComponent: TestComponentCreationConfig) {
        nestedComponents.add(testComponent)
        testComponents.add(testComponent)
    }

    private fun addTestFixturesComponent(testFixturesComponent: TestFixturesCreationConfig) {
        nestedComponents.add(testFixturesComponent)
        testFixturesComponents.add(testFixturesComponent)
    }

    private fun getLazyManifestParser(
        file: File,
        isManifestFileRequired: Boolean
    ): LazyManifestParser {
        return lazyManifestParserMap.computeIfAbsent(
                file
        ) { f: File? ->
            LazyManifestParser(
                projectServices.objectFactory.fileProperty().fileValue(f),
                isManifestFileRequired,
                canParseManifest,
                projectServices,
            )
        }
    }

    private val canParseManifest = projectServices.objectFactory.property(Boolean::class.java).also {
        it.set(!dslServices.projectOptions[BooleanOption.DISABLE_EARLY_MANIFEST_PARSING])
    }

    fun setHasCreatedTasks(hasCreatedTasks: Boolean) {
        this.hasCreatedTasks = hasCreatedTasks
        canParseManifest.set(true)
    }

    fun lockVariantProperties() {
        variantPropertiesApiServices.lockProperties()
    }

    fun finalizeAllVariants() {
        finalizeAllComponents(
            variants.map { it.variant } + testComponents + testFixturesComponents
        )
    }

    init {
        signingOverride = createSigningOverride(dslServices)
        variantFilter = VariantFilter(ReadOnlyObjectProvider())
        variantBuilderServices = VariantBuilderServicesImpl(projectServices)
        variantPropertiesApiServices = VariantServicesImpl(
            projectServices,
            // detects whether we are running the plugin under unit test mode
            forUnitTesting = project.extensions.extraProperties.has("_agp_internal_test_mode_"),
        )
        taskCreationServices = TaskCreationServicesImpl(projectServices)
    }

    companion object {
        fun finalizeAllComponents(components: List<ComponentCreationConfig>) {
            components.forEach { component ->
                component.finalizeAndLock()
                ArtifactMetadataProcessor.wireAllFinalizedBy(component)
            }
        }
    }
}
