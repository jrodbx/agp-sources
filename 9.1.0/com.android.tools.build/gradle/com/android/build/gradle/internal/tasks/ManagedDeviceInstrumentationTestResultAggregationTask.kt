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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.work.DisableCachingByDefault

/** Aggregates XML test results into one. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class ManagedDeviceInstrumentationTestResultAggregationTask : NonIncrementalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val deviceTestResultDirs: ConfigurableFileCollection

  @get:OutputDirectory abstract val outputTestReportHtmlDir: DirectoryProperty

  override fun doTaskAction() {
    TestReport(ReportType.SINGLE_FLAVOR, deviceTestResultDirs.files.toList(), outputTestReportHtmlDir.get().asFile).generateReport()

    val reportUrl = ConsoleRenderer().asClickableFileUrl(File(outputTestReportHtmlDir.get().asFile, "index.html"))
    logger.lifecycle("Test execution completed. See the report at: $reportUrl")
  }

  class CreationAction(
    creationConfig: InstrumentedTestCreationConfig,
    private val deviceTestResultDirs: List<File>,
    private val testReportHtmlOutputDir: File,
  ) : VariantTaskCreationAction<ManagedDeviceInstrumentationTestResultAggregationTask, InstrumentedTestCreationConfig>(creationConfig) {

    override val name: String
      get() = computeTaskName("merge", "TestResultProtos")

    override val type: Class<ManagedDeviceInstrumentationTestResultAggregationTask>
      get() = ManagedDeviceInstrumentationTestResultAggregationTask::class.java

    override fun handleProvider(taskProvider: TaskProvider<ManagedDeviceInstrumentationTestResultAggregationTask>) {
      creationConfig.artifacts
        .setInitialProvider(taskProvider, ManagedDeviceInstrumentationTestResultAggregationTask::outputTestReportHtmlDir)
        .withName("allDevices")
        .atLocation(testReportHtmlOutputDir.absolutePath)
        .on(InternalArtifactType.MANAGED_DEVICE_ANDROID_TEST_MERGED_RESULTS_REPORT)
    }

    override fun configure(task: ManagedDeviceInstrumentationTestResultAggregationTask) {
      super.configure(task)

      task.deviceTestResultDirs.from(deviceTestResultDirs).disallowChanges()
    }
  }
}
