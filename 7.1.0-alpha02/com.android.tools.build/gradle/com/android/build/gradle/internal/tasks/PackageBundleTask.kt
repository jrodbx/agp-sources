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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.AssetPackBundleExtension
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.packaging.IncrementalPackager.APP_METADATA_FILE_NAME
import com.android.builder.packaging.PackagingUtils
import com.android.bundle.Config
import com.android.tools.build.bundletool.commands.BuildBundleCommand
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Path

/**
 * Task that generates the bundle (.aab) with all the modules.
 */
abstract class PackageBundleTask : NonIncrementalTask() {

    // Android Gradle plugin supports two kinds of bundle:
    // * Regular App Bundle. This bundle contains all configured modules and is built by application
    //   plugin. Regular bundles are used for local development and to upload a new version of the
    //   app to distribution channel.
    // * Asset-pack (Asset-only) App Bundle. This bundle contains only subset of asset-packs and is
    //   built by asset-pack-bundle plugin. This is special kind of bundle which can be used only
    //   with Google Play to update assets independently from the application code.
    @get:Input
    abstract val bundleType: Property<Config.BundleConfig.BundleType>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val baseModuleZip: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var featureZips: FileCollection
        private set

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val assetPackZips: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundleDeps: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val mainDexList: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val obsfuscationMappingFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val integrityConfigFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeDebugMetadataFiles: ConfigurableFileCollection

    @get:Input
    abstract val abiFilters: SetProperty<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val appMetadata: RegularFileProperty

    @get:Input
    abstract val aaptOptionsNoCompress: ListProperty<String>

    @get:Nested
    lateinit var bundleOptions: BundleOptions
        private set

    @get:Input
    abstract val compressNativeLibs: Property<Boolean>

    @get:Nested
    @get:Optional
    abstract val assetPackOptionsForAssetPackBundle: Property<AssetPackOptionsForAssetPackBundle>

    @get:OutputFile
    abstract val bundleFile: RegularFileProperty

    @get:Input
    val fileName: String
        get() = bundleFile.get().asFile.name

    @get:Input
    abstract val bundleNeedsFusedStandaloneConfig: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val binaryArtProfile: RegularFileProperty

