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
 * Action for configuring the necessary [inputs][DeviceSetupInput] for optional Device Setup
 *
 * This class is used to take the [managed device DSL][Device] create a group of
 * [setup inputs][DeviceSetupInput].
 *
 * This interface can be implemented for use with a Custom Managed Device Registration,
 * as device setup is optional for Custom Managed Devices.
 *
 * Example [DeviceSetupInput] and [Device] implementation for Configuration Action
 * ```
 * abstract class CustomSetupInput: DeviceSetupInput {
 *     /** name of device from DSL, used for error reporting */
 *     @get: Internal
 *     abstract val deviceName: Property<String>
 *
 *     /** Id of device in a device farm, for example. */
 *     @get: Input
 *     abstract val deviceId: Property<Int>
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
 * [DeviceSetupConfigureAction] implementation
 *
 * ```
 * SetupConfigureAction(): DeviceSetupConfigureAction<CustomDevice, SetupInput> {
 *
 *     override fun configureTaskInput(
 *             deviceDSL: CustomDevice, objects: ObjectFactory): SetupInput =
 *         objects.newInstance(SetupInput::class.java).apply {
 *             deviceName.set(deviceDSL.getName())
 *             deviceId.set(deviceDSL.id)
 *         }
 * }
 * ```
 *
 * @param DeviceT: The interface of the Custom Managed Device this configure action corresponds to.
 * @param InputT: The specialized [DeviceSetupInput] this configuration action generates for the
 * instrumentation test task.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceSetupConfigureAction <DeviceT : Device, InputT: DeviceSetupInput> {

    /**
     * Generates the inputs into the test setup task to be consumed by the setup task action.
     *
     * @param deviceDSL The DSL for the individual managed device being setup.
     * @param objects Object factory available for convenience to instantiate the Setup Input.
     *
     * @return The inputs for the Setup Task. This will be consumed as part of the
     * setup action. As specified by the ManagedDeviceSetupFactory.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun configureTaskInput(deviceDSL: DeviceT, objects: ObjectFactory): InputT
}
