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

import com.android.SdkConstants
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.crash.PluginCrashReporter
import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.dexing.DexWorkAction
import com.android.build.gradle.internal.dexing.IncrementalDexSpec
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.dexing.ClassBucket
import com.android.builder.dexing.ClassBucketGroup
import com.android.builder.dexing.ClassFileInput
import com.android.builder.dexing.DexFilePerClassFile
import com.android.builder.dexing.DirectoryBucketGroup
import com.android.builder.dexing.JarBucketGroup
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.collect.Lists
import com.google.common.hash.Hashing
import com.google.common.io.Closer
import org.gradle.api.file.FileType
import org.gradle.api.provider.Provider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.WorkerExecutor
import java.io.BufferedInputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Path
import java.util.ArrayList

/**
 * Delegate for the [DexArchiveBuilderTask]. This is where the actual processing happens. Using the
 * inputs in the task, the delegate instance is configured. Main processing happens in [doProcess].
 */
class DexArchiveBuilderTaskDelegate(
    /** Whether incremental information is available. */
    isIncremental: Boolean,

    // Input class files
    private val projectClasses: Set<File>,
    private val projectChangedClasses: Set<FileChange> = emptySet(),

    private val subProjectClasses: Set<File>,
    private val subProjectChangedClasses: Set<FileChange> = emptySet(),

    private val externalLibClasses: Set<File>,
    private val externalLibChangedClasses: Set<FileChange> = emptySet(),

    private val mixedScopeClasses: Set<File>,
    private val mixedScopeChangedClasses: Set<FileChange> = emptySet(),

    private val projectOutputs: DexingOutputs,
    private val subProjectOutputs: DexingOutputs?,
    private val externalLibsOutputs: DexingOutputs?,
    private val mixedScopeOutputs: DexingOutputs?,

    // Dex parameters
    private val dexParams: DexParameters,

    // Incremental info
    private val desugarClasspathChangedClasses: Set<FileChange> = emptySet(),

    /**
     * Directory containing dependency graph(s) for desugaring, not `null` iff desugaring is
     * enabled.
     */
    private val desugarGraphDir: File?,

    // Other info
    private val inputJarHashesFile: File,
    private val numberOfBuckets: Int,
    private val workerExecutor: WorkerExecutor,
    private val projectPath: Provider<String>,
    private val taskPath: String,
    private val analyticsService: Provider<AnalyticsService>
) {
    private val outputMapping = OutputMapping(isIncremental)

    //(b/141854812) Temporarily disable incremental support when core library desugaring enabled in release build
    private val isIncremental =
        isIncremental && projectOutputs.keepRules == null && subProjectOutputs?.keepRules == null
                && externalLibsOutputs?.keepRules == null && mixedScopeOutputs?.keepRules == null
                && outputMapping.canProcessIncrementally

    private val changedFiles =
        with(
            HashSet<File>(
                projectChangedClasses.size +
                        subProjectChangedClasses.size +
                        externalLibChangedClasses.size +
                        mixedScopeChangedClasses.size +
                        desugarClasspathChangedClasses.size
            )
        ) {
            addAll(projectChangedClasses.map { it.file })
            addAll(subProjectChangedClasses.map { it.file })
            addAll(externalLibChangedClasses.map { it.file })
            addAll(mixedScopeChangedClasses.map { it.file })
            addAll(desugarClasspathChangedClasses.map { it.file })

            this
        }

    init {
        check(dexParams.withDesugaring xor (desugarGraphDir == null))
    }

    /**
     * Classpath resources provider is shared between invocations, and this key uniquely identifies
     * it.
     */
    data class ClasspathServiceKey(private val id: Long) :
        WorkerActionServiceRegistry.ServiceKey<ClassFileProviderFactory> {

        override val type = ClassFileProviderFactory::class.java
    }

    companion object {
        // Shared state used by worker actions.
        internal val sharedState = WorkerActionServiceRegistry()
    }

    fun doProcess() {
        loggerWrapper.verbose("Dex builder is incremental : %b ", isIncremental)

        try {
            Closer.create().use { closer ->
                val classpath = getClasspath(dexParams.withDesugaring)
                val bootclasspath =
                    getBootClasspath(dexParams.desugarBootclasspath, dexParams.withDesugaring)

                val bootClasspathProvider = ClassFileProviderFactory(bootclasspath)
                closer.register(bootClasspathProvider)
                val libraryClasspathProvider = ClassFileProviderFactory(classpath)
                closer.register(libraryClasspathProvider)

                val bootclasspathServiceKey = ClasspathServiceKey(bootClasspathProvider.id)
                val classpathServiceKey = ClasspathServiceKey(libraryClasspathProvider.id)
                sharedState.registerServiceAsCloseable(
                    bootclasspathServiceKey, bootClasspathProvider
                ).also { closer.register(it) }

                sharedState.registerServiceAsCloseable(
                    classpathServiceKey, libraryClasspathProvider
                ).also { closer.register(it) }

                val processInputType = { classes: Set<File>,
                                         changedClasses: Set<FileChange>,
                                         outputDir: File,
                                         outputKeepRules: File?,
                                         globalSynthetics: File?,
                                         // Not null iff desugaring is enabled
                                         desugarGraphDir: File? ->
                    processClassFromInput(
                        inputFiles = classes,
                        inputFileChanges = changedClasses,
                        outputDir = outputDir,
                        outputKeepRules = outputKeepRules,
                        globalSyntheticsDir = globalSynthetics,
                        desugarGraphDir = desugarGraphDir,
                        bootClasspathKey = bootclasspathServiceKey,
                        classpathKey = classpathServiceKey
                    )
                }
                processInputType(
                    projectClasses,
                    projectChangedClasses,
                    projectOutputs.dex,
                    projectOutputs.keepRules,
                    projectOutputs.globalSynthetics,
                    desugarGraphDir?.resolve("currentProject")
                )
                subProjectOutputs?.let {
                    processInputType(
                        subProjectClasses,
                        subProjectChangedClasses,
                        subProjectOutputs.dex,
                        subProjectOutputs.keepRules,
                        subProjectOutputs.globalSynthetics,
                        desugarGraphDir?.resolve("otherProjects")
                    )
                }
                mixedScopeOutputs?.let {
                    processInputType(
                        mixedScopeClasses,
                        mixedScopeChangedClasses,
                        mixedScopeOutputs.dex,
                        mixedScopeOutputs.keepRules,
                        mixedScopeOutputs.globalSynthetics,
                        desugarGraphDir?.resolve("mixedScopes")
                    )
                }
                externalLibsOutputs?.let {
                    processInputType(
                        externalLibClasses,
                        externalLibChangedClasses,
                        externalLibsOutputs.dex,
                        externalLibsOutputs.keepRules,
                        externalLibsOutputs.globalSynthetics,
                        desugarGraphDir?.resolve("externalLibs")
                    )
                }

                // all work items have been submitted, now wait for completion.
                // TODO (gavra): use build services in worker actions so ClassFileProviderFactory are not closed too early
                workerExecutor.await()

                loggerWrapper.verbose("Done with all dex archive conversions")
            }
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            loggerWrapper.error(null, Throwables.getStackTraceAsString(e))
            throw e
        }
    }

    private fun processClassFromInput(
        inputFiles: Set<File>,
        inputFileChanges: Set<FileChange>,
        outputDir: File,
        outputKeepRules: File?,
        globalSyntheticsDir: File?,
        desugarGraphDir: File?, // Not null iff desugaring is enabled
        bootClasspathKey: ClasspathServiceKey,
        classpathKey: ClasspathServiceKey
    ) {
        if (!isIncremental) {
            FileUtils.cleanOutputDir(outputDir)
            globalSyntheticsDir?.let { FileUtils.cleanOutputDir(it) }
            outputKeepRules?.let { FileUtils.cleanOutputDir(it) }
            desugarGraphDir?.let { FileUtils.cleanOutputDir(it) }
        } else {
            removeChangedJarOutputs(inputFileChanges, outputDir, globalSyntheticsDir)
            deletePreviousOutputsFromDirs(inputFileChanges, outputDir, globalSyntheticsDir)
        }

        val (directoryInputs, jarInputs) =
            inputFiles
                .filter { it.exists() }
                .partition { it.isDirectory }

        if (directoryInputs.isNotEmpty()) {
            directoryInputs.forEach { loggerWrapper.verbose("Processing input %s", it.toString()) }
            convertToDexArchive(
                inputs = DirectoryBucketGroup(directoryInputs, numberOfBuckets),
                outputDir = outputDir,
                isIncremental = isIncremental,
                bootClasspath = bootClasspathKey,
                classpath = classpathKey,
                changedFiles = changedFiles,
                desugarGraphDir = desugarGraphDir,
                outputKeepRulesDir = outputKeepRules,
                globalSyntheticsDir = globalSyntheticsDir
            )
        }

        for (input in jarInputs) {
            loggerWrapper.verbose("Processing input %s", input.toString())
            check(input.extension == SdkConstants.EXT_JAR) { "Expected jar, received $input" }

            convertJarToDexArchive(
                isIncremental = isIncremental,
                jarInput = input,
                outputDir = outputDir,
                bootclasspath = bootClasspathKey,
                classpath = classpathKey,
                changedFiles = changedFiles,
                desugarGraphDir = desugarGraphDir,
                outputKeepRulesDir = outputKeepRules,
                globalSyntheticsDir = globalSyntheticsDir
            )
        }
    }

    @Suppress("UnstableApiUsage")
    private fun deletePreviousOutputsFromDirs(
        inputFileChanges: Set<FileChange>,
        outputDir: File,
        globalSyntheticsDir: File?
    ) {
        // Remove outputs of removed, modified class files
        // TODO(b/246955560) remove outputs of impacted classes
        inputFileChanges
            .filter { it.changeType == ChangeType.REMOVED || it.changeType == ChangeType.MODIFIED}
            .forEach {
                when {
                    it.fileType == FileType.DIRECTORY -> {
                        FileUtils.deleteRecursivelyIfExists(outputDir.resolve(it.normalizedPath))
                        globalSyntheticsDir?.let { globalSyntheticsDir ->
                            globalSyntheticsDir.resolve(it.normalizedPath)
                        }
                    }
                    ClassFileInput.CLASS_MATCHER.test(it.normalizedPath) -> {
                        // We are in DexFilePerClassFile output mode
                        DexFilePerClassFile.getDexOutputRelativePathsOfClassFile(
                            classFileRelativePath = it.normalizedPath
                        ).forEach { outputRelativePath ->
                            FileUtils.deleteIfExists(outputDir.resolve(outputRelativePath))
                        }
                        globalSyntheticsDir?.let {globalSyntheticsDir ->
                            DexFilePerClassFile.getGlobalOutputRelativePathOfClassFile(
                                it.normalizedPath
                            ).let { outputRelativePath ->
                                FileUtils.deleteIfExists(globalSyntheticsDir.resolve(outputRelativePath))
                            }
                        }
                    }
                }
            }
    }

    private fun removeChangedJarOutputs(
        changes: Set<FileChange>,
        dexOutput: File,
        globalSyntheticsDir: File?
    ) {
        changes.filter { it.file.extension == SdkConstants.EXT_JAR }.forEach {
            outputMapping.getPreviousOutputsForJar(it.file, dexOutput).forEach {
                FileUtils.deleteIfExists(it)
            }
            globalSyntheticsDir?.let { globalSyntheticsDir ->
                outputMapping.getPreviousOutputsForJar(it.file, globalSyntheticsDir).forEach {
                    FileUtils.deleteIfExists(it)
                }
            }
        }
    }

    private fun convertJarToDexArchive(
        isIncremental: Boolean,
        jarInput: File,
        outputDir: File,
        bootclasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        changedFiles: Set<File>,
        desugarGraphDir: File?, // Not null iff desugaring is enabled
        outputKeepRulesDir: File?,
        globalSyntheticsDir: File?
    ) {
        if (dexParams.withDesugaring) {
            convertToDexArchive(
                inputs = JarBucketGroup(jarInput, numberOfBuckets),
                outputDir = outputDir,
                isIncremental = isIncremental,
                bootClasspath = bootclasspath,
                classpath = classpath,
                changedFiles = changedFiles,
                desugarGraphDir = desugarGraphDir!!,
                outputKeepRulesDir = outputKeepRulesDir,
                globalSyntheticsDir = globalSyntheticsDir
            )
        } else {
            if (isIncremental && jarInput !in changedFiles) {
                return
            }

            convertToDexArchive(
                inputs = JarBucketGroup(jarInput, numberOfBuckets),
                outputDir = outputDir,
                isIncremental = false,
                bootClasspath = bootclasspath,
                classpath = classpath,
                changedFiles = setOf(),
                desugarGraphDir = null,
                outputKeepRulesDir = outputKeepRulesDir,
                globalSyntheticsDir = globalSyntheticsDir
            )
        }
    }

    /**  Output directories for dex files and keep rules. */
    class DexingOutputs(
        val dex: File,
        val keepRules: File?,
        val globalSynthetics: File?
    ) {
        constructor(outputs: DexArchiveBuilderTask.DexingOutputs) : this(
            outputs.dex.asFile.get(),
            outputs.keepRules.asFile.orNull,
            outputs.globalSynthetics.asFile.orNull
        )
    }

    private fun convertToDexArchive(
        inputs: ClassBucketGroup,
        outputDir: File,
        isIncremental: Boolean,
        bootClasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        changedFiles: Set<File>,
        desugarGraphDir: File?, // Not null iff desugaring is enabled
        outputKeepRulesDir: File?,
        globalSyntheticsDir: File?
    ) {
        inputs.getRoots().forEach { loggerWrapper.verbose("Dexing ${it.absolutePath}") }

        for (bucketId in 0 until numberOfBuckets) {
            // For directory inputs, we prefer dexPerClass mode to support incremental dexing per
            // class, but dexPerClass mode is not supported by D8 when generating keep rules for
            // core library desugaring
            val dexPerClass = inputs is DirectoryBucketGroup && outputKeepRulesDir == null

            fun computeOutputPath(outputDir: File, dexOutput: Boolean): File {
                return when(inputs) {
                    is DirectoryBucketGroup -> {
                        if (dexPerClass) {
                            outputDir.also { FileUtils.mkdirs(it) }
                        } else {
                            // running in dexIndexMode, dex output location is determined by
                            // bucket and outputDir
                            outputDir.resolve(bucketId.toString()).also { FileUtils.mkdirs(it) }
                        }
                    }
                    is JarBucketGroup -> {
                        outputMapping.getOutputForJar(
                            inputs.jarFile,
                            outputDir,
                            bucketId,
                            dexOutput
                        ).also { FileUtils.mkdirs(it.parentFile) }
                    }
                }
            }

            val preDexOutputFile = computeOutputPath(outputDir, true)

            val globalSyntheticsOutput: File? = if (globalSyntheticsDir != null) {
                computeOutputPath(globalSyntheticsDir, false)
            } else null

            val outputKeepRuleFile = outputKeepRulesDir?.let { outputKeepRuleDir ->
                when (inputs) {
                    is DirectoryBucketGroup -> outputKeepRuleDir.resolve(bucketId.toString())
                    is JarBucketGroup ->
                        outputMapping.getOutputForJar(
                            inputs.jarFile,
                            outputKeepRuleDir,
                            bucketId,
                            false
                        )
                }.also {
                    FileUtils.mkdirs(it.parentFile)
                    it.createNewFile()
                }
            }

            val classBucket = ClassBucket(inputs, bucketId)
            workerExecutor.noIsolation().submit(DexWorkAction::class.java) { params ->
                params.initializeWith(projectPath, taskPath, analyticsService)
                params.dexSpec.set(
                        IncrementalDexSpec(
                        inputClassFiles = classBucket,
                        dexOutputPath = preDexOutputFile,
                        globalSyntheticsOutput = globalSyntheticsOutput,
                        dexParams = dexParams.toDexParametersForWorkers(
                                dexPerClass,
                                bootClasspath,
                                classpath,
                                outputKeepRuleFile,
                        ),
                        isIncremental = isIncremental,
                        changedFiles = changedFiles,
                        desugarGraphFile = desugarGraphDir?.let {
                            getDesugarGraphFile(it, classBucket)
                        }
                ))
            }
        }
    }

    private fun getClasspath(withDesugaring: Boolean): List<Path> {
        if (!withDesugaring) {
            return emptyList()
        }

        return ArrayList<Path>(
            projectClasses.size +
                    subProjectClasses.size +
                    externalLibClasses.size +
                    mixedScopeClasses.size +
                    dexParams.desugarClasspath.size
        ).also { list ->
            list.addAll(projectClasses.map { it.toPath() })
            list.addAll(subProjectClasses.map { it.toPath() })
            list.addAll(externalLibClasses.map { it.toPath() })
            list.addAll(mixedScopeClasses.map { it.toPath() })
            list.addAll(dexParams.desugarClasspath.map { it.toPath() })
        }
    }

    private fun getBootClasspath(
        androidJarClasspath: List<File>,
        withDesugaring: Boolean
    ): List<Path> {
        if (!withDesugaring) {
            return emptyList()
        }
        return androidJarClasspath.map { it.toPath() }
    }

    /** Returns the file containing the desugaring graph when processing a [ClassBucket]. */
    private fun getDesugarGraphFile(desugarGraphDir: File, classBucket: ClassBucket): File {
        return when (classBucket.bucketGroup) {
            is DirectoryBucketGroup -> File(
                desugarGraphDir,
                "dirs_bucket_${classBucket.bucketNumber}/graph.bin"
            )
            is JarBucketGroup -> {
                // Use the hash of the jar's path instead of its contents as we don't need to worry
                // about cache relocatability (the desugaring graph is not cached). If later on we
                // want to use the content hash, keep in mind that the jar may have been removed
                // (note that inputJarHashesValues contains hashes of removed jars too).
                val jarFilePath = (classBucket.bucketGroup as JarBucketGroup).jarFile.path
                File(
                    desugarGraphDir,
                    "jar_${Hashing.sha256().hashUnencodedChars(jarFilePath)}_" +
                            "bucket_${classBucket.bucketNumber}/graph.bin"
                )
            }
        }
    }

    /**
     * We are using file hashes to determine the output location for input jars. If the file
     * containing mapping from absolute paths to hashes exists, we will load it, and re-use its
     * content for all unchanged files. For changed jar files, we will recompute the hash.
     *
     * The mapping also specifies if this run can be incremental, see [canProcessIncrementally].
     * This is possible only if the list of files previously recorded is the same as the current
     * list of input files (from all input scopes). E.g. in case the file is removed, mapping
     * will report non-incremental run, but this is ok as most of the incremental builds are
     * changing the content of the jars, not their path. Also, this avoids bugs like b/154712997.
     */
    private inner class OutputMapping(isAbleToRunIncrementally: Boolean) {
        private val currentFileHashes: Map<File, String>
        private val previousFileHashes: Map<File, String>

        val canProcessIncrementally: Boolean

        init {
            val (fileHashes, isPreviousLoaded) = if (!inputJarHashesFile.exists() || !isAbleToRunIncrementally) {
                Pair(mutableMapOf(), false)
            } else {
                BufferedInputStream(inputJarHashesFile.inputStream()).use { input ->
                    try {
                        ObjectInputStream(input).use {
                            @Suppress("UNCHECKED_CAST")
                            val previousState = it.readObject() as MutableMap<File, String>
                            if (ifPreviousStateHasAllInputFiles(previousState)) {
                                Pair(previousState, true)
                            } else {
                                Pair(mutableMapOf(), false)
                            }
                        }
                    } catch (e: Exception) {
                        loggerWrapper.warning(
                            "Reading jar hashes from $inputJarHashesFile failed. Exception: ${e.message}"
                        )
                        Pair(mutableMapOf<File, String>(), false)
                    }
                }
            }
            previousFileHashes = fileHashes.toMap()

            fun getFileHash(file: File): String = file.inputStream().buffered().use {
                Hashing.sha256()
                    .hashBytes(it.readBytes())
                    .toString()
            }

            if (isPreviousLoaded) {
                // Update hashes of changed files.
                sequenceOf(
                    projectChangedClasses,
                    subProjectChangedClasses,
                    externalLibChangedClasses,
                    mixedScopeChangedClasses
                ).flatten().filter { it.file.extension == SdkConstants.EXT_JAR }.forEach {
                    check(it.changeType != ChangeType.REMOVED) {
                        "Reported ${it.file.canonicalPath} as removed. Output mapping should be non-incremental."
                    }
                    fileHashes[it.file] = getFileHash(it.file)
                }
            } else {
                getAllFilesToProcess().forEach { fileHashes[it] = getFileHash(it) }
            }
            FileUtils.deleteIfExists(inputJarHashesFile)
            FileUtils.mkdirs(inputJarHashesFile.parentFile)
            ObjectOutputStream(inputJarHashesFile.outputStream().buffered()).use {
                it.writeObject(fileHashes)
            }

            currentFileHashes = fileHashes
            canProcessIncrementally = isPreviousLoaded
        }

        fun getCurrentHash(file: File) = currentFileHashes.getValue(file)

        /**
         * Computes the output path without using the jar absolute path. This method will use the
         * hash of the file content to determine the final output path, and this makes sure the task is
         * relocatable.
         */
        fun getOutputForJar(
            input: File,
            outputDir: File,
            bucketId: Int,
            withJarExtension: Boolean = true
        ): File {
            val hash = getCurrentHash(input)
            return computeOutputPath(outputDir, hash, bucketId, withJarExtension)
        }

        /**
         * Get the output path for a jar in the previous run
         */
        fun getPreviousOutputsForJar(input: File, outputDir: File): List<File> {
            val hash = previousFileHashes.getValue(input)

            return Lists.newArrayListWithCapacity<File>(numberOfBuckets + 1).also {
                it.add(computeOutputPath(outputDir, hash, null))
                (0 until numberOfBuckets).forEach { bucketId ->
                    it.add(computeOutputPath(outputDir, hash, bucketId))
                }
            }
        }

        /**
         * Check if the previous mapping contains exactly all files currently being processed. If
         * not, return false.
         */
        private fun ifPreviousStateHasAllInputFiles(previousMapping: Map<File, String>): Boolean {
            val allFilesToProcess = getAllFilesToProcess()
            return previousMapping.size == allFilesToProcess.count() && allFilesToProcess.all { it in previousMapping.keys }
        }

        private fun getAllFilesToProcess() = sequenceOf(
            projectClasses,
            subProjectClasses,
            externalLibClasses,
            mixedScopeClasses
        ).flatten().filter { it.extension == SdkConstants.EXT_JAR }

        private fun computeOutputPath(
            outputDir: File,
            hash: String,
            bucketId: Int?,
            withJarExtension: Boolean = true
        ): File {
            val jarExtension = if(withJarExtension) ".jar" else ""
            return if (bucketId != null) {
                outputDir.resolve("${hash}_$bucketId$jarExtension")
            } else {
                outputDir.resolve("$hash$jarExtension")
            }
        }
    }
}

private val loggerWrapper = LoggerWrapper.getLogger(DexArchiveBuilderTaskDelegate::class.java)
