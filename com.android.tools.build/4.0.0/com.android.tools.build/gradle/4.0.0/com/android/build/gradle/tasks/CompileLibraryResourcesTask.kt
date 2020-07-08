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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.aapt.SharedExecutorResourceCompilationService
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2WorkersBuildService
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.services.getAapt2WorkersBuildService
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.Serializable
import javax.inject.Inject

@CacheableTask
abstract class CompileLibraryResourcesTask : NewIncrementalTask() {

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    @get:InputFiles
    @get:Incremental
    @get:PathSensitive(PathSensitivity.ABSOLUTE) // TODO(b/141301405): use relative paths
    abstract val mergedLibraryResourcesDir: DirectoryProperty

    @get:Input
    var pseudoLocalesEnabled: Boolean = false
        private set

    @get:Input
    var crunchPng: Boolean = true
        private set

    @get:Input
    lateinit var aapt2Version: String
        private set

    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    var useJvmResourceCompiler: Boolean = false
        private set

    @get:Internal
    abstract val aapt2WorkersBuildService: Property<Aapt2WorkersBuildService>

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    override fun doTaskAction(inputChanges: InputChanges) {
        val aapt2ServiceKey = aapt2DaemonBuildService.get().registerAaptService(
            aapt2FromMaven.singleFile, LoggerWrapper(logger)
        )

        getWorkerFacadeWithWorkers().use { workers ->
            val requests = ImmutableList.builder<CompileResourceRequest>()

            if (inputChanges.isIncremental) {
                doIncrementalTaskAction(
                    inputChanges.getFileChanges(mergedLibraryResourcesDir),
                    requests
                )
            } else {
                // do full task action
                FileUtils.deleteDirectoryContents(outputDir.asFile.get())

                // filter out the values files as they have to go through the resources merging
                // pipeline.
                mergedLibraryResourcesDir.asFile.get().listFiles()!!
                    .filter { it.isDirectory && !it.name.startsWith(FD_RES_VALUES) }
                    .forEach { dir ->
                        dir.listFiles()!!.forEach { file ->
                            submitFileToBeCompiled(file, requests)
                        }
                    }
            }

            workers.submit(
                CompileLibraryResourcesRunnable::class.java,
                CompileLibraryResourcesParams(
                    projectName,
                    path,
                    aapt2ServiceKey,
                    aapt2WorkersBuildService.get().getWorkersServiceKey(),
                    errorFormatMode,
                    requests.build(),
                    useJvmResourceCompiler
                )
            )
        }
    }

    private fun submitFileToBeCompiled(
        file: File,
        requests: ImmutableList.Builder<CompileResourceRequest>
    ) {
        requests.add(
            CompileResourceRequest(
                file,
                outputDir.asFile.get(),
                isPseudoLocalize = pseudoLocalesEnabled,
                isPngCrunching = crunchPng
            )
        )
    }

    private fun handleModifiedFile(
        file: File,
        changeType: ChangeType,
        requests: ImmutableList.Builder<CompileResourceRequest>
    ) {
        if (changeType == ChangeType.MODIFIED || changeType == ChangeType.REMOVED) {
            FileUtils.deleteIfExists(
                File(
                    outputDir.asFile.get(),
                    Aapt2RenamingConventions.compilationRename(file)
                )
            )
        }
        if (changeType == ChangeType.ADDED || changeType == ChangeType.MODIFIED) {
            submitFileToBeCompiled(file, requests)
        }
    }

    private fun doIncrementalTaskAction(
        fileChanges: Iterable<FileChange>,
        requests: ImmutableList.Builder<CompileResourceRequest>
    ) {
        fileChanges.filter {
            it.fileType == FileType.FILE &&
                    !it.file.parentFile.name.startsWith(FD_RES_VALUES)
        }
            .forEach { fileChange ->
                handleModifiedFile(
                    fileChange.file,
                    fileChange.changeType,
                    requests
                )
            }
    }

    private data class CompileLibraryResourcesParams(
        val projectName: String,
        val owner: String,
        val aapt2ServiceKey: Aapt2DaemonServiceKey,
        val aapt2WorkersBuildServiceKey: WorkerActionServiceRegistry.ServiceKey<Aapt2WorkersBuildService>,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val requests: List<CompileResourceRequest>,
        val useJvmResourceCompiler: Boolean
    ) : Serializable

    private class CompileLibraryResourcesRunnable
    @Inject constructor(private val params: CompileLibraryResourcesParams) : Runnable {
        override fun run() {
            SharedExecutorResourceCompilationService(
                params.projectName,
                params.owner,
                params.aapt2WorkersBuildServiceKey,
                params.aapt2ServiceKey,
                params.errorFormatMode,
                params.useJvmResourceCompiler
            ).use {
                it.submitCompile(params.requests)
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<CompileLibraryResourcesTask>(variantScope) {
        override val name: String
            get() = variantScope.getTaskName("compile", "LibraryResources")
        override val type: Class<CompileLibraryResourcesTask>
            get() = CompileLibraryResourcesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out CompileLibraryResourcesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.COMPILED_LOCAL_RESOURCES,
                taskProvider,
                CompileLibraryResourcesTask::outputDir
            )
        }

        override fun configure(task: CompileLibraryResourcesTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.mergedLibraryResourcesDir
            )

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            task.pseudoLocalesEnabled = variantScope
                .variantData
                .variantDslInfo
                .isPseudoLocalesEnabled

            task.crunchPng = variantScope.isCrunchPngs

            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)

            task.useJvmResourceCompiler =
              variantScope.globalScope.projectOptions[BooleanOption.ENABLE_JVM_RESOURCE_COMPILER]
            task.aapt2WorkersBuildService.set(getAapt2WorkersBuildService(task.project))
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }
}