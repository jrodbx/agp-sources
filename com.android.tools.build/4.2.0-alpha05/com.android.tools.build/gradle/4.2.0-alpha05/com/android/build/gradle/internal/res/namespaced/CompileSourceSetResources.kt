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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.use
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
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

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val partialRDirectory: DirectoryProperty

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
        workerExecutor.noIsolation().submit(WorkAction::class.java) { parameters ->
            parameters.initializeFromAndroidVariantTask(this)
            parameters.aapt2.set(aapt2)
            parameters.compileRequests.set(requests)
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        val requests = mutableListOf<CompileResourceRequest>()
        val deletes = mutableListOf<File>()
        val outDirectory = outputDirectory.get().asFile
        val partialRDirectory = partialRDirectory.get().asFile
        /** Only consider at files in first level subdirectories of the input directory */
        changedInputs.forEach { file, status ->
            if (willCompile(file) && (inputDirectories.any { it == file.parentFile.parentFile })) {
                when (status) {
                    FileStatus.NEW, FileStatus.CHANGED -> {
                        requests.add(compileRequest(file))
                    }
                    FileStatus.REMOVED -> {
                        val compiledName = Aapt2RenamingConventions.compilationRename(file)
                        deletes.add(outDirectory.resolve(compiledName))
                        deletes.add(partialRDirectory.resolve("$compiledName-R.txt"))
                    }
                }
            }
        }
        workerExecutor.noIsolation().submit(WorkAction::class.java) { parameters ->
            parameters.initializeFromAndroidVariantTask(this)
            parameters.aapt2.set(aapt2)
            parameters.compileRequests.set(requests)
            parameters.deleteRequests.set(deletes)
        }
    }

    protected abstract class CompileParameters : ProfileAwareWorkAction.Parameters() {
        @get:Nested
        abstract val aapt2: Property<Aapt2Input>
        abstract val compileRequests: ListProperty<CompileResourceRequest>
        abstract val deleteRequests: ListProperty<File>
    }

    protected abstract class WorkAction : ProfileAwareWorkAction<CompileParameters>() {
        override fun run() {
            parameters.aapt2.get().use(parameters) { processor ->
                for (request in parameters.compileRequests.get()) {
                    processor.submit { aapt2 ->
                        aapt2.compile(request, processor.iLogger)
                    }
                }
                if (parameters.deleteRequests.isPresent) {
                    for (toDelete in parameters.deleteRequests.get()) {
                        Files.delete(toDelete.toPath())
                    }
                }
            }
        }
    }

    private fun compileRequest(file: File, inputDirectoryName: String = file.parentFile.name) =
        CompileResourceRequest(
            inputFile = file,
            outputDirectory = outputDirectory.get().asFile,
            inputDirectoryName = inputDirectoryName,
            isPseudoLocalize = isPseudoLocalize,
            isPngCrunching = isPngCrunching,
            partialRFile = getPartialR(file)
        )

    private fun getPartialR(file: File) =
        File(
            partialRDirectory.get().asFile,
            "${Aapt2RenamingConventions.compilationRename(file)}-R.txt"
        )

    // TODO: filtering using same logic as DataSet.isIgnored.
    private fun willCompile(file: File) = !file.name.startsWith(".") && !file.isDirectory

    class CreationAction(
        override val name: String,
        private val inputDirectories: FileCollection,
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<CompileSourceSetResources, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val type: Class<CompileSourceSetResources>
            get() = CompileSourceSetResources::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CompileSourceSetResources>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.use(taskProvider)
                .wiredWith(CompileSourceSetResources::partialRDirectory)
                .toAppendTo(InternalMultipleArtifactType.PARTIAL_R_FILES)

            creationConfig.artifacts.use(taskProvider)
                .wiredWith(CompileSourceSetResources::outputDirectory)
                .toAppendTo(InternalMultipleArtifactType.RES_COMPILED_FLAT_FILES)
        }

        override fun configure(
            task: CompileSourceSetResources
        ) {
            super.configure(task)

            task.inputDirectories = inputDirectories
            task.isPngCrunching = creationConfig.variantScope.isCrunchPngs
            task.isPseudoLocalize =
                creationConfig.variantDslInfo.isPseudoLocalesEnabled

            task.dependsOn(creationConfig.taskContainer.resourceGenTask)

            creationConfig.services.initializeAapt2Input(task.aapt2)

        }
    }
}
