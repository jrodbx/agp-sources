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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.api.BaselineProfiles
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.LibraryArtifactType
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getFilteredFiles
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ART_PROFILE, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class MergeArtProfileTask: MergeFileTask() {

    @get:Classpath // The order of `inputFiles` is important
    abstract override val inputFiles: ConfigurableFileCollection

    // Use InputFiles rather than InputFile to allow the file not to exist
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val profileSource: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val profileSourceDirectories: ListProperty<Directory>

    @get:Input
    @get:Optional
    abstract val ignoreFrom: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val ignoreFromAllExternalDependencies: Property<Boolean>

    @get:Internal
    internal lateinit var libraryArtifacts: ArtifactCollection

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(MergeFilesWorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.inputFiles.from(inputFiles)

            it.inputFiles.from(
                profileSourceDirectories.get().map { directory -> directory.asFileTree.files }
                    .flatten()
                    .filter(BaselineProfiles::shouldBeMerged)
            )
            if (profileSource.get().asFile.isFile) {
                it.inputFiles.from(profileSource)
            }

            if (!ignoreFrom.get().isNullOrEmpty() ||
                ignoreFromAllExternalDependencies.get() == true) {
                it.inputFiles.setFrom(getFilteredFiles(
                    ignoreFrom.get(),
                    ignoreFromAllExternalDependencies.get(),
                    libraryArtifacts,
                    it.inputFiles,
                    LoggerWrapper.getLogger(MergeArtProfileTask::class.java),
                    LibraryArtifactType.BASELINE_PROFILES))
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
            mergeFiles(
                parameters.inputFiles.files.filter { it.isFile },
                parameters.outputFile.get().asFile
            )
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
            ).withName(BaselineProfiles.BaselineProfileFileName)
                .on(InternalArtifactType.MERGED_ART_PROFILE)
        }

        override fun configure(task: MergeArtProfileTask) {
            super.configure(task)

            task.libraryArtifacts = creationConfig.variantDependencies.getArtifactCollection(
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                AndroidArtifacts.ArtifactScope.ALL,
                AndroidArtifacts.ArtifactType.ART_PROFILE)

            task.inputFiles.fromDisallowChanges(task.libraryArtifacts.artifactFiles)

            // for backwards compat we need to keep reading the old location for baseline profile
            creationConfig.sources.artProfile?.let { artProfile ->
                task.profileSource.fileProvider(artProfile)
            }

            task.profileSource.disallowChanges()

            creationConfig.sources.baselineProfiles {
                task.profileSourceDirectories.setDisallowChanges(it.all)
            }

            task.ignoreFrom.setDisallowChanges(
                creationConfig.optimizationCreationConfig.ignoreFromInBaselineProfile
            )

            task.ignoreFromAllExternalDependencies.setDisallowChanges(
                creationConfig.optimizationCreationConfig.ignoreFromAllExternalDependenciesInBaselineProfile
            )
        }
    }
}
