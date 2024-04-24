/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.coverage

import com.android.build.gradle.internal.caching.DisabledCachingReason.FAST_TASK
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.utils.FileUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * Writes the java resource file for jacoco to work out of the box.
 *
 * See https://issuetracker.google.com/151471144 for context
 */
@DisableCachingByDefault(because = FAST_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class JacocoPropertiesTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val propertiesDir: DirectoryProperty

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(WriteJacocoPropertiesFile::class.java) {
            it.propertiesDir.setDisallowChanges(propertiesDir)
        }
    }

    abstract class WriteJacocoPropertiesFile : WorkAction<WriteJacocoPropertiesFile.Parameters> {

        interface Parameters : WorkParameters {
            val propertiesDir: DirectoryProperty
        }

        override fun execute() {
            FileUtils.writeToFile(
                FileUtils.join(
                    parameters.propertiesDir.asFile.get(), "jacoco-agent.properties"
                ),
                "#Injected by the Android Gradle Plugin\noutput=none\n"
            )
        }
    }

    class CreationAction(creationConfig: ComponentCreationConfig) : VariantTaskCreationAction<JacocoPropertiesTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String =
            creationConfig.computeTaskName("generate", "JacocoPropertiesFile")
        override val type: Class<JacocoPropertiesTask> get() = JacocoPropertiesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<JacocoPropertiesTask>) {
            creationConfig.artifacts
                .setInitialProvider(taskProvider, JacocoPropertiesTask::propertiesDir)
                .on(InternalArtifactType.JACOCO_CONFIG_RESOURCES)
        }
    }
}
