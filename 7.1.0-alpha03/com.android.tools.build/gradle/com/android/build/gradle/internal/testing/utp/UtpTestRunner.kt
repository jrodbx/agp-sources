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
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
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
        private val utpTestResultListener: UtpTestResultListener?,
        private val configFactory: UtpConfigFactory = UtpConfigFactory())
    : BaseTestRunner(splitSelectExec, processExecutor, executor), UtpTestResultListener {

    private val testResultReporters: ConcurrentHashMap<String, UtpTestResultListener> =
        ConcurrentHashMap()

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
        UtpTestResultListenerServerRunner(this).use { resultListenerServerRunner ->
            val resultListenerServerMetadata = resultListenerServerRunner.metadata
            val workQueue = workerExecutor.noIsolation()

            val postProcessCallback = apksForDevice.map { (deviceConnector, apks) ->
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
                                        apks,
                                        installOptions,
                                        helperApks,
                                        utpDependencies,
                                        versionedSdkLoader,
                                        utpOutputDir,
                                        utpTmpDir,
                                        retentionConfig,
                                        coverageDir,
                                        useOrchestrator,
                                        resultListenerServerMetadata.serverPort,
                                        resultListenerServerMetadata.clientCert,
                                        resultListenerServerMetadata.clientPrivateKey,
                                        resultListenerServerMetadata.serverCert).writeTo(writer)
                            }
                        }

                lateinit var resultsProto: TestSuiteResult
                val ddmlibTestResultAdapter = DdmlibTestResultAdapter(
                        deviceConnector.name,
                        CustomTestRunListener(
                                deviceConnector.name,
                                projectName,
                                variantName,
                                logger).apply {
                            setReportDir(resultsDir)
                        }
                )
                testResultReporters[deviceConnector.serialNumber] = object: UtpTestResultListener {
                    override fun onTestResultEvent(testResultEvent: TestResultEvent) {
                        ddmlibTestResultAdapter.onTestResultEvent(testResultEvent)

                        if (testResultEvent.hasTestSuiteFinished()) {
                            resultsProto = testResultEvent.testSuiteFinished.testSuiteResult
                                    .unpack(TestSuiteResult::class.java)
                        }
                    }
                }

                runUtpTestSuite(
                    runnerConfigProtoFile,
                    configFactory,
                    utpDependencies,
                    workQueue)

                val postProcessFunc: () -> TestResult = {
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
                    logger.quiet(
                        "\nTest results saved as ${testResultPbFile.toURI()}. " +
                                "Inspect these results in Android Studio by selecting Run > Import Tests " +
                                "From File from the menu bar and importing test-result.pb."
                    )
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
                }
                postProcessFunc
            }

            workQueue.await()

            return postProcessCallback.map {
                it()
            }.toMutableList()
        }
    }

    @Synchronized
    override fun onTestResultEvent(testResultEvent: TestResultEvent) {
        testResultReporters[testResultEvent.deviceId]?.onTestResultEvent(testResultEvent)
        utpTestResultListener?.onTestResultEvent(testResultEvent)
    }
}
