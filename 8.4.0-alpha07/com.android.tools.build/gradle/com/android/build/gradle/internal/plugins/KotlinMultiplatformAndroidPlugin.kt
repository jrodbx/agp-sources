/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants.MAX_SUPPORTED_ANDROID_PLATFORM_VERSION
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.component.analytics.AnalyticsEnabledKotlinMultiplatformAndroidVariant
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.build.api.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.dsl.SettingsExtension
import com.android.build.api.extension.impl.KotlinMultiplatformAndroidComponentsExtensionImpl
import com.android.build.api.extension.impl.MultiplatformVariantApiOperationsRegistrar
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.impl.KmpAndroidCompilationType
import com.android.build.api.variant.impl.KmpVariantImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidCompilationImpl
import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTargetImpl
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.DependencyConfigurator
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.VariantManager.Companion.finalizeAllComponents
import com.android.build.gradle.internal.core.dsl.KmpComponentDslInfo
import com.android.build.gradle.internal.core.dsl.impl.KmpAndroidTestDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.KmpUnitTestDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.KmpVariantDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.features.KmpAndroidTestOptionsDslInfoImpl
import com.android.build.gradle.internal.dependency.AgpVersionCompatibilityRule
import com.android.build.gradle.internal.dependency.JacocoInstrumentationService
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule.Companion.setUp
import com.android.build.gradle.internal.dependency.SingleVariantBuildTypeRule
import com.android.build.gradle.internal.dependency.SingleVariantProductFlavorRule
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.SdkComponentsImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.manifest.LazyManifestParser
import com.android.build.gradle.internal.multiplatform.KotlinMultiplatformAndroidHandler
import com.android.build.gradle.internal.multiplatform.KotlinMultiplatformAndroidHandlerImpl
import com.android.build.gradle.internal.scope.KotlinMultiplatformBuildFeaturesValuesImpl
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.ClassesHierarchyBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.internal.services.VariantServicesImpl
import com.android.build.gradle.internal.services.VersionedSdkLoaderService
import com.android.build.gradle.internal.tasks.KmpTaskManager
import com.android.build.gradle.internal.tasks.SigningConfigUtils.Companion.createSigningOverride
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.KmpGlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry
import com.android.build.gradle.internal.utils.KOTLIN_MPP_PLUGIN_ID
import com.android.build.gradle.internal.utils.validatePreviewTargetValue
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.v2.ide.ProjectType
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import javax.inject.Inject
import org.jetbrains.kotlin.gradle.plugin.mpp.external.publishSources

