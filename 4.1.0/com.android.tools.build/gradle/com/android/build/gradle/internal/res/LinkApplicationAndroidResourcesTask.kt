/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res

import com.android.SdkConstants
import com.android.SdkConstants.FN_RES_BASE
import com.android.SdkConstants.FN_R_CLASS_JAR
import com.android.SdkConstants.RES_QUALIFIER_SEP
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.BaseCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.SplitList
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.getAaptDaemon
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.process.ProcessException
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.SymbolIo
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.ArrayList
import javax.inject.Inject

@CacheableTask
abstract class LinkApplicationAndroidResourcesTask @Inject constructor(objects: ObjectFactory) :
    ProcessAndroidResources() {

    companion object {
        private val LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

        private fun getOutputBaseNameFile(variantOutput: VariantOutputImpl.SerializedForm,
            resPackageOutputFolder: File): File {
            return File(
                resPackageOutputFolder,
                FN_RES_BASE + RES_QUALIFIER_SEP + variantOutput.fullName + SdkConstants.DOT_RES
            )
        }
    }

    @get:OutputDirectory
    @get:Optional
    abstract val sourceOutputDirProperty: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val textSymbolOutputFileProperty: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val symbolsWithPackageNameOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val proguardOutputFile: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val rClassOutputJar: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val mainDexListProguardOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val stableIdsOutputFileProperty: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var dependenciesFileCollection: FileCollection? = null
        private set

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localResourcesFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var sharedLibraryDependencies: FileCollection? = null
        private set

    @get:Optional
    @get:Input
    abstract val resOffset: Property<Int>

    private lateinit var type: VariantType

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:Input
    val canHaveSplits: Property<Boolean> = objects.property(Boolean::class.java)

    @get:Input
    @get:Optional
    abstract val noCompress: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val aaptAdditionalParameters: ListProperty<String>

    // Not an input as it is only used to rewrite exceptions and doesn't affect task output
    @get:Internal
    abstract val mergeBlameLogFolder: DirectoryProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var featureResourcePackages: FileCollection? = null
        private set

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    @get:Optional
    var buildTargetDensity: String? = null
        private set

    @get:Input
    var useConditionalKeepRules: Boolean = false
        private set

    @get:Input
    var useMinimalKeepRules: Boolean = false
        private set

    @get:OutputDirectory
    abstract val resPackageOutputFolder: DirectoryProperty

    @get:Input
    lateinit var projectBaseName: String
        private set

    @get:Input
    lateinit var taskInputType: InternalArtifactType<Directory>
        private set

    @get:Input
    var isNamespaced = false
        private set

    @get:Nested
    @get:Optional
    lateinit var splitList: SplitList
        private set

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResourcesDir: DirectoryProperty

    @get:Input
    var isLibrary: Boolean = false
        private set

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    @get:Input
    var useFinalIds: Boolean = true
        private set

    @get:Input
    var useStableIds: Boolean = false
        internal set

    @get:Nested
    abstract val variantOutputs : ListProperty<VariantOutputImpl>

    // aarMetadataCheck doesn't affect the task output, but it's marked as an input so that this
    // task depends on CheckAarMetadataTask.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aarMetadataCheck: ConfigurableFileCollection

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private var compiledDependenciesResources: ArtifactCollection? = null

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override val incremental: Boolean
        get() = useStableIds

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        // For now, we don't care about what changed - we only want to preserve the res IDs from the
        // previous run if stable IDs support is enabled.
        doFullTaskAction(stableIdsOutputFileProperty.orNull?.asFile)
    }

    override fun doFullTaskAction() {
        doFullTaskAction(null)
    }

    fun doFullTaskAction(inputStableIdsFile: File?) {
        val outputDirectory = resPackageOutputFolder.get().asFile
        FileUtils.deleteDirectoryContents(outputDirectory)

        val manifestBuiltArtifacts =
            (if (aaptFriendlyManifestFiles.isPresent)
                BuiltArtifactsLoaderImpl().load(aaptFriendlyManifestFiles)
            else BuiltArtifactsLoaderImpl().load(manifestFiles))
                ?: throw RuntimeException("Cannot load processed manifest files, please file a bug.")

        val featureResourcePackages = if (featureResourcePackages != null)
            featureResourcePackages!!.files
        else
            ImmutableSet.of()

        val dependencies = if (dependenciesFileCollection != null)
            dependenciesFileCollection!!.files
        else
            emptySet()
        val imports = if (sharedLibraryDependencies != null)
            sharedLibraryDependencies!!.files
        else
            emptySet()
        val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
            aapt2FromMaven.singleFile, LoggerWrapper(logger)
        )

        getWorkerFacadeWithWorkers().use {
            val variantOutputsList = variantOutputs.get()

            val unprocessedOutputs = variantOutputsList.toMutableList()
            val mainOutput = chooseOutput(variantOutputsList)

            unprocessedOutputs.remove(mainOutput)

            val compiledDependenciesResourcesDirs =
                getCompiledDependenciesResources()?.reversed()?.toImmutableList()
                    ?: emptyList<File>()

            it.submit(
                AaptSplitInvoker::class.java,
                AaptSplitInvokerParams(
                    mainOutput.toSerializedForm(),
                    manifestBuiltArtifacts.getBuiltArtifact(mainOutput)
                        ?: throw RuntimeException("Cannot find built manifest for $mainOutput"),
                    dependencies,
                    localResourcesFile.orNull?.asFile,
                    imports,
                    splitList,
                    featureResourcePackages,
                    true,
                    aapt2ServiceKey,
                    compiledDependenciesResourcesDirs,
                    this,
                    rClassOutputJar.orNull?.asFile,
                    inputStableIdsFile,
                    AaptOptions(noCompress.orNull, aaptAdditionalParameters.orNull)
                )
            )

            if (canHaveSplits.get()) {
                // If there are remaining splits to be processed we await for the main split to
                // finish since the output of the main split is used by the full splits bellow.
                it.await()

                for (variantOutput in unprocessedOutputs) {
                    // If we're supporting stable IDs we need to make sure the splits get exactly
                    // the same IDs as the main one.
                    it.submit(
                        AaptSplitInvoker::class.java,
                        AaptSplitInvokerParams(
                            variantOutput.toSerializedForm(),
                            manifestBuiltArtifacts.getBuiltArtifact(variantOutput)
                                ?: throw RuntimeException("Cannot find build manifest for $variantOutput"),
                            dependencies,
                            localResourcesFile.orNull?.asFile,
                            imports,
                            splitList,
                            featureResourcePackages,
                            false,
                            aapt2ServiceKey,
                            compiledDependenciesResourcesDirs,
                            this,
                            null,
                            if (useStableIds) stableIdsOutputFileProperty.get().asFile else null,
                            AaptOptions(noCompress.orNull, aaptAdditionalParameters.orNull)
                        )
                    )
                }
            }
            it
        }
    }

    private fun chooseOutput(variantOutputs: List<VariantOutputImpl>): VariantOutputImpl =
           variantOutputs.firstOrNull{ variantOutput ->
               variantOutput.variantOutputConfiguration.getFilter(
                   FilterConfiguration.FilterType.DENSITY) == null
                } ?: throw RuntimeException("No non-density apk found")

    abstract class BaseCreationAction(
        creationConfig: BaseCreationConfig,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: String?,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<LinkApplicationAndroidResourcesTask, BaseCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("process", "Resources")

        override val type: Class<LinkApplicationAndroidResourcesTask>
            get() = LinkApplicationAndroidResourcesTask::class.java

        protected open fun preconditionsCheck(creationConfig: BaseCreationConfig) {}

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processAndroidResTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::resPackageOutputFolder
            ).withName("out").on(InternalArtifactType.PROCESSED_RES)

            if (generatesProguardOutputFile(creationConfig)) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::proguardOutputFile
                ).withName(SdkConstants.FN_AAPT_RULES).on(InternalArtifactType.AAPT_PROGUARD_FILE)
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::mainDexListProguardOutputFile
                ).withName("manifest_keep.txt").on(InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES)
            }

            if (creationConfig.services.projectOptions[BooleanOption.ENABLE_STABLE_IDS]) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::stableIdsOutputFileProperty
                ).withName("stableIds.txt").on(InternalArtifactType.STABLE_RESOURCE_IDS_FILE)
            }
        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)
            val projectOptions = creationConfig.services.projectOptions

            preconditionsCheck(creationConfig)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(creationConfig.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.applicationId.setDisallowChanges(creationConfig.applicationId)

            task.incrementalFolder = creationConfig.paths.getIncrementalDir(name)
            if (creationConfig.variantType.canHaveSplits) {
                val splits = creationConfig.globalScope.extension.splits

                val densitySet = if (splits.density.isEnable)
                    ImmutableSet.copyOf(splits.densityFilters)
                else
                    ImmutableSet.of()
                val languageSet = if (splits.language.isEnable)
                    ImmutableSet.copyOf(splits.languageFilters)
                else
                    ImmutableSet.of()
                val abiSet = if (splits.abi.isEnable)
                    ImmutableSet.copyOf(splits.abiFilters)
                else
                    ImmutableSet.of()
                val resConfigSet = creationConfig.resourceConfigurations

                task.splitList = SplitList(densitySet, languageSet, abiSet, resConfigSet)
            } else {
                task.splitList = SplitList(
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of()
                )
            }

            task.mainSplit = creationConfig.outputs.getMainSplitOrNull()
            task.packageName.setDisallowChanges(creationConfig.packageName)

            task.taskInputType = creationConfig.manifestArtifactType
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS, task.aaptFriendlyManifestFiles
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(task.taskInputType, task.manifestFiles)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_MANIFESTS,
                task.mergedManifestFiles
            )

            task.setType(creationConfig.variantType)
            if (creationConfig is ApkCreationConfig) {
                task.noCompress.setDisallowChanges(creationConfig.globalScope.extension.aaptOptions.noCompress)
                task.aaptAdditionalParameters.set(creationConfig.aaptOptions.additionalParameters)
            }
            task.noCompress.disallowChanges()
            task.aaptAdditionalParameters.disallowChanges()

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.useConditionalKeepRules = projectOptions.get(BooleanOption.CONDITIONAL_KEEP_RULES)
            task.useMinimalKeepRules = projectOptions.get(BooleanOption.MINIMAL_KEEP_RULES)
            task.canHaveSplits.set(creationConfig.variantType.canHaveSplits)

            task.mergeBlameLogFolder.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_BLAME_FOLDER))

            val variantType = creationConfig.variantType

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            task.featureResourcePackages = if (variantType.isForTesting)
                null
            else
                creationConfig.variantDependencies.getArtifactFileCollection(
                    COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG
                )

            if (variantType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.projectBaseName = baseName!!
            task.isLibrary = isLibrary

            task.useFinalIds = !projectOptions.get(BooleanOption.USE_NON_FINAL_RES_IDS)

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.services.projectOptions
            )

            task.manifestMergeBlameFile = creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )
            task.aapt2DaemonBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.androidJarInput.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )

            task.useStableIds = projectOptions[BooleanOption.ENABLE_STABLE_IDS]

            creationConfig.outputs.getEnabledVariantOutputs().forEach(task.variantOutputs::add)

            task.aarMetadataCheck.from(
                creationConfig.artifacts.get(InternalArtifactType.AAR_METADATA_CHECK)
            )
        }
    }

    internal class CreationAction(
        creationConfig: BaseCreationConfig,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        private val sourceArtifactType: TaskManager.MergeType,
        baseName: String,
        isLibrary: Boolean
    ) : BaseCreationAction(
        creationConfig,
        generateLegacyMultidexMainDexProguardRules,
        baseName,
        isLibrary
    ) {

        override fun preconditionsCheck(creationConfig: BaseCreationConfig) {
            if (creationConfig.variantType.isAar) {
                throw IllegalArgumentException("Use GenerateLibraryRFileTask")
            } else {
                Preconditions.checkState(
                    sourceArtifactType === TaskManager.MergeType.MERGE,
                    "source output type should be MERGE",
                    sourceArtifactType
                )
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::rClassOutputJar
            ).withName(FN_R_CLASS_JAR).on(InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::textSymbolOutputFileProperty
            ).withName( SdkConstants.FN_RESOURCE_TEXT).on(InternalArtifactType.RUNTIME_SYMBOL_LIST)

            if (!creationConfig.services.projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]) {
                // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created
                // in process resources for local subprojects.
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::symbolsWithPackageNameOutputFile
                ).withName("package-aware-r.txt").on(InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            }
        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)

            // TODO: Remove separate flag for app R class.
            if (creationConfig.services.projectOptions[BooleanOption.NON_TRANSITIVE_R_CLASS]
                && creationConfig.services.projectOptions[BooleanOption.NON_TRANSITIVE_APP_R_CLASS]) {
                // List of local resources, used to generate a non-transitive R for the app module.
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                    task.localResourcesFile)
            }

            task.dependenciesFileCollection = creationConfig
                .variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                sourceArtifactType.outputType,
                task.inputResourcesDir
            )

            if (creationConfig.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources =
                    creationConfig.variantDependencies.getArtifactCollection(
                        RUNTIME_CLASSPATH,
                        ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                    )
            }
        }
    }

    /**
     * TODO: extract in to a separate task implementation once splits are calculated in the split
     * discovery task.
     */
    class NamespacedCreationAction(
        creationConfig: ApkCreationConfig,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        baseName: String?
    ) : BaseCreationAction(
        creationConfig,
        generateLegacyMultidexMainDexProguardRules,
        baseName,
        false
    ) {

        override fun handleProvider(
            taskProvider: TaskProvider<LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                LinkApplicationAndroidResourcesTask::sourceOutputDirProperty
            ).withName("out").on(InternalArtifactType.RUNTIME_R_CLASS_SOURCES)
        }

        override fun configure(
            task: LinkApplicationAndroidResourcesTask
        ) {
            super.configure(task)

            val dependencies = ArrayList<FileCollection>(2)
            dependencies.add(
                creationConfig.services.fileCollection(
                    creationConfig.artifacts.get(InternalArtifactType.RES_STATIC_LIBRARY))
            )
            dependencies.add(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
                )
            )

            task.dependenciesFileCollection = creationConfig.services.fileCollection(dependencies)

            task.sharedLibraryDependencies = creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY
            )

            task.isNamespaced = true
        }
    }

    private class AaptSplitInvoker @Inject
    internal constructor(private val params: AaptSplitInvokerParams) : Runnable {

        companion object {
            @Synchronized
            @Throws(IOException::class)
            fun appendOutput(
                applicationId: String,
                variantName: String,
                output: BuiltArtifactImpl,
                resPackageOutputFolder: File
            ) {
                val currentBuiltArtifacts = ArrayList(
                    BuiltArtifactsLoaderImpl.loadFromDirectory(resPackageOutputFolder)?.elements
                        ?: ArrayList()
                )
                currentBuiltArtifacts.add(output)
                BuiltArtifactsImpl(
                    artifactType = InternalArtifactType.PROCESSED_RES,
                    applicationId = applicationId,
                    variantName = variantName,
                    elements = currentBuiltArtifacts
                ).saveToDirectory(resPackageOutputFolder)
            }
        }

        override fun run() {
            try {
                invokeAaptForSplit(params)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }

        @Throws(IOException::class)
        private fun invokeAaptForSplit(params: AaptSplitInvokerParams) {

            val featurePackagesBuilder = ImmutableList.builder<File>()
            for (featurePackage in params.featureResourcePackages) {
                val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(featurePackage)

                if (buildElements?.elements != null && buildElements.elements.isNotEmpty()) {
                    val mainBuildOutput = buildElements.getBuiltArtifact(VariantOutputConfiguration.OutputType.SINGLE)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(File(mainBuildOutput.outputFile))
                    } else {
                        throw IOException(
                            "Cannot find PROCESSED_RES output for " + params.variantOutput
                        )
                    }
                }
            }

            val resOutBaseNameFile =
                getOutputBaseNameFile(params.variantOutput, params.resPackageOutputFolder)
            val manifestFile = params.manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (params.generateCode) {
                packageForR = if (params.variantType.isForTesting) {
                    // Workaround for b/162244493: Use application ID in the test variant to match
                    // previous behaviour.
                    // TODO(b/162244493): migrate everything to use the actual package name in
                    //  AGP 5.0.
                    params.applicationId
                } else {
                    params.packageName
                }

                // we have to clean the source folder output in case the package name changed.
                srcOut = params.sourceOutputDir
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = params.textSymbolOutputFile?.parentFile
                proguardOutputFile = params.proguardOutputFile
                mainDexListProguardOutputFile = params.mainDexListProguardOutputFile
            }

            val densityFilterData = params.variantOutput.variantOutputConfiguration
                .getFilter(FilterConfiguration.FilterType.DENSITY)
            // if resConfigs is set, we should not use our preferredDensity.
            val preferredDensity =
                densityFilterData?.identifier
                    ?: if (params.resourceConfigs.isEmpty()) params.buildTargetDensity else null


            try {

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                run {
                    val configBuilder = AaptPackageConfig.Builder()
                        .setManifestFile(File(manifestFile))
                        .setOptions(params.aaptOptions)
                        .setCustomPackageForR(packageForR)
                        .setSymbolOutputDir(symbolOutputDir)
                        .setSourceOutputDir(srcOut)
                        .setResourceOutputApk(resOutBaseNameFile)
                        .setProguardOutputFile(proguardOutputFile)
                        .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                        .setVariantType(params.variantType)
                        .setResourceConfigs(params.resourceConfigs)
                        .setPreferredDensity(preferredDensity)
                        .setPackageId(params.packageId)
                        .setAllowReservedPackageId(
                            params.packageId != null && params.packageId < FeatureSetMetadata.BASE_ID
                        )
                        .setDependentFeatures(featurePackagesBuilder.build())
                        .setImports(params.imports)
                        .setIntermediateDir(params.incrementalFolder)
                        .setAndroidJarPath(params.androidJarPath)
                        .setUseConditionalKeepRules(params.useConditionalKeepRules)
                        .setUseMinimalKeepRules(params.useMinimalKeepRules)
                        .setUseFinalIds(params.useFinalIds)
                        .addResourceDirectories(params.compiledDependenciesResourcesDirs)
                        .setEmitStableIdsFile(params.stableIdsOutputFile)
                        .setConsumeStableIdsFile(params.stableIdsInputFile)
                        .setLocalSymbolTableFile(params.localResourcesFile)

                    if (params.isNamespaced) {
                        configBuilder.setStaticLibraryDependencies(ImmutableList.copyOf(params.dependencies))
                    } else {
                        if (params.generateCode) {
                            configBuilder.setLibrarySymbolTableFiles(params.dependencies)
                        }
                        configBuilder.addResourceDir(checkNotNull(params.inputResourcesDir))
                    }

                    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                    Preconditions.checkNotNull<Aapt2DaemonServiceKey>(
                        params.aapt2ServiceKey, "AAPT2 daemon manager service not initialized"
                    )
                    val logger = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)
                    try {
                        getAaptDaemon(params.aapt2ServiceKey!!).use { aaptDaemon ->

                            processResources(
                                aaptDaemon,
                                configBuilder.build(),
                                params.rClassOutputJar,
                                LoggerWrapper(logger)
                            )
                        }
                    } catch (e: Aapt2Exception) {
                        throw rewriteLinkException(
                            e,
                            params.errorFormatMode,
                            params.mergeBlameFolder,
                            params.manifestMergeBlameFile,
                            logger
                        )
                    }

                    if (LOG.isInfoEnabled) {
                        LOG.info("Aapt output file {}", resOutBaseNameFile.absolutePath)
                    }
                }
                if (params.generateCode
                    && (params.isLibrary || !params.dependencies.isEmpty())
                    && params.symbolsWithPackageNameOutputFile != null
                ) {
                    SymbolIo.writeSymbolListWithPackageName(
                        params.textSymbolOutputFile!!.toPath(),
                        packageForR,
                        params.symbolsWithPackageNameOutputFile.toPath()
                    )
                }
                appendOutput(
                    params.applicationId.orEmpty(),
                    params.variantName,
                    params.manifestOutput.newOutput(
                        resOutBaseNameFile.toPath()
                    ),
                    params.resPackageOutputFolder
                )
            } catch (e: ProcessException) {
                throw BuildException(
                    "Failed to process resources, see aapt output above for details.", e
                )
            }
        }
    }

    private class AaptSplitInvokerParams internal constructor(
        val variantOutput: VariantOutputImpl.SerializedForm,
        val manifestOutput: BuiltArtifactImpl,
        val dependencies: Set<File>,
        val localResourcesFile: File?,
        val imports: Set<File>,
        splitList: SplitList,
        val featureResourcePackages: Set<File>,
        val generateCode: Boolean,
        val aapt2ServiceKey: Aapt2DaemonServiceKey?,
        val compiledDependenciesResourcesDirs: List<File>,
        task: LinkApplicationAndroidResourcesTask,
        val rClassOutputJar: File? = null,
        val stableIdsInputFile: File?,
        val aaptOptions: AaptOptions
    ) : Serializable {
        val resourceConfigs: Set<String> = splitList.resourceConfigs
        val resPackageOutputFolder: File = task.resPackageOutputFolder.get().asFile
        val isNamespaced: Boolean = task.isNamespaced
        val packageName: String = task.packageName.get()
        val applicationId: String? = task.applicationId.get()
        val sourceOutputDir: File? = task.getSourceOutputDir()
        val textSymbolOutputFile: File? = task.textSymbolOutputFileProperty.orNull?.asFile
        val proguardOutputFile: File? = task.proguardOutputFile.orNull?.asFile
        val mainDexListProguardOutputFile: File? = task.mainDexListProguardOutputFile.orNull?.asFile
        val buildTargetDensity: String? = task.buildTargetDensity
        val variantType: VariantType = task.type
        val variantName: String = task.name
        val packageId: Int? = task.resOffset.orNull
        val incrementalFolder: File = task.incrementalFolder!!
        val androidJarPath: String = task.androidJarInput.getAndroidJar().get().absolutePath
        val inputResourcesDir: File? = task.inputResourcesDir.orNull?.asFile
        val mergeBlameFolder: File = task.mergeBlameLogFolder.get().asFile
        val isLibrary: Boolean = task.isLibrary
        val symbolsWithPackageNameOutputFile: File? = task.symbolsWithPackageNameOutputFile.orNull?.asFile
        val useConditionalKeepRules: Boolean = task.useConditionalKeepRules
        val useMinimalKeepRules: Boolean = task.useMinimalKeepRules
        val useFinalIds: Boolean = task.useFinalIds
        val errorFormatMode: SyncOptions.ErrorFormatMode = task.errorFormatMode
        val manifestMergeBlameFile: File? = task.manifestMergeBlameFile.orNull?.asFile
        val stableIdsOutputFile: File? = task.stableIdsOutputFileProperty.orNull?.asFile
    }

    @Internal // sourceOutputDirProperty is already marked as @OutputDirectory
    override fun getSourceOutputDir(): File? {
        return sourceOutputDirProperty.orNull?.asFile
    }

    @Suppress("unused") // Used by butterknife
    @Internal
    fun getTextSymbolOutputFile(): File? = textSymbolOutputFileProperty.orNull?.asFile

    @Input
    fun getTypeAsString(): String {
        return type.name
    }

    fun setType(type: VariantType) {
        this.type = type
    }

    /**
     * Returns a file collection of the directories containing the compiled dependencies resource
     * files.
     */
    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getCompiledDependenciesResources(): FileCollection? {
        return compiledDependenciesResources?.artifactFiles
    }

    /**
     * Un-mangle a split name as created by the aapt tool to retrieve a split name as configured in
     * the project's build.gradle.
     *
     *
     * when dealing with several split language in a single split, each language (+ optional
     * region) will be separated by an underscore.
     *
     *
     * note that there is currently an aapt bug, remove the 'r' in the region so for instance,
     * fr-rCA becomes fr-CA, temporarily put it back until it is fixed.
     *
     * @param splitWithOptionalSuffix the mangled split name.
     */
    private fun unMangleSplitName(splitWithOptionalSuffix: String): String {
        val mangledName = splitWithOptionalSuffix.replace("_".toRegex(), ",")
        return if (mangledName.contains("-r")) mangledName else mangledName.replace("-", "-r")
    }
}
