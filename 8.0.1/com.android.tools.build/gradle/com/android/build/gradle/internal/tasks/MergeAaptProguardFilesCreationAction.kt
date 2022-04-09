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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import org.gradle.api.tasks.TaskProvider

/**
 * Configuration action for a task to merge aapt proguard files.
 * See [MergeFileTask] for Task implementation.
 */
class MergeAaptProguardFilesCreationAction(
    creationConfig: ConsumableCreationConfig
) : VariantTaskCreationAction<MergeFileTask, ConsumableCreationConfig>(
    creationConfig
) {

    override val name: String
            get() = computeTaskName("merge", "AaptProguardFiles")
    override val type: Class<MergeFileTask>
        get() = MergeFileTask::class.java

    override fun handleProvider(
        taskProvider: TaskProvider<MergeFileTask>
    ) {
        super.handleProvider(taskProvider)

        creationConfig.artifacts.setInitialProvider(
            taskProvider,
            MergeFileTask::outputFile
        ).withName(SdkConstants.FN_MERGED_AAPT_RULES)
            .on(InternalArtifactType.MERGED_AAPT_PROGUARD_FILE)
    }

    override fun configure(
        task: MergeFileTask
    ) {
        super.configure(task)

        val inputFiles =
            creationConfig
                .services
                .fileCollection(
                    creationConfig.artifacts.get(InternalArtifactType.AAPT_PROGUARD_FILE),
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.AAPT_PROGUARD_RULES
                    )
                )
        task.inputFiles.fromDisallowChanges(inputFiles)
    }
}
