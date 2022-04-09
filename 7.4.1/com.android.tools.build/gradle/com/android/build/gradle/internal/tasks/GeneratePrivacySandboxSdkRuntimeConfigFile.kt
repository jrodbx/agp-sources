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

import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.bundle.RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.android.zipflinger.ZipArchive
import com.google.protobuf.TextFormat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

@CacheableTask
abstract class GeneratePrivacySandboxSdkRuntimeConfigFile : NonIncrementalTask() {

    @get:Classpath
    abstract val asars: ConfigurableFileCollection

    @get:OutputFile
    abstract val privacySandboxSdkRuntimeConfigFile: RegularFileProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WorkAction::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.privacySandboxSdkRuntimeConfigFile.set(privacySandboxSdkRuntimeConfigFile)
            it.asars.from(asars)
        }
    }

    abstract class WorkAction: ProfileAwareWorkAction<WorkAction.Parameters>() {

        abstract class Parameters: ProfileAwareWorkAction.Parameters() {
            abstract val asars: ConfigurableFileCollection
            abstract val privacySandboxSdkRuntimeConfigFile: RegularFileProperty
        }

        override fun run() {
            // TODO(b/235469089): Check compatibility with dynamic features
            var resourcesPackage = 0x7e

            val proto = RuntimeEnabledSdkConfig.newBuilder().also { runtimeEnabledSdkConfig ->
                for (asar in parameters.asars) {
                    val sdkMetadata = ZipArchive(asar.toPath()).use { zip ->
                         SdkMetadata.parseFrom(zip.getInputStream("SdkMetadata.pb"))
                    }
                    runtimeEnabledSdkConfig.addRuntimeEnabledSdkBuilder().apply {
                        packageName = sdkMetadata.packageName
                        versionMajor = sdkMetadata.sdkVersion.major
                        versionMinor = sdkMetadata.sdkVersion.minor
                        certificateDigest = sdkMetadata.certificateDigest
                        resourcesPackageId = resourcesPackage
                    }
                    resourcesPackage--
                }
            }.build()

            parameters.privacySandboxSdkRuntimeConfigFile.get().asFile.outputStream().buffered().use {
                proto.writeTo(it)
            }
        }
    }

    class CreationAction(applicationCreationConfig: ApplicationCreationConfig) :
            VariantTaskCreationAction<GeneratePrivacySandboxSdkRuntimeConfigFile, ApplicationCreationConfig>(
                    applicationCreationConfig,
                    dependsOnPreBuildTask = false) {

        override val name: String
            get() = computeTaskName("generate", "PrivacySandboxSdkRuntimeConfigFile")
        override val type: Class<GeneratePrivacySandboxSdkRuntimeConfigFile>
            get() = GeneratePrivacySandboxSdkRuntimeConfigFile::class.java

        override fun handleProvider(taskProvider: TaskProvider<GeneratePrivacySandboxSdkRuntimeConfigFile>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(taskProvider, GeneratePrivacySandboxSdkRuntimeConfigFile::privacySandboxSdkRuntimeConfigFile)
                    .on(InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE)
        }

        override fun configure(task: GeneratePrivacySandboxSdkRuntimeConfigFile) {
            super.configure(task)

            task.asars.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE
                    )
            )
        }
    }
}
