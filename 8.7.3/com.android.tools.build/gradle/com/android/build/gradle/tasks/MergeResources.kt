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

import android.databinding.tool.LayoutXmlProcessor
import android.databinding.tool.LayoutXmlProcessor.OriginalFileLookup
import android.databinding.tool.util.RelativizableFile
import android.databinding.tool.writer.JavaFileWriter
import com.android.SdkConstants
import com.android.build.gradle.internal.DependencyResourcesComputer
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.databinding.MergingFileLookup
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.res.namespaced.NamespaceRemover
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE
import com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_RES_INCREMENTAL_FOLDER
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.Blocks
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.Workers.withGradleWorkers
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.AndroidResourcesTaskCreationActionImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.png.VectorDrawableRenderer
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.resources.ANDROID_AAPT_IGNORE
import com.android.ide.common.resources.CopyToOutputDirectoryResourceCompilationService
import com.android.ide.common.resources.FileValidity
import com.android.ide.common.resources.GeneratedResourceSet
import com.android.ide.common.resources.MergedResourceWriter
import com.android.ide.common.resources.MergedResourceWriterRequest
import com.android.ide.common.resources.MergingException
import com.android.ide.common.resources.NoOpResourcePreprocessor
import com.android.ide.common.resources.ResourceCompilationService
import com.android.ide.common.resources.ResourceMerger
import com.android.ide.common.resources.ResourcePreprocessor
import com.android.ide.common.resources.ResourceSet
import com.android.ide.common.resources.SingleFileProcessor
import com.android.ide.common.resources.getIdentifiedSourceSetMap
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.resources.Density
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.base.Preconditions
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.FileChange
import org.gradle.work.InputChanges
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.function.Supplier
import javax.xml.bind.JAXBException
import javax.xml.parsers.DocumentBuilderFactory

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeResources : NewIncrementalTask() {
    /**
     * Optional file to write any publicly imported resource types and names to
     */
    @get:Input
    var processResources = false
        private set

    @get:Input
    var crunchPng = false
        private set

    @get:Input
    abstract val minSdk: Property<Int>

    @Input
    fun getFlags(): String {
        return flags.map { it.name }.sorted().joinToString { "," }
    }

    @get:Input
    var isVectorSupportLibraryUsed = false
        private set

    @get:Input
    var enableVectorDrawables = true
        private set

    @get:Input
    var generatedDensities: Collection<String> = emptySet()
        private set

    @get:Input
    abstract val dataBindingEnabled: Property<Boolean>

    @get:Input
    abstract val viewBindingEnabled: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val namespace: Property<String>

    @get:Optional
    @get:Input
    abstract val useAndroidX: Property<Boolean>

    /**
     * Set of absolute paths to resource directories that are located outside the root project
     * directory when data binding / view binding is enabled.
     *
     *
     * These absolute paths appear in the layout info files generated by data binding, so we have
     * to mark them as @Input to ensure build correctness. (If this set is not empty, this task is
     * still cacheable but not relocatable.)
     *
     *
     * If data binding / view binding is not enabled, this set is empty as it won't be used.
     */
    @get:Input
    abstract val resourceDirsOutsideRootProjectDir: SetProperty<String>

    @get:Input
    abstract val pseudoLocalesEnabled: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    @get:InputFiles
    abstract val renderscriptGeneratedResDir: DirectoryProperty

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Nested
    abstract val resourcesComputer: DependencyResourcesComputer

    /**
     * Directory to write the generated vector drawable resources to
     */
    @get:Optional
    @get:OutputDirectory
    abstract val generatedPngsOutputDir: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val dataBindingLayoutInfoOutFolder: DirectoryProperty

    /**
     * Directory to write the merged resources to
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputFile
    @get:Optional
    abstract val publicFile: RegularFileProperty

    /**
     * Optional blame output folder for the case where the task generates it.
     */
    @get:Optional
    @get:OutputDirectory
    abstract val blameLogOutputFolder: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val incrementalFolder: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val mergedNotCompiledResourcesOutputDirectory: DirectoryProperty

    @get:Internal
    abstract val aaptEnv: Property<String?>

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @get:Internal
    val aaptWorkerFacade: WorkerExecutorFacade
        get() = withGradleWorkers(projectPath.get(), path, workerExecutor, analyticsService)

    @get:Internal
    abstract val aapt2ThreadPoolBuildService: Property<Aapt2ThreadPoolBuildService>

    private var precompileDependenciesResources = false
    private var flags: Set<Flag> = emptySet()
    private var errorFormatMode: ErrorFormatMode? = null
    private var processedInputs: MutableList<ResourceSet> = mutableListOf()
    private val fileValidity = FileValidity<ResourceSet>()

    @Throws(IOException::class, JAXBException::class)
    protected fun doFullTaskAction() {
        val preprocessor = preprocessor
        val incrementalFolder = incrementalFolder.get().asFile

        // this is full run, clean the previous outputs
        val destinationDir = outputDir.get().asFile
        FileUtils.cleanOutputDir(destinationDir)
        if (dataBindingLayoutInfoOutFolder.isPresent) {
            FileUtils.deleteDirectoryContents(
                dataBindingLayoutInfoOutFolder.get().asFile
            )
        }
        val resourceSets = getConfiguredResourceSets(preprocessor, aaptEnv.orNull)

        // create a new merger and populate it with the sets.
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val merger = ResourceMerger(minSdk.get(), documentBuilderFactory)
        var mergingLog: MergingLog? = null
        if (blameLogOutputFolder.isPresent) {
            val blameLogFolder = blameLogOutputFolder.get().asFile
            FileUtils.cleanOutputDir(blameLogFolder)
            mergingLog = MergingLog(blameLogFolder)
        }
        try {
            aaptWorkerFacade.use { workerExecutorFacade ->
                getResourceProcessor(
                    this,
                    flags,
                    processResources,
                    aapt2
                ).use { resourceCompiler ->
                    val dataBindingLayoutProcessor = maybeCreateLayoutProcessor()
                    Blocks.recordSpan<MergingException>(
                        path,
                        GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_1,
                        analyticsService.get()
                    ) {
                        for (resourceSet in resourceSets) {
                            resourceSet.loadFromFiles(LoggerWrapper(logger))
                            merger.addDataSet(resourceSet)
                        }
                    }
                    val publicFile = if (publicFile.isPresent) publicFile.get().asFile else null
                    val sourceSetPaths =
                        getRelativeSourceSetMap(resourceSets, destinationDir, incrementalFolder)
                    val writer = MergedResourceWriter(
                        MergedResourceWriterRequest(
                            workerExecutor = workerExecutorFacade,
                            rootFolder = destinationDir,
                            publicFile = publicFile,
                            blameLog = mergingLog,
                            preprocessor = preprocessor,
                            resourceCompilationService = resourceCompiler,
                            temporaryDirectory = incrementalFolder,
                            dataBindingExpressionRemover = dataBindingLayoutProcessor,
                            notCompiledOutputDirectory = mergedNotCompiledResourcesOutputDirectory.orNull?.asFile,
                            pseudoLocalesEnabled = pseudoLocalesEnabled.get(),
                            crunchPng = crunchPng,
                            moduleSourceSets = sourceSetPaths
                        )
                    )
                    Blocks.recordSpan<MergingException>(
                        path,
                        GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_2,
                        analyticsService.get()
                    ) { merger.mergeData(writer, false /*doCleanUp*/) }
                    Blocks.recordSpan<JAXBException>(
                        path,
                        GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_3,
                        analyticsService.get()
                    ) { dataBindingLayoutProcessor?.end() }

                    // No exception? Write the known state.
                    Blocks.recordSpan<MergingException>(
                        path,
                        GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_4,
                        analyticsService.get()
                    ) { merger.writeBlobTo(incrementalFolder, writer, false) }
                }
            }
        } catch (e: Exception) {
            MergingException.findAndReportMergingException(
                e, MessageReceiverImpl(errorFormatMode!!, logger)
            )
            try {
                throw e
            } catch (mergingException: MergingException) {
                merger.cleanBlob(incrementalFolder)
                throw ResourceException(mergingException.message, mergingException)
            }
        } finally {
            cleanup()
        }
    }

    /**
     * Check if the changed file is filtered out from the input to be compiled in compile library
     * resources task, if it is then it should be ignored.
     */
    private fun isFilteredOutLibraryResource(changedFile: File): Boolean {
        val localLibraryResources: FileCollection = resourcesComputer.librarySourceSets
        val parentFile = changedFile.parentFile
        if (parentFile.name.startsWith(SdkConstants.FD_RES_VALUES)) {
            return false
        }
        for (resDir in localLibraryResources.files) {
            if (parentFile.absolutePath.startsWith(resDir.absolutePath)) {
                return true
            }
        }
        return false
    }

    override fun doTaskAction(inputChanges: InputChanges) {
        if (!inputChanges.isIncremental) {
            try {
                logger.info("[MergeResources] Inputs are non-incremental full task action.")
                doFullTaskAction()
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: JAXBException) {
                throw RuntimeException(e)
            }
            return
        }
        val preprocessor = preprocessor
        val incrementalFolder = incrementalFolder.get().asFile
        val thisProjectResourceChanges: List<FileChange> = ArrayList()
        for (input in resourcesComputer.resources.get().values) {
            val changes: Iterable<FileChange> =
                inputChanges.getFileChanges(input.sourceDirectories)
            Iterables.addAll(thisProjectResourceChanges, changes)
        }
        val libraryResourceChanges = inputChanges.getFileChanges(
            resourcesComputer.librarySourceSets
        )
        if (!thisProjectResourceChanges.iterator().hasNext()
            && !libraryResourceChanges.iterator().hasNext()
        ) {
            return
        }
        // create a merger and load the known state.
        val merger = ResourceMerger(minSdk.get())
        try {
            if (!merger.loadFromBlob(
                    incrementalFolder, true /*incrementalState*/, aaptEnv.orNull
                )
            ) {
                logger
                    .info("[MergeResources] Blob cannot be loaded causing a full task action.")
                doFullTaskAction()
                return
            }
            for (resourceSet in merger.dataSets) {
                resourceSet.setPreprocessor(preprocessor)
            }
            val resourceSets = getConfiguredResourceSets(preprocessor, aaptEnv.orNull)

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                logger.info("Changed Resource sets: full task run!")
                doFullTaskAction()
                return
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (entry in thisProjectResourceChanges) {
                if (!precompileDependenciesResources
                    || !isFilteredOutLibraryResource(entry.file)
                ) {
                    if (!tryUpdateResourceSetsWithChangedFile(merger, entry)) {
                        doFullTaskAction()
                        return
                    }
                }
            }
            for (entry in libraryResourceChanges) {
                if (!precompileDependenciesResources
                    || !isFilteredOutLibraryResource(entry.file)
                ) {
                    if (!tryUpdateResourceSetsWithChangedFile(merger, entry)) {
                        doFullTaskAction()
                        return
                    }
                }
            }
            val mergingLog = if (blameLogOutputFolder.isPresent) MergingLog(
                blameLogOutputFolder.get().asFile
            ) else null
            aaptWorkerFacade.use { workerExecutorFacade ->
                getResourceProcessor(
                    this,
                    flags,
                    processResources,
                    aapt2
                ).use { resourceCompiler ->
                    val dataBindingLayoutProcessor = maybeCreateLayoutProcessor()
                    val publicFile = if (publicFile.isPresent) publicFile.get().asFile else null
                    val destinationDir = outputDir.get().asFile
                    val sourceSetPaths =
                        getRelativeSourceSetMap(resourceSets, destinationDir, incrementalFolder)
                    val writer = MergedResourceWriter(
                        MergedResourceWriterRequest(
                            workerExecutorFacade,
                            destinationDir,
                            publicFile,
                            mergingLog,
                            preprocessor,
                            resourceCompiler,
                            incrementalFolder,
                            dataBindingLayoutProcessor,
                            mergedNotCompiledResourcesOutputDirectory.orNull?.asFile,
                            pseudoLocalesEnabled.get(),
                            crunchPng,
                            sourceSetPaths
                        )
                    )
                    merger.mergeData(writer, false /*doCleanUp*/)
                    dataBindingLayoutProcessor?.end()

                    // No exception? Write the known state.
                    merger.writeBlobTo(incrementalFolder, writer, false)
                }
            }
        } catch (e: Exception) {
            MergingException.findAndReportMergingException(
                e, MessageReceiverImpl(errorFormatMode!!, logger)
            )
            try {
                throw e
            } catch (mergingException: MergingException) {
                merger.cleanBlob(incrementalFolder)
                throw ResourceException(mergingException.message, mergingException)
            } catch (runTimeException: Exception) {
                throw RuntimeException(runTimeException)
            }
        } finally {
            cleanup()
        }
    }

    @Throws(MergingException::class)
    private fun tryUpdateResourceSetsWithChangedFile(
        merger: ResourceMerger,
        entry: FileChange
    ): Boolean {
        val changedFile = entry.file
        merger.findDataSetContaining(changedFile, fileValidity)
        if (fileValidity.status == FileValidity.FileStatus.UNKNOWN_FILE) {
            logger
                .info(
                    "[MergeResources] "
                            + changedFile.absolutePath
                            + " has an unknown file status, requiring full task run."
                )
            return false
        } else if (fileValidity.status == FileValidity.FileStatus.VALID_FILE) {
            if (!fileValidity
                    .dataSet
                    .updateWith(
                        fileValidity.sourceFile,
                        changedFile,
                        entry.changeType.toSerializable(),
                        LoggerWrapper(logger)
                    )
            ) {
                logger
                    .info(
                        String.format(
                            "[MergeResources] Failed to process %s event! "
                                    + "Requires full task run",
                            entry.changeType
                        )
                    )
                return false
            }
        }
        return true
    }

    private fun getRelativeSourceSetMap(
        resourceSets: List<ResourceSet>, destinationDir: File, incrementalFolder: File
    ): Map<String, String> {
        val sourceSets = resourceSets.flatMap { it.sourceFiles }.toMutableList()

        if (generatedPngsOutputDir.isPresent) {
            sourceSets.add(generatedPngsOutputDir.get().asFile)
        }
        mergedNotCompiledResourcesOutputDirectory.orNull?.asFile?.let {
            if (it.exists()) {
                sourceSets.add(it)
            }
        }
        sourceSets.add(destinationDir)
        sourceSets.add(FileUtils.join(incrementalFolder, SdkConstants.FD_MERGED_DOT_DIR))
        sourceSets.add(FileUtils.join(incrementalFolder, SdkConstants.FD_STRIPPED_DOT_DIR))
        return getIdentifiedSourceSetMap(sourceSets, namespace.get(), projectPath.get())
    }

    private fun maybeCreateLayoutProcessor(): SingleFileProcessor? {
        if (!dataBindingEnabled.get() && !viewBindingEnabled.get()) {
            return null
        }
        val fileLookup: OriginalFileLookup = if (blameLogOutputFolder.isPresent) {
            MergingFileLookup(blameLogOutputFolder.get().asFile)
        } else {
            OriginalFileLookup { null }
        }
        val processor = LayoutXmlProcessor(
            namespace.get(),
            object : JavaFileWriter() {
                // These methods are not supposed to be used, they are here only because
                // the superclass requires an implementation of it.
                // Whichever is calling this method is probably using it incorrectly
                // (see stacktrace).
                override fun writeToFile(canonicalName: String, contents: String) {
                    throw UnsupportedOperationException(
                        "Not supported in this mode"
                    )
                }

                override fun deleteFile(canonicalName: String) {
                    throw UnsupportedOperationException(
                        "Not supported in this mode"
                    )
                }
            },
            fileLookup,
            useAndroidX.get()
        )
        return object : SingleFileProcessor {

            @Throws(Exception::class)
            override fun processSingleFile(
                inputFile: File,
                outputFile: File,
                inputFileIsFromDependency: Boolean?
            ): Boolean {
                // Data binding doesn't need/want to process layout files that come
                // from dependencies (see bug 132637061).
                if (inputFileIsFromDependency === java.lang.Boolean.TRUE) {
                    return false
                }

                // For cache relocatability, we want to use relative paths, but we
                // can do that only for resource files that are located within the
                // root project directory.
                val rootProjectDir = projectRootDir.asFile.get()
                val normalizedInputFile: RelativizableFile
                if (FileUtils.isFileInDirectory(inputFile, rootProjectDir)) {
                    // Check that the layout file's absolute path is not an @Input (i.e., it must
                    // not be located in resourceDirsOutsideRootProjectDir, which is an @Input)
                    check(
                        !resourceIsInResourceDirs(
                            inputFile, resourceDirsOutsideRootProjectDir.get()
                        )
                    ) {
                        "Layout file ${inputFile.canonicalPath} is not expected be located in " +
                                resourceDirsOutsideRootProjectDir.get()
                                    .joinToString(", ", "[", "]") { File(it).canonicalPath }
                    }

                    // The base directory of the relative path has to be the root
                    // project directory, not the current project directory, because
                    // the consumer on the IDE side relies on that---see bug
                    // 128643036.
                    normalizedInputFile = RelativizableFile.fromAbsoluteFile(
                        inputFile.canonicalFile, rootProjectDir
                    )
                    Preconditions.checkState(normalizedInputFile.relativeFile != null)
                } else {
                    // Check that the layout file's absolute path is an @Input (i.e., it must be
                    // located in resourceDirsOutsideRootProjectDir, which is an @Input)
                    check(
                        resourceIsInResourceDirs(
                            inputFile, resourceDirsOutsideRootProjectDir.get()
                        )
                    ) {
                        "Layout file ${inputFile.canonicalPath} is not located in " +
                                resourceDirsOutsideRootProjectDir.get()
                                    .joinToString(", ", "[", "]") { File(it).canonicalPath }
                    }
                    normalizedInputFile =
                        RelativizableFile.fromAbsoluteFile(inputFile.canonicalFile, null)
                    Preconditions.checkState(normalizedInputFile.relativeFile == null)
                }
                return processor
                    .processSingleFile(
                        normalizedInputFile,
                        outputFile,
                        viewBindingEnabled.get(),
                        dataBindingEnabled.get()
                    )
            }

            /**
             * Returns `true` if the given resource file is located inside one of the resource
             * directories.
             */
            private fun resourceIsInResourceDirs(
                resFile: File, resDirs: Set<String>
            ): Boolean {
                return resDirs.any { resDir: String ->
                        FileUtils.isFileInDirectory(
                            resFile,
                            File(resDir)
                        )
                    }
            }

            override fun processRemovedFile(file: File) {
                processor.processRemovedFile(file)
            }

            override fun processFileWithNoDataBinding(file: File) {
                processor.processFileWithNoDataBinding(file)
            }

            @Throws(JAXBException::class)
            override fun end() {
                processor
                    .writeLayoutInfoFiles(
                        dataBindingLayoutInfoOutFolder.get().asFile
                    )
            }
        }
    }

    private class MergeResourcesVectorDrawableRenderer(
        minSdk: Int,
        supportLibraryIsUsed: Boolean,
        outputDir: File,
        densities: Collection<Density>,
        loggerSupplier: Supplier<ILogger?>?
    ) : VectorDrawableRenderer(
        minSdk,
        supportLibraryIsUsed,
        outputDir,
        densities,
        loggerSupplier!!
    ) {
        @Throws(IOException::class)
        override fun generateFile(toBeGenerated: File, original: File) {
            try {
                super.generateFile(toBeGenerated, original)
            } catch (e: ResourcesNotSupportedException) {
                // Add gradle-specific error message.
                throw GradleException(
                    String.format(
                        """
                    Can't process attribute %1${"$"}s="%2${"$"}s": references to other resources are not supported by build-time PNG generation.
                    %3${"$"}s
                    See http://developer.android.com/tools/help/vector-asset-studio.html for details.
                    """.trimIndent(),
                        e.name,
                        e.value,
                        getPreprocessingReasonDescription(original)
                    )
                )
            }
        }
    }// If the user doesn't want any PNGs, leave the XML file alone as well.

    /**
     * Only one pre-processor for now. The code will need slight changes when we add more.
     */
    private val preprocessor: ResourcePreprocessor
        get() {
            if (enableVectorDrawables.not()) {
                // If the user doesn't want any PNGs, leave the XML file alone as well.
                return NoOpResourcePreprocessor.INSTANCE
            }
            val densities = generatedDensities.map { value -> Density.getEnum(value) }
            return MergeResourcesVectorDrawableRenderer(
                minSdk.get(),
                isVectorSupportLibraryUsed,
                generatedPngsOutputDir.get().asFile,
                densities,
                LoggerWrapper.supplierFor(MergeResources::class.java)
            )
        }

    private fun getConfiguredResourceSets(
        preprocessor: ResourcePreprocessor, aaptEnv: String?
    ): List<ResourceSet> {
        // It is possible that this get called twice in case the incremental run fails and reverts
        // to full task run. Because the cached ResourceList is modified we don't want
        // to recompute this twice (plus, why recompute it twice anyway?)
        if (processedInputs.isEmpty()) {
            processedInputs.addAll(
                resourcesComputer.compute(
                    precompileDependenciesResources,
                    aaptEnv,
                    renderscriptGeneratedResDir
                )
            )
            val generatedSets: MutableList<ResourceSet> = ArrayList(
                processedInputs.size
            )
            for (resourceSet in processedInputs) {
                resourceSet.setPreprocessor(preprocessor)
                val generatedSet: ResourceSet = GeneratedResourceSet(resourceSet, aaptEnv)
                resourceSet.setGeneratedSet(generatedSet)
                generatedSets.add(generatedSet)
            }

            // We want to keep the order of the inputs. Given inputs:
            // (A, B, C, D)
            // We want to get:
            // (A-generated, A, B-generated, B, C-generated, C, D-generated, D).
            // Therefore, when later in {@link DataMerger} we look for sources going through the
            // list backwards, B-generated will take priority over A (but not B).
            // A real life use-case would be if an app module generated resource overrode a library
            // module generated resource (existing not in generated but bundled dir at this stage):
            // (lib, app debug, app main)
            // We will get:
            // (lib generated, lib, app debug generated, app debug, app main generated, app main)
            for (i in generatedSets.indices) {
                processedInputs.add(2 * i, generatedSets[i])
            }
        }
        return processedInputs
    }

    /**
     * Releases resource sets not needed anymore, otherwise they will waste heap space for the
     * duration of the build.
     *
     *
     * This might be called twice when an incremental build falls back to a full one.
     */
    private fun cleanup() {
        fileValidity.clear()
        processedInputs.clear()
    }

    class KotlinMultiplatformCreationAction(
        creationConfig: KmpCreationConfig,
        private val flags: Set<Flag> = emptySet(),
    ) : VariantTaskCreationAction<MergeResources, KmpCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("package", "Resources")
        override val type: Class<MergeResources>
            get() = MergeResources::class.java

        override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.mergeResourcesTask = taskProvider

            val artifacts = creationConfig.artifacts
            artifacts.setInitialProvider(taskProvider) { obj: MergeResources -> obj.outputDir }
                .on(InternalArtifactType.PACKAGED_RES)

            artifacts
                .use(taskProvider)
                .wiredWith { obj: MergeResources -> obj.incrementalFolder }
                .toCreate(MERGED_RES_INCREMENTAL_FOLDER)
        }

        override fun configure(task: MergeResources) {
            super.configure(task)
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.minSdk.setDisallowChanges(
                task.project.provider { creationConfig.minSdk.apiLevel }
            )
            task.useAndroidX.setDisallowChanges(
                creationConfig.services.projectOptions[BooleanOption.USE_ANDROID_X]
            )
            task.flags = flags
            task.resourcesComputer.initFromVariantScope(
                creationConfig = creationConfig,
                microApkResDir = creationConfig.services.fileCollection(),
                libraryDependencies = null,
            )
            task.aapt2ThreadPoolBuildService.setDisallowChanges(
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    Aapt2ThreadPoolBuildService::class.java
                )
            )
            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            task.aaptEnv
                .set(
                    creationConfig
                        .services
                        .gradleEnvironmentProvider
                        .getEnvVariable(ANDROID_AAPT_IGNORE)
                )
            task.projectRootDir.set(task.project.rootDir)
            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.services.projectOptions
            )
            task.dataBindingEnabled.setDisallowChanges(false)
            task.viewBindingEnabled.setDisallowChanges(false)
            task.resourceDirsOutsideRootProjectDir.setDisallowChanges(
                task.project.provider { emptySet() }
            )
            task.pseudoLocalesEnabled.setDisallowChanges(false)
            task.enableVectorDrawables = false
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig,
        private val mergeType: TaskManager.MergeType,
        private val mergedNotCompiledOutputDirectory: Provider<Directory>?,
        private val includeDependencies: Boolean,
        private val processResources: Boolean,
        private val flags: Set<Flag>,
        private val isLibrary: Boolean
    ) : VariantTaskCreationAction<MergeResources, ComponentCreationConfig>(creationConfig),
        AndroidResourcesTaskCreationAction by AndroidResourcesTaskCreationActionImpl(
            creationConfig
        ) {
        private val processVectorDrawables: Boolean = flags.contains(Flag.PROCESS_VECTOR_DRAWABLES)

        override val name: String
            get() = computeTaskName(mergeType.name.lowercase(Locale.ENGLISH), "Resources")
        override val type: Class<MergeResources>
            get() = MergeResources::class.java

        override fun handleProvider(taskProvider: TaskProvider<MergeResources>) {
            super.handleProvider(taskProvider)
            // In LibraryTaskManager#createMergeResourcesTasks, there are actually two
            // MergeResources tasks sharing the same task type (MergeResources) and CreationAction
            // code: packageResources with mergeType == PACKAGE, and mergeResources with
            // mergeType == MERGE. Since the following line of code is called for each task, the
            // latter one wins: The mergeResources task with mergeType == MERGE is the one that is
            // finally registered in the current scope.
            // Filed https://issuetracker.google.com//110412851 to clean this up at some point.
            creationConfig.taskContainer.mergeResourcesTask = taskProvider
            val artifacts = creationConfig.artifacts
            artifacts.setInitialProvider(taskProvider) { obj: MergeResources -> obj.outputDir }
                .on(mergeType.outputType)
            if (mergedNotCompiledOutputDirectory != null) {
                artifacts.setInitialProvider(taskProvider) { obj: MergeResources -> obj.mergedNotCompiledResourcesOutputDirectory }
                    .atLocation(mergedNotCompiledOutputDirectory)
                    .on(MERGED_NOT_COMPILED_RES)
            }
            artifacts.setInitialProvider(taskProvider) { obj: MergeResources -> obj.dataBindingLayoutInfoOutFolder }
                .withName("out")
                .on(
                    if (mergeType === TaskManager.MergeType.MERGE) DATA_BINDING_LAYOUT_INFO_TYPE_MERGE else DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE
                )

            // only the full run with dependencies generates the blame folder
            if (includeDependencies) {
                artifacts.setInitialProvider(taskProvider) { obj: MergeResources -> obj.blameLogOutputFolder }
                    .withName("out")
                    .on(InternalArtifactType.MERGED_RES_BLAME_FOLDER)
            }

            // use public API instead of setInitialProvider to ensure that the task name is embedded
            // in the created directory path.
            artifacts
                .use(taskProvider)
                .wiredWith { obj: MergeResources -> obj.incrementalFolder }
                .toCreate(MERGED_RES_INCREMENTAL_FOLDER)
            val vectorDrawablesOptions = androidResourcesCreationConfig.vectorDrawables
            val enableVectorDrawables = processVectorDrawables && vectorDrawablesOptions.generatedDensities != null
            if (enableVectorDrawables) {
                artifacts.setInitialProvider(
                    taskProvider
                ) { obj: MergeResources -> obj.generatedPngsOutputDir }.atLocation(
                    creationConfig.paths
                        .getGeneratedResourcesDir("pngs")
                        .get()
                        .asFile
                        .absolutePath
                ).on(InternalArtifactType.GENERATED_PNGS_RES)
            }
        }

        override fun configure(task: MergeResources) {
            super.configure(task)
            task.namespace.setDisallowChanges(creationConfig.namespace)
            task.minSdk
                .setDisallowChanges(
                    task.project
                        .provider { creationConfig.minSdk.apiLevel })
            task.processResources = processResources
            task.crunchPng = androidResourcesCreationConfig.isCrunchPngs
            val vectorDrawablesOptions = androidResourcesCreationConfig.vectorDrawables
            task.generatedDensities = vectorDrawablesOptions.generatedDensities ?: emptySet()
            task.enableVectorDrawables = processVectorDrawables && task.generatedDensities.isNotEmpty()

            // TODO: When support library starts supporting gradients (http://b/62421666), remove
            // the vectorSupportLibraryIsUsed field and set disableVectorDrawables when
            // the getUseSupportLibrary method returns TRUE.
            task.isVectorSupportLibraryUsed =
                java.lang.Boolean.TRUE == vectorDrawablesOptions.useSupportLibrary
            val libraryArtifacts = if (includeDependencies) creationConfig
                .variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.ANDROID_RES
                ) else null
            val microApk: FileCollection = creationConfig
                .services
                .fileCollection(
                    creationConfig
                        .artifacts
                        .get(InternalArtifactType.MICRO_APK_RES)
                )
            task.renderscriptGeneratedResDir.setDisallowChanges(
                creationConfig.artifacts.get(InternalArtifactType.RENDERSCRIPT_GENERATED_RES))

            task.resourcesComputer.initFromVariantScope(
                creationConfig = creationConfig,
                microApkResDir = microApk,
                libraryDependencies = libraryArtifacts,
            )
            val features = creationConfig.buildFeatures
            val isDataBindingEnabled = features.dataBinding
            val isViewBindingEnabled = features.viewBinding
            task.dataBindingEnabled.setDisallowChanges(isDataBindingEnabled)
            task.viewBindingEnabled.setDisallowChanges(isViewBindingEnabled)
            if (isDataBindingEnabled || isViewBindingEnabled) {
                task.useAndroidX.setDisallowChanges(
                    creationConfig
                        .services
                        .projectOptions[BooleanOption.USE_ANDROID_X]
                )
            }

            task.pseudoLocalesEnabled.setDisallowChanges(
                androidResourcesCreationConfig.pseudoLocalesEnabled
            )
            task.flags = flags
            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                creationConfig.services.projectOptions
            )
            task.precompileDependenciesResources =
                (mergeType == TaskManager.MergeType.MERGE && !isLibrary
                        && androidResourcesCreationConfig.isPrecompileDependenciesResourcesEnabled)
            task.resourceDirsOutsideRootProjectDir
                .set(
                    task.project
                        .provider {
                            getResourcesDirsOutsideRoot(
                                task,
                                isDataBindingEnabled,
                                isViewBindingEnabled
                            )
                        })
            task.resourceDirsOutsideRootProjectDir.disallowChanges()
            task.dependsOn(creationConfig.taskContainer.resourceGenTask)

            task.aapt2ThreadPoolBuildService.setDisallowChanges(
                getBuildService(
                    creationConfig.services.buildServiceRegistry,
                    Aapt2ThreadPoolBuildService::class.java
                )
            )
            creationConfig.services.initializeAapt2Input(task.aapt2, task)
            task.aaptEnv
                .set(
                    creationConfig
                        .services
                        .gradleEnvironmentProvider
                        .getEnvVariable(ANDROID_AAPT_IGNORE)
                )
            task.projectRootDir.set(task.project.rootDir)
        }
    }

    enum class Flag {
        REMOVE_RESOURCE_NAMESPACES, PROCESS_VECTOR_DRAWABLES
    }

    companion object {
        @Throws(IOException::class)
        private fun getResourcesDirsOutsideRoot(
            task: MergeResources,
            isDataBindingEnabled: Boolean,
            isViewBindingEnabled: Boolean
        ): Set<String> {
            val resourceDirsOutsideRootProjectDir: MutableSet<String> = HashSet()
            if (!isDataBindingEnabled && !isViewBindingEnabled) {
                // This set is used only when data binding / view binding is enabled
                return resourceDirsOutsideRootProjectDir
            }

            // In this task, data binding doesn't process layout files that come from dependencies
            // (see the code at processSingleFile()). Therefore, we'll look at resources in the
            // current subproject only (via task.getLocalResources()). These resources are usually
            // located inside the root project directory, but some of them may come from custom
            // resource sets that are outside the root project directory, so we need to collect
            // the latter set.
            val rootProjectDir = task.project.rootDir
            for (resourceSourceSet in task.resourcesComputer.resources.get().values) {
                for (resDir in resourceSourceSet.sourceDirectories.files) {
                    if (!FileUtils.isFileInDirectory(resDir, rootProjectDir)) {
                        resourceDirsOutsideRootProjectDir.add(resDir.canonicalPath)
                    }
                }
            }
            return resourceDirsOutsideRootProjectDir
        }
        private fun getResourceProcessor(
            mergeResourcesTask: MergeResources,
            flags: Set<Flag>,
            processResources: Boolean,
            aapt2Input: Aapt2Input
        ): ResourceCompilationService {
            // If we received the flag for removing namespaces we need to use the namespace remover to
            // process the resources.
            if (flags.contains(Flag.REMOVE_RESOURCE_NAMESPACES)) {
                return NamespaceRemover
            }

            // If we're not removing namespaces and there's no need to compile the resources, return a
            // no-op resource processor.
            return if (!processResources) {
                CopyToOutputDirectoryResourceCompilationService
            } else WorkerExecutorResourceCompilationService(
                mergeResourcesTask.projectPath,
                mergeResourcesTask.path,
                mergeResourcesTask.workerExecutor,
                mergeResourcesTask.analyticsService,
                aapt2Input
            )
        }
    }
}
