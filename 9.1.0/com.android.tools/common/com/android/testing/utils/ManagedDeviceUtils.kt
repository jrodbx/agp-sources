/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.testing.utils

/**
 * Checks if the given device ID belongs to a Gradle-managed device.
 *
 * Gradle-managed devices are automatically provisioned by the Android Gradle Plugin and their device IDs are standardized to end with
 * [ManagedDeviceDeviceIDSuffix].
 *
 * @param deviceId The unique identifier of the device to check.
 * @return `true` if the device ID has the characteristic suffix of a Gradle-managed device, `false` otherwise.
 */
fun isGradleManagedDevice(deviceId: String): Boolean = deviceId.endsWith(ManagedDeviceDeviceIDSuffix)

/** The suffix for device IDs of all Gradle-managed devices. */
const val ManagedDeviceDeviceIDSuffix = "_GradleManagedDevice"
