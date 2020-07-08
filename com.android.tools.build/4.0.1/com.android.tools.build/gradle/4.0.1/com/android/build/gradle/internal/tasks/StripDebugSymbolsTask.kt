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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.FileStatus.CHANGED
import com.android.ide.common.resources.FileStatus.NEW
import com.android.ide.common.resources.FileStatus.REMOVED
import com.android.ide.common.workers.WorkerExecutorFacade
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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.Serializable
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import javax.inject.Inject

/**
 * Task to remove debug symbols from native libraries.
 *
 * TODO(https://issuetracker.google.com/129217943)
 * <p>We can not use gradle worker in this task as we use [GradleProcessExecutor], which should
 * not be serialized.
 */
@CacheableTask
abstract class StripDebugSymbolsTask : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    lateinit var excludePatterns: List<String>
        private set

    @get:Inject
    abstract val execOperations: ExecOperations

    @Input
    @Optional
    fun getStripExecutablesMap(): Map<Abi, File>? {
        // if no input files, return null, because no need for executable(s), and we don't want to
        // spend the extra time or print out NDK-related spam if not necessary.
        return if (FileUtils.getAllFiles(inputDir.get().asFile).isEmpty) {
            null
        } else {
            stripToolFinderProvider.get().stripExecutables.toSortedMap()
        }
    }

    // We need inputFiles in addition to inputDir because SkipWhenEmpty doesn't work for inputDir
    // because it's a DirectoryProperty
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: Property<FileTree>

    private lateinit var stripToolFinderProvider: Provider<SymbolStripExecutableFinder>

    override val incremental: Boolean
        get() = true

    override fun doFullTaskAction() {
        getWorkerFacadeWithThreads(useGradleExecutor = false).use { workers ->
            StripDebugSymbolsDelegate(
                workers,
                inputDir.get().asFile,
                outputDir.get().asFile,
                excludePatterns,
                stripToolFinderProvider,
                GradleProcessExecutor(execOperations::exec),
                null
            ).run()
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        getWorkerFacadeWithThreads(useGradleExecutor = false).use { workers ->
            StripDebugSymbolsDelegate(
                workers,
                inputDir.get().asFile,
                outputDir.get().asFile,
                excludePatterns,
                stripToolFinderProvider,
                GradleProcessExecutor(execOperations::exec),
                changedInputs
            ).run()
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<StripDebugSymbolsTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("strip", "DebugSymbols")

        override val type: Class<StripDebugSymbolsTask>
            get() = StripDebugSymbolsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out StripDebugSymbolsTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                STRIPPED_NATIVE_LIBS,
                taskProvider,
                StripDebugSymbolsTask::outputDir,
                fileName = "out"
            )
        }

        override fun configure(task: StripDebugSymbolsTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(MERGED_NATIVE_LIBS, task.inputDir)
            task.excludePatterns =
                variantScope.globalScope.extension.packagingOptions.doNotStrip.sorted()
            task.stripToolFinderProvider =
                variantScope.globalScope.sdkComponents.stripExecutableFinderProvider
            task.inputFiles.setDisallowChanges(
                variantScope.globalScope.project.provider {
                    variantScope.globalScope.project.layout.files(task.inputDir).asFileTree
                }
            )
        }
    }
}

/**
 * Delegate to strip debug symbols from native libraries
 */
