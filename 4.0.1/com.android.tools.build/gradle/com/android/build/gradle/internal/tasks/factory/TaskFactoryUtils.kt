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

@file:JvmName("TaskFactoryUtils")
package com.android.build.gradle.internal.tasks.factory

import org.gradle.api.Action
import org.gradle.api.Buildable
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * Extension function for [TaskContainer] to add a way to create a task with our
 * [VariantTaskCreationAction] without having a [TaskFactory]
 */
fun <T : Task> TaskContainer.registerTask(
    creationAction: TaskCreationAction<T>,
    secondaryPreConfigAction: PreConfigAction? = null,
    secondaryAction: TaskConfigAction<in T>? = null,
    secondaryProviderCallback: TaskProviderCallback<T>? = null
): TaskProvider<T> {
    val actionWrapper = TaskAction(creationAction, secondaryPreConfigAction, secondaryAction, secondaryProviderCallback)
    return this.register(creationAction.name, creationAction.type, actionWrapper)
        .also { provider ->
            actionWrapper.postRegisterHook(provider)
        }
}

/**
 * Extension function for [TaskContainer] to add a way to create a task with our
 * [PreConfigAction] and [TaskConfigAction] without having a [TaskFactory]
 */
fun <T : Task> TaskContainer.registerTask(
    taskName: String,
    taskType: Class<T>,
    preConfigAction: PreConfigAction? = null,
    action: TaskConfigAction<in T>? = null,
    providerCallback: TaskProviderCallback<T>? = null
): TaskProvider<T> {
    val actionWrapper = TaskAction2(preConfigAction, action, providerCallback)
    return this.register(taskName, taskType, actionWrapper)
        .also { provider ->
            actionWrapper.postRegisterHook(provider)
        }
}

/**
 * Wrapper for the [VariantTaskCreationAction] as a simple [Action] that is passed
 * to [TaskContainer.register].
 *
 * If the task is configured during the register then [VariantTaskCreationAction.preConfigure] is called
 * right away.
 *
 * After register, if it has not been called then it is called,
 * alongside [VariantTaskCreationAction.handleProvider]
 */
private class TaskAction<T: Task>(
    val creationAction: TaskCreationAction<T>,
    val secondaryPreConfigAction: PreConfigAction? = null,
    val secondaryAction: TaskConfigAction<in T>? = null,
    val secondaryProviderCallback: TaskProviderCallback<T>? = null
) : Action<T> {

    var hasRunPreConfig = false

    override fun execute(task: T) {
        doPreConfig(task.name)

        creationAction.configure(task)
        secondaryAction?.configure(task)
    }

    fun postRegisterHook(taskProvider: TaskProvider<out T>) {
        doPreConfig(taskProvider.name)

        creationAction.handleProvider(taskProvider)
        secondaryProviderCallback?.handleProvider(taskProvider)
    }

    private fun doPreConfig(taskName: String) {
        if (!hasRunPreConfig) {
            creationAction.preConfigure(taskName)
            secondaryPreConfigAction?.preConfigure(taskName)
            hasRunPreConfig = true
        }
    }

}

/**
 * Wrapper for separate [TaskConfigAction], [PreConfigAction], and [TaskProviderCallback] as a
 * simple [Action] that is passed to [TaskContainer.register].
 *
 * If the task is configured during the register then [PreConfigAction.preConfigure] is called
 * right away.
 *
 * After register, if it has not been called then it is called,
 * alongside [TaskProviderCallback.handleProvider]
 */
private class TaskAction2<T: Task>(
    val preConfigAction: PreConfigAction? = null,
    val action: TaskConfigAction<in T>? = null,
    val taskProviderCallback: TaskProviderCallback<T>? = null
) : Action<T> {

    var hasRunPreConfig = false

    override fun execute(task: T) {
        doPreConfig(task.name)

        action?.configure(task)
    }

    fun postRegisterHook(taskProvider: TaskProvider<out T>) {
        doPreConfig(taskProvider.name)

        taskProviderCallback?.handleProvider(taskProvider)
    }

    private fun doPreConfig(taskName: String) {
        if (!hasRunPreConfig) {
            preConfigAction?.preConfigure(taskName)
            hasRunPreConfig = true
        }
    }
}

