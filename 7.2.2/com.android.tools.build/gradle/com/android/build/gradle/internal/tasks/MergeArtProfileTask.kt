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

import com.android.SdkConstants
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
abstract class MergeArtProfileTask: MergeFileTask() {

    @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
    abstract override val inputFiles: ConfigurableFileCollection

    @get:[InputFile PathSensitive(PathSensitivity.RELATIVE)]
    @get:Optional
    abstract val profileSource: RegularFileProperty

    @TaskAction
    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(MergeFilesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.from(inputFiles)
            if (profileSource.isPresent) {
                it.inputFiles.from(profileSource)
            }
            it.outputFile.set(outputFile)
        }
    }

    abstract class MergeFilesWorkAction: ProfileAwareWorkAction<MergeFilesWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputFiles: ConfigurableFileCollection
            abstract val outputFile: RegularFileProperty
        }

        override fun run() {
            mergeFiles(parameters.inputFiles.files, parameters.outputFile.get().asFile)
        }
    }

    class CreationAction(
            creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<MergeArtProfileTask, ApkCreationConfig>(creationConfig) {

        override val name: String
            get() = creationConfig.computeTaskName("merge", "ArtProfile")
        override val type: Class<MergeArtProfileTask>
            get() = MergeArtProfileTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<MergeArtProfileTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    MergeFileTask::outputFile
            ).withName(SdkConstants.FN_ART_PROFILE
            ).on(InternalArtifactType.MERGED_ART_PROFILE)
        }

        override fun configure(task: MergeArtProfileTask) {
            super.configure(task)
            val aarProfilesArtifactCollection = creationConfig
                    .variantDependencies
                    .getArtifactCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ART_PROFILE
                    )
            task.inputFiles.fromDisallowChanges(aarProfilesArtifactCollection.artifactFiles)

            task.profileSource.set(creationConfig.variantSources.artProfileIfExists)
            task.profileSource.disallowChanges()
        }
    }
}
