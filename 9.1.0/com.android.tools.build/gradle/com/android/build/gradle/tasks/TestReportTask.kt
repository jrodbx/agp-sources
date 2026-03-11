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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.test.report.ReportType
import com.android.build.gradle.internal.test.report.TestReport
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.internal.logging.ConsoleRenderer

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class TestReportTask : NonIncrementalGlobalTask() {

  @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE) abstract val testResults: ListProperty<Directory>

  @get:OutputDirectory abstract val reportDir: DirectoryProperty

  override fun doTaskAction() {
    if (!testResults.isPresent) {
      LoggerWrapper.getLogger(TestReportTask::class.java)
        .warning("Aggregated Test reporting feature is disabled, TestReportTask's execution is skipped.")
      return
    }
    val inputDirectories: List<File> = testResults.get().map { it.asFile }
    val reportDir = reportDir.get().asFile

    val report = TestReport(ReportType.MULTI_PROJECT, inputDirectories, reportDir)
    val compositeTestResults = report.generateReport()

    if (!compositeTestResults.failures.isEmpty()) {
      val reportUrl = ConsoleRenderer().asClickableFileUrl(File(reportDir, "index.html"))
      val message = "There were failing tests. See the report at: " + reportUrl
      throw GradleException(message)
    }
  }

  class AggregatedTestReportCreationAction(creationConfig: GlobalTaskCreationConfig, isReportAggregationEnabled: Boolean) :
    BaseCreationAction(creationConfig, isReportAggregationEnabled) {
    override val name = "createAggregatedTestReport"
    override val artifactType = InternalMultipleArtifactType.ALL_PROJECT_TEST_RESULTS
  }

  class TestReportCreationAction(creationConfig: GlobalTaskCreationConfig, isReportAggregationEnabled: Boolean) :
    BaseCreationAction(creationConfig, isReportAggregationEnabled) {
    override val name = "createTestReport"
    override val artifactType = InternalMultipleArtifactType.PROJECT_LEVEL_TEST_RESULTS
  }

  abstract class BaseCreationAction(val creationConfig: GlobalTaskCreationConfig, val isReportAggregationEnabled: Boolean) :
    GlobalTaskCreationAction<TestReportTask>() {

    abstract val artifactType: InternalMultipleArtifactType<Directory>
    override val type = TestReportTask::class.java

    override fun configure(task: TestReportTask) {
      super.configure(task)
      if (isReportAggregationEnabled) {
        task.testResults.set(creationConfig.globalArtifacts.getAll(artifactType))
      }
      task.reportDir.set(task.project.layout.buildDirectory.dir("intermediates/$name/"))
    }
  }
}
