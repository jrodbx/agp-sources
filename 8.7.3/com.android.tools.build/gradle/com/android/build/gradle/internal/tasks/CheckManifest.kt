/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Class that checks the presence of the manifest file, if it is required to exist.
 *
 * REMOVE ME (bug 139855995): This task can be removed when the new variant API is ready, we haven't
 * removed it yet for compatibility reasons.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION, secondaryTaskCategories = [TaskCategory.MANIFEST])
abstract class CheckManifest : NonIncrementalTask() {

    /** Whether the manifest file is required to exist. */
    private var manifestRequired: Boolean = false

    /** The path to the manifest file. */
    private lateinit var manifestFile: Provider<File>

    /** A fake output directory, used for task dependencies and UP-TO-DATE purposes. */
    @get:OutputDirectory
    abstract val fakeOutputDir: DirectoryProperty

    @Input
    fun isManifestRequiredButNotPresent() = manifestRequired && !manifestFile.get().isFile

    override fun doTaskAction() {
        if (isManifestRequiredButNotPresent()) {
            error(
                "Main manifest is missing for variant $variantName." +
                        " Expected path: ${manifestFile.get().absolutePath}"
            )
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<CheckManifest, ComponentCreationConfig>(
            creationConfig
    ) {

        override val name: String
            get() = computeTaskName("check", "Manifest")

        override val type: Class<CheckManifest>
            get() = CheckManifest::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<CheckManifest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.checkManifestTask = taskProvider

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CheckManifest::fakeOutputDir
            ).withName("out").on(InternalArtifactType.CHECK_MANIFEST_RESULT)
        }

        override fun configure(
            task: CheckManifest
        ) {
            super.configure(task)

            task.manifestRequired = creationConfig.componentType.requiresManifest
            task.manifestFile = creationConfig.sources.manifestFile
        }
    }
}