@Incubating
class KotlinMultiplatformAndroidPlugin @Inject constructor(
    listenerRegistry: BuildEventsListenerRegistry,
    private val buildFeatures: BuildFeatures,
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    private lateinit var global: GlobalTaskCreationConfig
    private lateinit var androidExtension: KotlinMultiplatformAndroidExtensionImpl
    private lateinit var kotlinMultiplatformAndroidComponentsExtension: KotlinMultiplatformAndroidComponentsExtension
    private lateinit var kmpVariantApiOperationsRegistrar: MultiplatformVariantApiOperationsRegistrar

    private lateinit var mainVariant: KmpVariantImpl

    private val dslServices by lazy {
        withProject("dslServices") { project ->
            val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
                SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
                ).execute()

            DslServicesImpl(
                projectServices,
                sdkComponentsBuildService,
                ProjectType.LIBRARY
            )
        }
    }

    private val kotlinMultiplatformHandler: KotlinMultiplatformAndroidHandler by lazy(LazyThreadSafetyMode.NONE) {
        withProject("kotlinMultiplatformHandler") { project ->
            KotlinMultiplatformAndroidHandlerImpl(
                project = project,
                dslServices = dslServices,
                objectFactory = projectServices.objectFactory
            )
        }
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType? =
        GradleBuildProject.PluginType.KOTLIN_MULTIPLATFORM_ANDROID_LIBRARY

    override fun apply(project: Project) {
        super.basePluginApply(project, buildFeatures)
    }

    override fun configureProject(project: Project) {
        FakeDependencyJarBuildService.RegistrationAction(project).execute()
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectServices.projectOptions).execute()
        ClassesHierarchyBuildService.RegistrationAction(project).execute()
        JacocoInstrumentationService.RegistrationAction(project).execute()
        SymbolTableBuildService.RegistrationAction(project).execute()

        val stringCachingService: Provider<StringCachingBuildService> =
            StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()
        LibraryDependencyCacheBuildService.RegistrationAction(
            project, mavenCoordinatesCacheBuildService
        ).execute()
        GlobalSyncService.RegistrationAction(project, mavenCoordinatesCacheBuildService).execute()

        LintClassLoaderBuildService.RegistrationAction(project).execute()
        LintFixBuildService.RegistrationAction(project).execute()

        // enable the gradle property that enables the kgp IDE import APIs that we rely on.
        project.extensions.extraProperties.set(
            "kotlin.mpp.import.enableKgpDependencyResolution", "true"
        )
        // publish the jvm target with TargetJvmEnvironment attribute so that we're able to
        // distinguish between jvm and android targets based on that attribute.
        project.extensions.extraProperties.set(
            "kotlin.publishJvmEnvironmentAttribute", "true"
        )
    }

    override fun configureExtension(project: Project) {
        androidExtension = kotlinMultiplatformHandler.createAndroidExtension()

        kmpVariantApiOperationsRegistrar = MultiplatformVariantApiOperationsRegistrar(
            androidExtension
        )

        settingsExtension?.let {
            androidExtension.initExtensionFromSettings(it)
        }

        BasePlugin.createAndroidTestUtilConfiguration(project)

        val versionedSdkLoaderService = withProject("versionedSdkLoaderService") {
            VersionedSdkLoaderService(
                dslServices,
                project,
                ::getCompileSdkVersion,
                ::getBuildToolsVersion
            )
        }

        val bootClasspathConfig = BootClasspathConfigImpl(
            project,
            projectServices,
            versionedSdkLoaderService,
            libraryRequests = emptyList(),
            isJava8Compatible = { true },
            returnDefaultValuesForMockableJar = { false },
            forUnitTest = false
        )

        global = KmpGlobalTaskCreationConfigImpl(
            project,
            androidExtension,
            settingsExtension,
            versionedSdkLoaderService,
            bootClasspathConfig,
            ::getCompileSdkVersion,
            ::getBuildToolsVersion,
            BasePlugin.createAndroidJarConfig(project),
            dslServices,
            createSettingsOptions(dslServices)
        )

        kotlinMultiplatformAndroidComponentsExtension = createComponentExtension(
            project,
            dslServices,
            kmpVariantApiOperationsRegistrar,
            bootClasspathConfig
        )
    }

    override fun createTasks(project: Project) {
        TaskManager.createTasksBeforeEvaluate(
            project,
            ComponentTypeImpl.KMP_ANDROID,
            emptySet(),
            global
        )

        project.afterEvaluate {
            if (!project.plugins.hasPlugin(KOTLIN_MPP_PLUGIN_ID)) {
                throw RuntimeException("Kotlin multiplatform plugin was not found. This plugin needs" +
                        " to be applied as part of the kotlin multiplatform plugin.")
            }
            afterEvaluate(it)
        }
    }

    protected open fun KotlinMultiplatformAndroidExtension.initExtensionFromSettings(
        settings: SettingsExtension
    ) {
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
            this.minSdk = minSdk
        }

        settings.minSdkPreview?.let { minSdkPreview ->
            this.minSdkPreview = minSdkPreview
        }

        settings.buildToolsVersion.let { buildToolsVersion ->
            this.buildToolsVersion = buildToolsVersion
        }
    }

    private fun getCompileSdkVersion(): String =
        androidExtension.compileSdkPreview?.let { validatePreviewTargetValue(it) }?.let { "android-$it" } ?:
        androidExtension.compileSdkExtension?.let { "android-${androidExtension.compileSdk}-ext$it" } ?:
        androidExtension.compileSdk?.let {"android-$it"} ?: throw RuntimeException(
            "compileSdk version is not set.\n" +
                    "Specify the compileSdk version in the module's build file like so:\n" +
                    "kotlin {\n" +
                    "    $ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME {\n" +
                    "        compileSdk = ${MAX_SUPPORTED_ANDROID_PLATFORM_VERSION.apiLevel}\n" +
                    "    }\n" +
                    "}\n"
        )

    private fun getBuildToolsVersion(): Revision =
        Revision.parseRevision(androidExtension.buildToolsVersion, Revision.Precision.MICRO)

    private fun afterEvaluate(
        project: Project
    ) {
        kmpVariantApiOperationsRegistrar.executeDslFinalizationBlocks()
        androidExtension.lock()


        val dependencyConfigurator = DependencyConfigurator(
            project = project,
            projectServices = projectServices
        )
            .configureDependencySubstitutions()
            .configureDependencyChecks()
            .configureGeneralTransforms(namespacedAndroidResources = global.namespacedAndroidResources, global.aarOrJarTypeToConsume)
            .configureCalculateStackFramesTransforms(global)

        val variantServices = VariantServicesImpl(projectServices)
        val taskServices = TaskCreationServicesImpl(projectServices)

        val taskManager = KmpTaskManager(
            project, global
        )

        mainVariant = createVariant(
            project,
            global,
            variantServices,
            taskServices,
            kotlinMultiplatformHandler.getAndroidTarget()
        )

        val sandboxConsumptionEnabled = mainVariant.privacySandboxCreationConfig != null
        configureDisambiguationRules(project, sandboxConsumptionEnabled)

        val unitTest = createUnitTestComponent(
            project,
            global,
            variantServices,
            taskServices,
            kotlinMultiplatformHandler.getAndroidTarget()
        )

        val androidTest = createAndroidTestComponent(
            project,
            global,
            variantServices,
            taskServices,
            taskManager,
            kotlinMultiplatformHandler.getAndroidTarget()
        )

        mainVariant.unitTest = unitTest
        mainVariant.androidTest = androidTest

        val stats = configuratorService.getVariantBuilder(
            project.path,
            mainVariant.name
        )

        kmpVariantApiOperationsRegistrar.variantOperations.executeOperations(
            stats?.let {
                variantServices.newInstance(
                    AnalyticsEnabledKotlinMultiplatformAndroidVariant::class.java,
                    mainVariant,
                    stats
                )
            } ?: mainVariant
        )


        listOfNotNull(mainVariant, unitTest, androidTest).forEach {
            it.syncAndroidAndKmpClasspathAndSources()
        }

        (global.compileOptions as CompileOptions)
            .finalizeSourceAndTargetCompatibility(project, global)

        dependencyConfigurator.configureVariantTransforms(
            variants = listOf(mainVariant),
            nestedComponents = mainVariant.nestedComponents,
            bootClasspathConfig = global
        )

        if (androidTest?.isAndroidTestCoverageEnabled == true) {
            dependencyConfigurator.configureJacocoTransforms()
        }

        taskManager.createTasks(
            project,
            mainVariant,
            unitTest,
            androidTest
        )

        finalizeAllComponents(listOfNotNull(mainVariant, unitTest, androidTest))

        kotlinMultiplatformHandler.finalize(mainVariant)
    }

    private fun Configuration.forMainVariantConfiguration(
        dslInfo: KmpComponentDslInfo
    ): Configuration? {
        return this.takeIf {
            !dslInfo.componentType.isTestComponent
        }
    }

    @OptIn(ExternalKotlinTargetApi::class)
    private fun createVariantDependencies(
        project: Project,
        dslInfo: KmpComponentDslInfo,
        androidKotlinCompilation: KotlinMultiplatformAndroidCompilation,
        androidTarget: KotlinMultiplatformAndroidTarget
    ): VariantDependencies {
        return VariantDependencies.createForKotlinMultiplatform(
            project = project,
            projectOptions = projectServices.projectOptions,
            dslInfo = dslInfo,
            apiClasspath = project.configurations.getByName(
                androidKotlinCompilation.apiConfigurationName
            ),
            compileClasspath = project.configurations.getByName(
                androidKotlinCompilation.compileDependencyConfigurationName
            ),
            runtimeClasspath = project.configurations.getByName(
                androidKotlinCompilation.runtimeDependencyConfigurationName!!
            ),
            apiElements = (androidTarget as KotlinMultiplatformAndroidTargetImpl)
                .apiElementsConfiguration.forMainVariantConfiguration(dslInfo),
            runtimeElements = androidTarget.runtimeElementsConfiguration.forMainVariantConfiguration(dslInfo),
            sourcesElements = project.configurations.findByName(
                androidTarget.sourcesElementsConfigurationName
            )?.forMainVariantConfiguration(dslInfo),
            apiPublication = androidTarget.apiElementsPublishedConfiguration.forMainVariantConfiguration(dslInfo),
            runtimePublication = androidTarget.runtimeElementsPublishedConfiguration.forMainVariantConfiguration(dslInfo),
            sourcesPublication = androidTarget.sourcesElementsPublishedConfiguration.forMainVariantConfiguration(dslInfo).also {
                it?.let { androidTarget.publishSources(androidKotlinCompilation as KotlinMultiplatformAndroidCompilationImpl) }
            }
        )
    }

    private fun getAndroidManifestDefaultLocation(
        compilation: KotlinMultiplatformAndroidCompilation
    ) = FileUtils.join(
        compilation.project.projectDir,
        "src",
        compilation.defaultSourceSet.name,
        "AndroidManifest.xml"
    )

    private fun createVariant(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        androidTarget: KotlinMultiplatformAndroidTarget
    ): KmpVariantImpl {

        val dslInfo = KmpVariantDslInfoImpl(
            androidExtension,
            variantServices,
            project.layout.buildDirectory,
            (androidTarget as KotlinMultiplatformAndroidTargetImpl).enableJavaSources
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        val kotlinCompilation = androidTarget.compilations.getByName(
            KmpAndroidCompilationType.MAIN.defaultCompilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        return KmpVariantImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            manifestFile = getAndroidManifestDefaultLocation(kotlinCompilation)
        )
    }

    private fun createUnitTestComponent(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        androidTarget: KotlinMultiplatformAndroidTarget
    ): KmpUnitTestImpl? {
        if (!mainVariant.dslInfo.enabledUnitTest) {
            return null
        }

        val dslInfo = KmpUnitTestDslInfoImpl(
            androidExtension,
            variantServices,
            mainVariant.dslInfo,
            (androidTarget as KotlinMultiplatformAndroidTargetImpl).enableJavaSources,
            dslServices
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        val kotlinCompilation = androidTarget.compilations.getByName(
            androidExtension.androidTestOnJvmBuilder!!.compilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        return KmpUnitTestImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(
                androidResources = global.unitTestOptions.isIncludeAndroidResources
            ),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            mainVariant = mainVariant,
            manifestFile = getAndroidManifestDefaultLocation(kotlinCompilation)
        )
    }

    private fun createAndroidTestComponent(
        project: Project,
        global: GlobalTaskCreationConfig,
        variantServices: VariantServices,
        taskCreationServices: TaskCreationServices,
        taskManager: KmpTaskManager,
        androidTarget: KotlinMultiplatformAndroidTarget
    ): KmpAndroidTestImpl? {
        if (!mainVariant.dslInfo.enableAndroidTest) {
            return null
        }

        val kotlinCompilation = androidTarget.compilations.getByName(
            androidExtension.androidTestOnDeviceBuilder!!.compilationName
        ) as KotlinMultiplatformAndroidCompilationImpl

        val manifestLocation = getAndroidManifestDefaultLocation(kotlinCompilation)

        taskManager.canParseManifest.set(
            !dslServices.projectOptions[BooleanOption.DISABLE_EARLY_MANIFEST_PARSING]
        )
        val manifestParser = LazyManifestParser(
            manifestFile = projectServices.objectFactory.fileProperty().fileValue(manifestLocation),
            manifestFileRequired = true,
            taskManager.canParseManifest,
            projectServices = projectServices,
        )

        val dslInfo = KmpAndroidTestDslInfoImpl(
            androidExtension,
            variantServices,
            manifestParser,
            mainVariant.dslInfo,
            createSigningOverride(dslServices),
            dslServices,
            (androidTarget as KotlinMultiplatformAndroidTargetImpl).enableJavaSources
        )

        val paths = VariantPathHelper(
            project.layout.buildDirectory,
            dslInfo,
            dslServices
        )

        val artifacts = ArtifactsImpl(project, dslInfo.componentIdentity.name)

        return KmpAndroidTestImpl(
            dslInfo = dslInfo,
            internalServices = variantServices,
            buildFeatures = KotlinMultiplatformBuildFeaturesValuesImpl(),
            variantDependencies = createVariantDependencies(project, dslInfo, kotlinCompilation, androidTarget),
            paths = paths,
            artifacts = artifacts,
            taskContainer = MutableTaskContainer(),
            services = taskCreationServices,
            global = global,
            androidKotlinCompilation = kotlinCompilation,
            mainVariant = mainVariant,
            manifestFile = manifestLocation
        )
    }

    private fun configureDisambiguationRules(
            project: Project, supportPrivacySandboxSdkConsumption: Boolean) {
        project.dependencies.attributesSchema { schema ->
            val buildTypesToMatch = androidExtension.dependencyVariantSelection.buildTypes.get()
            schema.attribute(BuildTypeAttr.ATTRIBUTE)
                .disambiguationRules
                .add(SingleVariantBuildTypeRule::class.java) { config ->
                    config.setParams(buildTypesToMatch)
                }

            androidExtension.dependencyVariantSelection.productFlavors.get().forEach { (dimension, fallbacks) ->
                schema.attribute(ProductFlavorAttr.of(dimension))
                    .disambiguationRules
                    .add(SingleVariantProductFlavorRule::class.java) { config ->
                        config.setParams(fallbacks)
                    }
            }

            schema.attribute(AgpVersionAttr.ATTRIBUTE)
                .compatibilityRules
                .add(AgpVersionCompatibilityRule::class.java)
            setUp(schema, supportPrivacySandboxSdkConsumption)
        }
    }

    val managedDeviceRegistry: ManagedDeviceRegistry by lazy(LazyThreadSafetyMode.NONE) {
        ManagedDeviceRegistry(KmpAndroidTestOptionsDslInfoImpl(androidExtension))
    }

    private fun createComponentExtension(
        project: Project,
        dslServices: DslServices,
        variantApiOperationsRegistrar: MultiplatformVariantApiOperationsRegistrar,
        bootClasspathConfig: BootClasspathConfig
    ): KotlinMultiplatformAndroidComponentsExtension {
        val sdkComponents: SdkComponents = dslServices.newInstance(
            SdkComponentsImpl::class.java,
            dslServices,
            project.provider(::getCompileSdkVersion),
            project.provider(::getBuildToolsVersion),
            project.provider(global::ndkVersion),
            project.provider<String>(global::ndkPath),
            project.provider(
                bootClasspathConfig::bootClasspath
            )
        )

        return project.extensions.create(
            KotlinMultiplatformAndroidComponentsExtension::class.java,
            "androidComponents",
            KotlinMultiplatformAndroidComponentsExtensionImpl::class.java,
            sdkComponents,
            managedDeviceRegistry,
            variantApiOperationsRegistrar
        )
    }

    companion object {
        internal const val ANDROID_TARGET_NAME = "android"
        const val ANDROID_EXTENSION_ON_KOTLIN_EXTENSION_NAME = "androidLibrary"
        fun String.getNamePrefixedWithAndroidTarget() = ANDROID_TARGET_NAME.appendCapitalized(this)
    }
}
