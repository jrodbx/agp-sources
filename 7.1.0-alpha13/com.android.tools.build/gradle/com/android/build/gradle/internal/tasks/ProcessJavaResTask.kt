/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class ProcessJavaResTask : Sync(), VariantAwareTask {

    @get:OutputDirectory
    abstract val outDirectory: DirectoryProperty

    // override to remove the @OutputDirectory annotation
    @Internal
    override fun getDestinationDir(): File {
        return outDirectory.get().asFile
    }

    @get:Internal
    override lateinit var variantName: String

    /** Configuration Action for a process*JavaRes tasks.  */
    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ProcessJavaResTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("process", "JavaRes")

        override val type: Class<ProcessJavaResTask>
            get() = ProcessJavaResTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessJavaResTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processJavaResourcesTask = taskProvider

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    ProcessJavaResTask::outDirectory
                ).withName("out").on(InternalArtifactType.JAVA_RES)
        }

        override fun configure(
            task: ProcessJavaResTask
        ) {
            super.configure(task)

            for (sourceProvider in creationConfig.variantSources.sortedSourceProviders) {
                task.from((sourceProvider as AndroidSourceSet).resources.getSourceFiles())
            }
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}
