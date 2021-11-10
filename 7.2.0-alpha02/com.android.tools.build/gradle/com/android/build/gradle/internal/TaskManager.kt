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

import android.databinding.tool.DataBindingBuilder
import com.android.SdkConstants
import com.android.SdkConstants.DATA_BINDING_KTX_LIB_ARTIFACT
import com.android.SdkConstants.DOT_JAR
import com.android.build.api.artifact.Artifact.Single
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.component.impl.AndroidTestImpl
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.component.impl.TestFixturesImpl
import com.android.build.api.component.impl.UnitTestImpl
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.variant.impl.VariantBuilderImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.attribution.CheckJetifierBuildService
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.UnitTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoPropertiesTask
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.cxx.configure.createCxxTasks
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution.androidXMappings
import com.android.build.gradle.internal.dependency.ConfigurationVariantMapping
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.res.GenerateApiPublicTxtTask
import com.android.build.gradle.internal.res.GenerateEmptyResourceFilesTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask.TestRuntimeStubRClassCreationAction
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DEX
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES
import com.android.build.gradle.internal.scope.InternalArtifactType.JACOCO_INSTRUMENTED_JARS
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_JAVA_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.scope.getRegularFiles
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.AndroidReportTask
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.BundleLibraryClassesDir
import com.android.build.gradle.internal.tasks.BundleLibraryClassesJar
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask
import com.android.build.gradle.internal.tasks.CheckJetifierTask
import com.android.build.gradle.internal.tasks.CheckProguardFiles
import com.android.build.gradle.internal.tasks.CompressAssetsTask
import com.android.build.gradle.internal.tasks.D8BundleMainDexListTask
import com.android.build.gradle.internal.tasks.DependencyReportTask
import com.android.build.gradle.internal.tasks.DesugarLibKeepRulesMergeTask
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.android.build.gradle.internal.tasks.DexFileDependenciesTask
import com.android.build.gradle.internal.tasks.DexMergingAction
import com.android.build.gradle.internal.tasks.DexMergingTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.FeatureDexMergeTask
import com.android.build.gradle.internal.tasks.GenerateLibraryProguardRulesTask
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import com.android.build.gradle.internal.tasks.LibraryAarJarsTask
import com.android.build.gradle.internal.tasks.LintCompile
import com.android.build.gradle.internal.tasks.ListingFileRedirectTask
import com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.ManagedDeviceSetupTask
import com.android.build.gradle.internal.tasks.MergeAaptProguardFilesCreationAction
import com.android.build.gradle.internal.tasks.MergeAssetsForUnitTest
import com.android.build.gradle.internal.tasks.MergeClassesTask
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import com.android.build.gradle.internal.tasks.PackageForUnitTest
import com.android.build.gradle.internal.tasks.PrepareLintJarForPublish
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.RecalculateStackFramesTask
import com.android.build.gradle.internal.tasks.ShrinkResourcesOldShrinkerTask
import com.android.build.gradle.internal.tasks.SigningConfigVersionsWriterTask
import com.android.build.gradle.internal.tasks.SigningConfigWriterTask
import com.android.build.gradle.internal.tasks.SigningReportTask
import com.android.build.gradle.internal.tasks.SourceSetsTask
import com.android.build.gradle.internal.tasks.TestServerTask.TestServerTaskCreationAction
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments.Companion.createArguments
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeBaseClassLogTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingTriggerTask
import com.android.build.gradle.internal.tasks.databinding.KAPT_FIX_KOTLIN_VERSION
import com.android.build.gradle.internal.tasks.databinding.MergeRFilesForDataBindingTask
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.featuresplit.getFeatureName
import com.android.build.gradle.internal.tasks.mlkit.GenerateMlModelClass
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.test.BundleTestDataImpl
import com.android.build.gradle.internal.test.TestDataImpl
import com.android.build.gradle.internal.testing.utp.shouldEnableUtp
import com.android.build.gradle.internal.transforms.LegacyShrinkBundleModuleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkAppBundleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkResourcesNewShrinkerTask
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.build.gradle.internal.utils.addComposeArgsToKotlinCompile
import com.android.build.gradle.internal.utils.getKotlinCompile
import com.android.build.gradle.internal.utils.getProjectKotlinPluginKotlinVersion
import com.android.build.gradle.internal.utils.isKotlinKaptPluginApplied
import com.android.build.gradle.internal.utils.isKotlinPluginApplied
import com.android.build.gradle.internal.utils.recordIrBackendForAnalytics
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.internal.variant.ComponentInfo
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.AnalyzeDependenciesTask
import com.android.build.gradle.tasks.BundleAar
import com.android.build.gradle.tasks.CompatibleScreensManifest
import com.android.build.gradle.tasks.CompileLibraryResourcesTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.ExtractDeepLinksTask
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.GenerateManifestJarTask
import com.android.build.gradle.tasks.GenerateResValues
import com.android.build.gradle.tasks.GenerateTestConfig
import com.android.build.gradle.tasks.GenerateTestConfig.TestConfigInputs
import com.android.build.gradle.tasks.JavaCompileCreationAction
import com.android.build.gradle.tasks.JavaPreCompileTask
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MapSourceSetPathsTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.MergeSourceSetFolders.LibraryAssetCreationAction
import com.android.build.gradle.tasks.MergeSourceSetFolders.MergeMlModelsSourceFoldersCreationAction
import com.android.build.gradle.tasks.MergeSourceSetFolders.MergeShaderSourceFoldersCreationAction
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.ProcessApplicationManifest
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.build.gradle.tasks.ProcessManifestForBundleTask
import com.android.build.gradle.tasks.ProcessManifestForInstantAppTask
import com.android.build.gradle.tasks.ProcessManifestForMetadataFeatureTask
import com.android.build.gradle.tasks.ProcessMultiApkApplicationManifest
import com.android.build.gradle.tasks.ProcessPackagedManifestTask
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ShaderCompile
import com.android.build.gradle.tasks.TransformClassesWithAsmTask
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask
import com.android.build.gradle.tasks.ZipMergingTask
import com.android.build.gradle.tasks.factory.AndroidUnitTest
import com.android.build.gradle.tasks.registerDataBindingOutputs
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.isLegacyMultiDexMode
import com.android.builder.errors.IssueReporter
import com.android.utils.usLocaleCapitalize
import com.google.common.base.MoreObjects
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ListMultimap
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Manages tasks creation
 *
 * @param variants these are all the variants
 * @param testComponents these are all the test components
 * @param hasFlavors whether there are flavors
 * @param globalScope the global scope
 * @param extension the extension
 */
