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
import com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
import com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.build.gradle.internal.packaging.SerializablePackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType.NATIVE_LIBS
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NATIVE_LIBS
import com.android.build.gradle.internal.scope.InternalArtifactType.RENDERSCRIPT_LIB
import com.android.build.gradle.internal.scope.VariantScope
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
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.function.Predicate
import javax.inject.Inject

/**
 * Task to merge native libs from multiple modules
 */
@CacheableTask
abstract class MergeNativeLibsTask
@Inject constructor(objects: ObjectFactory) : IncrementalTask() {

    // PathSensitivity.ABSOLUTE necessary here because of incorrect incremental info from Gradle
    // when using RELATIVE or NAME_ONLY: https://github.com/gradle/gradle/issues/9320, and we can't
    // use @Classpath because we need support for changing .so file names. A better solution will be
    // custom snapshots from gradle: https://github.com/gradle/gradle/issues/8503
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val projectNativeLibs: ConfigurableFileCollection

    @get:Classpath
    abstract val subProjectNativeLibs: ConfigurableFileCollection

    @get:Classpath
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
    // use it as an input, it's covered by the [projectNativeLibs] above.
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
        variantScope: VariantScope
    ) : VariantTaskCreationAction<MergeNativeLibsTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("merge", "NativeLibs")

        override val type: Class<MergeNativeLibsTask>
            get() = MergeNativeLibsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out MergeNativeLibsTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                MERGED_NATIVE_LIBS,
                taskProvider,
                MergeNativeLibsTask::outputDir,
                fileName = "out"
            )
        }

        override fun configure(task: MergeNativeLibsTask) {
            super.configure(task)

            task.packagingOptions =
                    SerializablePackagingOptions(
                        variantScope.globalScope.extension.packagingOptions)
            task.intermediateDir =
                    variantScope.getIncrementalDir(
                        "${variantScope.name}-mergeNativeLibs")

            val project = variantScope.globalScope.project

            task.cacheDir
                .fileProvider(project.provider { File(task.intermediateDir, "zip-cache") })
                .disallowChanges()
            task.incrementalStateFile = File(task.intermediateDir, "merge-state")

            task.projectNativeLibs.from(getProjectNativeLibs(variantScope).asFileTree.filter(spec))
                .disallowChanges()

            if (mergeScopes.contains(SUB_PROJECTS)) {
                task.subProjectNativeLibs.from(getSubProjectNativeLibs(variantScope))
            }
            task.subProjectNativeLibs.disallowChanges()

            if (mergeScopes.contains(EXTERNAL_LIBRARIES)) {
                task.externalLibNativeLibs.from(getExternalNativeLibs(variantScope))
            }
            task.externalLibNativeLibs.disallowChanges()

            task.unfilteredProjectNativeLibs
                .from(getProjectNativeLibs(variantScope)).disallowChanges()
        }
    }

    companion object {
        val predicate = Predicate<String> { filename ->
            filename.endsWith(SdkConstants.DOT_NATIVE_LIBS)
                    || SdkConstants.FN_GDBSERVER == filename
                    || SdkConstants.FN_GDB_SETUP == filename
        }
        val spec: (file: File) -> Boolean = { predicate.test(it.name) }
    }
}

fun getProjectNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    // add merged project native libs
    nativeLibs.from(
        scope.artifacts.getFinalProduct(InternalArtifactType.MERGED_JNI_LIBS)
    )
    // add content of the local external native build
    val project = scope.globalScope.project
    val taskContainer = scope.taskContainer
    if (taskContainer.externalNativeJsonGenerator != null) {
        nativeLibs.from(
            project
                .files(File(taskContainer.externalNativeJsonGenerator!!.get().objFolder))
                .builtBy(taskContainer.externalNativeBuildTask?.name)
        )
    }
    // add renderscript compilation output if support mode is enabled.
    if (scope.variantDslInfo.renderscriptSupportModeEnabled) {
        val rsFileCollection: ConfigurableFileCollection =
                project.files(scope.artifacts.getFinalProduct(RENDERSCRIPT_LIB))
        val rsLibs = scope.globalScope.sdkComponents.supportNativeLibFolderProvider.orNull
        if (rsLibs?.isDirectory != null) {
            rsFileCollection.from(rsLibs)
        }
        if (scope.variantDslInfo.renderscriptSupportModeBlasEnabled) {
            val rsBlasLib = scope.globalScope.sdkComponents.supportBlasLibFolderProvider.orNull
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

fun getSubProjectNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.PROJECT,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}

fun getExternalNativeLibs(scope: VariantScope): FileCollection {
    val nativeLibs = scope.globalScope.project.files()
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JAVA_RES
        )
    )
    nativeLibs.from(
        scope.getArtifactFileCollection(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            AndroidArtifacts.ArtifactScope.EXTERNAL,
            AndroidArtifacts.ArtifactType.JNI
        )
    )
    return nativeLibs
}
