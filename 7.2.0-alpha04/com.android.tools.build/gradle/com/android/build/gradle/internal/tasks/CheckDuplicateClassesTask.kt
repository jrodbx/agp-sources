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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ENUMERATED_RUNTIME_CLASSES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider

/**
 * A task that checks that project external dependencies do not contain duplicate classes. Without
 * this task in case duplicate classes exist the failure happens during dexing stage and the error
 * is not especially user friendly. Moreover, we would like to fail fast.
 */
@CacheableTask
abstract class CheckDuplicateClassesTask : NonIncrementalTask() {
    @get:OutputDirectory
    abstract val dummyOutputDirectory: DirectoryProperty

    private lateinit var enumeratedClassesArtifacts: ArtifactCollection

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val enumeratedClasses: FileCollection get() = enumeratedClassesArtifacts.artifactFiles


    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(CheckDuplicatesRunnable::class.java) { params ->
            params.enumeratedClasses.set(enumeratedClassesArtifacts.artifacts.map { it.id.displayName to it.file }.toMap())
        }
    }

    class CreationAction(creationConfig: VariantCreationConfig)
        : VariantTaskCreationAction<CheckDuplicateClassesTask, VariantCreationConfig>(
        creationConfig
    ) {

        override val type = CheckDuplicateClassesTask::class.java

        override val name = creationConfig.computeTaskName("check", "DuplicateClasses")

        override fun handleProvider(
            taskProvider: TaskProvider<CheckDuplicateClassesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                CheckDuplicateClassesTask::dummyOutputDirectory
            ).on(InternalArtifactType.DUPLICATE_CLASSES_CHECK)
        }

        override fun configure(
            task: CheckDuplicateClassesTask
        ) {
            super.configure(task)

            task.enumeratedClassesArtifacts = creationConfig.variantDependencies
                    .getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, ENUMERATED_RUNTIME_CLASSES)
        }
    }
}
