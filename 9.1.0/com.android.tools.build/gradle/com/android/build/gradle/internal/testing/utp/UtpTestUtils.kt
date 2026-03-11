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

import com.android.Version.ANDROID_TOOLS_BASE_VERSION
import com.android.build.api.instrumentation.StaticTestData
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.testing.utp.worker.RunUtpWorkAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.sdklib.BuildToolInfo
import com.android.tools.utp.gradle.api.EmulatorControlConfig
import com.android.tools.utp.gradle.api.RunUtpWorkParameters
import com.android.tools.utp.gradle.api.ShardConfig
import com.android.tools.utp.gradle.api.TargetApkConfigBundle
import com.android.tools.utp.gradle.api.TestData
import com.android.tools.utp.gradle.api.UtpDependencies
import com.android.tools.utp.gradle.api.UtpDependency
import java.io.File
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.workers.WorkerExecutor

private const val TEST_RESULT_EXIT_CODE_FILE_NAME = "test-result-exit-code.txt"
private const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"

/** Runs the given runner configs using Unified Test Platform. */
fun runUtpTestSuiteAndWait(
  runnerConfigs: List<RunUtpWorkParameters.UtpRunConfig>,
  workerExecutor: WorkerExecutor,
  projectPath: String,
  variantName: String,
  resultsDir: File,
  utpDependencies: UtpDependencies,
  versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader,
): Boolean {
  val mergedUtpResultProtoOutputFile = File(resultsDir, TEST_RESULT_PB_FILE_NAME)
  val testResultExitCodeFile = File(resultsDir, TEST_RESULT_EXIT_CODE_FILE_NAME)

  val workQueue = workerExecutor.classLoaderIsolation { spec -> spec.classpath.fromDisallowChanges(utpDependencies.gradleWorkAction) }

  workQueue.submit(RunUtpWorkAction::class.java) { params ->
    params.utpRunConfigs.setDisallowChanges(runnerConfigs)
    params.utpDependencies.setDisallowChanges(utpDependencies)
    params.projectPath.setDisallowChanges(projectPath)
    params.variantName.setDisallowChanges(variantName)
    params.xmlTestReportOutputDirectory.fileValue(resultsDir).disallowChanges()
    params.mergedUtpResultProtoOutputFile.fileValue(mergedUtpResultProtoOutputFile).disallowChanges()
    params.testResultExitCodeFile.fileValue(testResultExitCodeFile).disallowChanges()
    params.androidSdkDirectory.setDisallowChanges(versionedSdkLoader.sdkDirectoryProvider)
    params.adbExecutable.setDisallowChanges(versionedSdkLoader.adbExecutableProvider)
    params.aaptExecutable
      .fileValue(File(versionedSdkLoader.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.AAPT)))
      .disallowChanges()
    params.dexdumpExecutable.fileValue(File(versionedSdkLoader.buildToolInfoProvider.get().getPath(BuildToolInfo.PathId.DEXDUMP)))
  }

  workQueue.await()

  return testResultExitCodeFile.exists() && testResultExitCodeFile.isFile && testResultExitCodeFile.readText().trim().toInt() == 0
}

/** Factory function to create and configure a [RunUtpWorkParameters.UtpRunConfig] instance. */
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
  uninstallApksAfterTest: Boolean,
  reinstallIncompatibleApksBeforeTest: Boolean,
  shardConfig: ShardConfig?,
): RunUtpWorkParameters.UtpRunConfig {
  val utpRunConfig = objectFactory.newInstance(RunUtpWorkParameters.UtpRunConfig::class.java)

  utpRunConfig.deviceId.setDisallowChanges(deviceId)
  utpRunConfig.deviceName.setDisallowChanges(deviceName)
  utpRunConfig.deviceShardName.setDisallowChanges(
    if (shardConfig == null) {
      deviceName
    } else {
      "${deviceName}_${shardConfig.index}"
    }
  )
  utpRunConfig.utpResultProtoOutputFile.fileValue(File(outputDir, TEST_RESULT_PB_FILE_NAME)).disallowChanges()
  utpRunConfig.deviceSerialNumber.setDisallowChanges(deviceSerialNumber)
  utpRunConfig.testData.setDisallowChanges(testData.toWorkActionTestData())
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
  utpRunConfig.uninstallApksAfterTest.setDisallowChanges(uninstallApksAfterTest)
  utpRunConfig.reinstallIncompatibleApksBeforeTest.setDisallowChanges(reinstallIncompatibleApksBeforeTest)
  utpRunConfig.shardConfig.setDisallowChanges(shardConfig)

  return utpRunConfig
}

private fun StaticTestData.toWorkActionTestData(): TestData {
  return TestData(
    instrumentationTargetPackageId = this.instrumentationTargetPackageId,
    testedApplicationId = this.testedApplicationId,
    applicationId = this.applicationId,
    instrumentationRunner = this.instrumentationRunner,
    testApk = this.testApk,
    instrumentationRunnerArguments = this.instrumentationRunnerArguments,
    isTestCoverageEnabled = this.isTestCoverageEnabled,
    animationsDisabled = this.animationsDisabled,
  )
}

/** Looks for UTP configurations in a project, creates and add it to the project if missing. */
fun maybeCreateUtpConfigurations(configurations: ConfigurationContainer, dependencies: DependencyHandler) {
  UtpDependency.entries.forEach { utpDependency ->
    if (!configurations.names.contains(utpDependency.configurationName)) {
      configurations.register(utpDependency.configurationName) {
        it.isVisible = false
        it.isTransitive = true
        it.isCanBeConsumed = false
        it.description = "A configuration to resolve the Unified Test Platform dependencies."
      }
      dependencies.add(utpDependency.configurationName, utpDependency.mavenCoordinate(ANDROID_TOOLS_BASE_VERSION))
    }
  }
}

/** Resolves the UTP dependencies and populates this [UtpDependencies] object from the given [ConfigurationContainer]. */
fun UtpDependencies.resolveDependencies(configurationsContainer: ConfigurationContainer) {
  UtpDependency.entries.forEach { utpDependency ->
    utpDependency.mapperFunc(this).from(configurationsContainer.getByName(utpDependency.configurationName))
  }
}
