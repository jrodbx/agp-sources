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
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * A task merging desugar lib keep rules generated in dynamic feature modules.
 *
 * Caching disabled by default for this task because the task does very little work.
 * Input files are read from disk and concatenated into a single output file.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
abstract class DesugarLibKeepRulesMergeTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keepRulesFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val mergedKeepRules: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            MergeKeepRulesWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.mergedKeepRules.set(mergedKeepRules)
            it.keepRulesFiles.from(keepRulesFiles)
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig,
        private val enableDexingArtifactTransform: Boolean,
        private val separateFileDependenciesDexingTask: Boolean
    ) : VariantTaskCreationAction<DesugarLibKeepRulesMergeTask, ApkCreationConfig>(
        creationConfig
    ) {
        override val name = computeTaskName("desugarLibKeepRulesMerge")
        override val type = DesugarLibKeepRulesMergeTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<DesugarLibKeepRulesMergeTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, DesugarLibKeepRulesMergeTask::mergedKeepRules)
                .withName("mergedDesugarLibKeepRules")
                .on(InternalArtifactType.DESUGAR_LIB_MERGED_KEEP_RULES)
        }

        override fun configure(task: DesugarLibKeepRulesMergeTask) {
            super.configure(task)

            setDesugarLibKeepRules(
                task.keepRulesFiles,
                creationConfig,
                enableDexingArtifactTransform,
                separateFileDependenciesDexingTask
            )
        }
    }
}

abstract class MergeKeepRulesWorkAction
    : ProfileAwareWorkAction<MergeKeepRulesWorkAction.Params>()
{
    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val mergedKeepRules: RegularFileProperty
        abstract val keepRulesFiles: ConfigurableFileCollection
    }

    override fun run() {
        val outputFile = parameters.mergedKeepRules.asFile.get()
        val inputFiles = parameters.keepRulesFiles.asFileTree.files

        inputFiles.forEach{
            outputFile.appendText(it.readText())
        }
    }
}
