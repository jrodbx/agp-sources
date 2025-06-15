/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.buildanalyzer.common.TaskCategory
import com.android.bundle.RuntimeEnabledSdkConfigProto
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig
import com.android.tools.build.bundletool.splitters.RuntimeEnabledSdkTableInjector
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.File

/* Interm task for generating an asset metadata file required for Play rubidium libraries when an
* application is built with Privacy Sandbox support and support for legacy devices.
*/
@BuildAnalyzer(TaskCategory.APK_PACKAGING)
@CacheableTask
abstract class GenerateRuntimeEnabledSdkTableTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeConfigFile: RegularFileProperty

    @get:OutputFile
    abstract val runtimeEnabledSdkTableFile: RegularFileProperty

    override fun doTaskAction() {
        writeRuntimeEnabledSdkTableFromRuntimeConfig(
                runtimeConfigFile.get().asFile,
                runtimeEnabledSdkTableFile
        )
    }

    private fun writeRuntimeEnabledSdkTableFromRuntimeConfig(
            runtimeConfig: File, runtimeEnabledSdkTableOutput: RegularFileProperty) {
        val privacySandboxRuntimeConfig = runtimeConfig.inputStream().buffered()
                .use { input -> RuntimeEnabledSdkConfig.parseFrom(input) }
        runtimeEnabledSdkTableOutput.get().asFile.writeBytes(
                generateRuntimeEnabledSdkTableBytes(privacySandboxRuntimeConfig.runtimeEnabledSdkList)
        )
    }

    private fun generateRuntimeEnabledSdkTableBytes(
            runtimeEnabledSdkTables: List<RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk>)
            : ByteArray {
        return RuntimeEnabledSdkTableInjector.generateRuntimeEnabledSdkTableBytes(
                runtimeEnabledSdkTables.toImmutableList())
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
            VariantTaskCreationAction<GenerateRuntimeEnabledSdkTableTask, ComponentCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("generate","PrivacySandboxRuntimeEnabledSdkTable")
        override val type: Class<GenerateRuntimeEnabledSdkTableTask>
            get() = GenerateRuntimeEnabledSdkTableTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<GenerateRuntimeEnabledSdkTableTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    GenerateRuntimeEnabledSdkTableTask::runtimeEnabledSdkTableFile
            ).withName(RUNTIME_ENABLED_SDK_TABLE_FILE_NAME)
                    .on(InternalArtifactType.RUNTIME_ENABLED_SDK_TABLE)
        }

        override fun configure(task: GenerateRuntimeEnabledSdkTableTask) {
            super.configure(task)
            task.runtimeConfigFile.setDisallowChanges(
                    creationConfig.artifacts.get(
                            InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE)
            )
        }
    }

    companion object {
        const val RUNTIME_ENABLED_SDK_TABLE_FILE_NAME = "RuntimeEnabledSdkTable.xml";
    }
}
