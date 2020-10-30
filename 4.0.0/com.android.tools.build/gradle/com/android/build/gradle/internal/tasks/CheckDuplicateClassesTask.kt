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

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/**
 * A task that checks that project external dependencies do not contain duplicate classes. Without
 * this task in case duplicate classes exist the failure happens during dexing stage and the error
 * is not especially user friendly. Moreover, we would like to fail fast.
 */
@CacheableTask
abstract class CheckDuplicateClassesTask : NonIncrementalTask() {

    private lateinit var classesArtifacts: ArtifactCollection

    @get:OutputDirectory
    abstract val dummyOutputDirectory: DirectoryProperty

    @Classpath
    fun getClassesFiles(): FileCollection = classesArtifacts.artifactFiles

    override fun doTaskAction() {
        CheckDuplicateClassesDelegate(classesArtifacts).run(
            getWorkerFacadeWithThreads(
                useGradleExecutor = true
            )
        )
    }

    class CreationAction(scope: VariantScope)
        : VariantTaskCreationAction<CheckDuplicateClassesTask>(scope) {

        override val type = CheckDuplicateClassesTask::class.java

        override val name = variantScope.getTaskName("check", "DuplicateClasses")

        override fun handleProvider(taskProvider: TaskProvider<out CheckDuplicateClassesTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesDir(
                InternalArtifactType.DUPLICATE_CLASSES_CHECK,
                taskProvider,
                CheckDuplicateClassesTask::dummyOutputDirectory
            )
        }

        override fun configure(task: CheckDuplicateClassesTask) {
            super.configure(task)

            task.classesArtifacts =
                    variantScope.getArtifactCollection(RUNTIME_CLASSPATH, EXTERNAL, CLASSES_JAR)
        }
    }
}