/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * Currently, the only function of this task is to invoke Analytics build service to record raw
 * application id, but the functionality can be extended to record other statistics especially for
 * those which should not be computed at configuration time. This task does not use gradle's
 * up-to-date check and always runs for release artifacts.
 */
abstract class AnalyticsRecordingTask :
    UnsafeOutputsTask("AnalyticsRecordingTask always runs to record raw application id for release artifacts") {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val applicationId: RegularFileProperty

    override fun doTaskAction() {
        analyticsService.get().recordApplicationId(applicationId.get().asFile)
    }

    class CreationAction(creationConfig: ApkCreationConfig)
        : VariantTaskCreationAction<AnalyticsRecordingTask, ApkCreationConfig>
        (creationConfig)
    {
        override val name = computeTaskName("analyticsRecording")
        override val type = AnalyticsRecordingTask::class.java

        override fun configure(task: AnalyticsRecordingTask) {
            super.configure(task)
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.METADATA_APPLICATION_ID,
                task.applicationId
            )
        }
    }
}
