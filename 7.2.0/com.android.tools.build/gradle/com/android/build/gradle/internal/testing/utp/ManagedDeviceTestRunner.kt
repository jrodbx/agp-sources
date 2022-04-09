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
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.builder.testing.api.DeviceException
import com.android.builder.testing.api.TestException
import com.android.utils.ILogger
import com.google.testing.platform.proto.api.config.RunnerConfigProto
import java.io.File
import java.util.logging.Level
import org.gradle.workers.WorkerExecutor

class ManagedDeviceTestRunner(
    private val workerExecutor: WorkerExecutor,
    private val utpDependencies: UtpDependencies,
    private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
    private val retentionConfig: RetentionConfig,
    private val useOrchestrator: Boolean,
    private val numShards: Int?,
    private val utpLoggingLevel: Level = Level.WARNING,
    private val configFactory: UtpConfigFactory = UtpConfigFactory(),
    private val runUtpTestSuiteAndWaitFunc: (
        List<UtpRunnerConfig>, String, String, File, ILogger
    ) -> List<UtpTestRunResult> = { runnerConfigs, projectPath, variantName, resultsDir, logger ->
        runUtpTestSuiteAndWait(
            runnerConfigs, workerExecutor, projectPath, variantName, resultsDir, logger,
            null, utpDependencies)
    }
) {

    /**
     * @param additionalTestOutputDir output directory for additional test output, or null if disabled
     */
    fun runTests(
        managedDevice: UtpManagedDevice,
        outputDirectory: File,
        coverageOutputDirectory: File,
        additionalTestOutputDir: File?,
        projectPath: String,
        variantName: String,
        testData: StaticTestData,
        additionalInstallOptions: List<String>,
        helperApks: Set<File>,
        logger: ILogger
    ): Boolean {
        val testedApks = ManagedDeviceTestRunner.getTestedApks(testData, managedDevice, logger)
        val runnerConfigs = mutableListOf<UtpRunnerConfig>()
        repeat(numShards ?: 1) { currentShard ->
            val shardConfig = numShards?.let {
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
            val shardedManagedDevice = if (numShards == null) {
                managedDevice
            } else {
                managedDevice.forShard(currentShard)
            }
            val runnerConfigProto: (
                UtpTestResultListenerServerMetadata,
                File
            ) -> RunnerConfigProto.RunnerConfig = { resultListenerServerMetadata, utpTmpDir ->
                    configFactory.createRunnerConfigProtoForManagedDevice(
                        shardedManagedDevice,
                        testData,
                        testedApks,
                        additionalInstallOptions,
                        helperApks,
                        utpDependencies,
                        versionedSdkLoader,
                        utpOutputDir,
                        utpTmpDir,
                        retentionConfig,
                        coverageOutputDirectory,
                        additionalTestOutputDir,
                        useOrchestrator,
                        resultListenerServerMetadata,
                        shardConfig
                    )
                }
            runnerConfigs.add(UtpRunnerConfig(
                shardedManagedDevice.deviceName,
                shardedManagedDevice.id,
                utpOutputDir,
                runnerConfigProto,
                configFactory.createServerConfigProto(),
                shardConfig,
                utpLoggingLevel
            ))
        }

        val results = runUtpWithRetryForEmulatorTimeoutException(
            runnerConfigs,
            projectPath,
            variantName,
            outputDirectory,
            logger
        )

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
                resultsMerger.result.writeTo(mergedTestResultPbFile.outputStream())
            }
        }

        return results.all(UtpTestRunResult::testPassed)
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
                "Last Error: ${getPlatformErrorMessage(runResult?.resultsProto)}\n"
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
                    testData.testedApkFinder.invoke(deviceConfigProvider, logger)

                if (testedApks.isEmpty()) {
                    logger.warning("No matching Apks found for ${device.deviceName}.")
                }
                return testedApks
            }
            return listOf()
        }
    }
}
