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

package com.android.build.gradle.internal.test.report

/** Defines the complete data model for the final JSON report. */

/**
 * The root of the test report data model.
 *
 * @property variants A list of all variant names included in this report (e.g., "debug", "release"). These are used as keys in the
 *   [Function.results] map.
 * @property modules A list of modules in the project (e.g., ":app", ":lib").
 */
data class RootReport(val variants: List<String>, val modules: List<Module>)

/**
 * Represents a Gradle module in the test report.
 *
 * @property name The path of the module (e.g., ":app").
 * @property testSuites A list of test suites within this module (e.g., "testDebugUnitTest").
 */
data class Module(val name: String, val testSuites: List<TestSuite>)

/**
 * Represents a test suite, typically corresponding to a specific test task or type.
 *
 * @property name The name of the test suite (e.g., "common" or "UnitTest").
 * @property packages A list of Java/Kotlin packages containing tests.
 */
data class TestSuite(val name: String, val packages: List<Package>)

/**
 * Represents a Java/Kotlin package containing test classes.
 *
 * @property name The package name (e.g., "com.example.mytapp").
 * @property classes A list of test classes within this package.
 */
data class Package(val name: String, val classes: List<ClassType>)

/**
 * Represents a test class.
 *
 * @property name The simple name of the class (e.g., "ExampleUnitTest").
 * @property functions A list of test methods (functions) in this class.
 */
data class ClassType(val name: String, val functions: List<Function>)

/**
 * Represents a single test function execution result.
 *
 * @property status The result status (e.g., "passed", "failed", "skipped").
 * @property stackTrace The stack trace if the test failed, or null otherwise.
 */
data class TestResults(val status: String, val stackTrace: String? = null)

/**
 * Represents a test method with results across multiple variants.
 *
 * @property name The name of the test function (e.g., "testAddition").
 * @property results A map where keys are variant names (matching [RootReport.variants]) and values are the [TestResults] for that variant.
 *   This allows aggregating results for the same test across different build variants.
 */
data class Function(
  val name: String,
  // Store TestResult objects instead of simple Strings
  val results: Map<String, TestResults> = emptyMap(),
)
