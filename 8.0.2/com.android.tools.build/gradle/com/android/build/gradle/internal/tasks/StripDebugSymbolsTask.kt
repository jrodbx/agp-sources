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
import com.android.build.gradle.tasks.getChangesInSerializableForm
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.files.SerializableInputChanges
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.resources.FileStatus.CHANGED
import com.android.ide.common.resources.FileStatus.NEW
import com.android.ide.common.resources.FileStatus.REMOVED
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths
import javax.inject.Inject

/**
 * Task to remove debug symbols from native libraries.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.NATIVE, secondaryTaskCategories = [TaskCategory.SOURCE_PROCESSING])
abstract class StripDebugSymbolsTask : NewIncrementalTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val keepDebugSymbols: SetProperty<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Nested
    abstract val ndkHandlerInput: NdkHandlerInput

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    override fun doTaskAction(inputChanges: InputChanges) {

        if (!inputChanges.isIncremental) {
            FileUtils.cleanOutputDir(outputDir.get().asFile)
        }

        val changes = inputChanges.getChangesInSerializableForm(inputDir)

        workerExecutor.noIsolation().submit(StripDebugSymbolsDelegate::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.keepDebugSymbols.set(keepDebugSymbols.get())
            it.sdkBuildService.set(sdkBuildService)
            it.ndkHandlerInput.set(ndkHandlerInput)
            it.changes.set(changes)
            it.outputDir.set(outputDir)
        }
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

            task.inputDir.setDisallowChanges(creationConfig.artifacts.get(MERGED_NATIVE_LIBS))
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
abstract class StripDebugSymbolsDelegate : ProfileAwareWorkAction<StripDebugSymbolsDelegate.Params>() {

    @get:Inject
    abstract val workers: WorkerExecutor

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val keepDebugSymbols: SetProperty<String>
        abstract val sdkBuildService: Property<SdkComponentsBuildService>
        abstract val ndkHandlerInput: Property<NdkHandlerInput>
        // For unit testing, See StripDebugSymbolsTaskTest
        abstract val stripToolFinder: Property<SymbolStripExecutableFinder>
        abstract val changes: Property<SerializableInputChanges>
        abstract val outputDir: DirectoryProperty
    }

    override fun run() {

        val keepDebugSymbolsMatchers = parameters.keepDebugSymbols.get().map { compileGlob(it) }

        UnstrippedLibs.reset()

        val outputDir = parameters.outputDir.get().asFile

        for (change in parameters.changes.get().changes) {

            val path = change.normalizedPath
            val output = File(outputDir, path)

            when (change.fileStatus) {
                NEW, CHANGED -> {
                    val justCopyInput =
                        keepDebugSymbolsMatchers.any { matcher ->
                            matcher.matches(Paths.get(path))
                        }
                    workers.noIsolation().submit(StripDebugSymbolsRunnable::class.java) {
                        it.initializeFromProfileAwareWorkAction(parameters)
                        it.input.set(change.file)
                        it.output.set(output)
                        it.abi.set(Abi.getByName(change.file.parentFile.name))
                        it.justCopyInput.set(justCopyInput)
                        it.sdkBuildService.set(parameters.sdkBuildService)
                        it.ndkHandlerInput.set(parameters.ndkHandlerInput)
                        it.stripToolFinder.set(parameters.stripToolFinder)
                    }
                }
                REMOVED -> FileUtils.deletePath(output)
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

        if (parameters.justCopyInput.get()) {
            FileUtils.copyFile(parameters.input.get(), parameters.output.get())
            return
        }
        val stripToolFinder: Provider<SymbolStripExecutableFinder> = parameters.stripToolFinder.orElse(
            parameters.sdkBuildService.flatMap { sdk -> sdk.versionedNdkHandler(parameters.ndkHandlerInput.get()).stripExecutableFinderProvider }
        )
        val exe =
            stripToolFinder.get().stripToolExecutableFile(parameters.input.get(), parameters.abi.orNull) {
                UnstrippedLibs.add(parameters.input.get().name)
                logger.verbose("$it Packaging it as is.")
                return@stripToolExecutableFile null
            }

        if (exe == null) {
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
        abstract val sdkBuildService: Property<SdkComponentsBuildService>
        abstract val ndkHandlerInput: Property<NdkHandlerInput>
        // For unit testing, See StripDebugSymbolsTaskTest
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
