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
import com.android.SdkConstants.DOT_DBG
import com.android.SdkConstants.DOT_SYM
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_DEBUG_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_SYMBOL_TABLES
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.repository.Revision
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Task to produce native debug metadata files to be included in the app bundle.
 */
@CacheableTask
abstract class ExtractNativeDebugMetadataTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @Input
    fun getNdkRevision(): Provider<Revision> = sdkBuildService.flatMap { it.ndkRevisionProvider }

    @get:Input
    lateinit var debugSymbolLevel: DebugSymbolLevel
        private set

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    // We need this inputFiles property because SkipWhenEmpty doesn't work for inputDir because it's
    // a DirectoryProperty
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: Property<FileTree>

    override fun doTaskAction() {
        getWorkerFacadeWithThreads(useGradleExecutor = false).use { workers ->
            ExtractNativeDebugMetadataDelegate(
                workers,
                inputDir.get().asFile,
                outputDir.get().asFile,
                sdkBuildService.get().objcopyExecutableMapProvider.get(),
                debugSymbolLevel,
                GradleProcessExecutor(execOperations::exec)
            ).run()
        }
    }

    abstract class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<ExtractNativeDebugMetadataTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val type: Class<ExtractNativeDebugMetadataTask>
            get() = ExtractNativeDebugMetadataTask::class.java

        override fun configure(task: ExtractNativeDebugMetadataTask) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(MERGED_NATIVE_LIBS, task.inputDir)
            task.inputFiles.setDisallowChanges(
                creationConfig.globalScope.project.provider {
                    creationConfig.globalScope.project.layout.files(task.inputDir).asFileTree
                }
            )
            task.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }

    class FullCreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : CreationAction(componentProperties) {

        override val name: String
            get() = computeTaskName("extract", "NativeDebugMetadata")

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractNativeDebugMetadataTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractNativeDebugMetadataTask::outputDir
            ).withName("out").on(NATIVE_DEBUG_METADATA)
        }

        override fun configure(task: ExtractNativeDebugMetadataTask) {
            super.configure(task)
            task.debugSymbolLevel = DebugSymbolLevel.FULL
        }
    }

    class SymbolTableCreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : CreationAction(componentProperties) {

        override val name: String
            get() = computeTaskName("extract", "NativeSymbolTables")

        override fun handleProvider(
            taskProvider: TaskProvider<ExtractNativeDebugMetadataTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractNativeDebugMetadataTask::outputDir
            ).withName("out").on(NATIVE_SYMBOL_TABLES)
        }

        override fun configure(task: ExtractNativeDebugMetadataTask) {
            super.configure(task)
            task.debugSymbolLevel = DebugSymbolLevel.SYMBOL_TABLE
        }
    }

}

/**
 * Delegate to extract debug metadata from native libraries
 */
@VisibleForTesting
class ExtractNativeDebugMetadataDelegate(
    val workers: WorkerExecutorFacade,
    val inputDir: File,
    val outputDir: File,
    private val objcopyExecutableMap: Map<Abi, File>,
    private val debugSymbolLevel: DebugSymbolLevel,
    val processExecutor: ProcessExecutor
) {
    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    fun run() {
        FileUtils.cleanOutputDir(outputDir)
        for (inputFile in FileUtils.getAllFiles(inputDir)) {
            if (!inputFile.name.endsWith(SdkConstants.DOT_NATIVE_LIBS, ignoreCase = true)) {
                continue
            }
            val outputFile: File
            val objcopyArgs: List<String>
            when (debugSymbolLevel) {
                DebugSymbolLevel.FULL -> {
                    outputFile =
                        File(outputDir, "${inputFile.parentFile.name}/${inputFile.name}$DOT_DBG")
                    objcopyArgs = listOf("--only-keep-debug")
                }
                DebugSymbolLevel.SYMBOL_TABLE -> {
                    outputFile =
                        File(outputDir, "${inputFile.parentFile.name}/${inputFile.name}$DOT_SYM")
                    objcopyArgs = listOf("--strip-debug")
                }
                DebugSymbolLevel.NONE ->
                    throw RuntimeException(
                        "NativeDebugMetadataMode.NONE not supported in ${this.javaClass.name}"
                    )
            }
            val objcopyExecutable = objcopyExecutableMap[Abi.getByName(inputFile.parentFile.name)]
            if (objcopyExecutable == null) {
                logger.warning(
                    "Unable to extract native debug metadata from ${inputFile.absolutePath} " +
                            "because unable to locate the objcopy executable for the " +
                            "${inputFile.parentFile.name} ABI."
                )
                continue
            }
            workers.submit(
                ExtractNativeDebugMetadataRunnable::class.java,
                ExtractNativeDebugMetadataRunnable.Params(
                    inputFile,
                    outputFile,
                    objcopyExecutable,
                    objcopyArgs,
                    processExecutor
                )
            )

        }
    }
}

/**
 * Runnable to extract debug metadata from a native library
 */
private class ExtractNativeDebugMetadataRunnable @Inject constructor(val params: Params): Runnable {

    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    override fun run() {
        FileUtils.mkdirs(params.outputFile.parentFile)

        val builder = ProcessInfoBuilder()
        builder.setExecutable(params.objcopyExecutable)
        builder.addArgs(params.objcopyArgs)
        builder.addArgs(
            params.inputFile.toString(),
            params.outputFile.toString()
        )
        val result =
            params.processExecutor.execute(
                builder.createProcess(),
                LoggedProcessOutputHandler(
                    LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))
                )
            )
        if (result.exitValue != 0) {
            logger.warning(
                "Unable to extract native debug metadata from ${params.inputFile.absolutePath} " +
                        "because of non-zero exit value from objcopy."
            )
        }
    }

    data class Params(
        val inputFile: File,
        val outputFile: File,
        val objcopyExecutable: File,
        val objcopyArgs: List<String>,
        val processExecutor: ProcessExecutor
    ): Serializable
}
