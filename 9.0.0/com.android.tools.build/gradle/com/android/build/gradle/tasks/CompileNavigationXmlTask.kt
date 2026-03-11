/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.SdkConstants.FD_RES_NAVIGATION
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.ResourcePathEncoding
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Compile navigation XMLs for APK taken from application and libraries.
 * Simplified version of MergeResource task.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.COMPILATION])
abstract class CompileNavigationXmlTask : NewIncrementalTask() {

    @get:InputFiles
    @get:Incremental
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ConfigurableFileCollection

    @get:Input
    abstract val pseudoLocalesEnabled: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val partialRDirectory: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    override fun doTaskAction(inputChanges: InputChanges) {
        workerExecutor.noIsolation()
            .submit(CompileNavigationResourcesAction::class.java) { parameters ->
                parameters.initializeFromBaseTask(this)
                parameters.outputDirectory.set(outputDir)
                parameters.aapt2.set(aapt2)
                parameters.incremental.set(inputChanges.isIncremental)
                parameters.incrementalChanges.set(
                    if (inputChanges.isIncremental) {
                        inputChanges.getChangesInSerializableForm(inputDirectories)
                    } else {
                        null
                    }
                )
                parameters.inputDirectories.from(inputDirectories)
                parameters.partialRDirectory.set(partialRDirectory)
                parameters.pseudoLocalize.set(pseudoLocalesEnabled)
            }
    }

    protected abstract class CompileNavigationResourcesParams : ProfileAwareWorkAction.Parameters() {
        abstract val outputDirectory: DirectoryProperty

        @get:Nested
        abstract val aapt2: Property<Aapt2Input>
        abstract val incremental: Property<Boolean>
        abstract val incrementalChanges: Property<SerializableInputChanges>
        abstract val inputDirectories: ConfigurableFileCollection
        abstract val partialRDirectory: DirectoryProperty
        abstract val pseudoLocalize: Property<Boolean>
    }

    protected abstract class CompileNavigationResourcesAction :
        ProfileAwareWorkAction<CompileNavigationResourcesParams>() {

        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        override fun run() {
            WorkerExecutorResourceCompilationService(
                projectPath = parameters.projectPath,
                taskOwner = parameters.taskOwner.get(),
                workerExecutor = workerExecutor,
                analyticsService = parameters.analyticsService,
                aapt2Input = parameters.aapt2.get()
            ).use { compilationService ->
                if (parameters.incremental.get()) {
                    handleIncrementalChanges(
                        parameters.incrementalChanges.get(),
                        compilationService
                    )
                } else {
                    handleFullRun(compilationService)
                }
            }
        }

        private fun handleFullRun(processor: WorkerExecutorResourceCompilationService) {
            FileUtils.deleteDirectoryContents(parameters.outputDirectory.asFile.get())

            for (inputDirectory in parameters.inputDirectories) {
                if (!inputDirectory.isDirectory) {
                    continue
                }
                inputDirectory.listFiles()!!
                    .filter { it.isDirectory }
                    .forEach { dir ->
                        dir.listFiles()!!.forEach { file ->
                            submitFileToBeCompiled(file, processor)
                        }
                    }
            }
        }

        /**
         *  Given an input resource file return the partial R file to generate,
         *  or null if partial R generation is not enabled
         */
        private fun computePartialR(file: File): File? {
            val partialRDirectory = parameters.partialRDirectory.orNull?.asFile ?: return null
            val compiledName = Aapt2RenamingConventions.compilationRename(file)
            return partialRDirectory.resolve(partialRDirectory.resolve("$compiledName-R.txt"))
        }

        private fun submitFileToBeCompiled(
            file: File,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            val dir = File(parameters.outputDirectory.asFile.get(), FD_RES_NAVIGATION)
            dir.mkdir()
            val request = CompileResourceRequest(
                file,
                parameters.outputDirectory.asFile.get(),
                partialRFile = computePartialR(file),
                isPseudoLocalize = parameters.pseudoLocalize.get(),
                isPngCrunching = false,
                resourcePathEncoding = ResourcePathEncoding.AbsoluteNotRelocatable,
            )
            compilationService.submitCompile(request)
        }

        private fun handleModifiedFile(
            file: File,
            changeType: FileStatus,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            if (changeType == FileStatus.CHANGED || changeType == FileStatus.REMOVED) {
                FileUtils.deleteIfExists(
                    File(
                        parameters.outputDirectory.asFile.get(),
                        Aapt2RenamingConventions.compilationRename(file)
                    )
                )
                computePartialR(file)?.let { partialRFile -> FileUtils.delete(partialRFile) }

            }
            if (changeType == FileStatus.NEW || changeType == FileStatus.CHANGED) {
                submitFileToBeCompiled(file, compilationService)
            }
        }

        private fun handleIncrementalChanges(
            fileChanges: SerializableInputChanges,
            compilationService: WorkerExecutorResourceCompilationService
        ) {
            fileChanges.changes
                .forEach { fileChange ->
                    handleModifiedFile(
                        fileChange.file,
                        fileChange.fileStatus,
                        compilationService
                    )
                }
        }
    }

    open class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<CompileNavigationXmlTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = creationConfig.computeTaskNameInternal(
                "compile",
                "NavigationResources"
            )
        override val type: Class<CompileNavigationXmlTask>
            get() = CompileNavigationXmlTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<CompileNavigationXmlTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileNavigationXmlTask::outputDir
            ).on(InternalArtifactType.COMPILED_NAVIGATION_RES)
        }

        override fun configure(task: CompileNavigationXmlTask) {
            super.configure(task)

            val navigationResources =
                creationConfig.artifacts.get(InternalArtifactType.UPDATED_NAVIGATION_XML)
            task.inputDirectories.setFrom(
                creationConfig.services.fileCollection(navigationResources),
            )
            task.pseudoLocalesEnabled.setDisallowChanges(
                creationConfig.androidResourcesCreationConfig!!.pseudoLocalesEnabled
            )

            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            task.partialRDirectory.disallowChanges()
        }
    }
}
