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
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkParameters
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.testing.api.DeviceConnector
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.BuildToolInfo
import com.android.utils.ILogger
import com.google.testing.platform.proto.api.core.ErrorDetailProto
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.nio.file.Path

const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

private const val UNKNOWN_PLATFORM_ERROR_MESSAGE =
    "Unknown platform error occurred when running the UTP test suite. Please check logs for details."

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
    val appApks: List<File>,
    val isSplitApk: Boolean
) : Serializable

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
 * Runs the given runner configs using Unified Test Platform.
 */
fun runUtpTestSuiteAndWait(
    runnerConfigs: List<RunUtpWorkParameters.UtpRunConfig>,
    workerExecutor: WorkerExecutor,
    jvmExecutable: File,
    projectPath: String,
    variantName: String,
    resultsDir: File,
    logger: ILogger,
    utpDependencies: UtpDependencies,
    versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
): List<UtpTestRunResult> {
    val utpTestResultProtoFiles = runUtpTestSuiteAndWait(
        workerExecutor,
        runnerConfigs,
        utpDependencies,
        jvmExecutable,
        projectPath,
        variantName,
        resultsDir,
        versionedSdkLoader,
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
    configs: List<RunUtpWorkParameters.UtpRunConfig>,
    utpDependencies: UtpDependencies,
    jvmExecutable: File,
    projectPath: String,
    variantName: String,
    xmlTestReportOutputDirectory: File,
    versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
): List<File> {
    val workQueue = workerExecutor.noIsolation()

    workQueue.submit(RunUtpWorkAction::class.java) { params ->
        params.jvm.set(jvmExecutable)
        params.utpRunConfigs.setDisallowChanges(configs)
        params.utpDependencies.setDisallowChanges(utpDependencies)
        params.projectPath.setDisallowChanges(projectPath)
        params.variantName.setDisallowChanges(variantName)
        params.xmlTestReportOutputDirectory.fileValue(xmlTestReportOutputDirectory).disallowChanges()
        params.androidSdkDirectory.setDisallowChanges(versionedSdkLoader.sdkDirectoryProvider)
        params.adbExecutable.setDisallowChanges(versionedSdkLoader.adbExecutableProvider)
        params.aaptExecutable.fileValue(
            File(versionedSdkLoader.buildToolInfoProvider.get()
                .getPath(BuildToolInfo.PathId.AAPT))).disallowChanges()
        params.dexdumpExecutable.fileValue(
            File(versionedSdkLoader.buildToolInfoProvider.get()
                .getPath(BuildToolInfo.PathId.DEXDUMP)))
    }

    workQueue.await()

    return configs.map { it.utpResultProtoOutputFile.asFile.get() }
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
