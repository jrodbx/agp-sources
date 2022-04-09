/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.TaskCategory
import com.android.tools.profgen.HumanReadableProfile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.lang.RuntimeException

/**
 * Task that processes profiles files for library.
 *
 * As of now, we do not merge any files while building an aar, this is a potential future
 * enhancement.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE)
abstract class ProcessLibraryArtProfileTask: NonIncrementalTask() {

    // Use InputFiles rather than InputFile to allow the file not to exist
    @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract val profileSource: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    override fun doTaskAction() {
        val sourceFile = profileSource.get().asFile
        if (sourceFile.isFile) {
            // verify the human readable profile is valid so we error early if necessary
            HumanReadableProfile(sourceFile) {
                throw RuntimeException("Error while parsing ${sourceFile.absolutePath} : $it")
            }
            // all good, copy to target area.
            sourceFile.copyTo(outputFile.get().asFile, true)
        }
    }

    class CreationAction(
            creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ProcessLibraryArtProfileTask, ComponentCreationConfig>(
            creationConfig) {

        override val name: String
            get() = creationConfig.computeTaskName("prepare", "ArtProfile")
        override val type: Class<ProcessLibraryArtProfileTask>
            get() = ProcessLibraryArtProfileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ProcessLibraryArtProfileTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ProcessLibraryArtProfileTask::outputFile
            ).on(InternalArtifactType.LIBRARY_ART_PROFILE)
        }

        override fun configure(task: ProcessLibraryArtProfileTask) {
            super.configure(task)
            val variantSources = creationConfig.variantSources
            task.profileSource
                .fileProvider(creationConfig.services.provider(variantSources::artProfile))
            task.profileSource.disallowChanges()
        }
    }
}
