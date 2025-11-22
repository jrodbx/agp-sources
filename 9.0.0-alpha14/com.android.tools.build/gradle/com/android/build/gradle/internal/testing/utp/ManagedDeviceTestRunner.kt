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
import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.computeAbiFromArchitecture
import com.android.build.gradle.internal.computeAvdName
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.utp.worker.EmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.worker.ShardConfig
import com.android.build.gradle.internal.testing.utp.worker.TargetApkConfigBundle
import com.android.builder.testing.api.DeviceException
import com.android.builder.testing.api.TestException
import com.android.utils.ILogger
import com.google.common.base.Preconditions
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Path
import java.util.logging.Level

class ManagedDeviceTestRunner(
    private val workerExecutor: WorkerExecutor,
    private val objectFactory: ObjectFactory,
    private val utpDependencies: UtpDependencies,
    private val utpJvmExecutable: File,
    private val versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
    private val emulatorControlConfig: EmulatorControlConfig,
    private val useOrchestrator: Boolean,
    private val forceCompilation: Boolean,
    private val numShards: Int?,
    private val avdComponents: AvdComponentsBuildService,
    private val installApkTimeout: Int?,
    private val enableEmulatorDisplay: Boolean,
    private val utpLoggingLevel: Level,
    private val targetIsSplitApk: Boolean,
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
        val abi = computeAbiFromArchitecture(managedDevice)
        val utpManagedDevice = UtpManagedDevice(
            managedDevice.name,
            computeAvdName(managedDevice),
            managedDevice.apiLevel,
            computeAbiFromArchitecture(managedDevice),
            managedDevice.testedAbi ?: abi,
            avdComponents.avdFolder.get().asFile.absolutePath,
            runId,
            emulatorProvider.get().asFile.resolve(FN_EMULATOR).absolutePath,
            enableEmulatorDisplay
        )
        val testedApks = getTestedApks(testData, utpManagedDevice, logger)
        val extractedSdkApks = getExtractedSdkApks(testData, utpManagedDevice)

        val results = avdComponents.runWithAvds(
            utpManagedDevice.avdName, numShards ?: 1) { deviceSerials ->
            val devicesAcquired = deviceSerials.size
            if (devicesAcquired != (numShards ?: 1)) {
                logger.warning(
                    "Unable to retrieve $numShards devices, only " +
                            "$devicesAcquired available. Proceeding to run tests on " +
                            "$devicesAcquired shards."
                )
            }

            val runnerConfigs = deviceSerials.mapIndexed { currentShard, deviceSerial ->
                val shardConfig = numShards?.let {
                    ShardConfig(totalCount = devicesAcquired, index = currentShard)
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
                    utpManagedDevice
                } else {
                    utpManagedDevice.forShard(currentShard)
                }

                createUtpRunConfig(
                    objectFactory,
                    shardedManagedDevice.id,
                    shardedManagedDevice.deviceName,
                    deviceSerial,
                    testData,
                    TargetApkConfigBundle(testedApks, targetIsSplitApk),
                    additionalInstallOptions,
                    helperApks,
                    uninstallIncompatibleApks = true,
                    utpOutputDir,
                    emulatorControlConfig,
                    coverageOutputDirectory,
                    useOrchestrator,
                    forceCompilation,
                    additionalTestOutputDir,
                    findAdditionalTestOutputDirectoryOnManagedDevice(utpManagedDevice, testData),
                    installApkTimeout,
                    extractedSdkApks,
                    uninstallApksAfterTest = false,
                    reinstallIncompatibleApksBeforeTest = true,
                    shardConfig,
                    utpLoggingLevel,
                )
            }

            runUtpTestSuiteAndWait(
                runnerConfigs,
                workerExecutor,
                utpJvmExecutable,
                projectPath,
                variantName,
                outputDirectory,
                logger,
                utpDependencies,
                versionedSdkLoader,
            )
        }

        results.forEach { result ->
            if (result.resultsProto?.hasPlatformError() == true) {
                logger.error(null, getPlatformErrorMessage(result.resultsProto))
            }
            result.resultsProto?.issueList?.forEach { issue ->
                logger.error(null, issue.message)
            }
        }

        val resultProtos = results.mapNotNull(UtpTestRunResult::resultsProto)
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
    }

    companion object {
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
