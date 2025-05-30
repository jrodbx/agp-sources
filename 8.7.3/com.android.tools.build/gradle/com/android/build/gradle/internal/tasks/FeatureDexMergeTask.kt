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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * A task merging dex files in dynamic feature modules into a single artifact type.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class FeatureDexMergeTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            FeatureDexMergeWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.dexDirs.from(dexDirs)
            it.outputDir.set(outputDir)
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<FeatureDexMergeTask, ApkCreationConfig>(
        creationConfig
    ) {
        override val name = computeTaskName("featureDexMerge")
        override val type = FeatureDexMergeTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<FeatureDexMergeTask>) {
            super.handleProvider(taskProvider)
            creationConfig
                .artifacts
                .setInitialProvider(taskProvider, FeatureDexMergeTask::outputDir)
                .on(InternalArtifactType.FEATURE_PUBLISHED_DEX)
        }

        override fun configure(task: FeatureDexMergeTask) {
            super.configure(task)
            task.dexDirs.from(creationConfig.artifacts.getAll(InternalMultipleArtifactType.DEX))
            task.outputs.doNotCacheIf(
                "This is a copy paste task, so the cacheability overhead could outweigh its benefit"
            ) { true }
        }
    }
}

abstract class FeatureDexMergeWorkAction
    : ProfileAwareWorkAction<FeatureDexMergeWorkAction.Params>()
{
    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val dexDirs: ConfigurableFileCollection
        abstract val outputDir: DirectoryProperty
    }

    override fun run() {
        val inputFiles = parameters.dexDirs.asFileTree.files
        val outputFolder = parameters.outputDir.get().asFile
        inputFiles.forEachIndexed { index, file ->
            file.copyTo(outputFolder.resolve("$index.dex"))
        }
    }
}
