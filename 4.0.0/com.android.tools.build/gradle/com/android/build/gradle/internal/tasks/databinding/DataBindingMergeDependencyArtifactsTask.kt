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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Merges BR and Adapter artifacts from dependencies and serves it back to the annotation processor.
 * <b>
 * To account for V1 dependencies, we still copy their layout-info files from the compile classpath.
 */
@CacheableTask
abstract class DataBindingMergeDependencyArtifactsTask : NonIncrementalTask() {
    /**
     * Classes available at Runtime. We extract BR files from there so that even if there is no
     * compile time dependency on a particular artifact, we can still generate the BR file for it.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var runtimeDependencies: FileCollection
        private set
    /**
     * Classes that are available at Compile time. We use setter-store files in there so that
     * code access between dependencies is scoped to the compile classpath.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var compileTimeDependencies: FileCollection
        private set
    /**
     * Folder which includes all merged artifacts.
     */
    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                MergeArtifactsRunnable::class.java,
                MergeArtifactsParams(
                    outFolder = outFolder.get().asFile,
                    compileTimeDependencies = compileTimeDependencies.asFileTree.files,
                    runtimeDependencies = runtimeDependencies.asFileTree.files
                )
            )
        }
    }

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<DataBindingMergeDependencyArtifactsTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("dataBindingMergeDependencyArtifacts")
        override val type: Class<DataBindingMergeDependencyArtifactsTask>
            get() = DataBindingMergeDependencyArtifactsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DataBindingMergeDependencyArtifactsTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS,
                taskProvider,
                DataBindingMergeDependencyArtifactsTask::outFolder
            )
        }

        override fun configure(task: DataBindingMergeDependencyArtifactsTask) {
            super.configure(task)

            task.runtimeDependencies = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT
            )
            task.compileTimeDependencies = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.DATA_BINDING_ARTIFACT
            )
        }
    }
}

data class MergeArtifactsParams(
    val outFolder: File,
    val compileTimeDependencies: Set<File>,
    val runtimeDependencies: Set<File>
) : Serializable

class MergeArtifactsRunnable @Inject constructor(
    val params: MergeArtifactsParams
) : Runnable {
    override fun run() {
        params.run {
            FileUtils.cleanOutputDir(outFolder)

            compileTimeDependencies.filter { file ->
                DataBindingBuilder.RESOURCE_FILE_EXTENSIONS.any { ext ->
                    file.name.endsWith(ext)
                }
            }.forEach {
                FileUtils.copyFile(it, File(outFolder, it.name))
            }
            // feature's base dependency does not show up in Runtime so we copy everything from
            // compile and add runtimeDeps on top of it. We still override files because compile
            // dependency may reference to an older version of the BR file in non-feature
            // compilation.
            runtimeDependencies.filter {
                it.name.endsWith(DataBindingBuilder.BR_FILE_EXT)
            }.forEach {
                FileUtils.copyFile(it, File(outFolder, it.name))
            }
        }
    }
}