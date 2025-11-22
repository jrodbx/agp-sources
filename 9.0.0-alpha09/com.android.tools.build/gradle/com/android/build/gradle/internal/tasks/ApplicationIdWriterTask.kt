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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.apache.commons.io.FileUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task responsible for publishing the application Id.
 *
 * This task is currently used to publish the output as a text resource for others to consume.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class ApplicationIdWriterTask : NonIncrementalTask() {
    @get:Input
    @get:Optional
    abstract val applicationId: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    override fun doTaskAction() {
        FileUtils.write(outputFile.get().asFile, applicationId.get())
    }

    internal class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<ApplicationIdWriterTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("write", "ApplicationId")
        override val type: Class<ApplicationIdWriterTask>
            get() = ApplicationIdWriterTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ApplicationIdWriterTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ApplicationIdWriterTask::outputFile
            ).withName("application-id.txt").on(InternalArtifactType.METADATA_APPLICATION_ID)
        }

        override fun configure(
            task: ApplicationIdWriterTask
        ) {
            super.configure(task)

            task.applicationId.setDisallowChanges(creationConfig.applicationId)
        }
    }
}
