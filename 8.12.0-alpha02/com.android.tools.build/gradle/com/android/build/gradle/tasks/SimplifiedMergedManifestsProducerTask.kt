/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task is a simple alternative to ProcessMultiApkApplicationManifest.
 * In case we have dynamic feature, MERGED_MANIFEST still needs
 * to be consumed internally in order for external users to be able to transform
 * it using the Variant APIs.
 */
@DisableCachingByDefault(because = DisabledCachingReason.COPY_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class SimplifiedMergedManifestsProducerTask: ManifestProcessorTask() {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputFile
    abstract val mainMergedManifest: RegularFileProperty

    @get:OutputDirectory
    abstract val manifestOutputDirectory: DirectoryProperty

    @get:Input
    abstract val applicationId: Property<String>

    override fun doTaskAction() {
        val outputFile = File(
            manifestOutputDirectory.get().asFile,
            SdkConstants.ANDROID_MANIFEST_XML
        )
        mainMergedManifest.get().asFile.copyTo(outputFile, overwrite = true)

        BuiltArtifactsImpl(
            artifactType = InternalArtifactType.MERGED_MANIFESTS,
            applicationId = applicationId.get(),
            variantName = variantName,
            elements = listOf(
                BuiltArtifactImpl.make(
                    outputFile = outputFile.absolutePath
                )
            )
        ).save(manifestOutputDirectory.get())
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<SimplifiedMergedManifestsProducerTask, ApkCreationConfig>(creationConfig) {
        override val name: String
            get() = computeTaskName("copy", "MergedManifest")
        override val type: Class<SimplifiedMergedManifestsProducerTask>
            get() = SimplifiedMergedManifestsProducerTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<SimplifiedMergedManifestsProducerTask>) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                SimplifiedMergedManifestsProducerTask::manifestOutputDirectory
            ).on(InternalArtifactType.MERGED_MANIFESTS)
        }

        override fun configure(task: SimplifiedMergedManifestsProducerTask) {
            super.configure(task)

            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    SingleArtifact.MERGED_MANIFEST,
                    task.mainMergedManifest
                )

            task.applicationId.setDisallowChanges(creationConfig.applicationId)
        }
    }
}
