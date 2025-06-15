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

import java.util.regex.Pattern

/**
 * The regex for detecting if the emulator is part of a setup task
 *
 * Setup Device's ids are based off the GMD AVD name format, appended with _snapshot.
 * Because this is based off of the filename of the avd, it has a few strict requirements.
 */
private val setupDeviceIdRegex = Pattern.compile("""dev[0-9]+[_\-0-9a-zA-Z]*_snapshot$""")

/**
 * The regex for detecting if the emulator is part of a GMD test task.
 *
 * Test Device's ids are based off the path of the devices test task. The only requirement
 * Gradle has for task names is the `:` is a reserved character. The id may optionally include a
 * shard suffix if the test is using sharding. This is simply `_` followed by an integer
 * representing the shard index.
 *
 * So given these requirements, and that GMD cannot be declared at top level. We expect the
 * task to be one of the forms:
 *
 * ```
 *     <project>:<deviceName>AndroidTest
 *     <project>:<deviceName>AndroidTest_<shardIndex>
 * ```
 */
private val androidTestDeviceIdRegex =
    Pattern.compile("""[^:]+:[^:]+AndroidTest(_[0-9]+)?$""")

/**
 * Checks to see if a given device id is from a Gradle Managed Device. This is used to differentiate
 * emulators spawned from Gradle, than those spawned from the User.
 *
 * In order to get the id to pass into the function. You need to use the adb command
 * `adb -s <emulator-serial> emu avd id` or equivalent on the given emulator's serial.
 *
 * The device is then checked to see if the [deviceId] conforms to that of either a setup
 * device for establishing avd snapshots or a test device that is used in Managed Device Test
 * Tasks.
 *
 * @param deviceId the avd id of the given emulator instance. This can be retrieved via the adb
 * command: `adb -s <emulator-serial> emu avd id`
 *
 * @return True if and only if the id is a valid GMD device id. False, otherwise.
 */
fun isGradleManagedDevice(deviceId: String): Boolean =
    setupDeviceIdRegex.matcher(deviceId).find() ||
            androidTestDeviceIdRegex.matcher(deviceId).find()

/**
 * Gets the setup device id name for the given Gradle Managed Device AVD.
 *
 * @param avdName the name of the Gradle Managed Device AVD.
 *
 * @return the device id that should be called with the emulator command.
 */
fun createSetupDeviceId(avdName: String) = "${avdName}_snapshot"
