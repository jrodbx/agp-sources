/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.caching.DisabledCachingReason.FAST_TASK
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

/**
 * [org.gradle.api.Task] that produces the IDE listing file that will be passed through the model.
 */
@DisableCachingByDefault(because = FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.SYNC)
abstract class BundleIdeModelProducerTask : NonIncrementalTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val finalBundleFile: RegularFileProperty

    @get:OutputFile
    abstract val bundleIdeModel: RegularFileProperty

    @get:Input
    abstract val applicationId: Property<String>

    override fun doTaskAction() {
        // This task is fast-running, so we should not use a worker as the worker overhead could
        // outweigh its benefit.
        BuiltArtifactsImpl(
                artifactType = SingleArtifact.BUNDLE,
                applicationId = applicationId.get(),
                variantName = variantName,
                elements = listOf(
                        BuiltArtifactImpl.make(outputFile = finalBundleFile.asFile.get().absolutePath))
        ).saveToFile(bundleIdeModel.asFile.get())
    }


    /**
     * CreateAction for a task that will sign the bundle artifact.
     */
    class CreationAction(creationConfig: ApkCreationConfig) :
            VariantTaskCreationAction<BundleIdeModelProducerTask, ApkCreationConfig>(
                    creationConfig
            ) {
        override val name: String
            get() = computeTaskName("produce", "BundleIdeListingFile")

        override val type: Class<BundleIdeModelProducerTask>
            get() = BundleIdeModelProducerTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<BundleIdeModelProducerTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    BundleIdeModelProducerTask::bundleIdeModel
            ).withName(BuiltArtifactsImpl.METADATA_FILE_NAME)
                    .on(InternalArtifactType.BUNDLE_IDE_MODEL)
        }

        override fun configure(
                task: BundleIdeModelProducerTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                    SingleArtifact.BUNDLE,
                    task.finalBundleFile)
            task.applicationId.setDisallowChanges(creationConfig.applicationId)
        }
    }
}
