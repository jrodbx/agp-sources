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
import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.TransformException
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.crash.PluginCrashReporter
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dependency.getDexingArtifactConfiguration
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.DexMergingAction.MERGE_ALL
import com.android.build.gradle.internal.tasks.DexMergingAction.MERGE_EXTERNAL_LIBS
import com.android.build.gradle.internal.tasks.DexMergingAction.MERGE_LIBRARY_PROJECTS
import com.android.build.gradle.internal.tasks.DexMergingAction.MERGE_PROJECT
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.dexing.DexArchiveEntry
import com.android.builder.dexing.DexArchiveMerger
import com.android.builder.dexing.DexEntry
import com.android.builder.dexing.DexEntryBucket
import com.android.builder.dexing.DexingType
import com.android.builder.dexing.DexingType.LEGACY_MULTIDEX
import com.android.builder.dexing.DexingType.NATIVE_MULTIDEX
import com.android.builder.dexing.getSortedFilesInDir
import com.android.builder.dexing.getSortedRelativePathsInJar
import com.android.builder.dexing.isJarFile
import com.android.builder.files.SerializableFileChanges
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Throwables
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

/**
 * Task that merges input dex files into a smaller number of output dex files.
 *
 * It is used in two cases:
 *   1. When we want to put the smallest number of dex files in the APK (e.g., in release or legacy
 *      multidex builds). In this case, this task merges all the input dex files at once.
 *   2. When we can put a large number of dex files in the APK but have to keep them within a
 *      certain limit (e.g., in native multidex debug builds). In this case, for incrementality and
 *      parallelism, this task splits the input dex files into buckets, and merges each bucket in a
 *      Gradle work action. (The merged dex files of the buckets are then copied to the APK by a
 *      another task without further merging). In an incremental build, this task re-merges only
 *      the impacted buckets (those containing changed input dex files).
 */
@CacheableTask
abstract class DexMergingTask : NewIncrementalTask() {

    /**
     * Parameters shared between [DexMergingTask], [DexMergingTaskDelegate], and
     * [DexMergingWorkAction].
     */
    abstract class SharedParams {

        @get:Input
        abstract val dexingType: Property<DexingType>

        @get:Input
        abstract val minSdkVersion: Property<Int>

        @get:Input
        abstract val debuggable: Property<Boolean>

        @get:Internal
        abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

        @get:Nested
        abstract val mainDexListConfig: MainDexListConfig

        /**
         * Parameters for generating main dex list and merging dex files in [LEGACY_MULTIDEX] mode.
         */
        abstract class MainDexListConfig {

            @get:Optional
            @get:InputFile
            @get:PathSensitive(PathSensitivity.NONE)
            abstract val aaptGeneratedRules: RegularFileProperty

            @get:Optional
            @get:InputFiles
            @get:PathSensitive(PathSensitivity.NONE)
            abstract val userMultidexProguardRules: ListProperty<RegularFile>

            @get:Optional
            @get:Input
            abstract val platformMultidexProguardRules: ListProperty<String>

            @get:Optional
            @get:InputFile
            @get:PathSensitive(PathSensitivity.NONE)
            abstract val userMultidexKeepFile: Property<File>

            @get:Optional
            @get:Classpath
            abstract val libraryClasses: ConfigurableFileCollection
        }
    }

    @get:Nested
    abstract val sharedParams: SharedParams

    @get:Input
    abstract val numberOfBuckets: Property<Int>

    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var dexDirs: FileCollection
        private set

    @get:Incremental
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fileDependencyDexDir: DirectoryProperty

    // Fake folder, used as a way to set up dependency
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val duplicateClassesCheck: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Optional
    @get:OutputFile
    abstract val mainDexListOutput: RegularFileProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        // There are two sources of input dex files:
        //   - dexDirs: These directories contain dex files and possibly also jars of dex files
        //     (e.g., when generated by DexArchiveBuilderTask).
        //   - fileDependencyDexDir: This directory (if present) contains jars of dex files
        //     (generated by DexFileDependenciesTask).
        // Therefore, dexDirsOrJars = dexDirs + (jars in dexDirs and fileDependencyDexDir)
        val dexJars =
            (dexDirs + listOfNotNull(fileDependencyDexDir.orNull?.asFile)).flatMap { dir ->
                // Any valid jars should be immediately under the directory.
                // Also sort the jars to ensure deterministic order. Note that we sort files inside
                // each input directory, but not across the input directories, as the order of the
                // input directories is maintained by Gradle, and sorting them could break this
                // order and result in a bug (see
                // https://issuetracker.google.com/119064593#comment11 and commit
                // f4db68dccf76c35f5cdbd2cf3be3fb13b8abb767).
                check(dir.isDirectory) { "Directory does not exist: ${dir.path}" }
                dir.listFiles()!!.filter { isJarFile(it) }.sorted()
            }
        val dexDirsOrJars = dexDirs + dexJars

