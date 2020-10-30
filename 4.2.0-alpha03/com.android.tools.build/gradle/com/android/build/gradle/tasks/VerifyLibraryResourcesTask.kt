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

package com.android.build.gradle.tasks

import com.android.aaptcompiler.canCompileResourceInJvm
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.AndroidJarInput
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.Aapt2ProcessResourcesRunnable
import com.android.build.gradle.internal.res.ResourceCompilerRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.Aapt2WorkersBuildService
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.files.SerializableInputChanges
import com.android.builder.internal.aapt.AaptException
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

@CacheableTask
abstract class VerifyLibraryResourcesTask : NewIncrementalTask() {

    @get:OutputDirectory
    lateinit var compiledDirectory: File
        private set

    // Merged resources directory.
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: DirectoryProperty

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    /** A file collection of the directories containing the compiled dependencies resource files. */
    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledDependenciesResources: ConfigurableFileCollection

    @get:Input
    var useJvmResourceCompiler: Boolean = false
      private set

    @get:Internal
    abstract val aapt2WorkersBuildService: Property<Aapt2WorkersBuildService>

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    // Not an input as it doesn't affect task outputs
    @get:Internal
    abstract val mergeBlameFolder: DirectoryProperty

    @get:Nested
    abstract val androidJarInput: AndroidJarInput

    private lateinit var manifestMergeBlameFile: Provider<RegularFile>

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override fun doTaskAction(inputChanges: InputChanges) {
        val manifestsOutputs = BuiltArtifactsLoaderImpl().load(manifestFiles)
            ?: throw RuntimeException("Cannot load manifests from $manifestFiles")
        val manifestFile = Iterables.getOnlyElement(manifestsOutputs.elements).outputFile

        workerExecutor.noIsolation().submit(Action::class.java) { params ->
            params.initializeFromAndroidVariantTask(this)

            params.androidJar.set(androidJarInput.getAndroidJar().get())
            params.aapt2DaemonBuildService.set(aapt2DaemonBuildService)
            params.aapt2WorkersBuildService.set(aapt2WorkersBuildService)
            params.aapt2FromMaven.from(aapt2FromMaven)
            params.errorFormatMode.set(errorFormatMode)
            params.inputs.set(inputChanges.getChangesInSerializableForm(inputDirectory))
            params.manifestFile.set(File(manifestFile))
            params.compiledDependenciesResources.from(compiledDependenciesResources)
            params.manifestMergeBlameFile.set(manifestMergeBlameFile)
            params.compiledDirectory.set(compiledDirectory)
            params.mergeBlameFolder.set(mergeBlameFolder)
            params.useJvmResourceCompiler.set(useJvmResourceCompiler)
        }
    }

