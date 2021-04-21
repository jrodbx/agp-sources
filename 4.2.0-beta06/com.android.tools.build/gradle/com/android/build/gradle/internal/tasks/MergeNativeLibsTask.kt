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
import com.android.build.gradle.internal.BuildToolsExecutableInput
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.initialize
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.resources.FileStatus
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.function.Predicate
import javax.inject.Inject
import javax.inject.Provider

/**
 * Task to merge native libs from multiple modules
 */
@CacheableTask
abstract class MergeNativeLibsTask
@Inject constructor(objects: ObjectFactory) : IncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:SkipWhenEmpty
    abstract val projectNativeLibs: ConfigurableFileCollection

    @get:Classpath
    @get:SkipWhenEmpty
    abstract val subProjectNativeLibs: ConfigurableFileCollection

    @get:Classpath
    @get:SkipWhenEmpty
    abstract val externalLibNativeLibs: ConfigurableFileCollection

    @get:Classpath
    @get:Optional
    @get:SkipWhenEmpty
    abstract val profilerNativeLibs: DirectoryProperty

    @get:Input
    abstract val excludes: SetProperty<String>

    @get:Input
    abstract val pickFirsts: SetProperty<String>

    private lateinit var intermediateDir: File

    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    private lateinit var incrementalStateFile: File

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:Nested
    abstract val buildTools: BuildToolsExecutableInput


    override val incremental: Boolean
        get() = true

    // The runnable implementing the processing is not able to deal with fine-grained file but
    // instead is expecting directories of files. Use the unfiltered collection (since the filtering
    // changes the FileCollection of directories into a FileTree of files) to process, but don't
    // use it as a task input, it's covered by the [projectNativeLibs] above. This is a workaround
    // for the lack of gradle custom snapshotting: https://github.com/gradle/gradle/issues/8503.
    @get:Internal
    abstract val unfilteredProjectNativeLibs: ConfigurableFileCollection

    override fun doFullTaskAction() {
        doProcessing(false, emptyMap())
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        // Run non-incrementally if changedInputs.size > 20. Temporary workaround for
        // https://issuetracker.google.com/175337498
        val canRunIncrementally =
            incrementalStateFile.isFile && changedInputs.size <= 20
        doProcessing(canRunIncrementally, changedInputs)
    }

    private fun doProcessing(isIncremental: Boolean, changedInputs: Map<File, FileStatus>) {
        val allProfilerNativeLibs = profilerNativeLibs.orNull?.asFile?.listFiles()?.toSet() ?: emptySet()
        workerExecutor.noIsolation().submit(MergeJavaResWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.projectJavaRes.from(unfilteredProjectNativeLibs)
            it.subProjectJavaRes.from(subProjectNativeLibs)
            it.externalLibJavaRes.from(externalLibNativeLibs, allProfilerNativeLibs)
            it.outputDirectory.set(outputDir)
            it.incrementalStateFile.set(incrementalStateFile)
            it.incremental.set(isIncremental)
            it.cacheDir.set(cacheDir)
            if (isIncremental) {
                it.changedInputs.set(changedInputs)
            }
            it.contentType.set(NATIVE_LIBS)
            it.excludes.set(excludes)
            it.pickFirsts.set(pickFirsts)
        }
    }

    class CreationAction(creationConfig: VariantCreationConfig) :
            VariantTaskCreationAction<MergeNativeLibsTask, VariantCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("merge", "NativeLibs")

        override val type: Class<MergeNativeLibsTask>
            get() = MergeNativeLibsTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<MergeNativeLibsTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeNativeLibsTask::outputDir
            ).withName("out").on(MERGED_NATIVE_LIBS)
        }

        override fun configure(
            task: MergeNativeLibsTask
        ) {
            super.configure(task)

            task.excludes.setDisallowChanges(creationConfig.packagingOptions.jniLibs.excludes)
            task.pickFirsts.setDisallowChanges(creationConfig.packagingOptions.jniLibs.pickFirsts)
            task.intermediateDir =
                    creationConfig.paths.getIncrementalDir(
                        "${creationConfig.name}-mergeNativeLibs")

            val project = creationConfig.globalScope.project

            task.cacheDir
                .fileProvider(project.provider { File(task.intermediateDir, "zip-cache") })
                .disallowChanges()
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")

            task.buildTools.initialize(creationConfig)

            task.projectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools
                ).asFileTree.matching(patternSet))
                .disallowChanges()

            if (creationConfig is ApkCreationConfig) {
                task.externalLibNativeLibs.from(getExternalNativeLibs(creationConfig))
                    .disallowChanges()
                task.subProjectNativeLibs.from(getSubProjectNativeLibs(creationConfig))
                    .disallowChanges()
                if (creationConfig.shouldPackageProfilerDependencies) {
                    task.profilerNativeLibs.setDisallowChanges(
                            creationConfig.artifacts.get(InternalArtifactType.PROFILERS_NATIVE_LIBS)
                    )
                }
            }

            task.unfilteredProjectNativeLibs
                .from(getProjectNativeLibs(
                    creationConfig,
                    task.buildTools)
                ).disallowChanges()
        }
    }

    companion object {

        private const val includedFileSuffix = SdkConstants.DOT_NATIVE_LIBS
        private val includedFileNames = listOf(SdkConstants.FN_GDBSERVER, SdkConstants.FN_GDB_SETUP)

        // predicate logic must match patternSet logic below
        val predicate = Predicate<String> { fileName ->
            fileName.endsWith(includedFileSuffix) || includedFileNames.any { it == fileName }
        }

        // patternSet logic must match predicate logic above
        val patternSet: PatternSet
            get() {
                val patternSet = PatternSet().include("**/*$includedFileSuffix")
                includedFileNames.forEach { patternSet.include("**/$it") }
                return patternSet
            }
    }
}

