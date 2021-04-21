/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.TransformException
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.crash.PluginCrashReporter
import com.android.build.gradle.internal.dependency.getDexingArtifactConfiguration
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexingType
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessOutput
import com.android.utils.FileUtils
import com.android.utils.PathUtils.toSystemIndependentPath
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Throwables
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.InputChanges
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Dex merging task. This task will merge all specified dex files, using the specified parameters.
 *
 * If handles all dexing types, as specified in [DexingType].
 *
 * One of the interesting properties is [mergingThreshold]. For dex file inputs, this property will
 * determine if dex files can be just copied to output, or we have to merge them. If the number of
 * dex files is at least [mergingThreshold], the files will be merged in a single invocation.
 * Otherwise, we will just copy the dex files to the output directory.
 */
@CacheableTask
abstract class DexMergingTask : NewIncrementalTask() {

    @get:Input
    abstract val dexingType: Property<DexingType>

    @get:Input
    abstract val dexMerger: Property<DexMergerTool>

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val mergingThreshold: Property<Int>

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mainDexListFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var dexFiles: FileCollection
        private set

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fileDependencyDexFiles: DirectoryProperty

    // Dummy folder, used as a way to set up dependency
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val duplicateClassesCheck: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

    @get:Internal
    abstract val dxStateBuildService: Property<DxStateBuildService>

    override fun doTaskAction(inputChanges: InputChanges) {
        // TODO(132615300) Make this task incremental
        getWorkerFacadeWithWorkers().use {
            it.submit(
                DexMergingTaskRunnable::class.java,
                DexMergingParams(
                    dexingType.get(),
                    errorFormatMode.get(),
                    dexMerger.get(),
                    minSdkVersion.get(),
                    debuggable.get(),
                    mergingThreshold.get(),
                    mainDexListFile.orNull?.asFile,
                    dexFiles.files,
                    fileDependencyDexFiles.orNull?.asFile,
                    outputDir.get().asFile
                )
            )
        }
        if (dexMerger.get() == DexMergerTool.DX) {
            dxStateBuildService.get().clearStateAfterBuild()
        }
    }

    class CreationAction @JvmOverloads constructor(
        componentProperties: ComponentPropertiesImpl,
        private val action: DexMergingAction,
        private val dexingType: DexingType,
        private val dexingUsingArtifactTransforms: Boolean = true,
        private val separateFileDependenciesDexingTask: Boolean = false,
        private val outputType: InternalMultipleArtifactType = InternalMultipleArtifactType.DEX
    ) : VariantTaskCreationAction<DexMergingTask, ComponentPropertiesImpl>(componentProperties) {

        private val internalName: String = when (action) {
            DexMergingAction.MERGE_LIBRARY_PROJECTS -> componentProperties.computeTaskName("mergeLibDex")
            DexMergingAction.MERGE_EXTERNAL_LIBS -> componentProperties.computeTaskName("mergeExtDex")
            DexMergingAction.MERGE_PROJECT -> componentProperties.computeTaskName("mergeProjectDex")
            DexMergingAction.MERGE_ALL -> componentProperties.computeTaskName("mergeDex")
        }

        override val name = internalName
        override val type = DexMergingTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<DexMergingTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .use(taskProvider)
                .wiredWith(DexMergingTask::outputDir)
                .toAppendTo(outputType)
        }

