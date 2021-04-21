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
import com.android.build.gradle.internal.testing.CustomTestRunListener
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceConnector
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import com.google.testing.platform.proto.api.core.TestStatusProto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Unified Test Platform outputs test results with this hardcoded name file.
const val TEST_RESULT_OUTPUT_FILE_NAME = "test-result.textproto"

/**
 * Runs Android Instrumentation tests using UTP (Unified Test Platform).
 */
class UtpTestRunner @JvmOverloads constructor(
        splitSelectExec: File?,
        processExecutor: ProcessExecutor,
        private val javaProcessExecutor: JavaProcessExecutor,
        executor: ExecutorServiceAdapter,
        private val utpDependencies: UtpDependencies,
        private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
        private val retentionConfig: RetentionConfig,
        private val useOrchestrator: Boolean,
        private val utpTestResultListener: UtpTestResultListener?,
        private val configFactory: UtpConfigFactory = UtpConfigFactory())
    : BaseTestRunner(splitSelectExec, processExecutor, executor), UtpTestResultListener {

    private val testResultReporters: MutableMap<String, UtpTestResultListener> = mutableMapOf()

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
        return UtpTestResultListenerServerRunner(this).use { resultListenerServerRunner ->
            val resultListenerServerMetadata = resultListenerServerRunner.metadata
            apksForDevice.map { (deviceConnector, apks) ->
                val utpOutputDir = File(resultsDir, deviceConnector.name).apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val utpTmpDir = Files.createTempDir()
                val runnerConfigProtoFile =
                        File.createTempFile("runnerConfig", ".pb").also { file ->
                            FileOutputStream(file).use { writer ->
                                configFactory.createRunnerConfigProtoForLocalDevice(
                                        deviceConnector,
                                        testData,
                                        apks.union(helperApks) + testData.testApk,
                                        utpDependencies,
                                        versionedSdkLoader,
                                        utpOutputDir,
                                        utpTmpDir,
                                        retentionConfig,
                                        useOrchestrator,
                                        resultListenerServerMetadata.serverPort,
                                        resultListenerServerMetadata.clientCert,
                                        resultListenerServerMetadata.clientPrivateKey,
                                        resultListenerServerMetadata.serverCert).writeTo(writer)
                            }
                        }

                testResultReporters[deviceConnector.serialNumber] = DdmlibTestResultAdapter(
                        deviceConnector.name,
                        CustomTestRunListener(
                                deviceConnector.name,
                                projectName,
                                variantName,
                                logger).apply {
                            setReportDir(resultsDir)
                        }
                )

                val resultsProto = runUtpTestSuite(
                        runnerConfigProtoFile,
                        utpOutputDir,
                        configFactory,
                        utpDependencies,
                        javaProcessExecutor,
                        logger)

                testResultReporters.remove(deviceConnector.serialNumber)

                val testResultPbFile = File(utpOutputDir, "test-result.pb")
                resultsProto.writeTo(testResultPbFile.outputStream())

                try {
                    FileUtils.deleteRecursivelyIfExists(utpOutputDir.resolve(TEST_LOG_DIR))
                    FileUtils.deleteRecursivelyIfExists(utpTmpDir)
                } catch (e: IOException) {
                    logger.warning("Failed to cleanup temporary directories: $e")
                }

                if (resultsProto.hasPlatformError()) {
                    logger.error(null, "Platform error occurred when running the UTP test suite")
                }
                logger.quiet("\nTest results saved as ${testResultPbFile.toURI()}. " +
                        "Inspect these results in Android Studio by selecting Run > Import Tests " +
                        "From File from the menu bar and importing test-result.pb.")
                val testFailed = resultsProto.hasPlatformError() ||
                        resultsProto.testResultList.any { testCaseResult ->
                            testCaseResult.testStatus == TestStatusProto.TestStatus.FAILED
                                    || testCaseResult.testStatus == TestStatusProto.TestStatus.ERROR
                        }
                TestResult().apply {
                    testResult = if (testFailed) {
                        TestResult.Result.FAILED
                    } else {
                        TestResult.Result.SUCCEEDED
                    }
                }
            }.toMutableList()
        }
    }

    override fun onTestResultEvent(testResultEvent: GradleAndroidTestResultListenerProto.TestResultEvent) {
        testResultReporters[testResultEvent.deviceId]?.onTestResultEvent(testResultEvent)
        utpTestResultListener?.onTestResultEvent(testResultEvent)
    }
}
