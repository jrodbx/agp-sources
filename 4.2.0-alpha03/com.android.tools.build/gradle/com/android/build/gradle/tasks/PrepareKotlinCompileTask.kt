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

package com.android.build.gradle.tasks

import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * Task to prepare the KotlinCompile Task, provided by the kotlin-android plugin, so it works
 * correctly with Jetpack Compose.
 */
abstract class PrepareKotlinCompileTask() : NonIncrementalTask() {

    // No outputs -- this task must always run in order to properly prepare the KotlinCompile Task

    @get:Internal
    abstract val taskNameToConfigure: Property<String>

    // Input: Configuration to the kotlin compiler extension.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val kotlinCompilerExtension: ConfigurableFileCollection

    override fun doTaskAction() {
        val task = project.tasks.getByName(taskNameToConfigure.get()) as KotlinCompile
        task.kotlinOptions.useIR = true
        task.kotlinOptions.freeCompilerArgs +=
            listOf(
                "-Xplugin=${kotlinCompilerExtension.files.first().absolutePath}",
                "-XXLanguage:+NonParenthesizedAnnotationsOnFunctionalTypes",
                "-P",
                "plugin:androidx.compose.plugins.idea:enabled=true"
            )
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl,
        private val taskToConfigure: TaskProvider<Task>,
        private val kotlinExtension: Configuration
    ) : VariantTaskCreationAction<PrepareKotlinCompileTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String = computeTaskName("prepare", "KotlinCompileTask")
        override val type: Class<PrepareKotlinCompileTask> = PrepareKotlinCompileTask::class.java

        override fun configure(
            task: PrepareKotlinCompileTask
        ) {
            super.configure(task)

            task.taskNameToConfigure.set(taskToConfigure.name)
            task.kotlinCompilerExtension.from(kotlinExtension)
        }
    }


}