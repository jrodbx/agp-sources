/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.caching.DisabledCachingReason.FAST_TASK
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class ExtractProguardFiles : NonIncrementalGlobalTask() {

    @get:Internal("only for task execution")
    abstract val buildDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val proguardFilesDir: DirectoryProperty

    override fun doTaskAction() {
        for (name in ProguardFiles.KNOWN_FILE_NAMES) {
            val defaultProguardFile = ProguardFiles.getDefaultProguardFile(name, buildDirectory)
            if (!defaultProguardFile.isFile) {
                ProguardFiles.createProguardFile(name, defaultProguardFile)
            }
        }
    }

    class CreationAction(
        creationConfig: GlobalTaskCreationConfig
    ) : GlobalTaskCreationAction<ExtractProguardFiles>(creationConfig) {

        override val name = "extractProguardFiles"
        override val type = ExtractProguardFiles::class.java

        override fun configure(task: ExtractProguardFiles) {
            super.configure(task)
            task.buildDirectory.setDisallowChanges(creationConfig.services.projectInfo.buildDirectory)
        }

        override fun handleProvider(taskProvider: TaskProvider<ExtractProguardFiles>) {
            super.handleProvider(taskProvider)

            creationConfig.globalArtifacts
                .setInitialProvider(taskProvider, ExtractProguardFiles::proguardFilesDir)
                .atLocation (
                    ProguardFiles
                        .getDefaultProguardFileDirectory(creationConfig.services.projectInfo.buildDirectory)
                )
                .on(InternalArtifactType.DEFAULT_PROGUARD_FILES)
        }
    }
}
