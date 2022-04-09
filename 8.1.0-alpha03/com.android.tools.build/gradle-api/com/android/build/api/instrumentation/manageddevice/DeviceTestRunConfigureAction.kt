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

import com.android.build.api.dsl.Device
import org.gradle.api.Incubating
import org.gradle.api.model.ObjectFactory

/**
 * Action for configuring the device specific [inputs][DeviceTestRunInput] for the
 * Managed Device Test Task.
 *
 * This class is used to take the [managed device DSL][Device] to create a group of
 * [cachable inputs][DeviceTestRunInput]
 *
 * This should be implemented for use with a Custom Managed Device Registration.
 *
 * Example [DeviceTestRunInput] and [Device] implementation for Configuration Action
 * ```
 * abstract class CustomInput: DeviceTestRunInput {
 *     /** name of device from DSL */
 *     @get: Input
 *     abstract val deviceName: Property<String>
 *
 *     /** Id of device in a device farm, for example*/
 *     @get: Input
 *     abstract val deviceId: Property<Int>
 *
 *     @get: Internal
 *     abstract val timeoutSeconds: Property<Int>
 * }
 *
 * open class CustomDevice @Inject constructor(private val name: String): Device {
 *
 *     override fun getName() = name
 *
 *     var id: Int = 0
 *
 *     var timeoutSeconds: Int = 30
 * }
 * ```
 *
 * [DeviceTestRunConfigureAction] implementation
 *
 * ```
 * CustomConfigureAction(): DeviceTestRunConfigureAction<CustomDevice, CustomInput> {
 *
 *     override fun configureTaskInput(
 *             deviceDSL: CustomDevice, objects: ObjectFactory): CustomInput =
 *         objects.newInstance(CustomInput::class.java).apply {
 *             deviceName.set(deviceDSL.getName())
 *             deviceId.set(deviceDSL.id)
 *             timeoutSectonds.set(deviceDSL.timeoutSeconds)
 *         }
 * }
 * ```
 *
 *
 * @param DeviceT: The interface of the Custom Managed Device this configure action corresponds to.
 * @param InputT: The specialized [DeviceTestRunInput] this configuration action generates for the
 * instrumentation test task.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceTestRunConfigureAction <DeviceT : Device, InputT: DeviceTestRunInput> {

    /**
     * Generates the cacheable inputs to the test run task to be consumed by the corresponding
     * test run action.
     *
     * @param deviceDSL The DSL for the individual device for the test task.
     * @param objects Object factory available for convenience to instantiate the TestRunInput.
     *
     * @return The cacheable inputs for the test task. This will be consumed as part of the
     * [test run action][DeviceTestRunTaskAction]. As specified by the [ManagedDeviceTestRunFactory]
     *
     * @suppress Do not use from production code.This API exposed for prototype.
     */
    @Incubating
    fun configureTaskInput(deviceDSL: DeviceT, objects: ObjectFactory): InputT
}
