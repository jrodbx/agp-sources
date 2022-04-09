/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * Task that writes the SigningConfig information to a file, excluding the information about which
 * signature versions are enabled, which is handled by [SigningConfigVersionsWriterTask].
 *
 * Caching disabled by default for this task because the task does very little work.
 * Input values are written to a minimal JSON file and no computation is required.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class SigningConfigWriterTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val validatedSigningOutput: DirectoryProperty

    @get:Nested
    @get:Optional
    abstract val signingConfigData: Property<SigningConfigData?>

    // Add the store file path as an input as SigningConfigData ignores it (see its javadoc). This
    // will break cache relocatability, but we have to accept it for correctness (see bug
    // 135509623#comment6).
    @get:Input
    @get:Optional
    abstract val storeFilePath: Property<String?>

    public override fun doTaskAction() {
        SigningConfigUtils.saveSigningConfigData(outputFile.get().asFile, signingConfigData.orNull)
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<SigningConfigWriterTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("signingConfigWriter")

        override val type: Class<SigningConfigWriterTask>
            get() = SigningConfigWriterTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<SigningConfigWriterTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    SigningConfigWriterTask::outputFile
                ).withName("signing-config-data.json")
                .on(InternalArtifactType.SIGNING_CONFIG_DATA)
        }

        override fun configure(
            task: SigningConfigWriterTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.VALIDATE_SIGNING_CONFIG,
                task.validatedSigningOutput
            )

            // wrap the next two task input in provider as SigningConfigData constructor resolves
            // providers during construction.
            task.signingConfigData.setDisallowChanges(
                creationConfig.services.provider {
                    val signingConfig = creationConfig.signingConfigImpl
                    if (signingConfig != null && !signingConfig.name.isNullOrEmpty()) {
                        SigningConfigData.fromSigningConfig(signingConfig)
                    } else null
                }
            )
            task.storeFilePath.setDisallowChanges(
                creationConfig.services.provider<String?> {
                    val signingConfig = creationConfig.signingConfigImpl
                    if (signingConfig != null && signingConfig.storeFile.isPresent) {
                        signingConfig.storeFile.get()?.path
                    } else null
                }
            )
        }
    }
}
