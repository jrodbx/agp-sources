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

package com.android.build.gradle.internal.coverage.renderer.builders

import com.android.build.gradle.internal.coverage.renderer.data.ClassReport
import com.android.build.gradle.internal.coverage.renderer.data.CoverageReport
import com.android.build.gradle.internal.coverage.renderer.data.MethodReport
import com.android.build.gradle.internal.coverage.renderer.data.ModuleReport
import com.android.build.gradle.internal.coverage.renderer.data.PackageReport
import com.android.build.gradle.internal.coverage.renderer.data.TestSuiteReportCoverage
import com.android.build.gradle.internal.coverage.renderer.data.VariantCoverage
import com.android.build.gradle.internal.coverage.renderer.data.VariantLineNumber
import com.android.build.gradle.internal.coverage.renderer.data.VariantSourceFilePath
import com.android.build.gradle.internal.coverage.renderer.xmlparser.XMLTransformer

/**
 * Mutable builder classes used to construct the final immutable coverage report model, as defined in
 * [com.android.build.gradle.internal.coverage.renderer.data].
 *
 * These builders are primarily used during the parsing phase (e.g., by [XMLTransformer] when processing JaCoCo XML files) to accumulate
 * coverage data. Once parsing is complete, the `build()` method on each builder is called to create a final, immutable snapshot of the
 * report data, resulting in a [CoverageReport] object.
 */

/**
 * A mutable builder for creating an immutable [CoverageReport] instance. This is the top-level builder for the entire project's coverage
 * report.
 *
 * @param name The name of the project.
 * @param timeStamp The timestamp of when the report was generated.
 */
class CoverageReportBuilder(private val name: String, private val timeStamp: String) {
  /** A map of module names to their corresponding [ModuleReportBuilder] instances. */
  val moduleReportBuilders = mutableMapOf<String, ModuleReportBuilder>()
  /** Aggregated coverage information for each variant across all modules in the project. */
  val aggregatedVariantCoverages = mutableMapOf<String, VariantCoverage>()
  /** A set of all unique test suite names that contributed to this report. */
  val allTestSuiteNames = mutableSetOf<String>()

  /**
   * Constructs the final, immutable [CoverageReport] from the accumulated data.
   *
   * @return An immutable [CoverageReport] instance.
   */
  fun build(): CoverageReport {
    return CoverageReport(
      name = name,
      timeStamp = timeStamp,
      modules = moduleReportBuilders.values.map { it.build() },
      variantCoverages = aggregatedVariantCoverages.values.toList(),
      numberOfTestsSuites = allTestSuiteNames.size,
    )
  }
}

/**
 * A mutable builder for creating an immutable [ModuleReport] instance. Represents the coverage data for a single Gradle module.
 *
 * @param name The name of the module.
 */
class ModuleReportBuilder(val name: String) {
  /** A map of test suite names to [TestSuiteReportCoverageBuilder]s for suites that ran against this module. */
  val testSuiteCoverages = mutableMapOf<String, TestSuiteReportCoverageBuilder>()
  /** A map of package names to their corresponding [PackageReportBuilder] instances within this module. */
  val packages = mutableMapOf<String, PackageReportBuilder>()

  /**
   * Constructs the final, immutable [ModuleReport] from the accumulated data.
   *
   * @return An immutable [ModuleReport] instance.
   */
  fun build(): ModuleReport {
    return ModuleReport(
      name = name,
      testSuiteCoverages = testSuiteCoverages.values.map { it.build() },
      packages = packages.values.map { it.build() },
    )
  }
}

/**
 * A mutable builder for creating an immutable [PackageReport] instance. Represents coverage data for a single Java/Kotlin package.
 *
 * @param name The fully qualified name of the package.
 * @param moduleName The name of the module this package belongs to.
 */
class PackageReportBuilder(val name: String, private val moduleName: String) {
  /** A map of test suite names to [TestSuiteReportCoverageBuilder]s for suites covering this package. */
  val testSuiteCoverages = mutableMapOf<String, TestSuiteReportCoverageBuilder>()
  /** A map of class names to their corresponding [ClassReportBuilder] instances within this package. */
  val classes = mutableMapOf<String, ClassReportBuilder>()

  /**
   * Constructs the final, immutable [PackageReport] from the accumulated data.
   *
   * @return An immutable [PackageReport] instance.
   */
  fun build(): PackageReport {
    return PackageReport(
      name = name,
      moduleName = moduleName,
      testSuiteCoverages = testSuiteCoverages.values.map { it.build() },
      classes = classes.values.map { it.build() },
    )
  }
}

/**
 * A mutable builder for creating an immutable [ClassReport] instance. Represents coverage data for a single Java/Kotlin class.
 *
 * @param name The name of the class.
 * @param packageName The fully qualified name of the package this class belongs to.
 * @param sourceFileName The name of the source file.
 */
class ClassReportBuilder(val name: String, val packageName: String, val sourceFileName: String) {
  /** A map of test suite names to [TestSuiteReportCoverageBuilder]s for suites covering this class. */
  val testSuiteCoverages = mutableMapOf<String, TestSuiteReportCoverageBuilder>()
  /** A map of method signatures to their corresponding [MethodReportBuilder] instances. */
  val methods = mutableMapOf<String, MethodReportBuilder>()
  /** A list of paths to the source file for each variant, as paths can differ. */
  val variantSourceFilePaths = mutableListOf<VariantSourceFilePath>()

  /**
   * Constructs the final, immutable [ClassReport] from the accumulated data.
   *
   * @return An immutable [ClassReport] instance.
   */
  fun build(): ClassReport {
    return ClassReport(
      name = name,
      packageName = packageName,
      sourceFileName = sourceFileName,
      testSuiteCoverages = testSuiteCoverages.values.map { it.build() },
      variantSourceFilePaths = variantSourceFilePaths.toList(),
      methods = methods.values.map { it.build() },
    )
  }
}

/**
 * A mutable builder for creating an immutable [TestSuiteReportCoverage] instance. Represents the coverage data generated by a single test
 * suite.
 *
 * @param name The name of the test suite.
 */
class TestSuiteReportCoverageBuilder(val name: String) {
  /** Aggregated coverage for each variant covered by this test suite. */
  val variantCoverages = mutableListOf<VariantCoverage>()

  /**
   * Constructs the final, immutable [TestSuiteReportCoverage] from the accumulated data.
   *
   * @return An immutable [TestSuiteReportCoverage] instance.
   */
  fun build(): TestSuiteReportCoverage {
    return TestSuiteReportCoverage(name = name, variantCoverages = variantCoverages.toList())
  }
}

/**
 * A mutable builder for creating an immutable [MethodReport] instance. Represents coverage data for a single method within a class.
 *
 * @param name The signature of the method.
 */
class MethodReportBuilder(val name: String) {
  /** A list detailing the starting line numbers for this method, which may differ per variant due to varying source sets. */
  val variantLineNumbers = mutableListOf<VariantLineNumber>()

  /**
   * Constructs the final, immutable [MethodReport] from the accumulated data.
   *
   * @return An immutable [MethodReport] instance.
   */
  fun build(): MethodReport {
    return MethodReport(name = name, variantLineNumbers = variantLineNumbers.toList())
  }
}
