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
import com.android.build.gradle.internal.NdkHandlerInput
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.stripping.SymbolStripExecutableFinder
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.STRIPPED_NATIVE_LIBS
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.resources.FileStatus.CHANGED
import com.android.ide.common.resources.FileStatus.NEW
import com.android.ide.common.resources.FileStatus.REMOVED
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
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
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import javax.inject.Inject
import org.gradle.work.DisableCachingByDefault

/**
 * Task to remove debug symbols from native libraries.
 */
@DisableCachingByDefault
abstract class StripDebugSymbolsTask : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val keepDebugSymbols: SetProperty<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Nested
    abstract val ndkHandlerInput: NdkHandlerInput

    // We need inputFiles in addition to inputDir because SkipWhenEmpty doesn't work for inputDir
    // because it's a DirectoryProperty
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputFiles: FileTree
        get() = inputDir.asFileTree

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    override val incremental: Boolean
        get() = true

    override fun doFullTaskAction() {
            StripDebugSymbolsDelegate(
                workerExecutor,
                inputDir.get().asFile,
                outputDir.get().asFile,
                keepDebugSymbols.get(),
                sdkBuildService.get().versionedNdkHandler(ndkHandlerInput).stripExecutableFinderProvider,
                null,
                this
            ).run()
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        StripDebugSymbolsDelegate(
            workerExecutor,
            inputDir.get().asFile,
            outputDir.get().asFile,
            keepDebugSymbols.get(),
            sdkBuildService.get().versionedNdkHandler(ndkHandlerInput).stripExecutableFinderProvider,
            changedInputs,
            this
        ).run()
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<StripDebugSymbolsTask, VariantCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("strip", "DebugSymbols")

        override val type: Class<StripDebugSymbolsTask>
            get() = StripDebugSymbolsTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<StripDebugSymbolsTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                StripDebugSymbolsTask::outputDir
            ).withName("out").on(STRIPPED_NATIVE_LIBS)
        }

        override fun configure(
            task: StripDebugSymbolsTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(MERGED_NATIVE_LIBS, task.inputDir)
            task.keepDebugSymbols.setDisallowChanges(
                creationConfig.packaging.jniLibs.keepDebugSymbols
            )
            task.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.ndkHandlerInput.initialize(creationConfig)
        }
    }
}

/**
 * Delegate to strip debug symbols from native libraries
 */
@VisibleForTesting
class StripDebugSymbolsDelegate(
    val workers: WorkerExecutor,
    val inputDir: File,
    val outputDir: File,
    val keepDebugSymbols: Set<String>,
    val stripToolFinderProvider: Provider<SymbolStripExecutableFinder>,
    val changedInputs: Map<File, FileStatus>?,
    private val instantiator: AndroidVariantTask
) {

    fun run() {
        if (changedInputs == null) {
            FileUtils.cleanOutputDir(outputDir)
        }

        val keepDebugSymbolsMatchers = keepDebugSymbols.map { compileGlob(it) }

        // by lazy, because we don't want to spend the extra time or print out NDK-related spam if
        // there are no .so files to strip
        val stripToolFinder by lazy { stripToolFinderProvider.get() }

        UnstrippedLibs.reset()

        if (changedInputs != null) {
            for (input in changedInputs.keys) {
                if (input.isDirectory) {
                    continue
                }
                val path = input.toRelativeString(inputDir)
                val output = File(outputDir, path)

                when (changedInputs[input]) {
                    NEW, CHANGED -> {
                        val justCopyInput =
                            keepDebugSymbolsMatchers.any { matcher ->
                                matcher.matches(Paths.get(path))
                            }
                        workers.noIsolation().submit(StripDebugSymbolsRunnable::class.java) {
                            it.initializeFromAndroidVariantTask(instantiator)
                            it.input.set(input)
                            it.output.set(output)
                            it.abi.set(Abi.getByName(input.parentFile.name))
                            it.justCopyInput.set(justCopyInput)
                            it.stripToolFinder.set(stripToolFinder)
                        }
                    }
                    REMOVED -> FileUtils.deletePath(output)
                }
            }
        } else {
            for (input in FileUtils.getAllFiles(inputDir)) {
                if (input.isDirectory) {
                    continue
                }
                val path = input.toRelativeString(inputDir)
                val output = File(outputDir, path)
                val justCopyInput =
                    keepDebugSymbolsMatchers.any { matcher -> matcher.matches(Paths.get(path)) }

                workers.noIsolation().submit(StripDebugSymbolsRunnable::class.java) {
                    it.initializeFromAndroidVariantTask(instantiator)
                    it.input.set(input)
                    it.output.set(output)
                    it.abi.set(Abi.getByName(input.parentFile.name))
                    it.justCopyInput.set(justCopyInput)
                    it.stripToolFinder.set(stripToolFinder)
                }
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
abstract class StripDebugSymbolsRunnable : ProfileAwareWorkAction<StripDebugSymbolsRunnable.Params>() {

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun run() {
        val logger = LoggerWrapper(Logging.getLogger(StripDebugSymbolsTask::class.java))

        FileUtils.mkdirs(parameters.output.get().parentFile)

        val exe =
            parameters.stripToolFinder.get().stripToolExecutableFile(parameters.input.get(), parameters.abi.orNull) {
                UnstrippedLibs.add(parameters.input.get().name)
                logger.verbose("$it Packaging it as is.")
                return@stripToolExecutableFile null
            }

        if (exe == null || parameters.justCopyInput.get()) {
            // If exe == null, the strip executable couldn't be found and a message about the
            // failure was reported in getPathToStripExecutable, so we fall back to copying the file
            // to the output location.
            FileUtils.copyFile(parameters.input.get(), parameters.output.get())
            return
        }

        val builder = ProcessInfoBuilder()
        builder.setExecutable(exe)
        builder.addArgs("--strip-unneeded")
        builder.addArgs("-o")
        builder.addArgs(parameters.output.get().toString())
        builder.addArgs(parameters.input.get().toString())
        val result =
            GradleProcessExecutor(execOperations::exec).execute(
                builder.createProcess(), LoggedProcessOutputHandler(logger)
            )
        if (result.exitValue != 0) {
            UnstrippedLibs.add(parameters.input.get().name)
            logger.verbose(
                "Unable to strip library ${parameters.input.get().absolutePath} due to error "
                        + "${result.exitValue} returned from $exe, packaging it as is."
            )
            FileUtils.copyFile(parameters.input.get(), parameters.output.get())
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val input: Property<File>
        abstract val output: Property<File>
        abstract val abi: Property<Abi>
        abstract val justCopyInput: Property<Boolean>
        abstract val stripToolFinder: Property<SymbolStripExecutableFinder>
    }
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
