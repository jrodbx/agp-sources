/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.api.BaselineProfiles
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeStartupProfileTask: MergeFileTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val baselineProfilesSources: ListProperty<RegularFile>

    override fun doTaskAction() {
        val sources = baselineProfilesSources.orNull
        if (sources.isNullOrEmpty()) {
            logger.info(
                "Dex optimization based on startup profile is enabled, " +
                "but there are no source folders.")
        } else {
            val startupProfiles = sources.filter { it.asFile.exists() }.map { it.asFile }
            if (startupProfiles.isEmpty()) {
                logger.info(
                    "Dex optimization based on startup profile is enabled, but there are no " +
                    "input baseline profiles found in the baselineProfiles sources. " +
                    "You should add ${sources.first().asFile.absolutePath}, for instance.")
            } else {
                mergeFiles(startupProfiles, outputFile.get().asFile)
            }
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<MergeStartupProfileTask, ApkCreationConfig>(creationConfig) {

        override val name: String
            get() = creationConfig.computeTaskNameInternal("merge", "StartupProfile")
        override val type: Class<MergeStartupProfileTask>
            get() = MergeStartupProfileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<MergeStartupProfileTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeFileTask::outputFile
            ).withName(BaselineProfiles.StartupProfileFileName)
                .on(InternalArtifactType.MERGED_STARTUP_PROFILE)
        }

        override fun configure(task: MergeStartupProfileTask) {
            super.configure(task)

            creationConfig.sources.baselineProfiles {
                task.baselineProfilesSources.setDisallowChanges(
                    it.all.map { directories ->
                        directories.map { directory ->
                            directory.file(BaselineProfiles.StartupProfileFileName)
                        }
                    }
                )
            }
        }
    }
}