        val fileChanges = if (inputChanges.isIncremental) {
            inputChanges.getFileChanges(dexDirs) + inputChanges.getFileChanges(fileDependencyDexDir)
        } else {
            null
        }

        @Suppress("UnstableApiUsage")
        workerExecutor.noIsolation().submit(DexMergingTaskDelegate::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.initialize(
                sharedParams, numberOfBuckets, dexDirsOrJars, outputDir, inputChanges.isIncremental,
                fileChanges?.toSerializable(),
                mainDexListOutput = mainDexListOutput
            )
        }
    }

    class CreationAction @JvmOverloads constructor(
        creationConfig: ApkCreationConfig,
        private val action: DexMergingAction,
        private val dexingType: DexingType,
        private val dexingUsingArtifactTransforms: Boolean = true,
        private val separateFileDependenciesDexingTask: Boolean = false,
        private val outputType: InternalMultipleArtifactType<Directory> = InternalMultipleArtifactType.DEX
    ) : VariantTaskCreationAction<DexMergingTask, ApkCreationConfig>(creationConfig) {

        private val internalName: String = when (action) {
            MERGE_LIBRARY_PROJECTS -> creationConfig.computeTaskName("mergeLibDex")
            MERGE_EXTERNAL_LIBS -> creationConfig.computeTaskName("mergeExtDex")
            MERGE_PROJECT -> creationConfig.computeTaskName("mergeProjectDex")
            MERGE_ALL -> creationConfig.computeTaskName("mergeDex")
        }

        override val name = internalName
        override val type = DexMergingTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<DexMergingTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .use(taskProvider)
                .wiredWith(DexMergingTask::outputDir)
                .toAppendTo(outputType)

            if (dexingType === LEGACY_MULTIDEX) {
                creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider, DexMergingTask::mainDexListOutput)
                    .withName("mainDexList.txt")
                    .on(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
            }
        }

        override fun configure(task: DexMergingTask) {
            super.configure(task)

            val projectOptions = creationConfig.services.projectOptions

            // Shared parameters
            task.sharedParams.dexingType.setDisallowChanges(dexingType)
            task.sharedParams.minSdkVersion.setDisallowChanges(
                creationConfig.minSdkVersionWithTargetDeviceApi.getFeatureLevel()
            )
            task.sharedParams.debuggable.setDisallowChanges(creationConfig.debuggable)
            task.sharedParams.errorFormatMode.setDisallowChanges(
                SyncOptions.getErrorFormatMode(projectOptions)
            )
            if (dexingType === LEGACY_MULTIDEX) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES,
                    task.sharedParams.mainDexListConfig.aaptGeneratedRules
                )

                task.sharedParams.mainDexListConfig.userMultidexProguardRules.setDisallowChanges(
                    creationConfig.artifacts.getAll(MultipleArtifact.MULTIDEX_KEEP_PROGUARD)
                )

                creationConfig.multiDexKeepFile?.let {
                    task.sharedParams.mainDexListConfig.userMultidexKeepFile.setDisallowChanges(it)
                }

                task.sharedParams.mainDexListConfig.platformMultidexProguardRules
                    .setDisallowChanges(getPlatformRules())

                val libraryScopes = setOf(
                    QualifiedContent.Scope.PROVIDED_ONLY,
                    QualifiedContent.Scope.TESTED_CODE
                )
                val libraryClasses = creationConfig.transformManager
                    .getPipelineOutputAsFileCollection { contentTypes, scopes ->
                        contentTypes.contains(
                            QualifiedContent.DefaultContentType.CLASSES
                        ) && libraryScopes.intersect(scopes).isNotEmpty()
                    }

                val bootClasspath = creationConfig.variantScope.bootClasspath
                task.sharedParams.mainDexListConfig.libraryClasses
                    .from(bootClasspath, libraryClasses).disallowChanges()
            }

            // Input properties
            task.numberOfBuckets.setDisallowChanges(
                task.project.providers.provider { getNumberOfBuckets(projectOptions) }
            )

            // Input files
            task.dexDirs = getDexDirs(creationConfig, action)
            if (separateFileDependenciesDexingTask) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.EXTERNAL_FILE_LIB_DEX_ARCHIVES,
                    task.fileDependencyDexDir
                )
            }
            if (projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
                creationConfig.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.DUPLICATE_CLASSES_CHECK,
                    task.duplicateClassesCheck
                )
            }
        }

        private fun getDexDirs(
            creationConfig: ApkCreationConfig,
            action: DexMergingAction
        ): FileCollection {
            val attributes = getDexingArtifactConfiguration(creationConfig).getAttributes()

            fun forAction(action: DexMergingAction): FileCollection {
                when (action) {
                    MERGE_EXTERNAL_LIBS -> {
                        return if (dexingUsingArtifactTransforms) {
                            // If the file dependencies are being dexed in a task, don't also include them here
                            val artifactScope: AndroidArtifacts.ArtifactScope =
                                if (separateFileDependenciesDexingTask) {
                                    AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE
                                } else {
                                    AndroidArtifacts.ArtifactScope.EXTERNAL
                                }
                            creationConfig.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                artifactScope,
                                AndroidArtifacts.ArtifactType.DEX,
                                attributes
                            )
                        } else {
                            creationConfig.services.fileCollection(
                                creationConfig.artifacts.get(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE),
                                creationConfig.artifacts.get(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS)
                            )
                        }
                    }
                    MERGE_LIBRARY_PROJECTS -> {
                        return if (dexingUsingArtifactTransforms) {
                            // For incremental dexing, when requesting DEX we will need to indicate
                            // a preference for CLASSES_DIR over CLASSES_JAR, otherwise Gradle will
                            // select CLASSES_JAR by default. We do that by adding the
                            // LibraryElements.CLASSES attribute to the query.
                            val classesLibraryElements =
                                creationConfig.services.variantPropertiesApiServices.named(
                                    LibraryElements::class.java,
                                    LibraryElements.CLASSES
                                )
                            check(attributes.libraryElementsAttribute == null)
                            val updatedAttributes = AndroidAttributes(
                                attributes.stringAttributes, classesLibraryElements
                            )
                            creationConfig.variantDependencies.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.PROJECT,
                                AndroidArtifacts.ArtifactType.DEX,
                                updatedAttributes
                            )
                        } else {
                            creationConfig.services.fileCollection(
                                creationConfig.artifacts.get(InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE)
                            )
                        }
                    }
                    MERGE_PROJECT -> {
                        val files =
                            creationConfig.services.fileCollection(
                                creationConfig.artifacts.get(InternalArtifactType.PROJECT_DEX_ARCHIVE),
                                creationConfig.artifacts.get(InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE)
                            )

                        val variantType = creationConfig.variantType
                        if (variantType.isApk) {
                            creationConfig.onTestedConfig {
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
                    MERGE_ALL -> {
                        // technically, the Provider<> may not be needed, but the code would
                        // then assume that EXTERNAL_LIBS_DEX has already been registered by a
                        // Producer. Better to execute as late as possible.
                        return creationConfig.services.fileCollection(
                            forAction(MERGE_PROJECT),
                            forAction(MERGE_LIBRARY_PROJECTS),
                            if (dexingType == LEGACY_MULTIDEX) {
                                // we have to dex it
                                forAction(MERGE_EXTERNAL_LIBS)
                            } else {
                                // we merge external dex in a separate task
                                creationConfig.artifacts.getAll(InternalMultipleArtifactType.EXTERNAL_LIBS_DEX)
                            }
                        )
                    }
                }
            }

            return forAction(action)
        }

        private fun getNumberOfBuckets(projectOptions: ProjectOptions): Int {
            return when (action) {
                MERGE_ALL, MERGE_EXTERNAL_LIBS -> 1 // No bucketing
                MERGE_PROJECT, MERGE_LIBRARY_PROJECTS -> {
                    check(dexingType == NATIVE_MULTIDEX)

                    val customNumberOfBuckets =
                        projectOptions.getProvider(IntegerOption.DEXING_NUMBER_OF_BUCKETS).orNull
                    if (customNumberOfBuckets != null) {
                        check(customNumberOfBuckets >= 1) {
                            "The value of ${IntegerOption.DEXING_NUMBER_OF_BUCKETS.propertyName}" +
                                    " is invalid (must be >= 1): $customNumberOfBuckets"
                        }
                        return customNumberOfBuckets
                    }

                    // If the property is not set, compute its value
                    val minSdkVersion =
                        creationConfig.minSdkVersionWithTargetDeviceApi.getFeatureLevel()
                    val overrideMinSdkVersion =
                        if (creationConfig.variantType.isDynamicFeature && minSdkVersion < 21) {
                            // Dynamic features can always be built in native multidex mode
                            // even with minSdkVersion < 21, so for the following
                            // computation, we consider it to be 21.
                            21
                        } else {
                            minSdkVersion
                        }
                    getNumberOfBuckets(minSdkVersion = overrideMinSdkVersion)
                }
            }
        }

        /**
         * Returns the number of buckets based on a number of factors (e.g., dex file limit and
         * number of workers).
         *
         * Note that while increasing this number gives more fine-grained incrementality and more
         * parallelism, it could also have the following negative effects:
         *   1. Increased risk of hitting the dex file limit.
         *   2. Overhead of launching work actions and D8 invocations to process the too many
         *      small/empty buckets.
         */
        private fun getNumberOfBuckets(minSdkVersion: Int): Int {
            return min(
                getMaxNumberOfBucketsBasedOnDexFileLimit(minSdkVersion),
                getRecommendedNumberOfBucketsBasedOnWorkers()
            )
        }

        /**
         * Returns the maximum number of buckets based on the dex file limit. (In this context, the
         * dex file limit means the maximum number of dex files that can be put in an APK, not the
         * 64K method reference limit within a dex file.)
         *
         * The dex file limit is:
         *   - 100 for SDK 21 and 22 (see https://issuetracker.google.com/37134279)
         *   - ~500 for SDK 23+ (see dex2oat issue at https://issuetracker.google.com/110374966)
         *
         * When changing this number, we'll need to consider the effects documented at
         * [getNumberOfBuckets].
         */
        private fun getMaxNumberOfBucketsBasedOnDexFileLimit(minSdkVersion: Int): Int {
            // We figure out the maximum number of buckets as follows:
            //   - Suppose there are N buckets per dex merging task.
            //   - When bucketing happens, there are 3 dex merging tasks: one for the current
            //     project, one for library subprojects, and one for external libraries. The first
            //     two have N buckets each, the last one has 1 bucket. (See `getNumberOfBuckets
            //     method`.)
            //   - => There are 2 x N + 1 buckets.
            //   - After merging, each bucket produces a certain number of full dex files (dex files
            //     that hit the 64K limit) + at most one not-yet-full dex file.
            //   - => After merging, all buckets produce a certain number of full dex files + at
            //     most 2 x N + 1 not-yet-full dex files.
            //   - Assume that the total number of full dex files is at most 50. (Note that it's
            //     possible that if we merge these full dex files, we can get a smaller number of
            //     full dex files, as the 64K limit is the method reference limit, not the file
            //     size limit. Therefore, this estimate is intentionally generous.)
            //   - => The total number of merged dex files is at most 50 + 2 x N + 1. We need to
            //     keep this under the dex file limit => 51 + 2 x N <= dex file limit.
            //   - => Max N = (dex file limit - 51) / 2
            //   - In the following, we set max N slightly lower for safety.
            check(minSdkVersion >= 21) {
                "Expected minSdkVersion >= 21 but found $minSdkVersion"
            }
            return when (minSdkVersion) {
                21, 22 -> 20
                else -> 200
            }
        }

        /**
         * Returns the recommended number of buckets based on the number of workers.
         *
         * When changing this number, we'll need to consider the effects documented at
         * [getNumberOfBuckets].
         */
        private fun getRecommendedNumberOfBucketsBasedOnWorkers(): Int {
            // Set this to a constant for now to avoid possible remote cache misses (bug 164568060).
            // Once that bug is fixed, we can set this based on
            // project.gradle.startParameter.maxWorkerCount.
            // We set this to a high number because:
            //   - We want to add more incrementality to the task.
            //   - In an incremental build, only one or a few work actions are actually launched.
            //   - In a non-incremental build, this may have a positive or negative impact, but note
            //   that most builds should be incremental.
            return 16
        }
    }
}

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

