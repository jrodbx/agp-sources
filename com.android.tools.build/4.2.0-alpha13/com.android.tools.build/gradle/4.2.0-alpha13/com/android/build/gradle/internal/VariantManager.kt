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

import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.getBuilder
import com.android.build.gradle.internal.core.VariantDslInfoBuilder.Companion.computeSourceSetName
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.gradle.options.ProjectOptions
import com.android.build.api.extension.impl.OperationsRegistrar
import com.android.build.gradle.internal.manifest.LazyManifestParser
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.builder.core.AbstractProductFlavor.DimensionRequest
import com.android.build.gradle.TestedAndroidConfig
import java.lang.RuntimeException
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.core.VariantDslInfoBuilder
import com.android.build.gradle.internal.core.VariantDslInfoImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.dependency.VariantDependenciesBuilder
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.crash.ExternalApiUsageException
import com.android.builder.errors.IssueReporter
import java.util.Locale
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.builder.core.VariantTypeImpl
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider
import com.android.build.gradle.internal.api.VariantFilter
import com.android.build.gradle.internal.dsl.*
import com.android.build.gradle.internal.scope.*
import com.android.build.gradle.internal.services.*
import com.android.build.gradle.internal.variant.*
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SigningOptions
import com.android.builder.core.VariantType
import com.android.builder.dexing.isLegacyMultiDexMode
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.wireless.android.sdk.stats.ApiVersion
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import java.io.File
import java.util.function.BooleanSupplier
import java.util.stream.Collectors

