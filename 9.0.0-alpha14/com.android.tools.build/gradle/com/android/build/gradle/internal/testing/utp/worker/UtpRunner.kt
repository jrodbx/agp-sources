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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.testing.CustomTestRunListener
import com.android.build.gradle.internal.testing.utp.DdmlibTestResultAdapter
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.internal.testing.utp.UtpDependency
import com.android.build.gradle.internal.testing.utp.UtpTestResultListener
import com.android.build.gradle.internal.testing.utp.UtpTestResultListenerServerRunner
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.android.utils.GrabProcessOutput
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.util.Base64
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs UTP test suites in external Java processes.
 *
 * This class is designed to be used within a Gradle WorkAction. It handles the
 * entire lifecycle of running UTP, including:
 * 1. Launching all UTP processes in parallel using an [ExecutorService].
 * 2. Collecting results via the utpTestResultListenerServer, which generates XML reports and writes
 *    the final `test-result.pb` file.
 *
 * @param javaExecFile The path to the `java` executable.
 * @param utpDependencies Resolved UTP dependency artifacts.
 * @param enableUtpTestReportingForAndroidStudio If true, test results are also printed
 * to stdout in a base64-encoded format for Android Studio to parse.
 * @param utpTestResultListenerServer The server that listens for UTP test results. This runner
 * is responsible for setting the listener on this server, but not for managing its lifecycle.
 * @param logger A Gradle logger instance.
 * @param processBuilderFactory A factory function to create [ProcessBuilder] instances.
 * @param executorServiceFactory A factory function to create the [ExecutorService]
 * used for parallel process execution.
 */
