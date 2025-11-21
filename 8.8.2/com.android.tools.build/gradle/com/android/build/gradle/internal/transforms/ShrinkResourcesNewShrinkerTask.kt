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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.R8ResourceShrinkingParameters
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.initialize
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.build.shrinker.LinkedResourcesFormat
import com.android.build.shrinker.LoggerAndFileDebugReporter
import com.android.build.shrinker.ResourceShrinkerImpl
import com.android.build.shrinker.gatherer.ProtoResourceTableGatherer
import com.android.build.shrinker.graph.ProtoResourcesGraphBuilder
import com.android.build.shrinker.obfuscation.ProguardMappingsRecorder
import com.android.build.shrinker.usages.DexUsageRecorder
import com.android.build.shrinker.usages.ProtoAndroidManifestUsageRecorder
import com.android.build.shrinker.usages.ToolsAttributeUsageRecorder
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.ResourceShrinkingConfig
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import javax.inject.Inject

/**
 * Shrinks application resources in proto format.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION, secondaryTaskCategories = [TaskCategory.ANDROID_RESOURCES])
abstract class ShrinkResourcesNewShrinkerTask : NonIncrementalTask() {

    @get:Nested
    abstract val params: R8ResourceShrinkingParameters

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dex: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:Optional
    abstract val mappingFile: RegularFileProperty

    override fun doTaskAction() {
        val inputBuiltArtifacts = params.loadInputBuiltArtifacts()
        params.saveOutputBuiltArtifactsMetadata()

        val workQueue = workerExecutor.noIsolation()

        repeat(inputBuiltArtifacts.elements.size) { index ->
            workQueue.submit(ShrinkProtoResourcesAction::class.java) {
                it.config.set(params.toConfig())
                it.index.set(index)
                it.dex.from(dex)
                it.mappingFile.set(mappingFile)
            }
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<ShrinkResourcesNewShrinkerTask, ApkCreationConfig>(
        creationConfig
    ) {
        override val type = ShrinkResourcesNewShrinkerTask::class.java
        override val name = computeTaskName("shrink", "Res")

        override fun handleProvider(taskProvider: TaskProvider<ShrinkResourcesNewShrinkerTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(taskProvider) {
                it.params.shrunkResourcesOutputDir
            }.on(SHRUNK_RESOURCES_PROTO_FORMAT)
        }

        override fun configure(task: ShrinkResourcesNewShrinkerTask) {
            super.configure(task)

            task.params.initialize(creationConfig, task.mappingFile)
            task.dex.from(PackageAndroidArtifact.CreationAction.getDexFolders(creationConfig))
            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.mappingFile
            )
        }
    }
}

abstract class ShrinkProtoResourcesParams : DecoratedWorkParameters {

    abstract val config: Property<ResourceShrinkingConfig>

    /**
     * For multi-APKs, each worker action creates 1 APK. Suppose there are N worker actions to
     * create N APKs. Then, [index] is a number from 0 to N-1, corresponding to the current worker
     * action / APK.
     */
    abstract val index: Property<Int>

    abstract val dex: ConfigurableFileCollection

    @get:Optional
    abstract val mappingFile: RegularFileProperty

}

abstract class ShrinkProtoResourcesAction @Inject constructor() :
    WorkAction<ShrinkProtoResourcesParams> {

    private val logger = Logging.getLogger(ShrinkResourcesNewShrinkerTask::class.java)

    override fun execute() {
        val config = parameters.config.get()
        val originalProtoFile = config.linkedResourcesInputFiles[parameters.index.get()]
        val shrunkProtoFile = config.shrunkResourcesOutputFiles[parameters.index.get()]

        FileUtils.createZipFilesystem(originalProtoFile.toPath()).use { fs ->
            val dexRecorders = parameters.dex.files.map { DexUsageRecorder(it.toPath()) }
            val manifestRecorder =
                ProtoAndroidManifestUsageRecorder(fs.getPath("AndroidManifest.xml"))
            val toolsRecorder =
                ToolsAttributeUsageRecorder(config.mergedNotCompiledResourcesInputDir.toPath())
            val gatherer = ProtoResourceTableGatherer(fs.getPath("resources.pb"))
            val graphBuilder = ProtoResourcesGraphBuilder(
                resourceRoot = fs.getPath("res"),
                resourceTable = fs.getPath("resources.pb")
            )
            val obfuscationMappings =
                parameters.mappingFile.orNull?.asFile?.let { ProguardMappingsRecorder(it.toPath()) }

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
                    config.logFile
                ),
                supportMultipackages = false,
                usePreciseShrinking = config.usePreciseShrinking
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
