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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

/**
 * Interface that represents input into the Managed Device Setup Task, created by
 * a [DeviceSetupConfigureAction], to be consumed by the [DeviceSetupTaskAction].
 *
 * This interface can be implemented for use with a Custom Managed Device Registration,
 * as device setup is optional for Custom Managed Devices.
 * The implementation of this class is used as an input into the
 * Managed Device Setup Task. All properties of the implementation of this interface
 * _must_ be marked with [Input], [Nested] or [Internal].
 *
 * Additionally, making the type compatible with [ObjectFactory.newInstance] makes
 * implementing the [DeviceSetupConfigureAction] easier. See: [DeviceSetupConfigureAction]
 *
 * Example Implementation:
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
 * ```
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceSetupInput: Serializable
