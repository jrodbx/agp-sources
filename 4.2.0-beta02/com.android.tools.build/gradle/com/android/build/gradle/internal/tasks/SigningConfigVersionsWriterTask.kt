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
import com.android.build.gradle.internal.signing.SigningConfigVersions
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.OptionalBooleanOption
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 * Task that writes the [SigningConfigVersions] information to a file.
 */
@CacheableTask
abstract class SigningConfigVersionsWriterTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val enableV1Signing: Property<Boolean>

    @get:Input
    abstract val enableV2Signing: Property<Boolean>

    @get:Input
    abstract val enableV3Signing: Property<Boolean>

    @get:Input
    abstract val enableV4Signing: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val overrideEnableV1Signing: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val overrideEnableV2Signing: Property<Boolean>

    public override fun doTaskAction() {
        SigningConfigUtils.saveSigningConfigVersions(
            outputFile.get().asFile,
            SigningConfigVersions(
                enableV1Signing = overrideEnableV1Signing.orNull ?: enableV1Signing.get(),
                enableV2Signing = overrideEnableV2Signing.orNull ?: enableV2Signing.get(),
                enableV3Signing = enableV3Signing.get(),
                enableV4Signing = enableV4Signing.get()
            )
        )
    }

    class CreationAction(creationConfig: ApkCreationConfig) :
        VariantTaskCreationAction<SigningConfigVersionsWriterTask, ApkCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("write", "signingConfigVersions")

        override val type: Class<SigningConfigVersionsWriterTask>
            get() = SigningConfigVersionsWriterTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<SigningConfigVersionsWriterTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    SigningConfigVersionsWriterTask::outputFile
                ).withName("signing-config-versions.json")
                .on(InternalArtifactType.SIGNING_CONFIG_VERSIONS)
        }

        override fun configure(
            task: SigningConfigVersionsWriterTask
        ) {
            super.configure(task)

            creationConfig.signingConfig?.enableV1Signing?.let { task.enableV1Signing.set(it) }
            task.enableV1Signing.disallowChanges()
            creationConfig.signingConfig?.enableV2Signing?.let { task.enableV2Signing.set(it) }
            task.enableV2Signing.disallowChanges()
            creationConfig.signingConfig?.enableV3Signing?.let { task.enableV3Signing.set(it) }
            task.enableV3Signing.disallowChanges()
            creationConfig.signingConfig?.enableV4Signing?.let { task.enableV4Signing.set(it) }
            task.enableV4Signing.disallowChanges()

            task.overrideEnableV1Signing.setDisallowChanges(
                creationConfig.services.projectOptions.get(OptionalBooleanOption.SIGNING_V1_ENABLED)
            )
            task.overrideEnableV2Signing.setDisallowChanges(
                creationConfig.services.projectOptions.get(OptionalBooleanOption.SIGNING_V2_ENABLED)
            )
        }
    }
}
