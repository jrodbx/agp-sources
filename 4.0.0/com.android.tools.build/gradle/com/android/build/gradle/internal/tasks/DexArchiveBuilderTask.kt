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

import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.dexing.DexParameters
import com.android.build.gradle.internal.dexing.DxDexParameters
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getDesugarLibConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.DexOptions
import com.android.builder.dexing.DexerTool
import com.android.builder.utils.FileCache
import com.android.sdklib.AndroidVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File

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

    @get:Nested
    abstract val dxDexParams: DxDexParameterInputs

    @get:Input
    abstract val incrementalDexingV2: Property<Boolean>

    @get:LocalState
    @get:Optional
    abstract val desugarGraphDir: DirectoryProperty

    @get:Input
    abstract val projectVariant: Property<String>

    @get:LocalState
    abstract val inputJarHashesFile: RegularFileProperty

    @get:Input
    abstract val dexer: Property<DexerTool>

    @get:Input
    abstract val numberOfBuckets: Property<Int>

    @get:Input
    abstract val useGradleWorkers: Property<Boolean>

    private var userLevelCache: FileCache? = null

    override fun doTaskAction(inputChanges: InputChanges) {
        DexArchiveBuilderTaskDelegate(
            isIncremental = inputChanges.isIncremental,

            projectClasses = projectClasses.files,
            projectChangedClasses = getChanged(inputChanges, projectClasses),
            subProjectClasses = subProjectClasses.files,
            subProjectChangedClasses = getChanged(inputChanges, subProjectClasses),
            externalLibClasses = externalLibClasses.files,
            externalLibChangedClasses = getChanged(inputChanges, externalLibClasses),
            mixedScopeClasses = mixedScopeClasses.files,
            mixedScopeChangedClasses = getChanged(inputChanges, mixedScopeClasses),

            projectOutputDex = projectOutputDex.asFile.get(),
            projectOutputKeepRules = projectOutputKeepRules.asFile.orNull,
            subProjectOutputDex = subProjectOutputDex.asFile.get(),
            subProjectOutputKeepRules = subProjectOutputKeepRules.asFile.orNull,
            externalLibsOutputDex = externalLibsOutputDex.asFile.get(),
            externalLibsOutputKeepRules = externalLibsOutputKeepRules.asFile.orNull,
            mixedScopeOutputDex = mixedScopeOutputDex.asFile.get(),
            mixedScopeOutputKeepRules = mixedScopeOutputKeepRules.asFile.orNull,

            dexParams = dexParams.toDexParameters(),
            dxDexParams = dxDexParams.toDxDexParameters(),

            desugarClasspathChangedClasses = getChanged(
                inputChanges,
                dexParams.desugarClasspath
            ),

            incrementalDexingV2 = incrementalDexingV2.get(),
            desugarGraphDir = desugarGraphDir.get().asFile.takeIf { incrementalDexingV2.get() },

            projectVariant = projectVariant.get(),
            inputJarHashesFile = inputJarHashesFile.get().asFile,
            dexer = dexer.get(),
            numberOfBuckets = numberOfBuckets.get(),
            useGradleWorkers = useGradleWorkers.get(),
            workerExecutor = workerExecutor,
            userLevelCache = userLevelCache,
            messageReceiver = MessageReceiverImpl(dexParams.errorFormatMode.get(), logger)
        ).doProcess()
    }

    /**
     * Some files will be reported as both added and removed, as order of inputs may shift and we
     * are using @Classpath on inputs. For those, ignore the removed change,
     * and just handle them as added. For non-incremental builds return an empty set as dexing
     * pipeline traverses directories and we'd like to avoid serializing this information to the
     * worker action.
     */
    private fun getChanged(inputChanges: InputChanges, input: FileCollection): Set<FileChange> {
        if (!inputChanges.isIncremental) {
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
        private val dexOptions: DexOptions,
        enableDexingArtifactTransform: Boolean,
        private val userLevelCache: FileCache?,
        variantScope: VariantScope
    ) : VariantTaskCreationAction<DexArchiveBuilderTask>(variantScope) {

        override val name = variantScope.getTaskName("dexBuilder")

        private val projectClasses: FileCollection
        private val subProjectsClasses: FileCollection
        private val externalLibraryClasses: FileCollection
        private val mixedScopeClasses: FileCollection
        private val desugaringClasspathClasses: FileCollection

        init {
            val classesFilter =
                StreamFilter { types, _ -> DefaultContentType.CLASSES in types }

            projectClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                StreamFilter { _, scopes -> scopes == setOf(Scope.PROJECT) },
                classesFilter
            )

            val desugaringClasspathScopes: MutableSet<ScopeType> =
                mutableSetOf(Scope.TESTED_CODE, Scope.PROVIDED_ONLY)
            if (enableDexingArtifactTransform) {
                subProjectsClasses = variantScope.globalScope.project.files()
                externalLibraryClasses = variantScope.globalScope.project.files()
                mixedScopeClasses = variantScope.globalScope.project.files()

                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
            } else if (variantScope.consumesFeatureJars()) {
                subProjectsClasses = variantScope.globalScope.project.files()
                externalLibraryClasses = variantScope.globalScope.project.files()

                // Get all classes from the scopes we are interested in.
                mixedScopeClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.isNotEmpty() && scopes.subtract(
                            TransformManager.SCOPE_FULL_WITH_FEATURES
                        ).isEmpty()
                    },
                    classesFilter
                )
                desugaringClasspathScopes.add(Scope.EXTERNAL_LIBRARIES)
                desugaringClasspathScopes.add(Scope.SUB_PROJECTS)
                desugaringClasspathScopes.add(InternalScope.FEATURES)
            } else {
                subProjectsClasses =
                    variantScope.transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.SUB_PROJECTS) },
                        classesFilter
                    )
                externalLibraryClasses =
                    variantScope.transformManager.getPipelineOutputAsFileCollection(
                        StreamFilter { _, scopes -> scopes == setOf(Scope.EXTERNAL_LIBRARIES) },
                        classesFilter
                    )
                // Get all classes that have more than 1 scope. E.g. project & subproject, or
                // project & subproject & external libs.
                mixedScopeClasses = variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes -> scopes.size > 1 && scopes.subtract(TransformManager.SCOPE_FULL_PROJECT).isEmpty() },
                    classesFilter
                )
            }

            desugaringClasspathClasses =
                variantScope.transformManager.getPipelineOutputAsFileCollection(
                    StreamFilter { _, scopes ->
                        scopes.subtract(desugaringClasspathScopes).isEmpty()
                    },
                    classesFilter
                )

            @Suppress("DEPRECATION") // remove all class files from the transform streams
            variantScope.transformManager.consumeStreams(
                TransformManager.SCOPE_FULL_WITH_FEATURES,
                TransformManager.CONTENT_CLASS
            )
        }

        override val type: Class<DexArchiveBuilderTask> = DexArchiveBuilderTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DexArchiveBuilderTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.PROJECT_DEX_ARCHIVE,
                taskProvider,
                DexArchiveBuilderTask::projectOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.SUB_PROJECT_DEX_ARCHIVE,
                taskProvider,
                DexArchiveBuilderTask::subProjectOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.EXTERNAL_LIBS_DEX_ARCHIVE,
                taskProvider,
                DexArchiveBuilderTask::externalLibsOutputDex
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.MIXED_SCOPE_DEX_ARCHIVE,
                taskProvider,
                DexArchiveBuilderTask::mixedScopeOutputDex
            )
            variantScope.artifacts.producesFile(
                InternalArtifactType.DEX_ARCHIVE_INPUT_JAR_HASHES,
                taskProvider,
                DexArchiveBuilderTask::inputJarHashesFile
            )
            variantScope.artifacts.producesDir(
                InternalArtifactType.DESUGAR_GRAPH,
                taskProvider,
                DexArchiveBuilderTask::desugarGraphDir
            )
            if (variantScope.needsShrinkDesugarLibrary) {
                variantScope.artifacts.producesDir(
                    InternalArtifactType.DESUGAR_LIB_PROJECT_KEEP_RULES,
                    taskProvider,
                    DexArchiveBuilderTask::projectOutputKeepRules
                )
                variantScope.artifacts.producesDir(
                    InternalArtifactType.DESUGAR_LIB_SUBPROJECT_KEEP_RULES,
                    taskProvider,
                    DexArchiveBuilderTask::subProjectOutputKeepRules
                )
                variantScope.artifacts.producesDir(
                    InternalArtifactType.DESUGAR_LIB_EXTERNAL_LIBS_KEEP_RULES,
                    taskProvider,
                    DexArchiveBuilderTask::externalLibsOutputKeepRules
                )
                variantScope.artifacts.producesDir(
                    InternalArtifactType.DESUGAR_LIB_MIXED_SCOPE_KEEP_RULES,
                    taskProvider,
                    DexArchiveBuilderTask::mixedScopeOutputKeepRules
                )
            }
        }

        override fun configure(task: DexArchiveBuilderTask) {
            super.configure(task)

            val projectOptions = variantScope.globalScope.projectOptions

            task.projectClasses.from(projectClasses)
            task.subProjectClasses.from(subProjectsClasses)
            task.externalLibClasses.from(externalLibraryClasses)
            task.mixedScopeClasses.from(mixedScopeClasses)

            task.incrementalDexingV2.setDisallowChanges(
                variantScope.globalScope.project.provider {
                    projectOptions.get(BooleanOption.ENABLE_INCREMENTAL_DEXING_V2)
                })

            val minSdkVersion = variantScope
                .variantDslInfo
                .minSdkVersionWithTargetDeviceApi
                .featureLevel
            task.dexParams.minSdkVersion.set(minSdkVersion)
            val languageDesugaring =
                variantScope.java8LangSupportType == VariantScope.Java8LangSupport.D8
            task.dexParams.withDesugaring.set(languageDesugaring)
            if (languageDesugaring && minSdkVersion < AndroidVersion.VersionCodes.N
            ) {
                // Set classpath only if desugaring with D8 and minSdkVersion < 24
                task.dexParams.desugarClasspath.from(desugaringClasspathClasses)
            }
            // Set bootclasspath only for two cases:
            // 1. language desugaring with D8 and minSdkVersion < 24
            // 2. library desugaring enabled(required for API conversion)
            val libraryDesugaring = variantScope.isCoreLibraryDesugaringEnabled
            if (languageDesugaring && minSdkVersion < AndroidVersion.VersionCodes.N
                || libraryDesugaring) {
                task.dexParams.desugarBootclasspath
                    .from(variantScope.globalScope.filteredBootClasspath)
            }

            task.dexParams.errorFormatMode.set(SyncOptions.getErrorFormatMode(projectOptions))
            task.dexer.set(variantScope.dexer)
            task.useGradleWorkers.set(projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
            task.dxDexParams.inBufferSize.set(
                (projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE)
                    ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
            )
            task.dxDexParams.outBufferSize.set(
                (projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE)
                    ?: DEFAULT_BUFFER_SIZE_IN_KB) * 1024
            )
            task.dexParams.debuggable.setDisallowChanges(
                variantScope.variantDslInfo.isDebuggable
            )
            task.projectVariant.set(
                "${variantScope.globalScope.project.name}:${variantScope.name}"
            )
            task.numberOfBuckets.set(
                projectOptions.get(IntegerOption.DEXING_NUMBER_OF_BUCKETS) ?: DEFAULT_NUM_BUCKETS
            )
            task.dxDexParams.dxNoOptimizeFlagPresent.set(
                dexOptions.additionalParameters.contains("--no-optimize")
            )
            task.userLevelCache = userLevelCache
            if (libraryDesugaring) {
                task.dexParams.coreLibDesugarConfig
                    .set(getDesugarLibConfig(variantScope.globalScope.project))
            }
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

/** Parameters required for dexing with DX. */
abstract class DxDexParameterInputs {

    @get:Input
    abstract val inBufferSize: Property<Int>

    @get:Input
    abstract val outBufferSize: Property<Int>

    @get:Input
    abstract val dxNoOptimizeFlagPresent: Property<Boolean>

    fun toDxDexParameters(): DxDexParameters {
        return DxDexParameters(
            inBufferSize = inBufferSize.get(),
            outBufferSize = outBufferSize.get(),
            dxNoOptimizeFlagPresent = dxNoOptimizeFlagPresent.get(),
            jumboMode = DexArchiveBuilderCacheHandler.isJumboModeEnabledForDx()
        )
    }
}
