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
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dependency.BaseDexingTransform
import com.android.build.gradle.internal.dependency.computeGlobalSyntheticsDirName
import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.DexingTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.DexingTaskCreationActionImpl
import com.android.build.gradle.internal.utils.DesugarConfigJson.Companion.combineFileContents
import com.android.build.gradle.internal.utils.getDesugarLibConfigFiles
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import kotlin.math.max

/**
 * Task that converts CLASS files to dex archives, [com.android.builder.dexing.DexArchive].
 * This will process class files, and for each of the input scopes (project, subprojects, external
 * Maven libraries, mixed-scope classes), corresponding dex archive will be produced.
 *
 * This task is incremental, only changed classes will be converted again. If only a single class
 * file is changed, only that file will be dex'ed. Additionally, if a jar changes, only classes in
 * that jar will be dex'ed.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING)
abstract class DexArchiveBuilderTask : NewIncrementalTask() {

    @get:Incremental
    @get:Classpath
    abstract val projectClasses: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val subProjectClasses: ConfigurableFileCollection

    @get:Incremental
    @get:Classpath
    abstract val externalLibClasses: ConfigurableFileCollection

    /**
     * These are classes that contain multiple transform API scopes. E.g. if there is a transform
     * running before this task that outputs classes with both project and subProject scopes, this
     * input will contain them.
     */
    @get:Incremental
    @get:Classpath
    abstract val mixedScopeClasses: ConfigurableFileCollection

    @get:Nested
    abstract val projectOutputs: DexingOutputs

    @get:Nested
    abstract val subProjectOutputs: DexingOutputs

    @get:Nested
    abstract val externalLibsOutputs: DexingOutputs

    @get:Nested
    abstract val externalLibsFromArtifactTransformsOutputs: DexingOutputs

    @get:Nested
    abstract val mixedScopeOutputs: DexingOutputs

    @get:Nested
    abstract val dexParams: DexParameterInputs

    @get:LocalState
    @get:Optional
    abstract val desugarGraphDir: DirectoryProperty

    @get:Input
    abstract val projectVariant: Property<String>

    @get:LocalState
    abstract val inputJarHashesFile: RegularFileProperty

    /**
     * This property is annotated with [Internal] in order to allow cache hits across build that use
     * different number of buckets. Changing this property will not re-run the task, but that is
     * fine. See [canRunIncrementally] for details how this impacts incremental builds.
     */
    @get:Internal
    abstract val numberOfBuckets: Property<Int>

    @get:LocalState
    abstract val previousRunNumberOfBucketsFile: RegularFileProperty

    @get:Incremental
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    abstract val externalLibDexFiles: ConfigurableFileCollection

    /**
     * Task runs incrementally if input changes allow that and if the number of buckets is the same
     * as in the previous run. This is necessary in order to have correct incremental builds as
     * the output location for an input file is computed using the number of buckets.
     *
     * The following scenarios are handled:
     * - changing the number of buckets between runs will cause non-incremental run
     * - cache hit will not restore [previousRunNumberOfBucketsFile], and it will cause next run to
     * be non-incremental
     * - build in which the number of buckets is the same as in the [previousRunNumberOfBucketsFile]
     * can be incremental
     */
    private fun canRunIncrementally(inputChanges: InputChanges): Boolean {
        val canRunIncrementally =
            if (!inputChanges.isIncremental) false
            else {
                with(previousRunNumberOfBucketsFile.asFile.get()) {
                    if (!isFile) false
                    else readText() == numberOfBuckets.get().toString()
                }
            }

        if (!canRunIncrementally) {
            // If incremental run is not possible write the current number of buckets
            with(previousRunNumberOfBucketsFile.get().asFile) {
                FileUtils.mkdirs(parentFile)
                writeText(numberOfBuckets.get().toString())
            }
        }
        return canRunIncrementally
    }

    override fun doTaskAction(inputChanges: InputChanges) {
        val isIncremental = canRunIncrementally(inputChanges)

        if ((!externalLibDexFiles.isEmpty && !isIncremental) || getChanged(
                isIncremental,
                inputChanges,
                externalLibDexFiles
            ).isNotEmpty()
        ) {
            // If non-incremental run (with files), or any of the dex files changed, copy them again.
            workerExecutor.noIsolation().submit(CopyDexOutput::class.java) {
                it.initializeFromBaseTask(this)
                it.inputDirs.from(externalLibDexFiles.files)
                it.outputDexDir.set(externalLibsFromArtifactTransformsOutputs.dex)
                it.outputGlobalSynthetics.set(externalLibsFromArtifactTransformsOutputs.globalSynthetics)
            }
        }

        DexArchiveBuilderTaskDelegate(
            isIncremental = isIncremental,

            projectClasses = projectClasses.files,
            projectChangedClasses = getChanged(isIncremental, inputChanges, projectClasses),
            subProjectClasses = subProjectClasses.files,
            subProjectChangedClasses = getChanged(isIncremental, inputChanges, subProjectClasses),
            externalLibClasses = externalLibClasses.files,
            externalLibChangedClasses = getChanged(isIncremental, inputChanges, externalLibClasses),
            mixedScopeClasses = mixedScopeClasses.files,
            mixedScopeChangedClasses = getChanged(isIncremental, inputChanges, mixedScopeClasses),

            projectOutputs = DexArchiveBuilderTaskDelegate.DexingOutputs(projectOutputs),
            subProjectOutputs = DexArchiveBuilderTaskDelegate.DexingOutputs(subProjectOutputs),
            externalLibsOutputs = DexArchiveBuilderTaskDelegate.DexingOutputs(externalLibsOutputs),
            mixedScopeOutputs = DexArchiveBuilderTaskDelegate.DexingOutputs(mixedScopeOutputs),

            dexParams = dexParams.toDexParameters(),

            desugarClasspathChangedClasses = getChanged(
                isIncremental,
                inputChanges,
                dexParams.desugarClasspath
            ),
            desugarGraphDir = desugarGraphDir.get().asFile.takeIf { dexParams.withDesugaring.get() },

            inputJarHashesFile = inputJarHashesFile.get().asFile,
            numberOfBuckets = numberOfBuckets.get(),
            workerExecutor = workerExecutor,
            projectPath = projectPath,
            taskPath = path,
            analyticsService = analyticsService
        ).doProcess()
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
        classesClasspathUtils: ClassesClasspathUtils,
    ) : VariantTaskCreationAction<DexArchiveBuilderTask, ApkCreationConfig>(
        creationConfig
    ), DexingTaskCreationAction by DexingTaskCreationActionImpl(
        creationConfig
    ) {

        override val name = creationConfig.computeTaskNameInternal("dexBuilder")

        private val projectClasses: FileCollection
        private val subProjectsClasses: FileCollection
        private val externalLibraryClasses: FileCollection
        private val mixedScopeClasses: FileCollection
        private val desugaringClasspathClasses: FileCollection
        // Difference between this property and desugaringClasspathClasses is that for the test
        // variant, this property does not contain tested project code, allowing us to have more
        // cache hits when using artifact transforms.
        private val desugaringClasspathForArtifactTransforms: FileCollection
        private val dexExternalLibsInArtifactTransform: Boolean

        init {
            desugaringClasspathClasses = classesClasspathUtils.desugaringClasspathClasses
            projectClasses = classesClasspathUtils.projectClasses
            dexExternalLibsInArtifactTransform = classesClasspathUtils.dexExternalLibsInArtifactTransform

            subProjectsClasses = classesClasspathUtils.subProjectsClasses
            externalLibraryClasses = classesClasspathUtils.externalLibraryClasses
            mixedScopeClasses = classesClasspathUtils.mixedScopeClasses
            desugaringClasspathForArtifactTransforms = classesClasspathUtils.desugaringClasspathForArtifactTransforms
        }

        override val type: Class<DexArchiveBuilderTask> = DexArchiveBuilderTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<DexArchiveBuilderTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider
            ) { it.projectOutputs.dex }
                .withName("out").withName("out").on(InternalArtifactType.PROJECT_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider
            ) { it.subProjectOutputs.dex }
                .withName("out").withName("out").on(InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider
            ) { it.externalLibsOutputs.dex }
                .withName("out").on(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider
            ) { it.externalLibsFromArtifactTransformsOutputs.dex }
                .withName("out").on(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS)
            creationConfig.artifacts.setInitialProvider(
                taskProvider
            ) { it.mixedScopeOutputs.dex }
                .withName("out").on(InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::inputJarHashesFile
            ).withName("out").on(InternalArtifactType.DEX_ARCHIVE_INPUT_JAR_HASHES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::desugarGraphDir
            ).withName("out").on(InternalArtifactType.DESUGAR_GRAPH)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::previousRunNumberOfBucketsFile
            ).withName("out").on(InternalArtifactType.DEX_NUMBER_OF_BUCKETS_FILE)
            if (creationConfig.enableGlobalSynthetics) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider
                ) { it.projectOutputs.globalSynthetics }
                    .withName("out").on(InternalArtifactType.GLOBAL_SYNTHETICS_PROJECT)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider
                ) { it.subProjectOutputs.globalSynthetics }
                    .withName("out").on(InternalArtifactType.GLOBAL_SYNTHETICS_SUBPROJECT)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider
                ) { it.externalLibsOutputs.globalSynthetics }
                    .withName("out").on(InternalArtifactType.GLOBAL_SYNTHETICS_EXTERNAL_LIB)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider
                ) { it.mixedScopeOutputs.globalSynthetics }
                    .withName("out").on(InternalArtifactType.GLOBAL_SYNTHETICS_MIXED_SCOPE)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider
                ) { it.externalLibsFromArtifactTransformsOutputs.globalSynthetics }
                    .withName("out").on(InternalArtifactType.GLOBAL_SYNTHETICS_EXTERNAL_LIBS_ARTIFACT_TRANSFORM)
            }
        }

        override fun configure(task: DexArchiveBuilderTask) {
            super.configure(task)

            val projectOptions = creationConfig.services.projectOptions

            task.projectClasses.from(projectClasses)
            task.subProjectClasses.from(subProjectsClasses)
            task.mixedScopeClasses.from(mixedScopeClasses)

            val minSdkVersionForDexing = dexingCreationConfig.minSdkVersionForDexing
            task.dexParams.minSdkVersion.set(minSdkVersionForDexing)
            val languageDesugaring =
                dexingCreationConfig.java8LangSupportType == Java8LangSupport.D8
            task.dexParams.withDesugaring.set(languageDesugaring)

            // If min sdk version for dexing is >= N(24) then we can avoid adding extra classes to
            // the desugar classpaths.
            val languageDesugaringNeeded = languageDesugaring && minSdkVersionForDexing < AndroidVersion.VersionCodes.N
            if (languageDesugaringNeeded) {
                // Set classpath only if desugaring with D8 and minSdkVersion < 24
                task.dexParams.desugarClasspath.from(desugaringClasspathClasses)
                if (dexExternalLibsInArtifactTransform) {
                    // If dexing external libraries in artifact transforms, make sure to add them
                    // as desugaring classpath.
                    task.dexParams.desugarClasspath.from(externalLibraryClasses)
                }
            }
            // Set bootclasspath only for two cases:
            // 1. language desugaring with D8 and minSdkVersionForDexing < 24
            // 2. library desugaring enabled(required for API conversion)
            val libraryDesugaring = dexingCreationConfig.isCoreLibraryDesugaringEnabled
            if (languageDesugaringNeeded || libraryDesugaring) {
                task.dexParams.desugarBootclasspath
                        .from(creationConfig.global.filteredBootClasspath)
            }

            task.dexParams.errorFormatMode.set(SyncOptions.getErrorFormatMode(projectOptions))
            task.dexParams.debuggable.setDisallowChanges(
                creationConfig.debuggable
            )
            task.projectVariant.set(
                "${task.project.name}:${creationConfig.name}"
            )
            task.numberOfBuckets.set(
                task.project.providers.provider {
                    projectOptions.getProvider(IntegerOption.DEXING_NUMBER_OF_BUCKETS).orNull
                        ?: DEFAULT_NUM_BUCKETS
                }
            )
            if (libraryDesugaring) {
                task.dexParams.desugarLibConfigFiles.setFrom(
                        getDesugarLibConfigFiles(creationConfig.services))
            }

            task.dexParams.enableApiModeling.set(creationConfig.enableApiModeling)
            task.dexParams.enableGlobalSynthetics.set(creationConfig.enableGlobalSynthetics)

            if (dexExternalLibsInArtifactTransform) {
                task.externalLibDexFiles.from(getDexForExternalLibs(task, "jar"))
                task.externalLibDexFiles.from(getDexForExternalLibs(task, "dir"))
            } else {
                task.externalLibClasses.from(externalLibraryClasses)
            }
        }

        /** Creates a detached configuration and sets up artifact transform for dexing. */
        private fun getDexForExternalLibs(task: DexArchiveBuilderTask, inputType: String): FileCollection {
            val services = creationConfig.services

            services.dependencies.registerTransform(
                DexingExternalLibArtifactTransform::class.java
            ) {
                it.parameters.run {
                    this.projectName.set(services.projectInfo.name)
                    this.minSdkVersion.set(task.dexParams.minSdkVersion)
                    this.debuggable.set(task.dexParams.debuggable)
                    this.bootClasspath.from(task.dexParams.desugarBootclasspath)
                    this.desugaringClasspath.from(desugaringClasspathForArtifactTransforms)
                    this.errorFormat.set(task.dexParams.errorFormatMode)
                    this.enableDesugaring.set(task.dexParams.withDesugaring)
                    this.desugarLibConfigFiles.setFrom(task.dexParams.desugarLibConfigFiles)
                    this.enableGlobalSynthetics.set(task.dexParams.enableGlobalSynthetics)
                    this.enableApiModeling.set(task.dexParams.enableApiModeling)
                }

                // Until Gradle provides a better way to run artifact transforms for arbitrary
                // configuration, use "artifactType" attribute as that one is always present.
                it.from.attribute(Attribute.of("artifactType", String::class.java), inputType)
                // Make this attribute unique by using task name. This ensures that every task will
                // have a unique transform to run which is required as input parameters are
                // task-specific.
                it.to.attribute(Attribute.of("artifactType", String::class.java), "ext-dex-$name")
            }

            val detachedExtConf = services.configurations.detachedConfiguration()
            detachedExtConf.isCanBeConsumed = false
            detachedExtConf.isCanBeResolved = true
            detachedExtConf.dependencies.add(services.dependencies.create(externalLibraryClasses))

            return detachedExtConf.incoming.artifactView {
                it.attributes.attribute(
                    Attribute.of("artifactType", String::class.java),
                    "ext-dex-$name"
                )
            }.files
        }
    }

    /** Outputs for dexing (with d8) */
    abstract class DexingOutputs {

        @get:OutputDirectory
        abstract val dex: DirectoryProperty

        @get:Optional
        @get:OutputDirectory
        abstract val globalSynthetics: DirectoryProperty
    }

    companion object {
        /**
         * Some files will be reported as both added and removed, as order of inputs may shift and we
         * are using @Classpath on inputs. For those, ignore the removed change,
         * and just handle them as added. For non-incremental builds return an empty set as dexing
         * pipeline traverses directories and we'd like to avoid serializing this information to the
         * worker action.
         */
        fun getChanged(
            canRunIncrementally: Boolean,
            inputChanges: InputChanges,
            input: FileCollection
        ): Set<FileChange> {
            if (!canRunIncrementally) {
                return emptySet()
            }
            val fileChanges = mutableMapOf<File, FileChange>()

            inputChanges.getFileChanges(input).forEach { change ->
                val currentValue = fileChanges[change.file]
                if (currentValue == null || (currentValue.changeType == ChangeType.REMOVED && change.changeType == ChangeType.ADDED)) {
                    fileChanges[change.file] = change
                }
            }
            return fileChanges.values.toSet()
        }
    }
}