abstract class TaskManager<VariantBuilderT : VariantBuilderImpl, VariantT : VariantImpl>(
        private val variants: List<ComponentInfo<VariantBuilderT, VariantT>>,
        private val testComponents: List<TestComponentImpl>,
        private val testFixturesComponents: List<TestFixturesImpl>,
        private val hasFlavors: Boolean,
        private val projectOptions: ProjectOptions,
        @JvmField protected val globalScope: GlobalScope,
        @JvmField protected val extension: BaseExtension,
        private val projectInfo: ProjectInfo) {

    @JvmField
    protected val project = projectInfo.getProject()
    protected val logger: Logger = Logging.getLogger(this.javaClass)

    @JvmField
    protected val taskFactory: TaskFactory = TaskFactoryImpl(project.tasks)
    protected val lintTaskManager: LintTaskManager = LintTaskManager(globalScope, taskFactory, projectInfo)
    @JvmField
    protected val variantPropertiesList: List<VariantT> =
            variants.map(ComponentInfo<VariantBuilderT, VariantT>::variant)
    private val nestedComponents: List<ComponentImpl> =
        testComponents + testFixturesComponents
    private val allPropertiesList: List<ComponentCreationConfig> =
            variantPropertiesList + nestedComponents


    /**
     * This is the main entry point into the task manager
     *
     *
     * This creates the tasks for all the variants and all the test components
     */
    fun createTasks(
            variantType: VariantType, variantModel: VariantModel) {
        // this is called before all the variants are created since they are all going to depend
        // on the global LINT_PUBLISH_JAR task output
        // setup the task that reads the config and put the lint jar in the intermediate folder
        // so that the bundle tasks can copy it, and the inter-project publishing can publish it
        createPrepareLintJarForPublishTask()

        // create a lifecycle task to build the lintChecks dependencies
        taskFactory.register(COMPILE_LINT_CHECKS_TASK) { task: Task ->
            task.dependsOn(globalScope.localCustomLintChecks)
        }

        // Create top level test tasks.
        createTopLevelTestTasks()

        // Create tasks to manage test devices.
        createTestDevicesTasks()

        // Create tasks for all variants (main, testFixtures and tests)
        for (variant in variants) {
            createTasksForVariant(variant, variants)
        }
        for (testFixturesComponent in testFixturesComponents) {
            createTasksForTestFixtures(testFixturesComponent)
        }
        for (testComponent in testComponents) {
            createTasksForTest(testComponent)
        }
        lintTaskManager.createLintTasks(
            variantType,
            variantModel,
            variantPropertiesList,
            testComponents
        )
        createReportTasks()


        // Create C/C++ configuration, build, and clean tasks
        val androidLocationBuildService: Provider<AndroidLocationsBuildService> =
                getBuildService(project.gradle.sharedServices)
        createCxxTasks(
            androidLocationBuildService.get(),
            globalScope.sdkComponents.get(),
            globalScope.dslServices.issueReporter,
            taskFactory,
            projectOptions,
            variants
        )
    }

    fun createPostApiTasks() {

        // must run this after scopes are created so that we can configure kotlin
        // kapt tasks
        addBindingDependenciesIfNecessary(extension.dataBinding)

        // configure Kotlin compilation if needed.
        configureKotlinPluginTasksIfNecessary()

        createAnchorAssembleTasks(extension.productFlavors.size, extension.flavorDimensionList.size)
    }

    /**
     * Create tasks for the specified variant.
     *
     *
     * This creates tasks common to all variant types.
     */
    private fun createTasksForVariant(
            variant: ComponentInfo<VariantBuilderT, VariantT>,
            variants: List<ComponentInfo<VariantBuilderT, VariantT>>) {
        val variantProperties = variant.variant
        val variantType = variantProperties.variantType
        val variantDependencies = variantProperties.variantDependencies
        if (variantProperties.dexingType.isLegacyMultiDexMode()
                && variantProperties.variantType.isApk) {
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
        if (variantProperties.renderscript?.supportModeEnabled?.get() == true) {
            val fileCollection = project.files(
                    globalScope.versionedSdkLoader.flatMap {
                        it.renderScriptSupportJarProvider
                    }
            )
            project.dependencies.add(variantDependencies.compileClasspath.name, fileCollection)
            if (variantType.isApk && !variantType.isForTesting) {
                project.dependencies.add(variantDependencies.runtimeClasspath.name, fileCollection)
            }
        }
        createAssembleTask(variantProperties)
        if (variantType.isBaseModule) {
            createBundleTask(variantProperties)
        }
        doCreateTasksForVariant(variant)
    }

    /**
     * Entry point for each specialized TaskManager to create the tasks for a given VariantT
     *
     * @param variantInfo the variantInfo for which to create the tasks
     */
    protected abstract fun doCreateTasksForVariant(
            variantInfo: ComponentInfo<VariantBuilderT, VariantT>)

    /** Create tasks for the specified test fixtures component.  */
    private fun createTasksForTestFixtures(testFixturesComponent: TestFixturesImpl) {
        createAssembleTask(testFixturesComponent)
        createAnchorTasks(testFixturesComponent)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(testFixturesComponent)

        // java resources tasks
        createProcessJavaResTask(testFixturesComponent)

        // android resources tasks
        if (testFixturesComponent.androidResourcesEnabled) {
            taskFactory.register(ExtractDeepLinksTask.CreationAction(testFixturesComponent))

            createGenerateResValuesTask(testFixturesComponent)

            val flags: ImmutableSet<MergeResources.Flag?> =
                if (extension.aaptOptions.namespaced) {
                    Sets.immutableEnumSet(
                        MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                        MergeResources.Flag.PROCESS_VECTOR_DRAWABLES
                    )
                } else {
                    Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES)
                }
            // Create a merge task to only merge the resources from this library and not
            // the dependencies. This is what gets packaged in the aar.
            basicCreateMergeResourcesTask(
                testFixturesComponent,
                MergeType.PACKAGE,
                includeDependencies = false,
                processResources = false,
                alsoOutputNotCompiledResources = false,
                flags = flags,
                taskProviderCallback = object: TaskProviderCallback<MergeResources> {
                    override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
                        testFixturesComponent.artifacts
                            .setInitialProvider<RegularFile, MergeResources>(taskProvider)
                            { obj: MergeResources -> obj.publicFile }
                            .withName(SdkConstants.FN_PUBLIC_TXT)
                            .on(InternalArtifactType.PUBLIC_RES)
                    }
                }
            )

            // This task merges all the resources, including the dependencies of this library.
            // This should be unused, except that external libraries might consume it.
            // Also used by the VerifyLibraryResourcesTask (only ran in release builds).
            createMergeResourcesTask(
                testFixturesComponent,
                processResources = false,
                flags = ImmutableSet.of()
            )

            // Task to generate the public.txt for the API that always exists
            // Unlike the internal one which is packaged in the AAR which only exists if the
            // developer has explicitly marked resources as public.
            taskFactory.register(GenerateApiPublicTxtTask.CreationAction(testFixturesComponent))

            taskFactory.register(CompileLibraryResourcesTask.CreationAction(testFixturesComponent))

            // Add a task to generate resource source files, directing the location
            // of the r.txt file to be directly in the bundle.
            createProcessResTask(
                testFixturesComponent,
                null,
                MergeType.PACKAGE,
                testFixturesComponent.services.projectInfo.getProjectBaseName()
            )

            // Only verify resources if in Release and not namespaced.
            if (!testFixturesComponent.debuggable &&
                !extension.aaptOptions.namespaced) {
                createVerifyLibraryResTask(testFixturesComponent)
            }

            registerLibraryRClassTransformStream(testFixturesComponent)
        } else {
            // Create a task to generate empty/mock required resource artifacts.
            taskFactory.register(GenerateEmptyResourceFilesTask.CreateAction(testFixturesComponent))
        }

        // Add a task to merge or generate the manifest
        taskFactory.register(
            ProcessLibraryManifest.CreationAction(
                testFixturesComponent,
                targetSdkVersion = null,
                maxSdkVersion = null,
                manifestPlaceholders = null
            )
        )

        // Add tasks to merge the assets folders
        createMergeAssetsTask(testFixturesComponent)
        taskFactory.register(LibraryAssetCreationAction(testFixturesComponent))

        // compilation tasks

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(testFixturesComponent)

        // Add a task to auto-generate classes for ML model files.
        createMlkitTask(testFixturesComponent)

        val javacTask = createJavacTask(testFixturesComponent)
        addJavacClassesStream(testFixturesComponent)
        setJavaCompilerTask(javacTask, testFixturesComponent)

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (testFixturesComponent
                .services
                .projectOptions
                .get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(ExtractAnnotations.CreationAction(testFixturesComponent))
        }

        val instrumented: Boolean = testFixturesComponent.variantDslInfo.isTestCoverageEnabled

        if (instrumented) {
            createJacocoTask(testFixturesComponent)
        }

        maybeCreateTransformClassesWithAsmTask(testFixturesComponent, instrumented)

        // packaging tasks

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
            BundleLibraryClassesJar.CreationAction(
                testFixturesComponent, PublishedConfigType.RUNTIME_ELEMENTS
            )
        )

        // Also create a directory containing the same classes for incremental dexing
        taskFactory.register(BundleLibraryClassesDir.CreationAction(testFixturesComponent))

        taskFactory.register(BundleLibraryJavaRes.CreationAction(testFixturesComponent))

        // Add a task to create the AAR metadata file
        taskFactory.register(AarMetadataTask.CreationAction(testFixturesComponent))

        // Add a task to write the local lint AAR file
        taskFactory.register(BundleAar.TestFixturesLocalLintCreationAction(testFixturesComponent))

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(ZipMergingTask.CreationAction(testFixturesComponent))

        // now add a task that will take all the classes and java resources and package them
        // into the main and secondary jar files that goes in the AAR.
        // This is used for building the AAR.
        taskFactory.register(
            LibraryAarJarsTask.CreationAction(
                testFixturesComponent,
                minifyEnabled = false
            )
        )

        createBundleTaskForTestFixtures(testFixturesComponent)

        // This hides the assemble test fixtures task from the task list.
        testFixturesComponent.taskContainer.assembleTask.configure { task: Task ->
            task.group = null
        }
    }

    private fun createBundleTaskForTestFixtures(testFixturesComponent: TestFixturesImpl) {
        taskFactory.register(BundleAar.TestFixturesCreationAction(testFixturesComponent))
        testFixturesComponent.taskContainer
            .assembleTask
            .configure { task: Task ->
                task.dependsOn(
                    testFixturesComponent.artifacts.get(
                        SingleArtifact.AAR
                    )
                )
            }
        // publish testFixtures for libraries only
        if (this !is LibraryTaskManager) {
            return
        }
        val variantDependencies = testFixturesComponent.variantDependencies
        testFixturesComponent.variantDslInfo.publishInfo?.components?.forEach {
            val componentName = it.componentName
            val component = project.components.findByName(componentName) as AdhocComponentWithVariants? ?:
            globalScope.componentFactory.adhoc(componentName).let { project.components.add(it) } as AdhocComponentWithVariants
            val apiPub = variantDependencies.getElements(PublishedConfigSpec(PublishedConfigType.API_PUBLICATION, it))
            val runtimePub = variantDependencies.getElements(PublishedConfigSpec(PublishedConfigType.RUNTIME_PUBLICATION, it))
            component.addVariantsFromConfiguration(
                apiPub, ConfigurationVariantMapping("compile", it.isClassifierRequired)
            )
            component.addVariantsFromConfiguration(
                runtimePub, ConfigurationVariantMapping("runtime", it.isClassifierRequired)
            )
        }
    }

    protected fun createVerifyLibraryResTask(component: ComponentCreationConfig) {
        taskFactory.register(VerifyLibraryResourcesTask.CreationAction(component))
        component.taskContainer
            .assembleTask
            .configure { task: Task ->
                task.dependsOn(
                    component.artifacts.get(
                        InternalArtifactType.VERIFIED_LIBRARY_RESOURCES
                    )
                )
            }
    }

    @Suppress("DEPRECATION") // Legacy support (b/195153220)
    protected fun registerLibraryRClassTransformStream(component: ComponentCreationConfig) {
        if (!component.androidResourcesEnabled) {
            return
        }
        val compileRClass: FileCollection = project.files(
            component.artifacts
                .get(InternalArtifactType.COMPILE_R_CLASS_JAR)
        )
        component.transformManager
            .addStream(
                OriginalStream.builder("compile-only-r-class")
                    .addContentTypes(TransformManager.CONTENT_CLASS)
                    .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY)
                    .setFileCollection(compileRClass)
                    .build()
            )
    }

    /** Create tasks for the specified variant.  */
    private fun createTasksForTest(testVariant: TestComponentImpl) {
        createAssembleTask(testVariant)
        val testedVariant = testVariant.testedVariant
        val variantDependencies = testVariant.variantDependencies
        if (testedVariant.renderscript?.supportModeEnabled?.get() == true) {
            project.dependencies
                    .add(
                            variantDependencies.compileClasspath.name,
                            project.files(
                                    globalScope.versionedSdkLoader.flatMap {
                                        it.renderScriptSupportJarProvider
                                    }
                            ))

        }
        if (testVariant.variantType.isApk) { // ANDROID_TEST
            if ((testVariant as ApkCreationConfig).dexingType.isLegacyMultiDexMode()) {
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
            createAndroidTestVariantTasks(testVariant as AndroidTestImpl)
        } else {
            // UNIT_TEST
            createUnitTestVariantTasks(testVariant as UnitTestImpl)
        }
    }

    protected open fun createPrepareLintJarForPublishTask() {
        taskFactory.register(PrepareLintJarForPublish.CreationAction(globalScope))
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
                globalScope.extension.composeOptions.kotlinCompilerExtensionVersion

        val useLiveLiterals = globalScope.extension.composeOptions.useLiveLiterals

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
                val compileKotlin = getKotlinCompile(project, creationConfig)

                compileKotlin.configure {
                    addComposeArgsToKotlinCompile(
                            it, creationConfig, project.files(kotlinExtension), useLiveLiterals)
                }
            } catch (e: UnknownTaskException) {
                // ignore
            }
        }
    }

    // This is for config attribute debugging
    open class ConfigAttrTask : DefaultTask() {
        @get:Internal
        var consumable = false
        @get:Internal
        var resolvable = false
        @TaskAction
        fun run() {
            for (config in project.configurations) {
                val attributes = config.attributes
                if (consumable && config.isCanBeConsumed
                        || resolvable && config.isCanBeResolved) {
                    println(config.name)
                    println("\tcanBeResolved: " + config.isCanBeResolved)
                    println("\tcanBeConsumed: " + config.isCanBeConsumed)
                    for (attr in attributes.keySet()) {
                        println(
                                "\t" + attr.name + ": " + attributes.getAttribute(attr))
                    }
                    if (consumable && config.isCanBeConsumed) {
                        for (artifact in config.artifacts) {
                            println("\tArtifact: " + artifact.name + " (" + artifact.file.name + ")")
                        }
                        for (cv in config.outgoing.variants) {
                            println("\tConfigurationVariant: " + cv.name)
                            for (pa in cv.artifacts) {
                                println("\t\tArtifact: " + pa.file)
                                println("\t\tType:" + pa.type)
                            }
                        }
                    }
                }
            }
        }
    }

    fun createMockableJarTask() {
        project.dependencies
                .add(
                        VariantDependencies.CONFIG_NAME_ANDROID_APIS,
                        project.files(
                                Callable {
                                    globalScope.versionedSdkLoader.flatMap {
                                        it.androidJarProvider
                                    }.orNull
                                } as Callable<*>))

        // Adding this task to help the IDE find the mockable JAR.
        taskFactory.register(
                CREATE_MOCKABLE_JAR_TASK_NAME
        ) { task: Task ->
            task.dependsOn(
                    globalScope.mockableJarArtifact)
        }
    }

    @Suppress("DEPRECATION") // Legacy support (b/195153220)
    protected open fun createDependencyStreams(creationConfig: ComponentCreationConfig) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(creationConfig)
        creationConfig.configureAndLockAsmClassesVisitors(project.objects)
        val transformManager = creationConfig.transformManager

        // This might be consumed by RecalculateFixedStackFrames if that's created
        transformManager.addStream(
                OriginalStream.builder("ext-libs-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                        .setFileCollection(
                                creationConfig.getDependenciesClassesJarsPostAsmInstrumentation(
                                        ArtifactScope.EXTERNAL))
                        .build())

        // Add stream of external java resources if EXTERNAL_LIBRARIES isn't in the set of java res
        // merging scopes.
        if (!getJavaResMergingScopes(creationConfig, com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                        .contains(com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES)) {
            transformManager.addStream(
                    OriginalStream.builder("ext-libs-java-res")
                            .addContentTypes(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                            .addScope(com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                            .setArtifactCollection(
                                    creationConfig
                                            .variantDependencies
                                            .getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH,
                                                    ArtifactScope.EXTERNAL,
                                                    AndroidArtifacts.ArtifactType.JAVA_RES))
                            .build())
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        transformManager.addStream(
                OriginalStream.builder("sub-projects-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS)
                        .setFileCollection(
                                creationConfig.getDependenciesClassesJarsPostAsmInstrumentation(
                                        ArtifactScope.PROJECT))
                        .build())

        // same for the java resources, if SUB_PROJECTS isn't in the set of java res merging scopes.
        if (!getJavaResMergingScopes(creationConfig, com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES).contains(
                com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS)) {
            transformManager.addStream(
                    OriginalStream.builder("sub-projects-java-res")
                            .addContentTypes(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                            .addScope(com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS)
                            .setArtifactCollection(
                                    creationConfig
                                            .variantDependencies
                                            .getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH,
                                                    ArtifactScope.PROJECT,
                                                    AndroidArtifacts.ArtifactType.JAVA_RES))
                            .build())
        }
        val variantScope = creationConfig.variantScope

        // if variantScope.consumesFeatureJars(), add streams of classes from features or
        // dynamic-features.
        // The main dex list calculation for the bundle also needs the feature classes for reference
        // only
        if (variantScope.consumesFeatureJars() || creationConfig.needsMainDexListForBundle) {
            transformManager.addStream(
                    OriginalStream.builder("metadata-classes")
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(InternalScope.FEATURES)
                            .setArtifactCollection(
                                    creationConfig
                                            .variantDependencies
                                            .getArtifactCollection(ConsumedConfigType.REVERSE_METADATA_VALUES,
                                                    ArtifactScope.PROJECT,
                                                    AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES))
                            .build())
        }

        // provided only scopes.
        transformManager.addStream(
                OriginalStream.builder("provided-classes")
                        .addContentTypes(TransformManager.CONTENT_CLASS)
                        .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY)
                        .setFileCollection(variantScope.providedOnlyClasspath)
                        .build())
        creationConfig.onTestedConfig<Any?> { testedConfig: VariantCreationConfig ->
            val testedCodeDeps: FileCollection = if (testedConfig is ComponentImpl) {
                testedConfig.getDependenciesClassesJarsPostAsmInstrumentation(ArtifactScope.ALL)
            } else {
                testedConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        ArtifactScope.ALL,
                        if (creationConfig.services.projectOptions[
                                    BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION]) {
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                        } else {
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                        }
                    )
            }
            transformManager.addStream(
                    OriginalStream.builder("tested-code-deps")
                            .addContentTypes(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                            .addScope(com.android.build.api.transform.QualifiedContent.Scope.TESTED_CODE)
                            .setFileCollection(testedCodeDeps)
                            .build())
            null
        }
    }

    fun createMergeApkManifestsTask(component: ComponentImpl) {
        val apkVariantData = component.variantData as ApkVariantData
        val screenSizes = apkVariantData.compatibleScreens

        // FIXME
        val creationConfig = component as ApkCreationConfig
        taskFactory.register(
                CompatibleScreensManifest.CreationAction(creationConfig, screenSizes))
        val processManifestTask = createMergeManifestTasks(creationConfig)
        val taskContainer = component.taskContainer
        if (taskContainer.microApkTask != null) {
            processManifestTask.dependsOn(taskContainer.microApkTask)
        }
    }

    /** Creates the merge manifests task.  */
    protected open fun createMergeManifestTasks(
            creationConfig: ApkCreationConfig): TaskProvider<out ManifestProcessorTask?> {
        taskFactory.register(ProcessManifestForBundleTask.CreationAction(creationConfig))
        taskFactory.register(
                ProcessManifestForMetadataFeatureTask.CreationAction(creationConfig))
        taskFactory.register(ProcessManifestForInstantAppTask.CreationAction(creationConfig))
        taskFactory.register(ProcessPackagedManifestTask.CreationAction(creationConfig))
        taskFactory.register(GenerateManifestJarTask.CreationAction(creationConfig))
        taskFactory.register(ProcessApplicationManifest.CreationAction(creationConfig))
        return taskFactory.register(
                ProcessMultiApkApplicationManifest.CreationAction(creationConfig))
    }

    protected fun createProcessTestManifestTask(creationConfig: TestCreationConfig) {
        taskFactory.register(ProcessTestManifest.CreationAction(creationConfig))
    }

    fun createRenderscriptTask(creationConfig: ConsumableCreationConfig) {
        if (creationConfig.buildFeatures.renderScript) {
            val renderscript = creationConfig.renderscript
                ?: throw RuntimeException(
                        "Renderscript is enabled but no configuration available, please file a bug.")
            val taskContainer = creationConfig.taskContainer
            val rsTask = taskFactory.register(
                RenderscriptCompile.CreationAction(creationConfig, renderscript))
            taskContainer.resourceGenTask.dependsOn(rsTask)
            // since rs may generate Java code, always set the dependency.
            taskContainer.sourceGenTask.dependsOn(rsTask)
        }
    }

    fun createMergeResourcesTask(
            creationConfig: ComponentCreationConfig,
            processResources: Boolean,
            flags: ImmutableSet<MergeResources.Flag?>) {
        val alsoOutputNotCompiledResources = (creationConfig.variantType.isApk
                && !creationConfig.variantType.isForTesting
                && creationConfig.useResourceShrinker())
        val includeDependencies = true
        val mergeResourcesTask = basicCreateMergeResourcesTask(
                creationConfig,
                MergeType.MERGE,
                includeDependencies,
                processResources,
                alsoOutputNotCompiledResources,
                flags,
                null /*configCallback*/)
        if (creationConfig
                        .services
                        .projectOptions[BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP]) {
            taskFactory.register(
                    MapSourceSetPathsTask.CreateAction(
                        creationConfig,
                        mergeResourcesTask,
                        includeDependencies
                    ))
        }
    }

    /** Defines the merge type for [.basicCreateMergeResourcesTask]  */
    enum class MergeType {

        /** Merge all resources with all the dependencies resources (i.e. "big merge").  */
        MERGE {

            override val outputType: Single<Directory>
                get() = MERGED_RES
        },

        /**
         * Merge all resources without the dependencies resources for an aar (i.e. "small merge").
         */
        PACKAGE {

            override val outputType: Single<Directory>
                get() = PACKAGED_RES
        };

        abstract val outputType: Single<Directory>
    }

    fun basicCreateMergeResourcesTask(
            creationConfig: ComponentCreationConfig,
            mergeType: MergeType,
            includeDependencies: Boolean,
            processResources: Boolean,
            alsoOutputNotCompiledResources: Boolean,
            flags: ImmutableSet<MergeResources.Flag?>,
            taskProviderCallback: TaskProviderCallback<MergeResources>?
    ): TaskProvider<MergeResources> {
        val mergedNotCompiledDir = if (alsoOutputNotCompiledResources) File(
                creationConfig.services.projectInfo.getIntermediatesDir()
                        .toString() + "/merged-not-compiled-resources/"
                        + creationConfig.dirName) else null
        val mergeResourcesTask: TaskProvider<MergeResources> = taskFactory.register(
                MergeResources.CreationAction(
                        creationConfig,
                        mergeType,
                        mergedNotCompiledDir,
                        includeDependencies,
                        processResources,
                        flags,
                        creationConfig.variantType.isAar),
                null,
                null,
                taskProviderCallback)
        if (extension.testOptions.unitTests.isIncludeAndroidResources) {
            creationConfig.taskContainer.compileTask.dependsOn(mergeResourcesTask)
        }
        return mergeResourcesTask
    }

    fun createMergeAssetsTask(creationConfig: ComponentCreationConfig) {
        taskFactory.register(MergeSourceSetFolders.MergeAppAssetCreationAction(creationConfig))
    }

    fun createMergeJniLibFoldersTasks(creationConfig: ConsumableCreationConfig) {
        // merge the source folders together using the proper priority.
        taskFactory.register(
                MergeSourceSetFolders.MergeJniLibFoldersCreationAction(creationConfig))
        taskFactory.register(MergeNativeLibsTask.CreationAction(creationConfig))
    }

    fun createBuildConfigTask(creationConfig: VariantCreationConfig) {
        if (creationConfig.buildFeatures.buildConfig) {
            val generateBuildConfigTask =
                    taskFactory.register(GenerateBuildConfig.CreationAction(creationConfig))
            val isBuildConfigBytecodeEnabled = creationConfig
                    .services
                    .projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            if (!isBuildConfigBytecodeEnabled
                    || !creationConfig.variantDslInfo.getBuildConfigFields().isEmpty()) {
                creationConfig.taskContainer.sourceGenTask.dependsOn(generateBuildConfigTask)
            }
        }
    }

    fun createGenerateResValuesTask(creationConfig: ComponentCreationConfig) {
        if (creationConfig.buildFeatures.resValues) {
            val generateResValuesTask =
                    taskFactory.register(GenerateResValues.
                    CreationAction(creationConfig))
            creationConfig.taskContainer.resourceGenTask.dependsOn(generateResValuesTask)
        }
    }

    fun createMlkitTask(creationConfig: ComponentCreationConfig) {
        if (creationConfig.buildFeatures.mlModelBinding) {
            taskFactory.register(
                    MergeMlModelsSourceFoldersCreationAction(
                            creationConfig))
            val generateMlModelClassTask =
                    taskFactory.register(GenerateMlModelClass.CreationAction(creationConfig))
            creationConfig.taskContainer.sourceGenTask.dependsOn(generateMlModelClassTask)
        }
    }

    fun createApkProcessResTask(creationConfig: VariantCreationConfig) {
        val variantType = creationConfig.variantType
        val packageOutputType: InternalArtifactType<Directory>? =
                if (variantType.isApk && !variantType.isForTesting) FEATURE_RESOURCE_PKG else null
        createApkProcessResTask(creationConfig, packageOutputType)
        if (creationConfig.variantScope.consumesFeatureJars()) {
            taskFactory.register(MergeAaptProguardFilesCreationAction(creationConfig))
        }
    }

    private fun createApkProcessResTask(
            creationConfig: ComponentCreationConfig,
            packageOutputType: Single<Directory>?) {
        val projectInfo = creationConfig.services.projectInfo

        // Check AAR metadata files
        taskFactory.register(CheckAarMetadataTask.CreationAction(creationConfig))

        // Create the APK_ file with processed resources and manifest. Generate the R class.
        createProcessResTask(
                creationConfig,
                packageOutputType,
                MergeType.MERGE,
                projectInfo.getProjectBaseName())
        val projectOptions = creationConfig.services.projectOptions
        val nonTransitiveR = projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
        val namespaced: Boolean = projectInfo.getExtension().aaptOptions.namespaced

        // TODO(b/138780301): Also use compile time R class in android tests.
        if ((projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS] || nonTransitiveR)
                && !creationConfig.variantType.isForTesting
                && !namespaced) {
            // Generate the COMPILE TIME only R class using the local resources instead of waiting
            // for the above full link to finish. Linking will still output the RUN TIME R class.
            // Since we're gonna use AAPT2 to generate the keep rules, do not generate them here.
            createProcessResTask(
                    creationConfig,
                    packageOutputType,
                    MergeType.PACKAGE,
                    projectInfo.getProjectBaseName())
        }
    }

    fun createProcessResTask(
            creationConfig: ComponentCreationConfig,
            packageOutputType: Single<Directory>?,
            mergeType: MergeType,
            baseName: String) {
        val scope = creationConfig.variantScope
        val variantData = creationConfig.variantData
        variantData.calculateFilters(creationConfig.globalScope.extension.splits)

        // The manifest main dex list proguard rules are always needed for the bundle,
        // even if legacy multidex is not explicitly enabled.
        val useAaptToGenerateLegacyMultidexMainDexProguardRules =
                (creationConfig is ApkCreationConfig
                        && creationConfig
                        .dexingType
                        .needsMainDexList)
        if (creationConfig.services.projectInfo.getExtension()
                        .aaptOptions.namespaced) {
            // TODO: make sure we generate the proguard rules in the namespaced case.
            NamespacedResourcesTaskManager(taskFactory, creationConfig)
                    .createNamespacedResourceTasks(
                            packageOutputType,
                            baseName,
                            useAaptToGenerateLegacyMultidexMainDexProguardRules)
            val rFiles: FileCollection = project.files(
                    creationConfig.artifacts.get(RUNTIME_R_CLASS_CLASSES))
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            creationConfig
                    .transformManager
                    .addStream(
                            OriginalStream.builder("final-r-classes")
                                    .addContentTypes(
                                            if (scope.needsJavaResStreams) TransformManager.CONTENT_JARS else setOf(
                                                com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES))
                                    .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                                    .setFileCollection(rFiles)
                                    .build())
            creationConfig
                    .artifacts
                    .appendTo(
                            MultipleArtifact.ALL_CLASSES_DIRS,
                            creationConfig.artifacts.get(RUNTIME_R_CLASS_CLASSES));
            return
        }
        createNonNamespacedResourceTasks(
                creationConfig,
                packageOutputType,
                mergeType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules)
    }

    private fun createNonNamespacedResourceTasks(
            creationConfig: ComponentCreationConfig,
            packageOutputType: Single<Directory>?,
            mergeType: MergeType,
            baseName: String,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val artifacts = creationConfig.artifacts
        val projectOptions = creationConfig.services.projectOptions
        when(mergeType) {
            MergeType.PACKAGE -> {
                // MergeType.PACKAGE means we will only merged the resources from our current module
                // (little merge). This is used for finding what goes into the AAR (packaging), and also
                // for parsing the local resources and merging them with the R.txt files from its
                // dependencies to write the R.txt for this module and R.jar for this module and its
                // dependencies.

                // First collect symbols from this module.
                taskFactory.register(ParseLibraryResourcesTask.CreateAction(creationConfig))

                // Only generate the keep rules when we need them. We don't need to generate them here
                // for non-library modules since AAPT2 will generate them from MergeType.MERGE.
                if (generatesProguardOutputFile(creationConfig) &&
                    creationConfig.variantType.isAar) {
                    taskFactory.register(
                            GenerateLibraryProguardRulesTask.CreationAction(creationConfig))
                }
                val nonTransitiveRClassInApp = projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
                val compileTimeRClassInApp = projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]
                // Generate the R class for a library using both local symbols and symbols
                // from dependencies.
                // TODO: double check this (what about dynamic features?)
                if (!nonTransitiveRClassInApp || compileTimeRClassInApp || creationConfig.variantType.isAar) {
                    taskFactory.register(GenerateLibraryRFileTask.CreationAction(
                        creationConfig,
                        creationConfig.variantType.isAar
                    ))
                }
            }
            MergeType.MERGE -> {
                // MergeType.MERGE means we merged the whole universe.
                taskFactory.register(
                        LinkApplicationAndroidResourcesTask.CreationAction(
                                creationConfig,
                                useAaptToGenerateLegacyMultidexMainDexProguardRules,
                                mergeType,
                                baseName,
                                creationConfig.variantType.isAar))
                if (packageOutputType != null) {
                    creationConfig.artifacts.republish(PROCESSED_RES, packageOutputType)
                }

                // TODO: also support stable IDs for the bundle (does it matter?)
                // create the task that creates the aapt output for the bundle.
                if (creationConfig is ApkCreationConfig
                        && !creationConfig.variantType.isForTesting) {
                    taskFactory.register(
                            LinkAndroidResForBundleTask.CreationAction(
                                    creationConfig))
                }
                artifacts.appendTo(
                        MultipleArtifact.ALL_CLASSES_JARS,
                        artifacts.get(COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR));

                if (!creationConfig.debuggable &&
                        !creationConfig.variantType.isForTesting &&
                         projectOptions[BooleanOption.ENABLE_RESOURCE_OPTIMIZATIONS]) {
                    taskFactory.register(OptimizeResourcesTask.CreateAction(creationConfig))
                }
            }
            else -> throw RuntimeException("Unhandled merge type : $mergeType")
        }
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     *
     * @param creationConfig the scope of the variant being processed.
     * @param contentType the contentType of java resources, must be RESOURCES or NATIVE_LIBS
     * @return the list of scopes for which to merge the java resources.
     */
    @Suppress("DEPRECATION") // Legacy support (b/195153220)
    protected abstract fun getJavaResMergingScopes(
            creationConfig: ComponentCreationConfig,
            contentType: com.android.build.api.transform.QualifiedContent.ContentType): Set<com.android.build.api.transform.QualifiedContent.ScopeType>

    /**
     * Creates the java resources processing tasks.
     *
     *
     * The java processing will happen in two steps:
     *
     *
     *  * [Sync] task configured with [ProcessJavaResTask.CreationAction] will sync
     * all source folders into a single folder identified by [InternalArtifactType]
     *  * [MergeJavaResourceTask] will take the output of this merge plus the dependencies
     * and will create a single merge with the [PackagingOptions] settings applied.
     *
     *
     * This sets up only the Sync part. The java res merging is setup via [ ][.createMergeJavaResTask]
     */
    fun createProcessJavaResTask(creationConfig: ComponentCreationConfig) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        taskFactory.register(ProcessJavaResTask.CreationAction(creationConfig))

        // create the stream generated from this task, but only if a library with custom transforms,
        // in which case the custom transforms must be applied before java res merging.
        if (creationConfig.variantScope.needsJavaResStreams) {
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            creationConfig
                    .transformManager
                    .addStream(
                            OriginalStream.builder("processed-java-res")
                                    .addContentType(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
                                    .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                                    .setFileCollection(
                                            creationConfig
                                                    .services
                                                    .fileCollection(
                                                            creationConfig
                                                                    .artifacts
                                                                    .get(
                                                                            JAVA_RES)))
                                    .build())
        }
    }

    /**
     * Sets up the Merge Java Res task.
     *
     * @see .createProcessJavaResTask
     */
    fun createMergeJavaResTask(creationConfig: ConsumableCreationConfig) {
        val transformManager = creationConfig.transformManager

        // Compute the scopes that need to be merged.
        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        val mergeScopes = getJavaResMergingScopes(creationConfig, com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES)
        taskFactory.register(MergeJavaResourceTask.CreationAction(mergeScopes, creationConfig))

        // also add a new merged java res stream if needed.
        if (creationConfig.getNeedsMergedJavaResStream()) {
            val mergedJavaResProvider = creationConfig.artifacts.get(MERGED_JAVA_RES)
            transformManager.addStream(
                    OriginalStream.builder("merged-java-res")
                            .addContentTypes(TransformManager.CONTENT_RESOURCES)
                            .addScopes(mergeScopes)
                            .setFileCollection(project.layout.files(mergedJavaResProvider))
                            .build())
        }
    }

    fun createAidlTask(creationConfig: VariantCreationConfig) {
        if (creationConfig.buildFeatures.aidl) {
            val taskContainer = creationConfig.taskContainer
            val aidlCompileTask = taskFactory.register(AidlCompile.CreationAction(creationConfig))
            taskContainer.sourceGenTask.dependsOn(aidlCompileTask)
        }
    }

    fun createShaderTask(creationConfig: VariantCreationConfig) {
        if (creationConfig.buildFeatures.shaders) {
            // merge the shader folders together using the proper priority.
            taskFactory.register(
                    MergeShaderSourceFoldersCreationAction(
                            creationConfig))

            // compile the shaders
            val shaderCompileTask =
                    taskFactory.register(ShaderCompile.CreationAction(creationConfig))
            creationConfig.taskContainer.assetGenTask.dependsOn(shaderCompileTask)
        }
    }

    protected open fun postJavacCreation(creationConfig: ComponentCreationConfig) {
        creationConfig
                .artifacts
                .appendAll(
                        MultipleArtifact.ALL_CLASSES_JARS,
                        creationConfig.variantData.allPreJavacGeneratedBytecode.getRegularFiles(
                                project.layout.projectDirectory
                        ));

        creationConfig
                .artifacts
                .appendAll(
                        MultipleArtifact.ALL_CLASSES_DIRS,
                        creationConfig.variantData.allPreJavacGeneratedBytecode.getDirectories(
                            project.layout.projectDirectory
                        ));

        creationConfig
                .artifacts
                .appendAll(
                        MultipleArtifact.ALL_CLASSES_JARS,
                        creationConfig.variantData.allPostJavacGeneratedBytecode.getRegularFiles(
                            project.layout.projectDirectory
                        ));

        creationConfig
                .artifacts
                .appendAll(
                        MultipleArtifact.ALL_CLASSES_DIRS,
                        creationConfig.variantData.allPostJavacGeneratedBytecode.getDirectories(
                            project.layout.projectDirectory
                        ));
        creationConfig
                .artifacts
                .appendTo(
                        MultipleArtifact.ALL_CLASSES_DIRS,
                        creationConfig.artifacts.get(JAVAC));
    }

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    fun createJavacTask(
            creationConfig: ComponentCreationConfig
    ): TaskProvider<out JavaCompile> {
        val usingKapt = isKotlinKaptPluginApplied(project)
        taskFactory.register(JavaPreCompileTask.CreationAction(creationConfig, usingKapt))
        val javacTask: TaskProvider<out JavaCompile> =
            taskFactory.register(JavaCompileCreationAction(creationConfig, usingKapt))
        postJavacCreation(creationConfig)
        return javacTask
    }

    /**
     * Add stream of classes compiled by javac to transform manager.
     *
     *
     * This should not be called for classes that will also be compiled from source by jack.
     */
    @Suppress("DEPRECATION") // Legacy support (b/195153220)
    protected fun addJavacClassesStream(creationConfig: ComponentCreationConfig) {
        // create separate streams for all the classes coming from javac, pre/post hooks and R.
        val transformManager = creationConfig.transformManager
        val needsJavaResStreams = creationConfig.variantScope.needsJavaResStreams
        transformManager.addStream(
                OriginalStream.builder("all-classes") // Need both classes and resources because some annotation
                        // processors generate resources
                        .addContentTypes(
                                if (needsJavaResStreams) TransformManager.CONTENT_JARS else setOf(
                                    com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES))
                        .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                        .setFileCollection(creationConfig.artifacts.getAllClasses())
                        .build())
    }

    /** Creates the tasks to build unit tests.  */
    private fun createUnitTestVariantTasks(
            unitTestCreationConfig: UnitTestCreationConfig) {
        val taskContainer = unitTestCreationConfig.taskContainer
        val testedVariant = unitTestCreationConfig.testedConfig
        val includeAndroidResources = extension.testOptions.unitTests
                .isIncludeAndroidResources
        createAnchorTasks(unitTestCreationConfig)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(unitTestCreationConfig)

        // process java resources
        createProcessJavaResTask(unitTestCreationConfig)
        if (includeAndroidResources) {
            // merging task for assets in unit tests.
            taskFactory.register(MergeAssetsForUnitTest.CreationAction(unitTestCreationConfig))

            if (testedVariant.variantType.isAar) {
                // Add a task to process the manifest
                createProcessTestManifestTask(unitTestCreationConfig)

                // Add a task to create the res values
                createGenerateResValuesTask(unitTestCreationConfig)

                // Add a task to merge the assets folders
                createMergeAssetsTask(unitTestCreationConfig)
                createMergeResourcesTask(unitTestCreationConfig, true, ImmutableSet.of())
                // Add a task to process the Android Resources and generate source files
                createApkProcessResTask(unitTestCreationConfig, FEATURE_RESOURCE_PKG)
                taskFactory.register(PackageForUnitTest.CreationAction(unitTestCreationConfig))

                // Add data binding tasks if enabled
                createDataBindingTasksIfNecessary(unitTestCreationConfig)
            } else if (testedVariant.variantType.isApk) {
                // The IDs will have been inlined for an non-namespaced application
                // so just re-export the artifacts here.
                unitTestCreationConfig
                        .artifacts
                        .copy(PROCESSED_RES, testedVariant.artifacts)
                unitTestCreationConfig
                        .artifacts
                        .copy(MultipleArtifact.ASSETS, testedVariant.artifacts)
                taskFactory.register(PackageForUnitTest.CreationAction(unitTestCreationConfig))
            } else {
                throw IllegalStateException(
                        "Tested variant "
                                + testedVariant.name
                                + " in "
                                + project.path
                                + " must be a library or an application to have unit tests.")
            }
            taskFactory.register(MergeAssetsForUnitTest.CreationAction(unitTestCreationConfig.testedConfig))
            val generateTestConfig = taskFactory.register(
                    GenerateTestConfig.
                    CreationAction(unitTestCreationConfig))
            val compileTask = taskContainer.compileTask
            compileTask.dependsOn(generateTestConfig)
            // The GenerateTestConfig task has 2 types of inputs: direct inputs and indirect inputs.
            // Only the direct inputs are registered with Gradle, whereas the indirect inputs are
            // not (see that class for details).
            // Since the compile task also depends on the indirect inputs to the GenerateTestConfig
            // task, making the compile task depend on the GenerateTestConfig task is not enough, we
            // also need to register those inputs with Gradle explicitly here. (We can't register
            // @Nested objects programmatically, so it's important to keep these inputs consistent
            // with those defined in TestConfigInputs.)
            compileTask.configure { task: Task ->
                val testConfigInputs = TestConfigInputs(unitTestCreationConfig)
                val taskInputs = task.inputs
                taskInputs.property(
                        "isUseRelativePathEnabled",
                        testConfigInputs.isUseRelativePathEnabled)
                taskInputs
                        .files(testConfigInputs.resourceApk)
                        .withPropertyName("resourceApk")
                        .optional()
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs
                        .files(testConfigInputs.mergedAssets)
                        .withPropertyName("mergedAssets")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs
                        .files(testConfigInputs.mergedManifest)
                        .withPropertyName("mergedManifest")
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                taskInputs.property(
                        "mainVariantOutput", testConfigInputs.mainVariantOutput)
                taskInputs.property(
                        "packageNameOfFinalRClassProvider",
                        testConfigInputs.packageNameOfFinalRClass)
            }
        } else {
            if (testedVariant.variantType.isAar && testedVariant.androidResourcesEnabled) {
                // With compile classpath R classes, we need to generate a dummy R class for unit
                // tests
                // See https://issuetracker.google.com/143762955 for more context.
                taskFactory.register(
                        TestRuntimeStubRClassCreationAction(
                                unitTestCreationConfig))
            }
        }

        // :app:compileDebugUnitTestSources should be enough for running tests from AS, so add
        // dependencies on tasks that prepare necessary data files.
        val compileTask = taskContainer.compileTask
        compileTask.dependsOn(taskContainer.processJavaResourcesTask,
                testedVariant.taskContainer.processJavaResourcesTask)
        val javacTask = createJavacTask(unitTestCreationConfig)
        addJavacClassesStream(unitTestCreationConfig)
        setJavaCompilerTask(javacTask, unitTestCreationConfig)
        // This should be done automatically by the classpath
        //        TaskFactoryUtils.dependsOn(javacTask,
        // testedVariantScope.getTaskContainer().getJavacTask());
        maybeCreateTransformClassesWithAsmTask(unitTestCreationConfig, false)


        // TODO: use merged java res for unit tests (bug 118690729)
        createRunUnitTestTask(unitTestCreationConfig)

        // This hides the assemble unit test task from the task list.
        taskContainer.assembleTask.configure { task: Task -> task.group = null }
    }

    /** Creates the tasks to build android tests.  */
    private fun createAndroidTestVariantTasks(androidTestProperties: AndroidTestImpl) {
        createAnchorTasks(androidTestProperties)

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(androidTestProperties)

        // Add a task to process the manifest
        createProcessTestManifestTask(androidTestProperties)

        // Add a task to create the res values
        createGenerateResValuesTask(androidTestProperties)

        // Add a task to compile renderscript files.
        createRenderscriptTask(androidTestProperties)

        // Add a task to merge the resource folders
        createMergeResourcesTask(androidTestProperties, true, ImmutableSet.of())

        // Add tasks to compile shader
        createShaderTask(androidTestProperties)

        // Add a task to merge the assets folders
        createMergeAssetsTask(androidTestProperties)
        taskFactory.register(CompressAssetsTask.CreationAction(androidTestProperties))

        // Add a task to create the BuildConfig class
        createBuildConfigTask(androidTestProperties)

        // Add a task to generate resource source files
        createApkProcessResTask(androidTestProperties)

        // process java resources
        createProcessJavaResTask(androidTestProperties)
        createAidlTask(androidTestProperties)

        // add tasks to merge jni libs.
        createMergeJniLibFoldersTasks(androidTestProperties)

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(androidTestProperties)

        // Add a task to compile the test application
        val javacTask = createJavacTask(androidTestProperties)
        addJavacClassesStream(androidTestProperties)
        setJavaCompilerTask(javacTask, androidTestProperties)
        createPostCompilationTasks(androidTestProperties)

        // Add tasks to produce the signing config files
        createValidateSigningTask(androidTestProperties)
        taskFactory.register(SigningConfigWriterTask.CreationAction(androidTestProperties))
        taskFactory.register(SigningConfigVersionsWriterTask.CreationAction(androidTestProperties))
        createPackagingTask(androidTestProperties)
        taskFactory.configure(
                ASSEMBLE_ANDROID_TEST
        ) { assembleTest: Task ->
            assembleTest.dependsOn(
                    androidTestProperties
                            .taskContainer
                            .assembleTask
                            .name)
        }

        createConnectedTestForVariant(androidTestProperties)
    }

    /** Returns the full path of a task given its name.  */
    private fun getTaskPath(taskName: String): String {
        return if (project.rootProject === project) ":$taskName" else project.path + ':' + taskName
    }

    private fun createRunUnitTestTask(unitTestCreationConfig: UnitTestCreationConfig) {
        val ant = JacocoConfigurations.getJacocoAntTaskConfiguration(
            project, JacocoTask.getJacocoVersion(unitTestCreationConfig))

        val runTestsTask =
                taskFactory.register(AndroidUnitTest.CreationAction(unitTestCreationConfig))
        taskFactory.configure(JavaPlugin.TEST_TASK_NAME) {
                test: Task -> test.dependsOn(runTestsTask)
        }

        if (unitTestCreationConfig.isTestCoverageEnabled) {
            unitTestCreationConfig.services.projectInfo.getProject().plugins.withType(
                JacocoPlugin::class.java
            ) {
                // Jacoco plugin is applied and test coverage enabled, ∴ generate coverage report.
                taskFactory.register(
                    JacocoReportTask.CreateActionUnitTest(
                        unitTestCreationConfig,
                        ant
                    )
                )
            }
       }
    }

    private fun createTopLevelTestTasks() {
        createMockableJarTask()
        val reportTasks: MutableList<String> = mutableListOf()
        val providers = extension.deviceProviders

        // If more than one flavor, create a report aggregator task and make this the parent
        // task for all new connected tasks.  Otherwise, create a top level connectedAndroidTest
        // Task.
        val connectedAndroidTestTask: TaskProvider<out Task>
        if (hasFlavors) {
            connectedAndroidTestTask = taskFactory.register(
                    AndroidReportTask.CreationAction(
                            globalScope,
                            AndroidReportTask.CreationAction.TaskKind.CONNECTED, projectInfo))
            reportTasks.add(connectedAndroidTestTask.name)
        } else {
            connectedAndroidTestTask = taskFactory.register(
                    CONNECTED_ANDROID_TEST
            ) { connectedTask: Task ->
                connectedTask.group = JavaBasePlugin.VERIFICATION_GROUP
                connectedTask.description = (
                        "Installs and runs instrumentation tests "
                                + "for all flavors on connected devices.")
            }
        }
        taskFactory.configure(
                CONNECTED_CHECK) { check: Task -> check.dependsOn(connectedAndroidTestTask.name) }
        val deviceAndroidTestTask: TaskProvider<out Task>
        // if more than one provider tasks, either because of several flavors, or because of
        // more than one providers, then create an aggregate report tasks for all of them.
        if (providers.size > 1 || hasFlavors) {
            deviceAndroidTestTask = taskFactory.register(
                    AndroidReportTask.CreationAction(
                            globalScope,
                            AndroidReportTask.CreationAction.TaskKind.DEVICE_PROVIDER, projectInfo))
            reportTasks.add(deviceAndroidTestTask.name)
        } else {
            deviceAndroidTestTask = taskFactory.register(
                    DEVICE_ANDROID_TEST
            ) { providerTask: Task ->
                providerTask.group = JavaBasePlugin.VERIFICATION_GROUP
                providerTask.description = (
                        "Installs and runs instrumentation tests "
                                + "using all Device Providers.")
            }
        }
        taskFactory.configure(
                DEVICE_CHECK) { check: Task -> check.dependsOn(deviceAndroidTestTask.name) }

        // Create top level unit test tasks.
        taskFactory.register(
                JavaPlugin.TEST_TASK_NAME
        ) { unitTestTask: Task ->
            unitTestTask.group = JavaBasePlugin.VERIFICATION_GROUP
            unitTestTask.description = "Run unit tests for all variants."
        }
        taskFactory.configure(
                JavaBasePlugin.CHECK_TASK_NAME
        ) { check: Task -> check.dependsOn(JavaPlugin.TEST_TASK_NAME) }

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants, or
        // or several device providers).
        // To do that, the report tasks must run even if one of their dependent tasks (flavor
        // or specific provider tasks) fails, when --continue is used, and the report task is
        // meant to run (== is in the task graph).
        // To do this, we make the children tasks ignore their errors (ie they won't fail and
        // stop the build).
        //TODO: move to mustRunAfter once is stable.
        if (reportTasks.isNotEmpty() && project.gradle.startParameter
                        .isContinueOnFailure) {
            project.gradle
                    .taskGraph
                    .whenReady { taskGraph: TaskExecutionGraph ->
                        for (reportTask in reportTasks) {
                            if (taskGraph.hasTask(getTaskPath(reportTask))) {
                                taskFactory.configure(
                                        reportTask
                                ) { task: Task -> (task as AndroidReportTask).setWillRun() }
                            }
                        }
                    }
        }
    }

    protected fun createTestDevicesTasks() {
        if (!shouldEnableUtp(projectOptions, extension.testOptions, variantType = null)) {
            return
        }

        if (extension.testOptions.devices.isNotEmpty()) {
            logger.warn(
                "WARNING: The Gradle Managed Device DSL and associated tests are experimental")
        }
        val managedDevices = mutableListOf<ManagedVirtualDevice>()
        extension
                .testOptions
                .devices
                .forEach { device ->
                    if (device is ManagedVirtualDevice) {
                        managedDevices.add(device)
                    } else {
                        error("Unsupported managed device type: ${device.javaClass}")
                    }
                }
        taskFactory.register(
                ManagedDeviceCleanTask.CreationAction(
                    "cleanManagedDevices",
                    globalScope,
                    managedDevices))
        val allDevices = taskFactory.register(
            ALL_DEVICES_CHECK
        ) { allDevicesCheckTask: Task ->
            allDevicesCheckTask.description =
                "Runs all device checks on all managed devices defined in the TestOptions dsl."
            allDevicesCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
        }

        for (device in managedDevices) {
            taskFactory.register(
                ManagedDeviceSetupTask.CreationAction(
                    setupTaskName(device),
                    device,
                    globalScope))

            val deviceAllVariantsTask = taskFactory.register(
                managedDeviceAllVariantsTaskName(device)
            ) { deviceVariantTask: Task ->
                deviceVariantTask.description =
                    "Runs all device checks on the managed device ${device.name}."
                deviceVariantTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
            allDevices.dependsOn(deviceAllVariantsTask)
        }

        val deviceGroups = extension.testOptions.deviceGroups
        for (group in deviceGroups) {
            taskFactory.register(
                managedDeviceGroupAllVariantsTaskName(group)
            ) { deviceGroupTask: Task ->
                deviceGroupTask.description =
                    "Runs all device checks on all devices defined in group ${group.name}."
                deviceGroupTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
        }
    }

    private fun createConnectedTestForVariant(androidTestProperties: AndroidTestImpl) {
        val testedVariant = androidTestProperties.testedVariant
        val isLibrary = testedVariant.variantType.isAar
        val testData: AbstractTestDataImpl = if (testedVariant.variantType.isDynamicFeature) {
            BundleTestDataImpl(
                    androidTestProperties.namespace,
                    androidTestProperties,
                    androidTestProperties.artifacts.get(SingleArtifact.APK),
                    getFeatureName(project.path),
                    testedVariant
                            .variantDependencies
                            .getArtifactFileCollection(ConsumedConfigType.RUNTIME_CLASSPATH,
                                    ArtifactScope.PROJECT,
                                    AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE))
        } else {
            val testedApkFileCollection =
                    project.files(testedVariant.artifacts.get(SingleArtifact.APK))
            TestDataImpl(
                    androidTestProperties.namespace,
                    androidTestProperties,
                    androidTestProperties.artifacts.get(SingleArtifact.APK),
                    if (isLibrary) null else testedApkFileCollection)
        }
        configureTestData(androidTestProperties, testData)
        val connectedCheckSerials: Provider<List<String>> =
            taskFactory.named(CONNECTED_CHECK).flatMap { test ->
                (test as DeviceSerialTestTask).serialValues
            }
        val connectedTask = taskFactory.register(
                DeviceProviderInstrumentTestTask.CreationAction(
                        androidTestProperties, testData, connectedCheckSerials))
        taskFactory.configure(
                CONNECTED_ANDROID_TEST
        ) { connectedAndroidTest: Task -> connectedAndroidTest.dependsOn(connectedTask) }
        if (testedVariant.variantDslInfo.isTestCoverageEnabled) {
            val jacocoAntConfiguration = JacocoConfigurations.getJacocoAntTaskConfiguration(
                    project, JacocoTask.getJacocoVersion(androidTestProperties))
            val reportTask = taskFactory.register(
                    JacocoReportTask.CreationActionConnectedTest(
                            androidTestProperties, jacocoAntConfiguration))
            testedVariant.taskContainer.coverageReportTask.dependsOn(reportTask)
            taskFactory.configure(
                CONNECTED_ANDROID_TEST
            ) { connectedAndroidTest: Task -> connectedAndroidTest.dependsOn(reportTask) }
        }
        val providers = extension.deviceProviders
        if (providers.isNotEmpty()) {
            getBuildService(project.gradle.sharedServices, AnalyticsConfiguratorService::class.java)
                .get()
                .getProjectBuilder(project.path)
                ?.projectApiUseBuilder
                ?.builderTestApiDeviceProvider = true
        }
        // now the providers.
        for (deviceProvider in providers) {
            val providerTask = taskFactory.register(
                    DeviceProviderInstrumentTestTask.CreationAction(
                            androidTestProperties, deviceProvider, testData, connectedCheckSerials))
            taskFactory.configure(
                    DEVICE_ANDROID_TEST
            ) { deviceAndroidTest: Task -> deviceAndroidTest.dependsOn(providerTask) }
        }

        // now the test servers
        val servers = extension.testServers
        if (servers.isNotEmpty()) {
            getBuildService(project.gradle.sharedServices, AnalyticsConfiguratorService::class.java)
                .get()
                .getProjectBuilder(project.path)
                ?.projectApiUseBuilder
                ?.builderTestApiTestServer = true
        }
        for (testServer in servers) {
            val serverTask = taskFactory.register(
                    TestServerTaskCreationAction(
                            androidTestProperties, testServer))
            serverTask.dependsOn<Task>(androidTestProperties.taskContainer.assembleTask)
            taskFactory.configure(
                    DEVICE_CHECK) { deviceAndroidTest: Task ->
                deviceAndroidTest.dependsOn(serverTask)
            }
        }

        if (shouldEnableUtp(projectOptions, extension.testOptions, testedVariant.variantType)) {
            // Now for each managed device defined in the dsl
            val managedDevices = mutableListOf<ManagedVirtualDevice>()
            extension
                .testOptions
                .devices
                .forEach { device ->
                    if (device is ManagedVirtualDevice) {
                        managedDevices.add(device)
                    }
                }
            val variantName = androidTestProperties.testedConfig.name
            val allDevicesVariantTask = taskFactory.register(
                androidTestProperties.computeTaskName("allDevices")
            ) { allDevicesVariant: Task ->
                allDevicesVariant.description =
                    "Runs the tests for $variantName on all managed devices in the dsl."
                allDevicesVariant.group = JavaBasePlugin.VERIFICATION_GROUP
            }
            taskFactory.configure(
                ALL_DEVICES_CHECK
            ) { allDevices: Task ->
                allDevices.dependsOn(allDevicesVariantTask)
            }
            val deviceToProvider = mutableMapOf<String, TaskProvider<out Task>>()
            for (managedDevice in managedDevices) {
                val managedDeviceTestTask = taskFactory.register(
                    ManagedDeviceInstrumentationTestTask.CreationAction(
                        androidTestProperties,
                        globalScope.avdComponents,
                        managedDevice,
                        testData
                    )
                )
                managedDeviceTestTask.dependsOn(setupTaskName(managedDevice))
                allDevicesVariantTask.dependsOn(managedDeviceTestTask)
                taskFactory.configure(
                    managedDeviceAllVariantsTaskName(managedDevice)
                ) { managedDeviceTests: Task ->
                    managedDeviceTests.dependsOn(managedDeviceTestTask)
                }
                deviceToProvider[managedDevice.name] = managedDeviceTestTask
            }

            // Register a test coverage report generation task to every managedDeviceCheck
            // task.
            if (testedVariant.variantDslInfo.isTestCoverageEnabled) {
                val jacocoAntConfiguration = JacocoConfigurations.getJacocoAntTaskConfiguration(
                    project, JacocoTask.getJacocoVersion(androidTestProperties))
                val reportTask = taskFactory.register(
                    JacocoReportTask.CreationActionManagedDeviceTest(
                        androidTestProperties, jacocoAntConfiguration))
                testedVariant.taskContainer.coverageReportTask.dependsOn(reportTask)
                for (managedDevice in managedDevices) {
                    taskFactory.configure(
                        managedDeviceAllVariantsTaskName(managedDevice)
                    ) { managedDeviceTests: Task ->
                        managedDeviceTests.dependsOn(reportTask)
                    }
                }
                // Run the report task after all tests are finished on all devices.
                deviceToProvider.values.forEach { managedDeviceTestTask ->
                    reportTask.configure {
                        it.mustRunAfter(managedDeviceTestTask)
                    }
                }
            }

            // Lastly the Device Group Tasks.
            for (group in extension.testOptions.deviceGroups) {
                val variantDeviceGroupTask = taskFactory.register(
                    managedDeviceGroupSingleVariantTaskName(androidTestProperties, group)
                ) { deviceGroupVariant: Task ->
                    deviceGroupVariant.description =
                        "Runs the tests for $variantName on all devices defined in ${group.name}."
                    deviceGroupVariant.group = JavaBasePlugin.VERIFICATION_GROUP
                }
                for (device in group.targetDevices) {
                    variantDeviceGroupTask.dependsOn(deviceToProvider.getValue(device.name))
                }
                taskFactory.configure(
                    managedDeviceGroupAllVariantsTaskName(group)
                ) { deviceGroupTask: Task ->
                    deviceGroupTask.dependsOn(variantDeviceGroupTask)
                }
            }
        }
    }

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps
     * like proguard and jacoco
     */
    fun createPostCompilationTasks(creationConfig: ApkCreationConfig) {
        Preconditions.checkNotNull(creationConfig.taskContainer.javacTask)
        val variantDslInfo = creationConfig.variantDslInfo
        val variantScope = creationConfig.variantScope
        val transformManager = creationConfig.transformManager
        taskFactory.register(MergeGeneratedProguardFilesCreationAction(creationConfig))

        // ---- Code Coverage first -----
        val isTestCoverageEnabled = (variantDslInfo.isTestCoverageEnabled
                && !creationConfig.variantType.isForTesting)
        if (isTestCoverageEnabled) {
            createJacocoTask(creationConfig)
        }
        maybeCreateTransformClassesWithAsmTask(creationConfig, isTestCoverageEnabled)
        val extension = creationConfig.globalScope.extension

        // Merge Java Resources.
        createMergeJavaResTask(creationConfig)

        // ----- External Transforms -----
        // apply all the external transforms.
        val customTransforms = extension.transforms
        val customTransformsDependencies = extension.transformsDependencies
        var registeredLegacyTransform = false
        var i = 0
        while (i < customTransforms.size) {
            val transform = customTransforms[i]
            val deps = customTransformsDependencies[i]
            registeredLegacyTransform = registeredLegacyTransform or transformManager
                    .addTransform(
                            taskFactory,
                            creationConfig,
                            transform,
                            null,
                            object : TaskConfigAction<TransformTask> {
                                override fun configure(task: TransformTask) {
                                    if (deps.isNotEmpty()) {
                                        task.dependsOn(deps)
                                    }
                                }

                            },
                            object : TaskProviderCallback<TransformTask> {
                                override fun handleProvider(taskProvider: TaskProvider<TransformTask>) {
                                    // if the task is a no-op then we make assemble task depend
                                    // on it.
                                    if (transform.scopes.isEmpty()) {
                                        creationConfig
                                                .taskContainer
                                                .assembleTask.dependsOn<Task>(taskProvider)
                                    }
                                }

                            }
                    )
                    .isPresent
            i++
        }

        // Add a task to create merged runtime classes if this is a dynamic-feature,
        // or a base module consuming feature jars. Merged runtime classes are needed if code
        // minification is enabled in a project with features or dynamic-features.
        if (creationConfig.variantType.isDynamicFeature
                || variantScope.consumesFeatureJars()) {
            taskFactory.register(MergeClassesTask.CreationAction(creationConfig))
        }

        // Produce better error messages when we have duplicated classes.
        maybeCreateCheckDuplicateClassesTask(creationConfig)

        // Resource Shrinking
        maybeCreateResourcesShrinkerTasks(creationConfig)

        // Code Shrinking
        // Since the shrinker (R8) also dexes the class files, if we have minifedEnabled we stop
        // the flow and don't set-up dexing.
        maybeCreateJavaCodeShrinkerTask(creationConfig)
        if (creationConfig.minifiedEnabled) {
            maybeCreateDesugarLibTask(creationConfig, false)
            return
        }

        // Code Dexing (MonoDex, Legacy Multidex or Native Multidex)
        if (creationConfig.needsMainDexListForBundle) {
            taskFactory.register(D8BundleMainDexListTask.CreationAction(creationConfig))
        }
        if (creationConfig.variantType.isDynamicFeature) {
            taskFactory.register(FeatureDexMergeTask.CreationAction(creationConfig))
        }
        createDexTasks(creationConfig, creationConfig.dexingType, registeredLegacyTransform)
    }

    /**
     * Creates tasks used for DEX generation. This will use an incremental pipeline that uses dex
     * archives in order to enable incremental dexing support.
     */
    private fun createDexTasks(
            creationConfig: ApkCreationConfig,
            dexingType: DexingType,
            registeredLegacyTransforms: Boolean) {
        val java8LangSupport = creationConfig.getJava8LangSupportType()
        val supportsDesugaringViaArtifactTransform =
                (java8LangSupport == VariantScope.Java8LangSupport.UNUSED
                        || (java8LangSupport == VariantScope.Java8LangSupport.D8
                        && creationConfig
                        .services
                        .projectOptions[BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM]))
        val enableDexingArtifactTransform = (creationConfig
                .services
                .projectOptions[BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM]
                && !registeredLegacyTransforms
                && supportsDesugaringViaArtifactTransform)
        taskFactory.register(
                DexArchiveBuilderTask.CreationAction(enableDexingArtifactTransform, creationConfig))
        maybeCreateDesugarLibTask(creationConfig, enableDexingArtifactTransform)
        createDexMergingTasks(creationConfig, dexingType, enableDexingArtifactTransform)
    }

    /**
     * Set up dex merging tasks when artifact transforms are used.
     *
     *
     * External libraries are merged in mono-dex and native multidex modes. In case of a native
     * multidex debuggable variant these dex files get packaged. In mono-dex case, we will re-merge
     * these files. Because this task will be almost always up-to-date, having a second merger run
     * over the external libraries will not cause a performance regression. In addition to that,
     * second dex merger will perform less I/O compared to reading all external library dex files
     * individually. For legacy multidex, we must merge all dex files in a single invocation in
     * order to generate correct primary dex file in presence of desugaring. See b/120039166.
     *
     *
     * When merging native multidex, debuggable variant, project's dex files are merged
     * independently. Also, the library projects' dex files are merged independently.
     *
     *
     * For all other variants (release, mono-dex, legacy-multidex), we merge all dex files in a
     * single invocation. This means that external libraries, library projects and project dex files
     * will be merged in a single task.
     */
    private fun createDexMergingTasks(
            creationConfig: ApkCreationConfig,
            dexingType: DexingType,
            dexingUsingArtifactTransforms: Boolean) {

        // When desugaring, The file dependencies are dexed in a task with the whole
        // remote classpath present, as they lack dependency information to desugar
        // them correctly in an artifact transform.
        // This should only be passed to Legacy Multidex MERGE_ALL or MERGE_EXTERNAL_LIBS of
        // other dexing modes, otherwise it will cause the output of DexFileDependenciesTask
        // to be included multiple times and will cause the build to fail because of same types
        // being defined multiple times in the final dex.
        val separateFileDependenciesDexingTask =
                (creationConfig.getJava8LangSupportType() == VariantScope.Java8LangSupport.D8
                        && dexingUsingArtifactTransforms)
        if (separateFileDependenciesDexingTask) {
            val desugarFileDeps = DexFileDependenciesTask.CreationAction(creationConfig)
            taskFactory.register(desugarFileDeps)
        }

        when (dexingType) {
            DexingType.MONO_DEX -> {
                taskFactory.register(
                        DexMergingTask.CreationAction(
                                creationConfig,
                                DexMergingAction.MERGE_EXTERNAL_LIBS,
                                dexingType,
                                dexingUsingArtifactTransforms,
                                separateFileDependenciesDexingTask,
                                InternalMultipleArtifactType.EXTERNAL_LIBS_DEX))
                taskFactory.register(
                        DexMergingTask.CreationAction(
                                creationConfig,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms))
            }
            DexingType.LEGACY_MULTIDEX -> {
                // For Legacy Multidex we cannot employ the same optimization of first merging
                // the external libraries, because in that step we don't have a main dex list file
                // to pass to D8 yet, and together with the fact that we'll be setting minApiLevel
                // to 20 or less it will make the external libs be merged in a way equivalent to
                // MonoDex, which might cause the build to fail if the external libraries alone
                // cannot fit into a single dex.
                taskFactory.register(
                        DexMergingTask.CreationAction(
                                creationConfig,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms,
                                separateFileDependenciesDexingTask))
            }
            DexingType.NATIVE_MULTIDEX -> {
                // For a debuggable variant, we merge different bits in separate tasks.
                // Potentially more .dex files being created, but during development-cycle of
                // developers, code changes will hopefully impact less .dex files and will make
                // the build be faster.
                // For non-debuggable (release) builds, we do only a MERGE_EXTERNAL_LIBS in a
                // separate task and then merge everything using a single MERGE_ALL pass in order
                // to minimize the number of produced .dex files since there is a systemic overhead
                // (size-wise) when we have multiple .dex files.
                if (creationConfig.debuggable) {
                    taskFactory.register(
                            DexMergingTask.CreationAction(
                                    creationConfig,
                                    DexMergingAction.MERGE_EXTERNAL_LIBS,
                                    dexingType,
                                    dexingUsingArtifactTransforms,
                                    separateFileDependenciesDexingTask))
                    taskFactory.register(
                            DexMergingTask.CreationAction(
                                    creationConfig,
                                    DexMergingAction.MERGE_PROJECT,
                                    dexingType,
                                    dexingUsingArtifactTransforms))
                    taskFactory.register(
                            DexMergingTask.CreationAction(
                                    creationConfig,
                                    DexMergingAction.MERGE_LIBRARY_PROJECTS,
                                    dexingType,
                                    dexingUsingArtifactTransforms))
                } else {
                    taskFactory.register(
                            DexMergingTask.CreationAction(
                                    creationConfig,
                                    DexMergingAction.MERGE_EXTERNAL_LIBS,
                                    dexingType,
                                    dexingUsingArtifactTransforms,
                                    separateFileDependenciesDexingTask,
                                    InternalMultipleArtifactType.EXTERNAL_LIBS_DEX))
                    taskFactory.register(
                            DexMergingTask.CreationAction(
                                    creationConfig,
                                    DexMergingAction.MERGE_ALL,
                                    dexingType,
                                    dexingUsingArtifactTransforms))
                }
            }
        }
    }

    protected fun handleJacocoDependencies(creationConfig: ComponentCreationConfig) {
        if (creationConfig.packageJacocoRuntime) {
            val jacocoAgentRuntimeDependency = JacocoConfigurations.getAgentRuntimeDependency(
                    JacocoTask.getJacocoVersion(creationConfig))
            project.dependencies
                    .add(
                            creationConfig.variantDependencies.runtimeClasspath.name,
                            jacocoAgentRuntimeDependency)

            // we need to force the same version of Jacoco we use for instrumentation
            creationConfig
                    .variantDependencies
                    .runtimeClasspath
                    .resolutionStrategy { r: ResolutionStrategy ->
                        r.force(jacocoAgentRuntimeDependency)
                    }
            taskFactory.register(JacocoPropertiesTask.CreationAction(creationConfig))
        }
    }

    fun createJacocoTask(creationConfig: ComponentCreationConfig) {

        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        creationConfig
            .transformManager
            .consumeStreams(
                mutableSetOf(com.android.build.api.transform.QualifiedContent.Scope.PROJECT),
                setOf(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
            )
        val jacocoTransformEnabled =
            projectOptions[BooleanOption.ENABLE_JACOCO_TRANSFORM_INSTRUMENTATION]

        taskFactory.register(JacocoTask.CreationAction(creationConfig))

        val instrumentedClasses: FileCollection =
            if (jacocoTransformEnabled &&
                creationConfig.variantDslInfo.isTestCoverageEnabled &&
                    creationConfig !is ApplicationCreationConfig) {
                // For libraries that can be published,avoid publishing classes
                // with runtime dependencies on Jacoco.
                creationConfig.artifacts.getAllClasses()
            } else {
                project.files(
                    creationConfig.artifacts.get(JACOCO_INSTRUMENTED_CLASSES),
                    project.files(creationConfig.artifacts.get(JACOCO_INSTRUMENTED_JARS)).asFileTree
                )
            }
        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        creationConfig
            .transformManager
            .addStream(
                OriginalStream.builder("jacoco-instrumented-classes")
                    .addContentTypes(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                    .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                    .setFileCollection(instrumentedClasses)
                    .build())
    }

    protected fun createDataBindingTasksIfNecessary(creationConfig: ComponentCreationConfig) {
        val dataBindingEnabled = creationConfig.buildFeatures.dataBinding
        val viewBindingEnabled = creationConfig.buildFeatures.viewBinding
        if (!dataBindingEnabled && !viewBindingEnabled) {
            return
        }
        taskFactory.register(DataBindingMergeBaseClassLogTask.CreationAction(creationConfig))
        taskFactory.register(
                DataBindingMergeDependencyArtifactsTask.CreationAction(creationConfig))
        DataBindingBuilder.setDebugLogEnabled(logger.isDebugEnabled)
        taskFactory.register(DataBindingGenBaseClassesTask.CreationAction(creationConfig))

        // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
        if (dataBindingEnabled) {
            if (projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
                    && isKotlinKaptPluginApplied(project)) {
                val kotlinVersion = getProjectKotlinPluginKotlinVersion(project)
                if (kotlinVersion != null && kotlinVersion < KAPT_FIX_KOTLIN_VERSION) {
                    // Before Kotlin version 1.5.20 there was an issue with KAPT resolving files
                    // at configuration time. We only need this task as a workaround for it, if the
                    // version is newer than 1.5.20 or KAPT isn't applied, we can skip it.
                    taskFactory.register(
                            MergeRFilesForDataBindingTask.CreationAction(creationConfig))
                }
            }
            taskFactory.register(DataBindingTriggerTask.CreationAction(creationConfig))
            setDataBindingAnnotationProcessorParams(creationConfig)
        }
    }

    private fun setDataBindingAnnotationProcessorParams(
            creationConfig: ComponentCreationConfig) {
        val processorOptions = creationConfig.javaCompilation.annotationProcessor

        val dataBindingArgs = createArguments(
                creationConfig,
                logger.isDebugEnabled,
                DataBindingBuilder.getPrintMachineReadableOutput(),
                isKotlinKaptPluginApplied(project),
                getProjectKotlinPluginKotlinVersion(project))
        // Even though at this point, the old variantDsl related objects are dead, the KAPT plugin
        // is using reflection to query the [CompilerArgumentProvider] to look if databinding is
        // turned on, so keep on adding to the [VariantDslInfo]'s list until KAPT switches to the
        // new variant API.
        creationConfig.variantDslInfo.javaCompileOptions.annotationProcessorOptions
            .compilerArgumentProviders.add(dataBindingArgs)

        // add it the new Variant API objects, this is what our tasks use.
        processorOptions.argumentProviders.add(dataBindingArgs)
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     */
    fun createPackagingTask(creationConfig: ApkCreationConfig) {
        // ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
        val taskContainer = creationConfig.taskContainer
        val signedApk = creationConfig.signingConfig?.isSigningReady() ?: false

        /*
         * PrePackaging step class that will look if the packaging of the main FULL_APK split is
         * necessary when running in InstantRun mode. In InstantRun mode targeting an api 23 or
         * above device, resources are packaged in the main split FULL_APK. However when a warm swap
         * is possible, it is not necessary to produce immediately the new main SPLIT since the
         * runtime use the resources.ap_ file directly. However, as soon as an incompatible change
         * forcing a cold swap is triggered, the main FULL_APK must be rebuilt (even if the
         * resources were changed in a previous build).
         */
        val variantScope = creationConfig.variantScope
        val manifestType: InternalArtifactType<Directory> = creationConfig.manifestArtifactType
        val manifests = creationConfig.artifacts.get<Directory>(manifestType)

        // Common code for both packaging tasks.
        val configureResourcesAndAssetsDependencies = Action { task: Task ->
            task.dependsOn(taskContainer.mergeAssetsTask)
            if (taskContainer.processAndroidResTask != null) {
                task.dependsOn(taskContainer.processAndroidResTask)
            }
        }
        val packageApp = taskFactory.register(
                PackageApplication.CreationAction(
                        creationConfig,
                        creationConfig.paths.apkLocation,
                        creationConfig.useResourceShrinker(),
                        manifests,
                        manifestType),
                null,
                object : TaskConfigAction<PackageApplication> {
                    override fun configure(task: PackageApplication) {
                        task.dependsOn(taskContainer.javacTask)
                        if (taskContainer.packageSplitResourcesTask != null) {
                            task.dependsOn(taskContainer.packageSplitResourcesTask)
                        }
                        if (taskContainer.packageSplitAbiTask != null) {
                            task.dependsOn(taskContainer.packageSplitAbiTask)
                        }
                        configureResourcesAndAssetsDependencies.execute(task)
                    }
                },
                null)

        // create the listing file redirect
        val ideRedirectFileTask = taskFactory.register(
            ListingFileRedirectTask.CreationAction(
                creationConfig = creationConfig,
                taskSuffix = "Apk",
                inputArtifactType = InternalArtifactType.APK_IDE_MODEL,
                outputArtifactType = InternalArtifactType.APK_IDE_REDIRECT_FILE
            )
        )

        taskContainer
                .assembleTask
                .configure { task: Task ->
                    task.dependsOn(
                            creationConfig.artifacts.get(SingleArtifact.APK),
                            ideRedirectFileTask
                    )
                }


        // create install task for the variant Data. This will deal with finding the
        // right output if there are more than one.
        // Add a task to install the application package
        if (signedApk) {
            createInstallTask(creationConfig)
        }

        // add an uninstall task
        val uninstallTask = taskFactory.register(UninstallTask.CreationAction(creationConfig))
        taskFactory.configure(UNINSTALL_ALL) { uninstallAll: Task ->
            uninstallAll.dependsOn(uninstallTask)
        }
    }

    protected open fun createInstallTask(creationConfig: ApkCreationConfig) {
        taskFactory.register(InstallVariantTask.CreationAction(creationConfig))
    }

    protected fun createValidateSigningTask(creationConfig: ApkCreationConfig) {
        if (creationConfig.signingConfig?.isSigningReady() != true) {
            return
        }

        val service: Provider<AndroidLocationsBuildService> =
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    AndroidLocationsBuildService::class.java
                )

        // FIXME create one per signing config instead of one per variant.
        taskFactory.register(
                ValidateSigningTask.CreationAction(
                        creationConfig,
                        service.get().getDefaultDebugKeystoreLocation()
                        ))
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
    fun createAnchorAssembleTasks(
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
                val variantType = creationConfig.variantType
                if (!variantType.isNestedComponent) {
                    val taskContainer = creationConfig.taskContainer
                    val variantDslInfo = creationConfig.variantDslInfo
                    val buildType = creationConfig.buildType
                    val assembleTask = taskContainer.assembleTask
                    if (buildType != null) {
                        assembleMap.put(buildType, assembleTask)
                    }
                    for (flavor in variantDslInfo.productFlavorList) {
                        assembleMap.put(flavor.name, assembleTask)
                    }

                    // if 2+ flavor dimensions, then make an assemble for the flavor combo
                    if (flavorDimensionCount > 1) {
                        assembleMap.put(creationConfig.flavorName, assembleTask)
                    }

                    // fill the bundle map only if the variant supports bundles.
                    if (variantType.isBaseModule) {
                        val bundleTask = taskContainer.bundleTask
                        if (buildType != null) {
                            bundleMap.put(buildType, bundleTask)
                        }
                        for (flavor in variantDslInfo.productFlavorList) {
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
                val variantType = creationConfig.variantType
                if (!variantType.isNestedComponent) {
                    val taskContainer = creationConfig.taskContainer
                    subAssembleTasks.add(taskContainer.assembleTask)
                    if (variantType.isBaseModule) {
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

    fun createAssembleTask(component: ComponentImpl) {
        taskFactory.register(
                component.computeTaskName("assemble"),
                null /*preConfigAction*/,
                object : TaskConfigAction<Task> {
                    override fun configure(task: Task) {
                        task.description =
                                "Assembles main output for variant " + component.name
                    }

                },
                object : TaskProviderCallback<Task> {
                    override fun handleProvider(taskProvider: TaskProvider<Task>) {
                        component.taskContainer.assembleTask =
                                taskProvider
                    }
                }
        )
    }

    fun createBundleTask(component: ComponentImpl) {
        taskFactory.register(
                component.computeTaskName("bundle"),
                null,
                object : TaskConfigAction<Task> {
                    override fun configure(task: Task) {
                        task.description = "Assembles bundle for variant " + component.name
                        task.dependsOn(component.artifacts.get(SingleArtifact.BUNDLE))
                        task.dependsOn(component.artifacts.get(InternalArtifactType.BUNDLE_IDE_MODEL))
                        task.dependsOn(component.artifacts.get(InternalArtifactType.BUNDLE_IDE_REDIRECT_FILE))
                    }
                },
                object : TaskProviderCallback<Task> {
                    override fun handleProvider(taskProvider: TaskProvider<Task>) {
                        component.taskContainer.bundleTask = taskProvider
                    }
                }
        )
    }

    protected open fun maybeCreateJavaCodeShrinkerTask(
            creationConfig: ConsumableCreationConfig) {
        if (creationConfig.minifiedEnabled) {
            doCreateJavaCodeShrinkerTask(creationConfig)
        }
    }

    /**
     * Actually creates the minify transform, using the given mapping configuration. The mapping is
     * only used by test-only modules.
     */
    protected fun doCreateJavaCodeShrinkerTask(
            creationConfig: ConsumableCreationConfig,
            isTestApplication: Boolean = false) {
        // The compile R class jar is added to the classes to be processed in libraries so that
        // proguard can shrink an empty library project, as the R class is always kept and
        // then removed by library jar transforms.
        val addCompileRClass = (this is LibraryTaskManager
                && creationConfig.androidResourcesEnabled)
        val task: TaskProvider<out Task> =
                createR8Task(creationConfig, isTestApplication, addCompileRClass)
        if (creationConfig.variantScope.postprocessingFeatures != null) {
            val checkFilesTask =
                    taskFactory.register(CheckProguardFiles.CreationAction(creationConfig))
            task.dependsOn(checkFilesTask)
        }
    }

    private fun createR8Task(
            creationConfig: ConsumableCreationConfig,
            isTestApplication: Boolean,
            addCompileRClass: Boolean): TaskProvider<R8Task> {
        if (creationConfig is ApplicationCreationConfig) {
            publishArtifactsToDynamicFeatures(
                    creationConfig,
                    FEATURE_DEX,
                    AndroidArtifacts.ArtifactType.FEATURE_DEX,
                    null)
            publishArtifactsToDynamicFeatures(
                    creationConfig,
                    InternalArtifactType.FEATURE_SHRUNK_JAVA_RES,
                    AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_JAVA_RES,
                    DOT_JAR)
        }
        return taskFactory.register(
                R8Task.CreationAction(creationConfig, isTestApplication, addCompileRClass))
    }

    /**
     * We have a separate method for publishing artifacts back to the features (instead of using the
     * typical PublishingSpecs pipeline) because multiple artifacts are published with different
     * attributes for the given ArtifactType in this case.
     *
     * <p>This method will publish each of the children of the directory corresponding to the given
     * internalArtifactType. The children files' names must match the names of the corresponding
     * feature modules + the given fileExtension.
     *
     * @param creationConfig the ApplicationCreationConfig
     * @param internalArtifactType the InternalArtifactType of the directory whose children will be
     *     published to the features
     * @param artifactType the ArtifactType used when publishing to the features
     * @param fileExtension the fileExtension of the directory's children files, or null if the
     *     children files are directories
     */
    private fun publishArtifactsToDynamicFeatures(
            creationConfig: ApplicationCreationConfig,
            internalArtifactType: InternalArtifactType<Directory>,
            artifactType: AndroidArtifacts.ArtifactType,
            fileExtension: String?) {
        // first calculate the list of module paths
        val modulePaths: Collection<String>
        val extension = globalScope.extension
        modulePaths = if (extension is BaseAppModuleExtension) {
            extension.dynamicFeatures
        } else {
            return
        }
        val configuration =
                creationConfig.variantDependencies.getElements(PublishedConfigSpec(PublishedConfigType.RUNTIME_ELEMENTS))
        Preconditions.checkNotNull(
                configuration,
                "Publishing to Runtime Element with no Runtime Elements configuration object. "
                        + "VariantType: "
                        + creationConfig.variantType)
        val artifact = creationConfig.artifacts.get(internalArtifactType)
        val artifactDirectory = project.objects.directoryProperty()
        artifactDirectory.set(artifact)
        for (modulePath in modulePaths) {
            val file = artifactDirectory.file(getFeatureFileName(modulePath, fileExtension))
            publishArtifactToConfiguration(
                    configuration!!,
                    file,
                    artifactType,
                    AndroidAttributes(
                            AndroidArtifacts.MODULE_PATH to project.absoluteProjectPath(modulePath)
                    )
            )
        }
    }

    /**
     * If resource shrinker is enabled, set-up and register the appropriate tasks.
     */
    private fun maybeCreateResourcesShrinkerTasks(
            creationConfig: ConsumableCreationConfig) {
        if (!creationConfig.useResourceShrinker()) {
            return
        }
        if (creationConfig.variantType.isDynamicFeature) {
            // For bundles resources are shrunk once bundle is packaged so the task is applicable
            // for base module only.
            return
        }
        if (projectOptions[BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER]) {
            // Shrink resources in APK with a new resource shrinker and produce stripped res
            // package.
            taskFactory.register(ShrinkResourcesNewShrinkerTask.CreationAction(creationConfig))
            // Shrink resources in bundles with new resource shrinker.
            taskFactory.register(ShrinkAppBundleResourcesTask.CreationAction(creationConfig))
        } else {
            // Shrink resources in APK with old resource shrinker and produce stripped res package.
            taskFactory.register(ShrinkResourcesOldShrinkerTask.CreationAction(creationConfig))
            // Shrink base module resources in proto format to be packaged to bundle.
            taskFactory.register(
                    LegacyShrinkBundleModuleResourcesTask.CreationAction(creationConfig))
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
                getBuildService<MavenCoordinatesCacheBuildService>(project.gradle.sharedServices).get()
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
            }
        }
        createDependencyAnalyzerTask()

        val checkJetifierBuildService =
            CheckJetifierBuildService.RegistrationAction(project, projectOptions).execute()
        taskFactory.register(
            CheckJetifierTask.CreationAction(globalScope, projectOptions, checkJetifierBuildService)
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

    fun createAnchorTasks(creationConfig: ComponentCreationConfig) {
        createVariantPreBuildTask(creationConfig)

        // also create sourceGenTask
        val variantData = creationConfig.variantData
        creationConfig
                .taskContainer
                .sourceGenTask = taskFactory.register(
                creationConfig.computeTaskName("generate", "Sources")
        ) { task: Task ->
            task.dependsOn(COMPILE_LINT_CHECKS_TASK)
            if (creationConfig.variantType.isAar) {
                task.dependsOn(PrepareLintJarForPublish.NAME)
            }
            task.dependsOn(variantData.extraGeneratedResFolders)
        }
        // and resGenTask
        creationConfig
                .taskContainer
                .resourceGenTask = taskFactory.register(
                creationConfig.computeTaskName("generate", "Resources"))
        creationConfig
                .taskContainer
                .assetGenTask =
                taskFactory.register(creationConfig.computeTaskName("generate", "Assets"))
        if (!creationConfig.variantType.isForTesting
                && creationConfig.variantDslInfo.isTestCoverageEnabled) {
            creationConfig
                    .taskContainer
                    .coverageReportTask = taskFactory.register(
                    creationConfig.computeTaskName("create", "CoverageReport")
            ) { task: Task ->
                task.group = JavaBasePlugin.VERIFICATION_GROUP
                task.description = String.format(
                        "Creates test coverage reports for the %s variant.",
                        creationConfig.name)
            }
        }

        // and compile task
        createCompileAnchorTask(creationConfig)
    }

    protected open fun createVariantPreBuildTask(creationConfig: ComponentCreationConfig) {
        // default pre-built task.
        createDefaultPreBuildTask(creationConfig)
    }

    protected fun createDefaultPreBuildTask(creationConfig: ComponentCreationConfig) {
        taskFactory.register(PreBuildCreationAction(creationConfig))
    }

    abstract class AbstractPreBuildCreationAction<TaskT : AndroidVariantTask>(creationConfig: ComponentCreationConfig) :
            VariantTaskCreationAction<TaskT, ComponentCreationConfig>(creationConfig, false) {

        override val name: String
            get() = computeTaskName("pre", "Build")

        override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
            super.handleProvider(taskProvider)
            creationConfig.variantData.taskContainer.preBuildTask = taskProvider
        }

        override fun configure(task: TaskT) {
            super.configure(task)
            task.dependsOn(MAIN_PREBUILD)
        }
    }

    private class PreBuildCreationAction(creationConfig: ComponentCreationConfig) :
            AbstractPreBuildCreationAction<AndroidVariantTask>(creationConfig) {

        override val type: Class<AndroidVariantTask>
            get() = AndroidVariantTask::class.java
    }

    private fun createCompileAnchorTask(creationConfig: ComponentCreationConfig) {
        val taskContainer = creationConfig.taskContainer
        taskContainer.compileTask = taskFactory.register(
                creationConfig.computeTaskName("compile", "Sources")
        ) { task: Task -> task.group = BUILD_GROUP }
    }

    private fun addBindingDependenciesIfNecessary(dataBindingOptions: DataBindingOptions) {
        val viewBindingEnabled = allPropertiesList.stream()
                .anyMatch { componentProperties: ComponentCreationConfig -> componentProperties.buildFeatures.viewBinding }
        val dataBindingEnabled = allPropertiesList.stream()
                .anyMatch { componentProperties: ComponentCreationConfig -> componentProperties.buildFeatures.dataBinding }
        val useAndroidX = projectOptions[BooleanOption.USE_ANDROID_X]
        val dataBindingBuilder = globalScope.dataBindingBuilder
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
                    dataBindingOptions.getVersion(),
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
            if (dataBindingOptions.isEnabledForTests() || this is LibraryTaskManager) {
                val dataBindingArtifact =
                        SdkConstants.DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT + ":" + version
                project.dependencies
                        .add("androidTestAnnotationProcessor", dataBindingArtifact)
                if (extension.testOptions.unitTests.isIncludeAndroidResources) {
                    project.dependencies.add("testAnnotationProcessor", dataBindingArtifact)
                }
            }
            if (dataBindingOptions.getAddDefaultAdapters()) {
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
        val ktxGradlePropertyValue = globalScope.dslServices.projectOptions
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
                    globalScope
                        .dslServices
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
                        globalScope
                                .dslServices
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
        var kaptTaskClass: Class<out Task?>? = null
        try {
            kaptTaskClass =
                    Class.forName("org.jetbrains.kotlin.gradle.internal.KaptTask") as Class<out Task?>
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
        // create a map from kapt task name to variant scope
        val kaptTaskLookup= allPropertiesList
                .map{ it.computeTaskName("kapt", "kotlin") to it }
                .toMap()
        project.tasks
                .withType(
                        kaptTaskClass,
                        Action { kaptTask: Task ->
                            // find matching scope.
                            val matchingComponent = kaptTaskLookup[kaptTask.name]
                            matchingComponent?.let {
                                configureKaptTaskInScopeForDataBinding(it,
                                        kaptTask)
                            }
                        } as Action<Task>)
    }

    private fun configureKaptTaskInScopeForDataBinding(
            creationConfig: ComponentCreationConfig, kaptTask: Task) {
        val dataBindingArtifactDir = creationConfig.services.projectInfo.getProject().objects.directoryProperty()
        val exportClassListFile = creationConfig.services.projectInfo.getProject().objects.fileProperty()
        val kaptTaskProvider = taskFactory.named(kaptTask.name)

        // Register data binding artifacts as outputs
        registerDataBindingOutputs(
                dataBindingArtifactDir,
                exportClassListFile,
                creationConfig.variantType.isExportDataBindingClassList,
                kaptTaskProvider,
                creationConfig.artifacts,
                false // forJavaCompile = false as this task is Kapt
        )

        // Register the DirectoryProperty / RegularFileProperty as outputs as they are not yet
        // annotated as outputs (same with the code in JavaCompileCreationAction.configure).
        kaptTask.outputs
                .dir(dataBindingArtifactDir)
                .withPropertyName("dataBindingArtifactDir")
        if (creationConfig.variantType.isExportDataBindingClassList) {
            kaptTask.outputs
                    .file(exportClassListFile)
                    .withPropertyName("dataBindingExportClassListFile")
        }
    }

    protected fun configureTestData(
            creationConfig: VariantCreationConfig, testData: AbstractTestDataImpl) {
        testData.animationsDisabled = creationConfig
                .services
                .provider(extension.testOptions::animationsDisabled)
        testData.setExtraInstrumentationTestRunnerArgs(
                creationConfig
                        .services
                        .projectOptions
                        .extraInstrumentationTestRunnerArgs)
    }

    private fun maybeCreateCheckDuplicateClassesTask(
            creationConfig: VariantCreationConfig) {
        if (creationConfig
                        .services
                        .projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
            taskFactory.register(CheckDuplicateClassesTask.CreationAction(creationConfig))
        }
    }

    private fun maybeCreateDesugarLibTask(
            apkCreationConfig: ApkCreationConfig,
            enableDexingArtifactTransform: Boolean) {
        val separateFileDependenciesDexingTask =
                (apkCreationConfig.getJava8LangSupportType() == VariantScope.Java8LangSupport.D8
                        && enableDexingArtifactTransform)
        if (apkCreationConfig.shouldPackageDesugarLibDex) {
            taskFactory.register(
                    L8DexDesugarLibTask.CreationAction(
                            apkCreationConfig,
                            enableDexingArtifactTransform,
                            separateFileDependenciesDexingTask))
        }

        if(apkCreationConfig.variantType.isDynamicFeature
            && apkCreationConfig.needsShrinkDesugarLibrary
        ) {
            taskFactory.register(
                DesugarLibKeepRulesMergeTask.CreationAction(
                    apkCreationConfig,
                    enableDexingArtifactTransform,
                    separateFileDependenciesDexingTask
                )
            )
        }
    }

    @Suppress("DEPRECATION") // Legacy support (b/195153220)
    protected fun maybeCreateTransformClassesWithAsmTask(
            creationConfig: ComponentCreationConfig, isTestCoverageEnabled: Boolean) {
        if (creationConfig.projectClassesAreInstrumented) {
            creationConfig
                    .transformManager
                    .consumeStreams(
                            mutableSetOf(com.android.build.api.transform.QualifiedContent.Scope.PROJECT),
                            setOf(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES))
            taskFactory.register(
                    TransformClassesWithAsmTask.CreationAction(
                            creationConfig,
                            isTestCoverageEnabled
                    )
            )
            if (creationConfig.asmFramesComputationMode
                    == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                taskFactory.register(RecalculateStackFramesTask.CreationAction(creationConfig))
            }
            creationConfig
                    .transformManager
                    .addStream(
                            OriginalStream.builder("asm-instrumented-classes")
                                    .addContentTypes(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES)
                                    .addScope(com.android.build.api.transform.QualifiedContent.Scope.PROJECT)
                                    .setFileCollection(
                                            creationConfig
                                                    .allProjectClassesPostAsmInstrumentation)
                                    .build())
        }
    }

    companion object {

        private const val MULTIDEX_VERSION = "1.0.2"
        private const val COM_ANDROID_SUPPORT_MULTIDEX =
                "com.android.support:multidex:" + MULTIDEX_VERSION
        private val ANDROIDX_MULTIDEX_MULTIDEX = androidXMappings.getValue("com.android.support:multidex")
        private const val COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
                "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION
        private val ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION =
                androidXMappings.getValue("com.android.support:multidex-instrumentation")

        // name of the task that triggers compilation of the custom lint Checks
        private const val COMPILE_LINT_CHECKS_TASK = "compileLintChecks"
        const val INSTALL_GROUP = "Install"
        const val BUILD_GROUP = BasePlugin.BUILD_GROUP
        const val ANDROID_GROUP = "Android"
        const val FEATURE_SUFFIX = "Feature"

        // Task names. These cannot be AndroidTasks as in the component model world there is nothing to
        // force generateTasksBeforeEvaluate to happen before the variant tasks are created.
        const val MAIN_PREBUILD = "preBuild"
        const val UNINSTALL_ALL = "uninstallAll"
        const val DEVICE_CHECK = "deviceCheck"
        const val DEVICE_ANDROID_TEST = BuilderConstants.DEVICE + VariantType.ANDROID_TEST_SUFFIX
        const val CONNECTED_CHECK = "connectedCheck"
        const val ALL_DEVICES_CHECK = "allDevicesCheck"
        const val CONNECTED_ANDROID_TEST =
                BuilderConstants.CONNECTED + VariantType.ANDROID_TEST_SUFFIX
        const val ASSEMBLE_ANDROID_TEST = "assembleAndroidTest"
        const val LINT = "lint"
        const val LINT_FIX = "lintFix"

        // Temporary static variables for Kotlin+Compose configuration
        const val KOTLIN_COMPILER_CLASSPATH_CONFIGURATION_NAME = "kotlinCompilerClasspath"
        const val COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION = "1.0.0-beta07"
        const val CREATE_MOCKABLE_JAR_TASK_NAME = "createMockableJar"

        /**
         * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
         * referenced by custom build logic.
         *
         * @param globalScope the global scope
         * @param variantType the main variant type as returned by the [     ]
         * @param sourceSetContainer the container of source set from the DSL.
         */
        @JvmStatic
        fun createTasksBeforeEvaluate(
                projectOptions: ProjectOptions,
                globalScope: GlobalScope,
                variantType: VariantType,
                sourceSetContainer: Iterable<AndroidSourceSet?>,
                projectInfo: ProjectInfo) {
            val project = projectInfo.getProject()
            val taskFactory = TaskFactoryImpl(project.tasks)
            taskFactory.register(
                    UNINSTALL_ALL
            ) { uninstallAllTask: Task ->
                uninstallAllTask.description = "Uninstall all applications."
                uninstallAllTask.group = INSTALL_GROUP
            }
            taskFactory.register(
                    DEVICE_CHECK
            ) { deviceCheckTask: Task ->
                deviceCheckTask.description =
                        "Runs all device checks using Device Providers and Test Servers."
                deviceCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
            taskFactory.register(
                    CONNECTED_CHECK,
                    DeviceSerialTestTask::class.java
            ) { connectedCheckTask: DeviceSerialTestTask ->
                connectedCheckTask.description =
                        "Runs all device checks on currently connected devices."
                connectedCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }

            // Make sure MAIN_PREBUILD runs first:
            taskFactory.register(MAIN_PREBUILD)
            taskFactory.register(ExtractProguardFiles.CreationAction(projectOptions, globalScope, projectInfo))
                .configure { it: ExtractProguardFiles -> it.dependsOn(MAIN_PREBUILD) }
            taskFactory.register(SourceSetsTask.CreationAction(sourceSetContainer))
            taskFactory.register(
                    ASSEMBLE_ANDROID_TEST
            ) { assembleAndroidTestTask: Task ->
                assembleAndroidTestTask.group = BasePlugin.BUILD_GROUP
                assembleAndroidTestTask.description = "Assembles all the Test applications."
            }
            taskFactory.register(LintCompile.CreationAction(projectInfo))
            // Don't register global lint or lintFix tasks for dynamic features because dynamic
            // features are analyzed and their lint issues are reported and/or fixed when running
            // lint or lintFix from the base app.
            if (!variantType.isForTesting && !variantType.isDynamicFeature) {
                LintTaskManager(globalScope, taskFactory, projectInfo).createBeforeEvaluateLintTasks()
            }

            // create a single configuration to point to a project or a local file that contains
            // the lint.jar for this project.
            // This is not the configuration that consumes lint.jar artifacts from normal dependencies,
            // or publishes lint.jar to consumers. These are handled at the variant level.
            globalScope.setLintChecks(createCustomLintChecksConfig(project))
            globalScope.setLintPublish(createCustomLintPublishConfig(project))
            globalScope.setAndroidJarConfig(createAndroidJarConfig(project))

            // for testing only.
            taskFactory.register(
                    "resolveConfigAttr",
                    ConfigAttrTask::class.java) { task: ConfigAttrTask -> task.resolvable = true }
            taskFactory.register(
                    "consumeConfigAttr",
                    ConfigAttrTask::class.java) { task: ConfigAttrTask -> task.consumable = true }
            createCoreLibraryDesugaringConfig(project)
        }

        fun createCustomLintChecksConfig(project: Project): Configuration {
            val lintChecks =
                    project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_LINTCHECKS)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to apply external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        fun createCustomLintPublishConfig(project: Project): Configuration {
            val lintChecks =
                    project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_LINTPUBLISH)
            lintChecks.isVisible = false
            lintChecks.description = "Configuration to publish external lint check jar"
            lintChecks.isCanBeConsumed = false
            return lintChecks
        }

        fun createAndroidJarConfig(project: Project): Configuration {
            val androidJarConfig =
                    project.configurations.maybeCreate(VariantDependencies.CONFIG_NAME_ANDROID_APIS)
            androidJarConfig.description =
                    "Configuration providing various types of Android JAR file"
            androidJarConfig.isCanBeConsumed = false
            return androidJarConfig
        }

        fun createCoreLibraryDesugaringConfig(project: Project) {
            var coreLibraryDesugaring =
                    project.configurations.findByName(VariantDependencies.CONFIG_NAME_CORE_LIBRARY_DESUGARING)
            if (coreLibraryDesugaring == null) {
                coreLibraryDesugaring =
                        project.configurations.create(VariantDependencies.CONFIG_NAME_CORE_LIBRARY_DESUGARING)
                coreLibraryDesugaring.isVisible = false
                coreLibraryDesugaring.isCanBeConsumed = false
                coreLibraryDesugaring.description = "Configuration to desugar libraries"
            }
        }

        private fun generatesProguardOutputFile(creationConfig: ComponentCreationConfig): Boolean {
            return ((creationConfig is ConsumableCreationConfig
                    && creationConfig.minifiedEnabled)
                    || creationConfig.variantType.isDynamicFeature)
        }

        /** Makes the given task the one used by top-level "compile" task.  */
        @JvmStatic
        fun setJavaCompilerTask(
                javaCompilerTask: TaskProvider<out JavaCompile>,
                creationConfig: ComponentCreationConfig) {
            creationConfig.taskContainer.compileTask.dependsOn(javaCompilerTask)
        }

        /**
         * Method to reliably generate matching feature file names when dex splitter is used.
         *
         * @param modulePath the gradle module path for the feature
         * @param fileExtension the desired file extension (e.g., ".jar"), or null if no file extension
         * (e.g., for a folder)
         * @return name of file
         */
        fun getFeatureFileName(
                modulePath: String, fileExtension: String?): String {
            val featureName = getFeatureName(modulePath)
            val sanitizedFeatureName = if (":" == featureName) "" else featureName
            // Prepend "feature-" to fileName in case a non-base module has module path ":base".
            return "feature-" + sanitizedFeatureName + Strings.nullToEmpty(fileExtension)
        }
    }
}