fun getProjectNativeLibs(
    creationConfig: VariantCreationConfig,
    buildTools: BuildToolsExecutableInput
): FileCollection {
    val artifacts = creationConfig.artifacts
    val taskContainer = creationConfig.taskContainer
    val nativeLibs = creationConfig.services.fileCollection()

    // add merged project native libs
    nativeLibs.from(
        artifacts.get(InternalArtifactType.MERGED_JNI_LIBS)
    )

    val sdkComponents =
        getBuildService<SdkComponentsBuildService>(creationConfig.services.buildServiceRegistry).get()

    // add content of the local external native build if there is one
    taskContainer.cxxConfigurationModel?.variant?.objFolder?.let { objFolder ->
        nativeLibs.from(
            creationConfig.services.fileCollection(objFolder)
                    .builtBy(taskContainer.externalNativeBuildTask?.name)
        )
    }

    // add renderscript compilation output if support mode is enabled.
    if (creationConfig.variantDslInfo.renderscriptSupportModeEnabled) {
        val rsFileCollection: ConfigurableFileCollection =
                creationConfig.services.fileCollection(artifacts.get(RENDERSCRIPT_LIB))
        rsFileCollection.from(buildTools::supportNativeLibFolderProvider)
        if (creationConfig.variantDslInfo.renderscriptSupportModeBlasEnabled) {
            rsFileCollection.from(buildTools.supportBlasLibFolderProvider().map { rsBlasLib ->
                    if (!rsBlasLib.isDirectory) {
                        throw GradleException(
                            "Renderscript BLAS support mode is not supported in BuildTools $rsBlasLib"
                        )
                    }
                    rsBlasLib
            })
        }
        nativeLibs.from(rsFileCollection)
    }
    return nativeLibs
}

fun getSubProjectNativeLibs(creationConfig: VariantCreationConfig): FileCollection {
    val nativeLibs = creationConfig.services.fileCollection()
    // TODO (bug 154984238) extract native libs from java res jar before this task
    nativeLibs.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}

fun getExternalNativeLibs(creationConfig: VariantCreationConfig): FileCollection {
    val nativeLibs = creationConfig.services.fileCollection()
    // TODO (bug 154984238) extract native libs from java res jar before this task
    nativeLibs.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        creationConfig.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}
