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
import com.android.build.gradle.internal.testing.utp.UtpDependency.CORE
import com.android.build.gradle.internal.testing.utp.UtpDependency.LAUNCHER
import com.android.builder.testing.api.DeviceConnector
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.common.io.Files
import com.google.protobuf.TextFormat
import com.google.test.platform.core.proto.TestStatusProto
import com.google.test.platform.core.proto.TestSuiteResultProto
import org.gradle.api.artifacts.ConfigurationContainer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

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
        private val configurations: ConfigurationContainer,
        private val sdkComponents: SdkComponentsBuildService,
        private val usesIcebox: Boolean,
        private val configFactory: UtpConfigFactory = UtpConfigFactory())
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
        return apksForDevice.map { (deviceConnector, apks) ->
            val utpOutputDir = Files.createTempDir()
            val utpTmpDir = Files.createTempDir()
            val utpTestLogDir = Files.createTempDir()
            val utpTestRunLogDir = Files.createTempDir()
            val runnerConfigProtoFile = File.createTempFile("runnerConfig", ".pb").also { file ->
                FileOutputStream(file).use { writer ->
                    configFactory.createRunnerConfigProto(
                            deviceConnector,
                            testData,
                            apks.union(helperApks) + testData.testApk,
                            configurations,
                            sdkComponents,
                            utpOutputDir,
                            utpTmpDir,
                            utpTestLogDir,
                            utpTestRunLogDir,
                            usesIcebox).writeTo(writer)
                }
            }
            val serverConfigProtoFile = File.createTempFile("serverConfig", ".pb").also { file ->
                FileOutputStream(file).use { writer ->
                    configFactory.createServerConfigProto().writeTo(writer)
                }
            }
            val loggingPropertiesFile = File.createTempFile("logging", "properties").also { file ->
                Files.asCharSink(file, Charsets.UTF_8).write("""
                    .level=SEVERE
                    .handlers=java.util.logging.ConsoleHandler
                    java.util.logging.ConsoleHandler.level=SEVERE
                """.trimIndent())
            }
            val javaProcessInfo = ProcessInfoBuilder().apply {
                setClasspath(configurations.getByName(LAUNCHER.configurationName).singleFile.absolutePath)
                setMain(LAUNCHER.mainClass)
                addArgs(configurations.getByName(CORE.configurationName).singleFile.absolutePath)
                addArgs("--proto_config=${runnerConfigProtoFile.absolutePath}")
                addArgs("--proto_server_config=${serverConfigProtoFile.absolutePath}")
                addJvmArg("-Djava.util.logging.config.file=${loggingPropertiesFile.absolutePath}")
            }.createJavaProcess()

            javaProcessExecutor.execute(javaProcessInfo, LoggedProcessOutputHandler(logger))
            val resultsProto = getResultsProto(utpOutputDir)

            try {
                FileUtils.deleteRecursivelyIfExists(utpOutputDir)
                FileUtils.deleteRecursivelyIfExists(utpTestLogDir)
                FileUtils.deleteRecursivelyIfExists(utpTestRunLogDir)
                FileUtils.deleteRecursivelyIfExists(utpTmpDir)
            } catch (e: IOException) {
                logger.warning("Failed to cleanup temporary directories: $e")
            }

            createTestReportXml(resultsProto, deviceConnector.name, projectName, variantName, logger, resultsDir)
            val testFailed = resultsProto.testResultList.any { testCaseResult ->
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

    /**
     * Creates JUnit test report XML file based on information in the results proto.
     */
    private fun createTestReportXml(resultsProto: TestSuiteResultProto.TestSuiteResult,
                                    deviceName: String,
                                    projectName: String,
                                    flavorName: String,
                                    logger: ILogger,
                                    reportOutputDir: File) {
        CustomTestRunListener(deviceName, projectName, flavorName, logger).apply {
            setReportDir(reportOutputDir)
            var numTestFails = 0
            var totalElapsedTimeMillis = 0L
            testRunStarted(deviceName, resultsProto.testResultCount)
            resultsProto.testResultList.forEach { testResult ->
                val testId = TestIdentifier(
                        "${testResult.testCase.testPackage}.${testResult.testCase.testClass}",
                        testResult.testCase.testMethod)
                testStarted(testId)
                when(testResult.testStatus) {
                    TestStatusProto.TestStatus.FAILED, TestStatusProto.TestStatus.ERROR -> {
                        testFailed(testId, testResult.error.stackTrace)
                        ++numTestFails
                    }
                    TestStatusProto.TestStatus.IGNORED -> {
                        testIgnored(testId)
                    }
                    else -> {}
                }
                testEnded(testId, mapOf())

                val startTimeMillis =
                        testResult.testCase.startTime.seconds * 1000L + testResult.testCase.startTime.nanos / 1000000L
                val endTimeMillis =
                        testResult.testCase.endTime.seconds * 1000L + testResult.testCase.endTime.nanos / 1000000L
                runResult.testResults.getValue(testId).apply {
                    startTime = startTimeMillis
                    endTime = endTimeMillis
                }
                totalElapsedTimeMillis += endTimeMillis - startTimeMillis
            }
            if (numTestFails > 0) {
                testRunFailed("There was ${numTestFails} failure(s).")
            }
            testRunEnded(totalElapsedTimeMillis, mapOf())
        }
    }

    /**
     * Retrieves a test suite result proto from the Unified Test Platform's output directory.
     */
    private fun getResultsProto(outputDir: File): TestSuiteResultProto.TestSuiteResult {
        return TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
            TextFormat.merge(
                    InputStreamReader(FileInputStream(File(outputDir, TEST_RESULT_OUTPUT_FILE_NAME))),
                    this)
        }.build()
    }
}