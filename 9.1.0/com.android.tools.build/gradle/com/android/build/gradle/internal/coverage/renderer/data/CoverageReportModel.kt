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

// =========================================================================================
// HIERARCHICAL REPORT DATA MODEL
// These classes represent the main, aggregated view of the coverage report,
// structured by project, module, package, and class.
// =========================================================================================

/**
 * The root data class for the entire code coverage report. This is the top-level object that will be serialized to JSON.
 *
 * @property name The name of the project.
 * @property timeStamp The timestamp of when the report was generated.
 * @property modules A list of all modules included in the report.
 * @property variantCoverages Aggregated coverage information for each variant, summarized across all modules and test suites.
 * @property numberOfTestsSuites The total count of test suites that contributed to this report.
 */
data class CoverageReport(
  val name: String,
  val timeStamp: String,
  val modules: List<ModuleReport>,
  val variantCoverages: List<VariantCoverage>,
  val numberOfTestsSuites: Int,
)

/**
 * Represents coverage data for a single Gradle module.
 *
 * @property name The name of the module.
 * @property testSuiteCoverages Aggregated coverage for each test suite within this module.
 * @property packages A list of packages within this module that have coverage data.
 */
data class ModuleReport(val name: String, val testSuiteCoverages: List<TestSuiteReportCoverage>, val packages: List<PackageReport>)

/**
 * Represents coverage data for a single Java/Kotlin package.
 *
 * @property name The fully qualified name of the package.
 * @property moduleName The name of the module this package belongs to.
 * @property testSuiteCoverages Aggregated coverage for each test suite within this package.
 * @property classes A list of classes within this package that have coverage data.
 */
data class PackageReport(
  val name: String,
  val moduleName: String,
  val testSuiteCoverages: List<TestSuiteReportCoverage>,
  val classes: List<ClassReport>,
)

/**
 * Represents coverage data for a single Java/Kotlin class.
 *
 * @property name The name of the class.
 * @property packageName The fully qualified name of the package this class belongs to.
 * @property sourceFileName The name of the source file.
 * @property testSuiteCoverages Aggregated coverage for each test suite of this class.
 * @property variantSourceFilePaths A list of paths to the source file for each variant.
 * @property methods A list of methods within this class that have coverage data.
 */
data class ClassReport(
  val name: String,
  val packageName: String,
  val sourceFileName: String,
  val testSuiteCoverages: List<TestSuiteReportCoverage>,
  val variantSourceFilePaths: List<VariantSourceFilePath>,
  val methods: List<MethodReport>,
)

/**
 * Represents coverage data for a single method within a class.
 *
 * @property name The signature of the method.
 * @property variantLineNumbers A list detailing the line numbers this method spans, potentially differing per variant.
 */
data class MethodReport(val name: String, val variantLineNumbers: List<VariantLineNumber>)

// =========================================================================================
// COMMON / SHARED DATA CLASSES
// These are fundamental data structures used throughout the other models.
// =========================================================================================

/**
 * A container for coverage data specific to a test suite.
 *
 * @property name The name of the test suite.
 * @property variantCoverages Aggregated coverage for each variant covered by this test suite.
 */
data class TestSuiteReportCoverage(val name: String, val variantCoverages: List<VariantCoverage>)

/**
 * A container for coverage data specific to a build variant.
 *
 * @property name The name of the variant.
 * @property instruction The instruction coverage information for this variant.
 * @property branch The branch coverage information for this variant.
 */
data class VariantCoverage(val name: String, val instruction: CoverageInfo, val branch: CoverageInfo)

/**
 * A generic container for coverage metrics.
 *
 * @property percent The coverage percentage.
 * @property covered The number of covered items.
 * @property total The total number of items.
 */
data class CoverageInfo(val percent: Int, val covered: Int, val total: Int)

/**
 * Associates a build variant with the absolute path to a specific source file.
 *
 * @property variantName The name of the variant.
 * @property path The absolute path to the source file for this variant.
 */
data class VariantSourceFilePath(val variantName: String, val path: String)

/**
 * Associates a build variant with a specific line number. Useful for methods whose line numbers might change across variants due to
 * different source sets.
 *
 * @property variantName The name of the variant.
 * @property lineNumber The line number relevant to this variant.
 */
data class VariantLineNumber(val variantName: String, val lineNumber: Int)