@VisibleForTesting
abstract class DexMergingTaskDelegate : ProfileAwareWorkAction<DexMergingTaskDelegate.Params>() {

    abstract class Params : ProfileAwareWorkAction.Parameters() {

        abstract val sharedParams: Property<DexMergingTask.SharedParams>
        abstract val numberOfBuckets: Property<Int>
        abstract val dexDirsOrJars: ListProperty<File>
        abstract val outputDir: DirectoryProperty
        abstract val mainDexListOutput: RegularFileProperty

        abstract val incremental: Property<Boolean>
        abstract val fileChanges: Property<SerializableFileChanges>

        fun initialize(
            sharedParams: DexMergingTask.SharedParams,
            numberOfBuckets: Property<Int>,
            dexDirsOrJars: List<File>,
            outputDir: DirectoryProperty,
            incremental: Boolean,
            fileChanges: SerializableFileChanges?,
            mainDexListOutput: RegularFileProperty
        ) {
            this.sharedParams.set(sharedParams)
            this.numberOfBuckets.set(numberOfBuckets)
            this.dexDirsOrJars.set(dexDirsOrJars)
            this.outputDir.set(outputDir)
            this.incremental.set(incremental)
            this.fileChanges.set(fileChanges)
            this.mainDexListOutput.set(mainDexListOutput)
        }
    }

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    override fun run() {
        @Suppress("UnstableApiUsage")
        with(parameters) {
            val buckets =
                getBucketsToMerge(dexDirsOrJars.get(),
                        numberOfBuckets.get(),
                        incremental.get(),
                        fileChanges.orNull)

            val workQueue = workerExecutor.noIsolation()
            for ((bucketNumber, bucket) in buckets) {
                // Do not create a subdirectory for the bucket if numberOfBuckets = 1 and always
                // create subdirectories for the buckets if numberOfBuckets > 1 (even if some of
                // them are empty). This is so that some consumers (e.g., PerModuleBundleTask) won't
                // confuse legacy multidex with native multidex (see comment in PerModuleBundleTask:
                // “Don't rename if there is only one input folder as this might be the legacy
                // multidex case”).
                val outputDirForBucket = if (numberOfBuckets.get() == 1) {
                    outputDir.get().asFile
                } else {
                    outputDir.get().asFile.resolve(bucketNumber.toString())
                }
                FileUtils.cleanOutputDir(outputDirForBucket)

                workQueue.submit(DexMergingWorkAction::class.java) {
                    it.initializeFromProfileAwareWorkAction(this)
                    it.initialize(
                        sharedParams = sharedParams,
                        // If there is no bucketing, use a ForkJoinPool to merge (this is also the
                        // behavior of this task in the past). Alternatively, we can skip using an
                        // executor service, but we'll need to monitor the performance impact.
                        useForkJoinPool = numberOfBuckets.get() == 1,
                        dexEntryBucket = bucket,
                        outputDirForBucket = outputDirForBucket,
                        mainDexListOutput = mainDexListOutput.asFile.orNull
                    )
                }
            }
        }
    }

