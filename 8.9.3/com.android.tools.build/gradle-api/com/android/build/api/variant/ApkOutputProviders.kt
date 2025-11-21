/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

@Incubating
interface ApkOutputProviders {

    /**
     * Add Variant's Apk Output for a specific device specification to a Task.
     * The Apk Output includes an ordered collection of batches of Apks to install on a device
     * that matches the device specification available at the configuration time.
     * @param taskProvider the [TaskProvider] returned by Gradle's Task manager when registering the
     * Task of type [TaskT].
     * @param taskInput The method reference the [TaskT] will use to retrieve the current artifact
     * @param deviceSpec the device specification
     */
    @Incubating
    fun <TaskT: Task> provideApkOutputToTask(
        taskProvider: TaskProvider<TaskT>,
        taskInput: (TaskT) -> Property<ApkOutput>,
        deviceSpec: DeviceSpec)
}
