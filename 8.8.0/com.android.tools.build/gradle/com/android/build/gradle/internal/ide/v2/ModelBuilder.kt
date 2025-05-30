/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.v2

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.component.impl.DeviceTestImpl
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.ScopedArtifacts.Scope.ALL
import com.android.build.api.variant.ScopedArtifacts.Scope.PROJECT
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.HasDeviceTestsCreationConfig
import com.android.build.api.variant.impl.HasHostTestsCreationConfig
import com.android.build.api.variant.impl.HasTestFixtures
import com.android.build.api.variant.impl.ManifestFilesImpl
import com.android.build.gradle.internal.BuildTypeData
import com.android.build.gradle.internal.ProductFlavorData
import com.android.build.gradle.internal.VariantDimensionData
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.attributes.VariantAttr
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.AdditionalArtifactType
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl.GlobalSyncIssueService
import com.android.build.gradle.internal.ide.DependencyFailureHandler
import com.android.build.gradle.internal.ide.Utils.getGeneratedAssetsFolders
import com.android.build.gradle.internal.ide.Utils.getGeneratedResourceFolders
import com.android.build.gradle.internal.ide.Utils.getGeneratedSourceFolders
import com.android.build.gradle.internal.ide.Utils.getGeneratedSourceFoldersForUnitTests
import com.android.build.gradle.internal.ide.dependencies.AdditionalArtifacts
import com.android.build.gradle.internal.ide.dependencies.FullDependencyGraphBuilder
import com.android.build.gradle.internal.ide.dependencies.GraphEdgeCache
import com.android.build.gradle.internal.ide.dependencies.LibraryService
import com.android.build.gradle.internal.ide.dependencies.LibraryServiceImpl
import com.android.build.gradle.internal.ide.dependencies.getArtifactsForModelBuilder
import com.android.build.gradle.internal.ide.dependencies.getVariantName
import com.android.build.gradle.internal.lint.getLocalCustomLintChecksForModel
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AnchorTaskNames
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask.Companion.checkProguardFiles
import com.android.build.gradle.internal.tasks.ExtractPrivacySandboxCompatApks
import com.android.build.gradle.internal.tasks.GenerateAdditionalApkSplitForDeploymentViaApk
import com.android.build.gradle.internal.tasks.getPublishedCustomLintChecks
import com.android.build.gradle.internal.utils.getDesugarLibConfigFile
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.internal.utils.toImmutableSet
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.BuildPrivacySandboxSdkApks
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.SyncIssue
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags.BooleanFlag
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.ArtifactDependenciesAdjacencyList
import com.android.builder.model.v2.ide.BasicArtifact
import com.android.builder.model.v2.ide.BundleInfo
import com.android.builder.model.v2.ide.BytecodeTransformation
import com.android.builder.model.v2.ide.CodeShrinker
import com.android.builder.model.v2.ide.JavaArtifact
import com.android.builder.model.v2.ide.PrivacySandboxSdkInfo
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestInfo
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectGraph
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.VariantDependenciesAdjacencyList
import com.android.builder.model.v2.models.VariantDependenciesFlatList
import com.android.builder.model.v2.models.Versions
import com.android.utils.associateNotNull
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.Serializable
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.EndElement
import com.android.build.gradle.internal.dsl.BuildType as InternalBuildType
import com.android.build.gradle.internal.dsl.ProductFlavor as InternalFlavor