    companion object {

        private val isDexFile: (relativePath: String) -> Boolean =
            { it.endsWith(SdkConstants.DOT_DEX, ignoreCase = true) }

        /**
         * Splits the input dex files into buckets and returns the buckets that should be merged.
         *
         * In a non-incremental build, this method returns all buckets, some of which may be empty.
         *
         * In an incremental build, this method returns only the buckets that are impacted by the
         * file changes.
         *
         * @return a map from bucket numbers to [DexEntryBucket]'s.
         */
        private fun getBucketsToMerge(
            dexDirsOrJars: List<File>,
            numberOfBuckets: Int,
            incremental: Boolean,
            fileChanges: SerializableFileChanges?
        ): Map<Int, DexEntryBucket> {
            val bucketsToMerge = if (incremental) {
                getImpactedBuckets(fileChanges!!, numberOfBuckets)
            } else {
                (0 until numberOfBuckets).toSet()
            }

            val bucketMap = mutableMapOf<Int, MutableList<DexEntry>>()
            for (bucketNumber in bucketsToMerge) {
                bucketMap[bucketNumber] = mutableListOf()
            }

            for (dexDirOrJar in dexDirsOrJars) {
                val dexEntryRelativePaths = if (dexDirOrJar.isDirectory) {
                    getSortedFilesInDir(dexDirOrJar.toPath(), isDexFile).map {
                        dexDirOrJar.toPath().relativize(it).toString()
                    }
                } else {
                    getSortedRelativePathsInJar(dexDirOrJar, isDexFile)
                }
                for (relativePath in dexEntryRelativePaths) {
                    val bucketNumber = getBucketNumber(relativePath, numberOfBuckets)
                    if (bucketNumber in bucketsToMerge) {
                        bucketMap[bucketNumber]!!.add(DexEntry(dexDirOrJar, relativePath))
                    }
                }
            }

            return bucketMap.map { it.key to DexEntryBucket(it.value) }.toMap()
        }

        /** Returns the buckets that are impacted by the file changes in an incremental build. */
        private fun getImpactedBuckets(
            fileChanges: SerializableFileChanges,
            numberOfBuckets: Int
        ): Set<Int> {
            val hasModifiedRemovedJars =
                (fileChanges.modifiedFiles + fileChanges.removedFiles)
                    .find { isJarFile(it.file) } != null
            if (hasModifiedRemovedJars) {
                // We don't know if/what dex files were removed in the modified/removed jars, so we
                // don't know exactly which buckets are impacted and have to consider all buckets to
                // be impacted.
                return (0 until numberOfBuckets).toSet()
            }

            // For dex files in added jars and added/modified/removed dex files in directories,
            // compute their bucket numbers, those are impacted buckets.
            val addedJars = (fileChanges.addedFiles).map { it.file }.filter { isJarFile(it) }
            val relativePathsOfDexFilesInAddedJars =
                addedJars.flatMap { getSortedRelativePathsInJar(it, isDexFile) }
            val relativePathsOfChangedDexFilesInDirs =
                fileChanges.fileChanges.map { it.normalizedPath }.filter { isDexFile(it) }

            return (relativePathsOfDexFilesInAddedJars + relativePathsOfChangedDexFilesInDirs)
                    .map { getBucketNumber(it, numberOfBuckets) }.toSet()
        }

        /**
         * Returns the bucket number for a dex file or jar entry having the given relative path.
         *
         * Note that classes of the same package must be put in the same bucket, so that they can be
         * put in the same merged dex file by D8 (the merged dex files of the buckets are then
         * copied to the APK by another task without further merging).
         *   - This requirement is documented in a comment in the "Instant Run implementation"
         *     design doc (but not Instant Run specific): "there was a verifier error in preN where
         *     the virtual machine would fail to verify classes from the same package that are split
         *     in 2 dex files".
         *   - It is also a requirement from D8 until bug 158159959 is fixed.
         */
        @VisibleForTesting
        fun getBucketNumber(relativePath: String, numberOfBuckets: Int): Int {
            check(!File(relativePath).isAbsolute) {
                "Expected relative path but found absolute path: $relativePath"
            }
            check(relativePath.endsWith(SdkConstants.DOT_DEX, ignoreCase = true)) {
                "Expected .dex file but found: $relativePath"
            }

            val packagePath = File(relativePath).parent
            return if (packagePath.isNullOrEmpty()) {
                //  - This means that the dex file was produced in dex-indexed mode (e.g.,
                // `<jar-or-dir>/classes.dex`). (It's also possible that the class contained in the
                // dex file does not have a package name, e.g. `<jar-or-dir>/Foo.dex`, but this case
                // is not common and is not a correctness issue given the bucket assignment logic
                // below.)
                //  - We assume that the classes in indexed dex files don't share package names with
                // those in other dex files. Therefore, we put these dex files in a dedicated bucket
                // (bucket 0).
                //  - Note that the above assumption doesn't hold true for the indexed dex files
                // containing R classes, but it is probably okay for R classes to be put in a
                // separate bucket.
                0
            } else {
                //  - This means that the dex file was produced in dex-per-class mode (e.g.,
                // `com/example/Foo.dex`). (It's also possible that the dex file was produced in
                // dex-indexed mode and was put in a subdirectory, e.g.
                // `<jar-or-dir>/subdir/classes.dex`, but this case is not common and is not a
                // correctness issue given the bucket assignment logic below).
                //  - We will spread these dex files among the buckets, except bucket 0, which is
                // dedicated for indexed dex files (unless there is only 1 bucket). The reason is
                // that indexed dex files usually don't change, and dex-per-class dex files can
                // change frequently, so in an incremental build, we don't want a change in a
                // dex-per-class dex file to trigger re-merging the bucket for indexed dex files,
                // which is likely the biggest bucket.
                when (numberOfBuckets) {
                    1 -> 0
                    else -> {
                        // Normalize the path so that it is stable across filesystems. (For jar
                        // entries, the paths are already normalized.)
                        val normalizedPackagePath = File(packagePath).invariantSeparatorsPath
                        return abs(normalizedPackagePath.hashCode()) % (numberOfBuckets - 1) + 1
                    }
                }
            }
        }
    }
}