@VisibleForTesting
class StripDebugSymbolsDelegate(
    val workers: WorkerExecutorFacade,
    val inputDir: File,
    val outputDir: File,
    val excludePatterns: List<String>,
    val stripToolFinderProvider: Provider<SymbolStripExecutableFinder>,
    val processExecutor: ProcessExecutor,
    val changedInputs: Map<File, FileStatus>?
) {

    fun run() {
        if (changedInputs == null) {
            FileUtils.cleanOutputDir(outputDir)
        }

        val excludeMatchers = excludePatterns.map { compileGlob(it) }

        // by lazy, because we don't want to spend the extra time or print out NDK-related spam if
        // there are no .so files to strip
        val stripToolFinder by lazy { stripToolFinderProvider.get() }

        UnstrippedLibs.reset()

        if (changedInputs != null) {
            for (input in changedInputs.keys) {
                if (input.isDirectory) {
                    continue
                }
                val path = FileUtils.relativePossiblyNonExistingPath(input, inputDir)
                val output = File(outputDir, path)

                when (changedInputs[input]) {
                    NEW, CHANGED -> {
                        val justCopyInput =
                            excludeMatchers.any { matcher -> matcher.matches(Paths.get(path)) }
                        workers.submit(
                            StripDebugSymbolsRunnable::class.java,
                            StripDebugSymbolsRunnable.Params(
                                input,
                                output,
                                Abi.getByName(input.parentFile.name),
                                justCopyInput,
                                stripToolFinder,
                                processExecutor
                            )
                        )
                    }
                    REMOVED -> FileUtils.deletePath(output)
                }
            }
        } else {
            for (input in FileUtils.getAllFiles(inputDir)) {
                if (input.isDirectory) {
                    continue
                }
                val path = FileUtils.relativePath(input, inputDir)
                val output = File(outputDir, path)
                val justCopyInput =
                    excludeMatchers.any { matcher -> matcher.matches(Paths.get(path)) }

                workers.submit(
                    StripDebugSymbolsRunnable::class.java,
                    StripDebugSymbolsRunnable.Params(
                        input,
                        output,
                        Abi.getByName(input.parentFile.name),
                        justCopyInput,
                        stripToolFinder,
                        processExecutor
                    )
                )
            }
        }

        workers.await()
        if (UnstrippedLibs.isNotEmpty()) {
            val logger = LoggerWrapper(Logging.getLogger(StripDebugSymbolsTask::class.java))
            logger.warning(
                "Unable to strip the following libraries, packaging them as they are: "
                        + "${UnstrippedLibs.getJoinedString()}."
            )
        }
    }
}

/**
 * Runnable to strip debug symbols from a native library
 */
private class StripDebugSymbolsRunnable @Inject constructor(val params: Params): Runnable {

    override fun run() {
        val logger = LoggerWrapper(Logging.getLogger(StripDebugSymbolsTask::class.java))

        FileUtils.mkdirs(params.output.parentFile)

        val exe =
            params.stripToolFinder.stripToolExecutableFile(params.input, params.abi) {
                UnstrippedLibs.add(params.input.name)
                logger.verbose("$it Packaging it as is.")
                return@stripToolExecutableFile null
            }

        if (exe == null || params.justCopyInput) {
            // If exe == null, the strip executable couldn't be found and a message about the
            // failure was reported in getPathToStripExecutable, so we fall back to copying the file
            // to the output location.
            FileUtils.copyFile(params.input, params.output)
            return
        }

        val builder = ProcessInfoBuilder()
        builder.setExecutable(exe)
        builder.addArgs("--strip-unneeded")
        builder.addArgs("-o")
        builder.addArgs(params.output.toString())
        builder.addArgs(params.input.toString())
        val result =
            params.processExecutor.execute(
                builder.createProcess(), LoggedProcessOutputHandler(logger)
            )
        if (result.exitValue != 0) {
            UnstrippedLibs.add(params.input.name)
            logger.verbose(
                "Unable to strip library ${params.input.absolutePath} due to error "
                        + "${result.exitValue} returned from $exe, packaging it as is."
            )
            FileUtils.copyFile(params.input, params.output)
        }
    }

    data class Params(
        val input: File,
        val output: File,
        val abi: Abi?,
        val justCopyInput: Boolean,
        val stripToolFinder: SymbolStripExecutableFinder,
        val processExecutor: ProcessExecutor
    ): Serializable
}

object UnstrippedLibs {
    private val unstrippedLibs = mutableSetOf<String>()

    @Synchronized
    fun reset() {
        unstrippedLibs.removeAll { true }
    }

    @Synchronized
    fun add(name: String) {
        unstrippedLibs.add(name)
    }

    @Synchronized
    fun isNotEmpty() = unstrippedLibs.isNotEmpty()

    @Synchronized
    fun getJoinedString() = unstrippedLibs.sorted().joinToString()
}

private fun compileGlob(pattern: String): PathMatcher {
        val maybeSlash = if (pattern.startsWith("/") || pattern.startsWith("*")) "" else "/"
        return FileSystems.getDefault().getPathMatcher("glob:$maybeSlash$pattern")
}