class UtpRunner(
    private val javaExecFile: File,
    private val utpDependencies: UtpDependencies,
    private val enableUtpTestReportingForAndroidStudio: Boolean,
    private val utpTestResultListenerServer: UtpTestResultListenerServerRunner,
    private val logger: Logger = Logging.getLogger(UtpRunner::class.java),
    private val processBuilderFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) },
    private val executorServiceFactory: () -> ExecutorService = Executors::newCachedThreadPool,
) {
    /**
     * Executes all UTP test runs.
     *
     * This method orchestrates the entire test execution. It starts the result listener
     * server, configures all test runs to report to that server, and then executes
     * them in parallel, waiting for all to complete.
     *
     * @param utpRunnerConfigFileList List of base UTP runner config files (one per shard/run).
     * @param loggingPropertiesFileList List of logging properties files (one per shard/run).
     * @param deviceIDs List of device IDs, used to tag results and create listener plugins.
     * @param deviceNames List of device names, used for XML report generation.
     * @param deviceShardNames List of shard names, used for XML report generation.
     * @param projectPath The Gradle project path, passed to the XML report listener.
     * @param variantName The Gradle variant name, passed to the XML report listener.
     * @param xmlTestReportOutputDirectory The final directory for the `TEST-*.xml` reports.
     * @param utpResultProtoOutputFileList List of file paths where the final
     * `test-result.pb` for each run should be written.
     */
    fun execute(
        utpRunnerConfigFileList: List<File>,
        loggingPropertiesFileList: List<File>,
        deviceIDs: List<String>,
        deviceNames: List<String>,
        deviceShardNames: List<String>,
        projectPath: String,
        variantName: String,
        xmlTestReportOutputDirectory: File,
        utpResultProtoOutputFileList: List<File>,
    ) {
        val xmlReportCreators = deviceIDs.withIndex().associate { (i, deviceID) ->
            val ddmlibTestResultAdapter = DdmlibTestResultAdapter(
                deviceNames[i],
                CustomTestRunListener(
                    deviceShardNames[i],
                    projectPath,
                    variantName,
                    LoggerWrapper(logger)
                ).apply { setReportDir(xmlTestReportOutputDirectory) }
            )
            deviceID to ddmlibTestResultAdapter
        }

        val utpProtoFileMap = deviceIDs.zip(utpResultProtoOutputFileList).toMap()

        utpTestResultListenerServer.setListener(object: UtpTestResultListener {
            override fun onTestResultEvent(testResultEvent: GradleAndroidTestResultListenerProto.TestResultEvent) {
                xmlReportCreators[testResultEvent.deviceId]?.onTestResultEvent(testResultEvent)
                if (testResultEvent.hasTestSuiteFinished()) {
                    val resultProto = testResultEvent.testSuiteFinished.testSuiteResult
                        .unpack(TestSuiteResultProto.TestSuiteResult::class.java)
                    utpProtoFileMap[testResultEvent.deviceId]?.let { outputFile ->
                        outputFile.outputStream().use { outputFileStream ->
                            resultProto.writeTo(outputFileStream)
                        }
                    }
                }

                if (enableUtpTestReportingForAndroidStudio) {
                    println(
                        "<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>" +
                        Base64.getEncoder().encodeToString(testResultEvent.toByteArray()) +
                        "</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
                    )
                }
            }
        })

        execute(utpRunnerConfigFileList, loggingPropertiesFileList)
    }

    /**
     * Executes all the configured UTP test suites in parallel.
     *
     * This private method launches the external Java processes, each configured with a
     * runner config and logging properties. It waits for all processes to complete.
     *
     * @param utpRunnerConfigFileList List of base UTP runner config files (one per shard/run).
     * @param loggingPropertiesFileList List of logging properties files (one per shard/run).
     * @throws GradleException if any UTP process fails or an exception occurs.
     */
    private fun execute(
        utpRunnerConfigFileList: List<File>,
        loggingPropertiesFileList: List<File>,
    ) {
        val executorService = executorServiceFactory()
        try {
            val futures = utpRunnerConfigFileList.zip(loggingPropertiesFileList)
                .map { (runConfig, loggingPropertiesFile) ->
                    executorService.submit {
                        execute(runConfig, loggingPropertiesFile)
                    }
                }.toList()

            var hasExecutionFailures = false
            futures.forEach {
                try {
                    it.get()
                } catch (e: ExecutionException) {
                    logger.warn("Test Execution failed", e)
                    hasExecutionFailures = true
                }
            }

            if (hasExecutionFailures) {
                throw GradleException("Test Execution failed")
            }
        } finally {
            executorService.shutdownNow()
            executorService.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    /**
     * Executes a single UTP process for a given configuration.
     *
     * This method blocks until the external UTP process terminates. It streams the process output
     * to the logger. If the Gradle build is cancelled, the [InterruptedException] is caught,
     * the process is forcibly destroyed, and the exception is re-thrown to signal cancellation.
     *
     * @param utpRunnerConfigFile The runner configuration proto file for this specific UTP execution.
     * @param loggingPropertiesFile The logging properties file for this specific UTP execution.
     */
    private fun execute(utpRunnerConfigFile: File, loggingPropertiesFile: File) {
        val processBuilder = processBuilderFactory(
            listOf(
                javaExecFile.absolutePath,
                "-Djava.awt.headless=true",
                "-Djava.util.logging.config.file=${loggingPropertiesFile.absolutePath}",
                "-Dfile.encoding=UTF-8",
                "-cp",
                utpDependencies.launcher.files.joinToString(File.pathSeparator) { it.absolutePath },
                UtpDependency.LAUNCHER.mainClass,
                utpDependencies.core.files.joinToString(File.pathSeparator) { it.absolutePath },
                "--proto_config=${utpRunnerConfigFile.absolutePath}",
            )
        )
        val process = processBuilder.start()

        // Shutdown hook to close the process if gradle is closed/cancelled.
        val shutdownHook = Thread(process::destroyForcibly)

        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook)

            try {
                GrabProcessOutput.grabProcessOutput(
                    process,
                    GrabProcessOutput.Wait.WAIT_FOR_READERS,
                    object : GrabProcessOutput.IProcessOutput {
                        override fun out(line: String?) {
                            if (!line.isNullOrBlank()) {
                                logger.info(line)
                            }
                        }

                        override fun err(line: String?) {
                            if (!line.isNullOrBlank()) {
                                logger.info(line)
                            }
                        }
                    }, null, null
                )
            } catch (e: InterruptedException) {
                process.destroyForcibly()
                process.waitFor()

                // Re-throw the exception so Gradle knows the work was cancelled.
                Thread.currentThread().interrupt()
                throw e
            }
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}
