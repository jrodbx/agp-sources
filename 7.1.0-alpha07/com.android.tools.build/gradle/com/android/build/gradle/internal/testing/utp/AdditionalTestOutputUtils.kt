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

/**
 * Finds a directory for storing additional test output on a test device.
 */
fun findAdditionalTestOutputDirectoryOnDevice(
    device: DeviceConnector,
    testData: StaticTestData
): String {
    val userSpecifiedDir = testData.instrumentationRunnerArguments.get("additionalTestOutputDir")
    if (userSpecifiedDir != null) {
        return userSpecifiedDir
    }

    if (device.getApiLevel() < 16) {
        error("additionalTestOutput is not supported on devices running API level < 16")
    }

    if (device.getApiLevel() >= 29) {
        // sdcard/Android/media/<package_name> is the only special-cased storage dir, which
        // allows separate shell processes and instrumented tests to both have read/write access
        // without needing to apply external legacy storage flags (which were removed in API 30)
        // or --no-isolated-storage.
        return "/sdcard/Android/media/${testData.instrumentationTargetPackageId}/additional_test_output"
    }

    return requireNotNull(queryAdditionalTestOutputLocation(device, testData))
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

    return "${result}/data/${testData.instrumentationTargetPackageId}/files/test_data"
}
