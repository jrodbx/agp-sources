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

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.packaging.JarMerger
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/**
 * Package all the APKs and mapping file into a zip for publishing to a repo.
 */
abstract class ApkZipPackagingTask : NonIncrementalTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkFolder: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val mappingFile: RegularFileProperty

    @get:OutputFile
    abstract val apkZipFile: RegularFileProperty

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().submit(
            ApkZipPackagingRunnable::class.java,
            Params(
                apkFolder.asFile.get(),
                mappingFile.orNull?.asFile,
                apkZipFile.asFile.get()
            )
        )
    }

    private data class Params(
        val apkFolder: File,
        val mappingFile: File?,
        val zipOutputFile: File
    ) : Serializable

    private class ApkZipPackagingRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            FileUtils.deleteIfExists(params.zipOutputFile)

            val sourceFiles = params.apkFolder.listFiles() ?: emptyArray<File>()

            JarMerger(params.zipOutputFile.toPath()).use { jar ->
                for (sourceFile in sourceFiles) {
                    jar.addFile(sourceFile.name, sourceFile.toPath())
                }

                params.mappingFile?.let {
                    jar.addFile(it.name, it.toPath())
                }
            }
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ApkZipPackagingTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("zipApksFor")
        override val type: Class<ApkZipPackagingTask>
            get() = ApkZipPackagingTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ApkZipPackagingTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesFile(
                InternalArtifactType.APK_ZIP,
                taskProvider,
                ApkZipPackagingTask::apkZipFile,
                "apks.zip"
            )
        }

        override fun configure(task: ApkZipPackagingTask) {
            super.configure(task)

            variantScope.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.APK, task.apkFolder
            )
            if (variantScope.artifacts.hasFinalProduct(InternalArtifactType.APK_MAPPING)) {
                variantScope.artifacts.setTaskInputToFinalProduct(
                    InternalArtifactType.APK_MAPPING,
                    task.mappingFile
                )
            }
        }
    }
}