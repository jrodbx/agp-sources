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

import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask.DeviceProviderFactory
import com.android.build.gradle.internal.testing.TestData
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.testing.api.DeviceConfigProviderImpl
import com.android.ddmlib.IDevice
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkerExecutor

/**
 * Execute android test using Gradle worker.
 */
fun runAndroidTest(
    workerExecutor: WorkerExecutor,
    adbExecutable: Provider<RegularFile>,
    aaptExecutable: Provider<RegularFile>,
    deviceProviderFactory: DeviceProviderFactory,
    testData: TestData,
    testUtilApks: FileCollection,
    apkInstallTimeOutInMs: Provider<Integer>,
    apkInstallOptions: ListProperty<String>,
    uninstallApksAfterTests: Provider<Boolean>,
) {
    val deviceProvider = deviceProviderFactory.getDeviceProvider(
        adbExecutable,
        System.getenv("ANDROID_SERIAL"))
    deviceProvider.use {
        val workQueue = workerExecutor.noIsolation()
        val onlineDevices = deviceProvider.devices
            .filter { it.state != IDevice.DeviceState.UNAUTHORIZED }

        onlineDevices.forEach { device ->
            workQueue.submit(AndroidTestWorkAction::class.java) { params ->
                val deviceConfigProvider = DeviceConfigProviderImpl(device)
                val testedApks = testData.findTestedApks(deviceConfigProvider)
                params.adbExecutable.setDisallowChanges(adbExecutable)
                params.aaptExecutable.setDisallowChanges(aaptExecutable)
                params.deviceSerial.setDisallowChanges(device.serialNumber)
                params.deviceApiLevel.setDisallowChanges(deviceConfigProvider.apiLevel)
                params.instrumentationRunnerClass.setDisallowChanges(testData.instrumentationRunner)
                params.instrumentationTargetPackageId.setDisallowChanges(testData.instrumentationTargetPackageId)
                params.testedApks.fromDisallowChanges(testedApks)
                params.testUtilApks.fromDisallowChanges(testUtilApks)
                params.apkInstallTimeOutInMs.setDisallowChanges(apkInstallTimeOutInMs)
                params.apkInstallOptions.setDisallowChanges(apkInstallOptions)
                params.uninstallApksAfterTests.setDisallowChanges(uninstallApksAfterTests)
            }
        }

        workQueue.await()
    }
}
