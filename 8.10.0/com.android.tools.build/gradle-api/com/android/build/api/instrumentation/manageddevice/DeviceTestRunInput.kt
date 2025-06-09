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

import java.io.Serializable
import org.gradle.api.Incubating
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

/**
 * Interface that represents input into the Instrumented Test Task, created by
 * a [DeviceTestRunConfigureAction], to be consumed by the [DeviceTestRunTaskAction].
 *
 * This interface should be implemented for use with a Custom Managed Device Registration.
 * The implementation of this class is used as an input into the Managed Device Test Task.
 * Therefore, all properities on the implementation of this interface
 * _must_ be marked with [Input], [Nested] or [Internal] to ensure proper caching of test results.
 *
 * Additionally, making the type compatible with [ObjectFactory.newInstance] makes implementing
 * the [DeviceTestRunConfigureAction] easier. See: [DeviceTestRunConfureAction]
 *
 * Example Implementation:
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
 * ```
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceTestRunInput: Serializable
