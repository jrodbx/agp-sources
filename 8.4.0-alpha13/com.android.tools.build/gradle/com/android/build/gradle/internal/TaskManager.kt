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
import com.android.SdkConstants.DOT_JAR
import com.android.build.api.artifact.Artifact.Single
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifact
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.dsl.Device
import com.android.build.api.dsl.DeviceGroup
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.impl.TaskProviderBasedDirectoryEntryImpl
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.coverage.JacocoPropertiesTask
import com.android.build.gradle.internal.coverage.JacocoReportTask
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.packaging.getDefaultDebugKeystoreLocation
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType
import com.android.build.gradle.internal.publishing.PublishedConfigSpec
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import com.android.build.gradle.internal.res.LinkAndroidResForBundleTask
import com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask
import com.android.build.gradle.internal.res.ParseLibraryResourcesTask
import com.android.build.gradle.internal.res.namespaced.NamespacedResourcesTaskManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_DEX
import com.android.build.gradle.internal.scope.InternalArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_R_CLASS_CLASSES
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.R8ParallelBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.CheckAarMetadataTask
import com.android.build.gradle.internal.tasks.CheckDuplicateClassesTask
import com.android.build.gradle.internal.tasks.CheckProguardFiles
import com.android.build.gradle.internal.tasks.ClassesClasspathUtils
import com.android.build.gradle.internal.tasks.D8BundleMainDexListTask
import com.android.build.gradle.internal.tasks.DeviceSerialTestTask
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import com.android.build.gradle.internal.tasks.DexFileDependenciesTask
import com.android.build.gradle.internal.tasks.DexMergingAction
import com.android.build.gradle.internal.tasks.DexMergingTask
import com.android.build.gradle.internal.tasks.ExpandArtProfileWildcardsTask
import com.android.build.gradle.internal.tasks.ExtractProguardFiles
import com.android.build.gradle.internal.tasks.FeatureDexMergeTask
import com.android.build.gradle.internal.tasks.FeatureGlobalSyntheticsMergeTask
import com.android.build.gradle.internal.tasks.GenerateLibraryProguardRulesTask
import com.android.build.gradle.internal.tasks.GlobalSyntheticsMergeTask
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.build.gradle.internal.tasks.JacocoTask
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import com.android.build.gradle.internal.tasks.LintCompile
import com.android.build.gradle.internal.tasks.ListingFileRedirectTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestResultAggregationTask
import com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask
import com.android.build.gradle.internal.tasks.ManagedDeviceSetupTask
import com.android.build.gradle.internal.tasks.ManagedDeviceTestTask
import com.android.build.gradle.internal.tasks.MergeAaptProguardFilesCreationAction
import com.android.build.gradle.internal.tasks.MergeClassesTask
import com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction
import com.android.build.gradle.internal.tasks.MergeJavaResourceTask
import com.android.build.gradle.internal.tasks.MergeNativeLibsTask
import com.android.build.gradle.internal.tasks.OptimizeResourcesTask
import com.android.build.gradle.internal.tasks.PrepareLintJarForPublish
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.R8Task
import com.android.build.gradle.internal.tasks.RecalculateStackFramesTask
import com.android.build.gradle.internal.tasks.SourceSetsTask
import com.android.build.gradle.internal.tasks.UninstallTask
import com.android.build.gradle.internal.tasks.ValidateResourcesTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.VerifyLibraryClassesTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments.Companion.createArguments
import com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingMergeDependencyArtifactsTask
import com.android.build.gradle.internal.tasks.databinding.DataBindingTriggerTask
import com.android.build.gradle.internal.tasks.databinding.KAPT_FIX_KOTLIN_VERSION
import com.android.build.gradle.internal.tasks.databinding.MergeRFilesForDataBindingTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.tasks.factory.TaskProviderCallback
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.tasks.featuresplit.getFeatureName
import com.android.build.gradle.internal.tasks.mlkit.GenerateMlModelClass
import com.android.build.gradle.internal.test.AbstractTestDataImpl
import com.android.build.gradle.internal.testing.utp.TEST_RESULT_PB_FILE_NAME
import com.android.build.gradle.internal.transforms.ShrinkAppBundleResourcesTask
import com.android.build.gradle.internal.transforms.ShrinkResourcesNewShrinkerTask
import com.android.build.gradle.internal.utils.getProjectKotlinPluginKotlinVersion
import com.android.build.gradle.internal.utils.isKotlinKaptPluginApplied
import com.android.build.gradle.internal.utils.isKspPluginApplied
import com.android.build.gradle.internal.variant.ApkVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.tasks.AidlCompile
import com.android.build.gradle.tasks.CompatibleScreensManifest
import com.android.build.gradle.tasks.GenerateBuildConfig
import com.android.build.gradle.tasks.GenerateManifestJarTask
import com.android.build.gradle.tasks.GenerateResValues
import com.android.build.gradle.tasks.JavaCompileCreationAction
import com.android.build.gradle.tasks.JavaPreCompileTask
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MapSourceSetPathsTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.MergeSourceSetFolders.MergeMlModelsSourceFoldersCreationAction
import com.android.build.gradle.tasks.MergeSourceSetFolders.MergeShaderSourceFoldersCreationAction
import com.android.build.gradle.tasks.PackageApplication
import com.android.build.gradle.tasks.ProcessApplicationManifest
import com.android.build.gradle.tasks.ProcessManifestForBundleTask
import com.android.build.gradle.tasks.ProcessManifestForInstantAppTask
import com.android.build.gradle.tasks.ProcessManifestForMetadataFeatureTask
import com.android.build.gradle.tasks.ProcessMultiApkApplicationManifest
import com.android.build.gradle.tasks.ProcessPackagedManifestTask
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.RenderscriptCompile
import com.android.build.gradle.tasks.ShaderCompile
import com.android.build.gradle.tasks.SimplifiedMergedManifestsProducerTask
import com.android.build.gradle.tasks.TransformClassesWithAsmTask
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.dexing.DexingType
import com.android.utils.appendCapitalized
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File
import java.util.Locale

/**
 * Abstract class containing tasks creation logic that is shared between variants and components.
 */
