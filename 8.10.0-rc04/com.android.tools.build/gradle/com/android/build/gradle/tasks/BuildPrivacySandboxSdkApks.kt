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

package com.android.build.gradle.tasks

import com.android.build.api.variant.impl.BuiltArtifactsImpl.Companion.saveAll
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.options.StringOption
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files

/**
 * Task to fetch extracted privacy sandbox SDK APKs for this app and generate artifact metadata
 * model for IDE.
 */
@DisableCachingByDefault(because="Task only extracts zips")
abstract class BuildPrivacySandboxSdkApks : NonIncrementalTask() {

    @get:Classpath // Classpath as the output is brittle to the order
    abstract val sdkApksArchives: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val deviceConfig: RegularFileProperty

    @get:OutputFile
    abstract val ideModelFile: RegularFileProperty

    override fun doTaskAction() {
        if (sdkApksArchives.isEmpty) {
            logger.log(LogLevel.LIFECYCLE, "There are no privacy sandbox SDK dependencies for ${projectPath.get()} $variantName ")
            return
        }
        val ideModel = ideModelFile.get().asFile.toPath()
        Files.deleteIfExists(ideModel)

        val artifacts = sdkApksArchives.files.mapNotNull { sdkApkDir ->
            BuiltArtifactsLoaderImpl().load { sdkApkDir }
        }.toMutableList()
        artifacts.saveAll(ideModel)
    }

    class CreationAction(creationConfig: ApplicationCreationConfig) : VariantTaskCreationAction<BuildPrivacySandboxSdkApks, ApplicationCreationConfig>(
            creationConfig,
            dependsOnPreBuildTask = false
    ) {

        override val name: String
            get() = getTaskName(creationConfig)

        override val type: Class<BuildPrivacySandboxSdkApks>
            get() = BuildPrivacySandboxSdkApks::class.java

        override fun handleProvider(taskProvider: TaskProvider<BuildPrivacySandboxSdkApks>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    BuildPrivacySandboxSdkApks::ideModelFile
            ).withName("ide_model.json").on(InternalArtifactType.EXTRACTED_APKS_FROM_PRIVACY_SANDBOX_SDKs_IDE_MODEL)
        }

        override fun configure(task: BuildPrivacySandboxSdkApks) {
            super.configure(task)
            task.sdkApksArchives.fromDisallowChanges(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_EXTRACTED_SDK_APKS
                    )
            )
            val deviceConfigPath = creationConfig.services.projectOptions.get(StringOption.IDE_APK_SELECT_CONFIG)
            if (deviceConfigPath != null) {
                task.deviceConfig.set(File(deviceConfigPath))
            }
            task.deviceConfig.disallowChanges()
        }

        companion object {
            fun getTaskName(creationConfig: ApplicationCreationConfig) = creationConfig.computeTaskNameInternal("buildPrivacySandboxSdkApksFor")
        }
    }
}
