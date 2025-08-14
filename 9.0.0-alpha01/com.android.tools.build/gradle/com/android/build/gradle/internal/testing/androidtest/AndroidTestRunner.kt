/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.androidtest

import com.android.build.gradle.internal.testing.androidtest.AdbApkInstaller.InstallOptions
import java.io.File

/**
 * Sets up the device for and runs Android tests using the `adb shell am instrument` command.
 *
 * This class manages the entire lifecycle of an instrumented test. It handles installing the
 * application under test and any utility APKs, executing the test command, and finally cleaning up
 * the device by uninstalling the APKs.
 *
 * @param adbApkInstaller An [AdbApkInstaller] instance used to handle APK installation and
 * uninstallation on the target device.
 * @param testedApks A list of APK files for the application under test. This can be a single
 * base APK or multiple files for a split APK.
 * @param apkInstallOptions A list of additional command-line options to be used when
 * installing the [testedApks].
 * @param testUtilApks A list of utility APKs that need to be installed on the device for the
 * tests to run. These might include test services or other dependencies.
 * @param uninstallApksAfterTests If `true`, all APKs installed during the test run will be
 * uninstalled from the device after the test completes.
 */
class AndroidTestRunner(
    private val adbApkInstaller: AdbApkInstaller,
    private val testedApks: List<File>,
    private val apkInstallOptions: List<String>,
    private val testUtilApks: List<File>,
    private val uninstallApksAfterTests: Boolean,
    ) {

    /**
     * Executes the test run.
     *
     * This method orchestrates the following steps:
     * 1. Installs the main application APK(s).
     * 2. Installs any required test utility APKs.
     * 3. Runs the `am instrument` command to execute the tests (Note: this part is not yet implemented).
     * 4. Performs cleanup, which is guaranteed to run even if setup or the test itself fails.
     * Cleanup includes uninstalling all installed APKs if [uninstallApksAfterTests] is true.
     */
    fun run() {
        try {
            if (testedApks.size == 1) {
                adbApkInstaller.installApk(
                    testedApks.first(),
                    InstallOptions(extraArgs = apkInstallOptions)
                )
            } else if (testedApks.size > 1) {
                adbApkInstaller.installSplitApk(
                    testedApks,
                    InstallOptions(extraArgs = apkInstallOptions)
                )
            }
            testUtilApks.forEach { apk ->
                adbApkInstaller.installApk(
                    apk,
                    InstallOptions(grantPermissions = true, forceQueryable = true)
                )
            }

            // TODO: This class is still under construction. We will run am instrument command here
            //  to run android test after setup is complete.

        } finally {
            adbApkInstaller.postTestCleanup()
            if (uninstallApksAfterTests) {
                if (testedApks.isNotEmpty()) {
                    adbApkInstaller.uninstallApk(testedApks.first())
                }

                testUtilApks.forEach { apk ->
                    adbApkInstaller.uninstallApk(apk)
                }
            }
        }
    }
}
