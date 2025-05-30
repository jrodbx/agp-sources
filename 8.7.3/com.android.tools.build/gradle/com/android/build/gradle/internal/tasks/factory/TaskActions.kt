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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.VariantTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Basic task information for creation
 */
interface TaskInformation<TaskT: Task> {
    /** The name of the task to be created.  */
    val name: String

    /** The class type of the task to created.  */
    val type: Class<TaskT>
}

/** Creation action for a [Task]. */
abstract class TaskCreationAction<TaskT : Task> :
    TaskInformation<TaskT>, PreConfigAction, TaskProviderCallback<TaskT>, TaskConfigAction<TaskT> {

    override fun preConfigure(taskName: String) {
        // default does nothing
    }

    override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
        // default does nothing
    }

    override fun configure(task: TaskT) {
        // default does nothing
    }
}

/** [TaskCreationAction] for a [VariantTask]. */
abstract class VariantTaskCreationAction<TaskT, CreationConfigT : ComponentCreationConfig>
    @JvmOverloads
    constructor(
        @JvmField protected val creationConfig: CreationConfigT,
        private val dependsOnPreBuildTask: Boolean = true
    ) : TaskCreationAction<TaskT>() where TaskT : Task, TaskT : VariantTask {

    protected fun computeTaskName(prefix: String, suffix: String): String =
        creationConfig.computeTaskNameInternal(prefix, suffix)

    protected fun computeTaskName(prefix: String): String =
        creationConfig.computeTaskNameInternal(prefix)

    override fun configure(task: TaskT) {
        super.configure(task)

        if (task is BaseTask) {
            BaseTask.ConfigureAction.configure(task)
        }

        VariantTask.ConfigureAction.configure(task, variantName = creationConfig.name)

        if (dependsOnPreBuildTask) {
            task.dependsOn(creationConfig.taskContainer.preBuildTask)
        }
    }
}

/**
 * [TaskCreationAction] for an [AndroidVariantTask].
 *
 * DISCOURAGED USAGE: The use of this class is not recommended because it does not provide a
 * [variantName] by default. It is typically used when a [ComponentCreationConfig] is not available.
 * If a [ComponentCreationConfig] is available, use [VariantTaskCreationAction] instead. If you
 * still need to use this class, try to provide a [variantName] if possible.
 */
abstract class AndroidVariantTaskCreationAction<TaskT: AndroidVariantTask>(
    private val variantName: String = ""
): BaseTask.CreationAction<TaskT>() {

    override fun configure(task: TaskT) {
        super.configure(task)
        VariantTask.ConfigureAction.configure(task, variantName)
    }
}

/** [TaskCreationAction] for a [GlobalTask]. */
abstract class GlobalTaskCreationAction<TaskT>(
    @JvmField protected val creationConfig: GlobalTaskCreationConfig
) : TaskCreationAction<TaskT>() where TaskT: Task, TaskT: GlobalTask {

    override fun configure(task: TaskT) {
        super.configure(task)

        if (task is BaseTask) {
            BaseTask.ConfigureAction.configure(task)
        }

        GlobalTask.ConfigureAction.configure(task)
    }
}

/**
 * Configuration Action for tasks.
 */
interface TaskConfigAction<TaskT: Task> {

    /** Configures the task. */
    fun configure(task: TaskT)
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
interface TaskProviderCallback<TaskT: Task> {
    fun handleProvider(taskProvider: TaskProvider<TaskT>)
}
