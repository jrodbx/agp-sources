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
import com.android.build.api.dsl.TestOptions
import org.gradle.api.Incubating

// TODO(b/260721648) Add versioning to registrations separate from Plugin version
// for cleaner interfacing/compatibility check with the registry.
/**
 * Registry for Custom Managed Device types.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
interface CustomManagedDeviceRegistry {

    /**
     * Registers a Custom Managed Device
     *
     * Registers a Custom Managed Device with the given DSL and Test Run behavior.
     * Devices defined as a part of [TestOptions.managedDevices] will be able to
     * use the custom device DSL and perform the given test behavior in the corresponding
     * Test Tasks.
     *
     * @param DeviceT The api interface for the Custom Managed Device in the DSL.
     * @param TestRunInputT the input into the Test Run Task.
     * @param dsl The [ManagedDeviceDslRegistration] for this custom device, which allows
     *     the Android Plugin to associate the given device type with this registration.
     * @param testRunFactory The [ManagedDeviceTestRunFactory] to be used in the Test
     *     Tasks associated to devices of the given Custom Device Type.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    fun <DeviceT: Device, TestRunInputT: DeviceTestRunInput> registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    )

    /**
     * Registers a Custom Managed Device
     *
     * Registers a Custom Managed Device with the given DSL and Test Run behavior.
     * Devices defined as a part of [TestOptions.managedDevices] will be able to
     * use the custom device DSL and perform the given test behavior in the corresponding
     * Test Tasks. This also specifies the given action for the Device's Setup Task
     *
     * @param DeviceT The api interface for the Custom Managed Device in the DSL.
     * @param TestRunInputT the input into the Test Run Task.
     * @param SetupInputT the input into the Setup Task.
     * @param dsl The [ManagedDeviceDslRegistration] for this custom device, which allows
     *     the Android Plugin to associate the given device type with this registration.
     * @param setupFactory The [ManagedDeviceSetupFactory] to be used in the setup task
     *     for any device of the given Custom Device Type.
     * @param testRunFactory The [ManagedDeviceTestRunFactory] to be used in the Test
     *     Tasks associated to devices of the given Custom Device Type.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    fun <
            DeviceT: Device,
            SetupInputT: DeviceSetupInput,
            TestRunInputT: DeviceTestRunInput
    > registerCustomDeviceType(
        dsl: ManagedDeviceDslRegistration<DeviceT>,
        setupFactory: ManagedDeviceSetupFactory<DeviceT, SetupInputT>,
        testRunFactory: ManagedDeviceTestRunFactory<DeviceT, TestRunInputT>
    )
}
