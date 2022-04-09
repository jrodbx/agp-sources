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
 * Setup component of Custom Managed Device Registration.
 *
 * This component is entirely optional, as some Custom Managed Devices may not
 * need this feature.
 *
 * Consists of two parts: the configuration action, which takes information from
 * the dsl and converts it to task inputs; and the TaskAction, which actually
 * performs the general device setup.
 *
 * Setup is run only once per device (as opposed to once per test task) and should
 * handle any redundant work for the individual test runs. See [DeviceSetupTaskAction],
 * as well as any un-cacheable work that needs to be done to determine Test Task Inputs.
 *
 * @param DeviceT The custom device DSL associated with this Registration.
 * @param InputT The custom task input for this custom device type.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface ManagedDeviceSetupFactory <DeviceT: Device, InputT: DeviceSetupInput>: Serializable {

    /**
     * Creates the Configure Action to be used during the Setup Task's Configuration
     * step.
     *
     * This action converts the DSL and other project Information into task inputs. These
     * are then consumed by the created [taskAction].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun configAction(): DeviceSetupConfigureAction<DeviceT, InputT>

    /**
     * Returns the Explicit class that will perform the setup in the setup task.
     *
     * This class will be instantiated within the task using Gradle's [ObjectFactory.newInstance]
     * and will then run the test based on the inputs generated from the [configAction].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun taskActionClass(): Class<out DeviceSetupTaskAction<InputT>>
}
