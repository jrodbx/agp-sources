
/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.workeractions.DecoratedWorkParameters
import com.android.build.gradle.internal.workeractions.WorkActionAdapter
import com.android.ide.common.process.BaseProcessOutputHandler
import com.android.ide.common.process.CachedProcessOutputHandler
import com.android.ide.common.process.DefaultProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.android.utils.LineCollector
import com.android.utils.StdLogger
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import java.io.File
import javax.inject.Inject

/**
 * OptimizeResourceTask attempts to use AAPT2's optimize sub-operation to reduce the size of the
 * final apk. There are a number of potential optimizations performed such as resource obfuscation,
 * path shortening and sparse encoding. If the optimized apk file size is less than before, then
 * the optimized resources content is made identical to [InternalArtifactType.PROCESSED_RES].
 */
@CacheableTask
abstract class OptimizeResourcesTask : NonIncrementalTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputProcessedRes: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Input
    abstract val enableResourceObfuscation: Property<Boolean>

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<OptimizeResourcesTask>>

    @get:Nested
    abstract val variantOutputs : ListProperty<VariantOutputImpl>

    @get:OutputDirectory
    abstract val optimizedProcessedRes: DirectoryProperty

    @TaskAction
    override fun doTaskAction() {
        transformationRequest.get().submit(
                this,
                workerExecutor.noIsolation(),
                Aapt2OptimizeWorkAction::class.java
        ) { builtArtifact, outputLocation: Directory, parameters ->
            val variantOutput = variantOutputs.get().find {
                it.variantOutputConfiguration.outputType == builtArtifact.outputType
                        && it.variantOutputConfiguration.filters == builtArtifact.filters
            } ?: throw java.lang.RuntimeException("Cannot find variant output for $builtArtifact")

            parameters.inputResFile.set(File(builtArtifact.outputFile))
            parameters.aapt2Executable.set(aapt2.getAapt2Executable().toFile())
            parameters.enableResourceObfuscation.set(enableResourceObfuscation.get())
            parameters.outputResFile.set(File(outputLocation.asFile,
                    "resources-${variantOutput.baseName}-optimize${SdkConstants.DOT_RES}"))

            parameters.outputResFile.get().asFile
        }
    }

    interface OptimizeResourcesParams : DecoratedWorkParameters {
        val aapt2Executable: RegularFileProperty
        val inputResFile: RegularFileProperty
        val enableResourceObfuscation: Property<Boolean>
        val outputResFile: RegularFileProperty
    }

    abstract class Aapt2OptimizeWorkAction
    @Inject constructor(private val params: OptimizeResourcesParams) : WorkActionAdapter<OptimizeResourcesParams> {
        override fun doExecute() = doFullTaskAction(params)
    }

    class CreateAction(
            creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<OptimizeResourcesTask, ComponentCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("optimize", "Resources")
        override val type: Class<OptimizeResourcesTask>
            get() = OptimizeResourcesTask::class.java

        private lateinit var transformationRequest: ArtifactTransformationRequest<OptimizeResourcesTask>


        override fun handleProvider(taskProvider: TaskProvider<OptimizeResourcesTask>) {
            super.handleProvider(taskProvider)
            val resourceShrinkingEnabled = creationConfig.useResourceShrinker()
            val operationRequest = creationConfig.artifacts.use(taskProvider).wiredWithDirectories(
                    OptimizeResourcesTask::inputProcessedRes,
                    OptimizeResourcesTask::optimizedProcessedRes)

            transformationRequest = if (resourceShrinkingEnabled) {
                operationRequest.toTransformMany(
                    InternalArtifactType.SHRUNK_PROCESSED_RES,
                    InternalArtifactType.OPTIMIZED_PROCESSED_RES)
            } else {
                operationRequest.toTransformMany(
                        InternalArtifactType.PROCESSED_RES,
                        InternalArtifactType.OPTIMIZED_PROCESSED_RES)
            }
        }

        override fun configure(task: OptimizeResourcesTask) {
            super.configure(task)
            val enabledVariantOutputs = creationConfig.outputs.getEnabledVariantOutputs()

            creationConfig.services.initializeAapt2Input(task.aapt2)

            task.enableResourceObfuscation.setDisallowChanges(false)

            task.transformationRequest.setDisallowChanges(transformationRequest)

            task.variantOutputs.setDisallowChanges(enabledVariantOutputs)
        }
    }
}

enum class AAPT2OptimizeFlags(val flag: String) {
    COLLAPSE_RESOURCE_NAMES("--collapse-resource-names"),
    SHORTEN_RESOURCE_PATHS("--shorten-resource-paths"),
    ENABLE_SPARSE_ENCODING("--enable-sparse-encoding")
}

internal fun doFullTaskAction(params: OptimizeResourcesTask.OptimizeResourcesParams)  {
    val inputFile = params.inputResFile.get().asFile
    val outputFile = params.outputResFile.get().asFile

    val optimizeFlags = mutableSetOf(
        AAPT2OptimizeFlags.SHORTEN_RESOURCE_PATHS.flag
    )
    if (params.enableResourceObfuscation.get()) {
        optimizeFlags += AAPT2OptimizeFlags.COLLAPSE_RESOURCE_NAMES.flag
    }

    val aaptInputFile = if (inputFile.isDirectory) {
        inputFile.listFiles()
                ?.filter {
                    it.extension == SdkConstants.EXT_RES
                            || it.extension == SdkConstants.EXT_ANDROID_PACKAGE
                }
                ?.get(0)
    } else {
        inputFile
    }

    aaptInputFile?.let {
        invokeAapt(
                params.aapt2Executable.get().asFile,
                "optimize",
                it.path,
                *optimizeFlags.toTypedArray(),
                "-o",
                outputFile.path
        )
        // If the optimized file is greater number of bytes than the original file, it
        // is reassigned to the original file.
        if (outputFile.length() >= it.length()) {
            FileUtils.copyFile(inputFile, outputFile)
        }
    }
}

internal fun invokeAapt(aapt2: File, vararg args: String): List<String> {
    val processOutputHeader = CachedProcessOutputHandler()
    val processInfoBuilder = ProcessInfoBuilder()
            .setExecutable(aapt2)
            .addArgs(args)
    val processExecutor = DefaultProcessExecutor(StdLogger(StdLogger.Level.ERROR))
    processExecutor
            .execute(processInfoBuilder.createProcess(), processOutputHeader)
            .rethrowFailure()
    val output: BaseProcessOutputHandler.BaseProcessOutput = processOutputHeader.processOutput
    val lineCollector = LineCollector()
    output.processStandardOutputLines(lineCollector)
    return lineCollector.result
}
