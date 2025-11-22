/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.testing.StaticTestData
import com.android.build.gradle.internal.testing.utp.ShardConfig
import com.android.build.gradle.internal.testing.utp.TEST_RESULT_PB_FILE_NAME
import com.android.build.gradle.internal.testing.utp.TargetApkConfigBundle
import com.android.build.gradle.internal.testing.utp.UtpDependencies
import com.android.build.gradle.internal.testing.utp.emulatorcontrol.EmulatorControlConfig
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import java.io.File
import java.nio.file.Path
import java.util.logging.Level

/**
 * Parameters of [RunUtpWorkAction].
 */
interface RunUtpWorkParameters : WorkParameters {
    /** The Java executable to run UTP. */
    val jvm: RegularFileProperty
    /** UTP dependencies. */
    val utpDependencies: Property<UtpDependencies>
    /** Configurations for each UTP test run. */
    val utpRunConfigs: ListProperty<UtpRunConfig>
    /** The project path, used in the XML test report. */
    val projectPath: Property<String>
    /** The variant name, used in the XML test report. */
    val variantName: Property<String>
    /** The directory where XML test reports should be generated. */
    val xmlTestReportOutputDirectory: DirectoryProperty

    /** The Android SDK root directory. */
    val androidSdkDirectory: DirectoryProperty
    /** The adb executable file. */
    val adbExecutable: RegularFileProperty
    /** The aapt executable file. */
    val aaptExecutable: RegularFileProperty
    /** The dexdump executable file. */
    val dexdumpExecutable: RegularFileProperty

    /**
     * Configuration for a single UTP test run.
     */
    interface UtpRunConfig {
        /** The device ID (e.g., serial number) for this run. */
        val deviceId: Property<String>
        /** The device name, used in the XML test report. */
        val deviceName: Property<String>
        /** The unique name for this test shard, used as the test suite name in the XML report. */
        val deviceShardName: Property<String>
        /** The output file where the binary test-result.pb for this run should be written. */
        val utpResultProtoOutputFile: RegularFileProperty
        /** The device serial number to run the test on. */
        val deviceSerialNumber: Property<String>
        /** Test static metadata. */
        val testData: Property<StaticTestData>
        /** Bundle of app APKs to be installed. */
        val targetApkConfigBundle: Property<TargetApkConfigBundle>
        /** Additional options for APK installation. */
        val additionalInstallOptions: ListProperty<String>
        /** Helper APKs to be installed. */
        val helperApks: ConfigurableFileCollection
        /** Whether to uninstall incompatible APKs. */
        val uninstallIncompatibleApks: Property<Boolean>
        /** The output directory for this specific UTP run. */
        val outputDir: DirectoryProperty
        /** Configuration for emulator gRPC control. */
        val emulatorControlConfig: Property<EmulatorControlConfig>
        /** The output directory for test coverage results. */
        val coverageOutputDir: DirectoryProperty
        /** Whether to use Android Test Orchestrator. */
        val useOrchestrator: Property<Boolean>
        /** Whether to force compilation. */
        val forceCompilation: Property<Boolean>
        /** An optional directory for additional test outputs on the host. */
        val additionalTestOutputDir: DirectoryProperty
        /** An optional on-device directory for additional test outputs. */
        val additionalTestOutputOnDeviceDir: Property<String>
        /** Timeout for APK installation in seconds. */
        val installApkTimeout: Property<Int>
        /** Extracted Privacy Sandbox SDK APKs. */
        val extractedSdkApks: ListProperty<FileCollection>
        /** Whether to uninstall APKs after the test run. */
        val uninstallApksAfterTest: Property<Boolean>
        /** Whether to reinstall incompatible APKs before the test. */
        val reinstallIncompatibleApksBeforeTest: Property<Boolean>
        /** Sharding configuration, if any. */
        val shardConfig: Property<ShardConfig>
        /** The logging level for the UTP run. */
        val loggingLevel: Property<Level>
    }
}

/**
 * Factory function to create and configure a [RunUtpWorkParameters.UtpRunConfig] instance.
 */
fun createUtpRunConfig(
    objectFactory: ObjectFactory,
    deviceId: String,
    deviceName: String,
    deviceSerialNumber: String,
    testData: StaticTestData,
    targetApkConfigBundle: TargetApkConfigBundle,
    additionalInstallOptions: Iterable<String>,
    helperApks: Iterable<File>,
    uninstallIncompatibleApks: Boolean,
    outputDir: File,
    emulatorControlConfig: EmulatorControlConfig,
    coverageOutputDir: File,
    useOrchestrator: Boolean,
    forceCompilation: Boolean,
    additionalTestOutputDir: File?,
    additionalTestOutputOnDeviceDir: String?,
    installApkTimeout: Int?,
    extractedSdkApks: List<List<Path>>,
    uninstallApksAfterTest: Boolean,
    reinstallIncompatibleApksBeforeTest: Boolean,
    shardConfig: ShardConfig?,
    loggingLevel: Level,
): RunUtpWorkParameters.UtpRunConfig {
    val utpRunConfig = objectFactory.newInstance(RunUtpWorkParameters.UtpRunConfig::class.java)

    utpRunConfig.deviceId.setDisallowChanges(deviceId)
    utpRunConfig.deviceName.setDisallowChanges(deviceName)
    utpRunConfig.deviceShardName.setDisallowChanges(if (shardConfig == null) {
        deviceName
    } else {
        "${deviceName}_${shardConfig.index}"
    })
    utpRunConfig.utpResultProtoOutputFile.fileValue(
        File(outputDir, TEST_RESULT_PB_FILE_NAME)).disallowChanges()
    utpRunConfig.deviceSerialNumber.setDisallowChanges(deviceSerialNumber)
    utpRunConfig.testData.setDisallowChanges(testData)
    utpRunConfig.targetApkConfigBundle.setDisallowChanges(targetApkConfigBundle)
    utpRunConfig.additionalInstallOptions.setDisallowChanges(additionalInstallOptions)
    utpRunConfig.helperApks.fromDisallowChanges(helperApks)
    utpRunConfig.uninstallIncompatibleApks.setDisallowChanges(uninstallIncompatibleApks)
    utpRunConfig.outputDir.fileValue(outputDir).disallowChanges()
    utpRunConfig.emulatorControlConfig.setDisallowChanges(emulatorControlConfig)
    utpRunConfig.coverageOutputDir.fileValue(coverageOutputDir).disallowChanges()
    utpRunConfig.useOrchestrator.setDisallowChanges(useOrchestrator)
    utpRunConfig.forceCompilation.setDisallowChanges(forceCompilation)
    utpRunConfig.additionalTestOutputDir.fileValue(additionalTestOutputDir).disallowChanges()
    utpRunConfig.additionalTestOutputOnDeviceDir.setDisallowChanges(additionalTestOutputOnDeviceDir)
    utpRunConfig.installApkTimeout.setDisallowChanges(installApkTimeout)
    utpRunConfig.extractedSdkApks.setDisallowChanges(extractedSdkApks.map {
        objectFactory.fileCollection().convention(it).apply { disallowChanges() }
    })
    utpRunConfig.uninstallApksAfterTest.setDisallowChanges(uninstallApksAfterTest)
    utpRunConfig.reinstallIncompatibleApksBeforeTest.setDisallowChanges(reinstallIncompatibleApksBeforeTest)
    utpRunConfig.shardConfig.setDisallowChanges(shardConfig)
    utpRunConfig.loggingLevel.setDisallowChanges(loggingLevel)

    return utpRunConfig
}
