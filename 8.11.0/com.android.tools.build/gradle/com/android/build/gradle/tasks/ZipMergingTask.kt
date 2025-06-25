/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FN_INTERMEDIATE_FULL_JAR
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.util.function.Predicate
import java.util.zip.Deflater

/** Task to merge the res/classes intermediate jars from a library into a single one  */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MISC, secondaryTaskCategories = [TaskCategory.ZIPPING, TaskCategory.MERGING])
abstract class ZipMergingTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryInputFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val javaResDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    public override fun doTaskAction() {
        val destinationFile = outputFile.get().asFile
        FileUtils.cleanOutputDir(destinationFile.parentFile)
        val usedNamesPredicate = object : Predicate<String> {
            val usedNames = mutableSetOf<String>()

            override fun test(t: String): Boolean {
                return usedNames.add(t)
            }
        }

        JarFlinger(
            destinationFile.toPath(),
            usedNamesPredicate
        ).use {
            // Don't compress because compressing takes extra time, and this jar doesn't go into any
            // APKs or AARs.
            it.setCompressionLevel(Deflater.NO_COMPRESSION)
            val lib = libraryInputFile.get().asFile
            if (lib.exists()) {
                it.addJar(lib.toPath())
            }
            val javaRes = javaResDirectory.orNull?.asFile
            if (javaRes?.exists() == true) {
                it.addDirectory(javaRes.toPath())
            }
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<ZipMergingTask, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("createFullJar")
        override val type: Class<ZipMergingTask>
            get() = ZipMergingTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ZipMergingTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ZipMergingTask::outputFile
            ).withName(creationConfig.getArtifactName(FN_INTERMEDIATE_FULL_JAR))
             .on(InternalArtifactType.FULL_JAR)
        }

        override fun configure(
            task: ZipMergingTask
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts
            artifacts.setTaskInputToFinalProduct(InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR, task.libraryInputFile)
            artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.JAVA_RES,
                task.javaResDirectory
            )
        }
    }
}
