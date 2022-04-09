/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.InstallException
import com.android.ddmlib.MultiLineReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.gradle.api.logging.Logging.getLogger

private val logger = getLogger("AdditionalTestOutputUtils")

const val ADDITIONAL_TEST_OUTPUT_MIN_API_LEVEL = 16

/**
 * Finds a directory for storing additional test output on a test device.
 */
fun findAdditionalTestOutputDirectoryOnDevice(
    device: DeviceConnector,
    testData: StaticTestData
): String? {
    if (device.getApiLevel() < ADDITIONAL_TEST_OUTPUT_MIN_API_LEVEL) {
        logger.warn("additionalTestOutput is not supported on devices running API level < 16")
        return null
    }

    val userSpecifiedDir = testData.instrumentationRunnerArguments.get("additionalTestOutputDir")
    if (userSpecifiedDir != null) {
        return userSpecifiedDir
    }

    if (device.getApiLevel() >= 29) {
        // sdcard/Android/media/<package_name> is the only special-cased storage dir, which
        // allows separate shell processes and instrumented tests to both have read/write access
        // without needing to apply external legacy storage flags (which were removed in API 30)
        // or --no-isolated-storage.
        return "/sdcard/Android/media/${testData.instrumentationTargetPackageId}/additional_test_output"
    }

    val additionalTestOutputLocation = queryAdditionalTestOutputLocation(device, testData)
    if (additionalTestOutputLocation == null) {
        logger.warn("additionalTestOutput is not supported on this device running API level ${device.getApiLevel()} because the additional test output directory could not be found")
        return null
    }
    return "${additionalTestOutputLocation}/data/${testData.instrumentationTargetPackageId}/files/test_data"
}

/**
 * Finds a directory for storing additional test output on a Gradle managed device.
 */
fun findAdditionalTestOutputDirectoryOnManagedDevice(
    device: UtpManagedDevice,
    testData: StaticTestData
): String? {
    if (device.api < ADDITIONAL_TEST_OUTPUT_MIN_API_LEVEL) {
        logger.warn("additionalTestOutput is not supported on devices running API level < 16")
        return null
    }

    val userSpecifiedDir = testData.instrumentationRunnerArguments.get("additionalTestOutputDir")
    if (userSpecifiedDir != null) {
        return userSpecifiedDir
    }

    if (device.api < 29) {
        logger.warn("additionalTestOutput is not supported on Gradle managed devices running API level < 29")
        return null
    }
    // sdcard/Android/media/<package_name> is the only special-cased storage dir, which
    // allows separate shell processes and instrumented tests to both have read/write access
    // without needing to apply external legacy storage flags (which were removed in API 30)
    // or --no-isolated-storage.
    return "/sdcard/Android/media/${testData.instrumentationTargetPackageId}/additional_test_output"
}

private fun queryAdditionalTestOutputLocation(
    device: DeviceConnector,
    testData: StaticTestData
): String? {
    var result: String? = null
    val receiver: MultiLineReceiver = object : MultiLineReceiver() {
        override fun processNewLines(lines: Array<String>) {
            for (row: String in lines) {
                if (row.isEmpty()) {
                    break
                }
                // Ignore any lines to stdout which aren't results of the content
                // provider query.
                if (!row.startsWith("Row:")) {
                    break
                }
                result = row.split("_data=").toTypedArray()[1].trim { it <= ' ' }
            }
        }

        override fun isCancelled(): Boolean {
            return false
        }
    }

    device.executeShellCommand(
        "content query --uri content://media/external/file"
                + " --projection _data --where \"_data LIKE '%/Android'\"",
        receiver,
        DdmPreferences.getTimeOut().toLong(),
        TimeUnit.MILLISECONDS
    )
    receiver.flush()

    return result
}
