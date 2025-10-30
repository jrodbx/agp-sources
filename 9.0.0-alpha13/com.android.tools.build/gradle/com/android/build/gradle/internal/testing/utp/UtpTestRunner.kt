/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.BaseTestRunner
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.utp.emulatorcontrol.EmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.worker.createUtpRunConfig
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.logging.Level

/**
 * Runs Android Instrumentation tests using UTP (Unified Test Platform).
 */
class UtpTestRunner @JvmOverloads constructor(
        processExecutor: ProcessExecutor,
        private val workerExecutor: WorkerExecutor,
        private val objectFactory: ObjectFactory,
        executor: ExecutorServiceAdapter,
        private val utpJvmExecutable: File,
        private val utpDependencies: UtpDependencies,
        private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        private val emulatorControlConfig: EmulatorControlConfig,
        private val useOrchestrator: Boolean,
        private val forceCompilation: Boolean,
        private val uninstallIncompatibleApks: Boolean,
        private val utpLoggingLevel: Level,
        private val installApkTimeout: Int?,
        private val targetIsSplitApk: Boolean,
        private val uninstallApksAfterTest: Boolean,
)
    : BaseTestRunner(processExecutor, executor) {

    override fun scheduleTests(
            projectName: String,
            variantName: String,
            testData: StaticTestData,
            apksForDevice: MutableMap<DeviceConnector, ImmutableList<File>>,
            privacySandboxSdkInstallBundle: PrivacySandboxSdkInstallBundle,
            helperApks: MutableSet<File>,
            timeoutInMs: Int,
            installOptions: MutableCollection<String>,
            resultsDir: File,
            additionalTestOutputEnabled: Boolean,
            additionalTestOutputDir: File?,
            coverageDir: File,
            logger: ILogger): MutableList<TestResult> {

        val runnerConfigs = apksForDevice
            .filter { (device, _) ->
                !versionedSdkLoader.adbHelper.get().isManagedDevice(device.getSerialNumber(), logger)
            }
            .map { (deviceConnector, apks) ->
                val utpOutputDir = File(resultsDir, deviceConnector.name).apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val additionalTestOutputDir = if (additionalTestOutputEnabled && additionalTestOutputDir != null) {
                    File(additionalTestOutputDir, deviceConnector.name)
                } else {
                    null
                }
                val additionalTestOutputOnDeviceDir = if (additionalTestOutputDir != null) {
                    findAdditionalTestOutputDirectoryOnDevice(deviceConnector, testData)
                } else {
                    null
                }
                createUtpRunConfig(
                    objectFactory,
                    deviceId = deviceConnector.serialNumber,
                    deviceName = deviceConnector.name,
                    deviceSerialNumber = deviceConnector.serialNumber,
                    testData,
                    TargetApkConfigBundle(apks, targetIsSplitApk || apks.size > 1),
                    installOptions,
                    helperApks,
                    uninstallIncompatibleApks,
                    utpOutputDir,
                    emulatorControlConfig,
                    File(coverageDir, deviceConnector.name),
                    useOrchestrator,
                    forceCompilation,
                    additionalTestOutputDir,
                    additionalTestOutputOnDeviceDir,
                    installApkTimeout,
                    privacySandboxSdkInstallBundle.extractedApkMap[deviceConnector] ?: emptyList(),
                    uninstallApksAfterTest,
                    reinstallIncompatibleApksBeforeTest = false,
                    shardConfig = null,
                    utpLoggingLevel,
                )
            }.toList()

        val testSuiteResults = runUtpTestSuiteAndWait(
            runnerConfigs,
            workerExecutor,
            utpJvmExecutable,
            projectName,
            variantName,
            resultsDir,
            logger,
            utpDependencies,
            versionedSdkLoader,
        )

        testSuiteResults.forEach { result ->
            if (result.resultsProto?.hasPlatformError() == true) {
                logger.error(null, getPlatformErrorMessage(result.resultsProto))
            }
            result.resultsProto?.issueList?.forEach { issue ->
                logger.error(null, issue.message)
            }
        }

        val resultProtos = testSuiteResults.mapNotNull(UtpTestRunResult::resultsProto)
        if (resultProtos.isNotEmpty()) {
            val mergedTestResultPbFile = File(resultsDir, TEST_RESULT_PB_FILE_NAME)
            val resultsMerger = UtpTestSuiteResultMerger()
            resultProtos.forEach(resultsMerger::merge)
            mergedTestResultPbFile.outputStream().use {
                resultsMerger.result.writeTo(it)
            }
        }

        return testSuiteResults.map { testRunResult ->
            TestResult().apply {
                testResult = if (testRunResult.testPassed) {
                    TestResult.Result.SUCCEEDED
                } else {
                    TestResult.Result.FAILED
                }
            }
        }.toMutableList()
    }
}
