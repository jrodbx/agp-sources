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

package com.android.build.gradle.internal.coverage.renderer.builders

import com.android.build.gradle.internal.coverage.renderer.data.CoverageInfo
import com.android.build.gradle.internal.coverage.renderer.data.LineCoverage
import com.android.build.gradle.internal.coverage.renderer.data.SourceFileCoverageReport
import com.android.build.gradle.internal.coverage.renderer.data.TestSuiteCoverage
import com.android.build.gradle.internal.coverage.renderer.data.VariantCoverage
import com.android.build.gradle.internal.coverage.renderer.data.VariantCoverageDetails

/**
 * Mutable builders for constructing the coverage report of a single source file. The build method now takes the source code lines as a
 * parameter, separating the builder from file I/O operations.
 */
class SourceFileReportsBuilder {
  val sourceFileBuilders: MutableMap<String, SourceFileReportBuilder> = mutableMapOf()
}

class SourceFileReportBuilder(val packageFlattenedPath: String) {
  val variantFileCoverageBuilders: MutableMap<String, VariantFileCoverageBuilder> = mutableMapOf()

  fun build(sourceCodeLines: List<String>): SourceFileCoverageReport {
    val variantCoverageSummary =
      variantFileCoverageBuilders.map { (variantName, variantBuilder) -> variantBuilder.buildVariantCoverageDetails(variantName) }

    val lineCoverageDetails =
      sourceCodeLines.mapIndexed { index, lineText ->
        val lineNumber = index + 1
        val variantDetails =
          variantFileCoverageBuilders.map { (variantName, variantBuilder) ->
            variantBuilder.buildLineCoverageDetails(variantName, lineNumber)
          }
        LineCoverage(lineNumber = lineNumber, lineText = lineText, variantCoverageDetails = variantDetails)
      }

    return SourceFileCoverageReport(variantCoverageSummary = variantCoverageSummary, linesCoverages = lineCoverageDetails)
  }
}

class VariantFileCoverageBuilder {
  val testSuiteFileCoverageBuilders: MutableMap<String, TestSuiteFileCoverageBuilder> = mutableMapOf()

  fun buildVariantCoverageDetails(variantName: String): VariantCoverageDetails {
    val coveragesForTestSuites =
      testSuiteFileCoverageBuilders.map { (testSuiteName, testSuiteBuilder) -> testSuiteBuilder.buildSummary(testSuiteName, variantName) }
    return VariantCoverageDetails(variantName = variantName, testSuiteCoverages = coveragesForTestSuites)
  }

  fun buildLineCoverageDetails(variantName: String, lineNumber: Int): VariantCoverageDetails {
    val coveragesForTestSuites =
      testSuiteFileCoverageBuilders.mapNotNull { (testSuiteName, testSuiteBuilder) ->
        testSuiteBuilder.buildLineCoverage(testSuiteName, variantName, lineNumber)
      }
    return VariantCoverageDetails(variantName = variantName, testSuiteCoverages = coveragesForTestSuites)
  }
}

data class TestSuiteFileCoverageBuilder(
  var fileCoverage: CoverageInfo,
  val lineCoverageBuilders: MutableMap<Int, LineCoverageBuilder> = mutableMapOf(),
) {
  fun buildSummary(testSuiteName: String, variantName: String): TestSuiteCoverage {
    return TestSuiteCoverage(
      testSuiteName = testSuiteName,
      variantCoverage =
        VariantCoverage(
          name = variantName,
          instruction = fileCoverage,
          // The source file report from JaCoCo doesn't provide aggregated branch
          // coverage, only line-by-line.
          branch = CoverageInfo(0, 0, 0),
        ),
    )
  }

  fun buildLineCoverage(testSuiteName: String, variantName: String, lineNumber: Int): TestSuiteCoverage? {
    return lineCoverageBuilders[lineNumber]?.let { lineReportBuilder ->
      TestSuiteCoverage(
        testSuiteName = testSuiteName,
        variantCoverage =
          VariantCoverage(
            name = variantName,
            instruction = lineReportBuilder.instructionCoverageInfo,
            branch = lineReportBuilder.branchCoverageInfo,
          ),
      )
    }
  }
}

data class LineCoverageBuilder(val instructionCoverageInfo: CoverageInfo, val branchCoverageInfo: CoverageInfo)
