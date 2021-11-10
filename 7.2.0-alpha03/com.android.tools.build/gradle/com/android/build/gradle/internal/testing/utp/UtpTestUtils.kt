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

import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.testing.CustomTestRunListener
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import com.android.prefs.AndroidLocationsSingleton
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.ErrorDetailProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.service.ServerConfigProto
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.math.min
import org.gradle.api.logging.Logging
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

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
): List<Boolean> {
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
                    if (config.shardConfig == null) {
                        config.deviceName
                    } else {
                        "${config.deviceName}_${config.shardConfig.index}"
                    },
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

            val postProcessFunc: () -> Boolean = {
                testResultReporters.remove(config.deviceId)

                val resultsProto = resultsProto
                val testPassed = if (resultsProto != null) {
                    val testResultPbFile = File(config.utpOutputDir, "test-result.pb")
                    resultsProto.writeTo(testResultPbFile.outputStream())
                    logger.quiet(
                        "\nTest results saved as ${testResultPbFile.toURI()}. " +
                                "Inspect these results in Android Studio by selecting Run > Import Tests " +
                                "From File from the menu bar and importing test-result.pb."
                    )
                    if (resultsProto.hasPlatformError()) {
                        logger.error(
                            null, getPlatformErrorMessage(resultsProto.platformError.errorDetail))
                    }
                    val testFailed = resultsProto.hasPlatformError() ||
                            resultsProto.testResultList.any { testCaseResult ->
                                testCaseResult.testStatus == TestStatusProto.TestStatus.FAILED
                                        || testCaseResult.testStatus == TestStatusProto.TestStatus.ERROR
                            }
                    !testFailed
                } else {
                    logger.error(null, "Failed to receive the UTP test results")
                    false
                }

                testPassed
            }
            postProcessFunc
        }

        workQueue.await()

        return postProcessCallback.map {
            it()
        }.toList()
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
 * Returns true when UTP should be enabled, false otherwise.
 *
 * @param variantType a variant type of the tested APK. Null when it is unknown.
 */
fun shouldEnableUtp(
    projectOptions: ProjectOptions,
    testOptions: TestOptions?,
    variantType: VariantType?,
): Boolean {
    if (projectOptions[BooleanOption.ENABLE_TEST_SHARDING]) {
        Logging.getLogger("UtpTestUtils").warn(
            "Disabling ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM option because " +
                    "ENABLE_TEST_SHARDING is specified. ENABLE_TEST_SHARDING is not " +
                    "supported by ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM yet.")
        return false
    }
    if (variantType != null && variantType.isDynamicFeature) {
        Logging.getLogger("UtpTestUtils").warn(
            "Disabling ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM option because " +
                    "this is a dynamic-feature module. Dynamic-feature module is not " +
                    "supported by ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM yet.")
        return false
    }
    return (projectOptions[BooleanOption.ANDROID_TEST_USES_UNIFIED_TEST_PLATFORM]
            || (testOptions != null && testOptions.emulatorSnapshots.enableForTestFailures))
}

/**
 * Finds the root cause of the Platform Error if it came form the Managed Device
 * Android Provider.
 *
 * @param error the top level error detail to be analyzed.
 * @return if [error] is from the managed device plugin, the message of the error is returned. Will
 * otherwise check the cause of the error detail. If no cause is from the managed device plugin a
 * default error message is returned.
 */
private fun getPlatformErrorMessage(error : ErrorDetailProto.ErrorDetail) : String =
    when {
        getExceptionFromStackTrace(error.summary.stackTrace).contains("EmulatorTimeoutException") ->
            """
                PLATFORM ERROR:
                ${error.summary.errorMessage}
            """.trimIndent()
        error.hasCause() ->
            getPlatformErrorMessage(error.cause)
        else -> """
            Unknown platform error occurred when running the UTP test suite. Please check logs for details.
        """.trimIndent()
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
private fun getExceptionFromStackTrace(stackTrace: String): String =
    stackTrace.substring(0..min(stackTrace.indexOf(':'), stackTrace.indexOf('\n')))
