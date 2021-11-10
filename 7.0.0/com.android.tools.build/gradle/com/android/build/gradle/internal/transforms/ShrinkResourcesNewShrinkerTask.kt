/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.res.shrinker.LinkedResourcesFormat
import com.android.build.gradle.internal.res.shrinker.LoggerAndFileDebugReporter
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerImpl
import com.android.build.gradle.internal.res.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.gradle.internal.res.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.gradle.internal.res.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.gradle.internal.res.shrinker.usages.DexUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.gradle.internal.res.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAaptDaemon
import com.android.build.gradle.internal.services.registerAaptService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.aapt.AaptConvertConfig
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import javax.inject.Inject

/**
 * Shrinks application resources in proto format and convert them to binary via `aapt2 convert`.
 *
 * <p>Proto resources are taken from output of {@link LinkAndroidResForBundleTask} if possible. If
 * not, binary resources produced by {@link LinkApplicationAndroidResourcesTask} are converted to
 * proto first and passed as input to the shrinker.
 */
@CacheableTask
abstract class ShrinkResourcesNewShrinkerTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val shrunkResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalResources: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val originalResourcesForBundle: RegularFileProperty

    @get:Internal
    abstract val artifactTransformationRequest: Property<ArtifactTransformationRequest<ShrinkResourcesNewShrinkerTask>>

    @get:Nested
    abstract val variantOutputs: ListProperty<VariantOutputImpl>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dex: ListProperty<Directory>

    @get:Input
    abstract val usePreciseShrinking: Property<Boolean>

    @get:Nested
    abstract val aapt: Aapt2Input

    override fun doTaskAction() {
        artifactTransformationRequest.get().submit(
            task = this,
            workQueue = workerExecutor.noIsolation(),
            actionType = ShrinkProtoResourcesAction::class.java
        ) { builtArtifact: BuiltArtifact, directory: Directory, parameters: ShrinkProtoResourcesParams ->

            parameters.usePreciseShrinking.set(usePreciseShrinking)

            val variant = variantOutputs.get().find {
                it.variantOutputConfiguration.outputType == builtArtifact.outputType
                        && it.variantOutputConfiguration.filters == builtArtifact.filters
            } ?: throw java.lang.RuntimeException("Cannot find variant output for $builtArtifact")
            val variantName = variant.baseName

            parameters.outputFile.set(
                File(directory.asFile, "resources-$variantName-stripped.ap_")
            )
            parameters.shrunkProtoFile.set(
                File(directory.asFile, "resources-$variantName-proto-stripped.ap_")
            )
            parameters.originalFile.set(File(builtArtifact.outputFile))

            if (originalResourcesForBundle.isPresent) {
                parameters.requiresInitialConversionToProto.set(false)
                parameters.originalProtoFile.set(originalResourcesForBundle)
            } else {
                parameters.originalProtoFile.set(
                    File(directory.asFile, "original-$variantName-proto.ap_")
                )
                parameters.requiresInitialConversionToProto.set(true)
            }

            parameters.reportFile.set(
                File(directory.asFile, "resources-$variantName.txt")
            )

            parameters.dex.set(dex)
            parameters.mappingFileSrc.set(mappingFileSrc)
            parameters.resourceDir.set(resourceDir)

            parameters.aapt2ServiceKey.set(aapt.registerAaptService())

            parameters.outputFile.get().asFile
        }
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<ShrinkResourcesNewShrinkerTask, VariantCreationConfig>(
        creationConfig
    ) {
        override val type = ShrinkResourcesNewShrinkerTask::class.java
        override val name = computeTaskName("shrink", "Res")
        lateinit var transformationRequest: ArtifactTransformationRequest<ShrinkResourcesNewShrinkerTask>

        override fun handleProvider(taskProvider: TaskProvider<ShrinkResourcesNewShrinkerTask>) {
            super.handleProvider(taskProvider)
            transformationRequest = creationConfig.artifacts.use(taskProvider)
                .wiredWithDirectories(
                    ShrinkResourcesNewShrinkerTask::originalResources,
                    ShrinkResourcesNewShrinkerTask::shrunkResources
                )
                .toTransformMany(
                    InternalArtifactType.PROCESSED_RES,
                    InternalArtifactType.SHRUNK_PROCESSED_RES
                )
        }

        override fun configure(task: ShrinkResourcesNewShrinkerTask) {
            super.configure(task)

            task.usePreciseShrinking.set(
                creationConfig.services.projectOptions.get(
                    BooleanOption.ENABLE_NEW_RESOURCE_SHRINKER_PRECISE
                )
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.mappingFileSrc
            )

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_NOT_COMPILED_RES,
                task.resourceDir
            )

            creationConfig.outputs.getEnabledVariantOutputs().forEach(task.variantOutputs::add)

            // If we have only one variant there is no splits configured and we can consume proto
            // resources already created for bundles.
            val variantOutputs = creationConfig.outputs.variantOutputs
            if (variantOutputs.size == 1 && variantOutputs.all { it.filters.isEmpty() }) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LINKED_RES_FOR_BUNDLE,
                    task.originalResourcesForBundle
                )
            }

            task.artifactTransformationRequest.set(transformationRequest)

            task.dex.addAll(creationConfig.artifacts.getAll(InternalMultipleArtifactType.DEX))

            creationConfig.services.initializeAapt2Input(task.aapt)
        }
    }
}

