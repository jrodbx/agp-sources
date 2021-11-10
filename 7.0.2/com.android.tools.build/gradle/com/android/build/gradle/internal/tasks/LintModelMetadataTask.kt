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

import com.android.SdkConstants.MAVEN_ARTIFACT_ID_PROPERTY
import com.android.SdkConstants.MAVEN_GROUP_ID_PROPERTY
import com.android.build.gradle.internal.component.AarCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.FileUtils
import com.google.common.io.Files
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** A task that writes the lint model metadata, to be copied into the local aar for lint  */
@CacheableTask
abstract class LintModelMetadataTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val mavenGroupId: Property<String>

    @get:Input
    abstract val mavenArtifactId: Property<String>

    override fun doTaskAction() {
        val lintModelMetadataFile = outputFile.get().asFile
        FileUtils.deleteIfExists(lintModelMetadataFile)
        Files.createParentDirs(lintModelMetadataFile)
        writeLintModelMetadataFile(lintModelMetadataFile, mavenGroupId.get(), mavenArtifactId.get())
    }

    class CreationAction(
        creationConfig: AarCreationConfig
    ) : VariantTaskCreationAction<LintModelMetadataTask, AarCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("write", "LintModelMetadata")

        override val type: Class<LintModelMetadataTask>
            get() = LintModelMetadataTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelMetadataTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, LintModelMetadataTask::outputFile)
                .withName(LINT_MODEL_METADATA_FILE_NAME)
                .on(InternalArtifactType.LINT_MODEL_METADATA)
        }

        override fun configure(task: LintModelMetadataTask) {
            super.configure(task)

            val project = creationConfig.services.projectInfo.getProject()
            task.mavenGroupId.setDisallowChanges(project.group.toString())
            task.mavenArtifactId.setDisallowChanges(project.name)
        }
    }

    companion object {
        const val LINT_MODEL_METADATA_FILE_NAME = "lint-model-metadata.properties"
        const val LINT_MODEL_METADATA_ENTRY_PATH =
            "META-INF/com/android/build/gradle/$LINT_MODEL_METADATA_FILE_NAME"
    }
}

/** Writes a lint model metadata file with the given parameters */
fun writeLintModelMetadataFile(file: File, groupId: String, artifactId: String) {
    // We write the file manually instead of using the java.util.Properties API because (1) that API
    // doesn't guarantee the order of properties in the file and (2) that API writes an unnecessary
    // timestamp in the file.
    file.writeText(
        """
            $MAVEN_ARTIFACT_ID_PROPERTY=$artifactId
            $MAVEN_GROUP_ID_PROPERTY=$groupId
        """.trimIndent()
    )
}
