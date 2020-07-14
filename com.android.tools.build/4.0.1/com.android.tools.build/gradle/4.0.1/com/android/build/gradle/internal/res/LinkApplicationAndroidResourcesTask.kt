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
import com.android.build.VariantOutput
import com.android.build.api.component.ComponentProperties
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.dsl.convert
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.SplitList
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAaptDaemon
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.core.VariantType
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.ide.common.process.ProcessException
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
import java.nio.file.Files
import java.util.ArrayList
import java.util.regex.Pattern
import javax.inject.Inject

@CacheableTask
abstract class LinkApplicationAndroidResourcesTask @Inject constructor(objects: ObjectFactory) :
    ProcessAndroidResources() {

    companion object {
        private val LOG = Logging.getLogger(LinkApplicationAndroidResourcesTask::class.java)

        private fun getOutputBaseNameFile(apkData: ApkData, resPackageOutputFolder: File): File {
            return File(
                resPackageOutputFolder,
                FN_RES_BASE + RES_QUALIFIER_SEP + apkData.fullName + SdkConstants.DOT_RES
            )
        }
    }

    @get:OutputDirectory
    @get:Optional
    abstract val sourceOutputDirProperty: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val textSymbolOutputFileProperty: RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    @get:Optional
    abstract val symbolsWithPackageNameOutputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val proguardOutputFile: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val rClassOutputJar: RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    @get:Optional
    abstract val mainDexListProguardOutputFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    var dependenciesFileCollection: FileCollection? = null
        private set

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
    abstract val debuggable: Property<Boolean>

    private lateinit var aaptOptions: AaptOptions

    @Nested
    fun getAaptOptionsInput() = LinkingTaskInputAaptOptions(aaptOptions)

    private lateinit var mergeBlameLogFolder: File

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var featureResourcePackages: FileCollection? = null
        private set

    @get:Input
    abstract val originalApplicationId: Property<String>

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
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val convertedLibraryDependencies: DirectoryProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResourcesDir: DirectoryProperty

    @get:Input
    var isLibrary: Boolean = false
        private set

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @get:Input
    var useFinalIds: Boolean = true
        private set

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    // Not an input as it is only used to rewrite exception and doesn't affect task output
    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private var compiledDependenciesResources: ArtifactCollection? = null

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    // FIXME : make me incremental !
    override fun doFullTaskAction() {
        val outputDirectory = resPackageOutputFolder.get().asFile
        FileUtils.deleteDirectoryContents(outputDirectory)

        val manifestBuildElements = ExistingBuildElements.from(taskInputType, manifestFiles)

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
            val unprocessedManifest = manifestBuildElements.toMutableList()
            val mainOutput = chooseOutput(manifestBuildElements)

            unprocessedManifest.remove(mainOutput)

            val compiledDependenciesResourcesDirs =
                getCompiledDependenciesResources()?.reversed()?.toImmutableList()
                    ?: emptyList<File>()

            it.submit(
                AaptSplitInvoker::class.java,
                AaptSplitInvokerParams(
                    mainOutput,
                    dependencies,
                    imports,
                    splitList,
                    featureResourcePackages,
                    mainOutput.apkData,
                    true,
                    aapt2ServiceKey,
                    compiledDependenciesResourcesDirs,
                    this,
                    rClassOutputJar.orNull?.asFile
                )
            )

            if (canHaveSplits.get()) {
                // If there are remaining splits to be processed we await for the main split to
                // finish since the output of the main split is used by the full splits bellow.
                it.await()

                for (manifestBuildOutput in unprocessedManifest) {
                    val apkInfo = manifestBuildOutput.apkData
                    if (apkInfo.requiresAapt()) {
                        it.submit(
                            AaptSplitInvoker::class.java,
                            AaptSplitInvokerParams(
                                manifestBuildOutput,
                                dependencies,
                                imports,
                                splitList,
                                featureResourcePackages,
                                apkInfo,
                                false,
                                aapt2ServiceKey,
                                compiledDependenciesResourcesDirs,
                                this
                            )
                        )
                    }
                }
            }
            it
        }
    }

    private fun chooseOutput(manifestBuildElements: BuildElements): BuildOutput {
            val nonDensity = manifestBuildElements
                .stream()
                .filter { output ->
                    output.apkData.getFilter(VariantOutput.FilterType.DENSITY) == null
                }
                .findFirst()
            if (!nonDensity.isPresent) {
                throw RuntimeException("No non-density apk found")
            }
            return nonDensity.get()
    }

    abstract class BaseCreationAction(
        private val componentProperties: ComponentProperties,
        scope: VariantScope,
        private val generateLegacyMultidexMainDexProguardRules: Boolean,
        private val baseName: String?,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<LinkApplicationAndroidResourcesTask>(scope) {

        override val name: String
            get() = variantScope.getTaskName("process", "Resources")

        override val type: Class<LinkApplicationAndroidResourcesTask>
            get() = LinkApplicationAndroidResourcesTask::class.java

        protected open fun preconditionsCheck(variantData: BaseVariantData) {}

        override fun handleProvider(
            taskProvider: TaskProvider<out LinkApplicationAndroidResourcesTask>
        ) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processAndroidResTask = taskProvider
            variantScope.artifacts.producesDir(
                InternalArtifactType.PROCESSED_RES,
                taskProvider,
                LinkApplicationAndroidResourcesTask::resPackageOutputFolder
            )

            if (generatesProguardOutputFile(variantScope)) {
                variantScope.artifacts.producesFile(
                    InternalArtifactType.AAPT_PROGUARD_FILE,
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::proguardOutputFile,
                    SdkConstants.FN_AAPT_RULES
                )
            }

            if (generateLegacyMultidexMainDexProguardRules) {
                variantScope.artifacts.producesFile(
                    InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::mainDexListProguardOutputFile,
                    "manifest_keep.txt"
                )
            }
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)
            val variantScope = variantScope
            val variantData = variantScope.variantData
            val projectOptions = variantScope.globalScope.projectOptions
            val variantDslInfo = variantData.variantDslInfo

            preconditionsCheck(variantData)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            val project = variantScope.globalScope.project
            task.applicationId.setDisallowChanges(componentProperties.applicationId)

            task.incrementalFolder = variantScope.getIncrementalDir(name)
            if (variantData.type.canHaveSplits) {
                val splits = variantScope.globalScope.extension.splits

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
                val resConfigSet = variantScope.variantDslInfo.resourceConfigurations

                task.splitList = SplitList(densitySet, languageSet, abiSet, resConfigSet)
            } else {
                task.splitList = SplitList(
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    ImmutableSet.of()
                )
            }

            task.mainSplit = variantData.publicVariantPropertiesApi.outputs.getMainSplitOrNull()?.apkData
            task.originalApplicationId.set(project.provider { variantDslInfo.originalApplicationId })
            task.originalApplicationId.disallowChanges()

            val aaptFriendlyManifestsFilePresent = variantScope
                .artifacts
                .hasFinalProduct(InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS)
            task.taskInputType = if (aaptFriendlyManifestsFilePresent)
                InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS
            else
                variantScope.manifestArtifactType
            variantScope.artifacts.setTaskInputToFinalProduct(task.taskInputType, task.manifestFiles)

            task.setType(variantDslInfo.variantType)
            task.debuggable.setDisallowChanges(variantData.variantDslInfo.isDebuggable)
            task.aaptOptions = variantScope.globalScope.extension.aaptOptions.convert()

            task.buildTargetDensity = projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY)

            task.useConditionalKeepRules = projectOptions.get(BooleanOption.CONDITIONAL_KEEP_RULES)
            task.useMinimalKeepRules = projectOptions.get(BooleanOption.MINIMAL_KEEP_RULES)
            task.canHaveSplits.set(variantScope.type.canHaveSplits)

            task.setMergeBlameLogFolder(variantScope.resourceBlameLogDir)

            val variantType = variantScope.type

            // Tests should not have feature dependencies, however because they include the
            // tested production component in their dependency graph, we see the tested feature
            // package in their graph. Therefore we have to manually not set this up for tests.
            task.featureResourcePackages = if (variantType.isForTesting)
                null
            else
                variantScope.getArtifactFileCollection(
                    COMPILE_CLASSPATH, PROJECT, FEATURE_RESOURCE_PKG
                )

            if (variantType.isDynamicFeature) {
                task.resOffset.set(variantScope.resOffset)
                task.resOffset.disallowChanges()
            }

            task.projectBaseName = baseName!!
            task.isLibrary = isLibrary

            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            task.useFinalIds = !projectOptions.get(BooleanOption.USE_NON_FINAL_RES_IDS)

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )

            task.manifestMergeBlameFile = variantScope.artifacts.getFinalProduct(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }

    internal class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        private val sourceArtifactType: TaskManager.MergeType,
        baseName: String,
        isLibrary: Boolean
    ) : BaseCreationAction(componentProperties, componentProperties.variantScope, generateLegacyMultidexMainDexProguardRules, baseName, isLibrary) {

        override fun preconditionsCheck(variantData: BaseVariantData) {
            if (variantData.type.isAar) {
                throw IllegalArgumentException("Use GenerateLibraryRFileTask")
            } else {
                Preconditions.checkState(
                    sourceArtifactType === TaskManager.MergeType.MERGE,
                    "source output type should be MERGE",
                    sourceArtifactType
                )
            }
        }

        override fun handleProvider(taskProvider: TaskProvider<out LinkApplicationAndroidResourcesTask>) {
            super.handleProvider(taskProvider)

            if (variantScope.globalScope.projectOptions[BooleanOption.GENERATE_R_JAVA]) {
                variantScope.artifacts.producesDir(
                    InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::sourceOutputDirProperty,
                    fileName = SdkConstants.FD_RES_CLASS
                )
            } else {
                variantScope
                    .artifacts
                    .producesFile(
                        InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR,
                        taskProvider,
                        LinkApplicationAndroidResourcesTask::rClassOutputJar,
                        FN_R_CLASS_JAR
                    )
            }

            variantScope.artifacts.producesFile(
                InternalArtifactType.RUNTIME_SYMBOL_LIST,
                taskProvider,
                LinkApplicationAndroidResourcesTask::textSymbolOutputFileProperty,
                SdkConstants.FN_RESOURCE_TEXT
            )

            if (!variantScope.globalScope.projectOptions[BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS]) {
                // Synthetic output for AARs (see SymbolTableWithPackageNameTransform), and created
                // in process resources for local subprojects.
                variantScope.artifacts.producesFile(
                    InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                    taskProvider,
                    LinkApplicationAndroidResourcesTask::symbolsWithPackageNameOutputFile,
                    "package-aware-r.txt"
                )
            }
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)

            task.dependenciesFileCollection = variantScope
                .getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME
                )
            variantScope.artifacts.setTaskInputToFinalProduct(
                sourceArtifactType.outputType,
                task.inputResourcesDir
            )

            if (variantScope.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources =
                    variantScope.getArtifactCollection(
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
        componentProperties: ComponentProperties,
        scope: VariantScope,
        generateLegacyMultidexMainDexProguardRules: Boolean,
        baseName: String?
    ) : BaseCreationAction(componentProperties, scope, generateLegacyMultidexMainDexProguardRules, baseName, false) {

        override fun handleProvider(taskProvider: TaskProvider<out LinkApplicationAndroidResourcesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.RUNTIME_R_CLASS_SOURCES,
                taskProvider,
                LinkApplicationAndroidResourcesTask::sourceOutputDirProperty,
                fileName = "out"
            )
        }

        override fun configure(task: LinkApplicationAndroidResourcesTask) {
            super.configure(task)

            val projectOptions = variantScope.globalScope.projectOptions

            val dependencies = ArrayList<FileCollection>(2)
            dependencies.add(
                variantScope.globalScope.project.files(
                    variantScope.artifacts.getFinalProduct(
                        InternalArtifactType.RES_STATIC_LIBRARY))
            )
            dependencies.add(
                variantScope.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY
                )
            )
            if (variantScope.globalScope.extension.aaptOptions.namespaced && projectOptions.get(
                    BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES
                )
            ) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES,
                    task.convertedLibraryDependencies)
            }

            task.dependenciesFileCollection =
                variantScope.globalScope.project.files(dependencies)

            task.sharedLibraryDependencies = variantScope.getArtifactFileCollection(
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
                variantType: VariantType,
                output: BuildOutput,
                resPackageOutputFolder: File
            ) {
                val buildOutputs = ArrayList(
                    ExistingBuildElements.from(resPackageOutputFolder).elements
                )
                buildOutputs.add(output)
                BuildElements(
                    applicationId = applicationId,
                    variantType = variantType.toString(),
                    elements = buildOutputs).save(resPackageOutputFolder)
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
                val buildElements = ExistingBuildElements.from(
                    InternalArtifactType.PROCESSED_RES, featurePackage
                )
                if (!buildElements.isEmpty()) {
                    val mainBuildOutput = buildElements.elementByType(VariantOutput.OutputType.MAIN)
                    if (mainBuildOutput != null) {
                        featurePackagesBuilder.add(mainBuildOutput.outputFile)
                    } else {
                        throw IOException(
                            "Cannot find PROCESSED_RES output for " + params.apkData
                        )
                    }
                }
            }

            val resOutBaseNameFile =
                getOutputBaseNameFile(params.apkData, params.resPackageOutputFolder)
            val manifestFile = params.manifestOutput.outputFile

            var packageForR: String? = null
            var srcOut: File? = null
            var symbolOutputDir: File? = null
            var proguardOutputFile: File? = null
            var mainDexListProguardOutputFile: File? = null
            if (params.generateCode) {
                packageForR = params.originalApplicationId

                // we have to clean the source folder output in case the package name changed.
                srcOut = params.sourceOutputDir
                if (srcOut != null) {
                    FileUtils.cleanOutputDir(srcOut)
                }

                symbolOutputDir = params.textSymbolOutputFile?.parentFile
                proguardOutputFile = params.proguardOutputFile
                mainDexListProguardOutputFile = params.mainDexListProguardOutputFile
            }

            val densityFilterData = params.apkData.getFilter(VariantOutput.FilterType.DENSITY)
            // if resConfigs is set, we should not use our preferredDensity.
            val preferredDensity =
                densityFilterData?.identifier
                    ?: if (params.resourceConfigs.isEmpty()) params.buildTargetDensity else null


            try {

                // If the new resources flag is enabled and if we are dealing with a library process
                // resources through the new parsers
                run {
                    val configBuilder = AaptPackageConfig.Builder()
                        .setManifestFile(manifestFile)
                        .setOptions(params.aaptOptions)
                        .setCustomPackageForR(packageForR)
                        .setSymbolOutputDir(symbolOutputDir)
                        .setSourceOutputDir(srcOut)
                        .setResourceOutputApk(resOutBaseNameFile)
                        .setProguardOutputFile(proguardOutputFile)
                        .setMainDexListProguardOutputFile(mainDexListProguardOutputFile)
                        .setVariantType(params.variantType)
                        .setDebuggable(params.debuggable)
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

                    if (params.isNamespaced) {
                        val packagedDependencies = ImmutableList.builder<File>()
                        packagedDependencies.addAll(params.dependencies)
                        if (params.convertedLibraryDependenciesFile != null) {
                            Files.list(params.convertedLibraryDependenciesFile.toPath()).map { it.toFile() }
                                .forEach { packagedDependencies.add(it) }
                        }
                        configBuilder.setStaticLibraryDependencies(packagedDependencies.build())
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
                        manifestFile.toPath(),
                        params.symbolsWithPackageNameOutputFile.toPath()
                    )
                }
                appendOutput(
                    params.applicationId.orEmpty(),
                    params.variantType,
                    BuildOutput(
                        InternalArtifactType.PROCESSED_RES,
                        params.apkData,
                        resOutBaseNameFile,
                        params.manifestOutput.properties
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
        val manifestOutput: BuildOutput,
        val dependencies: Set<File>,
        val imports: Set<File>,
        splitList: SplitList,
        val featureResourcePackages: Set<File>,
        val apkData: ApkData,
        val generateCode: Boolean,
        val aapt2ServiceKey: Aapt2DaemonServiceKey?,
        val compiledDependenciesResourcesDirs: List<File>,
        task: LinkApplicationAndroidResourcesTask,
        val rClassOutputJar: File? = null
    ) : Serializable {
        val resourceConfigs: Set<String> = splitList.resourceConfigs
        val resPackageOutputFolder: File = task.resPackageOutputFolder.get().asFile
        val isNamespaced: Boolean = task.isNamespaced
        val originalApplicationId: String? = task.originalApplicationId.get()
        val applicationId: String? = task.applicationId.get()
        val sourceOutputDir: File? = task.getSourceOutputDir()
        val textSymbolOutputFile: File? = task.textSymbolOutputFileProperty.orNull?.asFile
        val proguardOutputFile: File? = task.proguardOutputFile.orNull?.asFile
        val mainDexListProguardOutputFile: File? = task.mainDexListProguardOutputFile.orNull?.asFile
        val buildTargetDensity: String? = task.buildTargetDensity
        val aaptOptions: AaptOptions = task.aaptOptions
        val variantType: VariantType = task.type
        val debuggable: Boolean = task.debuggable.get()
        val packageId: Int? = task.resOffset.orNull
        val incrementalFolder: File = task.incrementalFolder!!
        val androidJarPath: String =
            task.androidJar.get().absolutePath
        val convertedLibraryDependenciesFile= task.convertedLibraryDependencies.orNull?.asFile
        val inputResourcesDir: File? = task.inputResourcesDir.orNull?.asFile
        val mergeBlameFolder: File = task.mergeBlameLogFolder
        val isLibrary: Boolean = task.isLibrary
        val symbolsWithPackageNameOutputFile: File? = task.symbolsWithPackageNameOutputFile.orNull?.asFile
        val useConditionalKeepRules: Boolean = task.useConditionalKeepRules
        val useMinimalKeepRules: Boolean = task.useMinimalKeepRules
        val useFinalIds: Boolean = task.useFinalIds
        val errorFormatMode: SyncOptions.ErrorFormatMode = task.errorFormatMode
        val manifestMergeBlameFile: File? = task.manifestMergeBlameFile.orNull?.asFile
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

    fun setMergeBlameLogFolder(mergeBlameLogFolder: File) {
        this.mergeBlameLogFolder = mergeBlameLogFolder
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

    private fun findPackagedResForSplit(outputFolder: File?, apkData: ApkData): File? {
        val resourcePattern = Pattern.compile(
            FN_RES_BASE + RES_QUALIFIER_SEP + apkData.fullName + ".ap__(.*)"
        )

        if (outputFolder == null) {
            return null
        }
        val files = outputFolder.listFiles()
        if (files != null) {
            for (file in files) {
                val match = resourcePattern.matcher(file.name)
                // each time we match, we remove the associated filter from our copies.
                if (match.matches()
                    && !match.group(1).isEmpty()
                    && isValidSplit(apkData, match.group(1))
                ) {
                    return file
                }
            }
        }
        return null
    }

    /**
     * Returns true if the passed split identifier is a valid identifier (valid mean it is a
     * requested split for this task). A density split identifier can be suffixed with characters
     * added by aapt.
     */
    private fun isValidSplit(apkData: ApkData, splitWithOptionalSuffix: String): Boolean {

        var splitFilter = apkData.getFilter(VariantOutput.FilterType.DENSITY)
        if (splitFilter != null) {
            if (splitWithOptionalSuffix.startsWith(splitFilter.identifier)) {
                return true
            }
        }
        val mangledName = unMangleSplitName(splitWithOptionalSuffix)
        splitFilter = apkData.getFilter(VariantOutput.FilterType.LANGUAGE)
        return splitFilter != null && mangledName == splitFilter.identifier
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
