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
import com.android.build.api.component.ComponentIdentity
import com.android.build.api.component.UnitTest
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.TestedExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.HasTestFixturesBuilder
import com.android.build.api.variant.TestFixtures
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.api.variant.impl.HasAndroidTest
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.TestedAndroidConfig
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider
import com.android.build.gradle.internal.api.VariantFilter
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantDslInfoBuilder
import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.computeSourceSetName
import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.getBuilder
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.gradle.internal.crash.ExternalApiUsageException
import com.android.build.gradle.internal.dependency.VariantDependenciesBuilder
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.manifest.LazyManifestParser
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.scope.VariantScopeImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantApiServices
import com.android.build.gradle.internal.services.VariantApiServicesImpl
import com.android.build.gradle.internal.services.VariantPropertiesApiServicesImpl
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.DimensionCombination
import com.android.build.gradle.internal.variant.DimensionCombinator
import com.android.build.gradle.internal.variant.TestFixturesVariantData
import com.android.build.gradle.internal.variant.TestVariantData
import com.android.build.gradle.internal.variant.TestedVariantData
import com.android.build.gradle.internal.variant.VariantComponentInfo
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SigningOptions
import com.android.builder.core.AbstractProductFlavor.DimensionRequest
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.builder.dexing.isLegacyMultiDexMode
import com.android.builder.errors.IssueReporter
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.ApiVersion
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.GeneratedSubclass
import java.io.File
import java.util.Locale
import java.util.function.BooleanSupplier
import java.util.stream.Collectors