abstract class ShrinkProtoResourcesParams : DecoratedWorkParameters {
    abstract val usePreciseShrinking: Property<Boolean>
    abstract val requiresInitialConversionToProto: Property<Boolean>

    abstract val outputFile: RegularFileProperty
    abstract val shrunkProtoFile: RegularFileProperty

    abstract val originalProtoFile: RegularFileProperty
    @get:Optional
    abstract val originalFile: RegularFileProperty

    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    abstract val resourceDir: DirectoryProperty
    abstract val dex: ListProperty<Directory>

    abstract val aapt2ServiceKey: Property<Aapt2DaemonServiceKey>

    abstract val reportFile: RegularFileProperty
}

abstract class ShrinkProtoResourcesAction @Inject constructor() :
    WorkActionAdapter<ShrinkProtoResourcesParams> {

    private val logger = Logging.getLogger(ShrinkAppBundleResourcesTask::class.java)

    override fun doExecute() {
        val aapt2ServiceKey = parameters.aapt2ServiceKey.get()
        val originalFile = parameters.originalFile.get().asFile
        val originalProtoFile = parameters.originalProtoFile.get().asFile
        val shrunkProtoFile = parameters.shrunkProtoFile.get().asFile
        val shrunkFile = parameters.outputFile.get().asFile

        FileUtils.mkdirs(shrunkFile.parentFile)

        if (parameters.requiresInitialConversionToProto.get()) {
            getAaptDaemon(aapt2ServiceKey).use {
                it.convert(
                    AaptConvertConfig(
                        inputFile = originalFile,
                        outputFile = originalProtoFile,
                        convertToProtos = true
                    ),
                    LoggerWrapper(logger)
                )
            }
        }

        FileUtils.createZipFilesystem(originalProtoFile.toPath()).use { fs ->
            val dexRecorders = parameters.dex.get().map { DexUsageRecorder(it.asFile.toPath()) }
            val manifestRecorder =
                ProtoAndroidManifestUsageRecorder(fs.getPath("AndroidManifest.xml"))
            val toolsRecorder =
                ToolsAttributeUsageRecorder(parameters.resourceDir.asFile.get().toPath())
            val gatherer = ProtoResourceTableGatherer(fs.getPath("resources.pb"))
            val graphBuilder = ProtoResourcesGraphBuilder(
                resourceRoot = fs.getPath("res"),
                resourceTable = fs.getPath("resources.pb")
            )
            val obfuscationMappings =
                parameters.mappingFileSrc.orNull?.asFile?.let { ProguardMappingsRecorder(it.toPath()) }

            ResourceShrinkerImpl(
                resourcesGatherers = listOf(gatherer),
                obfuscationMappingsRecorder = obfuscationMappings,
                usageRecorders = dexRecorders + manifestRecorder + toolsRecorder,
                graphBuilders = listOf(graphBuilder),
                debugReporter = LoggerAndFileDebugReporter(
                    logger,
                    parameters.reportFile.get().asFile
                ),
                supportMultipackages = false,
                usePreciseShrinking = parameters.usePreciseShrinking.get()
            ).use { shrinker ->
                shrinker.analyze()

                shrinker.rewriteResourcesInApkFormat(
                    originalProtoFile,
                    shrunkProtoFile,
                    LinkedResourcesFormat.PROTO
                )

                // There is no need to convert file back because resource shrinker was unable to
                // decrease resources size and we can just use original.
                if (shrunkProtoFile.length() < originalProtoFile.length()) {
                    getAaptDaemon(aapt2ServiceKey).use {
                        it.convert(
                            AaptConvertConfig(
                                inputFile = shrunkProtoFile,
                                outputFile = shrunkFile,
                                convertToProtos = false
                            ),
                            LoggerWrapper(logger)
                        )
                    }

                    // After our processing we received file which is bigger than original -
                    // rollback to original one.
                    if (originalFile.length() < shrunkFile.length()) {
                        Files.copy(originalFile, shrunkFile)
                    }
                } else {
                    Files.copy(originalFile, shrunkFile)
                }

                // Dump some stats
                if (shrinker.unusedResourceCount > 0) {
                    val before = originalFile.length()
                    val after = shrunkFile.length()
                    val percent = ((before - after) * 100 / before).toInt()

                    val stat = "Removed unused resources: Binary resource data reduced from " +
                            "${toKbString(before)}KB to ${toKbString(after)}KB. Removed $percent%"
                    logger.info(stat)
                }
            }
        }
    }

    private fun toKbString(size: Long): String {
        return (size.toInt() / 1024).toString()
    }
}
