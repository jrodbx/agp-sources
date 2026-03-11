/* Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.coverage.tasks

import com.android.build.gradle.internal.coverage.renderer.CodeCoverageReportOrchestrator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.buildanalyzer.common.TaskCategory
import java.io.File
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.ConsoleRenderer

@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
abstract class CodeCoverageReportTask : NonIncrementalGlobalTask() {

  @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val coverageXmlReports: ListProperty<Directory>

  @get:OutputDirectory abstract val htmlReportDir: DirectoryProperty

  @get:Internal abstract val rootProjectName: Property<String>

  @get:Internal abstract val rootProjectDir: RegularFileProperty

  override fun doTaskAction() {
    val inputDirectories: List<File> = coverageXmlReports.get().map { it.asFile }

    val successfulReportGeneration : Boolean = CodeCoverageReportOrchestrator.orchestrate(inputDirectories, htmlReportDir, rootProjectName.get(), rootProjectDir.get().asFile)

    if(successfulReportGeneration) {
      val reportLocation =
        ConsoleRenderer().asClickableFileUrl(File(htmlReportDir.get().asFile, "index.html"))
      logger.lifecycle("View coverage report at $reportLocation")
    } else {
      logger.lifecycle(
        "No code coverage data found. The code coverage report is not generated. " +
          "This can happen if code coverage support is not enabled, " +
          "or if the tests did not execute any source code."
      )
    }
  }

  class AggregatedCoverageReportCreationAction(creationConfig: GlobalTaskCreationConfig) :
    BaseCoverageReportCreationAction(creationConfig) {
    override val name = "createAggregatedCoverageReport"
    override val artifactType = InternalMultipleArtifactType.AGGREGATED_CODE_COVERAGE_DATA

    override fun handleProvider(taskProvider: TaskProvider<CodeCoverageReportTask>) {
      super.handleProvider(taskProvider)

      creationConfig.globalArtifacts
        .setInitialProvider(taskProvider, CodeCoverageReportTask::htmlReportDir)
        .on(InternalArtifactType.AGGREGATED_CODE_COVERAGE_HTML_REPORT)
    }
  }

  class CoverageReportCreationAction(creationConfig: GlobalTaskCreationConfig) : BaseCoverageReportCreationAction(creationConfig) {
    override val name = "createCoverageReport"
    override val artifactType = InternalMultipleArtifactType.CODE_COVERAGE_DATA

    override fun handleProvider(taskProvider: TaskProvider<CodeCoverageReportTask>) {
      super.handleProvider(taskProvider)

      creationConfig.globalArtifacts
        .setInitialProvider(taskProvider, CodeCoverageReportTask::htmlReportDir)
        .on(InternalArtifactType.CODE_COVERAGE_HTML_REPORT)
    }
  }

  abstract class BaseCoverageReportCreationAction(val creationConfig: GlobalTaskCreationConfig) :
    GlobalTaskCreationAction<CodeCoverageReportTask>() {

    abstract val artifactType: InternalMultipleArtifactType<Directory>
    override val type = CodeCoverageReportTask::class.java

    override fun configure(task: CodeCoverageReportTask) {
      super.configure(task)

      task.coverageXmlReports.set(creationConfig.globalArtifacts.getAll(artifactType))
      task.rootProjectName.set(creationConfig.services.projectInfo.rootProjectName)
      task.rootProjectDir.set(creationConfig.services.projectInfo.rootDir)
    }
  }
}
