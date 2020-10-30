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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import java.io.File
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

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
    class CreationAction(scope: VariantScope) :
        VariantTaskCreationAction<ProcessJavaResTask>(scope) {

        override val name: String
            get() = variantScope.getTaskName("process", "JavaRes")

        override val type: Class<ProcessJavaResTask>
            get() = ProcessJavaResTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out ProcessJavaResTask>
        ) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processJavaResourcesTask = taskProvider
            variantScope
                .artifacts
                .producesDir(
                    InternalArtifactType.JAVA_RES,
                    taskProvider,
                    ProcessJavaResTask::outDirectory
                )
        }

        override fun configure(task: ProcessJavaResTask) {
            super.configure(task)

            for (sourceProvider in variantScope.variantSources.sortedSourceProviders) {
                task.from((sourceProvider as AndroidSourceSet).resources.sourceFiles)
            }
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}
