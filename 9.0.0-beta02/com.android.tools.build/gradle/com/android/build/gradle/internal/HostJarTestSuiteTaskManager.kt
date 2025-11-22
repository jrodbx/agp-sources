/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.variant.impl.FlatSourceDirectoriesImpl
import com.android.build.api.variant.impl.TestSuiteSourceContainer
import com.android.build.gradle.internal.api.TestSuiteSourceSet
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.ProcessJavaResTask
import com.android.build.gradle.internal.tasks.creationconfig.ProcessJavaResCreationConfig
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

/**
 * Task manager responsible for creating all tasks necessary to process a
 * [TestSuiteSourceSet.HostJar] source set.
 */
class HostJarTestSuiteTaskManager {

    /**
     * Creates all necessary tasks to process a [TestSuiteSourceSet.HostJar] type of source set.
     *
     * @return the final [TaskProvider] that can be used as a dependent of the
     * [com.android.build.gradle.tasks.TestSuiteTestTask].
     */
    fun createTasks(
        sourceContainer: TestSuiteSourceContainer,
        source: TestSuiteSourceSet.HostJar,
        taskFactory: TaskFactory,
        taskCreationServices: TaskCreationServices
    ): TaskProvider<out Task> {

        // first process java resources.
        val config = object: ProcessJavaResCreationConfig {
            override val extraClasses: Collection<FileCollection>
                get() = listOf()
            override val useBuiltInKotlinSupport: Boolean
                get() = false // so far, since we don't compile yet.
            override val packageJacocoRuntime: Boolean
                get() = false
            override val annotationProcessorConfiguration: Configuration?
                get() = null
            override val sources: FlatSourceDirectoriesImpl
                get() = source.resources()

            override fun setJavaResTask(task: TaskProvider<out Sync>) {}

            override val name: String
                get() = sourceContainer.identifier
            override val services: TaskCreationServices
                get() = taskCreationServices
            override val taskContainer: MutableTaskContainer
                get() = throw RuntimeException("Test Suites should not access the deprecated `taskContainer`")
            override val artifacts: ArtifactsImpl
                get() = sourceContainer.artifacts
        }

        return taskFactory.register(ProcessJavaResTask.CreationAction(config))
    }
}
