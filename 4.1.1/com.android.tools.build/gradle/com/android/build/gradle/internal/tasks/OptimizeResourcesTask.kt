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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.BaseProcessOutputHandler
import com.android.ide.common.process.CachedProcessOutputHandler
import com.android.ide.common.process.DefaultProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.LineCollector
import com.android.utils.StdLogger
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject

/**
 * OptimizeResourceTask attempts to use AAPT2's optimize sub-operation to reduce the size of the
 * final apk. There are a number of potential optimizations performed such as resource obfuscation,
 * path shortening and sparse encoding. If the optimized apk file size is less than before, then
 * the optimized resources are published as InternalArtifactType.PROCESSED_RES.
 */
abstract class OptimizeResourcesTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputApkFile: DirectoryProperty

    @get:Input
    abstract val aapt2Executable: Property<FileCollection>

    @get:Input
    abstract val enableSparseEncoding: Property<Boolean>

    @get:Input
    abstract val enableResourceObfuscation: Property<Boolean>

    @get:Input
    abstract val enableResourcePathShortening: Property<Boolean>

    @get:OutputFile
    abstract val optimizedApkFile: DirectoryProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                    Aapt2OptimizeRunnable::class.java,
                    OptimizeResourcesParams(
                            aapt2Executable = aapt2Executable.get().singleFile,
                            inputApkFile = inputApkFile.get().asFile,
                            enableResourceObfuscation = enableResourceObfuscation.get(),
                            enableSparseResourceEncoding = enableSparseEncoding.get(),
                            enableResourcePathShortening = enableResourcePathShortening.get(),
                            outputApkFile = optimizedApkFile.get().asFile
                    )
            )
        }
    }

    data class OptimizeResourcesParams(
            val aapt2Executable: File,
            val inputApkFile: File,
            val enableResourceObfuscation: Boolean,
            val enableSparseResourceEncoding: Boolean,
            val enableResourcePathShortening: Boolean,
            var outputApkFile: File
    ) : Serializable

    class Aapt2OptimizeRunnable
    @Inject constructor(private val params: OptimizeResourcesParams) : Runnable {
        override fun run() = doFullTaskAction(params)
    }

    class CreateAction(
            componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<OptimizeResourcesTask, ComponentPropertiesImpl>(componentProperties) {
        override val name: String
            get() = computeTaskName("optimize", "Resources")
        override val type: Class<OptimizeResourcesTask>
            get() = OptimizeResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<OptimizeResourcesTask>) {
            super.handleProvider(taskProvider)
            // OPTIMIZED_PROCESSED_RES will be republished as PROCESSED_RES on task completion.
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                OptimizeResourcesTask::optimizedApkFile
            ).on(InternalArtifactType.OPTIMIZED_PROCESSED_RES)
        }

        override fun configure(task: OptimizeResourcesTask) {
            super.configure(task)
            val resourceShrinkingEnabled = creationConfig.variantScope.useResourceShrinker()

            task.inputApkFile.setDisallowChanges(
                    creationConfig.artifacts.get(InternalArtifactType
                            .PROCESSED_RES))
            task.aapt2Executable.setDisallowChanges(
                    getAapt2FromMavenAndVersion(creationConfig.globalScope).first)
            // TODO(lukeedgar) Determine flags which can be enabled without resource shrinking
            //  enabled.
            task.enableSparseEncoding.setDisallowChanges(resourceShrinkingEnabled)
            task.enableResourceObfuscation.setDisallowChanges(resourceShrinkingEnabled)
            task.enableResourcePathShortening.setDisallowChanges(resourceShrinkingEnabled)
        }
    }
}

enum class AAPT2OptimizeFlags(val flag: String) {
    COLLAPSE_RESOURCE_NAMES("--collapse-resource-names"),
    SHORTEN_RESOURCE_PATHS("--shorten-resource-paths"),
    ENABLE_SPARSE_ENCODING("--enable-sparse-encoding")
}

internal fun doFullTaskAction(params: OptimizeResourcesTask.OptimizeResourcesParams) {
    if (!verifyAaptFlagEnabled(params)){
        throw NoSuchElementException("No AAPT OPTIMIZE flags enabled.")
    }
    val optimizeFlags = mutableSetOf<String>()
    if (params.enableResourceObfuscation) {
        optimizeFlags += AAPT2OptimizeFlags.COLLAPSE_RESOURCE_NAMES.flag
    }
    if (params.enableResourcePathShortening) {
        optimizeFlags += AAPT2OptimizeFlags.SHORTEN_RESOURCE_PATHS.flag
    }
    if (params.enableSparseResourceEncoding) {
        optimizeFlags += AAPT2OptimizeFlags.ENABLE_SPARSE_ENCODING.flag
    }
    invokeAapt(params.aapt2Executable, "optimize", params.inputApkFile.path,
            *optimizeFlags.toTypedArray(), "-o", params.outputApkFile.path)
    // If the optimized file is greater number of bytes than the original file, it
    // is reassigned to the original APK file.
    if (params.outputApkFile.length() >= params.inputApkFile.length()) {
        Files.copy(params.inputApkFile.toPath(), params.outputApkFile.toPath())
    }
}

internal fun invokeAapt(aapt2Executable: File, vararg args: String): List<String> {
    val processOutputHeader = CachedProcessOutputHandler()
    val processInfoBuilder = ProcessInfoBuilder()
            .setExecutable(aapt2Executable)
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

internal fun verifyAaptFlagEnabled(params: OptimizeResourcesTask.OptimizeResourcesParams): Boolean =
        params.enableResourceObfuscation
                || params.enableResourcePathShortening
                || params.enableSparseResourceEncoding