    protected abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val androidJar: RegularFileProperty
        abstract val aapt2WorkersBuildService: Property<Aapt2WorkersBuildService>
        abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>
        abstract val aapt2FromMaven: ConfigurableFileCollection
        abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
        abstract val inputs: Property<SerializableInputChanges>
        abstract val manifestFile: RegularFileProperty
        abstract val compiledDependenciesResources: ConfigurableFileCollection
        abstract val useJvmResourceCompiler: Property<Boolean>
        abstract val manifestMergeBlameFile: RegularFileProperty
        abstract val compiledDirectory: DirectoryProperty
        abstract val mergeBlameFolder: DirectoryProperty
    }

    /**
     * Compiles and links the resources of the library.
     */
    protected abstract class Action : ProfileAwareWorkAction<Params>() {

        override fun run() {
            val logger = Logging.getLogger(this.javaClass)
            val aapt2ServiceKey =
                parameters.aapt2DaemonBuildService.get().registerAaptService(parameters.aapt2FromMaven.singleFile, LoggerWrapper(logger))
            parameters.aapt2WorkersBuildService.get().getSharedExecutorForAapt2(
                parameters.projectName.get(), parameters.taskOwner.get()).use { facade ->
                    compileResources(
                        inputs = parameters.inputs.get(),
                        outDirectory = parameters.compiledDirectory.get().asFile,
                        workerExecutor = facade,
                        aapt2ServiceKey = aapt2ServiceKey,
                        errorFormatMode = parameters.errorFormatMode.get(),
                        mergeBlameFolder = parameters.mergeBlameFolder.get().asFile,
                        useJvmResourceCompiler = parameters.useJvmResourceCompiler.get()
                    )

                    val config = getAaptPackageConfig(
                        compiledDependenciesResources = parameters.compiledDependenciesResources,
                        androidJar = parameters.androidJar.get().asFile,
                        resDir = parameters.compiledDirectory.get().asFile,
                        manifestFile = parameters.manifestFile.get().asFile
                    )

                    val params = Aapt2ProcessResourcesRunnable.Params(
                        aapt2ServiceKey = aapt2ServiceKey,
                        request = config,
                        errorFormatMode = parameters.errorFormatMode.get(),
                        mergeBlameFolder = parameters.mergeBlameFolder.get().asFile,
                        manifestMergeBlameFile = parameters.manifestMergeBlameFile.get().asFile
                    )

                    facade.await() // All compilation must be done before linking.
                    facade.submit(Aapt2ProcessResourcesRunnable::class.java, params)
                }
        }
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<VerifyLibraryResourcesTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("verify", "Resources")
        override val type: Class<VerifyLibraryResourcesTask>
            get() = VerifyLibraryResourcesTask::class.java

        /** Configure the given newly-created task object.  */
        override fun configure(
            task: VerifyLibraryResourcesTask
        ) {
            super.configure(task)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(creationConfig.globalScope)
            task.aapt2FromMaven.fromDisallowChanges(aapt2FromMaven)
            task.aapt2Version = aapt2Version

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.MERGED_RES,
                task.inputDirectory
            )

            task.compiledDirectory = creationConfig.paths.compiledResourcesOutputDir
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.AAPT_FRIENDLY_MERGED_MANIFESTS,
                task.manifestFiles
            )
            task.mergeBlameFolder.setDisallowChanges(creationConfig.artifacts.get(InternalArtifactType.MERGED_RES_BLAME_FOLDER))

            task.manifestMergeBlameFile = creationConfig.artifacts.get(
                InternalArtifactType.MANIFEST_MERGE_BLAME_FILE
            )

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.services.projectOptions
            )

            if (creationConfig.variantScope.isPrecompileDependenciesResourcesEnabled) {
                task.compiledDependenciesResources.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES
                    ))
            }

            task.useJvmResourceCompiler =
              creationConfig.services.projectOptions[BooleanOption.ENABLE_JVM_RESOURCE_COMPILER]
            task.aapt2WorkersBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.aapt2DaemonBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.androidJarInput.sdkBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }

    companion object {
        private fun getAaptPackageConfig(compiledDependenciesResources: Iterable<File>, androidJar: File, resDir: File, manifestFile: File): AaptPackageConfig {
            val compiledDependenciesResourcesDirs = compiledDependenciesResources.reversed()

            // We're do not want to generate any files - only to make sure everything links properly.
            return AaptPackageConfig.Builder()
                .setManifestFile(manifestFile)
                .addResourceDirectories(compiledDependenciesResourcesDirs)
                .addResourceDir(resDir)
                .setLibrarySymbolTableFiles(ImmutableSet.of())
                .setOptions(AaptOptions())
                .setVariantType(VariantTypeImpl.LIBRARY)
                .setAndroidTarget(androidJar)
                .build()
        }

        /**
         * Compiles new or changed files and removes files that were compiled from the removed files.
         *
         *
         * Should only be called when using AAPT2.
         *
         * @param inputs the new, changed or modified files that need to be compiled or removed.
         * @param outDirectory the directory containing compiled resources.
         * @param aapt AAPT tool to execute the resource compiling, either must be supplied or
         * worker executor and revision must be supplied.
         * @param aapt2ServiceKey the AAPT2 service to inject in to the worker executor.
         * @param workerExecutor the worker executor to submit AAPT compilations to.
         * @param mergedResDirectory directory containing merged uncompiled resources.
         */
        @JvmStatic
        @VisibleForTesting
        fun compileResources(
            inputs: SerializableInputChanges,
            outDirectory: File,
            workerExecutor: WorkerExecutorFacade,
            aapt2ServiceKey: Aapt2DaemonServiceKey,
            errorFormatMode: SyncOptions.ErrorFormatMode,
            mergeBlameFolder: File,
            useJvmResourceCompiler: Boolean
        ) {

            for (change in inputs.changes) {
                // Accept only files in subdirectories of the merged resources directory.
                // Ignore files and directories directly under the merged resources directory.
                val dirName = change.normalizedPath.substringBeforeLast('/', "")
                if (dirName.isEmpty() || dirName.contains('/')) {
                    continue
                }

                when (change.fileStatus) {
                    FileStatus.NEW, FileStatus.CHANGED ->
                        // If the file is NEW or CHANGED we need to compile it into the output
                        // directory. AAPT2 overwrites files in case they were CHANGED so no need to
                        // remove the corresponding file.
                        try {
                            val request = CompileResourceRequest(
                                change.file,
                                outDirectory,
                                dirName,
                                isPseudoLocalize = false,
                                isPngCrunching = false,
                                mergeBlameFolder = mergeBlameFolder
                            )

                            if (useJvmResourceCompiler &&
                              canCompileResourceInJvm(request.inputFile, request.isPngCrunching)) {
                                workerExecutor.submit(
                                  ResourceCompilerRunnable::class.java,
                                  ResourceCompilerRunnable.Params(request)
                                )
                            } else {
                                workerExecutor.submit(
                                  Aapt2CompileRunnable::class.java,
                                  Aapt2CompileRunnable.Params(
                                    aapt2ServiceKey,
                                    listOf(request),
                                    errorFormatMode,
                                    true
                                  )
                                )
                            }
                        } catch (e: Exception) {
                            throw AaptException("Failed to compile file ${change.file.absolutePath}", e)
                        }

                    FileStatus.REMOVED ->
                        // If the file was REMOVED we need to remove the corresponding file from the
                        // output directory.
                        FileUtils.deleteIfExists(
                            File(outDirectory, Aapt2RenamingConventions.compilationRename(change.file))
                        )
                }
            }
            // We need to wait for the files to finish compiling before we do the link.
            workerExecutor.await()
        }
    }
}
