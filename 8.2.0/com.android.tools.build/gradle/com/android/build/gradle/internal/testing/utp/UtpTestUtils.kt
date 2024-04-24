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

import com.android.build.api.dsl.TestOptions
import com.android.build.gradle.internal.testing.CustomTestRunListener
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.testing.api.DeviceConnector
import com.android.prefs.AndroidLocationsSingleton
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.ErrorDetailProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.service.ServerConfigProto
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

private const val UNKNOWN_PLATFORM_ERROR_MESSAGE =
    "Unknown platform error occurred when running the UTP test suite. Please check logs for details."

/**
 * Encapsulates necessary information to run tests using Unified Test Platform.
 *
 * @property deviceName a displayable device name
 * @property deviceId an identifier for a device
 * @property utpOutputDir a path to the directory to store results from UTP
 * @property runnerConfig a function that constructs and returns UTP runner config proto
 * @property serverConfig a UTP server config proto
 * @property shardConfig an information about test sharding, or null if sharding is not enabled
 */
data class UtpRunnerConfig(
    val deviceName: String,
    val deviceId: String,
    val utpOutputDir: File,
    val runnerConfig: (
        UtpTestResultListenerServerMetadata,
        utpTmpDir: File,
    ) -> RunnerConfigProto.RunnerConfig,
    val serverConfig: ServerConfigProto.ServerConfig,
    val shardConfig: ShardConfig? = null,
    val utpLoggingLevel: Level = Level.WARNING,
)

/**
 * @property sdkApkSet the privacy sandbox SDK APK
 * @property extractedApks extracted APks from the privacy sandbox SDK APK to install during test
 */
data class PrivacySandboxSdkInstallBundle(
    val sdkApkSet: Set<File>,
    val extractedApkMap: Map<DeviceConnector, List<List<Path>>>
)

/**
 * Encapsulates installation configuration for app APKs
 */
data class TargetApkConfigBundle (
    val appApks: Iterable<File>,
    val isSplitApk: Boolean
)

fun UtpRunnerConfig.shardName(): String {
    return if (shardConfig == null) {
        deviceName
    } else {
        "${deviceName}_${shardConfig.index}"
    }
}

/**
 * Encapsulates result of a UTP test run.
 *
 * @property testPassed true when all test cases in the test suite is passed.
 * @property resultsProto test suite result protobuf message. This can be null if
 *     UTP exits unexpectedly.
 */
data class UtpTestRunResult(
    val testPassed: Boolean,
    val resultsProto: TestSuiteResultProto.TestSuiteResult?,
)

/**
 * Runs the given runner configs using Unified Test Platform. Test results are reported
 * though [utpTestResultListener] streamingly.
 */