    companion object {
        const val MIN_SDK_FOR_SPLITS = 21
    }

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.bundleType.set(bundleType)
            if (baseModuleZip.isPresent) {
                it.baseModuleFile.set(baseModuleZip.get().asFileTree.singleFile)
            }
            it.featureFiles.from(featureZips)
            if (assetPackZips.isPresent) {
                it.assetPackFiles.from(assetPackZips.get().asFileTree.files)
            }
            it.mainDexList.set(mainDexList)
            it.obfuscationMappingFile.set(obsfuscationMappingFile)
            it.integrityConfigFile.set(integrityConfigFile)
            it.nativeDebugMetadataFiles.from(nativeDebugMetadataFiles)
            it.aaptOptionsNoCompress.set(aaptOptionsNoCompress)
            it.bundleOptions.set(bundleOptions)
            it.compressNativeLibs.set(compressNativeLibs)
            it.assetPackOptionsForAssetPackBundle.set(assetPackOptionsForAssetPackBundle)
            it.bundleFile.set(bundleFile)
            it.bundleDeps.set(bundleDeps)
            it.bundleNeedsFusedStandaloneConfig.set(bundleNeedsFusedStandaloneConfig)
            it.appMetadata.set(appMetadata)
            it.abiFilters.set(abiFilters)
            it.binaryArtProfiler.set(binaryArtProfile)
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val bundleType: Property<Config.BundleConfig.BundleType>
        abstract val baseModuleFile: RegularFileProperty
        abstract val featureFiles: ConfigurableFileCollection
        abstract val assetPackFiles: ConfigurableFileCollection
        abstract val mainDexList: RegularFileProperty
        abstract val obfuscationMappingFile: RegularFileProperty
        abstract val integrityConfigFile: RegularFileProperty
        abstract val nativeDebugMetadataFiles: ConfigurableFileCollection
        abstract val aaptOptionsNoCompress: ListProperty<String>
        abstract val bundleOptions: Property<BundleOptions>
        abstract val compressNativeLibs: Property<Boolean>
        abstract val assetPackOptionsForAssetPackBundle: Property<AssetPackOptionsForAssetPackBundle>
        abstract val bundleFile: RegularFileProperty
        abstract val bundleDeps: RegularFileProperty
        abstract val bundleNeedsFusedStandaloneConfig: Property<Boolean>
        abstract val appMetadata: RegularFileProperty
        abstract val abiFilters: SetProperty<String>
        abstract val binaryArtProfiler: RegularFileProperty
    }

    abstract class BundleToolWorkAction : ProfileAwareWorkAction<Params>() {
        override fun run() {
            // BundleTool requires that the destination directory for the bundle file exists,
            // and that the bundle file itself does not
            val bundleFile = parameters.bundleFile.asFile.get()
            FileUtils.cleanOutputDir(bundleFile.parentFile)

            val builder = ImmutableList.builder<Path>()
            if (parameters.baseModuleFile.isPresent) {
                builder.add(parameters.baseModuleFile.asFile.get().toPath())
            }
            parameters.featureFiles.forEach { builder.add(getBundlePath(it)) }
            // BundleTool needs directories, not zips.
            parameters.assetPackFiles.files.forEach { builder.add(getBundlePath(it.parentFile)) }

            val noCompressGlobsForBundle =
                PackagingUtils.getNoCompressGlobsForBundle(parameters.aaptOptionsNoCompress.get())

            val splitsConfig = Config.SplitsConfig.newBuilder()
                .splitBy(Config.SplitDimension.Value.ABI, parameters.bundleOptions.get().enableAbi)
                .splitBy(
                    Config.SplitDimension.Value.SCREEN_DENSITY,
                    parameters.bundleOptions.get().enableDensity
                )
                .splitBy(Config.SplitDimension.Value.LANGUAGE, parameters.bundleOptions.get().enableLanguage)

            parameters.bundleOptions.get().enableTexture?.let {
                splitsConfig.addSplitDimension(
                    Config.SplitDimension.newBuilder()
                        .setValue(Config.SplitDimension.Value.TEXTURE_COMPRESSION_FORMAT)
                        .setSuffixStripping(
                            Config.SuffixStripping.newBuilder()
                                .setEnabled(true)
                                .setDefaultSuffix(parameters.bundleOptions.get().textureDefaultFormat ?: "")
                        )
                        .setNegate(!it)
                )
            }
            parameters.bundleOptions.get().enableDeviceTier?.let {
                splitsConfig.addSplitDimension(
                    Config.SplitDimension.newBuilder()
                        .setValue(Config.SplitDimension.Value.DEVICE_TIER)
                        .setSuffixStripping(
                            Config.SuffixStripping.newBuilder()
                                .setEnabled(true)
                                .setDefaultSuffix(parameters.bundleOptions.get().defaultDeviceTier?: "")
                        )
                        .setNegate(!it)
                )
            }

            val uncompressNativeLibrariesConfig = Config.UncompressNativeLibraries.newBuilder()
                .setEnabled(!parameters.compressNativeLibs.get())

            val bundleOptimizations = Config.Optimizations.newBuilder()
                .setSplitsConfig(splitsConfig)
                .setUncompressNativeLibraries(uncompressNativeLibrariesConfig)

            if (parameters.bundleNeedsFusedStandaloneConfig.get()) {
                bundleOptimizations.setStandaloneConfig(
                    Config.StandaloneConfig.newBuilder()
                        .addSplitDimension(
                            Config.SplitDimension.newBuilder()
                                .setValue(Config.SplitDimension.Value.ABI)
                                .setNegate(true)
                        )
                        .addSplitDimension(
                            Config.SplitDimension.newBuilder()
                                .setValue(Config.SplitDimension.Value.SCREEN_DENSITY)
                                .setNegate(true)
                        )
                        .addSplitDimension(
                            Config.SplitDimension.newBuilder()
                                .setValue(Config.SplitDimension.Value.LANGUAGE)
                                .setNegate(true)
                        )
                        .addSplitDimension(
                            Config.SplitDimension.newBuilder()
                                .setValue(Config.SplitDimension.Value.TEXTURE_COMPRESSION_FORMAT)
                                .setNegate(true)
                        )
                        .setStrip64BitLibraries(true)
                )
            }

            val bundleConfig =
                Config.BundleConfig.newBuilder()
                    .setType(parameters.bundleType.get())
                    .setCompression(
                        Config.Compression.newBuilder()
                            .addAllUncompressedGlob(noCompressGlobsForBundle)
                    )
                    .setOptimizations(bundleOptimizations)

            if (parameters.assetPackOptionsForAssetPackBundle.isPresent) {
                bundleConfig.assetModulesConfigBuilder
                    .setAssetVersionTag(
                        parameters.assetPackOptionsForAssetPackBundle.get().versionTag
                    )
                    .addAllAppVersion(
                        parameters.assetPackOptionsForAssetPackBundle.get().versionCodes
                            .map { it.toLong() }
                    )
            }

            val command = BuildBundleCommand.builder()
                // The bundle will be compressed if needed by FinalizeBundleTask
                .setUncompressedBundle(true)
                .setBundleConfig(bundleConfig.build())
                .setOutputPath(bundleFile.toPath())
                .setModulesPaths(builder.build())

            if (parameters.binaryArtProfiler.isPresent) {
                command.addMetadataFile(
                        SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK.replace('/', '.'),
                        SdkConstants.FN_BINARY_ART_PROFILE,
                        parameters.binaryArtProfiler.get().asFile.toPath(),
                )
            }

            parameters.bundleDeps.asFile.orNull?.let {
                command.addMetadataFile(
                    "com.android.tools.build.libraries",
                    "dependencies.pb",
                    it.toPath()
                )
            }

            parameters.mainDexList.asFile.orNull?.let {
                command.setMainDexListFile(it.toPath())
            }

            parameters.obfuscationMappingFile.asFile.orNull?.let {
                command.addMetadataFile(
                    "com.android.tools.build.obfuscation",
                    "proguard.map",
                    it.toPath()
                )
            }

            parameters.integrityConfigFile.asFile.orNull?.let {
                if (it.isFile) {
                    command.addMetadataFile(
                        "com.google.play.apps.integrity",
                        "AppIntegrityConfig.pb",
                        it.toPath()
                    )
                }
            }

            val abiFilters = parameters.abiFilters.get()
            parameters.nativeDebugMetadataFiles.forEach { file ->
                val abi = file.parentFile.name
                if (abiFilters.isEmpty() || abiFilters.contains(abi)) {
                    command.addMetadataFile(
                        "com.android.tools.build.debugsymbols",
                        "$abi/${file.name}",
                        file.toPath()
                    )
                }
            }

            command.addMetadataFile(
                    "com.android.tools.build.gradle",
                    APP_METADATA_FILE_NAME,
                    parameters.appMetadata.asFile.get().toPath()
            )

            command.build().execute()
        }

        private fun getBundlePath(folder: File): Path {
            val children = folder.listFiles()
            Preconditions.checkNotNull(children)
            Preconditions.checkState(children.size == 1)

            return children[0].toPath()
        }
    }

    data class BundleOptions(
        @get:Input
        @get:Optional
        val enableAbi: Boolean?,
        @get:Input
        @get:Optional
        val enableDensity: Boolean?,
        @get:Input
        @get:Optional
        val enableLanguage: Boolean?,
        @get:Input
        @get:Optional
        val enableTexture: Boolean?,
        @get:Input
        @get:Optional
        val textureDefaultFormat: String?,
        @get:Input
        @get:Optional
        val enableDeviceTier: Boolean?,
        @get:Input
        @get:Optional
        val defaultDeviceTier: String?
    ) : Serializable

    data class AssetPackOptionsForAssetPackBundle(
        @get:Input
        val versionCodes: List<Int>,
        @get:Input
        var versionTag: String?
    ) : Serializable

    /**
     * CreateAction for a Task that will pack the bundle artifact.
     */
    class CreationForAssetPackBundleAction(
        private val projectServices: ProjectServices,
        private val artifacts: ArtifactsImpl,
        private val assetPackBundle: AssetPackBundleExtension
    ) : TaskCreationAction<PackageBundleTask>() {

        override val type = PackageBundleTask::class.java
        override val name = "packageBundle"

        override fun handleProvider(
            taskProvider: TaskProvider<PackageBundleTask>
        ) {
            super.handleProvider(taskProvider)
            artifacts.setInitialProvider(
                taskProvider,
                PackageBundleTask::bundleFile
            ).on(InternalArtifactType.INTERMEDIARY_BUNDLE)
        }

        override fun configure(
            task: PackageBundleTask
        ) {
            task.configureVariantProperties(variantName = "", task.project)
            task.bundleType.set(Config.BundleConfig.BundleType.ASSET_ONLY)
            task.featureZips = projectServices.objectFactory.fileCollection()
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.ASSET_PACK_BUNDLE, task.assetPackZips
            )
            task.appMetadata
            task.bundleOptions = assetPackBundle.convert();
            task.compressNativeLibs.setDisallowChanges(true)
            task.assetPackOptionsForAssetPackBundle.set(
                AssetPackOptionsForAssetPackBundle(
                    versionCodes = assetPackBundle.versionCodes.toList(),
                    versionTag = assetPackBundle.versionTag
                )
            )
            task.bundleNeedsFusedStandaloneConfig.set(false)
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APP_METADATA,
                task.appMetadata
            )
        }
    }

    /**
     * CreateAction for a Task that will pack the bundle artifact.
     */
    class CreationAction(private val componentProperties: ApplicationVariantImpl) :
        VariantTaskCreationAction<PackageBundleTask, ApplicationVariantImpl>(
            componentProperties
        ) {

        override val name: String
            get() = computeTaskName("package", "Bundle")

        override val type: Class<PackageBundleTask>
            get() = PackageBundleTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageBundleTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackageBundleTask::bundleFile
            ).on(InternalArtifactType.INTERMEDIARY_BUNDLE)
        }

        override fun configure(
            task: PackageBundleTask
        ) {
            super.configure(task)

            task.bundleType.set(Config.BundleConfig.BundleType.REGULAR)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MODULE_BUNDLE, task.baseModuleZip
            )

            task.featureZips = creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE
            )

            if (creationConfig.needAssetPackTasks) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.ASSET_PACK_BUNDLE, task.assetPackZips
                )
            }

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BUNDLE_DEPENDENCY_REPORT,
                task.bundleDeps
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APP_INTEGRITY_CONFIG,
                task.integrityConfigFile
            )

            task.nativeDebugMetadataFiles.fromDisallowChanges(
                MergeNativeDebugMetadataTask.getNativeDebugMetadataFiles(creationConfig)
            )

            task.abiFilters.setDisallowChanges(creationConfig.variantDslInfo.supportedAbis)

            task.aaptOptionsNoCompress.setDisallowChanges(creationConfig.services.projectInfo.getExtension().aaptOptions.noCompress)

            task.bundleOptions =
                ((creationConfig.globalScope.extension as BaseAppModuleExtension).bundle).convert()

            task.compressNativeLibs.set(
                componentProperties.packaging.jniLibs.useLegacyPackagingFromBundle
            )
            // TODO(b/132103049, b/174695257) Deprecate the BooleanOption with instructions to use
            //  the DSL instead.
            if (!creationConfig.services.projectOptions[BooleanOption.ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE]) {
                task.compressNativeLibs.set(true)
            }
            task.compressNativeLibs.disallowChanges()

            if (creationConfig.needsMainDexListForBundle) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE,
                    task.mainDexList
                )
                // The dex files from this application are still processed for legacy multidex
                // in this case, as if none of the dynamic features are fused the bundle tool will
                // not reprocess the dex files.
            }

            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.obsfuscationMappingFile
            )

            task.bundleNeedsFusedStandaloneConfig.set(
                task.project.providers.provider {
                    creationConfig.minSdkVersion.getFeatureLevel() < MIN_SDK_FOR_SPLITS
                            && creationConfig.artifacts.get(InternalArtifactType.ASSET_PACK_BUNDLE).isPresent
                }
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APP_METADATA,
                task.appMetadata
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.BINARY_ART_PROFILE,
                task.binaryArtProfile)
        }
    }
}

