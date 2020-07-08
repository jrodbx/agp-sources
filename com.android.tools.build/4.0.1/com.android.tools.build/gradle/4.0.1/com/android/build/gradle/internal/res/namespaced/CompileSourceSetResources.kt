/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.MultipleArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Task to compile a single sourceset's resources in to AAPT intermediate format.
 *
 * The link step handles resource overlays.
 */
abstract class CompileSourceSetResources : IncrementalTask() {
    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    lateinit var inputDirectories: FileCollection
        private set
    @get:Input
    var isPngCrunching: Boolean = false
        private set
    @get:Input
    var isPseudoLocalize: Boolean = false
        private set
    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val partialRDirectory: DirectoryProperty

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override val incremental: Boolean
        get() = true

    override fun doFullTaskAction() {
        val requests = mutableListOf<CompileResourceRequest>()
        val addedFiles = mutableMapOf<Path, Path>()
        for (inputDirectory in inputDirectories) {
            if (!inputDirectory.isDirectory) {
                continue
            }

            /** Only look at files in first level subdirectories of the input directory */
            Files.list(inputDirectory.toPath()).use { fstLevel ->
                fstLevel.forEach { subDir ->
                    if (Files.isDirectory(subDir)) {
                        Files.list(subDir).use {
                            it.forEach { resFile ->
                                if (Files.isRegularFile(resFile)) {
                                    val relativePath = inputDirectory.toPath().relativize(resFile)
                                    if (addedFiles.contains(relativePath)) {
                                        throw RuntimeException(
                                                "Duplicated resource '$relativePath' found in a " +
                                                        "source set:\n" +
                                                        "    - ${addedFiles[relativePath]}\n" +
                                                        "    - $resFile"
                                        )
                                    }
                                    requests.add(compileRequest(resFile.toFile()))
                                    addedFiles[relativePath] = resFile
                                }
                            }
                        }
                    }
                }
            }
        }

        getWorkerFacadeWithWorkers().use {
            submit(requests, it)
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        val requests = mutableListOf<CompileResourceRequest>()
        val deletes = mutableListOf<File>()
        /** Only consider at files in first level subdirectories of the input directory */
        changedInputs.forEach { file, status ->
            if (willCompile(file) && (inputDirectories.any { it == file.parentFile.parentFile })) {
                when (status) {
                    FileStatus.NEW, FileStatus.CHANGED -> {
                        requests.add(compileRequest(file))
                    }
                    FileStatus.REMOVED -> {
                        deletes.add(file)
                    }
                }
            }
        }
        getWorkerFacadeWithWorkers().use {
            if (!deletes.isEmpty()) {
                it.submit(
                    Aapt2CompileDeleteRunnable::class.java,
                    Aapt2CompileDeleteRunnable.Params(
                        outputDirectory = outputDirectory.get().asFile,
                        deletedInputs = deletes,
                        partialRDirectory = partialRDirectory.get().asFile
                    )
                )
            }
            submit(requests, it)
        }
    }

    private fun compileRequest(file: File, inputDirectoryName: String = file.parentFile.name) =
            CompileResourceRequest(
                    inputFile = file,
                    outputDirectory = outputDirectory.get().asFile,
                    inputDirectoryName = inputDirectoryName,
                    isPseudoLocalize = isPseudoLocalize,
                    isPngCrunching = isPngCrunching,
                    partialRFile = getPartialR(file))

    private fun getPartialR(file: File) =
        File(partialRDirectory.get().asFile, "${Aapt2RenamingConventions.compilationRename(file)}-R.txt")

    private fun submit(requests: List<CompileResourceRequest>, workerFacade: WorkerExecutorFacade) {
        if (requests.isEmpty()) {
            return
        }
        val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
            aapt2FromMaven = aapt2FromMaven.singleFile,
            logger = LoggerWrapper(logger)
        )
        for (request in requests) {
            workerFacade.submit(
                Aapt2CompileRunnable::class.java,
                Aapt2CompileRunnable.Params(
                    aapt2ServiceKey,
                    listOf(request),
                    errorFormatMode
                )
            )
        }
    }

    // TODO: filtering using same logic as DataSet.isIgnored.
    private fun willCompile(file: File) = !file.name.startsWith(".") && !file.isDirectory

    class CreationAction(
        override val name: String,
        private val inputDirectories: FileCollection,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<CompileSourceSetResources>(variantScope) {

        override val type: Class<CompileSourceSetResources>
            get() = CompileSourceSetResources::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CompileSourceSetResources>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.getOperations().append(
                taskProvider,
                CompileSourceSetResources::partialRDirectory
            ).on(MultipleArtifactType.PARTIAL_R_FILES)

            variantScope.artifacts.getOperations().append(
                taskProvider,
                CompileSourceSetResources::outputDirectory
            ).on(MultipleArtifactType.RES_COMPILED_FLAT_FILES)
        }

        override fun configure(task: CompileSourceSetResources) {
            super.configure(task)

            task.inputDirectories = inputDirectories
            task.isPngCrunching = variantScope.isCrunchPngs
            task.isPseudoLocalize =
                    variantScope.variantData.variantDslInfo.isPseudoLocalesEnabled

            val (aapt2FromMaven,aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.dependsOn(variantScope.taskContainer.resourceGenTask)

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }
}