class ModelBuilder<
        BuildFeaturesT : BuildFeatures,
        BuildTypeT : BuildType,
        DefaultConfigT : DefaultConfig,
        ProductFlavorT : ProductFlavor,
        AndroidResourcesT : AndroidResources,
        InstallationT : Installation,
        ExtensionT : CommonExtension<
                BuildFeaturesT,
                BuildTypeT,
                DefaultConfigT,
                ProductFlavorT,
                AndroidResourcesT,
                InstallationT>>(
    private val project: Project,
    private val variantModel: VariantModel,
    private val extension: ExtensionT,
) : ParameterizedToolingModelBuilder<ModelBuilderParameter> {

    override fun getParameterType(): Class<ModelBuilderParameter> {
        return ModelBuilderParameter::class.java
    }

    override fun canBuild(className: String): Boolean {
        return className == Versions::class.java.name
                || className == BasicAndroidProject::class.java.name
                || className == AndroidProject::class.java.name
                || className == AndroidDsl::class.java.name
                || className == ProjectGraph::class.java.name
                || className == VariantDependencies::class.java.name
                || className == VariantDependenciesAdjacencyList::class.java.name
                || className == VariantDependenciesFlatList::class.java.name
                || className == ProjectSyncIssues::class.java.name
    }

    /**
     * Non-parameterized model query. Valid for all but the VariantDependencies model
     */
    override fun buildAll(className: String, project: Project): Any = when (className) {
        Versions::class.java.name -> buildModelVersions()
        BasicAndroidProject::class.java.name -> buildBasicAndroidProjectModel(project)
        AndroidProject::class.java.name -> buildAndroidProjectModel(project)
        AndroidDsl::class.java.name -> buildAndroidDslModel(project)
        ProjectSyncIssues::class.java.name -> buildProjectSyncIssueModel(project)
        VariantDependencies::class.java.name,
        VariantDependenciesAdjacencyList::class.java.name,
        ProjectGraph::class.java.name -> throw RuntimeException(
            "Please use parameterized Tooling API to obtain ${className.split(".").last()} model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    /**
     * Parameterized model query. Valid only for the VariantDependencies model
     */
    override fun buildAll(
        className: String,
        parameter: ModelBuilderParameter,
        project: Project
    ): Any? = when (className) {
        VariantDependencies::class.java.name -> buildVariantDependenciesModel(project, parameter)
        VariantDependenciesAdjacencyList::class.java.name -> buildVariantDependenciesModel(project, parameter, adjacencyList=true)
        VariantDependenciesFlatList::class.java.name -> buildVariantDependenciesFlatListModel(project, parameter)
        ProjectGraph::class.java.name -> buildProjectGraphModel(project, parameter)
        Versions::class.java.name,
        AndroidProject::class.java.name,
        AndroidDsl::class.java.name,
        ProjectSyncIssues::class.java.name -> throw RuntimeException(
            "Please use non-parameterized Tooling API to obtain $className model."
        )
        else -> throw RuntimeException("Does not support model '$className'")
    }

    /**
     * Normally, the dependencies of each app is fetched to figure out which variants to request for
     * each sub-project, however this initial fetch of dependencies might take a long time (in the
     * order of 10 seconds) for builds with many subprojects. In this case, AGP provides a lighter
     * weight graph that only contains the sub-project info. This allows the IDE to parallelize
     * execution much faster.
     */
    private fun buildProjectGraphModel(project: Project, parameter: ModelBuilderParameter): ProjectGraph? {
        val variantName = parameter.variantName
        val variant = variantModel.variants.singleOrNull { it.name == variantName } ?: return null

        return ProjectGraphImpl(
            resolvedVariants = (variant.variantDependencies.getArtifactCollectionForToolingModel(
                RUNTIME_CLASSPATH, AndroidArtifacts.ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.JAR
            ) {
                // Make a copy of the runtime classpath configuration and replace all non-project
                // dependencies with a self dependency. This makes sure Gradle skips any resolution
                // for external libraries.
                it.copyRecursive().apply {
                    resolutionStrategy.dependencySubstitution.all {
                        if (it.requested !is ProjectComponentSelector) {
                            it.useTarget(project)
                        }
                    }
                }
            }).artifacts.associateNotNull {
                // Requesting artifacts because asking for the resolution root is more expensive
                val resolvedVariantResult = it.variant
                val projectPath =  (resolvedVariantResult.owner as? ProjectComponentIdentifier)?.projectPath ?: return@associateNotNull null
                val resolvedVariantName = resolvedVariantResult.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name ?: return@associateNotNull null
                projectPath to resolvedVariantName
            }
        )
    }


    private fun buildModelVersions(): Versions {
        /**
         * This is currently unused, but could be used in the future to allow for softer breaking
         * changes, where some models are unavailable, but the project can still be imported.
         */
        val v2Version = VersionImpl(0,1)
        /**
         * The version of the model-producing code (i.e. the model builders in AGP)
         *
         * The major version is increased every time an addition is made to the model interfaces,
         * or other semantic change that the code injected by studio should react to, such as
         * instructing studio to stop calling a method that returns a collection that is no longer
         * populated which leaves the minor to be able to be incremented for partial backports.
         *
         * Changes made to the model must always be compatible with the MINIMUM_MODEL_CONSUMER
         * version of Android Studio. To make a breaking change, such as removing an older model
         * method not called by current versions of Studio, the MINIMUM_MODEL_CONSUMER version must
         * be increased to exclude all older versions of Studio that called that method.
         */
        val modelProducer = VersionImpl(12, 0, humanReadable = "Android Gradle Plugin 8.7")
        /**
         * The minimum required model consumer version, to allow AGP to control support for older
         * versions of Android Studio.
         *
         * Android Studio's model consumer version will be incremented after each release branching.
         *
         * The below Android Gradle plugin's minimumModelConsumerVersion will usually be increased
         * after the next version of Studio becomes stable, dropping support for previous
         * Android Studio versions.
         */
        val minimumModelConsumerVersion = VersionImpl(major = 66, minor = 1, humanReadable = "Android Studio Iguana")
        return VersionsImpl(
            agp = Version.ANDROID_GRADLE_PLUGIN_VERSION,
            versions = mutableMapOf<String, Versions.Version>(
                Versions.BASIC_ANDROID_PROJECT to v2Version,
                Versions.ANDROID_PROJECT to v2Version,
                Versions.ANDROID_DSL to v2Version,
                Versions.VARIANT_DEPENDENCIES to v2Version,
                Versions.NATIVE_MODULE to v2Version,
                Versions.MODEL_PRODUCER to modelProducer,
                Versions.MINIMUM_MODEL_CONSUMER to minimumModelConsumerVersion
            )
        )
    }

    /**
     * Indicates the dimensions used for a variant
     */
    internal data class DimensionInformation(
        val buildTypes: Set<String>,
        val flavors: Set<Pair<String, String>>
    ) {
        fun isNotEmpty(): Boolean = buildTypes.isNotEmpty() || flavors.isNotEmpty()

        companion object {
            fun createFrom(components: Collection<ComponentCreationConfig>): DimensionInformation {
                val buildTypes = mutableSetOf<String>()
                val flavors = mutableSetOf<Pair<String, String>>()

                for (component in components) {
                    component.buildType?.let { buildTypes.add(it) }
                    flavors.addAll(component.productFlavors)
                }

                return DimensionInformation(buildTypes, flavors)
            }
        }
    }

    private fun buildBasicAndroidProjectModel(project: Project): BasicAndroidProject {
        val sdkSetupCorrectly = variantModel.versionedSdkLoader.get().sdkSetupCorrectly.get()

        // Get the boot classpath. This will ensure the target is configured.
        val bootClasspath = if (sdkSetupCorrectly) {
            variantModel.filteredBootClasspath.get().map { it.asFile }
        } else {
            // SDK not set up, error will be reported as a sync issue.
            emptyList()
        }

        val variantInputs = variantModel.inputs
        val variants = variantModel.variants
        val variantDimensionInfo = DimensionInformation.createFrom(variants)

        // for now grab the first buildFeatureValues as they cannot be different.
        val buildFeatures = variantModel.buildFeatures

        // gather the default config
        val defaultConfig = if (variantDimensionInfo.isNotEmpty()) {
            DefaultSourceSetContainerBuilder().build()
        } else null

        // gather all the build types
        val buildTypes = mutableListOf<SourceSetContainer>()
        for (buildType in variantInputs.buildTypes.values) {
            if (variantDimensionInfo.buildTypes.contains(buildType.buildType.name)) {
                buildTypes.add(BuildTypeSourceSetBuilder(buildType).build())
            }
        }

        // gather product flavors
        val productFlavors = mutableListOf<SourceSetContainer>()
        for (flavor in variantInputs.productFlavors.values) {
            val flavorDimensionName = flavor.productFlavor.dimension to flavor.productFlavor.name
            if (variantDimensionInfo.flavors.contains(flavorDimensionName)) {
                productFlavors.add(FlavorSourceSetContainerBuilder(flavor).build())
            }
        }

        // gather variants
        val variantList = variants.map { createBasicVariant(it, buildFeatures) }

        return BasicAndroidProjectImpl(
            path = project.path,
            buildFolder = project.layout.buildDirectory.get().asFile,

            projectType = variantModel.projectType,

            mainSourceSet = defaultConfig,
            buildTypeSourceSets = buildTypes,
            productFlavorSourceSets = productFlavors,

            variants = variantList,

            bootClasspath = bootClasspath,
        )
    }

    /**
     * Abstract class for building SourceSetContainer. It has logic to add sourceSets, deviceTestSource,
     * hostTestSource and fixturesTesSources based on variantModel. This will allow excluding from the model
     * sourceSets that are not used by any of them.
     */
    abstract inner class BaseSourceSetContainerBuilder {

        val buildFeatures: BuildFeatureValues
            get() = variantModel.buildFeatures

        internal val androidTests =
            DimensionInformation.createFrom(variantModel.testComponents.filterIsInstance<DeviceTestCreationConfig>())
        internal val unitTests =
            DimensionInformation.createFrom(variantModel.testComponents.filter {
                it.componentType == ComponentTypeImpl.UNIT_TEST
            })
        internal val testFixtures =
            DimensionInformation.createFrom(variantModel.variants.mapNotNull { (it as? HasTestFixtures)?.testFixtures })
        internal val screenshotTests =
            DimensionInformation.createFrom(variantModel.testComponents.filter {
                it.componentType == ComponentTypeImpl.SCREENSHOT_TEST
            })

        abstract val variantData: VariantDimensionData
        abstract val defaultSourceSet: DefaultAndroidSourceSet

        private fun getAndroidTestSourceSet() =
            variantData.getSourceSetForModel(ComponentTypeImpl.ANDROID_TEST)
        private fun getUnitSourceSet() =
            variantData.getSourceSetForModel(ComponentTypeImpl.UNIT_TEST)
        private fun getScreenshotSourceSet() =
            variantData.getSourceSetForModel(ComponentTypeImpl.SCREENSHOT_TEST)
        private fun getFixtureSourceSet() =
            variantData.getSourceSetForModel(ComponentTypeImpl.TEST_FIXTURES)

        abstract fun shouldTakeAndroidTestSourceSet(): Boolean
        abstract fun shouldTakeUnitSourceSet(): Boolean
        abstract fun shouldTakeFixtureSourceSet(): Boolean
        abstract fun shouldTakeScreenshotSourceSet(): Boolean

        open fun additionalDefaultConfig(): List<ComponentCreationConfig> = listOf()
        open fun additionalAndroidConfig(): List<ComponentCreationConfig> = listOf()
        open fun additionalUnitConfig(): List<ComponentCreationConfig> = listOf()
        open fun additionalScreenshotConfig(): List<ComponentCreationConfig> = listOf()
        open fun additionalFixtureConfig(): List<ComponentCreationConfig> = listOf()

        fun build() =
            SourceSetContainerImpl(
                sourceProvider = defaultSourceSet.convert(buildFeatures, additionalDefaultConfig()),
                deviceTestSourceProviders = mutableMapOf<String, SourceProvider>().apply {
                    getAndroidTestSourceSet()
                        ?.takeIf { shouldTakeAndroidTestSourceSet() }
                        ?.convert(buildFeatures, additionalAndroidConfig())
                        ?.let { this[ComponentTypeImpl.ANDROID_TEST.artifactName] = it }
                },
                hostTestSourceProviders = mutableMapOf<String, SourceProvider>().apply {
                    getUnitSourceSet()
                        ?.takeIf { shouldTakeUnitSourceSet() }
                        ?.convert(buildFeatures, additionalUnitConfig())
                        ?.let { this[ComponentTypeImpl.UNIT_TEST.artifactName] = it }
                    getScreenshotSourceSet()
                        ?.takeIf { shouldTakeScreenshotSourceSet() }
                        ?.convert(buildFeatures, additionalScreenshotConfig())
                        ?.let { this[ComponentTypeImpl.SCREENSHOT_TEST.artifactName] = it }
                },
                testFixturesSourceProvider = getFixtureSourceSet()
                    ?.takeIf { shouldTakeFixtureSourceSet() }
                    ?.convert(buildFeatures, additionalFixtureConfig())
            )
    }

    /**
     * Build SourceSetContainer for particular build type.
     *
     * It also adds user defined static folders (added with variant api)
     * in case there are no flavours.
     */
    inner class BuildTypeSourceSetBuilder(val buildType: BuildTypeData<InternalBuildType>) :
        BaseSourceSetContainerBuilder() {

        private val buildTypeName = buildType.buildType.name
        override val defaultSourceSet = buildType.sourceSet

        override fun shouldTakeAndroidTestSourceSet() =
            androidTests.buildTypes.contains(buildTypeName)

        override fun shouldTakeUnitSourceSet() = unitTests.buildTypes.contains(buildTypeName)
        override fun shouldTakeFixtureSourceSet() = testFixtures.buildTypes.contains(buildTypeName)
        override fun shouldTakeScreenshotSourceSet() =
            screenshotTests.buildTypes.contains(buildTypeName)

        override val variantData: VariantDimensionData = buildType

        private fun isNoVariantProvider() = variantModel.inputs.productFlavors.values.isEmpty()

        // if there is no flavours than variant source provider is empty and we need to add custom
        // folders to build type
        override fun additionalDefaultConfig() =
            if (isNoVariantProvider()) variantModel.variants.filter { it.buildType == buildTypeName } else listOf()

        override fun additionalAndroidConfig(): List<DeviceTestCreationConfig> =
            additionalDefaultConfig()
                .filterIsInstance<HasDeviceTestsCreationConfig>()
                .flatMap { it.deviceTests.values.filterIsInstance<DeviceTestCreationConfig>() }

        override fun additionalUnitConfig() =
            additionalDefaultConfig()
                .filterIsInstance<HasHostTestsCreationConfig>()
                .flatMap { it.hostTests.values }
                .filter { it.componentType == ComponentTypeImpl.UNIT_TEST }

        override fun additionalScreenshotConfig() =
            additionalDefaultConfig()
                .filterIsInstance<HasHostTestsCreationConfig>()
                .flatMap { it.hostTests.values }
                .filter { it.componentType == ComponentTypeImpl.SCREENSHOT_TEST }

        override fun additionalFixtureConfig() =
            additionalDefaultConfig()
                .filterIsInstance<HasTestFixtures>()
                .mapNotNull { it.testFixtures }
    }

    /**
     * Builds SourceSetContainer for variant defaultConfigData.
     */
    inner class DefaultSourceSetContainerBuilder : BaseSourceSetContainerBuilder() {

        val defaultConfigData = variantModel.inputs.defaultConfigData
        override val defaultSourceSet = defaultConfigData.sourceSet
        override fun shouldTakeAndroidTestSourceSet() = androidTests.isNotEmpty()
        override fun shouldTakeUnitSourceSet() = unitTests.isNotEmpty()
        override fun shouldTakeFixtureSourceSet() = testFixtures.isNotEmpty()
        override fun shouldTakeScreenshotSourceSet() = screenshotTests.isNotEmpty()
        override val variantData: VariantDimensionData = defaultConfigData
    }

    /**
     * Builds SourceSetContainer for particular flavor.
     */
    inner class FlavorSourceSetContainerBuilder(flavor: ProductFlavorData<InternalFlavor>) :
        BaseSourceSetContainerBuilder() {

        private val flavorDimensionName =
            flavor.productFlavor.dimension to flavor.productFlavor.name

        override val defaultSourceSet = flavor.sourceSet
        override fun shouldTakeAndroidTestSourceSet() =
            androidTests.flavors.contains(flavorDimensionName)

        override fun shouldTakeUnitSourceSet() = unitTests.flavors.contains(flavorDimensionName)
        override fun shouldTakeFixtureSourceSet() =
            screenshotTests.flavors.contains(flavorDimensionName)

        override fun shouldTakeScreenshotSourceSet() =
            testFixtures.flavors.contains(flavorDimensionName)

        override val variantData = flavor
    }

    private fun buildAndroidProjectModel(project: Project): AndroidProject {
        val variants = variantModel.variants

        // Keep track of the result of parsing each manifest for instant app value.
        // This prevents having to reparse the
        val instantAppResultMap = mutableMapOf<File, Boolean>()

        // gather variants
        var namespace: String? = null
        var androidTestNamespace: String? = null
        var testFixturesNamespace: String? = null
        val variantList = variants.map {
            namespace = it.namespace.get()
            if (androidTestNamespace == null && it is HasDeviceTestsCreationConfig) {
                it.defaultDeviceTest?.let { androidTest ->
                    androidTestNamespace = androidTest.namespace.get()
                }
            }
            if (testFixturesNamespace == null && it is HasTestFixtures) {
                testFixturesNamespace = it.testFixtures?.namespace?.get()
            }

            checkProguardFiles(it)

            createVariant(it, instantAppResultMap)
        }

        val desugarLibConfig = if (extension.compileOptions.isCoreLibraryDesugaringEnabled)
            getDesugarLibConfigFile(project)
        else
            listOf()

        return AndroidProjectImpl(
            namespace = namespace ?: "",
            androidTestNamespace = androidTestNamespace,
            testFixturesNamespace = testFixturesNamespace,
            variants = variantList,
            javaCompileOptions = extension.compileOptions.convert(),
            resourcePrefix = extension.resourcePrefix,
            dynamicFeatures = (extension as? ApplicationExtension)?.dynamicFeatures?.toImmutableSet(),
            viewBindingOptions = ViewBindingOptionsImpl(
                variantModel.variants.any { it.buildFeatures.viewBinding }
            ),
            flags = getAgpFlags(
                variants = variantModel.variants,
                projectOptions = variantModel.projectOptions

            ),
            lintChecksJars = getLocalCustomLintChecksForModel(project, variantModel.syncIssueReporter),
            desugarLibConfig = desugarLibConfig,
            // Using first as we are going to use the global artifacts anyway
            lintJar = variantModel.variants.firstOrNull()?.global?.getPublishedCustomLintChecks()?.files?.singleOrNull()
        )
    }

    private fun checkProguardFiles(component: VariantCreationConfig) {
        // We check for default files unless it's a base module, which can include default files.
        val isBaseModule = component.componentType.isBaseModule
        val isDynamicFeature = component.componentType.isDynamicFeature
        if (!isBaseModule) {
            checkProguardFiles(
                project.layout.buildDirectory,
                isDynamicFeature,
                component.optimizationCreationConfig.consumerProguardFilePaths
            ) { errorMessage: String -> variantModel
                .syncIssueReporter
                .reportError(IssueReporter.Type.GENERIC, errorMessage)
            }
        }
    }

    private fun buildAndroidDslModel(project: Project): AndroidDsl {

        val variantInputs = variantModel.inputs

        // for now grab the first buildFeatureValues as they cannot be different.
        val buildFeatures = variantModel.buildFeatures

        // gather the default config
        val defaultConfig = variantInputs.defaultConfigData.defaultConfig.convert(buildFeatures)

        // gather all the build types
        val buildTypes = mutableListOf<com.android.builder.model.v2.dsl.BuildType>()
        for (buildType in variantInputs.buildTypes.values) {
            buildTypes.add(buildType.buildType.convert(buildFeatures))
        }

        // gather product flavors
        val productFlavors = mutableListOf<com.android.builder.model.v2.dsl.ProductFlavor>()
        for (flavor in variantInputs.productFlavors.values) {
            productFlavors.add(flavor.productFlavor.convert(buildFeatures))
        }

        val dependenciesInfo =
                if (extension is ApplicationExtension) {
                    DependenciesInfoImpl(
                        extension.dependenciesInfo.includeInApk,
                        extension.dependenciesInfo.includeInBundle
                    )
                } else null

        val extensionImpl =
            extension as? CommonExtensionImpl<*, *, *, *, *, *>
                ?: throw RuntimeException("Wrong extension provided to v2 ModelBuilder")
        val compileSdkVersion = extensionImpl.compileSdkVersion ?: "unknown"

        return AndroidDslImpl(
            buildToolsVersion = extension.buildToolsVersion,
            groupId = project.group.toString(),
            compileTarget = compileSdkVersion,
            defaultConfig = defaultConfig,
            buildTypes = buildTypes,
            flavorDimensions = ImmutableList.copyOf(extension.flavorDimensions),
            productFlavors = productFlavors,
            signingConfigs = extension.signingConfigs.map { it.convert() },
            aaptOptions = extension.androidResources.convert(),
            lintOptions = extension.lint.convert(),
            installation = extension.installation.convert(),
            dependenciesInfo = dependenciesInfo,
            )
    }

    private fun buildProjectSyncIssueModel(project: Project): ProjectSyncIssues {
        variantModel.syncIssueReporter.lockHandler()

        val allIssues = ImmutableSet.builder<SyncIssue>()
        allIssues.addAll(variantModel.syncIssueReporter.syncIssues)
        allIssues.addAll(
            getBuildService(project.gradle.sharedServices, GlobalSyncIssueService::class.java)
                .get()
                .getAllIssuesAndClear()
        )

        // For now we have to convert from the v1 to the v2 model.
        // FIXME: separate the internal-AGP and builder-model version of the SyncIssue classes
        val issues = allIssues.build().map {
            SyncIssueImpl(
                it.severity,
                it.type,
                it.data,
                it.message,
                it.multiLineMessage
            )
        }

        return ProjectSyncIssuesImpl(issues)
    }

    private fun buildVariantDependenciesFlatListModel(project: Project, parameter: ModelBuilderParameter): VariantDependenciesFlatList? {
        val variantName = parameter.variantName
        val variant = variantModel.variants.singleOrNull { it.name == variantName } ?: return null

        val globalLibraryBuildService =
            getBuildService(
                project.gradle.sharedServices,
                GlobalSyncService::class.java
            ).get()

        val libraryService = LibraryServiceImpl(globalLibraryBuildService.libraryCache)


        val deviceTests = (variant as? HasDeviceTestsCreationConfig)?.deviceTests?.values?.filterIsInstance<DeviceTestImpl>()
        val hostTests = (variant as? HasHostTestsCreationConfig)?.hostTests?.values
        val testFixtures = (variant as? HasTestFixtures)?.testFixtures

        val components = mutableListOf<ComponentCreationConfig>()

        components.add(variant)
        deviceTests?.forEach { components.add(it) }
        hostTests?.forEach { components.add(it) }
        testFixtures?.let { components.add(it) }

        val configTypes = listOf(COMPILE_CLASSPATH) +
                if (parameter.dontBuildRuntimeClasspath) {
                    emptyList()
                } else {
                    listOf(RUNTIME_CLASSPATH)
                }

        val results = components.associateWith { component ->
            val failures = mutableListOf<Throwable>()
            val graphs = configTypes.associateWith { configType ->
                fun getAdditionalArtifacts(type: AdditionalArtifactType) =
                    if (variant.services.projectOptions.get(BooleanOption.ADDITIONAL_ARTIFACTS_IN_MODEL))
                        variant.variantDependencies.getAdditionalArtifacts(configType, type).artifactFiles.files.single()
                    else
                        null

                val artifacts = getArtifactsForModelBuilder(component, configType) {
                    failures.addAll(it)
                }


                artifacts.map {
                    val javadoc = getAdditionalArtifacts(AdditionalArtifactType.JAVADOC)
                    val source = getAdditionalArtifacts(AdditionalArtifactType.SOURCE)
                    val sample = getAdditionalArtifacts(AdditionalArtifactType.SAMPLE)
                    val additionalArtifacts = AdditionalArtifacts(javadoc, source, sample)
                    libraryService.getLibrary(it, additionalArtifacts).key
                }
            }

            ArtifactDependenciesFlatListImpl(
                graphs[COMPILE_CLASSPATH]!!,
                graphs[RUNTIME_CLASSPATH],
                failures.map {
                    // Try and extract the failed component. Unfortunately this is not provided
                    // by gradle when requesting artifacts.
                    UnresolvedDependencyImpl((it as? ModuleVersionResolveException)?.selector?.toString() ?: "", it.message)
                }
            )
        }

        return VariantDependenciesFlatListImpl(
            name = variantName,
            mainArtifact = results[variant]!!,
            deviceTestArtifacts = deviceTests?.associate { it.artifactName to results[it]!! } ?: emptyMap(),
            hostTestArtifacts = hostTests?.associate { it.componentType.artifactName to results[it]!!} ?: emptyMap(),
            testFixturesArtifact = testFixtures?.let { results[it]!! },
            libraryService.getAllLibraries()
        )
    }


    private fun buildVariantDependenciesModel(
        project: Project,
        parameter: ModelBuilderParameter,
        adjacencyList: Boolean = false
    ): Serializable? {
        // get the variant to return the dependencies for
        val variantName = parameter.variantName
        val variant = variantModel.variants
            .singleOrNull { it.name == variantName }
            ?: return null

        val globalLibraryBuildService =
            getBuildService(
                project.gradle.sharedServices,
                GlobalSyncService::class.java
            ).get()

        val graphEdgeCache = globalLibraryBuildService.graphEdgeCache
        val libraryService = LibraryServiceImpl(globalLibraryBuildService.libraryCache)

        if (adjacencyList) {
            val deviceTestArtifacts = mutableMapOf<String, ArtifactDependenciesAdjacencyList>()
            (variant as? HasDeviceTestsCreationConfig)?.deviceTests?.values?.filterIsInstance<DeviceTestImpl>()?.forEach {
                deviceTestArtifacts[it.artifactName] =
                    createDependenciesWithAdjacencyList(
                        it,
                        libraryService,
                        graphEdgeCache,
                        parameter.dontBuildAndroidTestRuntimeClasspath
                    )
            }
            val hostTestArtifacts = mutableMapOf<String, ArtifactDependenciesAdjacencyList>()
            (variant as? HasHostTestsCreationConfig)?.hostTests?.values?.forEach { hostTest ->
                hostTestArtifacts[hostTest.componentType.artifactName] =
                    createDependenciesWithAdjacencyList(
                        hostTest,
                        libraryService,
                        graphEdgeCache,
                        parameter.dontBuildUnitTestRuntimeClasspath
                    )
            }

            return VariantDependenciesAdjacencyListImpl(
                    name = variantName,
                    mainArtifact = createDependenciesWithAdjacencyList(
                            variant,
                            libraryService,
                            graphEdgeCache,
                            parameter.dontBuildRuntimeClasspath
                    ),
                    deviceTestArtifacts = deviceTestArtifacts,
                    hostTestArtifacts = hostTestArtifacts,
                    testFixturesArtifact = (variant as? HasTestFixtures)?.testFixtures?.let {
                        createDependenciesWithAdjacencyList(
                            it,
                            libraryService,
                            graphEdgeCache,
                            parameter.dontBuildTestFixtureRuntimeClasspath
                        )
                    },
                    libraryService.getAllLibraries()
            )
        } else {
            val deviceTestArtifacts = mutableMapOf<String, ArtifactDependencies>()
            (variant as? HasDeviceTestsCreationConfig)?.deviceTests?.values?.filterIsInstance<DeviceTestImpl>()?.forEach {
                deviceTestArtifacts[it.artifactName] =
                    createDependencies(
                        it,
                        libraryService,
                        parameter.dontBuildAndroidTestRuntimeClasspath
                    )
            }
            val hostTestArtifacts = mutableMapOf<String, ArtifactDependencies>()
            (variant as? HasHostTestsCreationConfig)?.hostTests?.values?.forEach {
                hostTestArtifacts[it.componentType.artifactName] = createDependencies(
                    it,
                    libraryService,
                    parameter.dontBuildHostTestRuntimeClasspath[it.componentType.suffix] ?: false,
                )
            }
            return VariantDependenciesImpl(
                    name = variantName,
                    mainArtifact = createDependencies(
                            variant,
                            libraryService,
                            parameter.dontBuildRuntimeClasspath
                    ),
                    deviceTestArtifacts = deviceTestArtifacts,
                    hostTestArtifacts = hostTestArtifacts,
                    testFixturesArtifact = (variant as? HasTestFixtures)?.testFixtures?.let {
                        createDependencies(
                            it,
                            libraryService,
                            parameter.dontBuildTestFixtureRuntimeClasspath
                        )
                    },
                    libraryService.getAllLibraries()
            )
        }
    }

    private fun createBasicVariant(
        variant: VariantCreationConfig,
        features: BuildFeatureValues
    ): BasicVariantImpl {
        val deviceTestArtifacts = mutableMapOf<String, BasicArtifact>()
        (variant as? HasDeviceTestsCreationConfig)?.deviceTests?.values?.filterIsInstance<DeviceTestImpl>()?.forEach {
            deviceTestArtifacts[it.artifactName] = createBasicArtifact(it, features)
        }
        val hostTestArtifacts = mutableMapOf<String, BasicArtifact>()
        (variant as? HasHostTestsCreationConfig)?.hostTests?.values?.forEach { hostTest ->
            hostTestArtifacts[hostTest.componentType.artifactName] =
                createBasicArtifact(hostTest, features)

        }
        return BasicVariantImpl(
            name = variant.name,
            mainArtifact = createBasicArtifact(variant, features),
            deviceTestArtifacts = deviceTestArtifacts,
            hostTestArtifacts = hostTestArtifacts,
            testFixturesArtifact = (variant as? HasTestFixtures)?.testFixtures?.let {
                createBasicArtifact(it, features)
            },
            buildType = variant.buildType,
            productFlavors = variant.productFlavorList.map { it.name },
        )
    }

    private fun createBasicArtifact(
        component: ComponentCreationConfig,
        features: BuildFeatureValues
    ): BasicArtifact {
        return BasicArtifactImpl(
            variantSourceProvider = component.sources.variantSourceProvider?.convert(
                component.sources
            ),
            multiFlavorSourceProvider = component.sources.multiFlavorSourceProvider?.convert(
                features
            ),
        )
    }

    private fun createVariant(
        variant: VariantCreationConfig,
        instantAppResultMap: MutableMap<File, Boolean>
    ): VariantImpl {
        val deviceTestArtifacts = mutableMapOf<String, AndroidArtifact>()
        (variant as? HasDeviceTestsCreationConfig)?.deviceTests?.values?.filterIsInstance<DeviceTestImpl>()?.forEach {
            deviceTestArtifacts[it.artifactName] = createAndroidArtifact(it)
        }
        val hostTestArtifacts = mutableMapOf<String, JavaArtifact>()
        (variant as? HasHostTestsCreationConfig)?.hostTests?.values?.forEach { hostTest ->
            hostTestArtifacts[hostTest.componentType.artifactName] =
                createJavaArtifact(hostTest)
        }
        return VariantImpl(
            name = variant.name,
            displayName = variant.baseName,
            mainArtifact = createAndroidArtifact(variant),
            deviceTestArtifacts = deviceTestArtifacts,
            hostTestArtifacts = hostTestArtifacts,
            testFixturesArtifact = (variant as? HasTestFixtures)?.testFixtures?.let {
                createAndroidArtifact(it)
            },
            testedTargetVariant = getTestTargetVariant(variant),
            runTestInSeparateProcess = ModulePropertyKey.BooleanWithDefault.SELF_INSTRUMENTING.getValue(
                    variant.experimentalProperties.get()),
            isInstantAppCompatible = inspectManifestForInstantTag(variant, instantAppResultMap),
            desugaredMethods = getDesugaredMethods(
                variant.services,
                variant.isCoreLibraryDesugaringEnabledLintCheck,
                variant.minSdk,
                variant.global
            ).files.toList(),
            experimentalProperties = if (variant.experimentalProperties.isPresent) {
                variant.experimentalProperties.get().mapValues { it.value.toString() }
            } else emptyMap()
        )
    }

    private fun createPrivacySandboxSdkInfo(component: ComponentCreationConfig): PrivacySandboxSdkInfo? {
        if (component.privacySandboxCreationConfig == null) {
            return null
        }
        if (component !is ApplicationCreationConfig) {
            return null
        }
        val extractedApksFromPrivacySandboxIdeModel =
                component.artifacts.get(InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs_IDE_MODEL).orNull?.asFile
                        ?: return null
        val legacyExtractedApksForPrivacySandboxIdeModel =
                component.artifacts.get(InternalArtifactType.APK_FROM_SDKS_IDE_MODEL).orNull?.asFile
                        ?: return null
        val additionalApkSplitFile =
                component.artifacts.get(InternalArtifactType.USES_SDK_LIBRARY_SPLIT_FOR_LOCAL_DEPLOYMENT).orNull?.file(
                        BuiltArtifactsImpl.METADATA_FILE_NAME)?.asFile
                        ?: return null

        return PrivacySandboxSdkInfoImpl(
                task = BuildPrivacySandboxSdkApks.CreationAction.getTaskName(component),
                outputListingFile = extractedApksFromPrivacySandboxIdeModel,
                additionalApkSplitTask =  GenerateAdditionalApkSplitForDeploymentViaApk.CreationAction.computeTaskName(component),
                additionalApkSplitFile = additionalApkSplitFile,
                taskLegacy = ExtractPrivacySandboxCompatApks.CreationAction.getTaskName(component),
                outputListingLegacyFile = legacyExtractedApksForPrivacySandboxIdeModel
        )
    }

    private fun createAndroidArtifact(component: ComponentCreationConfig): AndroidArtifactImpl {
        val taskContainer: MutableTaskContainer = component.taskContainer

        // FIXME need to find a better way for this. We should be using PROJECT_CLASSES_DIRS.
        // The class folders. This is supposed to be the output of the compilation steps + other
        // steps that create bytecode
        val classesFolders = mutableSetOf<File>()
        classesFolders.add(component.artifacts.get(InternalArtifactType.JAVAC).get().asFile)
        component.oldVariantApiLegacySupport?.let{
            classesFolders.addAll(it.variantData.allPreJavacGeneratedBytecode.files)
            classesFolders.addAll(it.variantData.allPostJavacGeneratedBytecode.files)
        }
        component.androidResourcesCreationConfig?.compiledRClassArtifact?.get()?.asFile?.let {
            classesFolders.add(it)
        }
        component.getBuiltInKotlincOutput()?.orNull?.asFile?.let { classesFolders.add(it) }

        val generatedClassPaths = addGeneratedClassPaths(component, classesFolders)

        val testInfo: TestInfo? = when(component) {
            is TestVariantCreationConfig, is DeviceTestCreationConfig -> {
                val runtimeApks: Collection<File> = project
                    .configurations
                    .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)?.files
                    ?: listOf()

                DeviceProviderInstrumentTestTask.checkForNonApks(runtimeApks) { message ->
                    variantModel.syncIssueReporter.reportError(IssueReporter.Type.GENERIC, message)
                }

                val testOptionsDsl = extension.testOptions

                val testTaskName = taskContainer.connectedTestTask?.name ?: "".also {
                    variantModel.syncIssueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "unable to find connectedCheck task name for ${component.name}"
                    )
                }

                TestInfoImpl(
                    animationsDisabled = testOptionsDsl.animationsDisabled,
                    execution = testOptionsDsl.execution.convertToExecution(),
                    additionalRuntimeApks = runtimeApks,
                    instrumentedTestTaskName = testTaskName
                )
            }
            else -> null
        }

        val signingConfig = if (component is ApkCreationConfig)
            component.signingConfig else null

        val minSdkVersion =
                ApiVersionImpl(component.minSdk.apiLevel, component.minSdk.codename)
        val targetSdkVersionOverride = when (component) {
            is ApkCreationConfig -> component.targetSdkOverride
            is LibraryCreationConfig -> component.targetSdkOverride
            else -> null
        }?.let { ApiVersionImpl(it.apiLevel, it.codename) }
        val maxSdkVersion =
                if (component is VariantCreationConfig) component.maxSdk else null

        val coreLibDesugaring = (component as? ConsumableCreationConfig)?.isCoreLibraryDesugaringEnabledLintCheck
                ?: false
        val outputsAreSigned = component.oldVariantApiLegacySupport?.variantData?.outputsAreSigned ?: false
        val isSigned = (signingConfig?.hasConfig() ?: false) || outputsAreSigned

        return AndroidArtifactImpl(
            minSdkVersion = minSdkVersion,
            targetSdkVersionOverride = targetSdkVersionOverride,
            maxSdkVersion = maxSdkVersion,

            signingConfigName = signingConfig?.name,
            isSigned = isSigned,

            applicationId = getApplicationId(component),

            abiFilters = (component as? ConsumableCreationConfig)?.nativeBuildCreationConfig?.supportedAbis ?: emptySet(),
            testInfo = testInfo,
            bundleInfo = getBundleInfo(component),
            codeShrinker = CodeShrinker.R8.takeIf {
                component is ConsumableCreationConfig &&
                        component.optimizationCreationConfig.minifiedEnabled
            },

            assembleTaskName = taskContainer.assembleTask.name,
            compileTaskName = taskContainer.compileTask.name,
            sourceGenTaskName = taskContainer.sourceGenTask.name,
            resGenTaskName = if (component.buildFeatures.androidResources) taskContainer.resourceGenTask.name else null,
            ideSetupTaskNames = setOf(taskContainer.sourceGenTask.name),

            generatedSourceFolders = getGeneratedSourceFolders(component),
            generatedResourceFolders = getGeneratedResourceFolders(component),
            classesFolders = classesFolders,
            assembleTaskOutputListingFile = if (component.componentType.isApk)
                component.artifacts.get(InternalArtifactType.APK_IDE_REDIRECT_FILE).get().asFile
            else
                null,
            privacySandboxSdkInfo = createPrivacySandboxSdkInfo(component),
            desugaredMethodsFiles = getDesugaredMethods(
                component.services,
                coreLibDesugaring,
                component.minSdk,
                component.global
            ).files.toList(),
            generatedClassPaths = generatedClassPaths,
            bytecodeTransformations = getBytecodeTransformations(component),
            generatedAssetsFolders = getGeneratedAssetsFolders(component),
        )
    }

    private fun getApplicationId(component: ComponentCreationConfig): String? {
        if (!component.componentType.isApk || component.componentType.isDynamicFeature) {
            return null
        }
        return try {
            component.applicationId.orNull ?: ""
        } catch (e: Exception) {
            variantModel.syncIssueReporter.reportWarning(
                    IssueReporter.Type.APPLICATION_ID_MUST_NOT_BE_DYNAMIC,
                    RuntimeException("Failed to read applicationId for ${component.name}.\n" +
                            "Setting the application ID to the output of a task in the variant " +
                            "api is not supported",
                            e))
            ""
        }
    }

    private fun getBytecodeTransformations(component: ComponentCreationConfig): List<BytecodeTransformation> {
        val jacoco = (component as? ApkCreationConfig)?.requiresJacocoTransformation == true
        val classesProject = component.artifacts.forScope(PROJECT).getScopedArtifactsContainer(
            ScopedArtifact.CLASSES
        ).artifactsAltered.get()
        val classesAll = component.artifacts.forScope(ALL).getScopedArtifactsContainer(
            ScopedArtifact.CLASSES
        ).artifactsAltered.get()

        val asmProject =
            component.instrumentationCreationConfig?.projectClassesAreInstrumented == true
        val asmAll =
            component.instrumentationCreationConfig?.dependenciesClassesAreInstrumented == true
        return listOfNotNull(
            BytecodeTransformation.JACOCO_INSTRUMENTATION.takeIf { jacoco },
            BytecodeTransformation.MODIFIES_PROJECT_CLASS_FILES.takeIf { classesProject },
            BytecodeTransformation.MODIFIES_ALL_CLASS_FILES.takeIf { classesAll },
            BytecodeTransformation.ASM_API_PROJECT.takeIf { asmProject },
            BytecodeTransformation.ASM_API_ALL.takeIf { asmAll },
        )
    }

    private fun createJavaArtifact(component: ComponentCreationConfig): JavaArtifact {
        val taskContainer: MutableTaskContainer = component.taskContainer

        // FIXME need to find a better way for this. We should be using PROJECT_CLASSES_DIRS.
        // The class folders. This is supposed to be the output of the compilation steps + other
        // steps that create bytecode
        val classesFolders = mutableSetOf<File>()
        classesFolders.add(component.artifacts.get(InternalArtifactType.JAVAC).get().asFile)
        component.oldVariantApiLegacySupport?.let {
            classesFolders.addAll(it.variantData.allPreJavacGeneratedBytecode.files)
            classesFolders.addAll(it.variantData.allPostJavacGeneratedBytecode.files)
        }
        component.getBuiltInKotlincOutput()?.orNull?.asFile?.let { classesFolders.add(it) }
        // The separately compile R class, if applicable.
        if (extension.testOptions.unitTests.isIncludeAndroidResources ||
            component.componentType.isForScreenshotPreview) {
            classesFolders.add(component.artifacts.get(UNIT_TEST_CONFIG_DIRECTORY).get().asFile)
        }
        // TODO(b/111168382): When namespaced resources is on, then the provider returns null, so let's skip for now and revisit later
        if (!extension.androidResources.namespaced) {
            component.androidResourcesCreationConfig?.compiledRClassArtifact?.get()?.asFile?.let {
                classesFolders.add(it)
            }
        }

        val generatedClassPaths = addGeneratedClassPaths(component, classesFolders)

        return JavaArtifactImpl(
            assembleTaskName = taskContainer.assembleTask.name,
            compileTaskName = taskContainer.compileTask.name,
            ideSetupTaskNames = setOf(component.global.taskNames.createMockableJar),

            classesFolders = classesFolders,
            generatedSourceFolders = getGeneratedSourceFoldersForUnitTests(component),
            runtimeResourceFolder =
                component.oldVariantApiLegacySupport!!.variantData.javaResourcesForUnitTesting,

            mockablePlatformJar = variantModel.mockableJarArtifact.files.singleOrNull(),
            generatedClassPaths = generatedClassPaths,
            bytecodeTransformations = getBytecodeTransformations(component),
        )
    }

    private fun createDependencies(
        component: ComponentCreationConfig,
        libraryService: LibraryService,
        dontBuildRuntimeClasspath: Boolean,
    ) = getGraphBuilder(dontBuildRuntimeClasspath, component, libraryService).build()

    private fun createDependenciesWithAdjacencyList(
        component: ComponentCreationConfig,
        libraryService: LibraryService,
        graphEdgeCache: GraphEdgeCache,
        dontBuildRuntimeClasspath: Boolean
    ): ArtifactDependenciesAdjacencyList = getGraphBuilder(
        dontBuildRuntimeClasspath,
        component,
        libraryService,
        graphEdgeCache
    ).buildWithAdjacencyList()

    private fun getGraphBuilder(
        dontBuildRuntimeClasspath: Boolean,
        component: ComponentCreationConfig,
        libraryService: LibraryService,
        graphEdgeCache: GraphEdgeCache? = null,
    ) = FullDependencyGraphBuilder(
        { configType, root ->  getArtifactsForModelBuilder(component, configType, root) },
        project.path,
        component.variantDependencies,
        libraryService,
        graphEdgeCache,
        component.services.projectOptions.get(BooleanOption.ADDITIONAL_ARTIFACTS_IN_MODEL),
        dontBuildRuntimeClasspath
    )

    private fun getBundleInfo(
        component: ComponentCreationConfig
    ): BundleInfo? {
        if (!component.componentType.isBaseModule) {
            return null
        }

        // TODO(b/111168382): Remove when bundle can build apps with namespaced turned on.
        if (extension.androidResources.namespaced) {
            return null
        }

        // FIXME need to find a better way for this.
        val taskContainer: MutableTaskContainer = component.taskContainer
        val artifacts = component.artifacts

        return BundleInfoImpl(
            bundleTaskName = taskContainer.bundleTask?.name ?: error("failed to find bundle task name for ${component.name}"),
            bundleTaskOutputListingFile = artifacts.get(InternalArtifactType.BUNDLE_IDE_REDIRECT_FILE).get().asFile,
            apkFromBundleTaskName = AnchorTaskNames.getExtractApksAnchorTaskName(component),
            apkFromBundleTaskOutputListingFile = artifacts.get(InternalArtifactType.APK_FROM_BUNDLE_IDE_REDIRECT_FILE).get().asFile
        )
    }

    // FIXME this is coming from the v1 Model Builder and this needs to be rethought. b/160970116
    private fun inspectManifestForInstantTag(
        component: ComponentCreationConfig,
        instantAppResultMap: MutableMap<File, Boolean>
    ): Boolean {
        if (!component.componentType.isBaseModule && !component.componentType.isDynamicFeature) {
            return false
        }

        // get the manifest in descending order of priority. First one to return
        val manifests = mutableListOf<File>()
        // add the static overlays, we cannot at this time process the generated ones since they
        // depends on tasks execution. This means that if the user is setting the instant app
        // flag in the generated manifest only, then we will not see it.
        // The solution to this problem (and cleaning up this rather ugly code is to introduce
        // a specific dsl flag instead of poking the manifests at model building, see  b/160970116
        val manifestSources = component.sources.manifests as ManifestFilesImpl
        manifests.addAll(manifestSources.allStatic.map {
            // `allStatic` is ordered from most prioritized to less prioritizes (main manifest)
            // need to remove main manifest and reverse order from less to the most prioritized
            files -> files.dropLast(1).reversed().map { it.asFile }
        }.get().filter { it.isFile }
        )

        val mainManifest = component.sources.manifestFile
        if (mainManifest.isFile) {
            manifests.add(mainManifest)
        }

        if (manifests.isEmpty()) {
            return false
        }
        for (manifest in manifests) {
            // check if the manifest was already parsed. If so, just pull the information
            // from the map
            val parseResult = instantAppResultMap[manifest]
            if (parseResult != null) {
                if (parseResult) {
                    return true
                }
                continue
            }

            try {
                FileInputStream(manifest).use { inputStream ->
                    val factory = XMLInputFactory.newInstance()
                    val eventReader = factory.createXMLEventReader(inputStream)
                    while (eventReader.hasNext() && !eventReader.peek().isEndDocument) {
                        val event = eventReader.nextTag()
                        if (event.isStartElement) {
                            val startElement = event.asStartElement()
                            if (startElement.name.namespaceURI == SdkConstants.DIST_URI
                                && startElement.name.localPart.equals("module", ignoreCase = true)
                            ) {
                                val instant = startElement.getAttributeByName(
                                    QName(SdkConstants.DIST_URI, "instant")
                                )
                                if (instant != null
                                    && (
                                            instant.value == SdkConstants.VALUE_TRUE
                                                    || instant.value == SdkConstants.VALUE_1)
                                ) {
                                    eventReader.close()
                                    instantAppResultMap[manifest] = true
                                    return true
                                }
                            }
                        } else if (event.isEndElement
                            && (event as EndElement).name.localPart.equals("manifest", ignoreCase = true)
                        ) {
                            break
                        }
                    }
                    eventReader.close()
                }
            } catch (e: XMLStreamException) {
                variantModel.syncIssueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    """
                        Failed to parse XML in ${manifest.path}
                        ${e.message}
                        """.trimIndent()
                )
            } catch (e: IOException) {
                variantModel.syncIssueReporter.reportError(
                    IssueReporter.Type.GENERIC,
                    """
                        Failed to parse XML in ${manifest.path}
                        ${e.message}
                        """.trimIndent()
                )
            } finally {
                // check that we have not yet put a true in there
                instantAppResultMap.putIfAbsent(manifest, false)
            }
        }
        return false
    }

    private fun getTestTargetVariant(
        component: ComponentCreationConfig
    ): TestedTargetVariant? {
        if (extension is TestExtension) {
            val targetPath = extension.targetProjectPath ?: return null

            // to get the target variant we need to get the result of the dependency resolution
            val apkArtifacts = component
                .variantDependencies
                .getArtifactCollection(
                    PROVIDED_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.APK
                )

            // while there should be a single result, the list may be empty if the variant
            // matching is broken
            if (apkArtifacts.artifacts.size == 1) {
                val result = apkArtifacts.artifacts.single()
                // if the name of the variant is missing, then just return null, but this
                // should not happen
                val variantName = result.getVariantName() ?: return null
                return TestedTargetVariantImpl(targetPath, variantName)

            } else if (!apkArtifacts.failures.isEmpty()) {
                // probably there was an error...
                DependencyFailureHandler()
                    .addErrors(
                        "${project.path}@${component.name}/testTarget",
                        apkArtifacts.failures
                    )
                    .registerIssues(variantModel.syncIssueReporter)
            }
        }
        return null
    }

    private fun addGeneratedClassPaths(
        component: ComponentCreationConfig,
        classesFolders: MutableSet<File>
    ): Map<String, File> {
        val generatedClassPaths = mutableMapOf<String, File>()

        val buildConfigJar = component.artifacts.get(InternalArtifactType.COMPILE_BUILD_CONFIG_JAR)
        if (buildConfigJar.isPresent) {
            classesFolders.add(buildConfigJar.get().asFile)
            generatedClassPaths["buildConfigGeneratedClasses"] = buildConfigJar.get().asFile
        }

        val kaptClasses = component.getBuiltInKaptArtifact(InternalArtifactType.BUILT_IN_KAPT_CLASSES_DIR)
        if (kaptClasses != null) {
            classesFolders.add(kaptClasses.get().asFile)
            generatedClassPaths["kaptGeneratedClasses"] = kaptClasses.get().asFile
        }

        return generatedClassPaths
    }

    companion object {
        internal fun getAgpFlags(
            variants: List<VariantCreationConfig>,
            projectOptions: ProjectOptions
        ): AndroidGradlePluginProjectFlagsImpl {
            val flags =
                ImmutableMap.builder<BooleanFlag, Boolean>()

            val finalResIds = !projectOptions[BooleanOption.USE_NON_FINAL_RES_IDS]

            flags.put(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS, finalResIds)
            flags.put(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS, finalResIds)
            flags.put(
                BooleanFlag.JETPACK_COMPOSE,
                variants.any { it.buildFeatures.compose }
            )
            flags.put(
                BooleanFlag.ML_MODEL_BINDING,
                variants.any { it.buildFeatures.mlModelBinding }
            )
            flags.put(
                BooleanFlag.TRANSITIVE_R_CLASS,
                !projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
            )
            flags.put(
                BooleanFlag.UNIFIED_TEST_PLATFORM,
                projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM]
            )
            flags.put(
                BooleanFlag.USE_ANDROID_X,
                projectOptions[BooleanOption.USE_ANDROID_X]
            )
            flags.put(
                BooleanFlag.BUILD_FEATURE_ANDROID_RESOURCES,
                variants.any { it.buildFeatures.androidResources }
            )
            flags.put(
                BooleanFlag.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS,
                projectOptions[BooleanOption.EXCLUDE_LIBRARY_COMPONENTS_FROM_CONSTRAINTS]
            )
            flags.put(
                BooleanFlag.DATA_BINDING_ENABLED,
                variants.any { it.buildFeatures.dataBinding }
            )

            return AndroidGradlePluginProjectFlagsImpl(flags.build())
        }
    }
}