/** Class to create, manage variants.  */
@Suppress("UnstableApiUsage")
class VariantManager<
        CommonExtensionT: CommonExtension<*, *, *, *>,
        AndroidComponentsT: AndroidComponentsExtension<
                out CommonExtension<*, *, *, *>,
                out VariantBuilder,
                out Variant>,
        VariantBuilderT : VariantBuilderImpl,
        VariantT : VariantImpl>(
        private val globalScope: GlobalScope,
        private val project: Project,
        private val projectOptions: ProjectOptions,
        private val dslExtension: CommonExtensionT,
        private val androidComponentsExtension: AndroidComponentsT,
        private val variantApiOperationsRegistrar: VariantApiOperationsRegistrar<
                CommonExtension<*, *, *, *>,
                VariantBuilder,
                Variant,
                >,
        private val variantFactory: VariantFactory<VariantBuilderT, VariantT>,
        private val variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        private val projectServices: ProjectServices) {

    private val variantApiServices: VariantApiServices
    private val variantPropertiesApiServices: VariantPropertiesApiServicesImpl
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
     * Returns a list of all test components.
     *
     * @see .createVariants
     */
    val testComponents: MutableList<TestComponentImpl> =
            Lists.newArrayList()

    /**
     * Returns a list of all test fixtures components.
     */
    val testFixturesComponents: MutableList<TestFixturesImpl> = Lists.newArrayList()

    /**
     * Creates the variants.
     *
     * @param buildFeatureValues the build feature value instance
     * @param dslNamespace the namespace from the android extension DSL
     * @param dslTestNamespace the testNamespace from the android extension DSL
     */
    fun createVariants(
        buildFeatureValues: BuildFeatureValues,
    ) {
        variantFactory.validateModel(variantInputModel)
        variantFactory.preVariantWork(project)
        computeVariants(buildFeatureValues)
    }

    @Deprecated("Do not use. Use dslExtension instead")
    private val extension: BaseExtension
        get() = dslExtension as BaseExtension

    private fun getFlavorSelection(
            variantDslInfo: VariantDslInfo<*>): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
        val factory = project.objects
        return variantDslInfo.missingDimensionStrategies.entries.stream()
                .collect(
                        Collectors.toMap(
                                { entry: Map.Entry<String, DimensionRequest> ->
                                    Attribute.of(entry.key,
                                            ProductFlavorAttr::class.java)
                                }
                        ) { entry: Map.Entry<String, DimensionRequest> ->
                            factory.named(
                                    ProductFlavorAttr::class.java,
                                    entry.value.requested)
                        })
    }

    /**
     * Create all variants.
     *
     * @param buildFeatureValues the build feature value instance
     */
    private fun computeVariants(
        buildFeatureValues: BuildFeatureValues,
    ) {
        val flavorDimensionList: List<String> = dslExtension.flavorDimensions
        val computer = DimensionCombinator(
                variantInputModel,
                projectServices.issueReporter,
                flavorDimensionList)
        val variants = computer.computeVariants()

        // get some info related to testing
        val testBuildTypeData = testBuildTypeData

        // figure out whether there are inconsistency in the appId of the flavors
        val inconsistentTestAppId = checkInconsistentTestAppId(
            variantInputModel.productFlavors.values.map { it.productFlavor }
        )

        // loop on all the new variant objects to create the legacy ones.
        for (variant in variants) {
            createVariantsFromCombination(
                    variant,
                    testBuildTypeData,
                    buildFeatureValues,
                    inconsistentTestAppId
            )
        }

        // FIXME we should lock the variant API properties after all the beforeVariants, and
        // before any onVariants to avoid cross access between the two.
        // This means changing the way to run beforeVariants vs onVariants.
        variantApiServices.lockValues()
    }

    private val testBuildTypeData: BuildTypeData<BuildType>?
        get() {
            var testBuildTypeData: BuildTypeData<BuildType>? = null
            if (extension is TestedAndroidConfig) {
                val testedExtension = extension as TestedAndroidConfig
                testBuildTypeData = variantInputModel.buildTypes[testedExtension.testBuildType]
                if (testBuildTypeData == null) {
                    throw RuntimeException(String.format(
                            "Test Build Type '%1\$s' does not" + " exist.",
                            testedExtension.testBuildType))
                }
            }
            return testBuildTypeData
        }

    enum class NativeBuiltType { CMAKE, NDK_BUILD }

    fun configuredNativeBuilder(): NativeBuiltType? {
        if (extension.externalNativeBuild.ndkBuild.path != null) return NativeBuiltType.NDK_BUILD
        if (extension.externalNativeBuild.cmake.path != null) return NativeBuiltType.CMAKE
        return null;
    }

    private fun createVariant(
            dimensionCombination: DimensionCombination,
            buildTypeData: BuildTypeData<BuildType>,
            productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
            variantType: VariantType,
            buildFeatureValues: BuildFeatureValues,
    ): VariantComponentInfo<VariantBuilderT, VariantT>? {
        // entry point for a given buildType/Flavors/VariantType combo.
        // Need to run the new variant API to selectively ignore variants.
        // in order to do this, we need access to the VariantDslInfo, to create a
        @Suppress("DEPRECATION") val dslServices = globalScope.dslServices
        val defaultConfig = variantInputModel.defaultConfigData
        val defaultConfigSourceProvider = defaultConfig.sourceSet
        val variantDslInfoBuilder = getBuilder<CommonExtensionT>(
                dimensionCombination,
                variantType,
                defaultConfig.defaultConfig,
                defaultConfigSourceProvider,
                buildTypeData.buildType,
                buildTypeData.sourceSet,
                signingOverride,
                getLazyManifestParser(
                        defaultConfigSourceProvider.manifestFile,
                        variantType.requiresManifest) { canParseManifest() },
                dslServices,
                variantPropertiesApiServices,
                configuredNativeBuilder(),
                dslExtension,
                hasDynamicFeatures = globalScope.hasDynamicFeatures(),
                dslExtension.experimentalProperties,
                enableTestFixtures = dslExtension is TestedExtension &&
                        (dslExtension as TestedExtension).testFixtures.enable,
        )

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (productFlavorData in productFlavorDataList) {
            variantDslInfoBuilder.addProductFlavor(
                    productFlavorData.productFlavor, productFlavorData.sourceSet)
        }
        val variantDslInfo = variantDslInfoBuilder.createVariantDslInfo(
                project.layout.buildDirectory)
        val componentIdentity = variantDslInfo.componentIdentity

        // create the Variant object so that we can run the action which may interrupt the creation
        // (in case of enabled = false)
        val variantBuilder = variantFactory.createVariantBuilder(
                componentIdentity, variantDslInfo, variantApiServices)

        // now that we have the variant, create the analytics object,
        val configuratorService = getBuildService(
                project.gradle.sharedServices,
                AnalyticsConfiguratorService::class.java)
                .get()
        val profileEnabledVariantBuilder = configuratorService.getVariantBuilder(
                project.path, variantBuilder.name)

        val userVisibleVariantBuilder =
                variantBuilder.createUserVisibleVariantObject<VariantBuilder>(
                        projectServices,
                        profileEnabledVariantBuilder,
                )

        // execute the Variant API
        variantApiOperationsRegistrar.variantBuilderOperations.executeOperations(userVisibleVariantBuilder)
        if (!variantBuilder.enabled) {
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
                projectOptions,
                projectServices.issueReporter,
                variantDslInfo)
                .setFlavorSelection(getFlavorSelection(variantDslInfo))
                .addSourceSets(variantSourceSets)
        if (extension is BaseAppModuleExtension) {
            builder.setFeatureList((extension as BaseAppModuleExtension).dynamicFeatures)
        }

        val variantDependencies = builder.build()

        // Done. Create the (too) many variant objects
        val pathHelper =
            VariantPathHelper(project.layout.buildDirectory, variantDslInfo, dslServices)
        val artifacts = ArtifactsImpl(project, componentIdentity.name)
        val taskContainer = MutableTaskContainer()
        val transformManager = TransformManager(project, dslServices.issueReporter)

        // create the obsolete VariantScope
        val variantScope = VariantScopeImpl(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                pathHelper,
                artifacts,
                taskCreationServices,
                globalScope,
                null /* testedVariantProperties*/)

        // and the obsolete variant data
        val variantData = variantFactory.createVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                pathHelper,
                artifacts,
                variantPropertiesApiServices,
                globalScope,
                taskContainer)

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
                variantScope,
                variantData,
                transformManager,
                variantPropertiesApiServices,
                taskCreationServices,
                androidComponentsExtension)

        return VariantComponentInfo(
                variantBuilder,
                variantApiObject,
                profileEnabledVariantBuilder,
                variantApiOperationsRegistrar)
    }

    private fun createCompoundSourceSets(
            productFlavorList: List<ProductFlavorData<ProductFlavor>>,
            variantDslInfoBuilder: VariantDslInfoBuilder<CommonExtensionT>) {
        val variantType = variantDslInfoBuilder.variantType
        if (productFlavorList.isNotEmpty() /* && !variantConfig.getType().isSingleBuildType()*/) {
            val variantSourceSet = variantInputModel
                    .sourceSetManager
                    .setUpSourceSet(
                            computeSourceSetName(variantDslInfoBuilder.name, variantType),
                            variantType.isTestComponent) as DefaultAndroidSourceSet
            variantDslInfoBuilder.variantSourceProvider = variantSourceSet
        }
        if (productFlavorList.size > 1) {
            val multiFlavorSourceSet = variantInputModel
                    .sourceSetManager
                    .setUpSourceSet(
                            computeSourceSetName(variantDslInfoBuilder.flavorName,
                                                    variantType),
                            variantType.isTestComponent) as DefaultAndroidSourceSet
            variantDslInfoBuilder.multiFlavorSourceProvider = multiFlavorSourceSet
        }
    }

    /** Create a test fixtures component for the specified main component.  */
    private fun createTestFixturesComponent(
        dimensionCombination: DimensionCombination,
        buildTypeData: BuildTypeData<BuildType>,
        productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
        mainComponentInfo: VariantComponentInfo<VariantBuilderT, VariantT>
    ): TestFixturesImpl {
        val testFixturesVariantType = VariantTypeImpl.TEST_FIXTURES
        val testFixturesSourceSet = variantInputModel.defaultConfigData.testFixturesSourceSet!!
        @Suppress("DEPRECATION") val dslServices = globalScope.dslServices
        val variantDslInfoBuilder = getBuilder(
            dimensionCombination,
            testFixturesVariantType,
            variantInputModel.defaultConfigData.defaultConfig,
            testFixturesSourceSet,
            buildTypeData.buildType,
            buildTypeData.testFixturesSourceSet,
            signingOverride,
            getLazyManifestParser(
                testFixturesSourceSet.manifestFile,
                testFixturesVariantType.requiresManifest) { canParseManifest() },
            dslServices,
            variantPropertiesApiServices,
            extension = dslExtension,
            hasDynamicFeatures = globalScope.hasDynamicFeatures(),
            testFixtureMainVariantName = mainComponentInfo.variant.name
        )

        variantDslInfoBuilder.productionVariant = mainComponentInfo.variant.variantDslInfo as VariantDslInfoImpl<*>

        val productFlavorList = mainComponentInfo.variant.variantDslInfo.productFlavorList

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        val productFlavors = variantInputModel.productFlavors
        for (productFlavor in productFlavorList) {
            productFlavors[productFlavor.name]?.let {
                variantDslInfoBuilder.addProductFlavor(
                    it.productFlavor,
                    it.testFixturesSourceSet!!
                )
            }
        }
        val variantDslInfo = variantDslInfoBuilder.createVariantDslInfo(
            project.layout.buildDirectory
        )
        val apiAccessStats = mainComponentInfo.stats

        // todo: run actions registered at the extension level.
//        mainComponentInfo.variantApiOperationsRegistrar.testFixturesBuilderOperations
//            .executeOperations(testFixturesVariantBuilder)

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
        val testFixturesProductFlavors = variantDslInfo.productFlavorList
        val testFixturesVariantSourceSets: MutableList<DefaultAndroidSourceSet?> =
            Lists.newArrayListWithExpectedSize(4 + testFixturesProductFlavors.size)

        // 1. add the variant-specific if applicable.
        if (testFixturesProductFlavors.isNotEmpty()) {
            testFixturesVariantSourceSets.add(variantSources.variantSourceProvider)
        }

        // 2. the build type.
        val buildTypeConfigurationProvider = buildTypeData.testFixturesSourceSet
        buildTypeConfigurationProvider?.let {
            testFixturesVariantSourceSets.add(it)
        }

        // 3. the multi-flavor combination
        if (testFixturesProductFlavors.size > 1) {
            testFixturesVariantSourceSets.add(variantSources.multiFlavorSourceProvider)
        }

        // 4. the flavors.
        for (productFlavor in testFixturesProductFlavors) {
            variantInputModel.productFlavors[productFlavor.name]?.let {
                testFixturesVariantSourceSets.add(it.testFixturesSourceSet)
            }
        }

        // now add the default config
        testFixturesVariantSourceSets.add(variantInputModel.defaultConfigData.testFixturesSourceSet)

        // If the variant being tested is a library variant, VariantDependencies must be
        // computed after the tasks for the tested variant is created.  Therefore, the
        // VariantDependencies is computed here instead of when the VariantData was created.
        val variantDependencies = VariantDependenciesBuilder.builder(
            project,
            projectOptions,
            projectServices.issueReporter,
            variantDslInfo
        )
            .addSourceSets(testFixturesVariantSourceSets)
            .setFlavorSelection(getFlavorSelection(variantDslInfo))
            .overrideVariantNameAttribute(mainComponentInfo.variant.name)
            .build()
        val pathHelper =
            VariantPathHelper(project.layout.buildDirectory, variantDslInfo, dslServices)
        val componentIdentity = variantDslInfo.componentIdentity
        val artifacts = ArtifactsImpl(project, componentIdentity.name)
        val taskContainer = MutableTaskContainer()
        val transformManager = TransformManager(project, dslServices.issueReporter)
        val variantScope = VariantScopeImpl(
            componentIdentity,
            variantDslInfo,
            variantDependencies,
            pathHelper,
            artifacts,
            taskCreationServices,
            globalScope,
            null
        )

        // create the internal storage for this variant.
        val testFixturesVariantData = TestFixturesVariantData(
            componentIdentity,
            variantDslInfo,
            variantDependencies,
            variantSources,
            pathHelper,
            artifacts,
            variantPropertiesApiServices,
            globalScope,
            taskContainer
        )
        val buildFeatureValues = variantFactory.createTestFixturesBuildFeatureValues(
            extension.buildFeatures,
            projectOptions
        )

        val testFixturesComponent = variantFactory.createTestFixtures(
            variantDslInfo.componentIdentity,
            buildFeatureValues,
            variantDslInfo,
            variantDependencies,
            variantSources,
            pathHelper,
            artifacts,
            variantScope,
            testFixturesVariantData,
            mainComponentInfo.variant,
            transformManager,
            variantPropertiesApiServices,
            taskCreationServices,
            androidComponentsExtension
        )

        val userVisibleVariant =
            testFixturesComponent.createUserVisibleVariantObject<TestFixtures>(
                projectServices, variantApiOperationsRegistrar, apiAccessStats)
        // todo: execute the actions registered at the extension level.
//        mainComponentInfo.variantApiOperationsRegistrar.testFixturesOperations
//            .executeOperations(userVisibleVariant)

        // register testFixtures component to the main variant
        mainComponentInfo
            .variant
            .testFixturesComponent = testFixturesComponent

        return testFixturesComponent
    }

    /** Create a TestVariantData for the specified testedVariantData.  */
    fun createTestComponents(
        dimensionCombination: DimensionCombination,
        buildTypeData: BuildTypeData<BuildType>,
        productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
        testedComponentInfo: VariantComponentInfo<VariantBuilderT, VariantT>,
        variantType: VariantType,
        testFixturesEnabled: Boolean,
        inconsistentTestAppId: Boolean
    ): TestComponentImpl? {

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        val testSourceSet = variantInputModel.defaultConfigData.getTestSourceSet(variantType)
        @Suppress("DEPRECATION") val dslServices = globalScope.dslServices
        val variantDslInfoBuilder = getBuilder<CommonExtensionT>(
                dimensionCombination,
                variantType,
                variantInputModel.defaultConfigData.defaultConfig,
                testSourceSet!!,
                buildTypeData.buildType,
                buildTypeData.getTestSourceSet(variantType),
                signingOverride,
                getLazyManifestParser(
                        testSourceSet.manifestFile,
                        variantType.requiresManifest) { canParseManifest() },
                dslServices,
                variantPropertiesApiServices,
                extension = dslExtension,
                hasDynamicFeatures = globalScope.hasDynamicFeatures())
        variantDslInfoBuilder.productionVariant =
                testedComponentInfo.variant.variantDslInfo as VariantDslInfoImpl<*>
        variantDslInfoBuilder.inconsistentTestAppId = inconsistentTestAppId

        val productFlavorList = testedComponentInfo.variant.variantDslInfo.productFlavorList

        // We must first add the flavors to the variant builder, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        val productFlavors = variantInputModel.productFlavors
        for (productFlavor in productFlavorList) {
            productFlavors[productFlavor.name]?.let {
            variantDslInfoBuilder.addProductFlavor(
                    it.productFlavor,
                    it.getTestSourceSet(variantType)!!)
            }
        }
        val variantDslInfo = variantDslInfoBuilder.createVariantDslInfo(
                project.layout.buildDirectory)
        val apiAccessStats = testedComponentInfo.stats
        if (variantType.isApk
            && testedComponentInfo.variantBuilder is HasAndroidTestBuilder) {
            // this is ANDROID_TEST
            if (!testedComponentInfo.variantBuilder.enableAndroidTest) {
                return null
            }
        } else {
            // this is UNIT_TEST
            if (!testedComponentInfo.variantBuilder.enableUnitTest) {
                return null
            }
        }

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
        val testProductFlavors = variantDslInfo.productFlavorList
        val testVariantSourceSets: MutableList<DefaultAndroidSourceSet?> =
                Lists.newArrayListWithExpectedSize(4 + testProductFlavors.size)

        // 1. add the variant-specific if applicable.
        if (testProductFlavors.isNotEmpty()) {
            testVariantSourceSets.add(variantSources.variantSourceProvider)
        }

        // 2. the build type.
        val buildTypeConfigurationProvider = buildTypeData.getTestSourceSet(variantType)
        buildTypeConfigurationProvider?.let {
            testVariantSourceSets.add(it)
        }

        // 3. the multi-flavor combination
        if (testProductFlavors.size > 1) {
            testVariantSourceSets.add(variantSources.multiFlavorSourceProvider)
        }

        // 4. the flavors.
        for (productFlavor in testProductFlavors) {
            variantInputModel.productFlavors[productFlavor.name]?.let {
                testVariantSourceSets.add(it.getTestSourceSet(variantType))
            }
        }

        // now add the default config
        testVariantSourceSets.add(
                variantInputModel.defaultConfigData.getTestSourceSet(variantType))

        // If the variant being tested is a library variant, VariantDependencies must be
        // computed after the tasks for the tested variant is created.  Therefore, the
        // VariantDependencies is computed here instead of when the VariantData was created.
        val builder = VariantDependenciesBuilder.builder(
                project,
                projectOptions,
                projectServices.issueReporter,
                variantDslInfo)
                .addSourceSets(testVariantSourceSets)
                .setFlavorSelection(getFlavorSelection(variantDslInfo))
                .setTestedVariant(testedComponentInfo.variant)
               .setTestFixturesEnabled(testFixturesEnabled)
        val variantDependencies = builder.build()
        val pathHelper =
            VariantPathHelper(project.layout.buildDirectory, variantDslInfo, dslServices)
        val componentIdentity = variantDslInfo.componentIdentity
        val artifacts = ArtifactsImpl(project, componentIdentity.name)
        val taskContainer = MutableTaskContainer()
        val transformManager = TransformManager(project, dslServices.issueReporter)
        val variantScope = VariantScopeImpl(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                pathHelper,
                artifacts,
                taskCreationServices,
                globalScope,
                testedComponentInfo.variant)

        // create the internal storage for this variant.
        val testVariantData = TestVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                pathHelper,
                artifacts,
                (testedComponentInfo.variant.variantData as TestedVariantData),
                variantPropertiesApiServices,
                globalScope,
                taskContainer)
        val testComponent: TestComponentImpl
        val buildFeatureValues = variantFactory.createTestBuildFeatureValues(
                extension.buildFeatures, extension.dataBinding, projectOptions)

        // this is ANDROID_TEST
        testComponent = if (variantType.isApk) {
            val androidTest = variantFactory.createAndroidTest(
                    variantDslInfo.componentIdentity,
                    buildFeatureValues,
                    variantDslInfo,
                    variantDependencies,
                    variantSources,
                    pathHelper,
                    artifacts,
                    variantScope,
                    testVariantData,
                    testedComponentInfo.variant,
                    transformManager,
                    variantPropertiesApiServices,
                    taskCreationServices,
                    androidComponentsExtension)
            androidTest
        } else {
            // this is UNIT_TEST
            val unitTest = variantFactory.createUnitTest(
                    variantDslInfo.componentIdentity,
                    buildFeatureValues,
                    variantDslInfo,
                    variantDependencies,
                    variantSources,
                    pathHelper,
                    artifacts,
                    variantScope,
                    testVariantData,
                    testedComponentInfo.variant,
                    transformManager,
                    variantPropertiesApiServices,
                    taskCreationServices,
                    androidComponentsExtension)
            unitTest
        }

        // register
        testedComponentInfo
                .variant
                .testComponents[variantDslInfo.variantType] = testComponent
        return testComponent
    }

    /**
     * Creates Variant objects for a specific [ComponentIdentity]
     *
     *
     * This will create both the prod and the androidTest/unitTest variants.
     */
    private fun createVariantsFromCombination(
        dimensionCombination: DimensionCombination,
        testBuildTypeData: BuildTypeData<BuildType>?,
        buildFeatureValues: BuildFeatureValues,
        inconsistentTestAppId: Boolean,
    ) {
        val variantType = variantFactory.variantType

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
        extension.variantFilter?.let {
            variantFilter.reset(
                    dimensionCombination, defaultConfig, buildType, variantType, productFlavorList)
            try {
                // variantFilterAction != null always true here.
                it.execute(variantFilter)
            } catch (t: Throwable) {
                throw ExternalApiUsageException(t)
            }
            ignore = variantFilter.ignore
        }
        if (!ignore) {
            // create the prod variant
            createVariant(
                    dimensionCombination,
                    buildTypeData,
                    productFlavorDataList,
                    variantType,
                    buildFeatureValues
            )?.let { variantInfo ->
                addVariant(variantInfo)
                val variant = variantInfo.variant
                val variantBuilder = variantInfo.variantBuilder
                val minSdkVersion = variant.minSdkVersion
                val targetSdkVersion = variant.targetSdkVersion
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
                    testFixturesComponents.add(testFixtures)
                    (variant as HasTestFixtures).testFixtures = testFixtures
                }

                if (variantFactory.variantType.hasTestComponents) {
                    if (buildTypeData == testBuildTypeData) {
                        val androidTest = createTestComponents(
                                dimensionCombination,
                                buildTypeData,
                                productFlavorDataList,
                                variantInfo,
                                VariantTypeImpl.ANDROID_TEST,
                                testFixturesEnabledForVariant,
                                inconsistentTestAppId
                        )
                        androidTest?.let {
                            addTestComponent(it)
                            (variant as HasAndroidTest).androidTest =
                                it as com.android.build.api.component.AndroidTest
                        }
                    }
                    val unitTest = createTestComponents(
                        dimensionCombination,
                        buildTypeData,
                        productFlavorDataList,
                        variantInfo,
                        VariantTypeImpl.UNIT_TEST,
                        testFixturesEnabledForVariant,
                        false
                    )
                    unitTest?.let {
                        addTestComponent(it)
                        variant.unitTest = it as UnitTest
                    }
                }

                // Now that unitTest and/or androidTest have been created and added to the main
                // user visible variant object, we can run the onVariants() actions
                val userVisibleVariant = (variant as VariantImpl)
                    .createUserVisibleVariantObject<Variant>(projectServices,
                        variantApiOperationsRegistrar,
                        variantInfo.stats)

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
                        variantBuilder.registerExtension<Any>(
                            if (it is GeneratedSubclass) it.publicType() else it.javaClass,
                            it
                        )
                    }
                }
                variantApiOperationsRegistrar.variantOperations.executeOperations(userVisibleVariant)

                // all the variant public APIs have run, we can now safely fill the analytics with
                // the final values that will be used throughout the task creation and execution.
                val variantAnalytics = variantInfo.stats
                variantAnalytics?.let {
                    it
                        .setIsDebug(buildType.isDebuggable)
                        .setMinSdkVersion(AnalyticsUtil.toProto(minSdkVersion))
                        .setMinifyEnabled(variant.minifiedEnabled)
                        .setUseMultidex(variant.isMultiDexEnabled)
                        .setUseLegacyMultidex(variant.dexingType.isLegacyMultiDexMode())
                        .setVariantType(variant.variantType.analyticsVariantType)
                        .setDexBuilder(GradleBuildVariant.DexBuilderTool.D8_DEXER)
                        .setDexMerger(GradleBuildVariant.DexMergerTool.D8_MERGER)
                        .setCoreLibraryDesugaringEnabled(variant.isCoreLibraryDesugaringEnabled)
                        .testExecution = AnalyticsUtil.toProto(globalScope.extension.testOptions.getExecutionEnum())

                    if (variant.minifiedEnabled) {
                        // If code shrinker is used, it can only be R8
                        variantAnalytics.codeShrinker = GradleBuildVariant.CodeShrinkerTool.R8
                    }
                    variantAnalytics.targetSdkVersion = AnalyticsUtil.toProto(targetSdkVersion)
                    variant.maxSdkVersion?.let { version ->
                        variantAnalytics.setMaxSdkVersion(
                            ApiVersion.newBuilder().setApiLevel(version.toLong()))
                    }
                    val supportType = variant.getJava8LangSupportType()
                    if (supportType != VariantScope.Java8LangSupport.INVALID
                        && supportType != VariantScope.Java8LangSupport.UNUSED) {
                        variantAnalytics.java8LangSupport = AnalyticsUtil.toProto(supportType)
                    }
                }
            }
        }
    }

    private fun addVariant(variant: ComponentInfo<VariantBuilderT, VariantT>) {
        variants.add(variant)
    }

    private fun addTestComponent(
            testComponent: TestComponentImpl) {
        testComponents.add(testComponent)
    }

    private fun createSigningOverride(): SigningConfig? {
        SigningOptions.readSigningOptions(projectOptions)?.let { signingOptions ->
            val signingConfigDsl = globalScope.dslServices.newDecoratedInstance(SigningConfig::class.java, SigningOptions.SIGNING_CONFIG_NAME, globalScope.dslServices)
            signingConfigDsl.storeFile(File(signingOptions.storeFile))
            signingConfigDsl.storePassword(signingOptions.storePassword)
            signingConfigDsl.keyAlias(signingOptions.keyAlias)
            signingConfigDsl.keyPassword(signingOptions.keyPassword)
            signingOptions.storeType?.let {
                signingConfigDsl.storeType(it)
            }
            signingOptions.v1Enabled?.let {
                signingConfigDsl.enableV1Signing = it
            }
            signingOptions.v2Enabled?.let {
                signingConfigDsl.enableV2Signing = it
            }
            return signingConfigDsl
        }
        return null
    }

    private fun getLazyManifestParser(
            file: File,
            isManifestFileRequired: Boolean,
            isInExecutionPhase: BooleanSupplier): LazyManifestParser {
        return lazyManifestParserMap.computeIfAbsent(
                file
        ) { f: File? ->
            LazyManifestParser(
                    projectServices.objectFactory.fileProperty().fileValue(f),
                    isManifestFileRequired,
                    projectServices,
                    isInExecutionPhase)
        }
    }

    private fun canParseManifest(): Boolean {
        return hasCreatedTasks || !projectOptions[BooleanOption.DISABLE_EARLY_MANIFEST_PARSING]
    }

    fun setHasCreatedTasks(hasCreatedTasks: Boolean) {
        this.hasCreatedTasks = hasCreatedTasks
    }

    fun lockVariantProperties() {
        variantPropertiesApiServices.lockProperties()
    }

    companion object {

        /**
         * Returns a modified name.
         *
         *
         * This name is used to request a missing dimension. It is the same name as the flavor that
         * sets up the request, which means it's not going to be matched, and instead it'll go to a
         * custom fallbacks provided by the flavor.
         *
         *
         * We are just modifying the name to avoid collision in case the same name exists in
         * different dimensions
         */
        fun getModifiedName(name: String): String {
            return "____$name"
        }

        internal fun checkInconsistentTestAppId(
            flavors: List<ProductFlavor>
        ): Boolean {
            if (flavors.isEmpty()) {
                return false
            }

            // as soon as one flavor declares an ID or a suffix, we bail.
            // There are possible corner cases where a project could have 2 flavors setting the same
            // appId in which case it would be safe to keep the current behavior but this is
            // unlikely to be a common case.
            for (flavor in flavors) {
                if (flavor.applicationId != null || flavor.applicationIdSuffix != null) {
                    return true
                }
            }

            return false
        }
    }

    init {
        signingOverride = createSigningOverride()
        variantFilter = VariantFilter(ReadOnlyObjectProvider())
        variantApiServices = VariantApiServicesImpl(projectServices)
        variantPropertiesApiServices = VariantPropertiesApiServicesImpl(projectServices)
        taskCreationServices =
                TaskCreationServicesImpl(variantPropertiesApiServices, projectServices)
    }
}
