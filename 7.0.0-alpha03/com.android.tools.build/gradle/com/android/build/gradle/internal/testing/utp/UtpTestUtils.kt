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

import com.android.build.gradle.internal.testing.CustomTestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.LoggedProcessOutputHandler
import com.android.ide.common.process.ProcessInfoBuilder
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * Runs the given runner config on the Unified Test Platform server.
 *
 * Returns the result of the UTP execution from the given output directory.
 */
internal fun runUtpTestSuite(
    runnerConfigFile: File,
    utpOutputDir: File,
    configFactory: UtpConfigFactory,
    utpDependencies: UtpDependencies,
    javaProcessExecutor: JavaProcessExecutor,
    logger: ILogger
): TestSuiteResultProto.TestSuiteResult {

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
        setClasspath(utpDependencies.launcher.singleFile.absolutePath)
        setMain(UtpDependency.LAUNCHER.mainClass)
        addArgs(utpDependencies.core.singleFile.absolutePath)
        addArgs("--proto_config=${runnerConfigFile.absolutePath}")
        addArgs("--proto_server_config=${serverConfigProtoFile.absolutePath}")
        addJvmArg("-Djava.util.logging.config.file=${loggingPropertiesFile.absolutePath}")
    }.createJavaProcess()

    javaProcessExecutor.execute(javaProcessInfo, LoggedProcessOutputHandler(logger))
    return getResultsProto(utpOutputDir)
}

/**
 * Retrieves a test suite result proto from the Unified Test Platform's output directory.
 */
internal fun getResultsProto(outputDir: File): TestSuiteResultProto.TestSuiteResult {
    return TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
        TextFormat.merge(
            InputStreamReader(FileInputStream(File(outputDir, TEST_RESULT_OUTPUT_FILE_NAME))),
            this)
    }.build()
}

/**
 * Creates JUnit test report XML file based on information in the results proto.
 */
internal fun createTestReportXml(
    resultsProto: TestSuiteResultProto.TestSuiteResult,
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
            testRunFailed("There was $numTestFails failure(s).")
        }
        testRunEnded(totalElapsedTimeMillis, mapOf())
    }
}
