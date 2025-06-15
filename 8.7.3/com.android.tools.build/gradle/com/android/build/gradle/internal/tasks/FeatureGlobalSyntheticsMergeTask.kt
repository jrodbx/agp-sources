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

import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getGlobalSyntheticsInput
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
 * Different from [GlobalSyntheticsMergeTask], this task is to combine all intermediate global
 * synthetics into a single internal artifact and publish it from dynamic feature module to the
 * base module.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class FeatureGlobalSyntheticsMergeTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val globalSyntheticsInputs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val mergedGlobalSynthetics: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            FeatureGlobalSyntheticsMergeWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.globalSyntheticsInputs.from(globalSyntheticsInputs)
            it.outputDir.set(mergedGlobalSynthetics)
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val dexingUsingArtifactTransform: Boolean,
        private val separateFileDependenciesTask: Boolean
    ) : VariantTaskCreationAction<FeatureGlobalSyntheticsMergeTask, ApkCreationConfig>(creationConfig) {

        override val name = computeTaskName("featureGlobalSynthetics", "Merge")
        override val type = FeatureGlobalSyntheticsMergeTask::class.java

        override fun configure(task: FeatureGlobalSyntheticsMergeTask) {
            super.configure(task)

            task.globalSyntheticsInputs.from(
                getGlobalSyntheticsInput(
                    creationConfig,
                    DexMergingAction.MERGE_ALL,
                    dexingUsingArtifactTransform,
                    separateFileDependenciesTask
                )
            )
        }

        override fun handleProvider(taskProvider: TaskProvider<FeatureGlobalSyntheticsMergeTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider, FeatureGlobalSyntheticsMergeTask::mergedGlobalSynthetics)
                .withName("mergedRawGlobalSynthetics")
                .on(InternalArtifactType.GLOBAL_SYNTHETICS_MERGED)
        }
    }
}

abstract class FeatureGlobalSyntheticsMergeWorkAction
    : ProfileAwareWorkAction<FeatureGlobalSyntheticsMergeWorkAction.Params> ()
{
    abstract class Params: Parameters() {
        abstract val globalSyntheticsInputs: ConfigurableFileCollection
        abstract val outputDir: DirectoryProperty
    }

    override fun run() {
        val inputFiles = parameters.globalSyntheticsInputs.asFileTree.files
        val outputFolder = parameters.outputDir.get().asFile
        inputFiles.sorted().forEachIndexed { index, file ->
            file.copyTo(outputFolder.resolve("$index"))
        }
    }
}
