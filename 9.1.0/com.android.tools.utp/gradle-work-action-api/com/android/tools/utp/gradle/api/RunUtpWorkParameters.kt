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

package com.android.tools.utp.gradle.api

import java.io.File
import java.io.Serializable
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

/** Parameters of [UtpAction]. */
interface RunUtpWorkParameters : WorkParameters {
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
  /** The output file containing the merged binary test results from all [utpRunConfigs]. */
  val mergedUtpResultProtoOutputFile: RegularFileProperty
  /** The output file containing the integer exit code (0 for success, non-zero for failure). */
  val testResultExitCodeFile: RegularFileProperty

  /** The Android SDK root directory. */
  val androidSdkDirectory: DirectoryProperty
  /** The adb executable file. */
  val adbExecutable: RegularFileProperty
  /** The aapt executable file. */
  val aaptExecutable: RegularFileProperty
  /** The dexdump executable file. */
  val dexdumpExecutable: RegularFileProperty

  /** Configuration for a single UTP test run. */
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
    val testData: Property<TestData>
    /** Bundle of app APKs to be installed. */
    val targetApkConfigBundle: Property<TargetApkConfigBundle>
    /** Additional options for APK installation. */
    val additionalInstallOptions: ListProperty<String>
    /** Dependency APKs to be installed. */
    val dependencyApks: ListProperty<FileCollection>
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
    /** Whether to uninstall APKs after the test run. */
    val uninstallApksAfterTest: Property<Boolean>
    /** Whether to reinstall incompatible APKs before the test. */
    val reinstallIncompatibleApksBeforeTest: Property<Boolean>
    /** Sharding configuration, if any. */
    val shardConfig: Property<ShardConfig>
  }
}

/** Encapsulates test data. */
data class TestData(
  val instrumentationTargetPackageId: String,
  val testedApplicationId: String?,
  val applicationId: String,
  val instrumentationRunner: String,
  val testApk: File,
  val instrumentationRunnerArguments: Map<String, String>,
  val isTestCoverageEnabled: Boolean,
  val animationsDisabled: Boolean,
) : Serializable

/** Encapsulates installation configuration for app APKs */
data class TargetApkConfigBundle(val appApks: List<File>, val isSplitApk: Boolean) : Serializable

/** Information needed to access the emulator from within the tests. */
data class EmulatorControlConfig(val enabled: Boolean, val allowedEndpoints: Set<String>, val secondsValid: Int) : Serializable

/**
 * Class for keeping track of all sharding information to invoke a single shard.
 *
 * @param totalCount The total number of shards in this test invocation.
 * @param index The index of the this shard, should be in the range 0 to ([totalCount] - 1)
 */
data class ShardConfig(val totalCount: Int, val index: Int) : Serializable
