/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins

import com.android.SdkConstants
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SettingsExtension
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.build.gradle.internal.ApiObjectFactory
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.ClasspathVerifier.checkClasspathSanity
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.SdkLocator.sdkTestDirectory
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.VariantTaskManager
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.AndroidTestOptionsDslInfoImpl
import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.crash.runAction
import com.android.build.gradle.internal.dependency.CONFIG_NAME_ANDROID_JDK_IMAGE
import com.android.build.gradle.internal.dependency.JacocoInstrumentationService
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.CommonExtensionImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.IncompatibleProjectOptionsReporter
import com.android.build.gradle.internal.getManagedDeviceAvdFolder
import com.android.build.gradle.internal.getSdkDir
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.ide.v2.NativeModelBuilder
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.projectIsolationActive
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfigImpl
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry
import com.android.build.gradle.internal.utils.enforceMinimumVersionsOfPlugins
import com.android.build.gradle.internal.utils.getKotlinAndroidPluginVersion
import com.android.build.gradle.internal.utils.syncAgpAndKgpSources
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.build.gradle.internal.variant.VariantInputModel
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter.Type
import com.android.builder.model.v2.ide.ProjectType
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions.checkState
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import java.io.File
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Base class for all Android plugins */
abstract class BasePlugin<
                BuildFeaturesT: BuildFeatures,
                BuildTypeT: com.android.build.api.dsl.BuildType,
                DefaultConfigT: com.android.build.api.dsl.DefaultConfig,
                ProductFlavorT: com.android.build.api.dsl.ProductFlavor,
                AndroidResourcesT: com.android.build.api.dsl.AndroidResources,
                InstallationT: com.android.build.api.dsl.Installation,
                AndroidT: CommonExtension<
                        BuildFeaturesT,
                        BuildTypeT,
                        DefaultConfigT,
                        ProductFlavorT,
                        AndroidResourcesT,
                        InstallationT>,
                AndroidComponentsT:
                        AndroidComponentsExtension<
                                in AndroidT,
                                in VariantBuilderT,
                                in VariantT>,
                VariantBuilderT: VariantBuilder,
                VariantDslInfoT: VariantDslInfo,
                CreationConfigT: VariantCreationConfig,
                VariantT: Variant>(
    val registry: ToolingModelBuilderRegistry,
    val componentFactory: SoftwareComponentFactory,
    listenerRegistry: BuildEventsListenerRegistry,
    private val gradleBuildFeatures: org.gradle.api.configuration.BuildFeatures,
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    init {
        checkClasspathSanity()
    }

    protected class ExtensionData<
            BuildFeaturesT: BuildFeatures,
            BuildTypeT: com.android.build.api.dsl.BuildType,
            DefaultConfigT: com.android.build.api.dsl.DefaultConfig,
            ProductFlavorT: com.android.build.api.dsl.ProductFlavor,
            AndroidResourcesT: com.android.build.api.dsl.AndroidResources,
            InstallationT: com.android.build.api.dsl.Installation,
            AndroidT: CommonExtension<
                    out BuildFeaturesT,
                    out BuildTypeT,
                    out DefaultConfigT,
                    out ProductFlavorT,
                    out AndroidResourcesT,
                    out InstallationT>>(
        val oldExtension: BaseExtension,
        val newExtension: AndroidT,
        val bootClasspathConfig: BootClasspathConfigImpl,
    )

    @Suppress("DEPRECATION")
    private val buildOutputs by lazy {
        withProject("buildOutputs") {
            it.container(
                com.android.build.gradle.api.BaseVariantOutput::class.java
            )
        }
    }

    private val extensionData by lazy {
        createExtension(
            dslServices,
            variantInputModel,
            buildOutputs,
            extraModelInfo,
            versionedSdkLoaderService
        )
    }

    // TODO: BaseExtension should be changed into AndroidT
    @Deprecated("use newExtension")
    val extension: BaseExtension by lazy { extensionData.oldExtension }
    private val newExtension: AndroidT by lazy { extensionData.newExtension }

    private val variantApiOperations by lazy {
        VariantApiOperationsRegistrar<AndroidT, VariantBuilderT, VariantT>(
            extensionData.newExtension
        )
    }

    val managedDeviceRegistry: ManagedDeviceRegistry by lazy(LazyThreadSafetyMode.NONE) {
        ManagedDeviceRegistry(AndroidTestOptionsDslInfoImpl((newExtension as CommonExtensionImpl<*, *, *, *, *, *>)))
    }

    private val globalConfig by lazy {
        withProject("globalConfig") { project ->
            @Suppress("DEPRECATION")
            GlobalTaskCreationConfigImpl(
                project,
                extension,
                (newExtension as CommonExtensionImpl<*, *, *, *, *, *>),
                dslServices,
                versionedSdkLoaderService,
                bootClasspathConfig,
                createCustomLintPublishConfig(project),
                createCustomLintChecksConfig(project),
                createAndroidJarConfig(project),
                createFakeDependencyConfig(project),
                createSettingsOptions(dslServices),
                managedDeviceRegistry
            )
        }
    }


    @get:VisibleForTesting
    val variantManager: VariantManager<AndroidT, VariantBuilderT, VariantDslInfoT, CreationConfigT> by lazy {
        withProject("variantManager") { project ->
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            VariantManager(
                project,
                dslServices,
                extension,
                newExtension,
                variantApiOperations as VariantApiOperationsRegistrar<AndroidT, VariantBuilder, Variant>,
                variantFactory,
                variantInputModel,
                globalConfig,
                projectServices
            )
        }
    }


    @get:VisibleForTesting
    val variantInputModel: LegacyVariantInputManager by lazy {
        withProject("LegacyVariantInputManager") { project ->
            LegacyVariantInputManager(
            dslServices,
            variantFactory.componentType,
            SourceSetManager(
                project,
                isPackagePublished(),
                dslServices,
                DelayedActionsExecutor()
            ))
        }
    }

    private val sdkComponentsBuildService by lazy {
        withProject("sdkComponentsBuildService") { project ->
            SdkComponentsBuildService.RegistrationAction(project, projectServices.projectOptions)
                .execute()
        }
    }

    protected val dslServices: DslServicesImpl by lazy {
        DslServicesImpl(
            projectServices,
            sdkComponentsBuildService,
            getProjectTypeV2()
        ) {
            versionedSdkLoaderService
        }
    }

    private val taskManagerConfig: TaskManagerConfig by lazy {
        TaskManagerConfigImpl(dslServices, componentFactory)
    }

    protected val versionedSdkLoaderService: VersionedSdkLoaderService by lazy {
        withProject("versionedSdkLoaderService") { project ->
                VersionedSdkLoaderService(
                    dslServices,
                    project,
                    {
                        @Suppress("DEPRECATION")
                        extension.compileSdkVersion
                    },
                    {
                        @Suppress("DEPRECATION")
                        extension.buildToolsRevision
                    },
                )
            }
        }

    private val bootClasspathConfig: BootClasspathConfigImpl by lazy {
        extensionData.bootClasspathConfig
    }

    private val variantFactory: VariantFactory<VariantBuilderT, VariantDslInfoT, CreationConfigT> by lazy {
        createVariantFactory()
    }

    protected val extraModelInfo: ExtraModelInfo = ExtraModelInfo()

    private val hasCreatedTasks = AtomicBoolean(false)

    protected abstract fun createExtension(
        dslServices: DslServices,
        dslContainers: DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        @Suppress("DEPRECATION")
        buildOutputs: NamedDomainObjectContainer<com.android.build.gradle.api.BaseVariantOutput>,
        extraModelInfo: ExtraModelInfo,
        versionedSdkLoaderService: VersionedSdkLoaderService
    ): ExtensionData<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidResourcesT, InstallationT, AndroidT>

    protected abstract fun createComponentExtension(
        dslServices: DslServices,
        variantApiOperationsRegistrar: VariantApiOperationsRegistrar<AndroidT, VariantBuilderT, VariantT>,
        bootClasspathConfig: BootClasspathConfig
    ): AndroidComponentsT

    abstract override fun getAnalyticsPluginType(): GradleBuildProject.PluginType

    protected abstract fun createVariantFactory(): VariantFactory<VariantBuilderT, VariantDslInfoT, CreationConfigT>

    protected abstract fun createTaskManager(
        project: Project,
        variants: Collection<ComponentInfo<VariantBuilderT, CreationConfigT>>,
        testComponents: Collection<TestComponentCreationConfig>,
        testFixturesComponents: Collection<TestFixturesCreationConfig>,
        globalTaskCreationConfig: GlobalTaskCreationConfig,
        localConfig: TaskManagerConfig,
        extension: BaseExtension,
    ): VariantTaskManager<VariantBuilderT, CreationConfigT>

    protected abstract fun getProjectType(): Int

    /** The project type of the IDE model v2. */
    protected abstract fun getProjectTypeV2(): ProjectType

    override fun apply(project: Project) {
        runAction {
            basePluginApply(project, gradleBuildFeatures)
            pluginSpecificApply(project)
            project.pluginManager.apply(AndroidBasePlugin::class.java)
        }
    }

    protected abstract fun pluginSpecificApply(project: Project)

    override fun configureProject(project: Project) {
        val gradle = project.gradle

        val stringCachingService: Provider<StringCachingBuildService> =
            StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()

        LibraryDependencyCacheBuildService.RegistrationAction(
                project, mavenCoordinatesCacheBuildService
        ).execute()

        GlobalSyncService.RegistrationAction(project, mavenCoordinatesCacheBuildService)
            .execute()

        val projectOptions = projectServices.projectOptions
        val issueReporter = projectServices.issueReporter

        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        val locationsProvider = getBuildService(
            project.gradle.sharedServices,
            AndroidLocationsBuildService::class.java,
        )

        AvdComponentsBuildService.RegistrationAction(
            project,
            projectOptions,
            getManagedDeviceAvdFolder(
                project.objects,
                project.providers,
                locationsProvider.get()
            ),
            sdkComponentsBuildService,
            project.providers.provider {
                @Suppress("DEPRECATION")
                extension.compileSdkVersion
            },
            project.providers.provider {
                @Suppress("DEPRECATION")
                extension.buildToolsRevision
            },
        ).execute()

        SymbolTableBuildService.RegistrationAction(project).execute()
        ClassesHierarchyBuildService.RegistrationAction(project).execute()
        LintFixBuildService.RegistrationAction(project).execute()
        LintClassLoaderBuildService.RegistrationAction(project).execute()
        JacocoInstrumentationService.RegistrationAction(project).execute()

        FakeDependencyJarBuildService.RegistrationAction(project).execute()

        projectOptions
            .allOptions
            .forEach(projectServices.deprecationReporter::reportOptionIssuesIfAny)
        IncompatibleProjectOptionsReporter.check(projectOptions, issueReporter)

        // TODO(b/189990965) Re-enable checking minimum versions of certain plugins once
        // https://github.com/gradle/gradle/issues/23838 is fixed
        if (!gradleBuildFeatures.projectIsolationActive()) {
            enforceMinimumVersionsOfPlugins(project, issueReporter)
        }

        // Apply the Java plugin
        project.plugins.apply(JavaBasePlugin::class.java)

        project.tasks
            .named("assemble")
            .configure { task ->
                task.description = "Assembles all variants of all applications and secondary packages."
            }

        // As soon as project is evaluated we can clear the shared state for deprecation reporting.
        gradle.projectsEvaluated { DeprecationReporterImpl.clean() }

        createAndroidJdkImageConfiguration(project)
    }

    /** Creates the androidJdkImage configuration */
    private fun createAndroidJdkImageConfiguration(project: Project) {
        val config = project.configurations.create(CONFIG_NAME_ANDROID_JDK_IMAGE)
        config.isVisible = false
        config.isCanBeConsumed = false
        config.description = "Configuration providing JDK image for compiling Java 9+ sources"

        project.dependencies
            .add(
                CONFIG_NAME_ANDROID_JDK_IMAGE,
                project.files(
                    versionedSdkLoaderService
                        .versionedSdkLoader
                        .flatMap { it.coreForSystemModulesProvider }
                )
            )
    }

    companion object {
        fun createCustomLintChecksConfig(project: Project): Configuration {
            val lintChecks = project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_LINTCHECKS)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to apply external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        private fun createCustomLintPublishConfig(project: Project): Configuration {
            val lintChecks = project.configurations
                .maybeCreate(VariantDependencies.CONFIG_NAME_LINTPUBLISH)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to publish external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        fun createAndroidJarConfig(project: Project): Configuration  {
            val androidJarConfig: Configuration = project.configurations
                .maybeCreate(VariantDependencies.CONFIG_NAME_ANDROID_APIS)
            androidJarConfig.description = "Configuration providing various types of Android JAR file"
            androidJarConfig.isCanBeConsumed = false
            return androidJarConfig
        }
        private fun createFakeDependencyConfig(project: Project): Configuration {
            val fakeJarService = getBuildService(
                project.gradle.sharedServices,
                FakeDependencyJarBuildService::class.java,
            ).get()

            val fakeDependency = project.dependencies.create(project.files(fakeJarService.lazyCachedFakeJar))
            val configuration = project.configurations.detachedConfiguration(fakeDependency)
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            return configuration
        }

        // Create the "special" configuration for test buddy APKs. It will be resolved by the test
        // running task, so that we can install all the found APKs before running tests.
        internal fun createAndroidTestUtilConfiguration(project: Project) {
            project.logger
                .debug(
                    "Creating configuration "
                            + SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION
                )
            val configuration = project.configurations
                .maybeCreate(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION)
            configuration.isVisible = false
            configuration.description = "Additional APKs used during instrumentation testing."
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
        }
    }

    override fun configureExtension(project: Project) {
        // Create components extension
        createComponentExtension(
            dslServices,
            variantApiOperations,
            bootClasspathConfig
        )
        project.extensions.add("buildOutputs", buildOutputs)
        registerModels(
            project,
            registry,
            variantInputModel,
            extensionData,
            extraModelInfo,
            globalConfig)

        // create default Objects, signingConfig first as it's used by the BuildTypes.
        variantFactory.createDefaultComponents(variantInputModel)
        createAndroidTestUtilConfiguration(project)
    }

    protected open fun registerModels(
        project: Project,
        registry: ToolingModelBuilderRegistry,
        variantInputModel: VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
        extensionData: ExtensionData<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidResourcesT, InstallationT, AndroidT>,
        extraModelInfo: ExtraModelInfo,
        globalConfig: GlobalTaskCreationConfig
    ) {
        // Register a builder for the custom tooling model
        val variantModel: VariantModel = createVariantModel(globalConfig)
        registry.register(
            com.android.build.gradle.internal.ide.v2.ModelBuilder(
                project, variantModel, extensionData.newExtension
            )
        )

        // Register a builder for the native tooling model
        val nativeModelBuilderV2 = NativeModelBuilder(
            project,
            projectServices.issueReporter,
            projectServices.projectOptions,
            variantModel
        )
        registry.register(nativeModelBuilderV2)
    }

    private fun createVariantModel(globalConfig: GlobalTaskCreationConfig): VariantModel  {
        return VariantModelImpl(
            variantInputModel as VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>,
            {
                @Suppress("DEPRECATION")
                extension.testBuildType
            },
            { variantManager.mainComponents.map { it.variant } },
            { variantManager.testComponents },
            { variantManager.buildFeatureValues },
            getProjectType(),
            getProjectTypeV2(),
            globalConfig)
    }

    override fun createTasks(project: Project) {
        configuratorService.recordBlock(
            ExecutionType.TASK_MANAGER_CREATE_TASKS,
            project.path,
            null
        ) {
            @Suppress("DEPRECATION")
            TaskManager.createTasksBeforeEvaluate(
                project,
                variantFactory.componentType,
                extension.sourceSets,
                variantManager.globalTaskCreationConfig
            )
        }

        project.afterEvaluate(
            afterEvaluate {
                variantInputModel.sourceSetManager.runBuildableArtifactsActions()

                configuratorService.recordBlock(
                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                    project.path,
                    null
                ) {
                    createAndroidTasks(project)
                }
            }
        )
    }

    @Suppress("DEPRECATION")
    @VisibleForTesting
    fun createAndroidTasks(project: Project) {
        val globalConfig = variantManager.globalTaskCreationConfig
        if (hasCreatedTasks.get()) {
            return
        }
        hasCreatedTasks.set(true)

        variantManager.variantApiOperationsRegistrar.executeDslFinalizationBlocks()

        (globalConfig.compileOptions as CompileOptions)
            .finalizeSourceAndTargetCompatibility(project, globalConfig)

        if (extension.compileSdkVersion == null) {
            if (SyncOptions.getModelQueryMode(projectServices.projectOptions)
                == SyncOptions.EvaluationMode.IDE
            ) {
                val newCompileSdkVersion: String = findHighestSdkInstalled()
                    ?: ("android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
                extension.compileSdkVersion = newCompileSdkVersion
            }
            dslServices
                .issueReporter
                .reportError(
                    Type.COMPILE_SDK_VERSION_NOT_SET,
                    "compileSdkVersion is not specified. Please add it to build.gradle"
                )
        }

        // Make sure unit tests set the required fields.
        checkState(extension.compileSdkVersion != null, "compileSdkVersion is not specified.")

        // get current plugins and look for the default Java plugin.
        if (project.plugins.hasPlugin(JavaPlugin::class.java)) {
            throw BadPluginException(
                "The 'java' plugin has been applied, but it is not compatible with the Android plugins."
            )
        }
        if (project.plugins.hasPlugin("me.tatarka.retrolambda")) {
            val warningMsg =
                """One of the plugins you are using supports Java 8 language features. To try the support built into the Android plugin, remove the following from your build.gradle:
    apply plugin: 'me.tatarka.retrolambda'
To learn more, go to https://d.android.com/r/tools/java-8-support-message.html
"""
            dslServices.issueReporter.reportWarning(Type.GENERIC, warningMsg)
        }
        project.repositories
            .forEach(
                Consumer { artifactRepository: ArtifactRepository ->
                    if (artifactRepository is FlatDirectoryArtifactRepository) {
                        val warningMsg = String.format(
                            "Using %s should be avoided because it doesn't support any meta-data formats.",
                            artifactRepository.getName()
                        )
                        dslServices
                            .issueReporter
                            .reportWarning(Type.GENERIC, warningMsg)
                    }
                })

        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if ((!project.state.executed || project.state.failure != null)
            && sdkTestDirectory == null
        ) {
            return
        }
        variantInputModel.lock()
        extension.disableWrite()

        @Suppress("DEPRECATION")
        syncAgpAndKgpSources(project, extension.sourceSets)

        val projectBuilder = configuratorService.getProjectBuilder(
            project.path
        )
        if (projectBuilder != null) {
            projectBuilder
                .setCompileSdk(extension.compileSdkVersion)
                .setBuildToolsVersion(extension.buildToolsRevision.toString()).splits =
                AnalyticsUtil.toProto(extension.splits)
            getKotlinAndroidPluginVersion(project)?.let {
                projectBuilder.kotlinPluginVersion = it
            }
        }
        AnalyticsUtil.recordFirebasePerformancePluginVersion(project)

        // create the build feature object that will be re-used everywhere
        val buildFeatureValues = variantFactory.createBuildFeatureValues(
            extension.buildFeatures, projectServices.projectOptions
        )

        // create all registered custom source sets from the user on each AndroidSourceSet
        variantManager
            .variantApiOperationsRegistrar
            .onEachSourceSetExtensions { name: String ->
                extension
                    .sourceSets
                    .forEach(
                        Consumer { androidSourceSet: com.android.build.gradle.api.AndroidSourceSet? ->
                            if (androidSourceSet is DefaultAndroidSourceSet) {
                                androidSourceSet.extras.create(name)
                            }
                        })
            }
        variantManager.createVariants(buildFeatureValues)
        val variants = variantManager.mainComponents
        projectBuilder?.let { builder ->
            variants.forEach { variant ->
                variant.variant.experimentalProperties.get().keys.forEach { modulePropertyKey ->
                    ModulePropertyKey.OptionalString[modulePropertyKey]?.name?.let {
                        AnalyticsUtil.toProto(it).number
                    }?.let { builder.optionsBuilder.addModulePropertyKeys(it) }
                    ModulePropertyKey.BooleanWithDefault[modulePropertyKey]?.name?.let {
                        AnalyticsUtil.toProto(it).number
                    }?.let { builder.optionsBuilder.addModulePropertyKeys(it) }
                    ModulePropertyKey.Dependencies[modulePropertyKey]?.name?.let {
                        AnalyticsUtil.toProto(it).number
                    }?.let { builder.optionsBuilder.addModulePropertyKeys(it) }
                    ModulePropertyKey.OptionalBoolean[modulePropertyKey]?.name?.let {
                        AnalyticsUtil.toProto(it).number
                    }?.let { builder.optionsBuilder.addModulePropertyKeys(it) }
                }
            }
        }
        val taskManager = createTaskManager(
            project,
            variants,
            variantManager.testComponents,
            variantManager.testFixturesComponents,
            globalConfig,
            taskManagerConfig,
            extension
        )
        taskManager.createTasks(variantFactory.componentType, createVariantModel(globalConfig))
        val anyVariantSupportsSdkConsumption =
                variants.any { it.variant.privacySandboxCreationConfig != null }
        DependencyConfigurator(
            project,
            projectServices
        )
            .configureDependencySubstitutions()
            .configureDependencyChecks()
            .configureGeneralTransforms(globalConfig.namespacedAndroidResources, globalConfig.aarOrJarTypeToConsume)
            .configureVariantTransforms(
                variants.map { it.variant },
                variantManager.nestedComponents,
                globalConfig
            )
            .configureAttributeMatchingStrategies(variantInputModel, anyVariantSupportsSdkConsumption)
            .configureCalculateStackFramesTransforms(globalConfig)
                .apply {
                    // Registering Jacoco transforms causes the jacoco configuration to be created.
                    // Ensure there is at least one variant with enableAndroidTestCoverage
                    // enabled before registering the transforms.
                    if (variants.any { it.variant.isAndroidTestCoverageEnabled }) {
                        configureJacocoTransforms()
                    }
                }
                .apply {
                    // Registering privacy sandbox transforms creates various detatched
                    // configurations for tools it uses. Only register them if privacy sandbox
                    // consumption is enabled.
                    if (anyVariantSupportsSdkConsumption) {
                        configurePrivacySandboxSdkConsumerTransforms()
                        configurePrivacySandboxSdkVariantTransforms(
                                variants.map { it.variant },
                                globalConfig.compileSdkHashString,
                                globalConfig.buildToolsRevision,
                                globalConfig
                        )
                    }
                }


        // Run the old Variant API, after the variants and tasks have been created.
        @Suppress("DEPRECATION")
        val apiObjectFactory = ApiObjectFactory(extension, variantFactory, dslServices)
        for (variant in variants) {
            apiObjectFactory.create(variant.variant)
            variant.variant.oldVariantApiLegacySupport?.oldVariantApiCompleted()
        }

        // lock the Properties of the variant API after the old API because
        // of the versionCode/versionName properties that are shared between the old and new APIs.
        variantManager.lockVariantProperties()

        // Make sure no SourceSets were added through the DSL without being properly configured
        variantInputModel.sourceSetManager.checkForUnconfiguredSourceSets()

        // configure compose related tasks.
        taskManager.createPostApiTasks()

        // now publish all variant artifacts for non test variants since
        // tests don't publish anything.
        for (component in variants) {
            component.variant.publishBuildArtifacts()
        }

        // now publish all testFixtures components artifacts.
        for (testFixturesComponent in variantManager.testFixturesComponents) {
            testFixturesComponent.publishBuildArtifacts()
        }
        checkSplitConfiguration()
        variantManager.setHasCreatedTasks(true)
        variantManager.finalizeAllVariants()
    }

    private fun findHighestSdkInstalled(): String? {
        var highestSdk: String? = null
        val folder = withProject("findHighestSdkInstalled") { project ->
            File(getSdkDir(project.rootDir, syncIssueReporter, project.providers), "platforms")
        }
        val listOfFiles = folder.listFiles()
        if (listOfFiles != null) {
            Arrays.sort(listOfFiles, Comparator.comparing { obj: File -> obj.name }
                .reversed())
            for (file in listOfFiles) {
                if (AndroidTargetHash.getPlatformVersion(file.name) != null) {
                    highestSdk = file.name
                    break
                }
            }
        }
        return highestSdk
    }

    private fun checkSplitConfiguration() {
        val configApkUrl = "https://d.android.com/topic/instant-apps/guides/config-splits.html"
        @Suppress("DEPRECATION")
        val generatePureSplits = extension.generatePureSplits
        @Suppress("DEPRECATION")
        val splits = extension.splits

        // The Play Store doesn't allow Pure splits
        if (generatePureSplits) {
            dslServices
                .issueReporter
                .reportWarning(
                    Type.GENERIC,
                    "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false. For more information, go to "
                            + configApkUrl
                )
        }
        if (!generatePureSplits && splits.language.isEnable) {
            dslServices
                .issueReporter
                .reportWarning(
                    Type.GENERIC,
                    "Per-language APKs are supported only when building Android Instant Apps. For more information, go to "
                            + configApkUrl
                )
        }
    }

    /**
     * If overridden in a subclass to return "true," the package Configuration will be named
     * "publish" instead of "apk"
     */
    protected open fun isPackagePublished(): Boolean {
        return false
    }

    // Initialize the android extension with values from the android settings extension
    protected fun initExtensionFromSettings(extension: AndroidT) {
        settingsExtension?.let {
            extension.doInitExtensionFromSettings(it)
        }
    }

    protected open fun AndroidT.doInitExtensionFromSettings(settings: SettingsExtension) {
        settings.compileSdk?.let { compileSdk ->
            this.compileSdk = compileSdk

            settings.compileSdkExtension?.let { compileSdkExtension ->
                this.compileSdkExtension = compileSdkExtension
            }
        }

        settings.compileSdkPreview?.let { compileSdkPreview ->
            this.compileSdkPreview = compileSdkPreview
        }

        settings.minSdk?.let { minSdk ->
            this.defaultConfig.minSdk = minSdk
        }

        settings.minSdkPreview?.let { minSdkPreview ->
            this.defaultConfig.minSdkPreview = minSdkPreview
        }

        settings.ndkVersion.let { ndkVersion ->
            this.ndkVersion = ndkVersion
        }

        settings.ndkPath?.let { ndkPath ->
            this.ndkPath = ndkPath
        }

        settings.buildToolsVersion.let { buildToolsVersion ->
            this.buildToolsVersion = buildToolsVersion
        }
    }
}
