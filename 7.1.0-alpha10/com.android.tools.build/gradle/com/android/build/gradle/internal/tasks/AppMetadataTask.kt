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

import com.android.SdkConstants.ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.APP_METADATA_VERSION_PROPERTY
import com.android.Version
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.internal.packaging.IncrementalPackager.APP_METADATA_FILE_NAME
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** A task that writes the app metadata  */
@CacheableTask
abstract class AppMetadataTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val appMetadataVersion: Property<String>

    @get:Input
    abstract val agpVersion: Property<String>

    override fun doTaskAction() {
        val appMetadataFile = outputFile.get().asFile
        FileUtils.deleteIfExists(appMetadataFile)
        Files.createParentDirs(appMetadataFile)
        writeAppMetadataFile(
            appMetadataFile,
            appMetadataVersion.get(),
            agpVersion.get()
        )
    }

    class CreationAction(
        private val artifacts: ArtifactsImpl,
        private val variantName: String,
        override val name: String = "writeAppMetadata"
    ) : TaskCreationAction<AppMetadataTask>() {

        constructor(creationConfig: ApplicationCreationConfig) : this(
            creationConfig.artifacts,
            creationConfig.name,
            creationConfig.computeTaskName("write", "AppMetadata")
        )

        override val type = AppMetadataTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<AppMetadataTask>) {
            super.handleProvider(taskProvider)
            artifacts
                .setInitialProvider(taskProvider, AppMetadataTask::outputFile)
                .withName(APP_METADATA_FILE_NAME)
                .on(InternalArtifactType.APP_METADATA)
        }

        override fun configure(task: AppMetadataTask) {
            task.configureVariantProperties(variantName, task.project)

            task.appMetadataVersion.setDisallowChanges(APP_METADATA_VERSION)
            task.agpVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        }
    }

    companion object {
        const val APP_METADATA_VERSION = "1.0"
    }
}

/** Writes an app metadata file with the given parameters */
fun writeAppMetadataFile(
    file: File,
    appMetadataVersion: String,
    agpVersion: String
) {
    // We write the file manually instead of using the java.util.Properties API because (1) that API
    // doesn't guarantee the order of properties in the file and (2) that API writes an unnecessary
    // timestamp in the file.
    file.writeText(
        """
            $APP_METADATA_VERSION_PROPERTY=$appMetadataVersion
            $ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY=$agpVersion
        """.trimIndent()
    )
}
