/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergePackageListsForR8Task: MergeFileTask() {

    @get:Classpath // The order of `inputFiles` is important
    abstract override val inputFiles: ConfigurableFileCollection

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(MergeFilesWorkAction::class.java) {
            it.initializeFromBaseTask(this)
            it.inputFiles.from(inputFiles)
            it.outputFile.set(outputFile)
        }
    }

    abstract class MergeFilesWorkAction: ProfileAwareWorkAction<MergeFilesWorkAction.Parameters>() {
        abstract class Parameters : ProfileAwareWorkAction.Parameters() {
            abstract val inputFiles: ConfigurableFileCollection
            abstract val outputFile: RegularFileProperty
        }

        override fun run() {
            val filesToMerge = parameters.inputFiles.files.filter { it.exists() }
            mergeFiles(
                filesToMerge,
                parameters.outputFile.get().asFile
            )
        }
    }

    class CreationAction(
        creationConfig: ConsumableCreationConfig
    ) : VariantTaskCreationAction<MergePackageListsForR8Task, ConsumableCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = creationConfig.computeTaskNameInternal("merge", "PackageListsForR8")
        override val type: Class<MergePackageListsForR8Task>
            get() = MergePackageListsForR8Task::class.java

        override fun handleProvider(taskProvider: TaskProvider<MergePackageListsForR8Task>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                MergeFileTask::outputFile
            ).on(InternalArtifactType.MERGED_PACKAGES_FOR_R8)
        }

        override fun configure(task: MergePackageListsForR8Task) {
            super.configure(task)

            task.inputFiles.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.PACKAGES_FOR_R8
                ).artifactFiles
            )
        }
    }
}
