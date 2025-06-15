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
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.wireless.android.sdk.stats.DeviceTestSpanProfile
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.logging.Level

/**
 * Runs Android Instrumentation tests using UTP (Unified Test Platform).
 */
class UtpTestRunner @JvmOverloads constructor(
        splitSelectExec: File?,
        processExecutor: ProcessExecutor,
        private val workerExecutor: WorkerExecutor,
        executor: ExecutorServiceAdapter,
        private val utpJvmExecutable: File,
        private val utpDependencies: UtpDependencies,
        private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        private val emulatorControlConfig: EmulatorControlConfig,
        private val retentionConfig: RetentionConfig,
        private val useOrchestrator: Boolean,
        private val forceCompilation: Boolean,
        private val uninstallIncompatibleApks: Boolean,
        private val utpTestResultListener: UtpTestResultListener?,
        private val utpLoggingLevel: Level,
        private val installApkTimeout: Int?,
        private val targetIsSplitApk: Boolean,
        private val uninstallApksAfterTest: Boolean,
        private val utpRunProfileManager: UtpRunProfileManager,
        private val configFactory: UtpConfigFactory = UtpConfigFactory(),
        private val runUtpTestSuiteAndWaitFunc: (
            List<UtpRunnerConfig>, String, String, File, ILogger
        ) -> List<UtpTestRunResult> = { runnerConfigs, projectName, variantName, resultsDir, logger ->
            runUtpTestSuiteAndWait(
                runnerConfigs, workerExecutor, projectName, variantName, resultsDir, logger,
                utpTestResultListener, utpDependencies)
        },
)
    : BaseTestRunner(splitSelectExec, processExecutor, executor) {

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

        val runnerConfigs = apksForDevice.filter { (device, apks) ->
            !versionedSdkLoader.adbHelper.get().isManagedDevice(
                device.getSerialNumber(), logger)
        }.map { (deviceConnector, apks) ->
            val utpOutputDir = File(resultsDir, deviceConnector.name).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            val runnerConfig: (
                UtpTestResultListenerServerMetadata,
                File
            ) -> RunnerConfigProto.RunnerConfig = { resultListenerServerMetadata, utpTmpDir ->
                configFactory.createRunnerConfigProtoForLocalDevice(
                    deviceConnector,
                    testData,
                    TargetApkConfigBundle(apks, targetIsSplitApk || apks.size > 1),
                    installOptions,
                    helperApks,
                    uninstallIncompatibleApks,
                    utpDependencies,
                    versionedSdkLoader,
                    utpOutputDir,
                    utpTmpDir,
                    emulatorControlConfig,
                    retentionConfig,
                    File(coverageDir, deviceConnector.name),
                    useOrchestrator,
                    forceCompilation,
                    if (additionalTestOutputEnabled && additionalTestOutputDir != null) {
                        File(additionalTestOutputDir, deviceConnector.name)
                    } else {
                        null
                    },
                    resultListenerServerMetadata.serverPort,
                    resultListenerServerMetadata.clientCert,
                    resultListenerServerMetadata.clientPrivateKey,
                    resultListenerServerMetadata.serverCert,
                    installApkTimeout,
                    privacySandboxSdkInstallBundle.extractedApkMap[deviceConnector]?: emptyList(),
                    uninstallApksAfterTest,
                )
            }
            UtpRunnerConfig(
                utpJvmExecutable,
                deviceConnector.name,
                deviceConnector.serialNumber,
                utpOutputDir,
                runnerConfig,
                configFactory.createServerConfigProto(),
                utpRunProfile = utpRunProfileManager.createTestRunProfile(
                    utpOutputDir,
                    deviceConnector.getDeviceType(),
                    deviceConnector.serialNumber),
                utpLoggingLevel = utpLoggingLevel)
        }.toList()

        val testSuiteResults = runUtpTestSuiteAndWaitFunc(
            runnerConfigs,
            projectName,
            variantName,
            resultsDir,
            logger
        )

        testSuiteResults.forEach { result ->
            if (result.resultsProto?.hasPlatformError() == true) {
                logger.error(null, getPlatformErrorMessage(result.resultsProto))
            }
            result.resultsProto?.issueList?.forEach { issue ->
                logger.error(null, issue.message)
            }
        }

        val resultProtos = testSuiteResults
            .map(UtpTestRunResult::resultsProto)
            .filterNotNull()
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

fun DeviceConnector.getDeviceType() = if(serialNumber.startsWith("emulator")) {
        DeviceTestSpanProfile.DeviceType.CONNECTED_DEVICE_EMULATOR
    } else {
        DeviceTestSpanProfile.DeviceType.CONNECTED_DEVICE_PHYSICAL
    }
