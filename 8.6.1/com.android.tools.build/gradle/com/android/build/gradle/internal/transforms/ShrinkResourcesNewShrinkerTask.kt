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

import com.android.SdkConstants
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifact
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.shrinker.LinkedResourcesFormat
import com.android.build.shrinker.LoggerAndFileDebugReporter
import com.android.build.shrinker.ResourceShrinkerImpl
import com.android.build.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.shrinker.usages.DexUsageRecorder
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAaptDaemon
import com.android.build.gradle.internal.services.registerAaptService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.aapt.AaptConvertConfig
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LogLevel
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
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION, secondaryTaskCategories = [TaskCategory.ANDROID_RESOURCES])
abstract class ShrinkResourcesNewShrinkerTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val shrunkResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalResources: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val originalResourcesForBundle: RegularFileProperty

    @get:Internal
    abstract val artifactTransformationRequest: Property<ArtifactTransformationRequest<ShrinkResourcesNewShrinkerTask>>

    @get:Nested
    abstract val outputsHandler: Property<MultiOutputHandler>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dex: ConfigurableFileCollection

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

            parameters.shrunkProtoFile.set(
                File(
                    directory.asFile,
                    outputsHandler.get().getOutputNameForSplit(
                        prefix = "shrunk-resources",
                        suffix = "proto-format${SdkConstants.DOT_RES}",
                        outputType = builtArtifact.outputType,
                        filters = builtArtifact.filters
                    )
                )
            )
            parameters.originalFile.set(File(builtArtifact.outputFile))

            if (originalResourcesForBundle.isPresent) {
                parameters.requiresInitialConversionToProto.set(false)
                parameters.originalProtoFile.set(originalResourcesForBundle)
            } else {
                parameters.originalProtoFile.set(
                    File(
                        directory.asFile,
                        outputsHandler.get().getOutputNameForSplit(
                            prefix = "original-resources",
                            suffix = "proto-format.ap_",
                            outputType = builtArtifact.outputType,
                            filters = builtArtifact.filters
                        )
                    )
                )
                parameters.requiresInitialConversionToProto.set(true)
            }

            if (mappingFileSrc.isPresent) {
                mappingFileSrc.get().asFile.parentFile?.let {
                    parameters.reportFile.set(File(it, "resources.txt"));
                }
            }

            parameters.dex.from(dex)
            parameters.mappingFileSrc.set(mappingFileSrc)
            parameters.resourceDir.set(resourceDir)

            parameters.aapt2ServiceKey.set(aapt.registerAaptService())

            return@submit parameters.shrunkProtoFile.get().asFile
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<ShrinkResourcesNewShrinkerTask, ApkCreationConfig>(
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
                    InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT,
                    InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT
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

            // If we have only one variant there is no splits configured and we can consume proto
            // resources already created for bundles.
            if (creationConfig !is ApplicationCreationConfig ||
                (creationConfig.outputs.variantOutputs.size == 1 &&
                        creationConfig.outputs.variantOutputs.all { it.filters.isEmpty() })) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LINKED_RESOURCES_FOR_BUNDLE_PROTO_FORMAT,
                    task.originalResourcesForBundle
                )
            }

            task.artifactTransformationRequest.set(transformationRequest)

            task.dex.from(
                PackageAndroidArtifact.CreationAction.getDexFolders(creationConfig)
            )

            creationConfig.services.initializeAapt2Input(task.aapt)

            task.outputsHandler.setDisallowChanges(MultiOutputHandler.create(creationConfig))
        }
    }
}

abstract class ShrinkProtoResourcesParams : DecoratedWorkParameters {
    abstract val usePreciseShrinking: Property<Boolean>
    abstract val requiresInitialConversionToProto: Property<Boolean>

    abstract val shrunkProtoFile: RegularFileProperty

    abstract val originalProtoFile: RegularFileProperty
    @get:Optional
    abstract val originalFile: RegularFileProperty

    @get:Optional
    abstract val mappingFileSrc: RegularFileProperty

    abstract val resourceDir: DirectoryProperty
    abstract val dex: ConfigurableFileCollection

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
            val dexRecorders = parameters.dex.files.map { DexUsageRecorder(it.toPath()) }
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
                    logDebug = { debugMessage ->
                        if (logger.isEnabled(LogLevel.DEBUG)) {
                            logger.log(LogLevel.DEBUG, debugMessage)
                        }
                    },
                    logInfo = { infoMessage ->
                        if (logger.isEnabled(LogLevel.DEBUG)) {
                            logger.log(LogLevel.DEBUG, infoMessage)
                        }
                    },
                    parameters.reportFile.orNull?.asFile
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

                // Dump some stats
                if (shrinker.unusedResourceCount > 0) {
                    val before = shrunkProtoFile.length()
                    val after = shrunkProtoFile.length()
                    val percent = ((before - after) * 100 / before).toInt()

                    val stat = "Removed unused resources: Proto resource data reduced from " +
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
