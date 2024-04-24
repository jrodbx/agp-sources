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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
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
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Package all the APKs and mapping file into a zip for publishing to a repo.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
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
        workerExecutor.noIsolation().submit(ApkZipPackagingRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.apkFolder.set(apkFolder)
            it.mappingFile.set(mappingFile)
            it.zipOutputFile.set(apkZipFile)
        }
    }

    abstract class Params : ProfileAwareWorkAction.Parameters () {
        abstract val apkFolder: DirectoryProperty
        abstract val mappingFile: RegularFileProperty
        abstract val zipOutputFile: RegularFileProperty
    }

    abstract class ApkZipPackagingRunnable : ProfileAwareWorkAction<Params>() {
        override fun run() {
            FileUtils.deleteIfExists(parameters.zipOutputFile.asFile.get())

            val sourceFiles = parameters.apkFolder.asFile.get().listFiles() ?: emptyArray<File>()

            JarFlinger(parameters.zipOutputFile.asFile.get().toPath()).use { jar ->
                for (sourceFile in sourceFiles) {
                    jar.addFile(sourceFile.name, sourceFile.toPath())
                }

                parameters.mappingFile.asFile.orNull?.let {
                    jar.addFile(it.name, it.toPath())
                }
            }
        }
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ApkZipPackagingTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("zipApksFor")
        override val type: Class<ApkZipPackagingTask>
            get() = ApkZipPackagingTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ApkZipPackagingTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ApkZipPackagingTask::apkZipFile
            ).withName("apks.zip").on(InternalArtifactType.APK_ZIP)
        }

        override fun configure(
            task: ApkZipPackagingTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.APK, task.apkFolder
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                SingleArtifact.OBFUSCATION_MAPPING_FILE,
                task.mappingFile
            )
        }
    }
}
