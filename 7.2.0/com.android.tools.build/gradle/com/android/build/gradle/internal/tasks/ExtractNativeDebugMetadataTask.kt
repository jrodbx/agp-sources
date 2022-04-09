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
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.NdkHandlerInput
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_DEBUG_METADATA
import com.android.build.gradle.internal.scope.InternalArtifactType.NATIVE_SYMBOL_TABLES
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkerExecutor
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

    // The stripped native libs are an input to this task because we only want to keep the native
    // debug metadata files which actually contain native debug metadata; we delete native debug
    // metadata files that are the same size as the corresponding stripped native libraries.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val strippedNativeLibs: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Nested
    abstract val ndkHandlerInput: NdkHandlerInput

    @get:Input
    lateinit var debugSymbolLevel: DebugSymbolLevel
        private set

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    // We need this inputFiles property because SkipWhenEmpty doesn't work for inputDir because it's
    // a DirectoryProperty
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputFiles: FileTree
        get() = inputDir.asFileTree

    private val maxWorkerCount = project.gradle.startParameter.maxWorkerCount

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ExtractNativeDebugMetadataWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputDir.set(inputDir)
            it.strippedNativeLibs.set(strippedNativeLibs)
            it.outputDir.set(outputDir)
            it.objcopyExecutableMap.set(sdkBuildService.flatMap { buildService ->
                buildService.versionedNdkHandler(ndkHandlerInput).objcopyExecutableMapProvider
            })
            it.debugSymbolLevel.set(debugSymbolLevel)
            it.maxWorkerCount.set(maxWorkerCount)
        }
    }

    abstract class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<ExtractNativeDebugMetadataTask, VariantCreationConfig>(
        creationConfig
    ) {

        override val type: Class<ExtractNativeDebugMetadataTask>
            get() = ExtractNativeDebugMetadataTask::class.java

        override fun configure(task: ExtractNativeDebugMetadataTask) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(MERGED_NATIVE_LIBS, task.inputDir)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.STRIPPED_NATIVE_LIBS,
                task.strippedNativeLibs
            )
            task.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.ndkHandlerInput.initialize(creationConfig)
        }
    }

    class FullCreationAction(
        creationConfig: VariantCreationConfig
    ) : CreationAction(creationConfig) {

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
        creationConfig: VariantCreationConfig
    ) : CreationAction(creationConfig) {

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
abstract class ExtractNativeDebugMetadataWorkAction :
    ProfileAwareWorkAction<ExtractNativeDebugMetadataWorkAction.Parameters>() {

    abstract class Parameters: ProfileAwareWorkAction.Parameters() {
        abstract val inputDir: DirectoryProperty
        abstract val strippedNativeLibs: DirectoryProperty
        abstract val outputDir: DirectoryProperty
        abstract val objcopyExecutableMap: MapProperty<Abi, File>
        abstract val debugSymbolLevel: Property<DebugSymbolLevel>
        abstract val maxWorkerCount: Property<Int>
    }

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    override fun run() {
        val outputDir = parameters.outputDir.asFile.get()
        FileUtils.cleanOutputDir(outputDir)

        val inputDir = parameters.inputDir.asFile.get()
        val strippedNativeLibs = parameters.strippedNativeLibs.asFile.get()

        val allRequests = mutableListOf<ExtractNativeDebugMetadataRunnable.SingleRequest>()
        for (inputFile in FileUtils.getAllFiles(inputDir)) {
            if (!inputFile.name.endsWith(SdkConstants.DOT_NATIVE_LIBS, ignoreCase = true)) {
                continue
            }
            val outputFile: File
            val objcopyArgs: List<String>
            when (parameters.debugSymbolLevel.get()) {
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
            val objcopyExecutable = parameters.objcopyExecutableMap.get()[Abi.getByName(inputFile.parentFile.name)]
            if (objcopyExecutable == null) {
                logger.warning(
                    "Unable to extract native debug metadata from ${inputFile.absolutePath} " +
                            "because unable to locate the objcopy executable for the " +
                            "${inputFile.parentFile.name} ABI."
                )
                continue
            }
            val strippedNativeLib =
                File(strippedNativeLibs, "lib/${inputFile.parentFile.name}/${inputFile.name}")
            allRequests.add(
                ExtractNativeDebugMetadataRunnable.SingleRequest(
                    inputFile,
                    strippedNativeLib,
                    outputFile,
                    objcopyExecutable,
                    objcopyArgs
                )
            )
        }

        // split them into maxWorkersCount buckets
        var ord = 0
        allRequests.groupBy { (ord++) % parameters.maxWorkerCount.get() }.values.forEach { requests ->
            if (requests.isNotEmpty()) {
                workerExecutor.noIsolation()
                    .submit(ExtractNativeDebugMetadataRunnable::class.java) {
                        it.initializeFromProfileAwareWorkAction(parameters)
                        it.requests.set(requests)
                    }
            }
        }
    }
}

/**
 * Runnable to extract debug metadata from a native library
 */
abstract class ExtractNativeDebugMetadataRunnable : ProfileAwareWorkAction<ExtractNativeDebugMetadataRunnable.Params>() {

    private val logger : LoggerWrapper
        get() = LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun run() {
        val processExecutor = GradleProcessExecutor(execOperations::exec)
        parameters.requests.get().forEach {
            processSingle(
                it.inputFile,
                it.strippedFile,
                it.outputFile,
                it.objcopyExecutable,
                it.objcopyArgs,
                processExecutor)
        }
    }

    private fun processSingle(
        inputFile: File,
        strippedFile: File,
        outputFile: File,
        objcopyExecutable: File,
        objcopyArgs: List<String>,
        processExecutor: GradleProcessExecutor
    ) {
        FileUtils.mkdirs(outputFile.parentFile)

        val builder = ProcessInfoBuilder()
        builder.setExecutable(objcopyExecutable)
        builder.addArgs(objcopyArgs)
        builder.addArgs(
            inputFile.toString(),
            outputFile.toString()
        )
        val result =
            processExecutor.execute(
                builder.createProcess(),
                LoggedProcessOutputHandler(
                    LoggerWrapper(Logging.getLogger(ExtractNativeDebugMetadataTask::class.java))
                )
            )
        if (result.exitValue != 0) {
            logger.warning(
                "Unable to extract native debug metadata from ${inputFile.absolutePath} " +
                        "because of non-zero exit value from objcopy."
            )
        }
        // We delete a native debug metadata file that is the same size as the corresponding
        // stripped native library, because it doesn't contain any extra information.
        if (outputFile.length() == strippedFile.length()) {
            logger.info(
                "Unable to extract native debug metadata from ${inputFile.absolutePath} " +
                        "because the native debug metadata has already been stripped."
            )
            FileUtils.deleteIfExists(outputFile)
        }
    }

    data class SingleRequest(
        val inputFile: File,
        val strippedFile: File,
        val outputFile: File,
        val objcopyExecutable: File,
        val objcopyArgs: List<String>
    ): Serializable

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val requests: ListProperty<SingleRequest>
    }
}
