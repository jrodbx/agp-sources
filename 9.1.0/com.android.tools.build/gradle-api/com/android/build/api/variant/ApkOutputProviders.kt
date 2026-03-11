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

import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

/**
 * Provides APIs to obtain ordered collection of APK batches, each intended for local installation, facilitating staged installations.
 *
 * As an example , let's take a Gradle [org.gradle.api.Task] that needs all the Apks for local deployment:
 * ```kotlin
 *  abstract class FetchApkTask: DefaultTask() {
 *    @get:Internal
 *    abstract val apkOutput: Property<com.android.build.api.variant.ApkOutput>
 *
 *    @TaskAction
 *    fun execute() {
 *      def apkInstallGroups = apkOutput.apkInstallGroups
 *          for(installGroup: installGroups) {
 *              installOnDevice(installGroup.apks)
 *         }
 *    }
 *  }
 *
 *  val taskProvider: TaskProvider<FetchApkTask> =
 *      tasks.register("installAppApks", FetchApkTask::class.java)
 *
 *  androidComponents {
 *     onVariants(selector().withName("debug")) { variant ->
 *         val appVariant = variant as? ApplicationVariant
 *         appVariant?.let {
 *             it.outputProviders.provideApkOutputToTask(
 *                  taskProvider,
 *                  FetchApkTask::getOutput, '
 *                  DeviceSpec("testDevice", 33, "", emptyList(), true)
 *             )
 *         }
 *     }
 *  }
 *  ```
 */
interface ApkOutputProviders {

  /**
   * Add Variant's Apk Output for a specific device specification to a Task. The Apk Output includes an ordered collection of batches of
   * Apks to install on a device that matches the device specification available at the configuration time.
   *
   * @param taskProvider the [TaskProvider] returned by Gradle's Task manager when registering the Task of type [TaskT].
   * @param taskInput The method reference the [TaskT] will use to retrieve the current artifact
   * @param deviceSpec the device specification
   */
  fun <TaskT : Task> provideApkOutputToTask(
    taskProvider: TaskProvider<TaskT>,
    taskInput: (TaskT) -> Property<ApkOutput>,
    deviceSpec: DeviceSpec,
  )
}
