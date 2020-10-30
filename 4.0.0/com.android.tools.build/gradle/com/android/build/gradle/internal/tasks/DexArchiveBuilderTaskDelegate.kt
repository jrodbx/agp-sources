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
import com.android.build.gradle.internal.dexing.DexWorkActionParams
import com.android.build.gradle.internal.dexing.DxDexParameters
import com.android.build.gradle.internal.dexing.IncrementalDexSpec
import com.android.build.gradle.internal.dexing.launchProcessing
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.core.DefaultDexOptions
import com.android.builder.dexing.ClassBucket
import com.android.builder.dexing.ClassBucketGroup
import com.android.builder.dexing.ClassFileEntry
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.DirectoryBucketGroup
import com.android.builder.dexing.JarBucketGroup
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.builder.utils.FileCache
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.MessageReceiver
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.internal.WaitableExecutor
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessOutput
import com.android.utils.FileUtils
import com.google.common.base.Throwables
import com.google.common.collect.Iterables
import com.google.common.hash.Hashing
import com.google.common.io.Closer
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.BufferedInputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.function.Supplier

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

    // Output directories for dex files and keep rules
    private val projectOutputDex: File,
    private val projectOutputKeepRules: File?,

    private val subProjectOutputDex: File,
    private val subProjectOutputKeepRules: File?,

    private val externalLibsOutputDex: File,
    private val externalLibsOutputKeepRules: File?,

    private val mixedScopeOutputDex: File,
    private val mixedScopeOutputKeepRules: File?,

    // Dex parameters
    private val dexParams: DexParameters,
    private val dxDexParams: DxDexParameters,

    // Incremental info
    private val desugarClasspathChangedClasses: Set<FileChange> = emptySet(),

    /** Whether incremental desugaring V2 is enabled. */
    incrementalDexingV2: Boolean,

    /**
     * Directory containing dependency graph(s) for desugaring, not `null` iff
     * incrementalDexingV2 is enabled.
     */
    private val desugarGraphDir: File?,

    // Other info
    projectVariant: String,
    private val inputJarHashesFile: File,
    private val dexer: DexerTool,
    private val numberOfBuckets: Int,
    private val useGradleWorkers: Boolean,
    private val workerExecutor: WorkerExecutor,
    private val executor: WaitableExecutor = WaitableExecutor.useGlobalSharedThreadPool(),
    userLevelCache: FileCache? = null,
    private var messageReceiver: MessageReceiver
) {
    //(b/141854812) Temporarily disable incremental support when core library desugaring enabled in release build
    private val isIncremental =
        isIncremental && projectOutputKeepRules == null && subProjectOutputKeepRules == null
                && externalLibsOutputKeepRules == null && mixedScopeOutputKeepRules == null

    private val cacheHandler: DexArchiveBuilderCacheHandler =
        DexArchiveBuilderCacheHandler(
            userLevelCache,
            dxDexParams.dxNoOptimizeFlagPresent,
            dexParams.minSdkVersion,
            dexParams.debuggable,
            dexer,
            dexParams.coreLibDesugarConfig
        )

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

    // Whether impacted files are computed lazily in the workers instead of being computed up front
    // before the workers are launched.
    private val isImpactedFilesComputedLazily: Boolean =
        dexParams.withDesugaring && dexer == DexerTool.D8 && incrementalDexingV2

    // desugarIncrementalHelper is not null iff
    // !isImpactedFilesComputedLazily && dexParams.withDesugaring
    private val desugarIncrementalHelper: DesugarIncrementalHelper? =
        DesugarIncrementalHelper(
            projectVariant,
            isIncremental,
            Iterables.concat(
                projectClasses,
                subProjectClasses,
                externalLibClasses,
                mixedScopeClasses,
                dexParams.desugarClasspath
            ),
            Supplier { changedFiles.mapTo(HashSet<Path>(changedFiles.size)) { it.toPath() } },
            executor
        ).takeIf { !isImpactedFilesComputedLazily && dexParams.withDesugaring }

    private var inputJarHashesValues: MutableMap<File, String> = getCurrentJarInputHashes()

    init {
        check(incrementalDexingV2 xor (desugarGraphDir == null))
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
        if (dxDexParams.dxNoOptimizeFlagPresent) {
            loggerWrapper.warning(DefaultDexOptions.OPTIMIZE_WARNING)
        }

        loggerWrapper.verbose("Dex builder is incremental : %b ", isIncremental)

        // impactedFiles is not null iff !isImpactedFilesComputedLazily
        val impactedFiles: Set<File>? =
            if (isImpactedFilesComputedLazily) {
                null
            } else {
                if (dexParams.withDesugaring) {
                    desugarIncrementalHelper!!.additionalPaths.map { it.toFile() }.toSet()
                } else {
                    emptySet()
                }
            }

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
                    desugarGraphDir: File?, // Not null iff impactedFiles == null
                    useAndroidBuildCache: Boolean,
                    cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>?,
                    cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? ->
                    processClassFromInput(
                        inputFiles = classes,
                        inputFileChanges = changedClasses,
                        outputDir = outputDir,
                        outputKeepRules = outputKeepRules,
                        impactedFiles = impactedFiles,
                        desugarGraphDir = desugarGraphDir,
                        bootClasspathKey = bootclasspathServiceKey,
                        bootClasspath = bootclasspath,
                        classpathKey = classpathServiceKey,
                        classpath = classpath,
                        enableCaching = useAndroidBuildCache,
                        cacheableDexes = cacheableDexes,
                        cacheableKeepRules = cacheableKeepRules
                    )
                }
                processInputType(
                    projectClasses,
                    projectChangedClasses,
                    projectOutputDex,
                    projectOutputKeepRules,
                    desugarGraphDir?.resolve("currentProject").takeIf { impactedFiles == null },
                    false, // useAndroidBuildCache
                    null, // cacheableDexes
                    null // cacheableKeepRules
                )
                processInputType(
                    subProjectClasses,
                    subProjectChangedClasses,
                    subProjectOutputDex,
                    subProjectOutputKeepRules,
                    desugarGraphDir?.resolve("otherProjects").takeIf { impactedFiles == null },
                    false, // useAndroidBuildCache
                    null, // cacheableDexes
                    null // cacheableKeepRules
                )
                processInputType(
                    mixedScopeClasses,
                    mixedScopeChangedClasses,
                    mixedScopeOutputDex,
                    mixedScopeOutputKeepRules,
                    desugarGraphDir?.resolve("mixedScopes").takeIf { impactedFiles == null },
                    false, // useAndroidBuildCache
                    null, // cacheableDexes
                    null // cacheableKeepRules
                )
                // Caching is currently not supported when isImpactedFilesComputedLazily == true
                val enableCachingForExternalLibs = !isImpactedFilesComputedLazily
                val cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? =
                    mutableListOf<DexArchiveBuilderCacheHandler.CacheableItem>()
                        .takeIf { enableCachingForExternalLibs }
                val cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? =
                    mutableListOf<DexArchiveBuilderCacheHandler.CacheableItem>()
                        .takeIf { enableCachingForExternalLibs }
                val shrinkDesugarLibrary = externalLibsOutputKeepRules != null
                processInputType(
                    externalLibClasses,
                    externalLibChangedClasses,
                    externalLibsOutputDex,
                    externalLibsOutputKeepRules,
                    desugarGraphDir?.resolve("externalLibs").takeIf { impactedFiles == null },
                    enableCachingForExternalLibs,
                    cacheableDexes,
                    cacheableKeepRules
                )

                // all work items have been submitted, now wait for completion.
                if (useGradleWorkers) {
                    workerExecutor.await()
                } else {
                    executor.waitForTasksWithQuickFail<Any>(true)
                }

                // and finally populate the caches.
                if (cacheableDexes != null && cacheableDexes.isNotEmpty()) {
                    cacheHandler.populateDexCache(cacheableDexes, shrinkDesugarLibrary)
                }
                if (cacheableKeepRules != null && cacheableKeepRules.isNotEmpty()) {
                    cacheHandler.populateKeepRuleCache(
                        cacheableKeepRules,
                        shrinkDesugarLibrary
                    )
                }

                loggerWrapper.verbose("Done with all dex archive conversions");
            }
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            loggerWrapper.error(null, Throwables.getStackTraceAsString(e))
            throw e
        }
    }

    /**
     * We are using file hashes to determine the output location for input jars. If the file
     * containing mapping from absolute paths to hashes exists, we will load it, and re-use its
     * content for all unchanged files. For changed jar files, we will recompute the hash, and
     * deleted files will have their entries removed from the map.
     */
    private fun getCurrentJarInputHashes(): MutableMap<File, String> {
        val (fileHashes, isPreviousLoaded) = if (!inputJarHashesFile.exists() || !isIncremental) {
            Pair(mutableMapOf(), false)
        } else {
            BufferedInputStream(inputJarHashesFile.inputStream()).use { input ->
                try {
                    ObjectInputStream(input).use {
                        @Suppress("UNCHECKED_CAST")
                        Pair(it.readObject() as MutableMap<File, String>, true)
                    }
                } catch (e: Exception) {
                    loggerWrapper.warning(
                        "Reading jar hashes from $inputJarHashesFile failed. Exception: ${e.message}"
                    )
                    Pair(mutableMapOf<File, String>(), false)
                }
            }
        }

        fun getFileHash(file: File): String = file.inputStream().buffered().use {
            Hashing.sha256()
                .hashBytes(it.readBytes())
                .toString()
        }

        if (isPreviousLoaded) {
            // Update hashes of changed files.
            listOf(
                projectChangedClasses,
                subProjectChangedClasses,
                externalLibChangedClasses,
                mixedScopeChangedClasses
            ).forEach { changes ->
                changes.asSequence().filter { it.file.extension == SdkConstants.EXT_JAR }.forEach {
                    if (it.changeType == ChangeType.REMOVED) {
                        fileHashes.remove(it.file)
                    } else {
                        fileHashes[it.file] = getFileHash(it.file)
                    }
                }
            }
        } else {
            listOf(projectClasses, subProjectClasses, externalLibClasses, mixedScopeClasses)
                .forEach { files ->
                    files.asSequence().filter { it.extension == SdkConstants.EXT_JAR }
                        .forEach { fileHashes[it] = getFileHash(it) }
                }
        }

        FileUtils.deleteIfExists(inputJarHashesFile)
        FileUtils.mkdirs(inputJarHashesFile.parentFile)
        ObjectOutputStream(inputJarHashesFile.outputStream().buffered()).use {
            it.writeObject(fileHashes)
        }
        return fileHashes
    }

    private fun processClassFromInput(
        inputFiles: Set<File>,
        inputFileChanges: Set<FileChange>,
        outputDir: File,
        outputKeepRules: File?,
        impactedFiles: Set<File>?,
        desugarGraphDir: File?, // Not null iff impactedFiles == null
        bootClasspathKey: ClasspathServiceKey,
        bootClasspath: List<Path>,
        classpathKey: ClasspathServiceKey,
        classpath: List<Path>,
        enableCaching: Boolean,
        // cacheableDexes/cacheableKeepRules is not null iff enableCaching == true
        cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>?,
        cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>?
    ) {
        if (!isIncremental) {
            FileUtils.cleanOutputDir(outputDir)
            outputKeepRules?.let { FileUtils.cleanOutputDir(it) }
            desugarGraphDir?.let { FileUtils.cleanOutputDir(it) }
        } else {
            removeChangedJarOutputs(
                inputFiles,
                inputFileChanges,
                impactedFiles ?: emptySet(),
                outputDir
            )
            deletePreviousOutputsFromDirs(inputFileChanges, outputDir)
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
                impactedFiles = impactedFiles,
                desugarGraphDir = desugarGraphDir,
                outputKeepRulesDir = outputKeepRules
            )
        }

        for (input in jarInputs) {
            loggerWrapper.verbose("Processing input %s", input.toString())
            check(input.extension == SdkConstants.EXT_JAR) { "Expected jar, received $input" }

            val cacheInfo = if (enableCaching) {
                getD8DesugaringCacheInfo(
                    desugarIncrementalHelper,
                    bootClasspath,
                    classpath,
                    input
                )
            } else {
                DesugaringDontCache
            }

            val dexOutputs = convertJarToDexArchive(
                isIncremental = isIncremental,
                jarInput = input,
                outputDir = outputDir,
                bootclasspath = bootClasspathKey,
                classpath = classpathKey,
                changedFiles = changedFiles,
                impactedFiles = impactedFiles,
                desugarGraphDir = desugarGraphDir,
                cacheInfo = cacheInfo,
                outputKeepRulesDir = outputKeepRules
            )
            if (cacheInfo != DesugaringDontCache && dexOutputs.dexes.isNotEmpty()) {
                cacheableDexes!!.add(
                    DexArchiveBuilderCacheHandler.CacheableItem(
                        input,
                        dexOutputs.dexes,
                        cacheInfo.orderedD8DesugaringDependencies
                    )
                )
                if (dexOutputs.keepRules.isNotEmpty()) {
                    cacheableKeepRules!!.add(
                        DexArchiveBuilderCacheHandler.CacheableItem(
                            input,
                            dexOutputs.keepRules,
                            cacheInfo.orderedD8DesugaringDependencies
                        )
                    )
                }
            }
        }
    }

    private fun getD8DesugaringCacheInfo(
        desugarIncrementalHelper: DesugarIncrementalHelper?,
        bootclasspath: List<Path>,
        classpath: List<Path>,
        jarInput: File
    ): D8DesugaringCacheInfo {
        if (!dexParams.withDesugaring) {
            return DesugaringNoInfoCache
        }
        desugarIncrementalHelper as DesugarIncrementalHelper

        val unorderedD8DesugaringDependencies =
            desugarIncrementalHelper.getDependenciesPaths(jarInput.toPath())

        // Don't cache libraries depending on class files in folders:
        // Folders content is expected to change often so probably not worth paying the cache cost
        // if we frequently need to rebuild anyway.
        // Supporting dependency to class files would also require special care to respect order.
        if (unorderedD8DesugaringDependencies
                .any { path -> !path.toString().endsWith(SdkConstants.DOT_JAR) }
        ) {
            return DesugaringDontCache
        }

        // DesugaringGraph is not calculating the bootclasspath dependencies so just keep the full
        // bootclasspath for now.
        val bootclasspathPaths = bootclasspath.distinct()

        val classpathJars =
            classpath.distinct().filter { unorderedD8DesugaringDependencies.contains(it) }

        val allDependencies = ArrayList<Path>(bootclasspathPaths.size + classpathJars.size)

        allDependencies.addAll(bootclasspathPaths)
        allDependencies.addAll(classpathJars)
        return D8DesugaringCacheInfo(allDependencies)
    }

    private fun deletePreviousOutputsFromDirs(inputFileChanges: Set<FileChange>, output: File) {
        // Handle dir/file deletions only. We rewrite modified files, so no need to delete those.
        inputFileChanges.asSequence().filter {
            it.changeType == ChangeType.REMOVED && it.file.extension != SdkConstants.EXT_JAR
        }.forEach {
            val relativePath = it.normalizedPath

            val fileToDelete = if (it.file.extension == SdkConstants.EXT_CLASS) {
                ClassFileEntry.withDexExtension(relativePath.toString())
            } else {
                relativePath.toString()
            }

            FileUtils.deleteRecursivelyIfExists(output.resolve(fileToDelete))
        }
    }

    /**
     * In order to remove stale outputs, we find the set of unchanged jar files. For the unchanged
     * jars, we find the output jar locations. Any other output jar is removed.
     */
    private fun removeChangedJarOutputs(
        inputClasses: Set<File>,
        changes: Set<FileChange>,
        desugaringImpactedFiles: Set<File>,
        output: File
    ) {
        if (changes.isEmpty() && desugaringImpactedFiles.isEmpty()) return

        val changedFiles = changes.map { it.file }.toSet()
        val unchangedOutputs = mutableSetOf<File>()
        inputClasses.asSequence()
            .filter { it.extension == SdkConstants.EXT_JAR && it !in changedFiles && it !in desugaringImpactedFiles }
            .forEach { file ->
                unchangedOutputs.add(getDexOutputForJar(file, output, null))
                (0 until numberOfBuckets).forEach {
                    unchangedOutputs.add(getDexOutputForJar(file, output, it))
                }
            }
        val outputFiles = output.listFiles() as? Array<File> ?: return

        outputFiles.filter { it.extension == SdkConstants.EXT_JAR && it !in unchangedOutputs }
            .forEach { FileUtils.deleteIfExists(it) }
    }

    private fun convertJarToDexArchive(
        isIncremental: Boolean,
        jarInput: File,
        outputDir: File,
        bootclasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        changedFiles: Set<File>,
        impactedFiles: Set<File>?,
        desugarGraphDir: File?, // Not null iff impactedFiles == null
        cacheInfo: D8DesugaringCacheInfo,
        outputKeepRulesDir: File?
    ): DexOutputs {
        if (isImpactedFilesComputedLazily) {
            check(impactedFiles == null)
            return convertToDexArchive(
                inputs = JarBucketGroup(jarInput, numberOfBuckets),
                outputDir = outputDir,
                isIncremental = isIncremental,
                bootClasspath = bootclasspath,
                classpath = classpath,
                changedFiles = changedFiles,
                impactedFiles = null,
                desugarGraphDir = desugarGraphDir,
                outputKeepRulesDir = outputKeepRulesDir
            )
        } else {
            // This is the case where the set of impactedFiles was precomputed, so dexing
            // avoidance and caching is possible.
            checkNotNull(impactedFiles)
            if (isIncremental && jarInput !in changedFiles && jarInput !in impactedFiles) {
                return DexOutputs()
            }

            if (cacheInfo !== DesugaringDontCache) {
                val shrinkDesugarLibrary = outputKeepRulesDir != null
                val cachedDex = cacheHandler.getCachedDexIfPresent(
                    jarInput, cacheInfo.orderedD8DesugaringDependencies, shrinkDesugarLibrary
                )
                val cachedKeepRule = cacheHandler.getCachedKeepRuleIfPresent(
                    jarInput, cacheInfo.orderedD8DesugaringDependencies, shrinkDesugarLibrary
                )

                if (outputKeepRulesDir == null) {
                    if (cachedDex != null) {
                        val outputFile = getDexOutputForJar(jarInput, outputDir, null)
                        Files.copy(
                            cachedDex.toPath(),
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        // no need to try to cache an already cached version.
                        return DexOutputs()
                    }
                } else {
                    // cachedDex and cachedKeepRule must be both null or both non-null. Something
                    // must have gone wrong if one of them is null and the other is not.
                    if ((cachedDex == null) xor (cachedKeepRule == null)) {
                        loggerWrapper.warning("One of the cached dex outputs is missing. " +
                                "Re-dex without using existing cached outputs.")
                    }
                    if (cachedDex != null && cachedKeepRule != null) {
                        val outputFile = getDexOutputForJar(jarInput, outputDir, null)
                        Files.copy(
                            cachedDex.toPath(),
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        FileUtils.copyDirectory(cachedKeepRule, outputKeepRulesDir)
                        // no need to try to cache an already cached version.
                        return DexOutputs()
                    }
                }
            }
            return convertToDexArchive(
                inputs = JarBucketGroup(jarInput, numberOfBuckets),
                outputDir = outputDir,
                isIncremental = false,
                bootClasspath = bootclasspath,
                classpath = classpath,
                changedFiles = setOf(),
                impactedFiles = setOf(),
                desugarGraphDir = null,
                outputKeepRulesDir = outputKeepRulesDir
            )
        }
    }

    private open class D8DesugaringCacheInfo constructor(val orderedD8DesugaringDependencies: List<Path>)
    private object DesugaringNoInfoCache : D8DesugaringCacheInfo(emptyList())
    private object DesugaringDontCache : D8DesugaringCacheInfo(emptyList())

    private fun convertToDexArchive(
        inputs: ClassBucketGroup,
        outputDir: File,
        isIncremental: Boolean,
        bootClasspath: ClasspathServiceKey,
        classpath: ClasspathServiceKey,
        changedFiles: Set<File>,
        impactedFiles: Set<File>?,
        desugarGraphDir: File?, // Not null iff impactedFiles == null
        outputKeepRulesDir: File?
    ): DexOutputs {
        inputs.getRoots().forEach { loggerWrapper.verbose("Dexing ${it.absolutePath}") }

        val dexOutputs = DexOutputs()
        for (bucketId in 0 until numberOfBuckets) {
            // For directory inputs, we prefer dexPerClass mode to support incremental dexing per
            // class, but dexPerClass mode is not supported by D8 when generating keep rules for
            // core library desugaring
            val dexPerClass = inputs is DirectoryBucketGroup && outputKeepRulesDir == null

            val preDexOutputFile = when (inputs) {
                is DirectoryBucketGroup -> {
                    if (dexPerClass) {
                        outputDir.also { FileUtils.mkdirs(it) }
                    } else {
                        // running in dexIndexMode, dex output location is determined by bucket and
                        // outputDir
                        outputDir.resolve(bucketId.toString()).also { FileUtils.mkdirs(it) }
                    }
                }
                is JarBucketGroup -> {
                    getDexOutputForJar(inputs.jarFile, outputDir, bucketId)
                        .also { FileUtils.mkdirs(it.parentFile) }
                }
            }

            val outputKeepRuleFile = outputKeepRulesDir?.let { outputKeepRuleDir ->
                when (inputs) {
                    is DirectoryBucketGroup -> outputKeepRuleDir.resolve(bucketId.toString())
                    is JarBucketGroup ->
                        getKeepRulesOutputForJar(inputs.jarFile, outputKeepRuleDir, bucketId)
                }.also {
                    FileUtils.mkdirs(it.parentFile)
                    it.createNewFile()
                }
            }

            dexOutputs.addDex(preDexOutputFile)
            val classBucket = ClassBucket(inputs, bucketId)
            outputKeepRuleFile?.let { dexOutputs.addKeepRule(it) }
            val parameters = DexWorkActionParams(
                dexer = dexer,
                dexSpec = IncrementalDexSpec(
                    inputClassFiles = classBucket,
                    outputPath = preDexOutputFile,
                    dexParams = dexParams.toDexParametersForWorkers(
                        dexPerClass,
                        bootClasspath,
                        classpath,
                        outputKeepRuleFile
                    ),
                    isIncremental = isIncremental,
                    changedFiles = changedFiles,
                    impactedFiles = impactedFiles,
                    desugarGraphFile = if (impactedFiles == null) {
                        getDesugarGraphFile(desugarGraphDir!!, classBucket)
                    } else {
                        null
                    }
                ),
                dxDexParams = dxDexParams
            )

            if (useGradleWorkers) {
                workerExecutor.submit(
                    DexWorkAction::class.java
                ) { configuration ->
                    configuration.isolationMode = IsolationMode.NONE
                    configuration.setParams(parameters)
                }
            } else {
                executor.execute<Any> {
                    val outputHandler = ParsingProcessOutputHandler(
                        ToolOutputParser(
                            DexParser(), Message.Kind.ERROR, loggerWrapper
                        ),
                        ToolOutputParser(DexParser(), loggerWrapper),
                        messageReceiver
                    )
                    var output: ProcessOutput? = null
                    try {
                        outputHandler.createOutput().use {
                            output = it
                            launchProcessing(
                                parameters,
                                output!!.standardOutput,
                                output!!.errorOutput,
                                messageReceiver
                            )
                        }
                    } finally {
                        output?.let {
                            try {
                                outputHandler.handleOutput(it)
                            } catch (e: ProcessException) {
                                // ignore this one
                            }
                        }
                    }
                    null
                }
            }
        }
        return dexOutputs
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

    /**
     * Computes the output path without using the jar absolute path. This method will use the
     * hash of the file content to determine the final output path, and this makes sure the task is
     * relocatable.
     */
    private fun getDexOutputForJar(input: File, outputDir: File, bucketId: Int?): File {
        val hash = inputJarHashesValues.getValue(input)

        return if (bucketId != null) {
            outputDir.resolve("${hash}_$bucketId.jar")
        } else {
            outputDir.resolve("$hash.jar")
        }
    }

    private fun getKeepRulesOutputForJar(input: File, outputDir: File, bucketId: Int): File {
        val hash = inputJarHashesValues.getValue(input)
        return outputDir.resolve("${hash}_$bucketId")
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
}

private class DexOutputs {
    val dexes = mutableListOf<File>()
    val keepRules = mutableListOf<File>()

    fun addDex(file: File) {
        dexes.add(file)
    }

    fun addKeepRule(file: File) {
        keepRules.add(file)
    }
}

private val loggerWrapper = LoggerWrapper.getLogger(DexArchiveBuilderTaskDelegate::class.java)