private fun com.android.build.gradle.internal.dsl.BundleOptions.convert() =
    PackageBundleTask.BundleOptions(
        enableAbi = abi.enableSplit,
        enableDensity = density.enableSplit,
        enableLanguage = language.enableSplit,
        enableTexture = texture.enableSplit,
        textureDefaultFormat = texture.defaultFormat,
        enableDeviceTier = deviceTier.enableSplit,
        defaultDeviceTier = deviceTier.defaultTier
    )

private fun AssetPackBundleExtension.convert() =
    PackageBundleTask.BundleOptions(
        enableAbi = null,
        enableDensity = null,
        enableLanguage = null,
        enableTexture = texture.enableSplit,
        textureDefaultFormat = texture.defaultFormat,
        enableDeviceTier = deviceTier.enableSplit,
        defaultDeviceTier = deviceTier.defaultTier
    )

/**
 * convenience function to call [Config.SplitsConfig.Builder.addSplitDimension]
 *
 * @param flag the [Config.SplitDimension.Value] on which to set the value
 * @param value if true, split is enbaled for the given flag. If null, no change is made and the
 *              bundle-tool will decide the value.
 */
private fun Config.SplitsConfig.Builder.splitBy(
    flag: Config.SplitDimension.Value,
    value: Boolean?
): Config.SplitsConfig.Builder {
    value?.let {
        addSplitDimension(Config.SplitDimension.newBuilder().setValue(flag).setNegate(!it))
    }
    return this
}
