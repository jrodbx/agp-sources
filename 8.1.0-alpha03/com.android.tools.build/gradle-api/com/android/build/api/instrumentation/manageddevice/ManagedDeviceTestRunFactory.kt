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
import java.io.Serializable
import org.gradle.api.Incubating
import org.gradle.api.model.ObjectFactory

/**
 * Test Run component of Custom Managed Device Registration.
 *
 * Consists of two parts: the configuration action, which takes information from
 * the dsl and converts it to task inputs; and the TaskAction, which actually
 * performs the device test, consuming the task inputs from the config action
 *
 * @param DeviceT The custom device DSL associated with this Registration.
 * @param InputT The custom task input for this custom device type.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface ManagedDeviceTestRunFactory <DeviceT: Device, InputT: DeviceTestRunInput>: Serializable {

    /**
     * Creates a Configure Action to be used during the Test Task's Configuration
     * step.
     *
     * This action converts the DSL and other project information (properties, settings, etc.)
     * into cacheable inputs into the task. These are then consumed by the created [taskAction]
     * as a part of [DeviceTestRunParameters].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun configAction(): DeviceTestRunConfigureAction<DeviceT, InputT>

    /**
     * Returns the explicit class that will perform the test run in the test task.
     *
     * This class will be instantiated within the task using Gradle's [ObjectFactory.newInstance]
     * and will then run the test based on the inputs generated from the [configAction], passed in
     * as [DeviceTestRunParameters.deviceInput]
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun taskActionClass(): Class<out DeviceTestRunTaskAction<InputT>>
}
