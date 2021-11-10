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
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
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

/** Lazy Creation Action for non variant aware tasks
 *
 * This contains both meta-data to create the task ([name], [type])
 * and actions to configure the task ([preConfigure], [configure], [handleProvider])
 */
abstract class TaskCreationAction<TaskT : Task> : TaskInformation<TaskT>, PreConfigAction,
    TaskConfigAction<TaskT>, TaskProviderCallback<TaskT> {

    override fun preConfigure(taskName: String) {
        // default does nothing
    }

    override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
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
abstract class VariantTaskCreationAction<TaskT, CreationConfigT: ComponentCreationConfig>(
    @JvmField protected val creationConfig: CreationConfigT,
    private val dependsOnPreBuildTask: Boolean
) : TaskCreationAction<TaskT>() where TaskT: Task, TaskT: VariantAwareTask {

    constructor(
        creationConfig: CreationConfigT
    ): this(creationConfig, true)

    @JvmOverloads
    protected fun computeTaskName(prefix: String, suffix: String = ""): String =
        creationConfig.computeTaskName(prefix, suffix)

    override fun preConfigure(taskName: String) {
        // default does nothing
    }
    override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
        // default does nothing
    }

    override fun configure(task: TaskT) {
        if (dependsOnPreBuildTask) {
            val taskContainer: MutableTaskContainer = creationConfig.taskContainer
            task.dependsOn(taskContainer.preBuildTask)
        }
        task.configureVariantProperties(creationConfig.name, task.project)
    }
}

/**
 * Lazy Creation Action for non variant aware tasks.
 *
 * Tasks must implement [BaseTask]
 *
 * This contains both meta-data to create the task ([name], [type])
 * and actions to configure the task ([preConfigure], [configure], [handleProvider])
 */
abstract class GlobalTaskCreationAction<TaskT>(
    @JvmField protected val globalScope: GlobalScope
) : TaskCreationAction<TaskT>() where TaskT: Task, TaskT: BaseTask {

    override fun preConfigure(taskName: String) {
        // default does nothing
    }
    override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
        // default does nothing
    }

    override fun configure(task: TaskT) {
        task.analyticsService.set(getBuildService(task.project.gradle.sharedServices))
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
