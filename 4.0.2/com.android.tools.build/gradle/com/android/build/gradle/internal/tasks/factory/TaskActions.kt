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

package com.android.build.gradle.internal.tasks.factory

import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Basic task information for creation
 */
interface TaskInformation<T: Task> {
    /** The name of the task to be created.  */
    val name: String

    /** The class type of the task to created.  */
    val type: Class<T>
}

/** Lazy Creation Action for non variant aware tasks
 *
 * This contains both meta-data to create the task ([name], [type])
 * and actions to configure the task ([preConfigure], [configure], [handleProvider])
 */
abstract class TaskCreationAction<T : Task> : TaskInformation<T>, PreConfigAction,
    TaskConfigAction<T>, TaskProviderCallback<T> {

    override fun preConfigure(taskName: String) {
        // default does nothing
    }

    override fun handleProvider(taskProvider: TaskProvider<out T>) {
        // default does nothing
    }
}

/** Lazy Creation Action for variant aware tasks.
 *
 * Tasks must implement [VariantAwareTask]. The simplest way to do this is to extend
 * [AndroidVariantTask].
 *
 * This contains both meta-data to create the task ([name], [type])
 * and actions to configure the task ([preConfigure], [configure], [handleProvider])
 */
abstract class VariantTaskCreationAction<T>(
    protected val variantScope: VariantScope, private val dependsOnPreBuildTask: Boolean
) : TaskCreationAction<T>() where T: Task, T: VariantAwareTask {

    constructor(variantScope: VariantScope): this(variantScope, true)

    override fun configure(task: T) {
        if (dependsOnPreBuildTask) {
            val taskContainer: MutableTaskContainer = variantScope.taskContainer
            task.dependsOn(taskContainer.preBuildTask)
        }

        task.variantName = variantScope.name
        task.enableGradleWorkers.set(
            variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS)
        )
    }
}

/**
 * Configuration Action for tasks.
 */
interface TaskConfigAction<T: Task> {

    /** Configures the task. */
    fun configure(task: T)
}

/**
 * Pre-Configuration Action for lazily created tasks.
 */
interface PreConfigAction {
    /**
     * Pre-configures the task, acting on the taskName.
     *
     * This is meant to handle configuration that must happen always, even when the task
     * is configured lazily.
     *
     * @param taskName the task name
     */
    fun preConfigure(taskName: String)
}

/**
 * Callback for [TaskProvider]
 *
 * Once a TaskProvider is created this is called to process it.
 */
interface TaskProviderCallback<T: Task> {
    fun handleProvider(taskProvider: TaskProvider<out T>)
}
