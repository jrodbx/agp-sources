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

package com.android.build.gradle.internal.coverage.renderer

import com.android.build.gradle.internal.coverage.renderer.builders.CoverageReportBuilder
import com.android.build.gradle.internal.coverage.renderer.builders.SourceFileReportsBuilder
import com.android.build.gradle.internal.coverage.renderer.utils.copyResources
import com.android.build.gradle.internal.coverage.renderer.xmlparser.XMLTransformer
import com.google.gson.GsonBuilder
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty

/**
 * Orchestrates the entire process of generating the final HTML code coverage report. This object acts as the main entry point for the
 * report generation task. It coordinates the parsing of multiple JaCoCo XML files, aggregates the data into a comprehensive model,
 * serializes the model to JavaScript files, and copies the static web assets (HTML, CSS, JS) to create a self-contained, viewable report.
 */
object CodeCoverageReportOrchestrator {

  private val STATIC_HTML_RESOURCES =
    listOf("css/style.css", "index.html", "javascript/codecoveragescript.js", "javascript/sourceviewscript.js")

  /**
   * Executes the full report generation workflow. The process involves:
   * 1. Finding and parsing all JaCoCo XML reports from the input directories. If any XML file is
   *    malformed or cannot be parsed, the build will fail.
   * 2. Populating builder objects with the aggregated coverage data.
   * 3. Building the final, immutable CoverageReport model. If there was no coverage data,
   *    a warning will be logged and further execution will be paused.
   * 4. Serializing the main report model to data/report-data.js.
   * 5. Delegating the creation of detailed source file reports to [SourceFileReportOrchestrator].
   * 6. Copying all static HTML, CSS, and JS resources to the output directory.
   *
   * @param inputDirectories A list of directories containing the raw JaCoCo XML report files.
   * @param htmlReportDir The output directory where the final HTML report will be written.
   * @param rootProjectName The display name of the root project.
   * @param rootProjectDir The root directory of the project, used to resolve relative source file paths.
   * @throws GradleException if any of the input XML report files cannot be parsed.
   * @return `true` if the report was generated successfully, `false` if no coverage data was
   * found and report generation was skipped.
   */
  fun orchestrate(inputDirectories: List<File>, htmlReportDir: DirectoryProperty, rootProjectName: String, rootProjectDir: File) : Boolean {
    val reportDir = htmlReportDir.get().asFile

    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)
    val zonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    val formattedTimestamp = zonedDateTime.format(formatter)

    val coverageBuilder = CoverageReportBuilder(rootProjectName, formattedTimestamp)
    val sourceFileReportsBuilder = SourceFileReportsBuilder()

    inputDirectories
      .flatMap { it.walk() }
      .filter { it.isFile && it.extension == "xml" }
      .forEach { xmlFile ->
        try {
          XMLTransformer.transform(xmlFile, rootProjectDir, coverageBuilder, sourceFileReportsBuilder)
        } catch (e: Exception) {
          throw GradleException(
            "Failed to parse code coverage XML report: ${xmlFile.absolutePath}. " + "This may indicate a corrupt or invalid report file.",
            e,
          )
        }
      }

    if (coverageBuilder.moduleReportBuilders.isEmpty()) return false

    val coverageReport = coverageBuilder.build()

    val gson = GsonBuilder().setPrettyPrinting().create()
    val jsonString = gson.toJson(coverageReport)
    val dataDir = reportDir.resolve("data")
    dataDir.mkdirs()
    val reportDataJsFile = dataDir.resolve("report-data.js")
    val jsContent = "const fullReport = $jsonString;"
    reportDataJsFile.writeText(jsContent)

    val sourceFilesDir = reportDir.resolve("sourcefiles")
    SourceFileReportOrchestrator.orchestrate(sourceFileReportsBuilder, rootProjectDir, sourceFilesDir)

    copyResources(htmlReportDir, STATIC_HTML_RESOURCES, this::class.java)
    return true
  }
}
