/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.databinding.tool.DataBindingBuilder
import com.android.SdkConstants
import com.android.SdkConstants.DATA_BINDING_KTX_LIB_ARTIFACT
import com.android.build.api.dsl.DataBinding
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.attribution.CheckJetifierBuildService
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.cxx.configure.createCxxTasks
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.CheckJetifierTask
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.addComposeArgsToKotlinCompile
import com.android.build.gradle.internal.utils.configureKotlinCompileForProject
import com.android.build.gradle.internal.utils.isKotlinPluginApplied
import com.android.build.gradle.internal.utils.recordIrBackendForAnalytics
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.AnalyzeDependenciesTask
import com.android.build.gradle.tasks.registerDataBindingOutputs
import com.android.builder.core.ComponentType
import com.android.builder.dexing.isLegacyMultiDexMode
import com.android.builder.errors.IssueReporter
import com.android.utils.usLocaleCapitalize
import com.google.common.base.MoreObjects
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class VariantTaskManager<VariantBuilderT : VariantBuilder, VariantT : VariantCreationConfig>(
    project: Project,
    private val variants: Collection<ComponentInfo<VariantBuilderT, VariantT>>,
    private val testComponents: Collection<TestComponentCreationConfig>,
    private val testFixturesComponents: Collection<TestFixturesCreationConfig>,
    globalConfig: GlobalTaskCreationConfig,
    @JvmField protected val localConfig: TaskManagerConfig,
    @JvmField protected val extension: BaseExtension,
): TaskManager(project, globalConfig) {

    @JvmField
    protected val variantPropertiesList: List<VariantT> =
        variants.map(ComponentInfo<VariantBuilderT, VariantT>::variant)
    private val nestedComponents: List<NestedComponentCreationConfig> =
        testComponents + testFixturesComponents
    private val allPropertiesList: List<ComponentCreationConfig> =
        variantPropertiesList + nestedComponents


    private val lintTaskManager = LintTaskManager(globalConfig, taskFactory, project)

    private val unitTestTaskManager = UnitTestTaskManager(project, globalConfig)
    private val androidTestTaskManager = AndroidTestTaskManager(project, globalConfig)
    private val testFixturesTaskManager = TestFixturesTaskManager(project, globalConfig, localConfig)

    /**
     * This is the main entry point into the task manager
     *
     * This creates the tasks for all the variants and all the nested components
     */
    fun createTasks(
        componentType: ComponentType, variantModel: VariantModel
    ) {
        // this is called before all the variants are created since they are all going to depend
        // on the global LINT_PUBLISH_JAR task output
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        createPrepareLintJarForPublishTask()

        // create a lifecycle task to build the lintChecks dependencies
        taskFactory.register(globalConfig.taskNames.compileLintChecks) { task: Task ->
            task.dependsOn(globalConfig.localCustomLintChecks)
        }

        // Create top level test tasks.
        unitTestTaskManager.createTopLevelTasks()
        androidTestTaskManager.createTopLevelTasks()

        // Create tasks for all variants (main, testFixtures and tests)
        for (variant in variants) {
            createTasksForVariant(variant)
        }
        for (testFixturesComponent in testFixturesComponents) {
            testFixturesTaskManager.createTasks(testFixturesComponent)
        }
        for (testComponent in testComponents) {
            createTasksForTest(testComponent)
        }
        createTopLevelTasks(componentType, variantModel)
    }

    fun createPostApiTasks() {

        // must run this after scopes are created so that we can configure kotlin
        // kapt tasks
        addBindingDependenciesIfNecessary(globalConfig.dataBinding)

        // configure Kotlin compilation if needed.
        configureKotlinPluginTasksIfNecessary()

        createAnchorAssembleTasks(
            globalConfig.productFlavorCount,
            globalConfig.productFlavorDimensionCount)
    }

    /**
     * Create tasks for the specified variant.
     *
     *
     * This creates tasks common to all variant types.
     */
    private fun createTasksForVariant(
        variant: ComponentInfo<VariantBuilderT, VariantT>,
    ) {
        val variantProperties = variant.variant
        val componentType = variantProperties.componentType
        val variantDependencies = variantProperties.variantDependencies
        if (variantProperties is ApkCreationConfig &&
            variantProperties.dexingCreationConfig.dexingType.isLegacyMultiDexMode()) {
            val multiDexDependency =
                if (variantProperties
                        .services
                        .projectOptions[BooleanOption.USE_ANDROID_X])
                    ANDROIDX_MULTIDEX_MULTIDEX
                else COM_ANDROID_SUPPORT_MULTIDEX
            project.dependencies
                .add(variantDependencies.compileClasspath.name, multiDexDependency)
            project.dependencies
                .add(variantDependencies.runtimeClasspath.name, multiDexDependency)
        }
        if (variantProperties.renderscriptCreationConfig?.renderscript?.supportModeEnabled?.get()
            == true) {
            val fileCollection = project.files(
                globalConfig.versionedSdkLoader.flatMap {
                    it.renderScriptSupportJarProvider
                }
            )
            project.dependencies.add(variantDependencies.compileClasspath.name, fileCollection)
            if (componentType.isApk && !componentType.isForTesting) {
                project.dependencies.add(variantDependencies.runtimeClasspath.name, fileCollection)
            }
        }
        createAssembleTask(variantProperties)
        if (variantProperties.services.projectOptions.get(BooleanOption.IDE_INVOKED_FROM_IDE)) {
            variantProperties.taskContainer.assembleTask.configure {
                it.dependsOn(variantProperties.artifacts.get(InternalArtifactType.VARIANT_MODEL))
            }
        }

        doCreateTasksForVariant(variant)
    }

    open fun createTopLevelTasks(componentType: ComponentType, variantModel: VariantModel) {
        lintTaskManager.createLintTasks(
            componentType,
            variantModel,
            variantPropertiesList,
            testComponents,
            globalConfig.services.projectOptions.get(BooleanOption.LINT_ANALYSIS_PER_COMPONENT)
        )
        createReportTasks()

        // Create C/C++ configuration, build, and clean tasks
        val androidLocationBuildService: Provider<AndroidLocationsBuildService> =
            getBuildService(project.gradle.sharedServices)
        createCxxTasks(
            androidLocationBuildService.get(),
            getBuildService(
                globalConfig.services.buildServiceRegistry,
                SdkComponentsBuildService::class.java
            ).get(),
            globalConfig.services.issueReporter,
            taskFactory,
            globalConfig.services.projectOptions,
            variants,
            project
        )

        // Global tasks required for privacy sandbox sdk consumption
        if (globalConfig.services.projectOptions.get(BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT)) {
            taskFactory.register(ValidateSigningTask.PrivacySandboxSdkCreationAction(globalConfig))
        }
    }

    private fun createReportTasks() {
        taskFactory.register(
            "androidDependencies",
            DependencyReportTask::class.java
        ) { task: DependencyReportTask ->
            task.description = "Displays the Android dependencies of the project."
            task.variants.setDisallowChanges(variantPropertiesList)
            task.nestedComponents.setDisallowChanges(nestedComponents)
            task.group = ANDROID_GROUP
            task.mavenCoordinateCache.setDisallowChanges(
                getBuildService(
                    project.gradle.sharedServices,
                    MavenCoordinatesCacheBuildService::class.java
                ).get()
            )
            task.notCompatibleWithConfigurationCache(
                "DependencyReportTask not compatible with config caching"
            )
        }
        val signingReportComponents = allPropertiesList.stream()
            .filter { component: ComponentCreationConfig ->
                component is ApkCreationConfig
            }
            .map { component -> component as ApkCreationConfig }
            .collect(Collectors.toList())
        if (signingReportComponents.isNotEmpty()) {
            taskFactory.register(
                "signingReport",
                SigningReportTask::class.java
            ) { task: SigningReportTask ->
                task.description = "Displays the signing info for the base and test modules"
                task.setComponents(signingReportComponents)
                task.group = ANDROID_GROUP
                task.notCompatibleWithConfigurationCache(
                    "SigningReportTask is not compatible with config caching")
            }
        }
        createDependencyAnalyzerTask()

        val checkJetifierBuildService =
            CheckJetifierBuildService
                .RegistrationAction(project, globalConfig.services.projectOptions)
                .execute()
        taskFactory.register(
            CheckJetifierTask.CreationAction(
                globalConfig,
                checkJetifierBuildService,
                variants,
                testComponents,
                testFixturesComponents
            )
        )
    }

    private fun createDependencyAnalyzerTask() {
        for (variant in variantPropertiesList) {
            taskFactory.register(AnalyzeDependenciesTask.CreationAction(variant))
        }
        for (component in nestedComponents) {
            taskFactory.register(AnalyzeDependenciesTask.CreationAction(component))
        }
    }

    /** Create tasks for the specified variant.  */
    private fun createTasksForTest(testVariant: TestComponentCreationConfig) {
        createAssembleTask(testVariant)
        val testedVariant = testVariant.mainVariant
        val variantDependencies = testVariant.variantDependencies
        if (testedVariant.renderscriptCreationConfig?.renderscript?.supportModeEnabled?.get()
            == true) {
            project.dependencies
                .add(
                    variantDependencies.compileClasspath.name,
                    project.files(
                        globalConfig.versionedSdkLoader.flatMap {
                            it.renderScriptSupportJarProvider
                        }
                    ))

        }
        if (testVariant.componentType.isApk) { // ANDROID_TEST
            if ((testVariant as ApkCreationConfig).dexingCreationConfig.dexingType.isLegacyMultiDexMode()) {
                val multiDexInstrumentationDep = if (testVariant
                        .services
                        .projectOptions[BooleanOption.USE_ANDROID_X])
                    ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION
                else COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION
                project.dependencies
                    .add(
                        variantDependencies.compileClasspath.name,
                        multiDexInstrumentationDep)
                project.dependencies
                    .add(
                        variantDependencies.runtimeClasspath.name,
                        multiDexInstrumentationDep)
            }
            androidTestTaskManager.createTasks(testVariant as AndroidTestCreationConfig)
        } else {
            // UNIT_TEST
            unitTestTaskManager.createTasks(testVariant as UnitTestCreationConfig)
        }
    }

    private fun configureKotlinPluginTasksIfNecessary() {
        if (!isKotlinPluginApplied(project)) {
            return
        }
        val composeIsEnabled = allPropertiesList
            .any { componentProperties: ComponentCreationConfig ->
                componentProperties.buildFeatures.compose }
        recordIrBackendForAnalytics(
            allPropertiesList, extension, project, composeIsEnabled)
        if (!composeIsEnabled) {
            return
        }

        // any override coming from the DSL.
        val kotlinCompilerExtensionVersionInDsl =
            globalConfig.composeOptions.kotlinCompilerExtensionVersion

        val useLiveLiterals = globalConfig.composeOptions.useLiveLiterals

        // record in our metrics that compose is enabled.
        getBuildService(
            project.gradle.sharedServices, AnalyticsConfiguratorService::class.java)
            .get()
            .getProjectBuilder(project.path)?.composeEnabled = true

        // Create a project configuration that holds the androidx compose kotlin
        // compiler extension
        val kotlinExtension = project.configurations.create("kotlin-extension")
        project.dependencies
            .add(
                kotlinExtension.name, "androidx.compose.compiler:compiler:"
                        + (kotlinCompilerExtensionVersionInDsl
                    ?: COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION))
        kotlinExtension.isTransitive = false
        kotlinExtension.description = "Configuration for Compose related kotlin compiler extension"

        // add compose args to all kotlin compile tasks
        for (creationConfig in allPropertiesList) {
            try {
                configureKotlinCompileForProject(project, creationConfig) {
                    addComposeArgsToKotlinCompile(
                        it, creationConfig, project.files(kotlinExtension), useLiveLiterals
                    )
                }
            } catch (e: UnknownTaskException) {
                // ignore
            }
        }
    }

    private fun addBindingDependenciesIfNecessary(dataBindingOptions: DataBinding) {
        val viewBindingEnabled = allPropertiesList.stream()
            .anyMatch { componentProperties: ComponentCreationConfig -> componentProperties.buildFeatures.viewBinding }
        val dataBindingEnabled = allPropertiesList.stream()
            .anyMatch { componentProperties: ComponentCreationConfig -> componentProperties.buildFeatures.dataBinding }
        val useAndroidX = globalConfig.services.projectOptions.get(BooleanOption.USE_ANDROID_X)
        val dataBindingBuilder = localConfig.dataBindingBuilder
        if (viewBindingEnabled) {
            val version = dataBindingBuilder.getLibraryVersion(dataBindingBuilder.compilerVersion)
            val groupAndArtifact =
                if (useAndroidX)
                    SdkConstants.ANDROIDX_VIEW_BINDING_ARTIFACT
                else SdkConstants.VIEW_BINDING_ARTIFACT
            project.dependencies.add("api", "$groupAndArtifact:$version")
        }
        if (dataBindingEnabled) {
            val version = MoreObjects.firstNonNull(
                dataBindingOptions.version,
                dataBindingBuilder.compilerVersion)
            val baseLibArtifact =
                if (useAndroidX)
                    SdkConstants.ANDROIDX_DATA_BINDING_BASELIB_ARTIFACT
                else SdkConstants.DATA_BINDING_BASELIB_ARTIFACT
            project.dependencies
                .add(
                    "api",
                    baseLibArtifact
                            + ":"
                            + dataBindingBuilder.getBaseLibraryVersion(version))
            project.dependencies
                .add(
                    "annotationProcessor",
                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT
                            + ":"
                            + version)
            // TODO load config name from source sets
            if (dataBindingOptions.enableForTests || this is LibraryTaskManager) {
                val dataBindingArtifact =
                    SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version
                project.dependencies
                    .add("androidTestAnnotationProcessor", dataBindingArtifact)
                if (globalConfig.unitTestOptions.isIncludeAndroidResources) {
                    project.dependencies.add("testAnnotationProcessor", dataBindingArtifact)
                }
            }
            if ((dataBindingOptions as DataBindingOptions).addDefaultAdapters) {
                val libArtifact =
                    if (useAndroidX)
                        SdkConstants.ANDROIDX_DATA_BINDING_LIB_ARTIFACT
                    else SdkConstants.DATA_BINDING_LIB_ARTIFACT
                val adaptersArtifact =
                    if (useAndroidX)
                        SdkConstants.ANDROIDX_DATA_BINDING_ADAPTER_LIB_ARTIFACT
                    else SdkConstants.DATA_BINDING_ADAPTER_LIB_ARTIFACT
                project.dependencies
                    .add(
                        "api",
                        libArtifact + ":" + dataBindingBuilder.getLibraryVersion(version))
                project.dependencies
                    .add(
                        "api",
                        adaptersArtifact
                                + ":"
                                + dataBindingBuilder.getBaseAdaptersVersion(version))
            }

            addDataBindingKtxIfNecessary(project, dataBindingOptions, dataBindingBuilder, version,
                useAndroidX)

            project.pluginManager
                .withPlugin(KOTLIN_KAPT_PLUGIN_ID) {
                    configureKotlinKaptTasksForDataBinding(project, version)
                }
        }
    }

    private fun addDataBindingKtxIfNecessary(
        project: Project,
        dataBindingOptions: DataBindingOptions,
        dataBindingBuilder: DataBindingBuilder,
        version: String,
        useAndroidX: Boolean
    ) {
        val ktxDataBindingDslValue: Boolean? = dataBindingOptions.addKtx
        val ktxGradlePropertyValue = globalConfig.services.projectOptions
            .get(BooleanOption.ENABLE_DATABINDING_KTX)

        val enableKtx = ktxDataBindingDslValue ?: ktxGradlePropertyValue
        if (enableKtx) {
            // Add Ktx dependency if AndroidX and Kotlin is used
            if (useAndroidX && isKotlinPluginApplied(project)) {
                project.dependencies
                    .add(
                        "api",
                        DATA_BINDING_KTX_LIB_ARTIFACT
                                + ":"
                                + dataBindingBuilder.getLibraryVersion(version))
            } else {
                // Warn if user manually enabled Ktx via the DSL option and
                // it's not a Kotlin or AndroidX project.
                if (ktxDataBindingDslValue == true) {
                    globalConfig
                        .services
                        .issueReporter
                        .reportWarning(
                            IssueReporter.Type.GENERIC,
                            "The `android.dataBinding.addKtx` DSL option has no effect because " +
                                    "the `android.useAndroidX` property is not enabled or " +
                                    "the project does not use Kotlin."
                        )
                }
            }
        }
    }

    private fun configureKotlinKaptTasksForDataBinding(
        project: Project,
        version: String) {
        val kaptDeps = project.configurations.getByName("kapt").allDependencies
        kaptDeps.forEach(
            Consumer { dependency: Dependency ->
                // if it is a data binding compiler dependency w/ a different version, report
                // error
                if (dependency.group + ":" + dependency.name == SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT
                    && dependency.version != version) {
                    val depString = (dependency.group
                            + ":"
                            + dependency.name
                            + ":"
                            + dependency.version)
                    globalConfig
                        .services
                        .issueReporter
                        .reportError(
                            IssueReporter.Type.GENERIC,
                            "Data Binding annotation processor version needs to match the"
                                    + " Android Gradle Plugin version. You can remove the kapt"
                                    + " dependency "
                                    + depString
                                    + " and Android Gradle Plugin will inject"
                                    + " the right version.")
                }
            })
        project.dependencies
            .add(
                "kapt",
                SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version)
        var kaptTaskClass: Class<out Task>? = null
        try {
            kaptTaskClass =
                Class.forName("org.jetbrains.kotlin.gradle.internal.KaptTask") as Class<out Task>
        } catch (e: ClassNotFoundException) {
            logger.error(
                "Kotlin plugin is applied to the project "
                        + project.path
                        + " but we cannot find the KaptTask. Make sure you apply the"
                        + " kotlin-kapt plugin because it is necessary to use kotlin"
                        + " with data binding.")
        }
        if (kaptTaskClass == null) {
            return
        }

        // Find a matching variant and configure each Kapt task. Note: The task's name could be
        // kapt${variant}Kotlin, or kapt${variant}KotlinAndroid in KMP projects.
        val kaptTaskNameOrPrefixToVariant =
            allPropertiesList.associateBy { it.computeTaskName("kapt", "Kotlin") }
        project.tasks.withType(kaptTaskClass) { kaptTask: Task ->
            val variant = kaptTaskNameOrPrefixToVariant
                .keys.firstOrNull { kaptTask.name.startsWith(it) }
                ?.let { kaptTaskNameOrPrefixToVariant[it]!! }
            // If we can't find a matching variant, it could be that this is a Kapt task for JVM in
            // a KMP project (its name is "kaptKotlinJvm"), which we don't need to handle.
            variant?.let {
                configureKaptTaskInScopeForDataBinding(variant, kaptTask)
            }
        }
    }

    private fun configureKaptTaskInScopeForDataBinding(
        creationConfig: ComponentCreationConfig, kaptTask: Task) {
        val dataBindingArtifactDir = project.objects.directoryProperty()
        val exportClassListFile = project.objects.fileProperty()
        val kaptTaskProvider = taskFactory.named(kaptTask.name)

        // Register data binding artifacts as outputs
        registerDataBindingOutputs(
            dataBindingArtifactDir,
            exportClassListFile,
            creationConfig.componentType.isExportDataBindingClassList,
            kaptTaskProvider,
            creationConfig.artifacts,
            false // forJavaCompile = false as this task is Kapt
        )

        // Register the DirectoryProperty / RegularFileProperty as outputs as they are not yet
        // annotated as outputs (same with the code in JavaCompileCreationAction.configure).
        kaptTask.outputs
            .dir(dataBindingArtifactDir)
            .withPropertyName("dataBindingArtifactDir")
        if (creationConfig.componentType.isExportDataBindingClassList) {
            kaptTask.outputs
                .file(exportClassListFile)
                .withPropertyName("dataBindingExportClassListFile")
        }
    }

    /**
     * Create assemble* and bundle* anchor tasks.
     *
     *
     * This does not create the variant specific version of these tasks only the ones that are
     * per build-type, per-flavor, per-flavor-combo and the main 'assemble' and 'bundle' ones.
     *
     * @param flavorCount the number of flavors
     * @param flavorDimensionCount whether there are flavor dimensions at all.
     */
    private fun createAnchorAssembleTasks(
        flavorCount: Int,
        flavorDimensionCount: Int) {

        // sub anchor tasks that the main 'assemble' and 'bundle task will depend.
        val subAssembleTasks= mutableListOf<TaskProvider<out Task>>()
        val subBundleTasks= mutableListOf<TaskProvider<out Task>?>()

        // There are 2 different scenarios:
        // 1. There are 1+ flavors. In this case the variant-specific assemble task is
        //    different from all the assemble<BuildType> or assemble<Flavor>
        // 2. Else, the assemble<BuildType> is the same as the variant specific assemble task.

        // Case #1
        if (flavorCount > 0) {
            // loop on the variants and record their build type/flavor usage.
            // map from build type/flavor names to the variant-specific assemble/bundle tasks
            val assembleMap: ListMultimap<String, TaskProvider<out Task>> =
                ArrayListMultimap.create()
            val bundleMap: ListMultimap<String, TaskProvider<out Task>?> =
                ArrayListMultimap.create()
            for (creationConfig in allPropertiesList) {
                val componentType = creationConfig.componentType
                if (!componentType.isNestedComponent) {
                    val taskContainer = creationConfig.taskContainer
                    val buildType = creationConfig.buildType
                    val assembleTask = taskContainer.assembleTask
                    if (buildType != null) {
                        assembleMap.put(buildType, assembleTask)
                    }
                    for (flavor in creationConfig.productFlavorList) {
                        assembleMap.put(flavor.name, assembleTask)
                    }

                    // if 2+ flavor dimensions, then make an assemble for the flavor combo
                    if (flavorDimensionCount > 1) {
                        assembleMap.put(creationConfig.flavorName, assembleTask)
                    }

                    // fill the bundle map only if the variant supports bundles.
                    if (componentType.isBaseModule) {
                        val bundleTask = taskContainer.bundleTask
                        if (buildType != null) {
                            bundleMap.put(buildType, bundleTask)
                        }
                        for (flavor in creationConfig.productFlavorList) {
                            bundleMap.put(flavor.name, bundleTask)
                        }

                        // if 2+ flavor dimensions, then make an assemble for the flavor combo
                        if (flavorDimensionCount > 1) {
                            bundleMap.put(creationConfig.flavorName, bundleTask)
                        }
                    }
                }
            }

            // loop over the map of build-type/flavor to create tasks for each, setting a dependency
            // on the variant-specific task.
            // these keys should be the same for bundle and assemble
            val dimensionKeys = assembleMap.keySet()
            for (dimensionKey in dimensionKeys) {
                val dimensionName = dimensionKey.usLocaleCapitalize()

                // create the task and add it to the list
                subAssembleTasks.add(
                    taskFactory.register(
                        "assemble$dimensionName"
                    ) { task: Task ->
                        task.description = ("Assembles main outputs for all "
                                + dimensionName
                                + " variants.")
                        task.group = BasePlugin.BUILD_GROUP
                        task.dependsOn(assembleMap[dimensionKey])
                    })
                val subBundleMap = bundleMap[dimensionKey]
                if (!subBundleMap.isEmpty()) {

                    // create the task and add it to the list
                    subBundleTasks.add(
                        taskFactory.register(
                            "bundle$dimensionName"
                        ) { task: Task ->
                            task.description = ("Assembles bundles for all "
                                    + dimensionName
                                    + " variants.")
                            task.group = BasePlugin.BUILD_GROUP
                            task.dependsOn(subBundleMap)
                        })
                }
            }
        } else {
            // Case #2
            for (creationConfig in allPropertiesList) {
                val componentType = creationConfig.componentType
                if (!componentType.isNestedComponent) {
                    val taskContainer = creationConfig.taskContainer
                    subAssembleTasks.add(taskContainer.assembleTask)
                    if (componentType.isBaseModule) {
                        subBundleTasks.add(taskContainer.bundleTask)
                    }
                }
            }
        }

        // ---
        // ok now we can create the main 'assemble' and 'bundle' tasks and make them depend on the
        // sub-tasks.
        if (subAssembleTasks.isNotEmpty()) {
            // "assemble" task is already created by the java base plugin.
            taskFactory.configure(
                "assemble"
            ) { task: Task ->
                task.description = "Assemble main outputs for all the variants."
                task.group = BasePlugin.BUILD_GROUP
                task.dependsOn(subAssembleTasks)
            }
        }
        if (subBundleTasks.isNotEmpty()) {
            // root bundle task
            taskFactory.register(
                "bundle"
            ) { task: Task ->
                task.description = "Assemble bundles for all the variants."
                task.group = BasePlugin.BUILD_GROUP
                task.dependsOn(subBundleTasks)
            }
        }
    }

    /**
     * Entry point for each specialized TaskManager to create the tasks for a given VariantT
     *
     * @param variantInfo the variantInfo for which to create the tasks
     */
    protected abstract fun doCreateTasksForVariant(
        variantInfo: ComponentInfo<VariantBuilderT, VariantT>
    )

    companion object {
        private const val MULTIDEX_VERSION = "1.0.2"
        private const val COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:$MULTIDEX_VERSION"
        private val ANDROIDX_MULTIDEX_MULTIDEX =
            AndroidXDependencySubstitution.androidXMappings.getValue("com.android.support:multidex")
        private const val COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:$MULTIDEX_VERSION"
        private val ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION =
            AndroidXDependencySubstitution.androidXMappings.getValue("com.android.support:multidex-instrumentation")
    }
}
