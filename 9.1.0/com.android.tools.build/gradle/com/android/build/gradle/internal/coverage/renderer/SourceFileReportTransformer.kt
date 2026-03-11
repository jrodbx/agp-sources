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

import com.android.build.gradle.internal.coverage.renderer.builders.SourceFileReportBuilder
import com.android.build.gradle.internal.coverage.renderer.data.SourceFileCoverageReport
import java.io.File
import java.io.IOException

/**
 * Creates the final, immutable [SourceFileCoverageReport] from a fully populated [SourceFileReportBuilder].
 *
 * This function is responsible for enriching the raw coverage data held by the builder with the actual source file content. This separates
 * the file I/O concern from the builder classes, which act as pure data accumulators during the parsing phase.
 *
 * @param path The project-relative path to the source file.
 * @param projectBaseDir The base directory of the project, used to resolve the file path.
 * @param builder A builder containing the complete, aggregated coverage data for the source file across all variants and test suites.
 * @return A single [SourceFileCoverageReport] for the given source file.
 * @throws IOException if the source file cannot be read.
 * @throws IllegalArgumentException if the source file does not exist at the specified path.
 */
fun createSourceFileReport(path: String, projectBaseDir: File, builder: SourceFileReportBuilder): SourceFileCoverageReport {
  val sourceFile = File(projectBaseDir, path)
  require(sourceFile.exists()) { "Failed to generate coverage report. Source file not found: ${sourceFile.absolutePath}" }

  // Read the source file lines. Any IOException will propagate and fail the build,
  // ensuring that missing or unreadable source files do not lead to an incomplete
  // or misleading report.
  val sourceCodeLines = sourceFile.readLines()

  return builder.build(sourceCodeLines)
}