        override fun configure(task: DexMergingTask) {
            super.configure(task)

            task.dexFiles = getDexFiles(creationConfig, action)
            task.mergingThreshold.setDisallowChanges(getMergingThreshold(action, creationConfig))

            task.dexingType.setDisallowChanges(dexingType)
            if (DexMergingAction.MERGE_ALL == action && dexingType === DexingType.LEGACY_MULTIDEX) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST,
                    task.mainDexListFile)
            }

            task.errorFormatMode.setDisallowChanges(
                SyncOptions.getErrorFormatMode(creationConfig.services.projectOptions))
            task.dexMerger.setDisallowChanges(creationConfig.variantScope.dexMerger)
            task.minSdkVersion.setDisallowChanges(
                creationConfig.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel)
            task.debuggable.setDisallowChanges(creationConfig.variantDslInfo.isDebuggable)
            if (creationConfig.services
                    .projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.DUPLICATE_CLASSES_CHECK,
                    task.duplicateClassesCheck
                )
            }
            if (separateFileDependenciesDexingTask) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.EXTERNAL_FILE_LIB_DEX_ARCHIVES,
                    task.fileDependencyDexFiles
                )
            }
            task.dxStateBuildService.setDisallowChanges(
                DxStateBuildService.RegistrationAction(task.project).execute())
        }

        private fun getDexFiles(
            component: ComponentPropertiesImpl,
            action: DexMergingAction
        ): FileCollection {
            val attributes = getDexingArtifactConfiguration(component).getAttributes()

            fun forAction(action: DexMergingAction): FileCollection {
                when (action) {
                    DexMergingAction.MERGE_EXTERNAL_LIBS -> {
                        return if (dexingUsingArtifactTransforms) {
                            // If the file dependencies are being dexed in a task, don't also include them here
                            val artifactScope: AndroidArtifacts.ArtifactScope = if (separateFileDependenciesDexingTask) {
                                AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE
                            } else {
                                AndroidArtifacts.ArtifactScope.EXTERNAL
                            }
                            component.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                artifactScope,
                                AndroidArtifacts.ArtifactType.DEX,
                                attributes
                            )
                        } else {
                            component.globalScope.project.files(
                                component.artifacts.get(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE),
                                component.artifacts.get(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS)
                            )
                        }
                    }
                    DexMergingAction.MERGE_LIBRARY_PROJECTS -> {
                        return if (dexingUsingArtifactTransforms) {
                            component.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.PROJECT,
                                AndroidArtifacts.ArtifactType.DEX,
                                attributes
                            )
                        } else {
                            component.globalScope.project.files(
                                component.artifacts.get(InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE))
                        }
                    }
                    DexMergingAction.MERGE_PROJECT -> {
                        val files =
                            component.globalScope.project.files(
                                component.artifacts.get(InternalArtifactType.PROJECT_DEX_ARCHIVE),
                                component.artifacts.get(InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE)
                            )

                        val variantType = component.variantType
                        if (variantType.isApk) {
                            component.onTestedConfig {
                                if (dexingUsingArtifactTransforms && it.variantType.isAar) {
                                    // If dexing using artifact transforms, library production code will
                                    // be dex'ed in a task, so we need to fetch the output directly.
                                    // Otherwise, it will be in the dex'ed in the dex builder transform.
                                    files.from(it.artifacts.getAll(InternalMultipleArtifactType.DEX))
                                }
                            }
                        }

                        return files
                    }
                    DexMergingAction.MERGE_ALL -> {
                        // technically, the Provider<> may not be needed, but the code would
                        // then assume that EXTERNAL_LIBS_DEX has already been registered by a
                        // Producer. Better to execute as late as possible.
                        return component.globalScope.project.files(
                            forAction(DexMergingAction.MERGE_PROJECT),
                            forAction(DexMergingAction.MERGE_LIBRARY_PROJECTS),
                            if (dexingType == DexingType.LEGACY_MULTIDEX) {
                                // we have to dex it
                                forAction(DexMergingAction.MERGE_EXTERNAL_LIBS)
                            } else {
                                // we merge external dex in a separate task
                                component.artifacts.getAll(InternalMultipleArtifactType.EXTERNAL_LIBS_DEX)
                            })
                    }
                }
            }

            return forAction(action)
        }

        /**
         * Get the number of dex files that will trigger merging of those files in a single
         * invocation. Project and external libraries dex files are always merged as much as possible,
         * so this only matters for the library projects dex files. See [LIBRARIES_MERGING_THRESHOLD]
         * for details.
         */
        private fun getMergingThreshold(
            action: DexMergingAction,
            component: ComponentPropertiesImpl
        ): Int {
            return when (action) {
                DexMergingAction.MERGE_LIBRARY_PROJECTS ->
                    when {
                        component.variantDslInfo.minSdkVersionWithTargetDeviceApi.featureLevel < 23 ->
                            LIBRARIES_MERGING_THRESHOLD
                        else -> LIBRARIES_M_PLUS_MAX_THRESHOLD
                    }
                else -> 0
            }
        }
    }
}

/**
 * This returns a list of files from a set of files. If a file is in the set of files it is
 * added to the resulting set. If it is a directory, all files all collected recursively, and they
 * are sorted. This ensures that files from a single directory are always in deterministic order.
 *
 * We do not sort all files from a file collection as Gradle ensures consistent ordering of file
 * collection content across builds. This holds for artifact transform outputs that are in the file
 * collection. In fact, sorting it means that artifact transform outputs for library projects will
 * not be consistent across builds. See http://b/119064593#comment11 for details.
 */
private fun getAllRegularFiles(files: Iterable<File>): List<File> {
    return files.flatMap {
        if (it.isFile) listOf(it)
        else {
            it.walkTopDown()
                .filter(File::isFile)
                .sortedWith(
                    Comparator { left, right ->
                        val systemIndependentLeft = toSystemIndependentPath(left.toPath())
                        val systemIndependentRight = toSystemIndependentPath(right.toPath())
                        systemIndependentLeft.compareTo(systemIndependentRight)
                    }
                )
                .toList()
        }
    }
}

