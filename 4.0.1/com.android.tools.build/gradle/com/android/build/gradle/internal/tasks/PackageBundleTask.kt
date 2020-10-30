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

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.packaging.PackagingUtils
import com.android.bundle.Config
import com.android.tools.build.bundletool.commands.BuildBundleCommand
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
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
import javax.inject.Inject

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

    @get:Input
    lateinit var aaptOptionsNoCompress: Collection<String>
        private set

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
    abstract val debuggable: Property<Boolean>

    @get:Input
    var bundleNeedsFusedStandaloneConfig: Boolean = false
        private set

    companion object {
        const val MIN_SDK_FOR_SPLITS = 21
    }

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                BundleToolRunnable::class.java,
                Params(
                    baseModuleFile = baseModuleZip.get().asFileTree.singleFile,
                    featureFiles = featureZips.files,
                    assetPackFiles = if (!assetPackZips.isPresent) null else assetPackZips.get().asFileTree.files,
                    mainDexList = mainDexList.orNull?.asFile,
                    obfuscationMappingFile = if (obsfuscationMappingFile.isPresent) obsfuscationMappingFile.get().asFile else null,
                    integrityConfigFile = if (integrityConfigFile.isPresent) integrityConfigFile.get().asFile else null,
                    aaptOptionsNoCompress = aaptOptionsNoCompress,
                    bundleOptions = bundleOptions,
                    bundleFlags = bundleFlags,
                    bundleFile = bundleFile.get().asFile,
                    bundleDeps = if(bundleDeps.isPresent) bundleDeps.get().asFile else null,
                    // do not compress the bundle in debug builds where it will be only used as an
                    // intermediate artifact
                    uncompressBundle = debuggable.get(),
                    bundleNeedsFusedStandaloneConfig = bundleNeedsFusedStandaloneConfig
                )
            )
        }
    }

    private data class Params(
        val baseModuleFile: File,
        val featureFiles: Set<File>,
        val assetPackFiles: Set<File>?,
        val mainDexList: File?,
        val obfuscationMappingFile: File?,
        val integrityConfigFile: File?,
        val aaptOptionsNoCompress: Collection<String>,
        val bundleOptions: BundleOptions,
        val bundleFlags: BundleFlags,
        val bundleFile: File,
        val bundleDeps: File?,
        val uncompressBundle: Boolean,
        val bundleNeedsFusedStandaloneConfig: Boolean
    ) : Serializable

    private class BundleToolRunnable @Inject constructor(private val params: Params) : Runnable {
        override fun run() {
            // BundleTool requires that the destination directory for the bundle file exists,
            // and that the bundle file itself does not
            val bundleFile = params.bundleFile
            FileUtils.cleanOutputDir(bundleFile.parentFile)

            val builder = ImmutableList.builder<Path>()
            builder.add(params.baseModuleFile.toPath())
            params.featureFiles.forEach { builder.add(getBundlePath(it)) }
            // BundleTool needs directories, not zips.
            params.assetPackFiles?.forEach { builder.add(getBundlePath(it.parentFile)) }

            val noCompressGlobsForBundle =
                PackagingUtils.getNoCompressGlobsForBundle(params.aaptOptionsNoCompress)

            val splitsConfig =  Config.SplitsConfig.newBuilder()
                .splitBy(Config.SplitDimension.Value.ABI, params.bundleOptions.enableAbi)
                .splitBy(Config.SplitDimension.Value.SCREEN_DENSITY, params.bundleOptions.enableDensity)
                .splitBy(Config.SplitDimension.Value.LANGUAGE, params.bundleOptions.enableLanguage)
                .splitBy(Config.SplitDimension.Value.TEXTURE_COMPRESSION_FORMAT, params.bundleOptions.enableTexture)

            val uncompressNativeLibrariesConfig = Config.UncompressNativeLibraries.newBuilder()
                .setEnabled(params.bundleFlags.enableUncompressedNativeLibs)

            val bundleOptimizations = Config.Optimizations.newBuilder()
                .setSplitsConfig(splitsConfig)
                .setUncompressNativeLibraries(uncompressNativeLibrariesConfig)

            if (params.bundleNeedsFusedStandaloneConfig) {
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
                            .addAllUncompressedGlob(noCompressGlobsForBundle))
                    .setOptimizations(bundleOptimizations)

            val command = BuildBundleCommand.builder()
                .setUncompressedBundle(params.uncompressBundle)
                .setBundleConfig(bundleConfig.build())
                .setOutputPath(bundleFile.toPath())
                .setModulesPaths(builder.build())

             params.bundleDeps?.let {
                 command.addMetadataFile(
                 "com.android.tools.build.libraries",
                 "dependencies.pb",
                 it.toPath() )
             }

            params.mainDexList?.let {
                command.setMainDexListFile(it.toPath())
            }

            params.obfuscationMappingFile?.let {
                command.addMetadataFile(
                    "com.android.tools.build.obfuscation",
                    "proguard.map",
                    it.toPath()
                )
            }

            params.integrityConfigFile?.let {
                if (it.isFile) {
                    command.addMetadataFile(
                        "com.google.play.apps.integrity",
                        "AppIntegrityConfig.pb",
                        it.toPath()
                    )
                }
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

    data class BundleOptions (
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
        val enableTexture: Boolean?) : Serializable

    data class BundleFlags(
        @get:Input
        val enableUncompressedNativeLibs: Boolean
    ) : Serializable

    /**
     * CreateAction for a Task that will pack the bundle artifact.
     */
    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<PackageBundleTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("package", "Bundle")

        override val type: Class<PackageBundleTask>
            get() = PackageBundleTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out PackageBundleTask>) {
            super.handleProvider(taskProvider)

            val bundleName = "${variantScope.globalScope.projectBaseName}-${variantScope.variantDslInfo.baseName}.aab"
            variantScope.artifacts.producesFile(
                InternalArtifactType.INTERMEDIARY_BUNDLE,
                taskProvider,
                PackageBundleTask::bundleFile,
                bundleName
            )
        }

        override fun configure(task: PackageBundleTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MODULE_BUNDLE, task.baseModuleZip)

            task.featureZips = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.MODULE_BUNDLE
            )

            if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.ASSET_PACK_BUNDLE)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.ASSET_PACK_BUNDLE, task.assetPackZips
                )
            }

            if(variantScope.artifacts.hasFinalProduct(InternalArtifactType.BUNDLE_DEPENDENCY_REPORT)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.BUNDLE_DEPENDENCY_REPORT,
                    task.bundleDeps
                )
            }

            if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.APP_INTEGRITY_CONFIG)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APP_INTEGRITY_CONFIG,
                    task.integrityConfigFile
                )
            }

            task.aaptOptionsNoCompress =
                    variantScope.globalScope.extension.aaptOptions.noCompress ?: listOf()

            task.bundleOptions =
                    ((variantScope.globalScope.extension as BaseAppModuleExtension).bundle).convert()

            task.bundleFlags = BundleFlags(
                enableUncompressedNativeLibs = variantScope.globalScope.projectOptions[BooleanOption.ENABLE_UNCOMPRESSED_NATIVE_LIBS_IN_BUNDLE]
            )

            if (variantScope.needsMainDexListForBundle) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE,
                    task.mainDexList)
                // The dex files from this application are still processed for legacy multidex
                // in this case, as if none of the dynamic features are fused the bundle tool will
                // not reprocess the dex files.
            }

            if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.APK_MAPPING)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APK_MAPPING,
                    task.obsfuscationMappingFile
                )
            }

            task.debuggable
                .setDisallowChanges(variantScope.variantDslInfo.isDebuggable)

            if (variantScope.minSdkVersion.featureLevel < MIN_SDK_FOR_SPLITS
                && task.assetPackZips.isPresent) {
                task.bundleNeedsFusedStandaloneConfig = true
            }
        }
    }
}

private fun com.android.build.gradle.internal.dsl.BundleOptions.convert() =
    PackageBundleTask.BundleOptions(
        enableAbi = abi.enableSplit,
        enableDensity = density.enableSplit,
        enableLanguage = language.enableSplit,
        enableTexture = texture.enableSplit
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