fun runUtpTestSuiteAndWait(
    runnerConfigs: List<UtpRunnerConfig>,
    workerExecutor: WorkerExecutor,
    projectPath: String,
    variantName: String,
    resultsDir: File,
    logger: ILogger,
    utpTestResultListener: UtpTestResultListener?,
    utpDependencies: UtpDependencies,
    utpTestResultListenerServerRunner: (UtpTestResultListener?) -> UtpTestResultListenerServerRunner = {
        UtpTestResultListenerServerRunner(it)
    }
): List<UtpTestRunResult> {
    val workQueue = workerExecutor.noIsolation()

    val testResultReporters: ConcurrentHashMap<String, UtpTestResultListener> = ConcurrentHashMap()
    val testResultListener = object : UtpTestResultListener {
        @Synchronized
        override fun onTestResultEvent(testResultEvent: GradleAndroidTestResultListenerProto.TestResultEvent) {
            testResultReporters[testResultEvent.deviceId]?.onTestResultEvent(testResultEvent)
            utpTestResultListener?.onTestResultEvent(testResultEvent)
        }
    }

    utpTestResultListenerServerRunner(testResultListener).use { resultListenerServerRunner ->
        val resultListenerServerMetadata = resultListenerServerRunner.metadata

        val postProcessCallback = runnerConfigs.map { config ->
            var resultsProto: TestSuiteResultProto.TestSuiteResult? = null
            val ddmlibTestResultAdapter = DdmlibTestResultAdapter(
                config.deviceName,
                CustomTestRunListener(
                    config.shardName(),
                    projectPath,
                    variantName,
                    logger).apply {
                    setReportDir(resultsDir)
                }
            )
            testResultReporters[config.deviceId] = object: UtpTestResultListener {
                override fun onTestResultEvent(testResultEvent: GradleAndroidTestResultListenerProto.TestResultEvent) {
                    ddmlibTestResultAdapter.onTestResultEvent(testResultEvent)

                    if (testResultEvent.hasTestSuiteFinished()) {
                        resultsProto = testResultEvent.testSuiteFinished.testSuiteResult
                            .unpack(TestSuiteResultProto.TestSuiteResult::class.java)
                    }
                }
            }

            runUtpTestSuite(
                config,
                resultListenerServerMetadata,
                utpDependencies,
                workQueue)

            val postProcessFunc: () -> UtpTestRunResult = {
                testResultReporters.remove(config.deviceId)

                val resultsProto = resultsProto
                val testPassed = if (resultsProto != null) {
                    File(config.utpOutputDir, TEST_RESULT_PB_FILE_NAME).outputStream().use {
                        resultsProto.writeTo(it)
                    }
                    val testSuitePassed = resultsProto.testStatus.isPassedOrSkipped()
                    val hasAnyFailedTestCase = resultsProto.testResultList.any { testCaseResult ->
                        !testCaseResult.testStatus.isPassedOrSkipped()
                    }
                    testSuitePassed && !hasAnyFailedTestCase && !resultsProto.hasPlatformError()
                } else {
                    logger.error(null, "Failed to receive the UTP test results")
                    false
                }

                UtpTestRunResult(testPassed, resultsProto)
            }
            postProcessFunc
        }

        workQueue.await()

        return postProcessCallback.map {
            it()
        }.toList()
    }
}

private fun TestStatus.isPassedOrSkipped(): Boolean {
    return when (this) {
        TestStatus.PASSED,
        TestStatus.IGNORED,
        TestStatus.SKIPPED -> true
        else -> false
    }
}

/**
 * Runs the given runner config using Unified Test Platform.
 */
private fun runUtpTestSuite(
    config: UtpRunnerConfig,
    resultListenerServerMetadata: UtpTestResultListenerServerMetadata,
    utpDependencies: UtpDependencies,
    workQueue: WorkQueue
) {
    val utpRunTempDir = createUtpTempDirectory("utpRunTemp")
    val runnerConfigProtoFile = createUtpTempFile("runnerConfig", ".pb").also { file ->
        FileOutputStream(file).use { writer ->
            config.runnerConfig(resultListenerServerMetadata, utpRunTempDir).writeTo(writer)
        }
    }
    val serverConfigProtoFile = createUtpTempFile("serverConfig", ".pb").also { file ->
        FileOutputStream(file).use { writer ->
            config.serverConfig.writeTo(writer)
        }
    }
    val loggingPropertiesFile = createUtpTempFile("logging", "properties").also { file ->
        Files.asCharSink(file, Charsets.UTF_8).write("""
                .level=${config.utpLoggingLevel.getName()}
                .handlers=java.util.logging.ConsoleHandler
                java.util.logging.ConsoleHandler.level=${config.utpLoggingLevel.getName()}
            """.trimIndent())
    }
    workQueue.submit(RunUtpWorkAction::class.java) { params ->
        params.launcherJar.set(utpDependencies.launcher.singleFile)
        params.coreJar.set(utpDependencies.core.singleFile)
        params.runnerConfig.set(runnerConfigProtoFile)
        params.serverConfig.set(serverConfigProtoFile)
        params.loggingProperties.set(loggingPropertiesFile)
    }
}

