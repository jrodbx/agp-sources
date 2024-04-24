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

import com.android.build.gradle.internal.api.BaselineProfiles
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.profgen.HumanReadableProfile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task that processes profiles files for library.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE)
abstract class ProcessLibraryArtProfileTask: MergeFileTask() {

    @get: [InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract val baselineProfileSources: ConfigurableFileCollection

    override fun doTaskAction() {
        if (!baselineProfileSources.isEmpty) {
            val baselineProfiles = baselineProfileSources.files.filter { it.isFile }

            baselineProfiles.forEach { baselineProfile ->
                // verify the human-readable profile is valid so we error early if necessary
                HumanReadableProfile(baselineProfile) {
                    throw RuntimeException(
                        "Error while parsing ${outputFile.get().asFile.absolutePath} : $it")
                }
            }

            mergeFiles(baselineProfiles, outputFile.get().asFile)
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

            // for backwards compat we need to keep reading the old location for baseline profile
            creationConfig.sources.artProfile?.let { artProfile ->
                task.baselineProfileSources.from(artProfile)
            }

            creationConfig.sources.baselineProfiles {
                task.baselineProfileSources.fromDisallowChanges(
                    it.all.map { directories ->
                        directories.map {directory ->
                            directory.file(BaselineProfiles.BaselineProfileFileName)
                        }
                    }
                )
            }
        }
    }
}
