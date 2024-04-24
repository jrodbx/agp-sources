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

import com.android.SdkConstants
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE
import com.android.build.gradle.internal.tasks.ExportConsumerProguardFilesTask.Companion.checkProguardFiles
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.features.OptimizationTaskCreationActionImpl
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.errors.EvalIssueException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException

/**
 * Configuration action for a merge-Proguard-files task.
 *
 * @see MergeFileTask
 */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class MergeConsumerProguardFilesTask : MergeFileTask() {

    @get:Input
    var isDynamicFeature = false
        private set

    @get:Input
    var isBaseModule = false
        private set

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val consumerProguardFiles: ConfigurableFileCollection

    @get:Internal("only for task execution")
    abstract val buildDirectory: DirectoryProperty

    @Throws(IOException::class)
    public override fun doTaskAction() {
        val consumerProguardFiles = consumerProguardFiles.files
        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseModule) {
            checkProguardFiles(
                    buildDirectory,
                    isDynamicFeature,
                    consumerProguardFiles
            ) { errorMessage: String? -> throw EvalIssueException(errorMessage!!) }
        }

        consumerProguardFiles.forEach { file: File ->
            if (file.isFile) {
                // do nothing
            } else if (file.isDirectory) {
                logger.warn("Directories as consumer proguard configuration are not supported: ${file.path}")
            } else {
                logger.warn("Supplied consumer proguard configuration does not exist: ${file.path}")
            }
        }

        super.doTaskAction()
    }

    class CreationAction(
            creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<MergeConsumerProguardFilesTask, VariantCreationConfig>(
        creationConfig
    ), OptimizationTaskCreationAction by OptimizationTaskCreationActionImpl(creationConfig) {

        override val name: String
            get() = computeTaskName("merge", "ConsumerProguardFiles")
        override val type: Class<MergeConsumerProguardFilesTask>
            get() = MergeConsumerProguardFilesTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<MergeConsumerProguardFilesTask>) {
            super.handleProvider(taskProvider)
            creationConfig
                    .artifacts
                    .setInitialProvider(taskProvider, MergeConsumerProguardFilesTask::outputFile)
                    .withName(SdkConstants.FN_PROGUARD_TXT)
                    .on(InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE)
        }

        override fun configure(task: MergeConsumerProguardFilesTask) {
            super.configure(task)
            task.isBaseModule = creationConfig.componentType.isBaseModule
            task.isDynamicFeature = creationConfig.componentType.isDynamicFeature
            task.consumerProguardFiles.from(
                optimizationCreationConfig.consumerProguardFiles
            )
            val inputFiles = creationConfig
                    .services
                    .fileCollection(
                            task.consumerProguardFiles,
                            creationConfig
                                    .artifacts
                                    .get(GENERATED_PROGUARD_FILE))
            task.inputFiles.from(inputFiles)
            task.inputFiles.disallowChanges()
            task.buildDirectory.setDisallowChanges(task.project.layout.buildDirectory)
        }
    }
}
