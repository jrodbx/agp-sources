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

import com.android.build.gradle.internal.testing.androidtest.instrument.AmInstrumentationRunner
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * A Gradle work action to run Android instrumentation tests.
 */
abstract class AndroidTestWorkAction : WorkAction<AndroidTestWorkAction.Parameters> {
    interface Parameters : WorkParameters {
        val adbExecutable: RegularFileProperty
        val aaptExecutable: RegularFileProperty
        val deviceSerial: Property<String>
        val deviceApiLevel: Property<Int>
        val instrumentationRunnerClass: Property<String>
        val instrumentationTargetPackageId: Property<String>
        val testedApks: ConfigurableFileCollection
        val testUtilApks: ConfigurableFileCollection
        val apkInstallTimeOutInMs: Property<Integer>
        val apkInstallOptions: ListProperty<String>
        val uninstallApksAfterTests: Property<Boolean>
    }

    override fun execute() {
        val adb = parameters.adbExecutable.get().asFile
        val aaptExecutable = parameters.aaptExecutable.get().asFile
        val deviceSerial = parameters.deviceSerial.get()
        val deviceApiLevel = parameters.deviceApiLevel.get()
        val testedApks = parameters.testedApks.toList()
        val testUtilApks = parameters.testUtilApks.toList()
        val uninstallApksAfterTests = parameters.uninstallApksAfterTests.get()
        val apkInstallTimeOutInMs = parameters.apkInstallTimeOutInMs.get().toLong()
        val apkInstallOptions = parameters.apkInstallOptions.get()
        val adbApkInstaller = AdbApkInstaller(
            adb, aaptExecutable, deviceSerial, deviceApiLevel,
            apkInstallTimeOutInMs)
        val instrumentationRunnerClass = parameters.instrumentationRunnerClass.get()
        val instrumentationTargetPackageId = parameters.instrumentationTargetPackageId.get()
        val instrumentationRunner = AmInstrumentationRunner(
            adb, deviceSerial, instrumentationRunnerClass, instrumentationTargetPackageId)

        AndroidTestRunner(
            adbApkInstaller,
            instrumentationRunner,
            testedApks,
            apkInstallOptions,
            testUtilApks,
            uninstallApksAfterTests,
            ).run()
    }
}
