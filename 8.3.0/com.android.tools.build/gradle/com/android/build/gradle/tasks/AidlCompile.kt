/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.compiler.AidlProcessor
import com.android.builder.internal.compiler.DirectoryWalker
import com.android.builder.internal.compiler.DirectoryWalker.FileAction
import com.android.builder.internal.incremental.DependencyData
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.nio.file.Path
import javax.inject.Inject

/**
 * Task to compile aidl files. Supports incremental update.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.AIDL, secondaryTaskCategories = [TaskCategory.COMPILATION])
abstract class AidlCompile : NonIncrementalTask() {
    @get:Input
    @get:Optional
    var packagedList: Collection<String>? = null
        private set

    @get:Internal
    abstract val sourceDirs: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var importDirs: FileCollection
        private set

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    fun getAidlFrameworkProvider(): Provider<File> =
        buildTools.aidlFrameworkProvider()

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: Property<FileTree>

    @get:OutputDirectory
    abstract val sourceOutputDir: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val packagedDir: DirectoryProperty

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput

    private class DepFileProcessor : DependencyFileProcessor {
        override fun processFile(dependencyFile: File): DependencyData? {
            return DependencyData.parseDependencyFile(dependencyFile)
        }
    }

    override fun doTaskAction() {
        // this is full run, clean the previous output'
        val aidlExecutable = buildTools
            .aidlExecutableProvider()
            .get()
            .absoluteFile
        val frameworkLocation = getAidlFrameworkProvider().get().absoluteFile
        val destinationDir = sourceOutputDir.get().asFile
        val parcelableDir = packagedDir.orNull
        FileUtils.cleanOutputDir(destinationDir)
        if (parcelableDir != null) {
            FileUtils.cleanOutputDir(parcelableDir.asFile)
        }

        val sourceFolders = sourceDirs.get()
        val importFolders = importDirs.files

        val fullImportList = sourceFolders.map { it.asFile } + importFolders

        aidlCompileDelegate(
            workerExecutor,
            aidlExecutable,
            frameworkLocation,
            destinationDir,
            parcelableDir?.asFile,
            packagedList,
            sourceFolders.map { it.asFile },
            fullImportList,
            this
        )
    }

    class CreationAction(
        creationConfig: ConsumableCreationConfig
    ) : VariantTaskCreationAction<AidlCompile, ConsumableCreationConfig>(
        creationConfig
    ) {

        override val name: String = computeTaskName("compile", "Aidl")

        override val type: Class<AidlCompile> = AidlCompile::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<AidlCompile>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.aidlCompileTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                AidlCompile::sourceOutputDir
            ).withName("out").on(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR)


            if (creationConfig.componentType.isAar) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    AidlCompile::packagedDir
                ).withName("out").on(InternalArtifactType.AIDL_PARCELABLE)
            }
        }

        override fun configure(
            task: AidlCompile
        ) {
            super.configure(task)
            val services = creationConfig.services

            creationConfig.sources.aidl {
                task.sourceDirs.setDisallowChanges(it.all)
                // This is because aidl may be in the same folder as Java and we want to restrict to
                // .aidl files and not java files.
                task.sourceFiles.setDisallowChanges(services.fileCollection(task.sourceDirs).asFileTree.matching(PATTERN_SET))
            }

            task.importDirs = creationConfig.variantDependencies.getArtifactFileCollection(COMPILE_CLASSPATH, ALL, AIDL)

            if (creationConfig.componentType.isAar) {
                task.packagedList = creationConfig.global.aidlPackagedList
            }
            task.buildTools.initialize(creationConfig)
        }
    }

    internal class ProcessingRequest(val root: File, val file: File) : Serializable

    abstract class AidlCompileRunnable : ProfileAwareWorkAction<AidlCompileRunnable.Params>() {

        abstract class Params: Parameters() {
            abstract val aidlExecutable: RegularFileProperty
            abstract val frameworkLocation: DirectoryProperty
            abstract val importFolders: ConfigurableFileCollection
            abstract val sourceOutputDir: DirectoryProperty
            abstract val packagedOutputDir: DirectoryProperty
            abstract val packagedList: ListProperty<String>
            abstract val dir: Property<File>
        }

        @get:Inject
        abstract val execOperations: ExecOperations

        override fun run() {
            // Collect all aidl files in the directory then process them
            val processingRequests = mutableListOf<ProcessingRequest>()

            val collector =
                FileAction { root: Path, file: Path ->
                    processingRequests.add(ProcessingRequest(root.toFile(), file.toFile()))
                }

            try {
                DirectoryWalker.builder()
                    .root(parameters.dir.get().toPath())
                    .extensions("aidl")
                    .action(collector)
                    .build()
                    .walk()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

            val depFileProcessor = DepFileProcessor()
            val executor = GradleProcessExecutor(execOperations::exec)
            val logger = LoggedProcessOutputHandler(
                LoggerWrapper.getLogger(AidlCompileRunnable::class.java))

            for (request in processingRequests) {
                AidlProcessor.call(
                    parameters.aidlExecutable.get().asFile.canonicalPath,
                    parameters.frameworkLocation.get().asFile.canonicalPath,
                    parameters.importFolders.asIterable(),
                    parameters.sourceOutputDir.get().asFile,
                    parameters.packagedOutputDir.orNull?.asFile,
                    parameters.packagedList.orNull,
                    depFileProcessor,
                    executor,
                    logger,
                    request.root.toPath(),
                    request.file.toPath()
                )
            }
        }
    }

    companion object {
        private val PATTERN_SET = PatternSet().include("**/*.aidl")

        @VisibleForTesting
        fun aidlCompileDelegate(
            workerExecutor: WorkerExecutor,
            aidlExecutable: File,
            frameworkLocation: File,
            destinationDir: File,
            parcelableDir: File?,
            packagedList: Collection<String>?,
            sourceFolders: Collection<File>,
            fullImportList: Collection<File>,
            instantiator: AndroidVariantTask
        ) {
            for (dir in sourceFolders) {
                workerExecutor.noIsolation().submit(AidlCompileRunnable::class.java) {
                    it.initializeFromAndroidVariantTask(instantiator)
                    it.aidlExecutable.set(aidlExecutable)
                    it.frameworkLocation.set(frameworkLocation)
                    it.importFolders.from(fullImportList)
                    it.sourceOutputDir.set(destinationDir)
                    it.packagedOutputDir.set(parcelableDir)
                    it.packagedList.set(packagedList)
                    it.dir.set(dir)
                }
            }
        }
    }
}