@VisibleForTesting
abstract class DexMergingWorkAction : ProfileAwareWorkAction<DexMergingWorkAction.Params>() {

    abstract class Params : ProfileAwareWorkAction.Parameters() {

        abstract val sharedParams: Property<DexMergingTask.SharedParams>
        abstract val useForkJoinPool: Property<Boolean>
        abstract val dexEntryBucket: Property<DexEntryBucket>
        abstract val outputDirForBucket: DirectoryProperty
        abstract val mainDexListOutput: RegularFileProperty

        fun initialize(
            sharedParams: Property<DexMergingTask.SharedParams>,
            useForkJoinPool: Boolean,
            dexEntryBucket: DexEntryBucket,
            outputDirForBucket: File,
            mainDexListOutput: File?
        ) {
            this.sharedParams.set(sharedParams)
            this.useForkJoinPool.set(useForkJoinPool)
            this.dexEntryBucket.set(dexEntryBucket)
            this.outputDirForBucket.set(outputDirForBucket)
            this.mainDexListOutput.set(mainDexListOutput)
        }
    }

    @Suppress("UnstableApiUsage")
    override fun run() {
        val dexArchiveEntries = parameters.dexEntryBucket.get().getDexEntriesWithContents()
        if (dexArchiveEntries.isEmpty()) {
            return
        }

        val forkJoinPool = if (parameters.useForkJoinPool.get()) {
            ForkJoinPool()
        } else {
            null
        }
        try {
            merge(
                parameters.sharedParams.get(),
                forkJoinPool,
                dexArchiveEntries,
                parameters.outputDirForBucket.get().asFile,
                parameters.mainDexListOutput.asFile.orNull?.toPath()
            )
        } finally {
            forkJoinPool?.shutdown()
            forkJoinPool?.awaitTermination(100, TimeUnit.SECONDS)
        }
    }

