/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.instrumentation.manageddevice

import org.gradle.api.Incubating
import org.gradle.api.file.Directory

/**
 * The Action for doing setup for a given Device.
 *
 * This action is done in the Managed Device Setup Task, which is performed
 * _once_ per device not once per test run.
 *
 * This task action should handle work that would be redundant to do in each test
 * task (Such as setting up the AVD files for an emulator), or for determining
 * attributes about the Device that can potentially need the test to be rerun.
 * These include attributes that may be external to Gradle, such as the api
 * level of a remote device. Or ensuring a snapshot is up-to-date for a given
 * AVD. As such, the SetupTask is never considered up-to-date.
 *
 * In other words, you can consider any setup task as:
 * ```
 * setupTask {
 *     outputs.upToDateWhen { false }
 * }
 * ```
 *
 * An example would be determining the api level of a device specified in a
 * device farm. As this can change, the setup task should determine the api
 * level and write it to the output file, which will then be consumed as
 * input into the [DeviceTestRunTaskAction]
 *
 * @param SetupInputT The Custom Managed Device specific input for the Setup Action.
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceSetupTaskAction<SetupInputT: DeviceSetupInput> {

    /**
     * Perform setup for the Managed Device. This is will be performed every
     * build where the managed device will be required to run a test. That is
     * to say, the SetupTask is never considered up-to-date.
     *
     * If the setup fails, this function should throw an Exception.
     *
     * @param setupInput All parameters required to run the setup.
     * @param outputDir The output directory for the task action. This will be
     * consumed by the [DeviceTestRunTaskAction] as [DeviceTestRunParameters.setupResult].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun setup(setupInput: SetupInputT, outputDir: Directory)
}
