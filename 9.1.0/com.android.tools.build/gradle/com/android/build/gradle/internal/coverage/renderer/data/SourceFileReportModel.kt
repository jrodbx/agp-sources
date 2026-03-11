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

package com.android.build.gradle.internal.coverage.renderer.data

/**
 * Data model for the code coverage report of a single source file.
 *
 * This structure combines coverage information from multiple build variants and their corresponding test suites into a unified view. The
 * content of the source file itself is assumed to be identical across all variants included in the report.
 *
 * @property variantCoverageSummary A summary of the overall coverage for the entire source file, with details provided for each variant and
 *   test suite.
 * @property linesCoverages A list containing the detailed, line-by-line coverage information, aggregated across all relevant variants and
 *   test suites.
 */
data class SourceFileCoverageReport(val variantCoverageSummary: List<VariantCoverageDetails>, val linesCoverages: List<LineCoverage>)

/**
 * Data model representing the aggregated coverage information for a single line of source code.
 *
 * @property lineNumber The line number in the source file.
 * @property lineText The text content of the source code at this line.
 * @property variantCoverageDetails A list of coverage details for this line, with each entry representing the coverage from a specific
 *   build variant.
 */
data class LineCoverage(val lineNumber: Int, val lineText: String, val variantCoverageDetails: List<VariantCoverageDetails>)

/**
 * Represents the coverage information for a single build variant, broken down by test suite. This class is used for both file-level
 * summaries and individual line coverage.
 *
 * @property variantName The name of the build variant.
 * @property testSuiteCoverages A list of coverage results from individual test suites that were run against this variant.
 */
data class VariantCoverageDetails(val variantName: String, val testSuiteCoverages: List<TestSuiteCoverage>)

/**
 * Contains the coverage data from a specific test suite for a given build variant.
 *
 * @property testSuiteName The name of the test suite or "Aggregated", depending on the grouping by test suite or package.
 * @property variantCoverage The coverage data of this line for this variant under this test suite.
 */
data class TestSuiteCoverage(val testSuiteName: String, val variantCoverage: VariantCoverage)
