/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/** task packaging the rs headers */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.RENDERSCRIPT, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class PackageRenderscriptTask : Sync(), VariantAwareTask {

    @get:OutputDirectory
    abstract val headersDir: DirectoryProperty

    // Override to remove the @OutputDirectory, since it is captured by the above property
    @Suppress("RedundantOverride")
    override fun getDestinationDir(): File {
        return super.getDestinationDir()
    }

    @Internal
    override lateinit var variantName: String

    class CreationAction(creationConfig: VariantCreationConfig) :
        VariantTaskCreationAction<PackageRenderscriptTask, VariantCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("package", "Renderscript")
        override val type: Class<PackageRenderscriptTask>
            get() = PackageRenderscriptTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageRenderscriptTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackageRenderscriptTask::headersDir
            ).withName("out").on(InternalArtifactType.RENDERSCRIPT_HEADERS)
        }

        override fun configure(
            task: PackageRenderscriptTask
        ) {
            super.configure(task)

            creationConfig.sources.renderscript {
                task.from(it.all).include("**/*.rsh")
            }
            task.into(task.headersDir)
        }
    }
}