val DEFAULT_NUM_BUCKETS = max(Runtime.getRuntime().availableProcessors() / 2, 1)

/** Parameters required for dexing (with D8). */
abstract class DexParameterInputs {

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:Input
    abstract val withDesugaring: Property<Boolean>

    @get:CompileClasspath
    abstract val desugarBootclasspath: ConfigurableFileCollection

    @get:Incremental
    @get:CompileClasspath
    abstract val desugarClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val desugarLibConfigFiles: ConfigurableFileCollection

    @get:Input
    abstract val enableGlobalSynthetics: Property<Boolean>

    @get:Input
    abstract val enableApiModeling: Property<Boolean>

    @get:Input
    abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

    fun toDexParameters(): DexParameters {
        return DexParameters(
            minSdkVersion = minSdkVersion.get(),
            debuggable = debuggable.get(),
            withDesugaring = withDesugaring.get(),
            desugarBootclasspath = desugarBootclasspath.files.toList(),
            desugarClasspath = desugarClasspath.files.toList(),
            coreLibDesugarConfig = combineFileContents(desugarLibConfigFiles.files),
            enableApiModeling = enableApiModeling.get(),
            errorFormatMode = errorFormatMode.get(),
        )
    }
}

/**
 * Ad-hoc artifact transform used to desugar and dex external libraries, that is using Gradle
 * built-in caching. Every external library is desugared against classpath that consists of all
 * external libraries.
 */
