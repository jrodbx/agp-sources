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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FD_RES_VALUES
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
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

@CacheableTask
abstract class CompileLibraryResourcesTask : NewIncrementalTask() {

    @get:InputFiles
    @get:Incremental
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val inputDirectoriesAsAbsolute: ConfigurableFileCollection

    @get:InputFiles
    @get:Incremental
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectoriesAsRelative: ConfigurableFileCollection

    @get:Input
    abstract val pseudoLocalesEnabled: Property<Boolean>

    @get:Input
    abstract val relativeResourcePathsEnabled: Property<Boolean>

    @get:Input
    abstract val crunchPng: Property<Boolean>

    @get:Input
    abstract val excludeValuesFiles: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val partialRDirectory: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    override fun doTaskAction(inputChanges: InputChanges) {
        workerExecutor.noIsolation()
            .submit(CompileLibraryResourcesAction::class.java) { parameters ->
                parameters.initializeFromAndroidVariantTask(this)
                parameters.outputDirectory.set(outputDir)
                parameters.aapt2.set(aapt2)
                parameters.incremental.set(inputChanges.isIncremental)
                parameters.incrementalChanges.set(
                    if (inputChanges.isIncremental) {
                        inputChanges.getChangesInSerializableForm(getInputDirectories())
                    } else {
                        null
                    }
                )
                parameters.inputDirectories.from(getInputDirectories())
                parameters.partialRDirectory.set(partialRDirectory)
                parameters.pseudoLocalize.set(pseudoLocalesEnabled)
                parameters.crunchPng.set(crunchPng)
                parameters.excludeValues.set(excludeValuesFiles)
            }
    }

    private fun getInputDirectories(): ConfigurableFileCollection {
        return if (relativeResourcePathsEnabled.get()) {
            inputDirectoriesAsRelative
        } else {
            inputDirectoriesAsAbsolute
        }
    }

    protected abstract class CompileLibraryResourcesParams : ProfileAwareWorkAction.Parameters() {
        abstract val outputDirectory: DirectoryProperty

        @get:Nested
        abstract val aapt2: Property<Aapt2Input>
        abstract val incremental: Property<Boolean>
        abstract val incrementalChanges: Property<SerializableInputChanges>
        abstract val inputDirectories: ConfigurableFileCollection
        abstract val partialRDirectory: DirectoryProperty
        abstract val pseudoLocalize: Property<Boolean>
        abstract val crunchPng: Property<Boolean>
        abstract val excludeValues: Property<Boolean>
    }

    protected abstract class CompileLibraryResourcesAction :
        ProfileAwareWorkAction<CompileLibraryResourcesParams>() {

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

        /**
         * In the non-namespaced case, filter out the values directories,
         * as they have to go through the resources merging pipeline.
         */
        private fun includeDirectory(directory: File): Boolean {
            if (parameters.excludeValues.get()) {
                return !directory.name.startsWith(FD_RES_VALUES)
            }
            return true
        }

        private fun handleFullRun(processor: WorkerExecutorResourceCompilationService) {
            FileUtils.deleteDirectoryContents(parameters.outputDirectory.asFile.get())

            for (inputDirectory in parameters.inputDirectories) {
                if (!inputDirectory.isDirectory) {
                    continue
                }
                inputDirectory.listFiles()!!
                    .filter { it.isDirectory && includeDirectory(it) }
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
            val request = CompileResourceRequest(
                file,
                parameters.outputDirectory.asFile.get(),
                partialRFile = computePartialR(file),
                isPseudoLocalize = parameters.pseudoLocalize.get(),
                isPngCrunching = parameters.crunchPng.get()
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
            fileChanges.changes.filter { includeDirectory(it.file.parentFile) }
                .forEach { fileChange ->
                    handleModifiedFile(
                        fileChange.file,
                        fileChange.fileStatus,
                        compilationService
                    )
                }
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<CompileLibraryResourcesTask, ComponentCreationConfig>(
        creationConfig
    ) {
        override val name: String
            get() = computeTaskName("compile", "LibraryResources")
        override val type: Class<CompileLibraryResourcesTask>
            get() = CompileLibraryResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CompileLibraryResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CompileLibraryResourcesTask::outputDir
            ).withName("out").on(InternalArtifactType.COMPILED_LOCAL_RESOURCES)
        }

        override fun configure(
            task: CompileLibraryResourcesTask
        ) {
            super.configure(task)
            val services = creationConfig.services

            val packagedRes = creationConfig.artifacts.get(InternalArtifactType.PACKAGED_RES)
            val useRelativeInputDirectories =
                services.projectOptions[BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP] &&
                        services.projectOptions[BooleanOption.RELATIVE_COMPILE_LIB_RESOURCES]
            task.relativeResourcePathsEnabled.setDisallowChanges(useRelativeInputDirectories)
            if (useRelativeInputDirectories) {
                task.inputDirectoriesAsRelative.setFrom(
                    creationConfig.services.fileCollection(packagedRes)
                )
            } else {
                task.inputDirectoriesAsAbsolute.setFrom(
                    creationConfig.services.fileCollection(packagedRes)
                )
            }
            task.pseudoLocalesEnabled.setDisallowChanges(creationConfig
                .pseudoLocalesEnabled)

            task.crunchPng.setDisallowChanges(creationConfig.variantScope.isCrunchPngs)
            task.excludeValuesFiles.setDisallowChanges(true)
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.partialRDirectory.disallowChanges()
        }
    }


    class NamespacedCreationAction(
        override val name: String,
        private val inputDirectories: FileCollection,
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<CompileLibraryResourcesTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val type: Class<CompileLibraryResourcesTask>
            get() = CompileLibraryResourcesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CompileLibraryResourcesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.use(taskProvider)
                .wiredWith(CompileLibraryResourcesTask::partialRDirectory)
                .toAppendTo(InternalMultipleArtifactType.PARTIAL_R_FILES)

            creationConfig.artifacts.use(taskProvider)
                .wiredWith(CompileLibraryResourcesTask::outputDir)
                .toAppendTo(InternalMultipleArtifactType.RES_COMPILED_FLAT_FILES)
        }

        override fun configure(
            task: CompileLibraryResourcesTask
        ) {
            super.configure(task)
            val services = creationConfig.services
            val useRelativeInputDirectories =
                services.projectOptions[BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP] &&
                        services.projectOptions[BooleanOption.RELATIVE_COMPILE_LIB_RESOURCES]
            if (useRelativeInputDirectories) {
                task.inputDirectoriesAsRelative.from(inputDirectories)
            } else {
                task.inputDirectoriesAsAbsolute.from(inputDirectories)
            }
            task.inputDirectoriesAsAbsolute.from(inputDirectories)
            task.crunchPng.setDisallowChanges(creationConfig.variantScope.isCrunchPngs)
            task.pseudoLocalesEnabled.set(creationConfig.variantDslInfo.isPseudoLocalesEnabled)
            task.relativeResourcePathsEnabled.setDisallowChanges(useRelativeInputDirectories)
            task.excludeValuesFiles.set(false)
            task.dependsOn(creationConfig.taskContainer.resourceGenTask)

            creationConfig.services.initializeAapt2Input(task.aapt2)
        }
    }
}
