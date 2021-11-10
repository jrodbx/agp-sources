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

import com.android.build.api.variant.Renderscript
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.RENDERSCRIPT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NdkTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.compiler.DirectoryWalker
import com.android.builder.internal.compiler.RenderScriptProcessor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessOutputHandler
import com.android.repository.Revision
import com.android.sdklib.BuildToolInfo
import com.android.utils.FileUtils
import com.google.common.base.Preconditions.checkNotNull
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import javax.inject.Inject

/** Task to compile Renderscript files. Supports incremental update. */
@CacheableTask
abstract class RenderscriptCompile : NdkTask() {

    // ----- PUBLIC TASK API -----

    @get:OutputDirectory
    abstract val resOutputDir: DirectoryProperty

    @get:OutputDirectory
    lateinit var objOutputDir: Provider<Directory>

    // ----- PRIVATE TASK API -----

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var importDirs: FileCollection

    @get:Input
    abstract val targetApi: Property<Int>

    @get:Input
    abstract val supportMode: Property<Boolean>

    @get:Input
    var useAndroidX: Boolean = false
        private set

    @get:Input
    abstract val optimLevel: Property<Int>

    @get:Input
    abstract val ndkMode: Property<Boolean>

    @Input
    fun getBuildToolsVersion(): String =
        buildToolsRevision.get().toString()

    @get: Input
    abstract val compileSdkVersion: Property<String>

    @get: Internal
    abstract val buildToolsRevision: Property<Revision>

    @get:Internal
    abstract val sdkBuildService: Property<SdkComponentsBuildService>

    @get:OutputDirectory
    abstract val sourceOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val libOutputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    // get the import folders. If the .rsh files are not directly under the import folders,
    // we need to get the leaf folders, as this is what llvm-rs-cc expects.
    // TODO: should "rsh" be a constant somewhere?
    private val importFolders: Collection<File>
        get() {
            val results = Sets.newHashSet<File>()

            val dirs = Lists.newArrayList<File>()
            dirs.addAll(importDirs.files)
            dirs.addAll(sourceDirs.files)

            for (dir in dirs) {
                DirectoryWalker.builder()
                    .root(dir.toPath())
                    .extensions("rsh")
                    .action { _, path -> results.add(path.parent.toFile()) }
                    .build()
                    .walk()
            }

            return results
        }

    private lateinit var sourceDirs: FileCollection

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    fun getSourceDirs(): FileCollection {
        return sourceDirs.asFileTree
    }

    override fun doTaskAction() {
        // this is full run (always), clean the previous outputs
        val sourceDestDir = sourceOutputDir.get().asFile
        FileUtils.cleanOutputDir(sourceDestDir)

        val resDestDir = resOutputDir.get().asFile
        FileUtils.cleanOutputDir(resDestDir)

        val objDestDir = objOutputDir.get().asFile
        FileUtils.cleanOutputDir(objDestDir)

        val libDestDir = libOutputDir.get().asFile
        FileUtils.cleanOutputDir(libDestDir)

        val sourceDirectories = sourceDirs.files

        val buildToolsInfo = sdkBuildService.get().sdkLoader(
            compileSdkVersion = compileSdkVersion,
            buildToolsRevision = buildToolsRevision
        ).buildToolInfoProvider.get()
        compileAllRenderscriptFiles(
            sourceDirectories,
            importFolders,
            sourceDestDir,
            resDestDir,
            objDestDir,
            libDestDir,
            targetApi.get(),
            optimLevel.get(),
            ndkMode.get(),
            supportMode.get(),
            useAndroidX,
            ndkConfig?.abiFilters ?: setOf(),
            LoggedProcessOutputHandler(LoggerWrapper(logger)),
            buildToolsInfo
        )
    }

