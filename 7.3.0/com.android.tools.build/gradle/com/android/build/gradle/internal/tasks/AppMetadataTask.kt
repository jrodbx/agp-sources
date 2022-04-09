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

import com.android.SdkConstants.ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY
import com.android.SdkConstants.ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.APP_METADATA_VERSION_PROPERTY
import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.internal.packaging.IncrementalPackager.APP_METADATA_FILE_NAME
import com.android.utils.FileUtils
import com.google.common.io.Files
import java.io.File
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * A task that writes the app metadata
 *
 * Caching disabled by default for this task because the task does very little work.
 * Input values are written to a minimal Properties file and no computation is required.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
abstract class AppMetadataTask : NonIncrementalTask() {

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:Input abstract val appMetadataVersion: Property<String>

    @get:Input abstract val agpVersion: Property<String>

    @get:Input @get:Optional abstract val agdeVersion: Property<String>

    override fun doTaskAction() {
        val appMetadataFile = outputFile.get().asFile
        FileUtils.deleteIfExists(appMetadataFile)
        Files.createParentDirs(appMetadataFile)
        writeAppMetadataFile(
            appMetadataFile,
            appMetadataVersion.get(),
            agpVersion.get(),
            agdeVersion.orNull,
        )
    }

    private fun configureTaskInputs(projectOptions: ProjectOptions) {
        appMetadataVersion.setDisallowChanges(APP_METADATA_VERSION)
        agpVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        agdeVersion.setDisallowChanges(projectOptions.getProvider(StringOption.IDE_AGDE_VERSION))
    }

    class CreationAction(creationConfig: ApplicationCreationConfig) :
        VariantTaskCreationAction<AppMetadataTask, ApplicationCreationConfig>(creationConfig) {
        override val type = AppMetadataTask::class.java
        override val name = computeTaskName("write", "AppMetadata")

        override fun handleProvider(taskProvider: TaskProvider<AppMetadataTask>) {
            super.handleProvider(taskProvider)
            taskProvider.configureTaskOutputs(creationConfig.artifacts)
        }

        override fun configure(task: AppMetadataTask) {
            super.configure(task)
            task.configureTaskInputs(creationConfig.services.projectOptions)
        }
    }

    // CreationAction for use in AssetPackBundlePlugin
    class CreationForAssetPackBundleAction(
        private val artifacts: ArtifactsImpl,
        private val projectOptions: ProjectOptions,
    ) : TaskCreationAction<AppMetadataTask>() {
        override val type = AppMetadataTask::class.java
        override val name = "writeAppMetadata"

        override fun handleProvider(taskProvider: TaskProvider<AppMetadataTask>) {
            super.handleProvider(taskProvider)
            taskProvider.configureTaskOutputs(artifacts)
        }

        override fun configure(task: AppMetadataTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)
            task.configureTaskInputs(projectOptions)
        }
    }

    companion object {
        const val APP_METADATA_VERSION = "1.1"
    }
}

/** Writes an app metadata file with the given parameters */
private fun writeAppMetadataFile(
    file: File,
    appMetadataVersion: String,
    agpVersion: String,
    agdeVersion: String?,
) {
    // We write the file manually instead of using the java.util.Properties API because (1) that API
    // doesn't guarantee the order of properties in the file and (2) that API writes an unnecessary
    // timestamp in the file.
    file.bufferedWriter().use { writer ->
        writer.appendLine("$APP_METADATA_VERSION_PROPERTY=$appMetadataVersion")
        writer.appendLine("$ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=$agpVersion")
        if (agdeVersion != null) {
            writer.appendLine("$ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY=$agdeVersion")
        }
    }
}

private fun TaskProvider<AppMetadataTask>.configureTaskOutputs(artifacts: ArtifactsImpl) {
    artifacts
        .setInitialProvider(this, AppMetadataTask::outputFile)
        .withName(APP_METADATA_FILE_NAME)
        .on(InternalArtifactType.APP_METADATA)
}