/**
 * Creates an empty temporary file for UTP in Android Preference directory.
 */
fun createUtpTempFile(fileNamePrefix: String, fileNameSuffix: String): File {
    val utpPrefRootDir = getUtpPreferenceRootDir()
    return File.createTempFile(fileNamePrefix, fileNameSuffix, utpPrefRootDir).apply {
        deleteOnExit()
    }
}

/**
 * Creates an empty temporary directory for UTP in Android Preference directory.
 */
fun createUtpTempDirectory(dirNamePrefix: String): File {
    val utpPrefRootDir = getUtpPreferenceRootDir()
    return java.nio.file.Files.createTempDirectory(
        utpPrefRootDir.toPath(), dirNamePrefix).toFile().apply {
        deleteOnExit()
    }
}

/**
 * Returns the UTP preference root directory. Typically it is "~/.android/utp". If the preference
 * directory dosen't exist, it creates and returns it.
 */
fun getUtpPreferenceRootDir(): File {
    val utpPrefRootDir = File(AndroidLocationsSingleton.prefsLocation.toFile(), "utp")
    if (!utpPrefRootDir.exists()) {
        utpPrefRootDir.mkdirs()
    }
    return utpPrefRootDir
}

/**
 * Returns true if the root cause of the Platform error is the EmulatorTimeoutException.
 */
fun hasEmulatorTimeoutException(resultsProto: TestSuiteResultProto.TestSuiteResult?): Boolean {
    resultsProto ?: return false
    return resultsProto.platformError.errorsList.any(::hasEmulatorTimeoutException)
}

private fun hasEmulatorTimeoutException(error: ErrorDetailProto.ErrorDetail): Boolean {
    return when {
        getExceptionFromStackTrace(error.summary.stackTrace)
            .contains("EmulatorTimeoutException") -> true
        error.hasCause() -> hasEmulatorTimeoutException(error.cause)
        else -> false
    }
}

/**
 * Finds the root cause of the Platform Error and returns the error message.
 */
fun getPlatformErrorMessage(resultsProto: TestSuiteResultProto.TestSuiteResult?): String {
    resultsProto ?: return UNKNOWN_PLATFORM_ERROR_MESSAGE
    return resultsProto.platformError.errorsList.joinToString(
        "\n", transform = ::getPlatformErrorMessage)
}

/**
 * Finds the root cause of the Platform Error and returns the error message.
 *
 * @param error the top level error detail to be analyzed.
 */
private fun getPlatformErrorMessage(
    error : ErrorDetailProto.ErrorDetail,
    errorMessageBuilder: StringBuilder = StringBuilder()) : StringBuilder {
    if (error.hasCause()) {
        if (error.summary.errorMessage.isNotBlank()) {
            errorMessageBuilder.append("${error.summary.errorMessage}\n")
        }
        getPlatformErrorMessage(error.cause, errorMessageBuilder)
    } else {
        if (error.summary.errorMessage.isNotBlank()) {
            errorMessageBuilder.append("${error.summary.errorMessage}\n")
        } else {
            errorMessageBuilder.append("$UNKNOWN_PLATFORM_ERROR_MESSAGE\n")
        }
        errorMessageBuilder.append(error.summary.stackTrace)
    }
    return errorMessageBuilder
}

/**
 * Attempts to get a simple string by which the exception can be easily parsed.
 *
 * Due to the nature of UTP error details, the exception may be in a serialized string format, or
 * in a simple toString() format. This method is meant to separate the parent exception from the
 * stacktrace (which may include more exceptions)
 *
 * @param stackTrace the stackTrace of the exception in either serialized or toString() format
 * @return A simple string, that the only exception that is contained is the top-level exception.
 */
private fun getExceptionFromStackTrace(stackTrace: String): String {
    val endIndex = stackTrace.indexOf(':')
    return if (endIndex >= 0) {
        stackTrace.substring(0, endIndex)
    } else {
        stackTrace.lineSequence().firstOrNull() ?: ""
    }
}
