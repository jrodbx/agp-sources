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
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dependency.BaseDexingTransform
import com.android.build.gradle.internal.dependency.KEEP_RULES_FILE_NAME
import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
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
import java.nio.file.Path

/**
 * Task that converts CLASS files to dex archives, [com.android.builder.dexing.DexArchive].
 * This will process class files, and for each of the input scopes (project, subprojects, external
 * Maven libraries, mixed-scope classses), corresponding dex archive will be produced.
 *
 * This task is incremental, only changed classes will be converted again. If only a single class
 * file is changed, only that file will be dex'ed. Additionally, if a jar changes, only classes in
 * that jar will be dex'ed.
 */
@CacheableTask
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

    @get:OutputDirectory
    abstract val projectOutputDex: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val projectOutputKeepRules: DirectoryProperty

    @get:OutputDirectory
    abstract val subProjectOutputDex: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val subProjectOutputKeepRules: DirectoryProperty

    @get:OutputDirectory
    abstract val externalLibsOutputDex: DirectoryProperty

    @get:OutputDirectory
    abstract val externalLibsFromAritfactTransformsDex: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val externalLibsFromAritfactTransformsKeepRules: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val externalLibsOutputKeepRules: DirectoryProperty

    @get:OutputDirectory
    abstract val mixedScopeOutputDex: DirectoryProperty

    @get:Optional
    @get:OutputDirectory
    abstract val mixedScopeOutputKeepRules: DirectoryProperty

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
                it.initializeFromAndroidVariantTask(this)
                it.inputDirs.from(externalLibDexFiles.files)
                it.outputDexDir.set(externalLibsFromAritfactTransformsDex)
                it.outputKeepRules.set(externalLibsFromAritfactTransformsKeepRules)
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

            projectOutputDex = projectOutputDex.asFile.get(),
            projectOutputKeepRules = projectOutputKeepRules.asFile.orNull,
            subProjectOutputDex = subProjectOutputDex.asFile.get(),
            subProjectOutputKeepRules = subProjectOutputKeepRules.asFile.orNull,
            externalLibsOutputDex = externalLibsOutputDex.asFile.get(),
            externalLibsOutputKeepRules = externalLibsOutputKeepRules.asFile.orNull,
            mixedScopeOutputDex = mixedScopeOutputDex.asFile.get(),
            mixedScopeOutputKeepRules = mixedScopeOutputKeepRules.asFile.orNull,

            dexParams = dexParams.toDexParameters(),

            desugarClasspathChangedClasses = getChanged(
                isIncremental,
                inputChanges,
                dexParams.desugarClasspath
            ),
            desugarGraphDir = desugarGraphDir.get().asFile.takeIf { dexParams.withDesugaring.get() },

            projectVariant = projectVariant.get(),
            inputJarHashesFile = inputJarHashesFile.get().asFile,
            numberOfBuckets = numberOfBuckets.get(),
            workerExecutor = workerExecutor,
            projectName = projectName,
            taskPath = path,
            analyticsService = analyticsService
        ).doProcess()
    }

    /**
     * Some files will be reported as both added and removed, as order of inputs may shift and we
     * are using @Classpath on inputs. For those, ignore the removed change,
     * and just handle them as added. For non-incremental builds return an empty set as dexing
     * pipeline traverses directories and we'd like to avoid serializing this information to the
     * worker action.
     */
    private fun getChanged(
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

    class CreationAction(
        enableDexingArtifactTransform: Boolean,
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<DexArchiveBuilderTask, ApkCreationConfig>(
        creationConfig
    ) {

        override val name = creationConfig.computeTaskName("dexBuilder")

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
            val classesFilter =
                StreamFilter { types, _ -> DefaultContentType.CLASSES in types }

            val transformManager = creationConfig.transformManager

            projectClasses = transformManager.getPipelineOutputAsFileCollection(
                StreamFilter { _, scopes -> scopes == setOf(Scope.PROJECT) },
                classesFilter
            )

            val desugaringClasspathScopes: MutableSet<ScopeType> = mutableSetOf(Scope.PROVIDED_ONLY)
            if (enableDexingArtifactTransform) {
                subProjectsClasses = creationConfig.services.fileCollection()
                externalLibraryClasses = creationConfig.services.fileCollection()
                mixedScopeClasses = creationConfig.services.fileCollection()
                dexExternalLibsInArtifactTransform = false

                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.TESTED_CODE)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
            } else if (creationConfig.variantScope.consumesFeatureJars()) {
                subProjectsClasses = creationConfig.services.fileCollection()
                externalLibraryClasses = creationConfig.services.fileCollection()
                dexExternalLibsInArtifactTransform = false

                // Get all classes from the scopes we are interested in.
                mixedScopeClasses = transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.isNotEmpty() && scopes.subtract(
                            TransformManager.SCOPE_FULL_WITH_FEATURES
                        ).isEmpty()
                    },
                    classesFilter
                )
                desugaringClasspathScopes.add(Scope.TESTED_CODE)
                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
                desugaringClasspathScopes.add(InternalScope.FEATURES)
            } else {
                subProjectsClasses =
                    transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.SUB_PROJECTS) },
                        classesFilter
                    )
                externalLibraryClasses =
                    transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.EXTERNAL_LIBRARIES) },
                        classesFilter
                    )
                // Get all classes that have more than 1 scope. E.g. project & subproject, or
                // project & subproject & external libs.
                mixedScopeClasses = transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes -> scopes.size > 1 && scopes.subtract(TransformManager.SCOPE_FULL_PROJECT).isEmpty() },
                    classesFilter
                )
                dexExternalLibsInArtifactTransform =
                    creationConfig.services.projectOptions[BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM_FOR_EXTERNAL_LIBS]
            }

            desugaringClasspathForArtifactTransforms = if (dexExternalLibsInArtifactTransform) {
                val testedExternalLibs = creationConfig.onTestedConfig {
                    it.variantDependencies.getArtifactCollection(
                        ConsumedConfigType.RUNTIME_CLASSPATH,
                        ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR
                    ).artifactFiles
                } ?: creationConfig.services.fileCollection()

                // Before b/115334911 was fixed, provided classpath did not contain the tested project.
                // Because we do not want tested variant classes in the desugaring classpath for
                // external libraries, we explicitly remove it.
                val testedProject = this.creationConfig.onTestedConfig {
                    val artifactType =
                        it.variantScope.publishingSpec.getSpec(
                            AndroidArtifacts.ArtifactType.CLASSES_JAR,
                            AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS
                        )!!.outputType
                    creationConfig.services.fileCollection(
                        it.artifacts.get(artifactType)
                    )
                } ?: creationConfig.services.fileCollection()

                creationConfig.services.fileCollection(
                    creationConfig.transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes ->
                            scopes.subtract(desugaringClasspathScopes).isEmpty()
                        },
                        classesFilter
                    ), testedExternalLibs, externalLibraryClasses
                ).minus(testedProject)
            } else {
                creationConfig.services.fileCollection()
            }

            desugaringClasspathClasses =
                creationConfig.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.contains(Scope.TESTED_CODE)
                                || scopes.subtract(desugaringClasspathScopes).isEmpty()
                    },
                    classesFilter
                )

            transformManager.consumeStreams(
                TransformManager.SCOPE_FULL_WITH_FEATURES,
                TransformManager.CONTENT_CLASS
            )
        }

        override val type: Class<DexArchiveBuilderTask> = DexArchiveBuilderTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<DexArchiveBuilderTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::projectOutputDex
            ).withName("out").withName("out").on(InternalArtifactType.PROJECT_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::subProjectOutputDex
            ).withName("out").withName("out").on(InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::externalLibsOutputDex
            ).withName("out").on(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::externalLibsFromAritfactTransformsDex
            ).withName("out").on(InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE_WITH_ARTIFACT_TRANSFORMS)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DexArchiveBuilderTask::mixedScopeOutputDex
            ).withName("out").on(InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE)
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
            if (creationConfig.needsShrinkDesugarLibrary) {
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    DexArchiveBuilderTask::projectOutputKeepRules
                ).withName("out").on(InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    DexArchiveBuilderTask::subProjectOutputKeepRules
                ).withName("out").on(InternalArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    DexArchiveBuilderTask::externalLibsOutputKeepRules
                ).withName("out").on(InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    DexArchiveBuilderTask::externalLibsFromAritfactTransformsKeepRules
                ).withName("out").on(InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_ARTIFACT_TRANSFORM_KEEP_RULES)
                creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    DexArchiveBuilderTask::mixedScopeOutputKeepRules
                ).withName("out").on(InternalArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES)
            }
        }

        override fun configure(task: DexArchiveBuilderTask) {
            super.configure(task)

            val projectOptions = creationConfig.services.projectOptions

            task.projectClasses.from(projectClasses)
            task.subProjectClasses.from(subProjectsClasses)
            task.mixedScopeClasses.from(mixedScopeClasses)

            val minSdkVersion = creationConfig
                .minSdkVersionWithTargetDeviceApi
                .getFeatureLevel()
            task.dexParams.minSdkVersion.set(minSdkVersion)
            val languageDesugaring =
                creationConfig.getJava8LangSupportType() == VariantScope.Java8LangSupport.D8
            task.dexParams.withDesugaring.set(languageDesugaring)
            if (languageDesugaring && minSdkVersion < AndroidVersion.VersionCodes.N
            ) {
                // Set classpath only if desugaring with D8 and minSdkVersion < 24
                task.dexParams.desugarClasspath.from(desugaringClasspathClasses)
                if (dexExternalLibsInArtifactTransform) {
                    // If dexing external libraries in artifact transforms, make sure to add them
                    // as desugaring classpath.
                    task.dexParams.desugarClasspath.from(externalLibraryClasses)
                }
            }
            // Set bootclasspath only for two cases:
            // 1. language desugaring with D8 and minSdkVersion < 24
            // 2. library desugaring enabled(required for API conversion)
            val libraryDesugaring = creationConfig.isCoreLibraryDesugaringEnabled
            if (languageDesugaring && minSdkVersion < AndroidVersion.VersionCodes.N
                || libraryDesugaring) {
                task.dexParams.desugarBootclasspath
                    .from(creationConfig.globalScope.filteredBootClasspath)
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
                task.dexParams.coreLibDesugarConfig.set(getDesugarLibConfig(task.project))
            }

            if (dexExternalLibsInArtifactTransform) {
                task.externalLibDexFiles.from(getDexForExternalLibs(task, "jar"))
                task.externalLibDexFiles.from(getDexForExternalLibs(task, "dir"))
            } else {
                task.externalLibClasses.from(externalLibraryClasses)
            }
        }

        /** Creates a detached configuration and sets up artifact transform for dexing. */
        private fun getDexForExternalLibs(task: DexArchiveBuilderTask, inputType: String): FileCollection {
            val project = creationConfig.services.projectInfo.getProject()
            project.dependencies.registerTransform(
                DexingExternalLibArtifactTransform::class.java
            ) {
                it.parameters.run {
                    this.projectName.set(project.name)
                    this.minSdkVersion.set(task.dexParams.minSdkVersion)
                    this.debuggable.set(task.dexParams.debuggable)
                    this.bootClasspath.from(task.dexParams.desugarBootclasspath)
                    this.desugaringClasspath.from(desugaringClasspathForArtifactTransforms)
                    this.enableDesugaring.set(task.dexParams.withDesugaring)
                    this.libConfiguration.set(task.dexParams.coreLibDesugarConfig)
                    this.errorFormat.set(task.dexParams.errorFormatMode)
                }

                // Until Gradle provides a better way to run artifact transforms for arbitrary
                // configuration, use "artifactType" attribute as that one is always present.
                it.from.attribute(Attribute.of("artifactType", String::class.java), inputType)
                // Make this attribute unique by using task name. This ensures that every task will
                // have a unique transform to run which is required as input parameters are
                // task-specific.
                it.to.attribute(Attribute.of("artifactType", String::class.java), "ext-dex-$name")
            }

            val detachedExtConf = project.configurations.detachedConfiguration()
            detachedExtConf.dependencies.add(project.dependencies.create(externalLibraryClasses))

            return detachedExtConf.incoming.artifactView {
                it.attributes.attribute(
                    Attribute.of("artifactType", String::class.java),
                    "ext-dex-$name"
                )
            }.files
        }
    }
}

