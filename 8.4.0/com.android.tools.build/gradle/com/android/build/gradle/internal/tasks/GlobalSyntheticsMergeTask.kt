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
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.getGlobalSyntheticsInput
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.SyncOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.dexing.DexingType
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * To merge global synthetics for native multidex build
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.DEXING, secondaryTaskCategories = [TaskCategory.MERGING])
abstract class GlobalSyntheticsMergeTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val globalSynthetics: ConfigurableFileCollection

    @get:Nested
    abstract val sharedParams: DexMergingTask.SharedParams

    @get:OutputDirectory
    abstract val globalSyntheticsOutput: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(DexMergingTaskDelegate::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.initialize(
                sharedParams = sharedParams,
                numberOfBuckets = 1,
                dexDirsOrJars = emptyList(),
                globalSynthetics = globalSynthetics,
                outputDir = globalSyntheticsOutput,
                incremental = false,
                fileChanges = null,
                mainDexListOutput = null
            )
        }
    }

    class CreationAction constructor(
        creationConfig: ApkCreationConfig,
        private val dexingUsingArtifactTransform: Boolean = true,
        private val separateFileDependenciesTask: Boolean = false
    ) : VariantTaskCreationAction<GlobalSyntheticsMergeTask, ApkCreationConfig>(creationConfig) {

        override val name = computeTaskName("merge", "GlobalSynthetics")
        override val type = GlobalSyntheticsMergeTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GlobalSyntheticsMergeTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GlobalSyntheticsMergeTask::globalSyntheticsOutput
            ).on(InternalArtifactType.GLOBAL_SYNTHETICS_DEX)
        }

        override fun configure(task: GlobalSyntheticsMergeTask) {
            super.configure(task)
            task.sharedParams.apply {
                dexingType.setDisallowChanges(DexingType.NATIVE_MULTIDEX)
                minSdkVersion.setDisallowChanges(
                    creationConfig.dexing.minSdkVersionForDexing)
                debuggable.setDisallowChanges(creationConfig.debuggable)
                errorFormatMode.setDisallowChanges(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
            }

            task.globalSynthetics.from(
                getGlobalSyntheticsInput(
                    creationConfig,
                    DexMergingAction.MERGE_ALL,
                    dexingUsingArtifactTransform,
                    separateFileDependenciesTask
                )
            )

            if (creationConfig.componentType.isBaseModule) {
                task.globalSynthetics.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                        AndroidArtifacts.ArtifactScope.PROJECT,
                        AndroidArtifacts.ArtifactType.GLOBAL_SYNTHETICS_MERGED
                    )
                )
            }
        }
    }
}
