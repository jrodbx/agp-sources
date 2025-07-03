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
 * An interface to register a new Managed Device Type.
 *
 * @param DeviceT The api interface for the Managed Device, which should be visible to developers
 * using the DSL.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceDslRegistration<DeviceT : Device> {

    /**
     * The implementation class of the DSL device.
     */
    @get:Incubating
    var dslImplementationClass: Class<out DeviceT>

    /**
     * Sets setup actions for this managed device type, [DeviceT].
     *
     * @param configureAction The class that creates the Configure Action to be used during
     * the Setup Task's Configuration step. This class will be instantiated within the task using
     * Gradle's [ObjectFactory.newInstance] and will then convert the DSL model into task inputs.
     * These are then consumed by the created [taskAction].
     * @param taskAction The class that will perform the setup in the setup task. This class will
     * be instantiated within the task using Gradle's [ObjectFactory.newInstance] and will
     * then run the test based on the inputs generated from the [configureAction].
     */
    @Incubating
    fun <SetupInputT: DeviceSetupInput> setSetupActions(
        configureAction: Class<out DeviceSetupConfigureAction<DeviceT, SetupInputT>>,
        taskAction: Class<out DeviceSetupTaskAction<SetupInputT>>)

    /**
     * Sets task actions for this managed device type, [DeviceT].
     *
     * @param configureAction The class that creates a Configure Action to be used during the Test
     * Task's Configuration step. This class will be instantiated within the task using Gradle's
     * [ObjectFactory.newInstance] and will then convert the DSL and other project information
     * (properties, settings, etc.) into cacheable inputs into the task. These are then consumed by
     * the created [taskAction] as a part of [DeviceTestRunParameters].
     * @param taskAction The class that will perform the test run in the test task. This class
     * will be instantiated within the task using Gradle's [ObjectFactory.newInstance] and will then
     * run the test based on the inputs generated from the [configureAction], passed in as
     * [DeviceTestRunParameters.deviceInput].
     */
    @Incubating
    fun <TestRunInputT: DeviceTestRunInput> setTestRunActions(
        configureAction: Class<out DeviceTestRunConfigureAction<DeviceT, TestRunInputT>>,
        taskAction: Class<out DeviceTestRunTaskAction<TestRunInputT>>
    )
}
