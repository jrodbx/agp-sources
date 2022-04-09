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

// TODO(b/260721648) add more information on how the output is supposed to be formatted.
/**
 * All parameters for a [DeviceTestRunTaskAction].
 *
 * @param InputT The specialized input type for the custom managed device associated
 *     with the [DeviceTestRunTaskAction].
 */
@Incubating
interface DeviceTestRunParameters<InputT: DeviceTestRunInput> {

    /**
     * All inputs specific to the Custom Managed Device type created
     * by a [DeviceTestRunConfigureAction].
     */
    @get: Incubating
    val deviceInput: InputT

    /**
     * All inputs for the Test Run independent of the type of managed device.
     *
     * See [TestRunData].
     */
    @get: Incubating
    val testRunData: TestRunData
}