/**
 * Native multidex mode on android L does not support more
 * than 100 DEX files (see <a href="http://b.android.com/233093">http://b.android.com/233093</a>).
 *
 * We assume the maximum number of dexes that will be produced from the external dependencies and
 * project dex files is 50. The remaining 50 dex file can be used for library project.
 *
 * This means that if the number of library dex files is 51+, we might merge all of them when minSdk
 * is 21 or 22.
 */
internal const val LIBRARIES_MERGING_THRESHOLD = 51
/**
 * Max number of DEX files to generate on 23+, above that dex2out might have issues. See
 * http://b/110374966 for more info.
 */
internal const val LIBRARIES_M_PLUS_MAX_THRESHOLD = 500

enum class DexMergingAction {
    /** Merge only external libraries' dex files. */
    MERGE_EXTERNAL_LIBS,
    /** Merge only library projects' dex files. */
    MERGE_LIBRARY_PROJECTS,
    /** Merge only project's dex files. */
    MERGE_PROJECT,
    /** Merge external libraries, library projects, and project dex files. */
    MERGE_ALL,
}

/** Delegate for [DexMergingTask]. It contains all logic for merging dex files. */
@VisibleForTesting
class DexMergingTaskRunnable @Inject constructor(
    private val params: DexMergingParams
) : Runnable {

    override fun run() {
        val logger = LoggerWrapper.getLogger(DexMergingTaskRunnable::class.java)
        val messageReceiver = MessageReceiverImpl(
            params.errorFormatMode,
            Logging.getLogger(DexMergingTask::class.java)
        )
        val forkJoinPool = ForkJoinPool()

        val outputHandler = ParsingProcessOutputHandler(
            ToolOutputParser(DexParser(), Message.Kind.ERROR, logger),
            ToolOutputParser(DexParser(), logger),
            messageReceiver
        )

        var processOutput: ProcessOutput? = null
        try {
            processOutput = outputHandler.createOutput()

            // Analyze top-level inputs, and find all jars in dirs. These jars will be part of the
            // input dex files to merge.
            val jarsInInput = params.getAllDexFiles().filter { it.isDirectory }.flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == SdkConstants.EXT_JAR }
                    .toSet()
            }
            val dexFiles = params.getAllDexFiles() + jarsInInput
            FileUtils.cleanOutputDir(params.outputDir)

            if (dexFiles.isEmpty()) {
                return
            }

            val allDexFiles = lazy { getAllRegularFiles(dexFiles) }
            if (dexFiles.size >= params.mergingThreshold
                || allDexFiles.value.size >= params.mergingThreshold) {
                DexMergerTransformCallable(
                    messageReceiver,
                    params.dexingType,
                    processOutput,
                    params.outputDir,
                    dexFiles.map { it.toPath() }.iterator(),
                    params.mainDexListFile?.toPath(),
                    forkJoinPool,
                    params.dexMerger,
                    params.minSdkVersion,
                    params.isDebuggable
                ).call()
            } else {
                val outputPath =
                    { id: Int -> params.outputDir.resolve("classes_$id.${SdkConstants.EXT_DEX}") }
                var index = 0
                for (file in allDexFiles.value) {
                    if (file.extension == SdkConstants.EXT_JAR) {
                        // Dex files can also come from jars when dexing is not done in artifact
                        // transforms. See b/130965921 for details.
                        ZipFile(file).use {
                            for (entry in it.entries()) {
                                BufferedInputStream(it.getInputStream(entry)).use { inputStream ->
                                    BufferedOutputStream(outputPath(index++).outputStream()).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                        }
                    } else {
                        file.copyTo(outputPath(index++))
                    }
                }
            }
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e))
            throw TransformException(e)
        } finally {
            processOutput?.let {
                try {
                    outputHandler.handleOutput(it)
                    processOutput.close()
                } catch (ignored: ProcessException) {
                }
            }
            forkJoinPool.shutdown()
            forkJoinPool.awaitTermination(100, TimeUnit.SECONDS)
        }
    }
}

@VisibleForTesting
data class DexMergingParams(
    val dexingType: DexingType,
    val errorFormatMode: SyncOptions.ErrorFormatMode,
    val dexMerger: DexMergerTool,
    val minSdkVersion: Int,
    val isDebuggable: Boolean,
    val mergingThreshold: Int,
    val mainDexListFile: File?,
    private val dexFiles: Set<File>,
    private val fileDependencyDexFiles: File?,
    val outputDir: File
) : Serializable {

    fun getAllDexFiles(): List<File> {
        val allDexFiles = ArrayList<File>(dexFiles)
        fileDependencyDexFiles?.let {
            Files.list(it.toPath()).use { files ->
                files.forEach { file ->
                    if (Files.isRegularFile(file) && file.toString().endsWith(".jar")) {
                        allDexFiles.add(file.toFile())
                    }
                }
            }
        }
        return Collections.unmodifiableList(allDexFiles)
    }
}
