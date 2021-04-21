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

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.AIDL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.compiling.DependencyFileProcessor
import com.android.builder.internal.compiler.AidlProcessor
import com.android.builder.internal.compiler.DirectoryWalker
import com.android.builder.internal.incremental.DependencyData
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import java.io.File
import java.io.IOException
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.ExecOperations

/**
 * Task to compile aidl files. Supports incremental update.
 *
 *
 * TODO(b/124424292)
 *
 *
 * We can not use gradle worker in this task as we use [GradleProcessExecutor] for
 * compiling aidl files, which should not be serialized.
 */
@CacheableTask
abstract class AidlCompile : NonIncrementalTask() {
    @get:Input
    @get:Optional
    var packageWhitelist: Collection<String>? = null

    @get:Internal
    abstract val sourceDirs: ListProperty<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var importDirs: FileCollection
        private set

    @get:Inject
    abstract val execOperations: ExecOperations

    // Given the same version, the path or contents of the AIDL tool may change across platforms,
    // but it would still produce the same output (given the same inputs)---see bug 138920846.
    // Therefore, the path or contents of the tool should not be an input. Instead, we set the
    // tool's version as input.
    @get:Input
    val aidlVersion: String
        get() {
            val buildToolsRevision = sdkBuildService.get().buildToolsRevisionProvider.orNull
            Preconditions.checkState(buildToolsRevision != null, "Build Tools not present")

            val aidlExecutable = sdkBuildService.get().aidlExecutableProvider.orNull
            Preconditions.checkState(
                aidlExecutable != null,
                "AIDL executable not present in Build Tools $buildToolsRevision"
            )
            Preconditions.checkState(
                aidlExecutable!!.exists(),
                "AIDL executable does not exist: ${aidlExecutable.path}"
            )

            return buildToolsRevision.toString()
        }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    fun getAidlFrameworkProvider(): Provider<File> = sdkBuildService.flatMap { it.aidlFrameworkProvider }

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: Property<FileTree>

    @get:OutputDirectory
    abstract val sourceOutputDir: DirectoryProperty

    @get:OutputDirectory
    @get:Optional
    abstract val packagedDir: DirectoryProperty

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    private class DepFileProcessor : DependencyFileProcessor {
        override fun processFile(dependencyFile: File): DependencyData? {
            return DependencyData.parseDependencyFile(dependencyFile)
        }
    }

    override fun doTaskAction() {
        // this is full run, clean the previous output
        val destinationDir = sourceOutputDir.get().asFile
        val parcelableDir = packagedDir.orNull
        FileUtils.cleanOutputDir(destinationDir)
        if (parcelableDir != null) {
            FileUtils.cleanOutputDir(parcelableDir.asFile)
        }

        getWorkerFacadeWithThreads(false).use { workers ->
            val sourceFolders = sourceDirs.get()
            val importFolders = importDirs.files

            val fullImportList = sourceFolders + importFolders

            val processor = AidlProcessor(
                sdkBuildService.get().aidlExecutableProvider.get().absolutePath,
                getAidlFrameworkProvider().get().absolutePath,
                fullImportList,
                destinationDir,
                parcelableDir?.asFile,
                packageWhitelist,
                DepFileProcessor(),
                GradleProcessExecutor(execOperations::exec),
                LoggedProcessOutputHandler(LoggerWrapper(logger))
            )

            for (dir in sourceFolders) {
                workers.submit(AidlCompileRunnable::class.java, AidlCompileParams(dir, processor))
            }
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<AidlCompile, ComponentPropertiesImpl>(
        componentProperties
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

            if (creationConfig.variantType.isAar) {
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
            val globalScope = creationConfig.globalScope
            val project = globalScope.project

            val variantSources = creationConfig.variantSources

            val sdkComponents = globalScope.sdkComponents

            task
                .sourceDirs
                .set(project.provider { variantSources.aidlSourceList })
            task.sourceDirs.disallowChanges()

            // This is because aidl may be in the same folder as Java and we want to restrict to
            // .aidl files and not java files.
            task
                .sourceFiles
                .set(
                    project.provider {
                        project.layout.files(task.sourceDirs).asFileTree.matching(PATTERN_SET)
                    })
            task.sourceFiles.disallowChanges()

            task.importDirs = creationConfig.variantDependencies.getArtifactFileCollection(COMPILE_CLASSPATH, ALL, AIDL)

            if (creationConfig.variantType.isAar) {
                task.packageWhitelist = globalScope.extension.aidlPackageWhiteList
            }
            task.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }

    internal class AidlCompileRunnable @Inject
    constructor(private val params: AidlCompileParams) : Runnable {

        override fun run() {
            try {
                DirectoryWalker.builder()
                    .root(params.dir.toPath())
                    .extensions("aidl")
                    .action(params.processor)
                    .build()
                    .walk()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }

        }
    }

    internal class AidlCompileParams(val dir: File, val processor: AidlProcessor): Serializable

    companion object {
        private val PATTERN_SET = PatternSet().include("**/*.aidl")
    }
}