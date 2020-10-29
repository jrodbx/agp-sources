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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.dexing.isProguardRule
import org.gradle.api.tasks.TaskProvider

class MergeGeneratedProguardFilesCreationAction(variantScope: VariantScope)
    : VariantTaskCreationAction<MergeFileTask>(variantScope) {

    override val name: String
        get() = variantScope.getTaskName("merge", "GeneratedProguardFiles")
    override val type: Class<MergeFileTask>
        get() = MergeFileTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<out MergeFileTask>) {
        super.handleProvider(taskProvider)
        variantScope.artifacts.producesFile(
            InternalArtifactType.GENERATED_PROGUARD_FILE,
            taskProvider,
            MergeFileTask::outputFile,
            SdkConstants.FN_PROGUARD_TXT
        )
    }

    override fun configure(task: MergeFileTask) {
        super.configure(task)

        val allClasses = variantScope.artifacts.getAllClasses()
        val proguardFiles = allClasses.asFileTree.filter { f ->
            val baseFolders = allClasses.files
            val baseFolder = baseFolders.first { f.startsWith(it) }
            isProguardRule(f.relativeTo(baseFolder).invariantSeparatorsPath)
        }

        task.inputFiles = proguardFiles
    }
}