/** Class to create, manage variants.  */
@Suppress("UnstableApiUsage")
class VariantManager<VariantBuilderT : VariantBuilderImpl, VariantT : VariantImpl>(
        private val globalScope: GlobalScope,
        private val project: Project,
        private val projectOptions: ProjectOptions,
        private val extension: BaseExtension,

    private val variantBuilderOperationsRegistrar: OperationsRegistrar<VariantBuilder>,
        private val variantOperationsRegistrar: OperationsRegistrar<Variant>,
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
    val testComponents: MutableList<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> =
            Lists.newArrayList()

    /**
     * Creates the variants.
     *
     * @param buildFeatureValues the build feature value instance
     */
    fun createVariants(buildFeatureValues: BuildFeatureValues) {
        variantFactory.validateModel(variantInputModel)
        variantFactory.preVariantWork(project)
        computeVariants(buildFeatureValues)
    }

    private fun getFlavorSelection(
            variantDslInfo: VariantDslInfo): Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> {
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
    private fun computeVariants(buildFeatureValues: BuildFeatureValues) {
        val flavorDimensionList: List<String> = extension.flavorDimensionList
        val computer = DimensionCombinator(
                variantInputModel,
                projectServices.issueReporter,
                flavorDimensionList)
        val variants = computer.computeVariants()

        // get some info related to testing
        val testBuildTypeData = testBuildTypeData

        // loop on all the new variant objects to create the legacy ones.
        for (variant in variants) {
            createVariantsFromCombination(variant, testBuildTypeData, buildFeatureValues)
        }

        // FIXME we should lock the variant API properties after all the onVariants, and
        // before any onVariantProperties to avoid cross access between the two.
        // This means changing the way to run onVariants vs onVariantProperties.
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

    private fun createVariant(
            dimensionCombination: DimensionCombination,
            buildTypeData: BuildTypeData<BuildType>,
            productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
            variantType: VariantType,
            buildFeatureValues: BuildFeatureValues): ComponentInfo<VariantBuilderT, VariantT>? {
        // entry point for a given buildType/Flavors/VariantType combo.
        // Need to run the new variant API to selectively ignore variants.
        // in order to do this, we need access to the VariantDslInfo, to create a
        @Suppress("DEPRECATION") val dslServices = globalScope.dslServices
        val defaultConfig = variantInputModel.defaultConfigData
        val defaultConfigSourceProvider = defaultConfig.sourceSet
        val variantDslInfoBuilder = getBuilder(
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
                variantPropertiesApiServices)

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

        // HACK, we need access to the new type rather than the old. This will go away in the
        // future
        @Suppress("UNCHECKED_CAST")
        val commonExtension =
                extension as ActionableVariantObjectOperationsExecutor<VariantBuilder, Variant>
        val userVisibleVariantBuilder =
                variantBuilder.createUserVisibleVariantObject(projectServices, profileEnabledVariantBuilder)
        commonExtension.executeVariantBuilderOperations(userVisibleVariantBuilder)

        // execute the new API
        variantBuilderOperationsRegistrar.executeOperations(userVisibleVariantBuilder)
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
        val pathHelper = VariantPathHelper(project, variantDslInfo, dslServices)
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
                taskCreationServices)

        // Run the VariantProperties actions
        val userVisibleVariant = (variantApiObject as VariantImpl)
                .createUserVisibleVariantObject(projectServices, profileEnabledVariantBuilder)
        commonExtension.executeVariantOperations(userVisibleVariant)
        variantOperationsRegistrar.executeOperations(userVisibleVariant)
        return ComponentInfo(
                variantBuilder, variantApiObject, profileEnabledVariantBuilder, userVisibleVariantBuilder)
    }

    private fun createCompoundSourceSets(
            productFlavorList: List<ProductFlavorData<ProductFlavor>>,
            variantDslInfoBuilder: VariantDslInfoBuilder) {
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

    /** Create a TestVariantData for the specified testedVariantData.  */
    fun createTestComponents(
            dimensionCombination: DimensionCombination,
            buildTypeData: BuildTypeData<BuildType>,
            productFlavorDataList: List<ProductFlavorData<ProductFlavor>>,
            testedComponentInfo: ComponentInfo<VariantBuilderT, VariantT>,
            variantType: VariantType): ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>? {

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        val testSourceSet = variantInputModel.defaultConfigData.getTestSourceSet(variantType)
        @Suppress("DEPRECATION") val dslServices = globalScope.dslServices
        val variantDslInfoBuilder = getBuilder(
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
                variantPropertiesApiServices)
        variantDslInfoBuilder.testedVariant =
                testedComponentInfo.variant.variantDslInfo as VariantDslInfoImpl
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
        // this is ANDROID_TEST
        val component = if (variantType.isApk) {
            val androidTestVariant = variantFactory.createAndroidTestBuilder(
                    variantDslInfo.componentIdentity,
                    variantDslInfo,
                    variantApiServices)

            // run the action registered on the tested variant via androidTest {}
            testedComponentInfo.userVisibleVariant?.executeAndroidTestActions(androidTestVariant)
            androidTestVariant
        } else {
            // this is UNIT_TEST
            val unitTestVariant = variantFactory.createUnitTestBuilder(
                    variantDslInfo.componentIdentity,
                    variantDslInfo,
                    variantApiServices)

            // run the action registered on the tested variant via unitTest {}
            testedComponentInfo.userVisibleVariant?.executeUnitTestActions(unitTestVariant)
            unitTestVariant
        }
        if (!component.enabled) {
            return null
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
        val variantDependencies = builder.build()
        val pathHelper = VariantPathHelper(project, variantDslInfo, dslServices)
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
        val componentProperties: TestComponentImpl
        val buildFeatureValues = variantFactory.createTestBuildFeatureValues(
                extension.buildFeatures, extension.dataBinding, projectOptions)

        // this is ANDROID_TEST
        componentProperties = if (variantType.isApk) {
            val androidTestProperties = variantFactory.createAndroidTest(
                    (component as AndroidTestBuilderImpl),
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
                    taskCreationServices)

            // also execute the delayed actions registered on the Component via
            // androidTest { onProperties {} }
            val userVisibleVariant =
                    androidTestProperties.createUserVisibleVariantObject(
                            projectServices, apiAccessStats)
            // or on the tested variant via unitTest {}
            testedComponentInfo.userVisibleVariant
                    ?.executeAndroidTestPropertiesActions(userVisibleVariant)
            androidTestProperties
        } else {
            // this is UNIT_TEST
            val unitTestProperties = variantFactory.createUnitTest(
                    (component as UnitTestBuilderImpl),
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
                    taskCreationServices)

            // execute the delayed actions registered on the Component via
            // unitTest { onProperties {} }
            val userVisibleVariant =
                    unitTestProperties.createUserVisibleVariantObject(
                            projectServices, apiAccessStats)
            // or on the tested variant via unitTestProperties {}
            testedComponentInfo.userVisibleVariant
                    ?.executeUnitTestPropertiesActions(userVisibleVariant)
            unitTestProperties
        }

        // register
        testedComponentInfo
                .variant
                .testComponents[variantDslInfo.variantType] = componentProperties
        return ComponentInfo(
                component, componentProperties, testedComponentInfo.stats, null)
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
            buildFeatureValues: BuildFeatureValues) {
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
                    buildFeatureValues)?.let { variantInfo ->
                addVariant(variantInfo)
                val variant = variantInfo.variant
                val variantDslInfo = variant.variantDslInfo
                val variantScope = variant.variantScope
                val minSdkVersion = variantInfo.variant.minSdkVersion.apiLevel
                val targetSdkVersion = variantDslInfo.targetSdkVersion.apiLevel
                if (minSdkVersion > 0 && targetSdkVersion > 0 && minSdkVersion > targetSdkVersion) {
                    projectServices
                            .issueReporter
                            .reportWarning(
                                    IssueReporter.Type.GENERIC, String.format(
                                    Locale.US,
                                    "minSdkVersion (%d) is greater than targetSdkVersion"
                                            + " (%d) for variant \"%s\". Please change the"
                                            + " values such that minSdkVersion is less than or"
                                            + " equal to targetSdkVersion.",
                                    minSdkVersion,
                                    targetSdkVersion,
                                    variant.name))
                }
                val variantBuilder = variantInfo.stats
                variantBuilder
                        .setIsDebug(buildType.isDebuggable)
                        .setMinSdkVersion(AnalyticsUtil.toProto(variantInfo.variant.minSdkVersion))
                        .setMinifyEnabled(variant.codeShrinker != null)
                        .setUseMultidex(variant.isMultiDexEnabled)
                        .setUseLegacyMultidex(variant.dexingType.isLegacyMultiDexMode())
                        .setVariantType(variant.variantType.analyticsVariantType)
                        .setDexBuilder(AnalyticsUtil.toProto(variantScope.dexer))
                        .setDexMerger(AnalyticsUtil.toProto(variantScope.dexMerger))
                        .setCoreLibraryDesugaringEnabled(variant.isCoreLibraryDesugaringEnabled)
                        .testExecution = AnalyticsUtil.toProto(
                                globalScope
                                        .extension
                                        .testOptions
                                        .getExecutionEnum())
                variant.codeShrinker?.let {
                    variantBuilder.codeShrinker = AnalyticsUtil.toProto(it)
                }
                if (variantDslInfo.targetSdkVersion.apiLevel > 0) {
                    variantBuilder.targetSdkVersion =
                            AnalyticsUtil.toProto(variantDslInfo.targetSdkVersion)
                }
                variantDslInfo.maxSdkVersion?.let {
                    variantBuilder.setMaxSdkVersion(
                            ApiVersion.newBuilder().setApiLevel(it.toLong()))
                }
                val supportType = variant.getJava8LangSupportType()
                if (supportType != VariantScope.Java8LangSupport.INVALID
                        && supportType != VariantScope.Java8LangSupport.UNUSED) {
                    variantBuilder.java8LangSupport = AnalyticsUtil.toProto(supportType)
                }
                if (variantFactory.variantType.hasTestComponents) {
                    if (buildTypeData == testBuildTypeData) {
                        val androidTest = createTestComponents(
                                dimensionCombination,
                                buildTypeData,
                                productFlavorDataList,
                                variantInfo, VariantTypeImpl.ANDROID_TEST)
                        androidTest?.let { addTestComponent(it) }
                    }
                    val unitTest = createTestComponents(
                            dimensionCombination,
                            buildTypeData,
                            productFlavorDataList,
                            variantInfo, VariantTypeImpl.UNIT_TEST)
                    unitTest?.let { addTestComponent(it) }
                }
            }
        }
    }

    private fun addVariant(variant: ComponentInfo<VariantBuilderT, VariantT>) {
        variants.add(variant)
    }

    private fun addTestComponent(
            testComponent: ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>) {
        testComponents.add(testComponent)
    }

    private fun createSigningOverride(): SigningConfig? {
        SigningOptions.readSigningOptions(projectOptions)?.let { signingOptions ->
            val signingConfigDsl = SigningConfig("externalOverride")
            signingConfigDsl.storeFile(File(signingOptions.storeFile))
            signingConfigDsl.storePassword(signingOptions.storePassword)
            signingConfigDsl.keyAlias(signingOptions.keyAlias)
            signingConfigDsl.keyPassword(signingOptions.keyPassword)
            signingOptions.storeType?.let {
                signingConfigDsl.storeType(it)
            }
            signingOptions.v1Enabled?.let {
                @Suppress("DEPRECATION")
                signingConfigDsl.isV1SigningEnabled = it
            }
            signingOptions.v2Enabled?.let {
                @Suppress("DEPRECATION")
                signingConfigDsl.isV2SigningEnabled = it
            }
            signingConfigDsl.enableV3Signing = signingOptions.enableV3Signing
            signingConfigDsl.enableV4Signing = signingOptions.enableV4Signing
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

        val SHRINKER_ATTR: Attribute<String> = Attribute.of("codeShrinker", String::class.java)

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
