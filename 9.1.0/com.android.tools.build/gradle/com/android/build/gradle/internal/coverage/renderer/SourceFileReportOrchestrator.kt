/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.coverage.renderer

import com.android.build.gradle.internal.coverage.renderer.builders.SourceFileReportsBuilder
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Orchestrates the creation and serialization of individual source file coverage reports.
 *
 * This object iterates through a map of source file builders, delegates the creation of each report to the `createSourceFileReport`
 * function, and writes the resulting report to a unique JavaScript-wrapped JSON file.
 */
object SourceFileReportOrchestrator {

  private val gson = GsonBuilder().create()

  /**
   * Processes the raw coverage data for all source files and generates individual report files.
   *
   * @param sourceFileReportsBuilder The top-level builder containing all source file builders.
   * @param projectBaseDir The base directory of the project.
   * @param baseOutputDir The directory where the final `.json.js` report files will be written.
   */
  fun orchestrate(sourceFileReportsBuilder: SourceFileReportsBuilder, projectBaseDir: File, baseOutputDir: File) {
    baseOutputDir.mkdirs()

    sourceFileReportsBuilder.sourceFileBuilders.entries.parallelStream().forEach { (path, builder) ->
      // Delegate the report creation, including file I/O, to the creator function.
      // Any file I/O exception during this call will propagate and fail the build task,
      // which is the desired behavior for ensuring a correct and complete report.
      val aggregatedReport = createSourceFileReport(path, projectBaseDir, builder)

      val jsonReport = gson.toJson(aggregatedReport)

      val pathAsJsonKey = gson.toJson(builder.packageFlattenedPath)
      val jsContent = "window.coverageData = window.coverageData || {};\nwindow.coverageData[$pathAsJsonKey] = $jsonReport;"

      val outputFile = File(baseOutputDir, "${builder.packageFlattenedPath}.json.js")

      outputFile.parentFile.mkdirs()
      outputFile.writeText(jsContent, Charsets.UTF_8)
    }
  }
}