    private fun merge(
        sharedParams: DexMergingTask.SharedParams,
        forkJoinPool: ForkJoinPool?,
        dexArchiveEntries: List<DexArchiveEntry>,
        outputDir: File,
        mainDexListOutput: Path?
    ) {
        val logger = LoggerWrapper.getLogger(DexMergingWorkAction::class.java)
        val messageReceiver = MessageReceiverImpl(
            sharedParams.errorFormatMode.get(),
            Logging.getLogger(DexMergingTask::class.java)
        )

        try {
            var d8MinSdkVersion: Int = sharedParams.minSdkVersion.get()
            val dexingType = sharedParams.dexingType.get()
            if (d8MinSdkVersion < 21 && dexingType === NATIVE_MULTIDEX) {
                // D8 has baked-in logic that does not allow multiple dex files without
                // main dex list if min sdk < 21. When we deploy the app to a device with api
                // level 21+, we will promote legacy multidex to native multidex, but the min
                // sdk version will be less than 21, which will cause D8 failure as we do not
                // supply the main dex list. In order to prevent that, it is safe to set min
                // sdk version to 21.
                d8MinSdkVersion = 21
            }
            val merger = DexArchiveMerger.createD8DexMerger(
                messageReceiver,
                d8MinSdkVersion,
                sharedParams.debuggable.get(),
                forkJoinPool
            )

            val proguardRules = mutableListOf<Path>()
            sharedParams.mainDexListConfig.aaptGeneratedRules.asFile.orNull?.let {
                proguardRules.add(it.toPath())
            }
            sharedParams.mainDexListConfig.userMultidexProguardRules.get()
                    .asSequence()
                    .map(RegularFile::getAsFile)
                    .map(File::toPath)
                    .forEach { proguardRules.add(it) }
            merger.mergeDexArchives(
                dexArchiveEntries,
                outputDir.toPath(),
                proguardRules,
                sharedParams.mainDexListConfig.platformMultidexProguardRules.orNull,
                sharedParams.mainDexListConfig.userMultidexKeepFile.orNull?.toPath(),
                sharedParams.mainDexListConfig.libraryClasses.map { it.toPath() },
                mainDexListOutput
            )
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e))
            throw TransformException(e)
        }
    }
}
