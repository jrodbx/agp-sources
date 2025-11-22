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

import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkParameters
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.testing.api.DeviceConnector
import com.android.prefs.AndroidLocationsSingleton
import com.android.utils.ILogger
import com.google.common.io.Files
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import com.google.testing.platform.proto.api.core.ErrorDetailProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.logging.Level

const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

private const val UNKNOWN_PLATFORM_ERROR_MESSAGE =
    "Unknown platform error occurred when running the UTP test suite. Please check logs for details."

/**
 * Encapsulates necessary information to run tests using Unified Test Platform.
 *
 * @property jvm the JAVA environment to run UTP on.
 * @property deviceName a displayable device name
 * @property deviceId an identifier for a device
 * @property utpOutputDir a path to the directory to store results from UTP
 * @property runnerConfig a function that constructs and returns UTP runner config proto
 * @property shardConfig an information about test sharding, or null if sharding is not enabled
 */
data class UtpRunnerConfig(
    val deviceName: String,
    val deviceId: String,
    val utpOutputDir: File,
    val runnerConfig: RunnerConfigProto.RunnerConfig,
    val shardConfig: ShardConfig? = null,
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
    objectFactory: ObjectFactory,
    jvmExecutable: File,
    projectPath: String,
    variantName: String,
    resultsDir: File,
    logger: ILogger,
    utpDependencies: UtpDependencies,
    utpLoggingLevel: Level,
): List<UtpTestRunResult> {
    val utpTestResultProtoFiles = runUtpTestSuiteAndWait(
        workerExecutor,
        objectFactory,
        runnerConfigs,
        utpDependencies,
        utpLoggingLevel,
        jvmExecutable,
        projectPath,
        variantName,
        resultsDir,
    )

    return utpTestResultProtoFiles.map { protoFile ->
        if (protoFile.exists()) {
            protoFile.inputStream().use {
                TestSuiteResultProto.TestSuiteResult.parseFrom(it)
            }
        } else {
            null
        }
    }.map { resultProto ->
        val testPassed = if (resultProto != null) {
            val testSuitePassed = resultProto.testStatus.isPassedOrSkipped()
            val hasAnyFailedTestCase = resultProto.testResultList.any { testCaseResult ->
                !testCaseResult.testStatus.isPassedOrSkipped()
            }
            testSuitePassed && !hasAnyFailedTestCase && !resultProto.hasPlatformError()
        } else {
            logger.error(null, "Failed to receive the UTP test results")
            false
        }

        UtpTestRunResult(testPassed, resultProto)
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
private fun runUtpTestSuiteAndWait(
    workerExecutor: WorkerExecutor,
    objectFactory: ObjectFactory,
    configs: List<UtpRunnerConfig>,
    utpDependencies: UtpDependencies,
    utpLoggingLevel: Level,
    jvmExecutable: File,
    projectPath: String,
    variantName: String,
    xmlTestReportOutputDirectory: File,
): List<File> {
    val utpRunConfigs = configs.map { config ->
        val runnerConfigProtoFile = createUtpTempFile("runnerConfig", ".pb").also { file ->
            FileOutputStream(file).use { writer ->
                config.runnerConfig.writeTo(writer)
            }
        }

        val utpRunConfig = objectFactory.newInstance(RunUtpWorkParameters.UtpRunConfig::class.java)

        utpRunConfig.runnerConfigFile.set(runnerConfigProtoFile)
        utpRunConfig.runnerConfigFile.disallowChanges()

        val loggingPropertiesFile = createUtpTempFile("logging", "properties").also { file ->
            Files.asCharSink(file, Charsets.UTF_8).write("""
                .level=INFO
                .handlers=java.util.logging.ConsoleHandler,java.util.logging.FileHandler
                java.util.logging.ConsoleHandler.level=${utpLoggingLevel.name}
                java.util.logging.SimpleFormatter.format=%4${'$'}s: %5${'$'}s%n
                java.util.logging.FileHandler.level=INFO
                java.util.logging.FileHandler.pattern=${config.utpOutputDir.invariantSeparatorsPath}/utp.%u.log
                java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter
            """.trimIndent())
        }
        utpRunConfig.loggingPropertiesFile.set(loggingPropertiesFile)
        utpRunConfig.loggingPropertiesFile.disallowChanges()

        utpRunConfig.deviceId.setDisallowChanges(config.deviceId)
        utpRunConfig.deviceName.setDisallowChanges(config.deviceName)
        utpRunConfig.deviceShardName.setDisallowChanges(config.shardName())

        utpRunConfig.utpResultProtoOutputFile.set(
            File(config.utpOutputDir, TEST_RESULT_PB_FILE_NAME))
        utpRunConfig.utpResultProtoOutputFile.disallowChanges()

        utpRunConfig
    }

    val workQueue = workerExecutor.noIsolation()

    workQueue.submit(RunUtpWorkAction::class.java) { params ->
        params.jvm.set(jvmExecutable)
        params.utpRunConfigs.setDisallowChanges(utpRunConfigs)
        params.utpDependencies.setDisallowChanges(utpDependencies)
        params.projectPath.setDisallowChanges(projectPath)
        params.variantName.setDisallowChanges(variantName)
        params.xmlTestReportOutputDirectory.fileValue(xmlTestReportOutputDirectory).disallowChanges()
    }

    workQueue.await()

    return configs.map { File(it.utpOutputDir, TEST_RESULT_PB_FILE_NAME) }
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
