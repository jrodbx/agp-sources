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

/**
 * The Action for running the Test within the test Task.
 *
 * This is the action to perform all of the test behavior for the given Custom
 * Managed Device. This includes:
 *
 * 1. Setting up the device for testing
 * 2. Installing all apks necessary for the test
 * 3. Running the tests
 * 4. Any clean up for the device.
 *
 * This should be implemented for use with a Custom Managed Device Registration.
 *
 * @param InputT The Custom Managed Device specific input to be passed
 *     in as part of the [DeviceTestRunParameters] when the tests
 *     are run.
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceTestRunTaskAction<InputT: DeviceTestRunInput> {

    /**
     * Runs the tests with the given parameters.
     *
     * @param params All parameters required to run this test.
     * @return returns true if and only if all tests passed. Determines if the
     *   task succeeds or fails.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @Incubating
    fun runTests(params: DeviceTestRunParameters<InputT>): Boolean
}
