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

/**
 * Provides an ordered collection of APK batches, each intended for local installation,
 * facilitating staged installations.
 *
 * An instance of [ApkOutput] can be obtained via [ApplicationVariant.outputProviders]
 *
 * As an example , let's take a Gradle [org.gradle.api.Task] that needs all the Apks for local deployment:
 *
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
 *  def taskProvider = tasks.register<FetchApkTask>("installAppApks")
 *  androidComponents {
 *      onVariants(selector().withName("debug")) { variant ->
 *          val appVariant = variant as? ApplicationVariant
 *          if (appVariant != null) {
 *              appVariant.outputProviders.provideApkOutputToTask(taskProvider, FetchApkTask::getOutput, DeviceSpec("testDevice", 33, "", [], true))
 *          }
 *      }
 *  }
 *  ```
 */
@Incubating
interface ApkOutput {
    /**
     * Returns an ordered collection of co-installable APK batches targeted for a specific device.
     *
     */
    @get:Incubating
    val apkInstallGroups: List<ApkInstallGroup>
}