    /**
     * Compiles all the renderscript files found in the given source folders.
     *
     *
     * Right now this is the only way to compile them as the renderscript compiler requires all
     * renderscript files to be passed for all compilation.
     *
     *
     * Therefore whenever a renderscript file or header changes, all must be recompiled.
     *
     * @param sourceFolders all the source folders to find files to compile
     * @param importFolders all the import folders.
     * @param sourceOutputDir the output dir in which to generate the source code
     * @param resOutputDir the output dir in which to generate the bitcode file
     * @param targetApi the target api
     * @param optimLevel the optimization level
     * @param ndkMode whether the renderscript code should be compiled to generate C/C++ bindings
     * @param supportMode support mode flag to generate .so files.
     * @param useAndroidX whether to use AndroidX dependencies
     * @param abiFilters ABI filters in case of support mode
     * @throws IOException failed
     * @throws InterruptedException failed
     */
    private fun compileAllRenderscriptFiles(
        sourceFolders: Collection<File>,
        importFolders: Collection<File>,
        sourceOutputDir: File,
        resOutputDir: File,
        objOutputDir: File,
        libOutputDir: File,
        targetApi: Int,
        optimLevel: Int,
        ndkMode: Boolean,
        supportMode: Boolean,
        useAndroidX: Boolean,
        abiFilters: Set<String>,
        processOutputHandler: ProcessOutputHandler,
        buildToolInfo: BuildToolInfo
    ) {
        checkNotNull(sourceFolders, "sourceFolders cannot be null.")
        checkNotNull(importFolders, "importFolders cannot be null.")
        checkNotNull(sourceOutputDir, "sourceOutputDir cannot be null.")
        checkNotNull(resOutputDir, "resOutputDir cannot be null.")

        val renderscript = buildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC)
        if (renderscript == null || !File(renderscript).isFile) {
            throw IllegalStateException("llvm-rs-cc is missing")
        }

        val processor = RenderScriptProcessor(
            sourceFolders,
            importFolders,
            sourceOutputDir,
            resOutputDir,
            objOutputDir,
            libOutputDir,
            buildToolInfo,
            targetApi,
            optimLevel,
            ndkMode,
            supportMode,
            useAndroidX,
            abiFilters,
            LoggerWrapper(logger)
        )
        processor.build(GradleProcessExecutor(execOperations::exec), processOutputHandler)
    }

    // ----- CreationAction -----

    class CreationAction(
        creationConfig: ConsumableCreationConfig,
        val renderscript: Renderscript
    ) : VariantTaskCreationAction<RenderscriptCompile, ConsumableCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("compile", "Renderscript")

        override val type: Class<RenderscriptCompile>
            get() = RenderscriptCompile::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<RenderscriptCompile>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.renderscriptCompileTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                RenderscriptCompile::sourceOutputDir
            ).withName("out").on(RENDERSCRIPT_SOURCE_OUTPUT_DIR)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                RenderscriptCompile::libOutputDir
            ).withName("lib").on(RENDERSCRIPT_LIB)
        }

        override fun configure(
            task: RenderscriptCompile
        ) {
            super.configure(task)

            val variantDslInfo = creationConfig.variantDslInfo
            val variantSources = creationConfig.variantSources

            task.targetApi.setDisallowChanges(creationConfig.renderscriptTargetApi)

            task.supportMode.setDisallowChanges(renderscript.supportModeEnabled)
            task.useAndroidX = creationConfig.services.projectOptions.get(BooleanOption.USE_ANDROID_X)
            task.ndkMode.setDisallowChanges(renderscript.ndkModeEnabled)
            task.optimLevel.setDisallowChanges(renderscript.optimLevel)

            task.sourceDirs =
                creationConfig.services.fileCollection(Callable { variantSources.renderscriptSourceList })
            task.importDirs = creationConfig.variantDependencies.getArtifactFileCollection(
                COMPILE_CLASSPATH, ALL, RENDERSCRIPT
            )

            task.resOutputDir.setDisallowChanges(creationConfig.paths.renderscriptResOutputDir)
            task.objOutputDir = creationConfig.paths.renderscriptObjOutputDir

            task.ndkConfig = variantDslInfo.ndkConfig

            task.buildToolsRevision.setDisallowChanges(
                creationConfig.globalScope.extension.buildToolsRevision
            )
            task.compileSdkVersion.setDisallowChanges(
                creationConfig.globalScope.extension.compileSdkVersion
            )
            task.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            if (creationConfig.variantType.isTestComponent) {
                task.dependsOn(creationConfig.taskContainer.processManifestTask!!)
            }
        }
    }
}
