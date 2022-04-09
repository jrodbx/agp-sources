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
import java.io.File
import java.util.logging.Level
import org.gradle.workers.WorkerExecutor

/**
 * Runs Android Instrumentation tests using UTP (Unified Test Platform).
 */
class UtpTestRunner @JvmOverloads constructor(
        splitSelectExec: File?,
        processExecutor: ProcessExecutor,
        private val workerExecutor: WorkerExecutor,
        executor: ExecutorServiceAdapter,
        private val utpDependencies: UtpDependencies,
        private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        private val retentionConfig: RetentionConfig,
        private val useOrchestrator: Boolean,
        private val uninstallIncompatibleApks: Boolean,
        private val utpTestResultListener: UtpTestResultListener?,
        private val utpLoggingLevel: Level,
        private val configFactory: UtpConfigFactory = UtpConfigFactory(),
        private val runUtpTestSuiteAndWaitFunc: (
            List<UtpRunnerConfig>, String, String, File, ILogger
        ) -> List<UtpTestRunResult> = { runnerConfigs, projectName, variantName, resultsDir, logger ->
            runUtpTestSuiteAndWait(
                runnerConfigs, workerExecutor, projectName, variantName, resultsDir, logger,
                utpTestResultListener, utpDependencies)
        }
)
    : BaseTestRunner(splitSelectExec, processExecutor, executor) {

    override fun scheduleTests(
            projectName: String,
            variantName: String,
            testData: StaticTestData,
            apksForDevice: MutableMap<DeviceConnector, ImmutableList<File>>,
            helperApks: MutableSet<File>,
            timeoutInMs: Int,
            installOptions: MutableCollection<String>,
            resultsDir: File,
            additionalTestOutputEnabled: Boolean,
            additionalTestOutputDir: File?,
            coverageDir: File,
            logger: ILogger): MutableList<TestResult> {
        val runnerConfigs = apksForDevice.map { (deviceConnector, apks) ->
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
                    apks,
                    installOptions,
                    helperApks,
                    uninstallIncompatibleApks,
                    utpDependencies,
                    versionedSdkLoader,
                    utpOutputDir,
                    utpTmpDir,
                    retentionConfig,
                    coverageDir,
                    useOrchestrator,
                    if (additionalTestOutputEnabled) {
                        additionalTestOutputDir
                    } else {
                        null
                    },
                    resultListenerServerMetadata.serverPort,
                    resultListenerServerMetadata.clientCert,
                    resultListenerServerMetadata.clientPrivateKey,
                    resultListenerServerMetadata.serverCert
                )
            }
            UtpRunnerConfig(
                deviceConnector.name,
                deviceConnector.serialNumber,
                utpOutputDir,
                runnerConfig,
                configFactory.createServerConfigProto(),
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
        }

        val resultProtos = testSuiteResults
            .map(UtpTestRunResult::resultsProto)
            .filterNotNull()
        if (resultProtos.isNotEmpty()) {
            val mergedTestResultPbFile = File(resultsDir, TEST_RESULT_PB_FILE_NAME)
            val resultsMerger = UtpTestSuiteResultMerger()
            resultProtos.forEach(resultsMerger::merge)
            resultsMerger.result.writeTo(mergedTestResultPbFile.outputStream())
            logger.quiet(
                "\nTest results saved as ${mergedTestResultPbFile.toURI()}. " +
                        "Inspect these results in Android Studio by selecting Run > Import Tests " +
                        "From File from the menu bar and importing test-result.pb."
            )
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
