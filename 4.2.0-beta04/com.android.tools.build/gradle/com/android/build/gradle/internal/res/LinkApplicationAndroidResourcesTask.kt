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
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.SymbolTableBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.services.getErrorFormatMode
import com.android.build.gradle.internal.services.getLeasingAapt2
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.ide.common.process.ProcessException
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.SymbolIo
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
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
import org.gradle.api.provider.SetProperty
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
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.util.ArrayList
import javax.inject.Inject

@CacheableTask
abstract class LinkApplicationAndroidResourcesTask @Inject constructor(objects: ObjectFactory) :
    ProcessAndroidResources() {

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
    abstract val dependenciesFileCollection: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localResourcesFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sharedLibraryDependencies: ConfigurableFileCollection

    @get:Optional
    @get:Input
    abstract val resOffset: Property<Int>

    private lateinit var type: VariantType

    @get:Input
    val canHaveSplits: Property<Boolean> = objects.property(Boolean::class.java)

    @get:Input
    abstract val resourceConfigs: SetProperty<String>

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
    abstract val featureResourcePackages: ConfigurableFileCollection

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

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Internal
    abstract val symbolTableBuildService: Property<SymbolTableBuildService>

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledDependenciesResources: ConfigurableFileCollection

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
        workerExecutor.noIsolation().submit(TaskAction::class.java) { parameters ->
            parameters.initializeFromAndroidVariantTask(this)

            parameters.mainDexListProguardOutputFile.set(mainDexListProguardOutputFile)
            parameters.outputStableIdsFile.set(stableIdsOutputFileProperty)
            parameters.proguardOutputFile.set(proguardOutputFile)
            parameters.rClassOutputJar.set(rClassOutputJar)
            parameters.resPackageOutputDirectory.set(resPackageOutputFolder)
            parameters.sourceOutputDirectory.set(sourceOutputDirProperty)
            parameters.symbolsWithPackageNameOutputFile.set(symbolsWithPackageNameOutputFile)
            parameters.textSymbolOutputFile.set(textSymbolOutputFileProperty)

            parameters.androidJarInput.set(androidJarInput)
            parameters.aapt2.set(aapt2)
            parameters.symbolTableBuildService.set(symbolTableBuildService)

            parameters.aaptOptions.set(AaptOptions(noCompress.orNull, aaptAdditionalParameters.orNull))
            parameters.applicationId.set(applicationId)
            parameters.buildTargetDensity.set(buildTargetDensity)
            parameters.canHaveSplits.set(canHaveSplits)
            parameters.compiledDependenciesResources.from(compiledDependenciesResources)
            parameters.dependencies.from(dependenciesFileCollection)
            parameters.featureResourcePackages.from(featureResourcePackages)
            parameters.imports.from(sharedLibraryDependencies)
            parameters.incrementalDirectory.set(incrementalFolder)
            parameters.inputResourcesDirectory.set(inputResourcesDir)
            parameters.inputStableIdsFile.set(inputStableIdsFile)
            parameters.library.set(isLibrary)
            parameters.localResourcesFile.set(localResourcesFile)
            parameters.manifestFiles.set(if (aaptFriendlyManifestFiles.isPresent) aaptFriendlyManifestFiles else manifestFiles)
            parameters.manifestMergeBlameFile.set(manifestMergeBlameFile)
            parameters.mergeBlameDirectory.set(mergeBlameLogFolder)
            parameters.namespaced.set(isNamespaced)
            parameters.packageId.set(resOffset)
            parameters.packageName.set(packageName)
            parameters.resourceConfigs.set(resourceConfigs)
            parameters.sharedLibraryDependencies.from(sharedLibraryDependencies)
            parameters.useConditionalKeepRules.set(useConditionalKeepRules)
            parameters.useFinalIds.set(useFinalIds)
            parameters.useMinimalKeepRules.set(useMinimalKeepRules)
            parameters.useStableIds.set(useStableIds)
            parameters.variantName.set(variantName)
            parameters.variantOutputs.set(variantOutputs.get().map { it.toSerializedForm() })
            parameters.variantType.set(type)
        }
    }

    abstract class TaskWorkActionParameters : ProfileAwareWorkAction.Parameters() {

        abstract val mainDexListProguardOutputFile: RegularFileProperty
        abstract val outputStableIdsFile: RegularFileProperty
        abstract val proguardOutputFile: RegularFileProperty
        abstract val rClassOutputJar: RegularFileProperty
        abstract val resPackageOutputDirectory: DirectoryProperty
        abstract val sourceOutputDirectory: DirectoryProperty
        abstract val symbolsWithPackageNameOutputFile: RegularFileProperty
        abstract val textSymbolOutputFile: RegularFileProperty

        @get:Nested abstract val androidJarInput: Property<AndroidJarInput>
        @get:Nested abstract val aapt2: Property<Aapt2Input>
        abstract val symbolTableBuildService: Property<SymbolTableBuildService>
        abstract val aaptOptions: Property<AaptOptions>
        abstract val applicationId: Property<String>
        abstract val buildTargetDensity: Property<String?>
        abstract val canHaveSplits: Property<Boolean>
        abstract val compiledDependenciesResources: ConfigurableFileCollection
        abstract val dependencies: ConfigurableFileCollection
        abstract val featureResourcePackages: ConfigurableFileCollection
        abstract val imports: ConfigurableFileCollection
        abstract val incrementalDirectory: DirectoryProperty
        abstract val inputResourcesDirectory: DirectoryProperty
        abstract val inputStableIdsFile: RegularFileProperty
        abstract val library: Property<Boolean>
        abstract val localResourcesFile: RegularFileProperty
        abstract val manifestFiles: DirectoryProperty
        abstract val manifestMergeBlameFile: RegularFileProperty
        abstract val mergeBlameDirectory: DirectoryProperty
        abstract val namespaced: Property<Boolean>
        abstract val packageId: Property<Int>
        abstract val packageName: Property<String>
        abstract val resourceConfigs: SetProperty<String>
        abstract val sharedLibraryDependencies: ConfigurableFileCollection
        abstract val useConditionalKeepRules: Property<Boolean>
        abstract val useFinalIds: Property<Boolean>
        abstract val useMinimalKeepRules: Property<Boolean>
        abstract val useStableIds: Property<Boolean>
        abstract val variantName: Property<String>
        abstract val variantOutputs: ListProperty<VariantOutputImpl.SerializedForm>
        abstract val variantType: Property<VariantType>
    }

    abstract class TaskAction : ProfileAwareWorkAction<TaskWorkActionParameters>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun run() {
            val manifestBuiltArtifacts = BuiltArtifactsLoaderImpl().load(parameters.manifestFiles)
                ?: throw RuntimeException("Cannot load processed manifest files, please file a bug.")
            // 'Incremental' runs should only preserve the stable IDs file.
            FileUtils.deleteDirectoryContents(parameters.resPackageOutputDirectory.get().asFile)

            val variantOutputsList: List<VariantOutputImpl.SerializedForm> = parameters.variantOutputs.get()
            val mainOutput = chooseOutput(variantOutputsList)


            invokeAaptForSplit(
                    mainOutput,
                    manifestBuiltArtifacts.getBuiltArtifact(mainOutput.variantOutputConfiguration)
                        ?: throw RuntimeException("Cannot find built manifest for $mainOutput"),
                    true,
                    parameters.aapt2.get().getLeasingAapt2(),
                    parameters.inputStableIdsFile.orNull?.asFile, parameters
            )

            if (parameters.canHaveSplits.get()) {
                // This must happen after the main split is done, since the output of the main
                // split is used by the full splits.
                val workQueue = workerExecutor.noIsolation()
                val unprocessedOutputs = variantOutputsList.minus(mainOutput)
                for (variantOutput in unprocessedOutputs) {

                    val manifestOutput: BuiltArtifactImpl =
                        manifestBuiltArtifacts.getBuiltArtifact(variantOutput.variantOutputConfiguration)
                            ?: throw RuntimeException("Cannot find build manifest for $variantOutput")

                    workQueue.submit(InvokeAaptForSplitAction::class.java) { splitParameters ->
                        splitParameters.initializeFromProfileAwareWorkAction(this.parameters)
                        splitParameters.globalParameters.set(parameters)
                        splitParameters.variantOutput.set(variantOutput)
                        splitParameters.manifestOutput.set(manifestOutput)
                    }
                }
            }
        }
        private fun chooseOutput(variantOutputs: List<VariantOutputImpl.SerializedForm>): VariantOutputImpl.SerializedForm =
            variantOutputs.firstOrNull { variantOutput ->
                variantOutput.variantOutputConfiguration.getFilter(
                    FilterConfiguration.FilterType.DENSITY
                ) == null
            } ?: throw RuntimeException("No non-density apk found")

    }


    abstract class InvokeAaptForSplitAction : ProfileAwareWorkAction<InvokeAaptForSplitAction.Parameters>() {
        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val globalParameters: Property<TaskWorkActionParameters>
            abstract val variantOutput: Property<VariantOutputImpl.SerializedForm>
            abstract val manifestOutput: Property<BuiltArtifactImpl>
        }

        override fun run() {
            // If we're supporting stable IDs we need to make sure the splits get exactly
            // the same IDs as the main one.
            val globalParameters = parameters.globalParameters.get()
            invokeAaptForSplit(
                parameters.variantOutput.get(),
                parameters.manifestOutput.get(),
                false,
                globalParameters.aapt2.get().getLeasingAapt2(),
                if (globalParameters.useStableIds.get()) globalParameters.outputStableIdsFile.get().asFile else null,
                globalParameters)
        }
    }

    abstract class BaseCreationAction(
        creationConfig: ComponentCreationConfig,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: String?,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<LinkApplicationAndroidResourcesTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("process", "Resources")

        override val type: Class<LinkApplicationAndroidResourcesTask>
            get() = LinkApplicationAndroidResourcesTask::class.java

        protected open fun preconditionsCheck(creationConfig: ComponentCreationConfig) {}

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

            task.applicationId.setDisallowChanges(creationConfig.applicationId)

            task.incrementalFolder = creationConfig.paths.getIncrementalDir(name)

            task.resourceConfigs.setDisallowChanges(
                if (creationConfig.variantType.canHaveSplits) {
                    creationConfig.resourceConfigurations
                } else {
                    ImmutableSet.of()
                }
            )

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

            task.mergeBlameLogFolder.setDisallowChanges(
                creationConfig.artifacts.get(
                    InternalArtifactType.MERGED_RES_BLAME_FOLDER
                )
            )

            val variantType = creationConfig.variantType

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            if (!variantType.isForTesting) {
                task.featureResourcePackages.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG
                    )
                )
            } else {
                task.featureResourcePackages.disallowChanges()
            }

            if (variantType.isDynamicFeature && creationConfig is DynamicFeatureCreationConfig) {
                task.resOffset.set(creationConfig.resOffset)
                task.resOffset.disallowChanges()
            }

            task.projectBaseName = baseName!!
            task.isLibrary = isLibrary

            task.useFinalIds = !projectOptions.get(BooleanOption.USE_NON_FINAL_RES_IDS)

            task.manifestMergeBlameFile = creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.symbolTableBuildService.set(getBuildService(creationConfig.services.buildServiceRegistry))
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
        creationConfig: ComponentCreationConfig,
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

        override fun preconditionsCheck(creationConfig: ComponentCreationConfig) {
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
                && creationConfig.services.projectOptions[BooleanOption.NON_TRANSITIVE_APP_R_CLASS]
            ) {
                // List of local resources, used to generate a non-transitive R for the app module.
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LOCAL_ONLY_SYMBOL_LIST,
                    task.localResourcesFile
                )
            }

            task.dependenciesFileCollection.fromDisallowChanges(
                creationConfig
                    .variantDependencies.getArtifactFileCollection(
                        RUNTIME_CLASSPATH,
                        ALL,
                        AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                    )
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                sourceArtifactType.outputType,
                task.inputResourcesDir
            )

            if (creationConfig.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        RUNTIME_CLASSPATH,
                        ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                    ))
            } else {
                task.compiledDependenciesResources.disallowChanges()
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
                    creationConfig.artifacts.get(InternalArtifactType.RES_STATIC_LIBRARY)
                )
            )
            dependencies.add(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH, ALL, AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
                )
            )

            task.dependenciesFileCollection.fromDisallowChanges(
                creationConfig.services.fileCollection(
                    dependencies
                )
            )

            task.sharedLibraryDependencies.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    COMPILE_CLASSPATH, ALL, AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY
                )
            )

            task.isNamespaced = true
        }
    }

    companion object {
        private val LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

        private fun getOutputBaseNameFile(
            variantOutput: VariantOutputImpl.SerializedForm,
            resPackageOutputFolder: File
        ): File {
            return File(
                resPackageOutputFolder,
                FN_RES_BASE + RES_QUALIFIER_SEP + variantOutput.fullName + SdkConstants.DOT_RES
            )
        }

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

        @Throws(IOException::class)
        private fun invokeAaptForSplit(
            variantOutput: VariantOutputImpl.SerializedForm,
            manifestOutput: BuiltArtifactImpl,
            generateRClass: Boolean,
            aapt2: Aapt2,
            stableIdsInputFile: File?,
            parameters: TaskWorkActionParameters) {

            val featurePackagesBuilder = ImmutableList.builder<File>()
            for (featurePackage in parameters.featureResourcePackages.files) {
                val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(featurePackage)

                if (buildElements?.elements != null && buildElements.elements.isNotEmpty()) {
                    val mainBuildOutput =
                        buildElements.getBuiltArtifact(VariantOutputConfiguration.OutputType.SINGLE)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(File(mainBuildOutput.outputFile))
                    } else {
                        throw IOException(
                            "Cannot find PROCESSED_RES output for " + variantOutput
                        )
                    }
                }
            }

            val resOutBaseNameFile =
                getOutputBaseNameFile(variantOutput,
                    parameters.resPackageOutputDirectory.get().asFile
                )
            val manifestFile = manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (generateRClass) {
                packageForR = if (parameters.variantType.get().isForTesting) {
                    // Workaround for b/162244493: Use application ID in the test variant to match
                    // previous behaviour.
                    // TODO(170945282): migrate everything to use the actual package name in AGP
                    //  7.0.
                    parameters.applicationId.get()
                } else {
                    parameters.packageName.get()
                }


                // we have to clean the source folder output in case the package name changed.
                srcOut = parameters.sourceOutputDirectory.orNull?.asFile
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = parameters.textSymbolOutputFile.orNull?.asFile?.parentFile
                proguardOutputFile = parameters.proguardOutputFile.orNull?.asFile
                mainDexListProguardOutputFile = parameters.mainDexListProguardOutputFile.orNull?.asFile
            }

            val densityFilterData = variantOutput.variantOutputConfiguration
                .getFilter(FilterConfiguration.FilterType.DENSITY)
            // if resConfigs is set, we should not use our preferredDensity.
            val preferredDensity =
                densityFilterData?.identifier
                    ?: if (parameters.resourceConfigs.get().isEmpty()) parameters.buildTargetDensity.orNull else null


            try {

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                run {
                    val configBuilder = AaptPackageConfig.Builder()
                        .setManifestFile(File(manifestFile))
                        .setOptions(parameters.aaptOptions.get())
                        .setCustomPackageForR(packageForR)
                        .setSymbolOutputDir(symbolOutputDir)
                        .setSourceOutputDir(srcOut)
                        .setResourceOutputApk(resOutBaseNameFile)
                        .setProguardOutputFile(proguardOutputFile)
                        .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                        .setVariantType(parameters.variantType.get())
                        .setResourceConfigs(parameters.resourceConfigs.get())
                        .setPreferredDensity(preferredDensity)
                        .setPackageId(parameters.packageId.orNull)
                        .setAllowReservedPackageId(
                            parameters.packageId.isPresent && parameters.packageId.get() < FeatureSetMetadata.BASE_ID
                        )
                        .setDependentFeatures(featurePackagesBuilder.build())
                        .setImports(parameters.imports.files)
                        .setIntermediateDir(parameters.incrementalDirectory.get().asFile)
                        .setAndroidJarPath(parameters.androidJarInput.get().getAndroidJar().get().absolutePath)
                        .setUseConditionalKeepRules(parameters.useConditionalKeepRules.get())
                        .setUseMinimalKeepRules(parameters.useMinimalKeepRules.get())
                        .setUseFinalIds(parameters.useFinalIds.get())
                        .addResourceDirectories(parameters.compiledDependenciesResources.files.reversed().toImmutableList())
                        .setEmitStableIdsFile(parameters.outputStableIdsFile.orNull?.asFile)
                        .setConsumeStableIdsFile(stableIdsInputFile)
                        .setLocalSymbolTableFile(parameters.localResourcesFile.orNull?.asFile)
                        .setMergeBlameDirectory(parameters.mergeBlameDirectory.get().asFile)
                        .setManifestMergeBlameFile(parameters.manifestMergeBlameFile.orNull?.asFile)

                    if (parameters.namespaced.get()) {
                        configBuilder.setStaticLibraryDependencies(ImmutableList.copyOf(parameters.dependencies.files))
                    } else {
                        if (generateRClass) {
                            configBuilder.setLibrarySymbolTableFiles(parameters.dependencies.files)
                        }
                        configBuilder.addResourceDir(checkNotNull(parameters.inputResourcesDirectory.orNull?.asFile))
                    }

                    val logger = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

                    processResources(
                        aapt = aapt2,
                        aaptConfig = configBuilder.build(),
                        rJar = if(generateRClass) parameters.rClassOutputJar.orNull?.asFile else null,
                        logger = logger,
                        errorFormatMode = parameters.aapt2.get().getErrorFormatMode(),
                        symbolTableLoader = parameters.symbolTableBuildService.get()::loadClasspath
                    )

                    if (LOG.isInfoEnabled) {
                        LOG.info("Aapt output file {}", resOutBaseNameFile.absolutePath)
                    }
                }

                if (generateRClass
                    && (parameters.library.get() || !parameters.dependencies.files.isEmpty())
                    && parameters.symbolsWithPackageNameOutputFile.isPresent
                ) {
                    SymbolIo.writeSymbolListWithPackageName(
                        parameters.textSymbolOutputFile.orNull?.asFile!!.toPath(),
                        packageForR,
                        parameters.symbolsWithPackageNameOutputFile.get().asFile.toPath()
                    )
                }
                appendOutput(
                    parameters.applicationId.get().orEmpty(),
                    parameters.variantName.get(),
                    manifestOutput.newOutput(
                        resOutBaseNameFile.toPath()
                    ),
                    parameters.resPackageOutputDirectory.get().asFile
                )
            } catch (e: ProcessException) {
                throw BuildException(
                    "Failed to process resources, see aapt output above for details.", e
                )
            }
        }
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
}