/**
 * Sets dependency between 2 [TaskProvider], as an extension method on [TaskProvider].
 *
 * This handles if either of the 2 providers are null or not present.
 */
fun <T: Task> TaskProvider<out T>?.dependsOn(task: TaskProvider<out T>?): TaskProvider<out T>? {
    this?.letIfPresent { nonNullThis ->
        task?.letIfPresent { nonNullTask ->
            nonNullThis.configure { it.dependsOn(nonNullTask.get()) }
        }
    }

    return this
}

/**
 * Sets dependency between a [TaskProvider] and a task name, as an extension method on [TaskProvider].
 *
 * This handles if the provider is null or not present.
 */
fun <T: Task> TaskProvider<out T>?.dependsOn(taskName: String): TaskProvider<out T>? {
    this?.letIfPresent { nonNullThis ->
        nonNullThis.configure { it.dependsOn(taskName) }
    }

    return this
}

/**
 * Sets dependency between a [TaskProvider] and a [Buildable], as an extension method on [TaskProvider].
 *
 * This handles if the provider is null or not present.
 */
fun <T: Task> TaskProvider<out T>?.dependsOn(buildable: Buildable): TaskProvider<out T>? {
    this?.letIfPresent { nonNullThis ->
        nonNullThis.configure { it.dependsOn(buildable) }
    }

    return this
}

/**
 * Sets dependency between a [TaskProvider] and a [Task], as an extension method on [TaskProvider].
 *
 * This handles if the provider or the task are null or not present.
 *
 * @deprecated This is meant to be replaced with the version using 2 [TaskProvider] as [Task]
 * get replaced with [TaskProvider]
 */
@Deprecated("Use TaskProvider.dependsOn(TaskProvider)")
fun <T: Task> TaskProvider<out T>?.dependsOn(task: Task?): TaskProvider<out T>? {
    this?.letIfPresent { nonNullThis ->
        task?.let { nonNullTask ->
            nonNullThis.configure { it.dependsOn(nonNullTask) }
        }
    }

    return this
}

/**
 * Sets dependency between a [Task] and a [TaskProvider], as an extension method on [Task].
 *
 * This handles if the provider or the task are null or not present.
 *
 * @deprecated This is meant to be replaced with the version using 2 [TaskProvider] as [Task]
 * get replaced with [TaskProvider]
 */
@Deprecated("Use TaskProvider.dependsOn(TaskProvider)")
fun <T: Task> Task?.dependsOn(task: TaskProvider<out T>?): Task? {
    this?.let { nonNullThis ->
        task?.letIfPresent { nonNullTask ->
            nonNullThis.dependsOn(nonNullTask.get())
        }
    }

    return this
}

inline fun <T: Task> TaskProvider<out T>?.letIfPresent(block: (TaskProvider<out T>) -> Unit) {
    if (this != null && isPresent) {
        block(this)
    }
}

fun <T: Task> TaskProvider<out T>.dependsOn(tasks: Collection<TaskProvider<out Task>>): TaskProvider<out T> {
    if (tasks.isEmpty().not()) {
        configure { it.dependsOn(tasks) }
    }

    return this
}

fun <T: Task> TaskProvider<out T>.dependsOn(vararg tasks: TaskProvider<out Task>): TaskProvider<out T> {
    if (tasks.isEmpty().not()) {
        configure { it.dependsOn(*tasks) }
    }

    return this
}

@Deprecated("Use TaskProvider.dependsOn(Collection<TaskProvider>)")
fun <T: Task> TaskProvider<out T>.dependsOn(vararg tasks: Task): TaskProvider<out T> {
    if (tasks.isEmpty().not()) {
        configure { it.dependsOn(*tasks) }
    }

    return this
}