private const val DEFAULT_BUFFER_SIZE_IN_KB = 100
private val DEFAULT_NUM_BUCKETS = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)

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

    @get:Input
    @get:Optional
    abstract val coreLibDesugarConfig: Property<String>

    @get:Input
    abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>

    fun toDexParameters(): DexParameters {
        return DexParameters(
            minSdkVersion = minSdkVersion.get(),
            debuggable = debuggable.get(),
            withDesugaring = withDesugaring.get(),
            desugarBootclasspath = desugarBootclasspath.files.toList(),
            desugarClasspath = desugarClasspath.files.toList(),
            coreLibDesugarConfig = coreLibDesugarConfig.orNull,
            errorFormatMode = errorFormatMode.get()
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

    override fun computeClasspathFiles(): List<Path> {
        return parameters.desugaringClasspath.files.map(File::toPath)
    }
}

/**
 * Implementation of the worker action that copies dex files and core library desugaring keep rules
 * to the final output locations. Originating files are output of [DexingExternalLibArtifactTransform]
 * transform.
 */
abstract class CopyDexOutput : ProfileAwareWorkAction<CopyDexOutput.Params>() {
    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val inputDirs: ConfigurableFileCollection
        abstract val outputDexDir: DirectoryProperty
        abstract val outputKeepRules: DirectoryProperty
    }
    override fun run() {
        FileUtils.cleanOutputDir(parameters.outputDexDir.asFile.get())
        var dexId = 0
        var keepRulesId = 0
        parameters.inputDirs.files.forEach { inputDir ->
            inputDir.walk().filter { it.extension == SdkConstants.EXT_DEX }.forEach { dexFile ->
                dexFile.copyTo(parameters.outputDexDir.asFile.get().resolve("classes_ext_${dexId++}.dex"))
            }
            parameters.outputKeepRules.asFile.orNull?.let {
                inputDir.resolve(KEEP_RULES_FILE_NAME).let {rules ->
                    if (rules.isFile) rules.copyTo(it.resolve("core_lib_keep_rules_${keepRulesId++}.txt"))
                }
            }
        }
    }
}
