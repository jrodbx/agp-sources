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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.AsyncResourceProcessor
import com.android.build.gradle.internal.services.use
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

@CacheableTask
abstract class CompileLibraryResourcesTask : NewIncrementalTask() {

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

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

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
                        inputChanges.getChangesInSerializableForm(mergedLibraryResourcesDir)
                    } else {
                        null
                    }
                )
                parameters.mergedLibraryResourceDirectory.set(mergedLibraryResourcesDir)
                parameters.pseudoLocalize.set(pseudoLocalesEnabled)
                parameters.crunchPng.set(crunchPng)
            }
    }

    protected abstract class CompileLibraryResourcesParams : ProfileAwareWorkAction.Parameters() {
        abstract val outputDirectory: DirectoryProperty

        @get:Nested
        abstract val aapt2: Property<Aapt2Input>
        abstract val incremental: Property<Boolean>
        abstract val incrementalChanges: Property<SerializableInputChanges>
        abstract val mergedLibraryResourceDirectory: DirectoryProperty
        abstract val pseudoLocalize: Property<Boolean>
        abstract val crunchPng: Property<Boolean>
    }

    protected abstract class CompileLibraryResourcesAction :
        ProfileAwareWorkAction<CompileLibraryResourcesParams>() {
        override fun run() {

            parameters.aapt2.get().use(parameters) { processor ->
                if (parameters.incremental.get()) {
                    handleIncrementalChanges(parameters.incrementalChanges.get(), processor)
                } else {
                    handleFullRun(processor)
                }
            }
        }

        private fun handleFullRun(processor: AsyncResourceProcessor<Aapt2>) {
            FileUtils.deleteDirectoryContents(parameters.outputDirectory.asFile.get())
            // filter out the values files as they have to go through the resources merging
            // pipeline.
            parameters.mergedLibraryResourceDirectory.asFile.get().listFiles()!!
                .filter { it.isDirectory && !it.name.startsWith(FD_RES_VALUES) }
                .forEach { dir ->
                    dir.listFiles()!!.forEach { file ->
                        submitFileToBeCompiled(file, processor)
                    }
                }
        }

        private fun submitFileToBeCompiled(
            file: File,
            processor: AsyncResourceProcessor<Aapt2>
        ) {
            val request = CompileResourceRequest(
                file,
                parameters.outputDirectory.asFile.get(),
                isPseudoLocalize = parameters.pseudoLocalize.get(),
                isPngCrunching = parameters.crunchPng.get()
            )
            processor.submit { aapt2 ->
                aapt2.compile(request, processor.iLogger)
            }
        }

        private fun handleModifiedFile(
            file: File,
            changeType: FileStatus,
            processor: AsyncResourceProcessor<Aapt2>
        ) {
            if (changeType == FileStatus.CHANGED || changeType == FileStatus.REMOVED) {
                FileUtils.deleteIfExists(
                    File(
                        parameters.outputDirectory.asFile.get(),
                        Aapt2RenamingConventions.compilationRename(file)
                    )
                )
            }
            if (changeType == FileStatus.NEW || changeType == FileStatus.CHANGED) {
                submitFileToBeCompiled(file, processor)
            }
        }

        private fun handleIncrementalChanges(
            fileChanges: SerializableInputChanges,
            processor: AsyncResourceProcessor<Aapt2>
        ) {
            fileChanges.changes.filter {
                !it.file.parentFile.name.startsWith(FD_RES_VALUES)
            }
                .forEach { fileChange ->
                    handleModifiedFile(
                        fileChange.file,
                        fileChange.fileStatus,
                        processor
                    )
                }
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<CompileLibraryResourcesTask, ComponentPropertiesImpl>(
        componentProperties
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

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.mergedLibraryResourcesDir
            )

            task.pseudoLocalesEnabled = creationConfig
                .variantDslInfo
                .isPseudoLocalesEnabled

            task.crunchPng = creationConfig.variantScope.isCrunchPngs

            creationConfig.services.initializeAapt2Input(task.aapt2)

        }
    }
}