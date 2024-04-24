/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.api.variant.impl.*
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dsl.ModulePropertyKey.OptionalString
import com.android.build.gradle.internal.manifest.parseManifest
import com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.*
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.signing.SigningConfigDataProvider
import com.android.build.gradle.internal.signing.SigningConfigProviderParams
import com.android.build.gradle.internal.tasks.ModuleMetadata.Companion.load
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.SigningConfigUtils.Companion.loadSigningConfigVersions
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.PackageApplication.Companion.recordMetrics
import com.android.builder.core.DefaultManifestParser
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.dexing.DexingType
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.builder.files.*
import com.android.builder.internal.packaging.ApkFlinger
import com.android.builder.packaging.PackagingUtils
import com.android.builder.utils.isValidZipEntryName
import com.android.ide.common.resources.FileStatus
import com.android.tools.build.apkzlib.utils.IOExceptionWrapper
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import com.google.common.collect.Sets
import com.google.common.io.ByteStreams
import org.gradle.api.file.*
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.*
import java.nio.file.Files
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.text.Charsets.UTF_8

/** Abstract task to package an Android artifact.  */
@DisableCachingByDefault
abstract class PackageAndroidArtifact : NewIncrementalTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    @get:InputFiles
    abstract val manifests: DirectoryProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    @get:InputFiles
    abstract val resourceFiles: DirectoryProperty

    @get:Input
    abstract val abiFilters: SetProperty<String>

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val baseModuleMetadata: ConfigurableFileCollection

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Incremental
    @get:InputFile
    abstract val appMetadata: RegularFileProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Incremental
    @get:InputFiles
    abstract val mergedArtProfile: RegularFileProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Incremental
    @get:InputFiles
    abstract val mergedArtProfileMetadata: RegularFileProperty

    @get:OutputDirectory
    abstract val incrementalFolder: DirectoryProperty
    private var manifestType: Artifact<Directory>? = null

    @get:Input
    val manifestTypeName: String
        get() = manifestType!!.name()

    @get:Incremental
    @get:Classpath
    abstract val javaResourceFiles: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val featureJavaResourceFiles: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val jniFolders: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    @get:InputFiles
    abstract val dexFolders: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    @get:InputFiles
    abstract val featureDexFolder: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Incremental
    @get:InputFiles
    abstract val assets: DirectoryProperty

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    @get:InputFile
    abstract val dependencyDataFile: RegularFileProperty

    @get:Input
    abstract val createdBy: Property<String>

    @get:Input
    var jniDebugBuild = false

    @get:Nested
    internal var signingConfigData: SigningConfigDataProvider? = null

    @get:Input
    abstract val aaptOptionsNoCompress: ListProperty<String>

    @get:Input
    abstract val jniLibsUseLegacyPackaging: Property<Boolean>

    @get:Input
    abstract val dexUseLegacyPackaging: Property<Boolean>

    @get:Optional
    @get:Input
    var buildTargetAbi: String? = null
        protected set

    @get:Input
    abstract val projectBaseName: Property<String>

    @get:Input
    abstract val debugBuild: Property<Boolean>

    @get:Input
    abstract val deterministicEntryOrder: Property<Boolean>

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val allInputFilesWithRelativePathSensitivity: ConfigurableFileCollection

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    abstract val allInputFilesWithNameOnlyPathSensitivity: ConfigurableFileCollection

    @get:Optional
    @get:Classpath
    abstract val allClasspathInputFiles: ConfigurableFileCollection

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    abstract val signingConfigVersions: ConfigurableFileCollection

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    val nativeLibrariesAndDexPackagingModeNames: List<String>
        /*
     * We don't really use this. But this forces a full build if the native libraries or dex
     * packaging mode changes.
     */ get() {
            val listBuilder = mutableListOf<String>()
            manifests
                    .get()
                    .asFileTree
                    .files
                    .forEach(
                            Consumer { manifest: File ->
                                if (manifest.isFile
                                        && (manifest.name
                                                == SdkConstants.ANDROID_MANIFEST_XML)) {
                                    val parser: ManifestAttributeSupplier = DefaultManifestParser(manifest, { true }, true, null)
                                    val nativeLibsPackagingMode = PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                                            parser.extractNativeLibs)
                                            .toString()
                                    listBuilder.add(nativeLibsPackagingMode)
                                    val dexPackagingMode = PackagingUtils
                                            .getDexPackagingMode(
                                                    parser.useEmbeddedDex,
                                                    dexUseLegacyPackaging.get())
                                            .toString()
                                    listBuilder.add(dexPackagingMode)
                                }
                            })
            return listBuilder.toList()
        }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val ideModelOutputFile: RegularFileProperty

    @get:Nested
    abstract val outputsHandler: Property<MultiOutputHandler>

    @Input
    abstract fun getTransformationRequest(): ArtifactTransformationRequest<PackageAndroidArtifact>

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Incremental
    @get:InputFiles
    abstract val versionControlInfoFile: RegularFileProperty

    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFiles
    @get:Incremental
    abstract val privacySandboxRuntimeEnabledSdkTable: RegularFileProperty?

    @get:Input
    abstract val pageSize: Property<Long>

    override fun doTaskAction(inputChanges: InputChanges) {
        if (!inputChanges.isIncremental) {
            checkFileNameUniqueness()
        }
        val changedResourceFiles = HashSet<File>()
        for (fileChange in inputChanges.getFileChanges(resourceFiles)) {
            if (fileChange.fileType == FileType.FILE) {
                changedResourceFiles.add(fileChange.file)
            }
        }
        getTransformationRequest()
                .submit(
                        this,
                        workerExecutor.noIsolation(),
                        IncrementalSplitterRunnable::class.java,
                        configure(changedResourceFiles, inputChanges))
    }

    private fun configure(
            changedResourceFiles: HashSet<File>, changes: InputChanges): (BuiltArtifact, Directory, SplitterParams) -> File {
        val outputsHandler = outputsHandler.get()
        return { builtArtifact: BuiltArtifact, _: Directory?, parameter: SplitterParams ->
            val variantOutput = outputsHandler.getOutput(
                    (builtArtifact as BuiltArtifactImpl).variantOutputConfiguration)
            parameter.variantOutput.set(variantOutput)
            parameter.outputHandler.set(outputsHandler.toSerializable())
            val outputFile = outputsHandler.computeBuildOutputFile(
                    outputDirectory.get().asFile, variantOutput!!)
            parameter.incrementalDirForSplit
                    .set(
                            outputsHandler.computeUniqueDirForSplit(
                                    incrementalFolder.get().asFile,
                                    variantOutput,
                                    variantName))
            parameter.androidResourcesFile.set(File(builtArtifact.outputFile))
            parameter.androidResourcesChanged
                    .set(changedResourceFiles.contains(File(builtArtifact.outputFile)))
            parameter.getProjectPath().set(projectPath.get())
            parameter.outputFile.set(outputFile)
            parameter.incrementalFolder.set(incrementalFolder)
            if (featureDexFolder.isEmpty) {
                parameter.dexFiles
                        .set(
                                changes.getChangesInSerializableForm(dexFolders))
                parameter.javaResourceFiles
                        .set(
                                changes.getChangesInSerializableForm(javaResourceFiles))
            } else {
                // We reach this code if we're in a dynamic-feature module and code shrinking is
                // enabled in the base module. In this case, we want to use the feature dex files
                // (and the feature java resource jar if using R8) published from the base.
                parameter.dexFiles
                        .set(
                                changes.getChangesInSerializableForm(featureDexFolder))
                parameter.javaResourceFiles
                        .set(
                                changes.getChangesInSerializableForm(featureJavaResourceFiles))
            }
            parameter.assetsFiles
                    .set(
                            changes.getChangesInSerializableForm(assets))
            parameter.jniFiles
                    .set(
                            changes.getChangesInSerializableForm(jniFolders))
            if (appMetadata.isPresent) {
                parameter.appMetadataFiles
                        .set(
                                changes.getChangesInSerializableForm(appMetadata))
            } else {
                parameter.appMetadataFiles
                        .set(SerializableInputChanges(listOf(), setOf()))
            }
            if (mergedArtProfile.isPresent
                    && mergedArtProfile.get().asFile.exists()) {
                parameter.mergedArtProfile
                        .set(
                                changes.getChangesInSerializableForm(mergedArtProfile))
            } else {
                parameter.mergedArtProfile
                        .set(SerializableInputChanges(listOf(), listOf()))
            }
            if (mergedArtProfileMetadata.isPresent
                    && mergedArtProfileMetadata.get().asFile.exists()) {
                parameter.mergedArtProfileMetadata
                        .set(
                                changes.getChangesInSerializableForm(mergedArtProfileMetadata))
            } else {
                parameter.mergedArtProfileMetadata
                        .set(SerializableInputChanges(listOf(), listOf()))
            }
            if (versionControlInfoFile.isPresent
                    && versionControlInfoFile.get().asFile.exists()) {
                parameter.versionControlInfoFile
                        .set(
                                changes.getChangesInSerializableForm(versionControlInfoFile))
            } else {
                parameter.versionControlInfoFile
                        .set(SerializableInputChanges(listOf(), listOf()))
            }
            parameter.getManifestType().set(manifestType)
            parameter.getSigningConfigData().set(signingConfigData!!.convertToParams())
            parameter.signingConfigVersionsFile
                    .set(signingConfigVersions.singleFile)
            if (baseModuleMetadata.isEmpty) {
                parameter.abiFilters.set(abiFilters)
            } else {
                // Dynamic feature
                val appAbiFilters: List<String?> = try {
                    load(baseModuleMetadata.singleFile)
                            .abiFilters
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                if (appAbiFilters.isEmpty()) {
                    // No ABI Filters were applied from the base application, but we still want
                    // to respect injected filters from studio, so use the task field (rather than
                    // just empty list)
                    parameter.abiFilters.set(abiFilters)
                } else {
                    // Respect the build author's explicit choice, even in the presence of injected
                    // ABI information from Studio
                    parameter.abiFilters.set(appAbiFilters)
                }
            }
            parameter.jniFolders.set(jniFolders.files)
            parameter.manifestDirectory.set(manifests)
            parameter.aaptOptionsNoCompress.set(aaptOptionsNoCompress.get())
            parameter.jniLibsUseLegacyPackaging.set(jniLibsUseLegacyPackaging.get())
            parameter.dexUseLegacyPackaging.set(dexUseLegacyPackaging.get())
            parameter.createdBy.set(createdBy.get())
            parameter.minSdkVersion.set(minSdkVersion.get())
            parameter.debuggableBuild.set(debugBuild.get())
            parameter.deterministicEntryOrder.set(deterministicEntryOrder.get())
            parameter.jniDebuggableBuild.set(jniDebugBuild)
            parameter.dependencyDataFile.set(dependencyDataFile)
            parameter.packagerMode
                    .set(
                            if (changes.isIncremental) IncrementalPackagerBuilder.BuildType.INCREMENTAL else IncrementalPackagerBuilder.BuildType.CLEAN)
            parameter.pageSize.set(pageSize)
            outputFile
        }
    }

    private fun checkFileNameUniqueness() {
        checkFileNameUniqueness(BuiltArtifactsLoaderImpl().load(resourceFiles.get()))
    }

    abstract class SplitterParams : DecoratedWorkParameters {
        abstract val variantOutput: Property<VariantOutputImpl.SerializedForm>
        abstract val outputHandler: Property<MultiOutputHandler>
        abstract fun getProjectPath(): Property<String?>
        abstract val androidResourcesFile: RegularFileProperty
        abstract val androidResourcesChanged: Property<Boolean>
        abstract val outputFile: RegularFileProperty
        abstract val incrementalFolder: DirectoryProperty
        abstract val incrementalDirForSplit: DirectoryProperty
        abstract val dexFiles: Property<SerializableInputChanges>
        abstract val assetsFiles: Property<SerializableInputChanges>
        abstract val jniFiles: Property<SerializableInputChanges>
        abstract val javaResourceFiles: Property<SerializableInputChanges>
        abstract val appMetadataFiles: Property<SerializableInputChanges>
        abstract fun getManifestType(): Property<Artifact<Directory>>
        @Optional
        abstract fun getSigningConfigData(): Property<SigningConfigProviderParams>
        abstract val signingConfigVersionsFile: RegularFileProperty
        abstract val abiFilters: SetProperty<String>
        abstract val jniFolders: ListProperty<File>
        abstract val manifestDirectory: DirectoryProperty
        abstract val aaptOptionsNoCompress: ListProperty<String>
        abstract val jniLibsUseLegacyPackaging: Property<Boolean>
        abstract val dexUseLegacyPackaging: Property<Boolean>

        @get:Optional
        abstract val createdBy: Property<String>
        abstract val minSdkVersion: Property<Int>
        abstract val debuggableBuild: Property<Boolean>
        abstract val deterministicEntryOrder: Property<Boolean>
        abstract val jniDebuggableBuild: Property<Boolean>
        abstract val packagerMode: Property<IncrementalPackagerBuilder.BuildType>

        @get:Optional
        abstract val dependencyDataFile: RegularFileProperty

        @get:Optional
        abstract val mergedArtProfile: Property<SerializableInputChanges>

        @get:Optional
        abstract val mergedArtProfileMetadata: Property<SerializableInputChanges>

        @get:Optional
        abstract val versionControlInfoFile: Property<SerializableInputChanges>

        abstract val pageSize: Property<Long>
    }

    abstract class IncrementalSplitterRunnable: WorkActionAdapter<SplitterParams> {
        override fun doExecute() {
            val params = parameters
            try {
                val incrementalDirForSplit = params.incrementalDirForSplit.asFile.get()
                val cacheDir = File(incrementalDirForSplit, ZIP_DIFF_CACHE_DIR)
                if (!cacheDir.exists()) {
                    FileUtils.mkdirs(cacheDir)
                }

                // Build a file cache that uses the indexes in the roots.
                // This is to work nicely with classpath sensitivity
                // Mutable as we need to add to it for the zip64 workaround in getChangedJavaResources
                val cacheKeyMap: MutableMap<File, String?> = HashMap()
                addCacheKeys(cacheKeyMap, "dex", params.dexFiles.get())
                addCacheKeys(cacheKeyMap, "javaResources", params.javaResourceFiles.get())
                addCacheKeys(cacheKeyMap, "assets", params.assetsFiles.get())
                cacheKeyMap[params.androidResourcesFile.get().asFile] = "androidResources"
                addCacheKeys(cacheKeyMap, "jniLibs", params.jniFiles.get())
                val cache = KeyedFileCache(
                        cacheDir) { file: File -> Objects.requireNonNull(cacheKeyMap[file]) }
                val cacheUpdates = mutableSetOf<Runnable>()
                val changedDexFiles = classpathToRelativeFileSet(
                        params.dexFiles.get(), cache, cacheUpdates)
                val changedJavaResources = getChangedJavaResources(params, cacheKeyMap, cache, cacheUpdates)
                val changedAndroidResources: Map<RelativeFile, FileStatus> = if (params.androidResourcesChanged.get()) {
                    IncrementalRelativeFileSets.fromZip(
                            ZipCentralDirectory(
                                    params.androidResourcesFile.get().asFile),
                            cache,
                            cacheUpdates)
                } else {
                    mapOf()
                }
                val changedJniLibs = classpathToRelativeFileSet(
                        params.jniFiles.get(), cache, cacheUpdates)
                val manifestOutputs = BuiltArtifactsLoaderImpl().load(params.manifestDirectory)
                doTask(
                        incrementalDirForSplit,
                        params.outputFile.get().asFile,
                        cache,
                        manifestOutputs!!,
                        changedDexFiles,
                        changedJavaResources,
                        params.assetsFiles.get().changes,
                        changedAndroidResources,
                        changedJniLibs,
                        params.appMetadataFiles.get().changes,
                        params.mergedArtProfile.get().changes,
                        params.mergedArtProfileMetadata.get().changes,
                        params.versionControlInfoFile.get().changes,
                        params)

                /*
                 * Update the cache
                 */cacheUpdates.forEach(Consumer { obj: Runnable -> obj.run() })
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            } finally {
                if (params!!.packagerMode.get() == IncrementalPackagerBuilder.BuildType.CLEAN) {
                    recordMetrics(
                            params.getProjectPath().get(),
                            params.outputFile.get().asFile,
                            params.androidResourcesFile.get().asFile,
                            params.analyticsService.get())
                }
            }
        }

        companion object {
            private fun addCacheKeys(
                    builder: MutableMap<File, String?>, prefix: String, changes: SerializableInputChanges) {
                val roots = changes.roots
                for (i in roots.indices) {
                    builder[roots[i]] = prefix + i
                }
            }

            /**
             * An adapted version of [classpathToRelativeFileSet]
             * that handles zip64 support within this task.
             */
            @Throws(IOException::class)
            private fun getChangedJavaResources(
                    params: SplitterParams?,
                    cacheKeyMap: MutableMap<File, String?>,
                    cache: KeyedFileCache,
                    cacheUpdates: MutableSet<Runnable>): Map<RelativeFile, FileStatus> {
                val changedJavaResources = mutableMapOf<RelativeFile, FileStatus>()
                for (change in params!!.javaResourceFiles.get().changes) {
                    if (change.normalizedPath.isEmpty()) {
                        try {
                            changedJavaResources.addZipChanges(change.file, cache, cacheUpdates)
                        } catch (e: Zip64NotSupportedException) {
                            val nonZip64 = copyJavaResourcesOnly(
                                    params.incrementalFolder.get().asFile,
                                    change.file)
                            // Map the copied file to the same cache key.
                            cacheKeyMap[nonZip64] = cacheKeyMap[change.file]
                            changedJavaResources.addZipChanges(nonZip64, cache, cacheUpdates)
                        }
                    } else {
                        changedJavaResources.addFileChange(change)
                    }
                }
                return Collections.unmodifiableMap(changedJavaResources)
            }
        }
    }

    // ----- CreationAction -----
    abstract class CreationAction<TaskT : PackageAndroidArtifact>(
            creationConfig: ApkCreationConfig,
            protected val manifests: Provider<Directory>,
            private val manifestType: Artifact<Directory>) : VariantTaskCreationAction<TaskT, ApkCreationConfig>(creationConfig) {
        override fun configure(packageAndroidArtifact: TaskT) {
            super.configure(packageAndroidArtifact)
            packageAndroidArtifact.minSdkVersion
                    .set(
                            packageAndroidArtifact
                                    .project
                                    .provider { creationConfig.minSdk.apiLevel })
            packageAndroidArtifact.minSdkVersion.disallowChanges()
            packageAndroidArtifact.applicationId.set(creationConfig.applicationId)
            packageAndroidArtifact.applicationId.disallowChanges()
            packageAndroidArtifact.outputsHandler.set(
                    MultiOutputHandler.create(creationConfig)
            )
            packageAndroidArtifact.outputsHandler.disallowChanges()
            packageAndroidArtifact.incrementalFolder
                    .set(
                            File(
                                    creationConfig
                                            .paths
                                            .getIncrementalDir(packageAndroidArtifact.name),
                                    "tmp"))
            if (creationConfig.androidResourcesCreationConfig != null) {
                packageAndroidArtifact.aaptOptionsNoCompress
                        .set(
                                creationConfig
                                        .androidResources
                                        .noCompress)
            } else {
                packageAndroidArtifact.aaptOptionsNoCompress.set(emptySet<String>())
            }
            packageAndroidArtifact.aaptOptionsNoCompress.disallowChanges()
            packageAndroidArtifact.jniLibsUseLegacyPackaging
                    .set(creationConfig.packaging.jniLibs.useLegacyPackaging)
            packageAndroidArtifact.jniLibsUseLegacyPackaging.disallowChanges()
            packageAndroidArtifact.dexUseLegacyPackaging
                    .set(creationConfig.packaging.dex.useLegacyPackaging)
            packageAndroidArtifact.dexUseLegacyPackaging.disallowChanges()
            packageAndroidArtifact.manifests.set(manifests)
            packageAndroidArtifact.dexFolders.from(getDexFolders(creationConfig))
            val projectPath = packageAndroidArtifact.project.path
            val featureDexFolder = getFeatureDexFolder(creationConfig, projectPath)
            if (featureDexFolder != null) {
                packageAndroidArtifact.featureDexFolder.from(featureDexFolder)
            }
            packageAndroidArtifact.javaResourceFiles.from(
                    creationConfig.artifacts.get(InternalArtifactType.MERGED_JAVA_RES))
            packageAndroidArtifact.javaResourceFiles.disallowChanges()
            val featureJavaResources = getFeatureJavaResources(creationConfig, projectPath)
            if (featureJavaResources != null) {
                packageAndroidArtifact.featureJavaResourceFiles.from(featureJavaResources)
            }
            packageAndroidArtifact.featureJavaResourceFiles.disallowChanges()
            val projectOptions = creationConfig.services.projectOptions
            packageAndroidArtifact.deterministicEntryOrder
                    .set(isDeterministicEntryOrder(creationConfig))
            packageAndroidArtifact.deterministicEntryOrder.disallowChanges()
            if (creationConfig is ApplicationCreationConfig) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                        InternalArtifactType.APP_METADATA,
                        packageAndroidArtifact.appMetadata)
                var vcsTaskRan = false
                if (creationConfig.includeVcsInfo == null) {
                    if (!creationConfig.debuggable) {
                        vcsTaskRan = true
                    }
                } else {
                    if (creationConfig.includeVcsInfo!!) {
                        vcsTaskRan = true
                    }
                }
                if (vcsTaskRan) {
                    creationConfig
                            .artifacts
                            .setTaskInputToFinalProduct(
                                    VERSION_CONTROL_INFO_FILE,
                                    packageAndroidArtifact.versionControlInfoFile)
                }
                if (isDeterministic(creationConfig)) {
                    packageAndroidArtifact.allInputFilesWithNameOnlyPathSensitivity
                            .from(packageAndroidArtifact.appMetadata)
                    if (vcsTaskRan) {
                        packageAndroidArtifact.allInputFilesWithNameOnlyPathSensitivity
                                .from(packageAndroidArtifact.versionControlInfoFile)
                    }
                }
                if (!creationConfig.debuggable) {
                    creationConfig
                            .artifacts
                            .setTaskInputToFinalProduct(
                                    BINARY_ART_PROFILE,
                                    packageAndroidArtifact.mergedArtProfile)
                    if (isDeterministic(creationConfig)) {
                        packageAndroidArtifact.allInputFilesWithRelativePathSensitivity
                                .from(packageAndroidArtifact.mergedArtProfile)
                    }
                    creationConfig
                            .artifacts
                            .setTaskInputToFinalProduct(
                                    BINARY_ART_PROFILE_METADATA,
                                    packageAndroidArtifact.mergedArtProfileMetadata)
                    if (isDeterministic(creationConfig)) {
                        packageAndroidArtifact.allInputFilesWithRelativePathSensitivity
                                .from(packageAndroidArtifact.mergedArtProfileMetadata)
                    }
                }
            }
            packageAndroidArtifact.assets
                    .set(creationConfig.artifacts.get(COMPRESSED_ASSETS))
            val isJniDebuggable: Boolean = if (creationConfig.nativeBuildCreationConfig != null) {
                creationConfig.nativeBuildCreationConfig!!.isJniDebuggable
            } else {
                false
            }
            packageAndroidArtifact.jniDebugBuild = isJniDebuggable
            packageAndroidArtifact.debugBuild.set(creationConfig.debuggable)
            packageAndroidArtifact.debugBuild.disallowChanges()
            packageAndroidArtifact.projectBaseName
                    .set(creationConfig.services.projectInfo.getProjectBaseName())
            packageAndroidArtifact.projectBaseName.disallowChanges()
            packageAndroidArtifact.manifestType = manifestType
            if (creationConfig is KmpComponentCreationConfig) {
                packageAndroidArtifact.buildTargetAbi = null
            } else {
                packageAndroidArtifact.buildTargetAbi = if (creationConfig.global.splits.abi.isEnable) projectOptions[StringOption.IDE_BUILD_TARGET_ABI] else null
            }
            if (creationConfig.componentType.isDynamicFeature) {
                packageAndroidArtifact.baseModuleMetadata
                        .from(
                                creationConfig
                                        .variantDependencies
                                        .getArtifactFileCollection(ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.BASE_MODULE_METADATA))
            }
            packageAndroidArtifact.baseModuleMetadata.disallowChanges()
            val supportedAbis: Set<String> = if (creationConfig.nativeBuildCreationConfig != null) {
                creationConfig.nativeBuildCreationConfig!!.supportedAbis
            } else {
                emptySet()
            }
            if (supportedAbis.isNotEmpty()) {
                // If the build author has set the supported Abis that is respected
                packageAndroidArtifact.abiFilters.set(supportedAbis)
            } else {
                // Otherwise, use the injected Abis if set.
                packageAndroidArtifact.abiFilters
                        .set(
                                if (projectOptions[BooleanOption.BUILD_ONLY_TARGET_ABI]) firstValidInjectedAbi(
                                        projectOptions[StringOption.IDE_BUILD_TARGET_ABI]) else setOf())
            }
            packageAndroidArtifact.abiFilters.disallowChanges()
            packageAndroidArtifact.createdBy.set(creationConfig.global.createdBy)
            if (creationConfig.componentType.isBaseModule
                    && creationConfig
                            .services
                            .projectOptions[BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS]) {
                creationConfig
                        .artifacts
                        .setTaskInputToFinalProduct(
                                SDK_DEPENDENCY_DATA,
                                packageAndroidArtifact.dependencyDataFile)
            }

            // If we're in a dynamic feature, we use FEATURE_SIGNING_CONFIG_VERSIONS, published from
            // the base. Otherwise, we use the SIGNING_CONFIG_VERSIONS internal artifact.
            if (creationConfig.componentType.isDynamicFeature
                    || (creationConfig is TestComponentCreationConfig
                            && (creationConfig as TestComponentCreationConfig)
                            .mainVariant
                            .componentType
                            .isDynamicFeature)) {
                packageAndroidArtifact.signingConfigVersions
                        .from(
                                creationConfig
                                        .variantDependencies
                                        .getArtifactFileCollection(ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.FEATURE_SIGNING_CONFIG_VERSIONS))
            } else {
                packageAndroidArtifact.signingConfigVersions
                        .from(
                                creationConfig
                                        .artifacts
                                        .get(SIGNING_CONFIG_VERSIONS))
            }
            packageAndroidArtifact.signingConfigVersions.disallowChanges()
            finalConfigure(packageAndroidArtifact)

            // To produce a deterministic APK, we must force the task to run non-incrementally by
            // adding all of the incremental file inputs again as non-incremental inputs. This is a
            // workaround for https://github.com/gradle/gradle/issues/16976.
            if (isDeterministic(creationConfig)) {
                packageAndroidArtifact.allInputFilesWithRelativePathSensitivity
                        .from(
                                packageAndroidArtifact.assets,
                                packageAndroidArtifact.dexFolders,
                                packageAndroidArtifact.featureDexFolder,
                                packageAndroidArtifact.manifests,
                                packageAndroidArtifact.resourceFiles)
                packageAndroidArtifact.allClasspathInputFiles
                        .from(
                                packageAndroidArtifact.featureJavaResourceFiles,
                                packageAndroidArtifact.javaResourceFiles,
                                packageAndroidArtifact.jniFolders)
            }
            packageAndroidArtifact.allInputFilesWithRelativePathSensitivity.disallowChanges()
            packageAndroidArtifact.allInputFilesWithNameOnlyPathSensitivity.disallowChanges()
            packageAndroidArtifact.allClasspathInputFiles.disallowChanges()
            packageAndroidArtifact.pageSize.setDisallowChanges(getPageSize(creationConfig))
        }

        protected open fun finalConfigure(task: TaskT) {
            task.jniFolders
                    .from(creationConfig.artifacts.get(STRIPPED_NATIVE_LIBS))
            task.signingConfigData = SigningConfigDataProvider.create(creationConfig)
        }

        private fun getFeatureJavaResources(
                creationConfig: ApkCreationConfig, projectPath: String): FileCollection? {
            return if (!creationConfig.componentType.isDynamicFeature) {
                null
            } else creationConfig
                    .variantDependencies
                    .getArtifactFileCollection(
                            ConsumedConfigType.RUNTIME_CLASSPATH, ArtifactScope.PROJECT,
                            AndroidArtifacts.ArtifactType.FEATURE_SHRUNK_JAVA_RES,
                            AndroidAttributes(Pair(AndroidArtifacts.MODULE_PATH, projectPath)))
        }

        companion object {
            fun getDexFolders(creationConfig: ApkCreationConfig): FileCollection {
                val artifacts = creationConfig.artifacts
                return if (creationConfig is ApplicationCreationConfig
                        && creationConfig.consumesFeatureJars) {
                    creationConfig
                            .services
                            .fileCollection(
                                    artifacts.get(InternalArtifactType.BASE_DEX),
                                    getDesugarLibDexIfExists(creationConfig),
                                    getGlobalSyntheticsDex(creationConfig))
                } else {
                    creationConfig
                            .services
                            .fileCollection(
                                    artifacts.getAll(InternalMultipleArtifactType.DEX),
                                    getDesugarLibDexIfExists(creationConfig),
                                    getGlobalSyntheticsDex(creationConfig))
                }
            }

            fun getFeatureDexFolder(
                    creationConfig: ApkCreationConfig, projectPath: String): FileCollection? {
                return if (!creationConfig.componentType.isDynamicFeature) {
                    null
                } else creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(
                                ConsumedConfigType.RUNTIME_CLASSPATH, ArtifactScope.PROJECT,
                                AndroidArtifacts.ArtifactType.FEATURE_DEX,
                                AndroidAttributes(Pair(AndroidArtifacts.MODULE_PATH, projectPath)))
            }

            private fun firstValidInjectedAbi(abis: String?): Set<String> {
                if (abis == null) {
                    return setOf()
                }
                val allowedAbis = Arrays.stream(Abi.values()).map { obj: Abi -> obj.tag }.collect(Collectors.toSet())
                val firstValidAbi = Arrays.stream(abis.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                        .map { obj: String -> obj.trim { it <= ' ' } }
                        .filter { o: String -> allowedAbis.contains(o) }
                        .findFirst()
                return firstValidAbi.map<Set<String>> {
                    element -> setOf(element)
                }.orElseGet { setOf() }
            }

            private fun getDesugarLibDexIfExists(
                    creationConfig: ApkCreationConfig): FileCollection {
                return if (!creationConfig.dexing.shouldPackageDesugarLibDex) {
                    creationConfig.services.fileCollection()
                } else creationConfig
                        .services
                        .fileCollection(
                                creationConfig
                                        .artifacts
                                        .get(InternalArtifactType.DESUGAR_LIB_DEX))
            }

            private fun getGlobalSyntheticsDex(
                    creationConfig: ApkCreationConfig): FileCollection {
                // No need to collect global synthetics in three cases:
                //   1. Global synthetics generation is disabled
                //   2. R8 is used and global synthetics are not generated
                //   3. In mono dex and legacy multidex where global synthetics are already merged into
                //      dex files in dex merging tasks
                return if (!creationConfig.enableGlobalSynthetics
                        || (creationConfig.dexing.dexingType != DexingType.NATIVE_MULTIDEX)
                        || creationConfig.optimizationCreationConfig.minifiedEnabled) {
                    creationConfig.services.fileCollection()
                } else creationConfig
                        .services
                        .fileCollection(
                                creationConfig
                                        .artifacts
                                        .get(InternalArtifactType.GLOBAL_SYNTHETICS_DEX))
            }

            // We always write new APK entries in a deterministic order except for debug builds invoked
            // from the IDE. Writing new APK entries in a deterministic order will produce deterministic
            // APKs for clean builds, but not incremental builds.
            private fun isDeterministicEntryOrder(creationConfig: ApkCreationConfig): Boolean {
                val projectOptions = creationConfig.services.projectOptions
                return (isDeterministic(creationConfig)
                        || !projectOptions[BooleanOption.IDE_INVOKED_FROM_IDE])
            }

            // We produce deterministic APKs for non-debuggable builds or when
            // android.experimental.forceDeterministicApk is true
            private fun isDeterministic(creationConfig: ApkCreationConfig): Boolean {
                val projectOptions = creationConfig.services.projectOptions
                return (!creationConfig.debuggable
                        || projectOptions[BooleanOption.FORCE_DETERMINISTIC_APK])
            }
        }
    }

    companion object {
        /**
         * Name of directory, inside the intermediate directory, where zip caches are kept.
         */
        private const val ZIP_DIFF_CACHE_DIR = "zip-cache"
        private const val ZIP_64_COPY_DIR = "zip64-copy"
        @JvmStatic
        @VisibleForTesting
        fun checkFileNameUniqueness(builtArtifacts: BuiltArtifactsImpl?) {
            if (builtArtifacts == null) return
            val fileOutputs: Collection<File?> = builtArtifacts.elements.stream()
                    .map { (outputFile): BuiltArtifactImpl -> File(outputFile) }
                    .collect(Collectors.toList())
            val repeatingFileNameOptional = fileOutputs
                    .stream()
                    .filter { fileOutput: File? -> Collections.frequency(fileOutputs, fileOutput) > 1 }
                    .map { obj: File? -> obj!!.name }
                    .findFirst()
            if (repeatingFileNameOptional.isPresent) {
                val repeatingFileName = repeatingFileNameOptional.get()
                val conflictingApks = builtArtifacts.elements.stream()
                        .filter { (outputFile): BuiltArtifactImpl ->
                            (File(outputFile)
                                    .name
                                    == repeatingFileName)
                        }
                        .map<String> { buildOutput: BuiltArtifactImpl ->
                            if (buildOutput.filters.isEmpty()) {
                                return@map buildOutput.outputType.toString()
                            } else {
                                return@map Joiner.on("-").join(buildOutput.filters)
                            }
                        }
                        .collect(Collectors.toList<String>())
                throw RuntimeException(String.format("Several variant outputs are configured to use "
                        + "the same file name \"%1\$s\", filters : %2\$s",
                        repeatingFileName, Joiner.on(":").join(conflictingApks)))
            }
        }

        /**
         * Copy the input zip file (probably a Zip64) content into a new Zip in the destination folder
         * stripping out all .class files.
         *
         * @param destinationFolder the destination folder to use, the output jar will have the same
         * name as the input zip file.
         * @param zip64File the input zip file.
         * @return the path to the stripped Zip file.
         * @throws IOException if the copying failed.
         */
        @JvmStatic
        @VisibleForTesting
        @Throws(IOException::class)
        fun copyJavaResourcesOnly(destinationFolder: File?, zip64File: File): File {
            val cacheDir = File(destinationFolder, ZIP_64_COPY_DIR)
            val copiedZip = File(cacheDir, zip64File.name)
            FileUtils.mkdirs(copiedZip.parentFile)
            ZipFile(zip64File).use { inFile ->
                ZipOutputStream(
                        BufferedOutputStream(FileOutputStream(copiedZip))).use { outFile ->
                    val entries = inFile.entries()
                    while (entries.hasMoreElements()) {
                        val zipEntry = entries.nextElement()
                        if (!zipEntry.name.endsWith(SdkConstants.DOT_CLASS)
                                && isValidZipEntryName(zipEntry)) {
                            outFile.putNextEntry(ZipEntry(zipEntry.name))
                            try {
                                ByteStreams.copy(
                                        BufferedInputStream(inFile.getInputStream(zipEntry)), outFile)
                            } finally {
                                outFile.closeEntry()
                            }
                        }
                    }
                }
            }
            return copiedZip
        }

        /**
         * Packages the application incrementally.
         *
         * @param outputFile expected output package file
         * @param changedDex incremental dex packaging data
         * @param changedJavaResources incremental java resources
         * @param changedAssets incremental assets
         * @param changedAndroidResources incremental Android resource
         * @param changedNLibs incremental native libraries changed
         * @param changedAppMetadata incremental app metadata
         * @param changedVersionControlInfo incremental version control info
         * @throws IOException failed to package the APK
         */
        @Throws(IOException::class)
        private fun doTask(
                incrementalDirForSplit: File,
                outputFile: File,
                cache: KeyedFileCache,
                manifestOutputs: BuiltArtifactsImpl,
                changedDex: Map<RelativeFile, FileStatus>,
                changedJavaResources: Map<RelativeFile, FileStatus>,
                changedAssets: Collection<SerializableChange>,
                changedAndroidResources: Map<RelativeFile, FileStatus>,
                changedNLibs: Map<RelativeFile, FileStatus>,
                changedAppMetadata: Collection<SerializableChange>,
                artProfile: Collection<SerializableChange>,
                artProfileMetadata: Collection<SerializableChange>,
                changedVersionControlInfo: Collection<SerializableChange>,
                params: SplitterParams) {
            val javaResourcesForApk = mutableMapOf<RelativeFile, FileStatus>()
            javaResourcesForApk.putAll(changedJavaResources)
            val (outputFile1) = params.outputHandler.get().extractArtifactForSplit(
                    manifestOutputs,
                    params.variantOutput.get().variantOutputConfiguration
            )
                    ?: throw RuntimeException(
                            "Found a .ap_ for split "
                                    + params.variantOutput.get()
                                    + " but no "
                                    + params.getManifestType().get()
                                    + " associated manifest file")
            FileUtils.mkdirs(outputFile.parentFile)

            // In execution phase, so can parse the manifest.
            val manifestData = parseManifest(
                    File(outputFile1).readText(UTF_8),
                    outputFile1,
                    true,
                    null,
                    MANIFEST_DATA_ISSUE_REPORTER)
            val nativeLibsPackagingMode = PackagingUtils.getNativeLibrariesLibrariesPackagingMode(
                    manifestData.extractNativeLibs)
            // Warn if params.getJniLibsUseLegacyPackaging() is not compatible with
            // nativeLibsPackagingMode. We currently fall back to what's specified in the manifest, but
            // in future versions of AGP, we should use what's specified via
            // params.getJniLibsUseLegacyPackaging().
            val logger = LoggerWrapper(Logging.getLogger(PackageAndroidArtifact::class.java))
            if (params.jniLibsUseLegacyPackaging.get()) {
                // TODO (b/149770867) make this an error in future AGP versions.
                if (nativeLibsPackagingMode == NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED) {
                    logger.warning(
                            "PackagingOptions.jniLibs.useLegacyPackaging should be set to false "
                                    + "because android:extractNativeLibs is set to \"false\" in "
                                    + "AndroidManifest.xml. Avoid setting "
                                    + "android:extractNativeLibs=\"false\" explicitly in "
                                    + "AndroidManifest.xml, and instead set "
                                    + "android.packagingOptions.jniLibs.useLegacyPackaging to false in "
                                    + "the build.gradle file.")
                }
            } else {
                if (nativeLibsPackagingMode == NativeLibrariesPackagingMode.COMPRESSED) {
                    logger.warning(
                            "PackagingOptions.jniLibs.useLegacyPackaging should be set to true "
                                    + "because android:extractNativeLibs is set to \"true\" in "
                                    + "AndroidManifest.xml.")
                }
            }
            val useEmbeddedDex = manifestData.useEmbeddedDex
            val dexPackagingMode = PackagingUtils.getDexPackagingMode(
                    useEmbeddedDex, params.dexUseLegacyPackaging.get())
            if (params.dexUseLegacyPackaging.get() && java.lang.Boolean.TRUE == useEmbeddedDex) {
                // TODO (b/149770867) make this an error in future AGP versions.
                logger.warning("PackagingOptions.dex.useLegacyPackaging should be set to false because "
                        + "android:useEmbeddedDex is set to \"true\" in AndroidManifest.xml.")
            }
            val dependencyData = if (params.dependencyDataFile.isPresent) Files.readAllBytes(
                    params.dependencyDataFile.get().asFile.toPath()) else null
            IncrementalPackagerBuilder(params.packagerMode.get())
                    .withOutputFile(outputFile)
                    .withSigning(
                            params.getSigningConfigData().get().resolve(),
                            loadSigningConfigVersions(
                                    params.signingConfigVersionsFile.get().asFile),
                            params.minSdkVersion.get(),
                            dependencyData)
                    .withCreatedBy(params.createdBy.get())
                    .withNativeLibraryPackagingMode(nativeLibsPackagingMode)
                    .withNoCompressPredicate(
                            PackagingUtils.getNoCompressPredicate(
                                    params.aaptOptionsNoCompress.get(),
                                    nativeLibsPackagingMode,
                                    dexPackagingMode))
                    .withIntermediateDir(incrementalDirForSplit)
                    .withDebuggableBuild(params.debuggableBuild.get())
                    .withDeterministicEntryOrder(params.deterministicEntryOrder.get())
                    .withAcceptedAbis(getAcceptedAbis(params))
                    .withJniDebuggableBuild(params.jniDebuggableBuild.get())
                    .withPageSize(params.pageSize.get())
                    .withChangedDexFiles(changedDex)
                    .withChangedJavaResources(changedJavaResources)
                    .withChangedAssets(changedAssets)
                    .withChangedAndroidResources(changedAndroidResources)
                    .withChangedNativeLibs(changedNLibs)
                    .withChangedAppMetadata(changedAppMetadata)
                    .withChangedArtProfile(artProfile)
                    .withChangedArtProfileMetadata(artProfileMetadata)
                    .withChangedVersionControlInfo(changedVersionControlInfo)
                    .build().use { packager -> packager.updateFiles() }
            /*
         * Save all used zips in the cache.
         */Stream.concat(
                    changedDex.keys.stream(),
                    Stream.concat(
                            changedJavaResources.keys.stream(),
                            Stream.concat(
                                    changedAndroidResources.keys.stream(),
                                    changedNLibs.keys.stream())))
                    .filter { it: RelativeFile -> it.getType() == RelativeFile.Type.JAR }
                    .map { obj: RelativeFile -> obj.base }
                    .distinct()
                    .forEach { f: File? ->
                        try {
                            cache.add(f!!)
                        } catch (e: IOException) {
                            throw IOExceptionWrapper(e)
                        }
                    }
        }

        private val MANIFEST_DATA_ISSUE_REPORTER: IssueReporter = object : IssueReporter() {
            override fun reportIssue(
                    type: Type,
                    severity: Severity,
                    exception: EvalIssueException) {
                if (severity === Severity.ERROR) {
                    throw exception
                }
            }

            override fun hasIssue(type: Type): Boolean {
                return false
            }
        }

        /**
         * Calculates the accepted ABIs based on the given [SplitterParams]. Also checks that the
         * accepted ABIs are all available, and logs a warning if not.
         *
         * @param params the [SplitterParams]
         */
        private fun getAcceptedAbis(params: SplitterParams): Set<String> {
            val splitAbiFilter = params.variantOutput.get().variantOutputConfiguration.getFilter(FilterConfiguration.FilterType.ABI)
            val acceptedAbis: Set<String> = if (splitAbiFilter != null) setOf(splitAbiFilter.identifier) else params.abiFilters.get().toSet()

            // After calculating acceptedAbis, we calculate availableAbis, which is the set of ABIs
            // present in params.jniFolders.
            val availableAbis: MutableSet<String?> = HashSet()
            for (jniFolder in params.jniFolders.get()) {
                val libDirs = jniFolder.listFiles() ?: continue
                for (libDir in libDirs) {
                    val abiDirs = libDir.listFiles()
                    if ("lib" != libDir.name || abiDirs == null) {
                        continue
                    }
                    for (abiDir in abiDirs) {
                        val soFiles = abiDir.listFiles()
                        if (soFiles != null && soFiles.isNotEmpty()) {
                            availableAbis.add(abiDir.name)
                        }
                    }
                }
            }
            // if acceptedAbis and availableAbis both aren't empty, we make sure that the ABIs in
            // acceptedAbis are also in availableAbis, or else we log a warning.
            if (acceptedAbis.isNotEmpty() && availableAbis.isNotEmpty()) {
                val missingAbis: Set<String> = Sets.difference(acceptedAbis, availableAbis)
                if (missingAbis.isNotEmpty()) {
                    val logger = LoggerWrapper(Logging.getLogger(PackageAndroidArtifact::class.java))
                    logger.warning(String.format(
                            "There are no .so files available to package in the APK for %s.",
                            Joiner.on(", ")
                                    .join(
                                            missingAbis
                                                    .stream()
                                                    .sorted()
                                                    .collect(Collectors.toList()))))
                }
            }
            return acceptedAbis
        }

        private fun getPageSize(creationConfig: ApkCreationConfig): Provider<Long> {
            val experimentalProperties: MapProperty<String, Any>
            val defaultPageSize = ApkFlinger.PAGE_SIZE_16K
            experimentalProperties = when (creationConfig) {
                is VariantCreationConfig -> {
                    (creationConfig as VariantCreationConfig).experimentalProperties
                }
                is NestedComponentCreationConfig -> {
                    (creationConfig as NestedComponentCreationConfig)
                        .mainVariant
                        .experimentalProperties
                }
                else -> {
                    return creationConfig.services.provider { defaultPageSize }
                }
            }
            return experimentalProperties.map {
                val pageSize =
                    OptionalString.NATIVE_LIBRARY_PAGE_SIZE.getValue(it)
                        ?: return@map defaultPageSize
                val lowercasePageSize = pageSize.lowercase(Locale.US)
                when (lowercasePageSize) {
                    "4k" -> return@map ApkFlinger.PAGE_SIZE_4K
                    "16k" -> return@map ApkFlinger.PAGE_SIZE_16K
                    "64k" -> return@map ApkFlinger.PAGE_SIZE_64K
                    else -> throw java.lang.RuntimeException(
                        "Invalid value for ${OptionalString.NATIVE_LIBRARY_PAGE_SIZE.key}. " +
                            "Supported values are \"4k\", \"16k\", and \"64k\"."
                    )
                }
            }
        }
    }
}
