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

import com.android.build.api.artifact.ArtifactType
import com.android.build.api.variant.impl.ApplicationVariantPropertiesImpl
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
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

    @get:InputFiles
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
    abstract val aaptOptionsNoCompress: ListProperty<String>

    @get:Nested
    lateinit var bundleOptions: BundleOptions
        private set

    @get:Nested
    lateinit var bundleFlags: BundleFlags
        private set

    @get:OutputFile
    abstract val bundleFile: RegularFileProperty

    @get:Input
    val fileName: String
        get() = bundleFile.get().asFile.name

    @get:Input
    abstract val bundleNeedsFusedStandaloneConfig: Property<Boolean>

    companion object {
        const val MIN_SDK_FOR_SPLITS = 21
    }

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(BundleToolWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.baseModuleFile.set(baseModuleZip.get().asFileTree.singleFile)
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
            it.bundleFlags.set(bundleFlags)
            it.bundleFile.set(bundleFile)
            it.bundleDeps.set(bundleDeps)
            it.bundleNeedsFusedStandaloneConfig.set(bundleNeedsFusedStandaloneConfig)
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val baseModuleFile: RegularFileProperty
        abstract val featureFiles: ConfigurableFileCollection
        abstract val assetPackFiles: ConfigurableFileCollection
        abstract val mainDexList: RegularFileProperty
        abstract val obfuscationMappingFile: RegularFileProperty
        abstract val integrityConfigFile: RegularFileProperty
        abstract val nativeDebugMetadataFiles: ConfigurableFileCollection
        abstract val aaptOptionsNoCompress: ListProperty<String>
        abstract val bundleOptions: Property<BundleOptions>
        abstract val bundleFlags: Property<BundleFlags>
        abstract val bundleFile: RegularFileProperty
        abstract val bundleDeps: RegularFileProperty
        abstract val bundleNeedsFusedStandaloneConfig: Property<Boolean>
    }

    abstract class BundleToolWorkAction : ProfileAwareWorkAction<Params>() {
        override fun run() {
            // BundleTool requires that the destination directory for the bundle file exists,
            // and that the bundle file itself does not
            val bundleFile = parameters.bundleFile.asFile.get()
            FileUtils.cleanOutputDir(bundleFile.parentFile)

            val builder = ImmutableList.builder<Path>()
            builder.add(parameters.baseModuleFile.asFile.get().toPath())
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
                .setEnabled(parameters.bundleFlags.get().enableUncompressedNativeLibs)

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
                    .setCompression(
                        Config.Compression.newBuilder()
                            .addAllUncompressedGlob(noCompressGlobsForBundle)
                    )
                    .setOptimizations(bundleOptimizations)

            val command = BuildBundleCommand.builder()
                // The bundle will be compressed if needed by FinalizeBundleTask
                .setUncompressedBundle(true)
                .setBundleConfig(bundleConfig.build())
                .setOutputPath(bundleFile.toPath())
                .setModulesPaths(builder.build())

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

            parameters.nativeDebugMetadataFiles.forEach { file ->
                command.addMetadataFile(
                    "com.android.tools.build.debugsymbols",
                    "${file.parentFile.name}/${file.name}",
                    file.toPath()
                )
            }

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

    data class BundleFlags(
        @get:Input
        val enableUncompressedNativeLibs: Boolean
    ) : Serializable

    /**
     * CreateAction for a Task that will pack the bundle artifact.
     */
    class CreationAction(componentProperties: ApplicationVariantPropertiesImpl) :
        VariantTaskCreationAction<PackageBundleTask, ApplicationVariantPropertiesImpl>(
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

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MODULE_BUNDLE, task.baseModuleZip
            )

            task.featureZips = creationConfig.variantDependencies.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE
            )

            if (creationConfig.needAssetPackTasks.get()) {
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

            task.aaptOptionsNoCompress.setDisallowChanges(creationConfig.globalScope.extension.aaptOptions.noCompress)

            task.bundleOptions =
                ((creationConfig.globalScope.extension as BaseAppModuleExtension).bundle).convert()

            task.bundleFlags = BundleFlags(
                enableUncompressedNativeLibs = creationConfig.services.projectOptions[BooleanOption.ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE]
            )

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
                ArtifactType.OBFUSCATION_MAPPING_FILE,
                task.obsfuscationMappingFile
            )

            task.bundleNeedsFusedStandaloneConfig.set(
                task.project.providers.provider {
                    creationConfig.minSdkVersion.getFeatureLevel() < MIN_SDK_FOR_SPLITS
                            && creationConfig.artifacts.get(InternalArtifactType.ASSET_PACK_BUNDLE).isPresent
                }
            )
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
