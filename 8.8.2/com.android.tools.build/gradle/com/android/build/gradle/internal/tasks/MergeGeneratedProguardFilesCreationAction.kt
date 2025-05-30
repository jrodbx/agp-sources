/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.getOrderedFileTree
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskProvider

/**
 * Configuration action for a task to merge generated proguard files.
 * See [MergeFileTask] for Task implementation.
 */
class MergeGeneratedProguardFilesCreationAction(
    creationConfig: ComponentCreationConfig
) : VariantTaskCreationAction<MergeFileTask, ComponentCreationConfig>(
    creationConfig
) {

    override val name: String
            get() = computeTaskName("merge", "GeneratedProguardFiles")
    override val type: Class<MergeFileTask>
        get() = MergeFileTask::class.java

    override fun handleProvider(
        taskProvider: TaskProvider<MergeFileTask>
    ) {
        super.handleProvider(taskProvider)
        creationConfig.artifacts.setInitialProvider(
            taskProvider,
            MergeFileTask::outputFile
        ).withName(SdkConstants.FN_PROGUARD_TXT).on(InternalArtifactType.GENERATED_PROGUARD_FILE)
    }

    override fun configure(
        task: MergeFileTask
    ) {
        super.configure(task)

        val allClasses = creationConfig.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .getFinalArtifacts(ScopedArtifact.CLASSES)

        task.inputFiles.fromDisallowChanges(
            allClasses.getDirectories(creationConfig.services.projectInfo.projectDirectory).map {
                it.mapNotNull { directory ->
                    getSubFolder(
                        directory,
                        SdkConstants.META_INF,
                        SdkConstants.PROGUARD_RULES_FOLDER_NAME
                    )?.getOrderedFileTree()
                }
            }
        )
    }

    companion object {
        internal fun getSubFolder(directory: Directory, vararg paths: String): Directory? {
            var current = directory
            for (path in paths) {
                val subFolder = current.asFile.listFiles()?.firstOrNull {
                    it.name.lowercase() == path.lowercase()
                }
                if (subFolder != null) {
                    current = current.dir(subFolder.name)
                } else
                    return null
            }
            return current
        }
    }
}