abstract class TaskManager(
    @JvmField protected val project: Project,
    @JvmField protected val globalConfig: GlobalTaskCreationConfig
) {
    protected val logger: Logger = Logging.getLogger(this.javaClass)
    @JvmField
    protected val taskFactory: TaskFactory = TaskFactoryImpl(project.tasks)

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

    protected fun createVerifyLibraryClassesTask(component: VariantCreationConfig) {
        taskFactory.register(VerifyLibraryClassesTask.CreationAction(component))
        component.taskContainer.assembleTask.configure { task: Task ->
            task.dependsOn(
                    component.artifacts.get(InternalArtifactType.VERIFIED_LIBRARY_CLASSES)
            )
        }
    }

    protected fun registerLibraryRClassTransformStream(component: ComponentCreationConfig) {
        if (!component.buildFeatures.androidResources) {
            return
        }
        component.artifacts.forScope(InternalScopedArtifacts.InternalScope.COMPILE_ONLY)
            .setInitialContent(
                ScopedArtifact.CLASSES,
                component.artifacts,
                InternalArtifactType.COMPILE_R_CLASS_JAR
            )
    }

    protected open fun createPrepareLintJarForPublishTask() {
        taskFactory.register(PrepareLintJarForPublish.CreationAction(globalConfig))
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

    protected open fun createDependencyStreams(creationConfig: ComponentCreationConfig) {
        // Since it's going to chance the configurations, we need to do it before
        // we start doing queries to fill the streams.
        handleJacocoDependencies(creationConfig)
        creationConfig.instrumentationCreationConfig?.configureAndLockAsmClassesVisitors(
            project.objects
        )
        fun getFinalRuntimeClassesJarsFromComponent(
            component: ComponentCreationConfig,
            scope: ArtifactScope
        ): FileCollection {
            return component.instrumentationCreationConfig?.getDependenciesClassesJarsPostInstrumentation(
                scope
            ) ?: component.variantDependencies.getArtifactFileCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                scope,
                AndroidArtifacts.ArtifactType.CLASSES_JAR
            )
        }

        // This might be consumed by RecalculateFixedStackFrames if that's created
        creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
            .setInitialContent(
                ScopedArtifact.CLASSES,
                getFinalRuntimeClassesJarsFromComponent(
                    creationConfig,
                    ArtifactScope.EXTERNAL
                )
            )

        creationConfig
            .artifacts
            .forScope(InternalScopedArtifacts.InternalScope.LOCAL_DEPS)
            .setInitialContent(
                ScopedArtifact.CLASSES,
                creationConfig.computeLocalPackagedJars()
            )

        // Add stream of external java resources if EXTERNAL_LIBRARIES isn't in the set of java res
        // merging scopes.
        if (!javaResMergingScopes.contains(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)) {
            creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
                .setInitialContent(
                    ScopedArtifact.JAVA_RES,
                    creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.JAVA_RES)
                )
        }

        // for the sub modules, new intermediary classes artifact has its own stream
        creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
            .setInitialContent(
                ScopedArtifact.CLASSES,
                getFinalRuntimeClassesJarsFromComponent(
                    creationConfig,
                    ArtifactScope.PROJECT
                )
            )

        // same for the java resources, if SUB_PROJECTS isn't in the set of java res merging scopes.
        if (!javaResMergingScopes.contains(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)) {
            creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                .setInitialContent(
                    ScopedArtifact.JAVA_RES,
                    creationConfig
                        .variantDependencies
                        .getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.JAVA_RES).artifactFiles
                )

        }

        // if consumesFeatureJars, add streams of classes from features or
        // dynamic-features.
        // The main dex list calculation for the bundle also needs the feature classes for reference
        // only
        if ((creationConfig as? ApplicationCreationConfig)?.consumesFeatureJars == true ||
            (creationConfig as? ApkCreationConfig)?.dexing?.needsMainDexListForBundle == true) {
            creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.FEATURES)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    creationConfig
                        .variantDependencies
                        .getArtifactCollection(ConsumedConfigType.REVERSE_METADATA_VALUES,
                            ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.REVERSE_METADATA_CLASSES).artifactFiles
                )
        }

        // provided only scopes.
        creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.COMPILE_ONLY)
            .setInitialContent(
                ScopedArtifact.CLASSES,
                creationConfig.providedOnlyClasspath
            )
        (creationConfig as? TestComponentCreationConfig)?.onTestedVariant { testedVariant ->
            creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.TESTED_CODE)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    getFinalRuntimeClassesJarsFromComponent(
                        testedVariant,
                        ArtifactScope.ALL
                    )
                )
        }
    }

    fun createMergeApkManifestsTask(creationConfig: ApkCreationConfig) {
        if (creationConfig is ApplicationCreationConfig) {
            taskFactory.register(
                CompatibleScreensManifest.CreationAction(
                    creationConfig,
                    screenSizes = (creationConfig.oldVariantApiLegacySupport!!.variantData as ApkVariantData).compatibleScreens
                )
            )
        }
        val processManifestTask = createMergeManifestTasks(creationConfig)
        val taskContainer = creationConfig.taskContainer
        if (taskContainer.microApkTask != null && processManifestTask != null) {
            processManifestTask.dependsOn(taskContainer.microApkTask)
        }
    }

    /** Creates the merge manifests task.  */
    protected open fun createMergeManifestTasks(
        creationConfig: ApkCreationConfig
    ): TaskProvider<out ManifestProcessorTask?>? {
        taskFactory.register(ProcessManifestForBundleTask.CreationAction(creationConfig))
        taskFactory.register(
                ProcessManifestForMetadataFeatureTask.CreationAction(creationConfig))
        taskFactory.register(ProcessManifestForInstantAppTask.CreationAction(creationConfig))
        taskFactory.register(ProcessPackagedManifestTask.CreationAction(creationConfig))
        taskFactory.register(GenerateManifestJarTask.CreationAction(creationConfig))
        taskFactory.register(ProcessApplicationManifest.CreationAction(creationConfig))

        return if (creationConfig is ApplicationCreationConfig) {
            taskFactory.register(ProcessMultiApkApplicationManifest.CreationAction(creationConfig))
        } else {
            taskFactory.register(SimplifiedMergedManifestsProducerTask.CreationAction(creationConfig))
        }
    }

    protected fun createProcessTestManifestTask(creationConfig: TestCreationConfig) {
        taskFactory.register(ProcessTestManifest.CreationAction(creationConfig))
    }

    protected fun createRenderscriptTask(creationConfig: ConsumableCreationConfig) {
        creationConfig.renderscriptCreationConfig?.let { renderscriptCreationConfig ->
            val taskContainer = creationConfig.taskContainer
            val rsTask = taskFactory.register(
                RenderscriptCompile.
                CreationAction(
                    creationConfig,
                    ndkConfig = creationConfig.nativeBuildCreationConfig!!.ndkConfig
                )
            )

            if (!renderscriptCreationConfig.renderscript.ndkModeEnabled.get()) {
                creationConfig.sources.java {
                    it.addSource(
                        TaskProviderBasedDirectoryEntryImpl(
                            name = "generated_renderscript",
                            directoryProvider = creationConfig.artifacts.get(
                                InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
                            ),
                        )
                    )
                }
            }
            taskContainer.resourceGenTask.dependsOn(rsTask)
            // since rs may generate Java code, always set the dependency.
            taskContainer.sourceGenTask.dependsOn(rsTask)
        }
    }

    fun createMergeResourcesTask(
            creationConfig: ComponentCreationConfig,
            processResources: Boolean,
            flags: Set<MergeResources.Flag>) {
        if (!creationConfig.buildFeatures.androidResources &&
            creationConfig !is AndroidTestCreationConfig) {
            return
        }
        val alsoOutputNotCompiledResources = (creationConfig.componentType.isApk
                && !creationConfig.componentType.isForTesting
                && creationConfig.androidResourcesCreationConfig!!.useResourceShrinker)
        val includeDependencies = true
        basicCreateMergeResourcesTask(
                creationConfig,
                MergeType.MERGE,
                includeDependencies,
                processResources,
                alsoOutputNotCompiledResources,
                flags,
                null /*configCallback*/)
        taskFactory.register(
                MapSourceSetPathsTask.CreateAction(creationConfig, includeDependencies))
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
            flags: Set<MergeResources.Flag>,
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
                        creationConfig.componentType.isAar),
                null,
                null,
                taskProviderCallback)
        if (globalConfig.unitTestOptions.isIncludeAndroidResources) {
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

    fun createBuildConfigTask(creationConfig: ConsumableCreationConfig) {
        creationConfig.buildConfigCreationConfig?.let { buildConfigCreationConfig ->
            val generateBuildConfigTask =
                    taskFactory.register(GenerateBuildConfig.CreationAction(creationConfig))
            val isBuildConfigBytecodeEnabled = creationConfig
                    .services
                    .projectOptions[BooleanOption.ENABLE_BUILD_CONFIG_AS_BYTECODE]
            if (!isBuildConfigBytecodeEnabled
                    // TODO(b/224758957): This is wrong we need to check the final build config
                    //  fields from the variant API
                    || buildConfigCreationConfig.dslBuildConfigFields.isNotEmpty()
            ) {
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

    fun createApkProcessResTask(creationConfig: ApkCreationConfig) {
        val componentType = creationConfig.componentType
        val packageOutputType: InternalArtifactType<Directory>? =
                if (componentType.isApk && !componentType.isForTesting) FEATURE_RESOURCE_PKG else null
        createApkProcessResTask(creationConfig, packageOutputType)
        if ((creationConfig as? ApplicationCreationConfig)?.consumesFeatureJars == true) {
            taskFactory.register(MergeAaptProguardFilesCreationAction(creationConfig))
        }
    }

    protected fun createApkProcessResTask(
            creationConfig: ComponentCreationConfig,
            packageOutputType: Single<Directory>?) {
        // Check AAR metadata files
        taskFactory.register(CheckAarMetadataTask.CreationAction(creationConfig))

        val projectInfo = creationConfig.services.projectInfo

        // Create the APK_ file with processed resources and manifest. Generate the R class.
        createProcessResTask(
                creationConfig,
                packageOutputType,
                MergeType.MERGE,
                projectInfo.getProjectBaseName())
        val projectOptions = creationConfig.services.projectOptions
        val nonTransitiveR = projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
        val enableAppCompileRClass = projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]
        val namespaced: Boolean = globalConfig.namespacedAndroidResources

        if (namespaced) return

        if (creationConfig.componentType.isForTesting
            && !isTestApkCompileRClassEnabled(enableAppCompileRClass, creationConfig.componentType)) {
            return
        }

        if (enableAppCompileRClass || nonTransitiveR) {
            // Generate the COMPILE TIME only R class using the local resources instead of waiting
            // for the above full link to finish. Linking will still output the RUN TIME R class.
            // Since we're gonna use AAPT2 to generate the keep rules, do not generate them here.
            createProcessResTask(
                creationConfig,
                packageOutputType,
                MergeType.PACKAGE,
                projectInfo.getProjectBaseName()
            )
        }
    }

    private fun isTestApkCompileRClassEnabled(
        compileRClassFlag: Boolean,
        componentType: ComponentType
    ): Boolean {
        return compileRClassFlag
            && componentType.isForTesting
            && componentType.isApk
    }

    fun createProcessResTask(
            creationConfig: ComponentCreationConfig,
            packageOutputType: Single<Directory>?,
            mergeType: MergeType,
            baseName: Provider<String>) {
        if (!creationConfig.buildFeatures.androidResources &&
            creationConfig !is AndroidTestCreationConfig) {
            return
        }
        creationConfig.oldVariantApiLegacySupport?.variantData?.calculateFilters(
            creationConfig.global.splits
        )

        // The manifest main dex list proguard rules are always needed for the bundle,
        // even if legacy multidex is not explicitly enabled.
        val useAaptToGenerateLegacyMultidexMainDexProguardRules =
                (creationConfig is ApkCreationConfig
                        && creationConfig.dexing.dexingType.isLegacyMultiDex)
        if (globalConfig.namespacedAndroidResources) {
            // TODO: make sure we generate the proguard rules in the namespaced case.
            NamespacedResourcesTaskManager(
                taskFactory,
                creationConfig
            ).createNamespacedResourceTasks(
                packageOutputType,
                baseName,
                useAaptToGenerateLegacyMultidexMainDexProguardRules
            )
            val rFiles: FileCollection = project.files(
                creationConfig.artifacts.get(RUNTIME_R_CLASS_CLASSES))
            creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    rFiles
                )
            creationConfig
                .artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    creationConfig.artifacts,
                    RUNTIME_R_CLASS_CLASSES
                )
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
            baseName: Provider<String>,
            useAaptToGenerateLegacyMultidexMainDexProguardRules: Boolean) {
        val artifacts = creationConfig.artifacts
        val projectOptions = creationConfig.services.projectOptions
        when (mergeType) {
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
                    creationConfig.componentType.isAar) {
                    taskFactory.register(
                            GenerateLibraryProguardRulesTask.CreationAction(creationConfig))
                }
                val nonTransitiveRClassInApp = projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
                val compileTimeRClassInApp = projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]
                // Generate the R class for a library using both local symbols and symbols
                // from dependencies.
                // TODO: double check this (what about dynamic features?)
                if (!nonTransitiveRClassInApp || compileTimeRClassInApp || creationConfig.componentType.isAar) {
                    taskFactory.register(GenerateLibraryRFileTask.CreationAction(
                        creationConfig
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
                                isLibrary = false
                        )
                )
                if (packageOutputType != null) {
                    creationConfig.artifacts.republish(PROCESSED_RES, packageOutputType)
                }

                // TODO: also support stable IDs for the bundle (does it matter?)
                // create the task that creates the aapt output for the bundle.
                if (!creationConfig.componentType.isForTesting) {
                    check(creationConfig is ApkCreationConfig) {
                        "Expected a component that produces an apk, instead found " +
                                "${creationConfig.name} of type ${creationConfig::class.java}."
                    }
                    taskFactory.register(
                            LinkAndroidResForBundleTask.CreationAction(
                                    creationConfig))
                }

                artifacts
                    .forScope(ScopedArtifacts.Scope.PROJECT)
                    .setInitialContent(
                        ScopedArtifact.CLASSES,
                        artifacts,
                        COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR
                    )

                if (!creationConfig.debuggable &&
                        !creationConfig.componentType.isForTesting &&
                         projectOptions[BooleanOption.ENABLE_RESOURCE_OPTIMIZATIONS]) {
                    check(creationConfig is ApkCreationConfig) {
                        "Expected a component that produces an apk, instead found " +
                                "${creationConfig.name} of type ${creationConfig::class.java}."
                    }
                    taskFactory.register(OptimizeResourcesTask.CreateAction(creationConfig))
                }
            }
        }
    }

    /**
     * Returns the scopes for which the java resources should be merged.
     */
    protected abstract val javaResMergingScopes: Set<InternalScopedArtifacts.InternalScope>

    /**
     * Creates the java resources processing tasks.
     *
     * [Sync] task configured with [ProcessJavaResTask.CreationAction] will sync
     * all source folders into a single folder identified by [InternalArtifactType]
     *
     * This sets up only the Sync part. The java res merging is setup via [ ][.createMergeJavaResTask]
     */
    protected fun createProcessJavaResTask(creationConfig: ComponentCreationConfig) {
        // Copy the source folders java resources into the temporary location, mainly to
        // maintain the PluginDsl COPY semantics.
        taskFactory.register(ProcessJavaResTask.CreationAction(creationConfig))
    }

    /**
     * Sets up the Merge Java Res task.
     *
     * @see .createProcessJavaResTask
     */
    protected fun createMergeJavaResTask(creationConfig: ConsumableCreationConfig) {
        // Compute the scopes that need to be merged.
        taskFactory.register(
            MergeJavaResourceTask.CreationAction(
                javaResMergingScopes, creationConfig.packaging, creationConfig
            )
        )
    }

    protected fun createAidlTask(creationConfig: ConsumableCreationConfig) {
        if (creationConfig.buildFeatures.aidl) {
            val taskContainer = creationConfig.taskContainer
            val aidlCompileTask = taskFactory.register(AidlCompile.CreationAction(creationConfig))
            taskContainer.sourceGenTask.dependsOn(aidlCompileTask)
        }
    }

    protected fun createShaderTask(creationConfig: ConsumableCreationConfig) {
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
        // Use the deprecated public artifact types to register the pre/post JavaC hooks as well as
        // the javac output itself.
        // It is necessary to do so in case some third-party plugin is using those deprecated public
        // artifact type to append/transform/replace content.
        // Once the deprecated types can be removed, all the methods below should use the
        // [ScopedArtifacts.setInitialContent] methods to initialize directly the scoped container.
        creationConfig.oldVariantApiLegacySupport?.variantData?.let { variantData ->

            creationConfig
                .artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    variantData.allPreJavacGeneratedBytecode
                )

            creationConfig
                .artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .setInitialContent(
                    ScopedArtifact.CLASSES,
                    variantData.allPostJavacGeneratedBytecode
                )
        }

       creationConfig
           .artifacts
           .forScope(ScopedArtifacts.Scope.PROJECT)
           .setInitialContent(
               ScopedArtifact.CLASSES,
               creationConfig.artifacts,
               JAVAC
           )
    }

    /**
     * Creates the task for creating *.class files using javac. These tasks are created regardless
     * of whether Jack is used or not, but assemble will not depend on them if it is. They are
     * always used when running unit tests.
     */
    protected fun createJavacTask(
            creationConfig: ComponentCreationConfig
    ): TaskProvider<out JavaCompile> {
        val usingKapt = isKotlinKaptPluginApplied(project)
        val usingKsp = isKspPluginApplied(project)
        taskFactory.register(JavaPreCompileTask.CreationAction(creationConfig, usingKapt, usingKsp))
        val javacTask: TaskProvider<out JavaCompile> =
            taskFactory.register(
                JavaCompileCreationAction(
                    creationConfig,
                    project.objects,
                    usingKapt
                )
            )
        postJavacCreation(creationConfig)
        return javacTask
    }

    /**
     * Creates the individual managed device tasks for the given variant
     *
     * @param creationConfig the test config
     * @param testData the extra test data
     * @param variantName name of the variant under test. This can be different from the testing
     * variant.
     * @param testTaskSuffix the suffix to be applied to the individual task names. This should be
     * used if the test config's name does not include a test suffix.
     */
    protected fun createTestDevicesForVariant(
        creationConfig: InstrumentedTestCreationConfig,
        testData: AbstractTestDataImpl,
        variantName: String,
        testTaskSuffix: String = ""
    ) {
        val flavor: String? = testData.flavorName.orNull
        //  TODO(b/271294549): Move BuildTarget into testData
        val buildTarget: String = if (flavor == null) {
            variantName
        } else {
            // build target is the variant with the flavor name stripped from the front.
            variantName.substring(flavor.length).lowercase(Locale.US)
        }

        val managedDevices = getManagedDevices()
        if (managedDevices.isEmpty()) {
            return
        }

        val allDevicesVariantTask = taskFactory.register(
            creationConfig.computeTaskNameInternal("allDevices", testTaskSuffix)
        ) { allDevicesVariant: Task ->
            allDevicesVariant.description =
                "Runs the tests for $variantName on all managed devices in the dsl."
            allDevicesVariant.group = JavaBasePlugin.VERIFICATION_GROUP
        }
        taskFactory.configure(
            globalConfig.taskNames.allDevicesCheck
        ) { allDevices: Task ->
            allDevices.dependsOn(allDevicesVariantTask)
        }

        val resultsRootDir = if (globalConfig.androidTestOptions.resultsDir.isNullOrEmpty()) {
            creationConfig.paths.outputDir(BuilderConstants.FD_ANDROID_RESULTS)
                .get().asFile
        } else {
            File(requireNotNull(globalConfig.androidTestOptions.resultsDir))
        }
        val reportRootDir = if (globalConfig.androidTestOptions.resultsDir.isNullOrEmpty()) {
            creationConfig.paths.reportsDir(BuilderConstants.FD_ANDROID_TESTS)
                .get().asFile
        } else {
            File(requireNotNull(globalConfig.androidTestOptions.reportDir))
        }
        val additionalOutputRootDir = creationConfig.paths.outputDir(
            InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_ADDITIONAL_OUTPUT.getFolderName()
        ).get().asFile
        val coverageOutputRootDir = creationConfig.paths.outputDir(
            InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE.getFolderName()
        ).get().asFile

        val flavorDir = if (flavor.isNullOrEmpty()) "" else "${BuilderConstants.FD_FLAVORS}/$flavor"
        val resultsDir =
            File(resultsRootDir, "${BuilderConstants.MANAGED_DEVICE}/$buildTarget/$flavorDir")
        val reportDir =
            File(reportRootDir, "${BuilderConstants.MANAGED_DEVICE}/$buildTarget/$flavorDir")
        val additionalTestOutputDir = File(additionalOutputRootDir, "$buildTarget/$flavorDir")
        val coverageOutputDir = File(coverageOutputRootDir, "$buildTarget/$flavorDir")

        val deviceToProvider = mutableMapOf<String, TaskProvider<out Task>>()

        for (managedDevice in managedDevices) {
            val registration = globalConfig.managedDeviceRegistry.get(managedDevice.javaClass)

            val deviceResults = File(resultsDir, managedDevice.name)
            val deviceReports = File(reportDir, managedDevice.name)
            val deviceAdditionalOutputs = File(additionalTestOutputDir, managedDevice.name)
            val deviceCoverage = File(coverageOutputDir, managedDevice.name)

            val managedDeviceTestTask = when {
                managedDevice is ManagedVirtualDevice -> taskFactory.register(
                        ManagedDeviceInstrumentationTestTask.CreationAction(
                            creationConfig,
                            managedDevice,
                            testData,
                            deviceResults,
                            deviceReports,
                            deviceAdditionalOutputs,
                            deviceCoverage,
                            testTaskSuffix,
                        )
                    )
                registration != null -> {
                    val setupResult: Provider<Directory>? = if (registration.hasSetupActions) {
                        taskFactory.named(setupTaskName(managedDevice)).flatMap { task ->
                            (task as ManagedDeviceSetupTask).setupResultDir
                        }
                    } else {
                        null
                    }
                    taskFactory.register(
                        ManagedDeviceTestTask.CreationAction(
                            creationConfig,
                            managedDevice,
                            registration.testRunConfigAction,
                            registration.testRunTaskAction,
                            testData,
                            deviceResults,
                            deviceReports,
                            deviceAdditionalOutputs,
                            deviceCoverage,
                            setupResult,
                            testTaskSuffix,
                        )
                    )
                }
                else -> error("Unsupported managed device type: ${managedDevice.javaClass}")
            }
            managedDeviceTestTask.dependsOn(setupTaskName(managedDevice))
            allDevicesVariantTask.dependsOn(managedDeviceTestTask)
            taskFactory.configure(
                managedDeviceAllVariantsTaskName(managedDevice)
            ) { managedDeviceTests: Task ->
                managedDeviceTests.dependsOn(managedDeviceTestTask)
            }
            deviceToProvider[managedDevice.name] = managedDeviceTestTask
        }

        // Register a task to aggregate test suite result protos.
        val testResultAggregationTask = taskFactory.register(
            ManagedDeviceInstrumentationTestResultAggregationTask.CreationAction(
                creationConfig,
                managedDevices.map { File(File(resultsDir, it.name), TEST_RESULT_PB_FILE_NAME) },
                File(resultsDir, TEST_RESULT_PB_FILE_NAME),
                reportDir,
            )
        )
        for (managedDevice in managedDevices) {
            taskFactory.configure(
                managedDeviceAllVariantsTaskName(managedDevice)
            ) { managedDeviceCheck ->
                managedDeviceCheck.dependsOn(testResultAggregationTask)
            }
        }
        deviceToProvider.values.forEach { managedDeviceTestTask ->
            testResultAggregationTask.configure {
                it.mustRunAfter(managedDeviceTestTask)
            }
            // Run test result aggregation task even after test failures.
            managedDeviceTestTask.configure {
                it.finalizedBy(testResultAggregationTask)
            }
        }

        // Register a test coverage report generation task to every managedDeviceCheck
        // task.
        if (creationConfig is TestComponentCreationConfig &&
            creationConfig.isAndroidTestCoverageEnabled) {
            val jacocoAntConfiguration = JacocoConfigurations.getJacocoAntTaskConfiguration(
                project, JacocoTask.getJacocoVersion(creationConfig)
            )
            val reportTask = taskFactory.register(
                JacocoReportTask.CreationActionManagedDeviceTest(
                    creationConfig, jacocoAntConfiguration
                )
            )
            creationConfig.mainVariant.taskContainer.coverageReportTask?.dependsOn(reportTask)
            // Run the report task after all tests are finished on all devices.
            deviceToProvider.values.forEach { managedDeviceTestTask ->
                reportTask.dependsOn(managedDeviceTestTask)
                reportTask.configure {
                    it.mustRunAfter(managedDeviceTestTask)
                }
            }
            taskFactory.configure(globalConfig.taskNames.allDevicesCheck) { allDevices: Task ->
                allDevices.dependsOn(reportTask)
            }
        }

        // Lastly the Device Group Tasks.
        for (group in getDeviceGroups()) {
            val variantDeviceGroupTask = taskFactory.register(
                managedDeviceGroupSingleVariantTaskName(creationConfig, group)
                    .appendCapitalized(testTaskSuffix)
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

    /**
     * Creates the post-compilation tasks for the given Variant.
     *
     *
     * These tasks create the dex file from the .class files, plus optional intermediary steps
     * like proguard and jacoco
     */
    protected fun createPostCompilationTasks(creationConfig: ApkCreationConfig) {
        if (creationConfig !is KmpComponentCreationConfig) {
            Preconditions.checkNotNull(creationConfig.taskContainer.javacTask)
        }
        taskFactory.register(MergeGeneratedProguardFilesCreationAction(creationConfig))

        // Merge Java Resources.
        createMergeJavaResTask(creationConfig)

        // -----------------------------------------------------------------------------------------
        // The following task registrations MUST follow the order:
        //   ASM API -> scoped artifacts transform -> jacoco transforms
        // -----------------------------------------------------------------------------------------

        maybeCreateTransformClassesWithAsmTask(creationConfig)

        // initialize the all classes scope
        creationConfig.artifacts.forScope(ScopedArtifacts.Scope.ALL)
            .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
            .initialScopedContent
            .run {
                from(
                    creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.CLASSES)
                )
                from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                        .getFinalArtifacts(ScopedArtifact.CLASSES)
                )
                from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
                        .getFinalArtifacts(ScopedArtifact.CLASSES)
                )
            }

        creationConfig.artifacts.forScope(ScopedArtifacts.Scope.ALL)
            .getScopedArtifactsContainer(ScopedArtifact.JAVA_RES)
            .initialScopedContent
            .run {
                from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
                        .getFinalArtifacts(ScopedArtifact.JAVA_RES)
                )
                from(
                    creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                        .getFinalArtifacts(ScopedArtifact.JAVA_RES)
                )
                from(
                    creationConfig.artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
                        .getFinalArtifacts(ScopedArtifact.JAVA_RES)
                )
            }

        // New gradle-transform jacoco instrumentation support.
        if (creationConfig.isAndroidTestCoverageEnabled &&
            !creationConfig.componentType.isForTesting) {
            createJacocoTask(creationConfig)
        } else {
            // When the Jacoco task does not run, republish CLASSES into FINAL_TRANSFORMED_CLASSES
            // because this is the artifact that will be used in the following tasks
            enumValues<ScopedArtifacts.Scope>().forEach {
                creationConfig.artifacts.forScope(it).republish(
                    ScopedArtifact.CLASSES, InternalScopedArtifact.FINAL_TRANSFORMED_CLASSES)
            }
        }

        enumValues<InternalScopedArtifacts.InternalScope>().forEach {
            creationConfig.artifacts.forScope(it)
                .republish(
                    ScopedArtifact.CLASSES,
                    InternalScopedArtifact.FINAL_TRANSFORMED_CLASSES
                )
        }

        // Add a task to create merged runtime classes if this is a dynamic-feature,
        // or a base module consuming feature jars. Merged runtime classes are needed if code
        // minification is enabled in a project with features or dynamic-features.
        if (creationConfig.componentType.isDynamicFeature
                || (creationConfig as? ApplicationCreationConfig)?.consumesFeatureJars == true) {
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
        if (creationConfig.optimizationCreationConfig.minifiedEnabled) {
            maybeCreateDesugarLibTask(creationConfig)
            return
        }

        // Code Dexing (MonoDex, Legacy Multidex or Native Multidex)
        if (creationConfig.dexing.needsMainDexListForBundle) {
            taskFactory.register(D8BundleMainDexListTask.CreationAction(creationConfig))
        }
        if (creationConfig.componentType.isDynamicFeature) {
            taskFactory.register(FeatureDexMergeTask.CreationAction(creationConfig))
        }
        createDexTasks(creationConfig, creationConfig.dexing.dexingType)
    }

    /**
     * Creates tasks used for DEX generation. This will use an incremental pipeline that uses dex
     * archives in order to enable incremental dexing support.
     */
    private fun createDexTasks(
            creationConfig: ApkCreationConfig,
            dexingType: DexingType) {
        val classpathUtils = getClassPathUtils(creationConfig)

        taskFactory.register(
                DexArchiveBuilderTask.CreationAction(
                    creationConfig,
                    classpathUtils,
                )
        )

        // When desugaring, The file dependencies are dexed in a task with the whole
        // remote classpath present, as they lack dependency information to desugar
        // them correctly in an artifact transform.
        // This should only be passed to Legacy Multidex MERGE_ALL or MERGE_EXTERNAL_LIBS of
        // other dexing modes, otherwise it will cause the output of DexFileDependenciesTask
        // to be included multiple times and will cause the build to fail because of same types
        // being defined multiple times in the final dex.
        val separateFileDependenciesDexingTask =
            (creationConfig.dexing.java8LangSupportType == Java8LangSupport.D8
                    && classpathUtils.enableDexingArtifactTransform)

        maybeCreateDesugarLibTask(creationConfig)

        createDexMergingTasks(
            creationConfig,
            dexingType,
            classpathUtils.enableDexingArtifactTransform,
            classpathUtils.classesAlteredThroughVariantAPI,
            separateFileDependenciesDexingTask
        )

        if (creationConfig.enableGlobalSynthetics) {
            if (dexingType == DexingType.NATIVE_MULTIDEX) {
                taskFactory.register(
                    GlobalSyntheticsMergeTask.CreationAction(
                        creationConfig,
                        classpathUtils.enableDexingArtifactTransform,
                        separateFileDependenciesDexingTask
                    )
                )
            }

            if (creationConfig.componentType.isDynamicFeature) {
                taskFactory.register(
                    FeatureGlobalSyntheticsMergeTask.CreationAction(
                        creationConfig,
                        classpathUtils.enableDexingArtifactTransform,
                        separateFileDependenciesDexingTask
                    )
                )
            }
        }
    }

    protected fun getClassPathUtils(creationConfig: ApkCreationConfig): ClassesClasspathUtils {
        val java8LangSupport = creationConfig.dexing.java8LangSupportType
        val supportsDesugaringViaArtifactTransform =
                (java8LangSupport == Java8LangSupport.UNUSED
                        || java8LangSupport == Java8LangSupport.D8)

        val classesAlteredThroughVariantAPI = creationConfig
            .artifacts
            .forScope(ScopedArtifacts.Scope.ALL)
            .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
            .artifactsAltered
            .get()

        val enableDexingArtifactTransform =
            supportsDesugaringViaArtifactTransform && !classesAlteredThroughVariantAPI

        return ClassesClasspathUtils(
                creationConfig,
                enableDexingArtifactTransform,
                classesAlteredThroughVariantAPI)
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
            dexingUsingArtifactTransforms: Boolean,
            classesAlteredThroughVariantAPI: Boolean,
            separateFileDependenciesDexingTask: Boolean
    ) {
        // if classes were altered at the ALL scoped level, we just need to merge the single jar
        // file resulting.
        if (classesAlteredThroughVariantAPI) {
            taskFactory.register(DexMergingTask.CreationAction(
                creationConfig,
                DexMergingAction.MERGE_TRANSFORMED_CLASSES,
                dexingType,
                dexingUsingArtifactTransforms))
            return
        }

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

    private fun handleJacocoDependencies(creationConfig: ComponentCreationConfig) {
        if (creationConfig is ApkCreationConfig && creationConfig.packageJacocoRuntime) {
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

    protected open fun createJacocoTask(creationConfig: ComponentCreationConfig) {
        val jacocoTask = taskFactory.register(JacocoTask.CreationAction(creationConfig))

        val classesAlteredThroughVariantAPI = creationConfig
            .artifacts
            .forScope(ScopedArtifacts.Scope.ALL)
            .getScopedArtifactsContainer(ScopedArtifact.CLASSES)
            .artifactsAltered
            .get()
        val scope = if (classesAlteredThroughVariantAPI) ScopedArtifacts.Scope.ALL
            else ScopedArtifacts.Scope.PROJECT

        // in case of application, we want to package the jacoco instrumented classes
        // so we basically transform the classes scoped artifact and republish it
        // untouched as the jacoco transformed artifact so the jacoco report task can
        // find them.
        creationConfig.artifacts.forScope(scope)
            .use(jacocoTask)
            .toFork(
                ScopedArtifact.CLASSES,
                { a ->  a.jarsWithIdentity.inputJars },
                JacocoTask::classesDir,
                JacocoTask::outputForJars,
                JacocoTask::outputForDirs,
                InternalScopedArtifact.FINAL_TRANSFORMED_CLASSES
            )
    }

    protected fun createDataBindingTasksIfNecessary(creationConfig: ComponentCreationConfig) {
        val dataBindingEnabled = creationConfig.buildFeatures.dataBinding
        val viewBindingEnabled = creationConfig.buildFeatures.viewBinding
        if (!dataBindingEnabled && !viewBindingEnabled) {
            return
        }
        taskFactory.register(
                DataBindingMergeDependencyArtifactsTask.CreationAction(creationConfig))
        DataBindingBuilder.setDebugLogEnabled(logger.isDebugEnabled)
        taskFactory.register(DataBindingGenBaseClassesTask.CreationAction(creationConfig))

        // DATA_BINDING_TRIGGER artifact is created for data binding only (not view binding)
        if (dataBindingEnabled) {
            if (creationConfig.services.projectOptions.get(BooleanOption.NON_TRANSITIVE_R_CLASS)
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
            creationConfig.sources.java {
                it.addSource(
                    TaskProviderBasedDirectoryEntryImpl(
                        name = "databinding_generated",
                        directoryProvider = creationConfig.artifacts.get(
                            InternalArtifactType.DATA_BINDING_TRIGGER
                        ),
                    )
                )
            }
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

        // add it the Variant API objects, this is what our tasks use
        processorOptions.argumentProviders.add(dataBindingArgs)
    }

    /**
     * Creates the final packaging task, and optionally the zipalign task (if the variant is signed)
     */
    protected fun createPackagingTask(creationConfig: ApkCreationConfig) {
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
        val manifestType: InternalArtifactType<Directory> =
            creationConfig.global.manifestArtifactType
        val manifests = creationConfig.artifacts.get(manifestType)

        // Common code for both packaging tasks.
        val configureResourcesAndAssetsDependencies = Action { task: Task ->
            task.dependsOn(taskContainer.mergeAssetsTask)
            if (taskContainer.processAndroidResTask != null) {
                task.dependsOn(taskContainer.processAndroidResTask)
            }
        }
        taskFactory.register(
                PackageApplication.CreationAction(
                        creationConfig,
                        creationConfig.paths.apkLocation,
                        manifests,
                        manifestType),
                null,
                object : TaskConfigAction<PackageApplication> {
                    override fun configure(task: PackageApplication) {
                        if (creationConfig !is KmpComponentCreationConfig) {
                            task.dependsOn(taskContainer.javacTask)
                        }
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
        taskFactory.register(
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
        taskFactory.configure(creationConfig.global.taskNames.uninstallAll) { uninstallAll: Task ->
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

    protected fun createAssembleTask(component: ComponentCreationConfig) {
        taskFactory.register(
                component.computeTaskNameInternal("assemble"),
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

    protected open fun maybeCreateJavaCodeShrinkerTask(
            creationConfig: ConsumableCreationConfig) {
        if (creationConfig.optimizationCreationConfig.minifiedEnabled) {
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
                && creationConfig.buildFeatures.androidResources)
        val task: TaskProvider<out Task> =
                createR8Task(creationConfig, isTestApplication, addCompileRClass)
        if (creationConfig.optimizationCreationConfig.postProcessingFeatures != null) {
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

        if (creationConfig.debuggable) {
            globalConfig.buildAnalyzerIssueReporter?.reportIssue(
                TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD
            )
        }

        R8ParallelBuildService.RegistrationAction(
            project,
            creationConfig.services.projectOptions.get(IntegerOption.R8_MAX_WORKERS)
        ).execute()
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
        val modulePaths: Collection<String> = creationConfig.global.dynamicFeatures
        val configuration =
                creationConfig.variantDependencies.getElements(PublishedConfigSpec(PublishedConfigType.RUNTIME_ELEMENTS))
        Preconditions.checkNotNull(
                configuration,
                "Publishing to Runtime Element with no Runtime Elements configuration object. "
                        + "componentType: "
                        + creationConfig.componentType)
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
        creationConfig: ApkCreationConfig
    ) {
        if (creationConfig.androidResourcesCreationConfig?.useResourceShrinker != true) {
            return
        }
        if (creationConfig.componentType.isDynamicFeature) {
            // For bundles resources are shrunk once bundle is packaged so the task is applicable
            // for base module only.
            return
        }
        // Shrink resources in APK with a new resource shrinker and produce stripped res
        // package.
        taskFactory.register(ShrinkResourcesNewShrinkerTask.CreationAction(creationConfig))
        // Shrink resources in bundles with new resource shrinker.
        taskFactory.register(ShrinkAppBundleResourcesTask.CreationAction(creationConfig))
    }

    protected fun createAnchorTasks(creationConfig: ComponentCreationConfig) {
        createVariantPreBuildTask(creationConfig)

        // also create sourceGenTask
        creationConfig
                .taskContainer
                .sourceGenTask = taskFactory.register(
                creationConfig.computeTaskNameInternal("generate", "Sources")
        ) { task: Task ->
            task.dependsOn(creationConfig.global.taskNames.compileLintChecks)
            if (creationConfig.componentType.isAar) {
                task.dependsOn(PrepareLintJarForPublish.NAME)
            }
            creationConfig.oldVariantApiLegacySupport?.variantData?.extraGeneratedResFolders?.let {
                task.dependsOn(it)
            }
        }
        creationConfig
            .taskContainer
            .resourceGenTask = taskFactory.register(ValidateResourcesTask.CreateAction(creationConfig))
        creationConfig
                .taskContainer
                .assetGenTask =
                taskFactory.register(creationConfig.computeTaskNameInternal("generate", "Assets"))
        // Create anchor task for creating instrumentation test coverage reports
        if (creationConfig is VariantCreationConfig && creationConfig.isAndroidTestCoverageEnabled) {
            creationConfig
                    .taskContainer
                    .coverageReportTask = taskFactory.register(
                    creationConfig.computeTaskNameInternal("create", "CoverageReport")
            ) { task: Task ->
                task.group = JavaBasePlugin.VERIFICATION_GROUP
                task.description = String.format(
                        "Creates instrumentation test coverage reports for the %s variant.",
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

    abstract class AbstractPreBuildCreationAction<
            TaskT : AndroidVariantTask,
            ComponentT: ComponentCreationConfig>(
        creationConfig: ComponentT
    ) : VariantTaskCreationAction<TaskT, ComponentT>(creationConfig, false) {

        override val name: String
            get() = computeTaskName("pre", "Build")

        override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.preBuildTask = taskProvider
        }

        override fun configure(task: TaskT) {
            super.configure(task)
            task.dependsOn(creationConfig.global.taskNames.mainPreBuild)
            creationConfig.lifecycleTasks.invokePreBuildActions(task)
        }
    }

    class PreBuildCreationAction(creationConfig: ComponentCreationConfig) :
            AbstractPreBuildCreationAction<AndroidVariantTask, ComponentCreationConfig>(creationConfig) {

        override val type: Class<AndroidVariantTask>
            get() = AndroidVariantTask::class.java
    }

    private fun createCompileAnchorTask(creationConfig: ComponentCreationConfig) {
        val taskContainer = creationConfig.taskContainer
        taskContainer.compileTask = taskFactory.register(
                creationConfig.computeTaskNameInternal("compile", "Sources")
        ) { task: Task -> task.group = BUILD_GROUP }
    }

    protected fun configureTestData(
            creationConfig: InstrumentedTestCreationConfig, testData: AbstractTestDataImpl) {
        testData.animationsDisabled = creationConfig
                .services
                .provider(globalConfig.androidTestOptions::animationsDisabled)
    }

    private fun maybeCreateCheckDuplicateClassesTask(
            creationConfig: ComponentCreationConfig) {
        if (creationConfig
                        .services
                        .projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
            taskFactory.register(CheckDuplicateClassesTask.CreationAction(creationConfig))
        }
    }

    private fun maybeCreateDesugarLibTask(apkCreationConfig: ApkCreationConfig) {
        if (apkCreationConfig.dexing.shouldPackageDesugarLibDex) {
            // The expansion of wildcards using the desugared lib jar should only run when
            // needsShrinkDesugarLibrary is true, and this conditional should match the generation
            // of desugared lib jar in [L8DexDesugarLibTask]
            if (apkCreationConfig.dexing.needsShrinkDesugarLibrary) {
                taskFactory.register(
                    ExpandArtProfileWildcardsTask.ExpandL8ArtProfileCreationAction(apkCreationConfig)
                )
            }

            taskFactory.register(
                L8DexDesugarLibTask.CreationAction(apkCreationConfig)
            )
        }
    }

    protected fun getManagedDevices(): List<Device> {
        return globalConfig
            .androidTestOptions
            .managedDevices
            .allDevices
            .toList()
    }

    protected fun getDeviceGroups(): Collection<DeviceGroup> =
        globalConfig.androidTestOptions.managedDevices.groups

    protected fun maybeCreateTransformClassesWithAsmTask(
        creationConfig: ComponentCreationConfig
    ) {
        val instrumentationCreationConfig = creationConfig.instrumentationCreationConfig ?: return
        if (instrumentationCreationConfig.projectClassesAreInstrumented) {
            val transformTask = taskFactory.register(
                TransformClassesWithAsmTask.CreationAction(
                    creationConfig
                )
            )
            creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .use(transformTask)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    { a ->  a.inputJarsWithIdentity.inputJars },
                    TransformClassesWithAsmTask::inputClassesDir,
                    TransformClassesWithAsmTask::jarsOutputDir,
                    TransformClassesWithAsmTask::classesOutputDir,
                )

            if (instrumentationCreationConfig.asmFramesComputationMode
                    == FramesComputationMode.COMPUTE_FRAMES_FOR_ALL_CLASSES) {
                val recalculateStackFramesTask = taskFactory.register(RecalculateStackFramesTask.CreationAction(creationConfig))
                creationConfig.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                    .use(recalculateStackFramesTask)
                    .toTransform(
                        ScopedArtifact.CLASSES,
                        RecalculateStackFramesTask::jarsInputDir,
                        RecalculateStackFramesTask::classesInputDir,
                        RecalculateStackFramesTask::jarsOutputDir,
                        RecalculateStackFramesTask::classesOutputDir,
                    )
            }
        }
    }

    companion object {
        const val INSTALL_GROUP = "Install"
        const val BUILD_GROUP = BasePlugin.BUILD_GROUP
        const val ANDROID_GROUP = "Android"

        // Task names. These cannot be AndroidTasks as in the component model world there is nothing to
        // force generateTasksBeforeEvaluate to happen before the variant tasks are created.
        const val DEVICE_ANDROID_TEST = BuilderConstants.DEVICE + ComponentType.ANDROID_TEST_SUFFIX
        const val CONNECTED_ANDROID_TEST =
                BuilderConstants.CONNECTED + ComponentType.ANDROID_TEST_SUFFIX
        const val ASSEMBLE_ANDROID_TEST = "assembleAndroidTest"
        const val ASSEMBLE_UNIT_TEST = "assembleUnitTest"

        // Temporary static variables for Kotlin+Compose configuration
        const val COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION = "1.3.2"
        const val COMPOSE_UI_VERSION = "1.3.0"

        /**
         * Create tasks before the evaluation (on plugin apply). This is useful for tasks that could be
         * referenced by custom build logic.
         *
         * @param componentType the main variant type as returned by the [     ]
         * @param sourceSetContainer the container of source set from the DSL.
         */
        @JvmStatic
        fun createTasksBeforeEvaluate(
            project: Project,
            componentType: ComponentType,
            sourceSetContainer: Iterable<AndroidSourceSet?>,
            globalConfig: GlobalTaskCreationConfig
        )  {
            val taskFactory = TaskFactoryImpl(project.tasks)
            taskFactory.register(
                globalConfig.taskNames.uninstallAll
            ) { uninstallAllTask: Task ->
                uninstallAllTask.description = "Uninstall all applications."
                uninstallAllTask.group = INSTALL_GROUP
            }
            taskFactory.register(
                globalConfig.taskNames.deviceCheck
            ) { deviceCheckTask: Task ->
                deviceCheckTask.description =
                        "Runs all device checks using Device Providers and Test Servers."
                deviceCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }
            taskFactory.register(
                globalConfig.taskNames.connectedCheck,
                DeviceSerialTestTask::class.java
            ) { connectedCheckTask: DeviceSerialTestTask ->
                connectedCheckTask.description =
                        "Runs all device checks on currently connected devices."
                connectedCheckTask.group = JavaBasePlugin.VERIFICATION_GROUP
            }

            // Make sure MAIN_PREBUILD runs first:
            taskFactory.register(globalConfig.taskNames.mainPreBuild)
            taskFactory.register(ExtractProguardFiles.CreationAction(globalConfig)).configure {
                it.dependsOn(globalConfig.taskNames.mainPreBuild)
            }
            taskFactory.register(SourceSetsTask.CreationAction(sourceSetContainer))
            taskFactory.register(
                ASSEMBLE_ANDROID_TEST
            ) { assembleAndroidTestTask: Task ->
                assembleAndroidTestTask.group = BasePlugin.BUILD_GROUP
                assembleAndroidTestTask.description = "Assembles all the Test applications."
            }
            taskFactory.register(
                ASSEMBLE_UNIT_TEST
            ) { assembleUnitTestTask: Task ->
                assembleUnitTestTask.group = BasePlugin.BUILD_GROUP
                assembleUnitTestTask.description = "Assembles all the unit test applications."
            }
            taskFactory.register(LintCompile.CreationAction(globalConfig))
            // Don't register global lint or lintFix tasks for dynamic features because dynamic
            // features are analyzed and their lint issues are reported and/or fixed when running
            // lint or lintFix from the base app.
            // Don't register global lint or lintFix tasks for KMP Android components because these
            // global tasks are registered by the standalone lint plugin.
            if (!componentType.isForTesting
                && !componentType.isDynamicFeature
                && componentType != ComponentTypeImpl.KMP_ANDROID) {
                LintTaskManager(globalConfig, taskFactory, project).createBeforeEvaluateLintTasks()
            }

            // for testing only.
            taskFactory.register(
                    "resolveConfigAttr",
                    ConfigAttrTask::class.java) { task: ConfigAttrTask -> task.resolvable = true }
            taskFactory.register(
                    "consumeConfigAttr",
                    ConfigAttrTask::class.java) { task: ConfigAttrTask -> task.consumable = true }
            createCoreLibraryDesugaringConfig(project)
        }

        private fun createCoreLibraryDesugaringConfig(project: Project) {
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
                    && creationConfig.optimizationCreationConfig.minifiedEnabled)
                    || creationConfig.componentType.isDynamicFeature)
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

        /** Returns the full path of a task given its name.  */
        @JvmStatic
        fun getTaskPath(project: Project, taskName: String) =
            if (project.rootProject === project) ":$taskName" else "${project.path}:$taskName"
    }
}
