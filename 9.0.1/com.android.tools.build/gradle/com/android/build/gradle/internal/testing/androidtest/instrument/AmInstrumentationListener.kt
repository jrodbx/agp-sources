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

package com.android.build.gradle.internal.testing.androidtest.instrument

import java.time.Instant

/**
 * Receives events during instrumentation runs.
 *
 * The order of calls is defined below. Calls in square brackets are optional.
 *
 * * `instrumentationStarted`
 * * Zero or more of:
 * ```
 *    * `testStarted`
 *    * `testEnded`
 * ```
 * * `[instrumentationFailed]`
 * * `instrumentationEnded`
 */
interface AmInstrumentationListener {

  /**
   * Reports the start of an instrumentation.
   *
   * @param testCount Total number of tests in the instrumentation. For custom, non-test
   * ```
   *     instrumentations the count is 0.
   * ```
   */
  fun instrumentationStarted(testCount: Int)

  /**
   * Reports the execution start of a test case.
   *
   * @param testIdentifier Identifies the started test case.
   */
  fun testStarted(testIdentifier: TestIdentifier)

  /**
   * Reports the execution end of a test case.
   *
   * @param testResult End result of the test case.
   */
  fun testEnded(testResult: TestResult)

  /**
   * Reports an instrumentation failed to complete due to a fatal error.
   *
   * @param errorMessage Describes the reason for the fatal error.
   */
  fun instrumentationFailed(errorMessage: String)

  /**
   * Reports the end of an instrumentation.
   *
   * @param instrumentationResult [InstrumentationResult] reported at the end of an
   * ```
   *     instrumentation run.
   * ```
   */
  fun instrumentationEnded(instrumentationResult: InstrumentationResult)
}

/** Identifier for an individual test case. */
data class TestIdentifier(
  val testPackage: String,
  val testClass: String,
  val testMethod: String
)

/**
 * Represents the final outcome of a single test case execution.
 *
 * The [status] is a raw integer code mirroring the Android instrumentation output.
 * Common state constants are defined in [AmInstrumentationParser]:
 *
 * - [AmInstrumentationParser.STATUS_CODE_OK]: The test completed successfully.
 * - [AmInstrumentationParser.STATUS_CODE_FAILURE] or [AmInstrumentationParser.STATUS_CODE_ERROR]:
 * The test failed. The [stackTrace] property will contain details of the assertion failure or exception.
 * - [AmInstrumentationParser.STATUS_CODE_IGNORED]: The test was explicitly ignored (e.g., via JUnit's `@Ignore`).
 * - [AmInstrumentationParser.STATUS_CODE_ASSUMPTION_FAILURE]: The test halted due to a failed assumption
 * (e.g., via JUnit's `assume*()` methods).
 */
data class TestResult(
  /** Test case this end result is for. */
  val testIdentifier: TestIdentifier,
  /** Final status of the test. */
  val status: Int,
  /** Start time of the test execution. Recorded by the parser. */
  val startTime: Instant,
  /** End time of the test execution. Recorded by the parser. */
  val endTime: Instant,
  /** In case of failures (assertions, exceptions, assumptions) the stack trace of the failure. */
  val stackTrace: String? = null,
  /** Additional status information written by the instrumentation. */
  val statusBundle: Map<String, String> = mapOf(),
)

/**
 * Results reported at the end of an `am instrument` command.
 *
 * The `code`, also called Session Result Code, reported by `am instrument` is defined in AOSP
 * `frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java` as:
 *
 * * -1: Success
 * * other: Failure
 */
data class InstrumentationResult(
  val code: Int? = null,
  val bundle: Map<String, String> = mapOf(),
) {
  val success
    get() = code == -1
}
