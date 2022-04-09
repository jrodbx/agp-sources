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

import com.android.SdkConstants.FN_EMULATOR
import com.android.build.api.dsl.Device
import com.android.build.api.instrumentation.StaticTestData
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.builder.testing.api.DeviceException
import com.android.builder.testing.api.TestException
import com.android.utils.ILogger
import com.google.common.base.Preconditions
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import java.io.File
import java.util.logging.Level
import org.gradle.api.logging.Logger
import org.gradle.workers.WorkerExecutor
import java.nio.file.Path

class ManagedDeviceTestRunner(
    private val workerExecutor: WorkerExecutor,
    private val utpDependencies: UtpDependencies,
    private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
    private val emulatorControlConfig: EmulatorControlConfig,
    private val retentionConfig: RetentionConfig,
    private val useOrchestrator: Boolean,
    private val numShards: Int?,
    private val emulatorGpuFlag: String,
    private val showEmulatorKernelLogging: Boolean,
    private val avdComponents: AvdComponentsBuildService,
    private val installApkTimeout: Int?,
    private val enableEmulatorDisplay: Boolean,
    private val utpLoggingLevel: Level = Level.WARNING,
    private val targetIsSplitApk: Boolean,
    private val configFactory: UtpConfigFactory = UtpConfigFactory(),
    private val runUtpTestSuiteAndWaitFunc: (
        List<UtpRunnerConfig>, String, String, File, ILogger
    ) -> List<UtpTestRunResult> = { runnerConfigs, projectPath, variantName, resultsDir, logger ->
        runUtpTestSuiteAndWait(
            runnerConfigs, workerExecutor, projectPath, variantName, resultsDir, logger,
            null, utpDependencies)
    },
) {

    /**
     * @param additionalTestOutputDir output directory for additional test output, or null if disabled
     * @param dependencyApks are the private sandbox SDK APKs
     */
    fun runTests(
        managedDevice: Device,
        runId: String,
        outputDirectory: File,
        coverageOutputDirectory: File,
        additionalTestOutputDir: File?,
        projectPath: String,
        variantName: String,
        testData: StaticTestData,
        additionalInstallOptions: List<String>,
        helperApks: Set<File>,
        logger: Logger,
        dependencyApks: Set<File>
    ): Boolean {
        managedDevice as ManagedVirtualDevice
        val logger = LoggerWrapper(logger)
        val emulatorProvider = avdComponents.emulatorDirectory
        Preconditions.checkArgument(
            emulatorProvider.isPresent(),
            "The emulator is missing. Download the emulator in order to use managed devices.")
        val utpManagedDevice = UtpManagedDevice(
            managedDevice.name,
            computeAvdName(managedDevice),
            managedDevice.apiLevel,
            computeAbiFromArchitecture(managedDevice),
            avdComponents.avdFolder.get().asFile.absolutePath,
            runId,
            emulatorProvider.get().asFile.resolve(FN_EMULATOR).absolutePath,
            enableEmulatorDisplay
        )
        val testedApks = getTestedApks(testData, utpManagedDevice, logger)
        val extractedSdkApks = getExtractedSdkApks(testData, utpManagedDevice)
        val runnerConfigs = mutableListOf<UtpRunnerConfig>()
        try {
            avdComponents.lockManager.lock(numShards ?: 1).use { lock ->
                val devicesAcquired = lock.lockCount
                if (devicesAcquired != (numShards ?: 1) ) {
                    logger.warning("Unable to retrieve $numShards devices, only " +
                            "$devicesAcquired available. Proceeding to run tests on " +
                            "$devicesAcquired shards.")
                }
                val shardsToRun = if (numShards == null) null else devicesAcquired

                repeat(shardsToRun ?: 1) { currentShard ->
                    val shardConfig = shardsToRun?.let {
                        ShardConfig(totalCount = it, index = currentShard)
                    }
                    val utpOutputDir = if (shardConfig == null) {
                        outputDirectory
                    } else {
                        File(outputDirectory, "shard_$currentShard")
                    }.apply {
                        if (!exists()) {
                            mkdirs()
                        }
                    }
                    val shardedManagedDevice = if (shardsToRun == null) {
                        utpManagedDevice
                    } else {
                        utpManagedDevice.forShard(currentShard)
                    }
                    val runnerConfigProto: (
                        UtpTestResultListenerServerMetadata,
                        File
                    ) -> RunnerConfigProto.RunnerConfig =
                        { resultListenerServerMetadata, utpTmpDir ->
                            configFactory.createRunnerConfigProtoForManagedDevice(
                                shardedManagedDevice,
                                testData,
                                TargetApkConfigBundle(testedApks, targetIsSplitApk),
                                additionalInstallOptions,
                                helperApks,
                                utpDependencies,
                                versionedSdkLoader,
                                utpOutputDir,
                                utpTmpDir,
                                emulatorControlConfig,
                                retentionConfig,
                                coverageOutputDirectory,
                                additionalTestOutputDir,
                                useOrchestrator,
                                resultListenerServerMetadata,
                                emulatorGpuFlag,
                                showEmulatorKernelLogging,
                                installApkTimeout,
                                extractedSdkApks,
                                shardConfig
                            )
                        }
                    runnerConfigs.add(
                        UtpRunnerConfig(
                            shardedManagedDevice.deviceName,
                            shardedManagedDevice.id,
                            utpOutputDir,
                            runnerConfigProto,
                            configFactory.createServerConfigProto(),
                            shardConfig,
                            utpLoggingLevel
                        )
                    )
                }
            }

            val results = runUtpWithRetryForEmulatorTimeoutException(
                runnerConfigs,
                projectPath,
                variantName,
                outputDirectory,
                logger
            )

            results.forEach { result ->
                if (result.resultsProto?.hasPlatformError() == true) {
                    logger.error(null, getPlatformErrorMessage(result.resultsProto))
                }
                result.resultsProto?.issueList?.forEach { issue ->
                    logger.error(null, issue.message)
                }
            }

            val resultProtos = results
                .map(UtpTestRunResult::resultsProto)
                .filterNotNull()
            if (resultProtos.isNotEmpty()) {
                // Create a merged result pb file in the outputDirectory. If it's a sharded
                // test, a result pb file is generated in a subdirectory per shard. If it's a
                // non-sharded test, a result pb is generated in the outputDirectory so we
                // don't need to create a merged result here.
                if (numShards != null) {
                    val resultsMerger = UtpTestSuiteResultMerger()
                    resultProtos.forEach(resultsMerger::merge)

                    val mergedTestResultPbFile = File(outputDirectory, TEST_RESULT_PB_FILE_NAME)
                    mergedTestResultPbFile.outputStream().use {
                        resultsMerger.result.writeTo(it)
                    }
                }
            }

            return results.all(UtpTestRunResult::testPassed)
        } finally {
            avdComponents.closeOpenEmulators(utpManagedDevice.id)
        }
    }

    private fun runUtpWithRetryForEmulatorTimeoutException(
        runnerConfigs: List<UtpRunnerConfig>,
        projectPath: String,
        variantName: String,
        outputDirectory: File,
        logger: ILogger
    ): List<UtpTestRunResult> {
        val results: MutableList<UtpTestRunResult> = mutableListOf()

        // A pairs of remaining utp runner config and its previous utp test run result.
        var remainingConfigs: List<Pair<UtpRunnerConfig, UtpTestRunResult?>> = runnerConfigs.map {
            it to null
        }
        for (i in 0..MAX_RETRY_FOR_EMULATOR_TIMEOUT_UTP_ERROR) {
            val runResults = runUtpTestSuiteAndWaitFunc(
                remainingConfigs.map { it.first },
                projectPath,
                variantName,
                outputDirectory,
                logger
            )

            val nextConfigs: MutableList<Pair<UtpRunnerConfig, UtpTestRunResult?>> = mutableListOf()
            for ((runResult, runConfig) in runResults.zip(remainingConfigs)) {
                if (hasEmulatorTimeoutException(runResult.resultsProto)) {
                    // Rerun UTP if it failed due to the emulator timeout exception.
                    nextConfigs.add(runConfig.first to runResult)
                } else {
                    results.add(runResult)
                }
            }

            val noProgress = remainingConfigs.size == nextConfigs.size
            remainingConfigs = nextConfigs

            // If all UTP runs failed due to emulator timeout exception, we don't retry
            // and simply gave up because it will likely fail again.
            if (remainingConfigs.isEmpty() || noProgress) {
                break
            }
        }

        remainingConfigs.forEach { (runConfig, runResult) ->
            if (runResult != null) {
                results.add(runResult)
            }

            logger.error(
                null,
                "Could not finish tests for device: ${runConfig.shardName()}.\n" +
                "${getPlatformErrorMessage(runResult?.resultsProto)}\n"
            )
        }

        return results
    }

    companion object {
        private const val MAX_RETRY_FOR_EMULATOR_TIMEOUT_UTP_ERROR = 1

        /**
         * Returns the tested apk for the given managed device and test data.
         */
        fun getTestedApks(
            testData: StaticTestData, device: UtpManagedDevice, logger: ILogger): List<File> {

            val minSdk = testData.minSdkVersion.apiLevel
            if (device.api < minSdk) {
                throw TestException(
                    DeviceException(
                        "Device ${device.deviceName} invalid: minSdkVersion $minSdk > deviceApiLevel " +
                                "${device.api}"))
            }
            val deviceConfigProvider = ManagedDeviceConfigProvider(device)
            if (!testData.isLibrary) {
                val testedApks =
                    testData.testedApkFinder.invoke(deviceConfigProvider)

                if (testedApks.isEmpty()) {
                    logger.warning("No matching Apks found for ${device.deviceName}.")
                }
                return testedApks
            }
            return listOf()
        }

        fun getExtractedSdkApks(
                testData: StaticTestData, device: UtpManagedDevice): List<List<Path>> {
            val deviceConfigProvider = ManagedDeviceConfigProvider(device)
            return testData.privacySandboxInstallBundlesFinder(deviceConfigProvider)
        }
    }
}
