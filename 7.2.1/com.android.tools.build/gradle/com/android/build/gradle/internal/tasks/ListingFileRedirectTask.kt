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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.Artifact
import com.android.ide.common.build.ListingFileRedirect
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * [org.gradle.api.Task] to create a redirect file that contains the location of the IDE model
 * file. The location of the redirect file is never changing and cannot be "replaced" by anyone.
 * The location is passed through the model to the IDE which is expecting to always find the
 * redirect file at the same location independently on where tasks will put APK, Bundle, etc...
 *
 * For instance, if any other plugin decide to replace the APKs, the APK_IDE_MODEL will be
 * automatically created by the variant API in the new location. The redirect file will not change
 * and will just point to the new location for the model file.
 *
 * Caching is disabled as the full path to the listing file is used as input. Plus the task
 * execution should be so fast, that it outweighs the benefits in performance.
 */
@DisableCachingByDefault
abstract class ListingFileRedirectTask: NonIncrementalTask() {

    @get:OutputFile
    abstract val redirectFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val listingFile: RegularFileProperty

    @TaskAction
    override fun doTaskAction() {
        ListingFileRedirect.writeRedirect(
            listingFile = listingFile.asFile.get(),
            into = redirectFile.asFile.get()
        )
    }

    internal class CreationAction(
        private val creationConfig: ComponentCreationConfig,
        taskSuffix: String,
        private val inputArtifactType: Artifact.Single<RegularFile>,
        private val outputArtifactType: Artifact.Single<RegularFile>,
    ) : TaskCreationAction<ListingFileRedirectTask>() {

        override val type = ListingFileRedirectTask::class.java
        override val name = creationConfig.computeTaskName(
            prefix = "create",
            suffix = "${taskSuffix}ListingFileRedirect")

        override fun handleProvider(taskProvider: TaskProvider<ListingFileRedirectTask>) {
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ListingFileRedirectTask::redirectFile
            ).withName(ListingFileRedirect.REDIRECT_FILE_NAME).on(outputArtifactType)
        }

        override fun configure(task: ListingFileRedirectTask) {
            task.configureVariantProperties(variantName = "", creationConfig.services.buildServiceRegistry)
            task.listingFile.setDisallowChanges(
                creationConfig.artifacts.get(inputArtifactType)
            )
        }
    }
}
