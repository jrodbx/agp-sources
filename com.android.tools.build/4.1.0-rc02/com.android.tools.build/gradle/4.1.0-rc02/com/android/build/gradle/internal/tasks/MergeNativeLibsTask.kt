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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.function.Predicate
import javax.inject.Inject

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

    @get:Nested
    lateinit var packagingOptions: SerializablePackagingOptions
        private set

    private lateinit var intermediateDir: File

    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    private lateinit var incrementalStateFile: File

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

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
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    unfilteredProjectNativeLibs.files,
                    subProjectNativeLibs.files,
                    externalLibNativeLibs.files,
                    null,
                    outputDir.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    false,
                    cacheDir.get().asFile,
                    null,
                    NATIVE_LIBS,
                    listOf()
                )
            )
        }
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        if (!incrementalStateFile.isFile) {
            doFullTaskAction()
            return
        }
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeJavaResRunnable::class.java,
                MergeJavaResRunnable.Params(
                    unfilteredProjectNativeLibs.files,
                    subProjectNativeLibs.files,
                    externalLibNativeLibs.files,
                    null,
                    outputDir.get().asFile,
                    packagingOptions,
                    incrementalStateFile,
                    true,
                    cacheDir.get().asFile,
                    changedInputs,
                    NATIVE_LIBS,
                    listOf()
                )
            )
        }
    }

    class CreationAction(
        private val mergeScopes: Collection<ScopeType>,
        componentProperties: ComponentPropertiesImpl
    ) : VariantTaskCreationAction<MergeNativeLibsTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

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

            task.packagingOptions =
                    SerializablePackagingOptions(
                        creationConfig.globalScope.extension.packagingOptions)
            task.intermediateDir =
                    creationConfig.paths.getIncrementalDir(
                        "${creationConfig.name}-mergeNativeLibs")

            val project = creationConfig.globalScope.project

            task.cacheDir
                .fileProvider(project.provider { File(task.intermediateDir, "zip-cache") })
                .disallowChanges()
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")

            task.projectNativeLibs
                .from(getProjectNativeLibs(creationConfig).asFileTree.matching(patternSet))
                .disallowChanges()

            if (mergeScopes.contains(SUB_PROJECTS)) {
                task.subProjectNativeLibs.from(getSubProjectNativeLibs(creationConfig))
            }
            task.subProjectNativeLibs.disallowChanges()

            if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
                task.externalLibNativeLibs.from(getExternalNativeLibs(creationConfig))
            }
            task.externalLibNativeLibs.disallowChanges()

            task.unfilteredProjectNativeLibs
                .from(getProjectNativeLibs(creationConfig)).disallowChanges()
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

fun getProjectNativeLibs(componentProperties: ComponentPropertiesImpl): FileCollection {
    val globalScope = componentProperties.globalScope
    val artifacts = componentProperties.artifacts
    val taskContainer = componentProperties.taskContainer
    val project = globalScope.project

    val nativeLibs = globalScope.project.files()


    // add merged project native libs
    nativeLibs.from(
        artifacts.get(InternalArtifactType.MERGED_JNI_LIBS)
    )
    // add content of the local external native build
    if (taskContainer.externalNativeJsonGenerator != null) {
        nativeLibs.from(
            project
                .files(File(taskContainer.externalNativeJsonGenerator!!.get().objFolder))
                .builtBy(taskContainer.externalNativeBuildTask?.name)
        )
    }
    // add renderscript compilation output if support mode is enabled.
    if (componentProperties.variantDslInfo.renderscriptSupportModeEnabled) {
        val rsFileCollection: ConfigurableFileCollection =
                project.files(artifacts.get(RENDERSCRIPT_LIB))
        val rsLibs = globalScope.sdkComponents.get().supportNativeLibFolderProvider.orNull
        if (rsLibs?.isDirectory != null) {
            rsFileCollection.from(rsLibs)
        }
        if (componentProperties.variantDslInfo.renderscriptSupportModeBlasEnabled) {
            val rsBlasLib = globalScope.sdkComponents.get().supportBlasLibFolderProvider.orNull
            if (rsBlasLib == null || !rsBlasLib.isDirectory) {
                throw GradleException(
                    "Renderscript BLAS support mode is not supported in BuildTools $rsBlasLib"
                )
            } else {
                rsFileCollection.from(rsBlasLib)
            }
        }
        nativeLibs.from(rsFileCollection)
    }
    return nativeLibs
}

fun getSubProjectNativeLibs(componentProperties: ComponentPropertiesImpl): FileCollection {
    val nativeLibs = componentProperties.globalScope.project.files()
    // TODO (bug 154984238) extract native libs from java res jar before this task
    nativeLibs.from(
        componentProperties.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        componentProperties.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}

fun getExternalNativeLibs(componentProperties: ComponentPropertiesImpl): FileCollection {
    val nativeLibs = componentProperties.globalScope.project.files()
    // TODO (bug 154984238) extract native libs from java res jar before this task
    nativeLibs.from(
        componentProperties.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        componentProperties.variantDependencies.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}