@CacheableTransform
abstract class DexingExternalLibArtifactTransform: BaseDexingTransform<DexingExternalLibArtifactTransform.Parameters>() {
    interface Parameters: BaseDexingTransform.Parameters {
        @get:CompileClasspath
        val desugaringClasspath: ConfigurableFileCollection
    }

    override fun computeClasspathFiles() = parameters.desugaringClasspath.files.toList()
}

/**
 * Implementation of the worker action that copies dex files and global synthetics
 * to the final output locations. Originating files are output of [DexingExternalLibArtifactTransform]
 * transform.
 */
abstract class CopyDexOutput : ProfileAwareWorkAction<CopyDexOutput.Params>() {
    abstract class Params : Parameters() {
        abstract val inputDirs: ConfigurableFileCollection
        abstract val outputDexDir: DirectoryProperty
        abstract val outputGlobalSynthetics: DirectoryProperty
    }
    override fun run() {
        FileUtils.cleanOutputDir(parameters.outputDexDir.asFile.get())
        parameters.outputGlobalSynthetics.asFile.orNull?.let { FileUtils.cleanOutputDir(it) }

        var dexId = 0
        var syntheticsId = 0
        parameters.inputDirs.files.forEach { inputDir ->
            inputDir.walk().filter { it.extension == SdkConstants.EXT_DEX }.forEach { dexFile ->
                dexFile.copyTo(parameters.outputDexDir.asFile.get().resolve("classes_ext_${dexId++}.dex"))
            }
            parameters.outputGlobalSynthetics.asFile.orNull?.let {
                inputDir.resolve(computeGlobalSyntheticsDirName(inputDir)).let { dir ->
                    dir.walk().forEach { file ->
                        file.copyTo(it.resolve("global_synthetics_${syntheticsId++}"))
                    }
                }
            }
        }
    }
}
