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

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * Task to prepare the KotlinCompile Task, provided by the kotlin-android plugin, so it works
 * correctly with Jetpack Compose.
 */
abstract class PrepareKotlinCompileTask() : NonIncrementalTask() {

    // No outputs -- this task must always run in order to properly prepare the KotlinCompile Task

    @get:Internal
    lateinit var tasksToConfigure: Iterable<Task>
        private set

    // Input: Configuration to the kotlin compiler extension.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val kotlinCompilerExtension: Property<Configuration>

    override fun doTaskAction() {
        tasksToConfigure.forEach { task ->
            val taskToConfigure = task as KotlinCompile
            taskToConfigure.kotlinOptions.useIR = true
            taskToConfigure.kotlinOptions.freeCompilerArgs +=
                listOf(
                    "-Xplugin=${kotlinCompilerExtension.get().files.first().absolutePath}",
                    "-XXLanguage:+NonParenthesizedAnnotationsOnFunctionalTypes",
                    "-P",
                    "plugin:androidx.compose.plugins.idea:enabled=true"
                )
        }
    }

    class CreationAction(
        variantScope: VariantScope,
        private val tasksToConfigure: Iterable<Task>,
        private val kotlinExtension: Configuration
    ) : VariantTaskCreationAction<PrepareKotlinCompileTask>(variantScope) {

        override val name: String = variantScope.getTaskName("prepare", "KotlinCompileTask")
        override val type: Class<PrepareKotlinCompileTask> = PrepareKotlinCompileTask::class.java

        override fun configure(task: PrepareKotlinCompileTask) {
            super.configure(task)

            task.tasksToConfigure = tasksToConfigure
            task.kotlinCompilerExtension.set(kotlinExtension)
        }
    }


}