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
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.FileUtils
import com.google.common.io.Files
import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/**
 * A task that writes the lint model metadata
 *
 * Caching disabled by default for this task because the task does very little work.
 * Input values are written to a minimal Properties file and no computation is required.
 * Calculating cache hit/miss and fetching results is likely more expensive than
 * simply executing the task.
 */
@DisableCachingByDefault
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
        creationConfig: ConsumableCreationConfig
    ) : VariantTaskCreationAction<LintModelMetadataTask, ConsumableCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("write", "LintModelMetadata")

        override val type: Class<LintModelMetadataTask>
            get() = LintModelMetadataTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelMetadataTask>) {
            super.handleProvider(taskProvider)
            registerOutputArtifacts(taskProvider, creationConfig.artifacts)
        }

        override fun configure(task: LintModelMetadataTask) {
            super.configure(task)

            val projectInfo = creationConfig.services.projectInfo
            task.mavenGroupId.setDisallowChanges(projectInfo.group)
            task.mavenArtifactId.setDisallowChanges(projectInfo.name)
        }
    }

    internal fun configureForStandalone(project: Project) {
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        this.mavenGroupId.setDisallowChanges(project.group.toString())
        this.mavenArtifactId.setDisallowChanges(project.name)
    }

    companion object {
        fun registerOutputArtifacts(
            taskProvider: TaskProvider<LintModelMetadataTask>,
            artifacts: ArtifactsImpl
        ) {
            artifacts.setInitialProvider(taskProvider, LintModelMetadataTask::outputFile)
                .withName("lint-model-metadata.properties")
                .on(InternalArtifactType.LINT_MODEL_METADATA)
        }
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
