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
 * Parses the raw output of an Instrumentation and provides results via [AmInstrumentationListener].
 *
 * The parser works for the output of `am instrument -w -r ...`. It handles individual test results,
 * as well as custom instrumentations.
 *
 * Instances of this class are mutable. To use them concurrently, clients must surround each method
 * invocation (or invocation sequence) with external synchronization of the clients choosing.
 *
 * ## Reporting test results
 *
 * For individual test results, the parser expects a series of status key/value pairs, followed by a
 * status code. Status codes identify the individual test phase: started(1), pass(0), error(-1),
 * fail(-2), ignored(-3), or assumption failure(-4).
 *
 * ## Reporting (custom) instrumentation results
 *
 * At the end, the parser expects an optional series of result key/value pairs, followed by an
 * instrumentation code. Instrumentation codes identify the end state: ok(-1), cancelled(0). These
 * codes map to `Activity.RESULT_*` constants.
 *
 * ## Sample output
 *
 * ```
 * ...
 * INSTRUMENTATION_STATUS_CODE: 1
 * INSTRUMENTATION_STATUS: class=com.example.Tests
 * INSTRUMENTATION_STATUS: test=testExample
 * INSTRUMENTATION_STATUS: current=2
 * INSTRUMENTATION_STATUS: numtests=2
 * INSTRUMENTATION_STATUS: stack=com.example.Tests#testExample:123
 *         at com.example.Tests
 * INSTRUMENTATION_STATUS_CODE: -2
 * INSTRUMENTATION_RESULT: infor1=value
 * INSTRUMENTATION_RESULT: info2=multi
 * line
 * value
 * INSTRUMENTATION_CODE: -1
 * ```
 */
class AmInstrumentationParser(
    private val listeners: Set<AmInstrumentationListener> = emptySet(),
    private val testTimeTrackerFactory: () -> TestTimeTracker = { TestTimeTracker() },
) {

    /** The result of the Instrumentation, available after parse completion. */
    var result: InstrumentationResult? = null
        private set

    /**
     * Potential error reported by the Instrumentation, available after parse completion.
     *
     * Same value as reported through [AmInstrumentationListener.instrumentationFailed].
     */
    var instrumentationError: String? = null
        private set

    /** The currently parsed, potentially multi-line, key. */
    private var currentKey: String? = null

    /** The currently parsed, potentially multi-line, instrumentation result value. */
    private var currentResultValue: StringBuilder? = null

    /** The currently parsed, potentially multi-line, test status value. */
    private var currentStatusValue: StringBuilder? = null

    private var code: Int? = null
    private var bundle = mutableMapOf<String, String>()

    private var expectedTestsCount: Int? = null
    private var completedTestsCount = 0

    private var currentTestRecord: TestRecord? = null

    /** Message from the Instrument.StatusReporter.onError callback */
    private var onErrorMessage: String? = null

    /** Whether the caller signaled via [done()] that parsing has completed. */
    private var parsingEnded = false

    /** Whether the end terminal `INSTRUMENTATION_CODE` was observed. */
    private var instrumentationEnded = false

    private var instrumentationStartedReported = false
    private var instrumentationEndedReported = false
    private var testStartedReported = false

    /** Parses an individual Instrumentation output line. */
    fun parse(line: String) {
        check(!parsingEnded) { "Parsing was completed, but parse() was called again!" }
        if (line.startsWith(PREFIX) || line.startsWith(ON_ERROR)) {
            storeCurrentValue()
        }
        when {
            line.startsWith(STATUS) -> {
                parseStatusKeyValue(line, STATUS.length)
            }

            line.startsWith(STATUS_CODE) -> {
                val statusCode = line.substring(STATUS_CODE.length).trim().toIntOrNull()
                handleStatusCode(statusCode)
            }

            line.startsWith(STATUS_FAILED) -> {
                handleInstrumentationEnded()
            }

            line.startsWith(ON_ERROR) -> {
                onErrorMessage = line.trim()
            }

            line.startsWith(ABORTED) -> {
                // Messages from ON_ERROR have precedence as they usually contain more information
                onErrorMessage = onErrorMessage ?: line.substring(ABORTED.length).trim()
            }

            line.startsWith(CODE) -> {
                code = line.substring(CODE.length).trim().toIntOrNull()
                instrumentationEnded = true
                handleInstrumentationEnded()
            }

            line.startsWith(RESULT) -> {
                parseResultKeyValue(line, RESULT.length)
            }

            currentStatusValue != null -> {
                currentStatusValue?.appendLine()?.append(line)
            }

            currentResultValue != null -> {
                currentResultValue?.appendLine()?.append(line)
            }
        }
    }

    /**
     * Signals the parser that no additional input will be provided.
     *
     * The parser will call any outstanding listener methods to complete the listener lifecycle.
     */
    fun done() {
        check(!parsingEnded) { "Parsing was completed, but done() was called again!" }
        parsingEnded = true

        // In error cases the parser can be in the middle of parsing a multi-line value.
        storeCurrentValue()

        handleInstrumentationEnded()
    }

    private fun parseResultKeyValue(line: String, startIndex: Int) {
        val idx = line.indexOf('=', startIndex)
        if (idx != -1) {
            currentKey = line.substring(startIndex, idx).trim()
            currentResultValue = StringBuilder().append(line.substring(idx + 1))
        }
    }

    private fun parseStatusKeyValue(line: String, startIndex: Int) {
        val idx = line.indexOf('=', startIndex)
        if (idx != -1) {
            currentKey = line.substring(startIndex, idx).trim()
            currentStatusValue = StringBuilder().append(line.substring(idx + 1))
        }
    }

    private fun storeCurrentValue() {
        val key = currentKey ?: return
        val statusValue = currentStatusValue
        if (statusValue != null) {
            getOrCreateCurrentTestRecord().storeStatus(key, statusValue.toString())
            currentStatusValue = null
            return
        }
        val resultValue = currentResultValue
        if (resultValue != null) {
            bundle[key] = resultValue.toString()
            currentResultValue = null
        }
    }

    /**
     * Handles the reporting when an individual test result has been parsed. This can be the result of
     * a started test case, or a completed test case.
     */
    private fun handleStatusCode(statusCode: Int?) {
        val testRecord = currentTestRecord

        if (testRecord?.isComplete() != true) {
            // An error occurred that is not understood by the parser. The record is dropped and we rely
            // on the check for expected number of executed tests to report the instrumentation failure.
            return
        }

        if (!instrumentationStartedReported) {
            reportInstrumentationStarted(testRecord)
        }

        if (statusCode == STATUS_CODE_IN_PROGRESS) {
            // Not used by any known Android test runner, and thus not supported by the parser.
            return
        }

        if (statusCode == STATUS_CODE_START) {
            reportTestStarted(testRecord)
            testRecord.markTestStarted()
            return
        }

        // Either the end of a test case was reached, or the test case never started successfully.

        currentTestRecord = null

        if (testStartedReported) {
            reportTestEnded(testRecord.toTestResult(statusCode))
        }

        if (testRecord.error != null) {
            handleInstrumentationEnded(testRecord.error)
        }
    }

    private fun handleInstrumentationEnded(errorCause: String? = null) {
        if (instrumentationEndedReported) {
            return
        }

        val shortMsg = bundle[STATUS_SHORTMSG]
        val streamMsg = bundle[STATUS_STREAM] ?: ""
        val wasInstrumentationStartedReported = instrumentationStartedReported
        val notEnoughTestsError: String? =
            if (expectedTestsCount ?: 0 > completedTestsCount) {
                "Expected $expectedTestsCount tests, received $completedTestsCount"
            } else {
                null
            }

        var error =
            when {
                errorCause != null -> errorCause
                // ActivityManagerService or custom instrumentation reported an instrumentation failure
                shortMsg != null -> "Instrumentation run failed due to $shortMsg"
                // Parsing completed without observing the instrumentation start and end output
                !wasInstrumentationStartedReported && !instrumentationEnded -> ERROR_NO_TEST_RESULTS
                // Less than the expected number of tests were seen/completed
                notEnoughTestsError != null -> notEnoughTestsError
                // The instrumentation fatally failed while being in -e log true mode. Resulting in only the
                // stream containing the exception.
                streamMsg.contains(FATAL_ERROR_MSG) -> streamMsg
                else -> null
            }

        if (error != null) {
            // In certain cases the causing error does not have enough information. If possible, attach
            // additional information.
            error =
                when {
                    onErrorMessage != null -> "$error. $onErrorMessage"
                    STREAM_FAILURES_REGEX.containsMatchIn(streamMsg) -> "$error. $streamMsg"
                    else -> error
                }
        }

        // Instrumentation crashed before any test case information was reported, but the end stream
        // has details about the crash.
        if (
            error == null &&
            expectedTestsCount == null &&
            STREAM_FAILURES_REGEX.containsMatchIn(streamMsg) &&
            streamMsg.contains(STREAM_INSTRUMENTATION_PROCESS_CRASHED)
        ) {
            error = streamMsg.trim()
        }

        if (!instrumentationStartedReported) {
            // If the instrumentation start wasn't reported yet it can be:
            //  * A custom instrumentation
            //  * A test run with no test cases
            //  * An error launching the instrumentation
            reportInstrumentationStarted(currentTestRecord)
        }

        if (testStartedReported) {
            // Reported the start of a test case, but never the end.
            val testRecord = getOrCreateCurrentTestRecord()
            currentTestRecord = null
            reportTestEnded(testRecord.toTestResult(STATUS_CODE_ERROR))
        }

        this.instrumentationError = error

        if (error != null) {
            reportInstrumentationFailed("Test run failed to complete. $error")
        }

        val result =
            InstrumentationResult(
                code,
                bundle.toMutableMap().also { knownStatus.forEach(it::remove) })
        this.result = result

        reportInstrumentationEnded(result)
    }

    private fun reportInstrumentationStarted(testRecord: TestRecord?) {
        expectedTestsCount = testRecord?.numTests
        instrumentationStartedReported = true
        listeners.forEach { it.instrumentationStarted(expectedTestsCount ?: 0) }
    }

    private fun reportTestStarted(testRecord: TestRecord) {
        val testIdentifier = testRecord.toTestIdentifier()
        testStartedReported = true
        listeners.forEach { it.testStarted(testIdentifier) }
    }

    private fun reportTestEnded(testResult: TestResult) {
        completedTestsCount += 1
        testStartedReported = false
        listeners.forEach { it.testEnded(testResult) }
    }

    private fun reportInstrumentationFailed(message: String) =
        listeners.forEach { it.instrumentationFailed(message) }

    private fun reportInstrumentationEnded(result: InstrumentationResult) {
        instrumentationEndedReported = true
        listeners.forEach { it.instrumentationEnded(result) }
    }

    private fun getOrCreateCurrentTestRecord(): TestRecord {
        var testResult = currentTestRecord
        if (testResult == null) {
            testResult = TestRecord(testTimeTrackerFactory())
            currentTestRecord = testResult
        }
        return testResult
    }

    /** Data holder for in-progress test information. */
    private class TestRecord(private val testTimeTracker: TestTimeTracker) {

        var numTests: Int? = null
            private set
        var error: String? = null
            private set
        private var testClass: String? = null
        private var testMethod: String? = null
        private var stackTrace: String? = null
        private val statusBundle = mutableMapOf<String, String>()
        private var startedTestClass: String? = null
        private var startedTestMethod: String? = null

        init {
            testTimeTracker.testStart()
        }

        fun markTestStarted() {
            // We reset the class and method to detect if the "test-end" output was written, and thus
            // we consider the record as complete. This is needed as custom test code can report status
            // information via `Instrumentation.sendStatus()` and use a final status code. E.g. the
            // AndroidX benchmark library v1.0.0 has this bug.
            // A copy of the class and method is stored for creating a `TestResult` from this record that
            // might not have the "test-end" output, as it can happen in exceptional cases.
            startedTestClass = testClass
            startedTestMethod = testMethod
            testClass = null
            testMethod = null
        }

        fun isComplete(): Boolean {
            return (testClass != null && testMethod != null) || error != null
        }

        fun storeStatus(key: String, value: String) {
            when (key) {
                STATUS_CLASS -> testClass = value.trim()
                STATUS_TEST -> testMethod = value.trim()
                STATUS_STACK -> stackTrace = value
                STATUS_NUMTESTS -> numTests = value.trim().toIntOrNull()
                STATUS_ERROR -> error = value
                !in knownStatus -> statusBundle[key] = value
            }
        }

        fun toTestIdentifier(): TestIdentifier {
            val testClass = testClass ?: startedTestClass ?: ""
            return TestIdentifier(
                testPackage = testClass.substringBeforeLast('.', ""),
                testClass = testClass.substringAfterLast('.', testClass),
                testMethod = testMethod ?: startedTestMethod ?: "unknown test method",
            )
        }

        fun toTestResult(statusCode: Int?): TestResult {
            testTimeTracker.testEnd()
            return TestResult(
                testIdentifier = toTestIdentifier(),
                status = statusCode ?: STATUS_CODE_FAILURE,
                startTime = Instant.ofEpochMilli(testTimeTracker.testTimingData.startTime),
                endTime = Instant.ofEpochMilli(testTimeTracker.testTimingData.endTime),
                stackTrace = stackTrace,
                statusBundle = statusBundle.toMap(),
            )
        }
    }

    companion object {
        private const val ABORTED = "INSTRUMENTATION_ABORTED: "
        private const val CODE = "INSTRUMENTATION_CODE: "
        private const val ON_ERROR = "onError: "
        private const val RESULT = "INSTRUMENTATION_RESULT: "
        private const val STATUS = "INSTRUMENTATION_STATUS: "
        private const val STATUS_CODE = "INSTRUMENTATION_STATUS_CODE: "
        private const val STATUS_FAILED = "INSTRUMENTATION_FAILED: "
        private const val PREFIX = "INSTRUMENTATION_"

        private const val STATUS_CLASS = "class"
        private const val STATUS_CURRENT = "current"
        private const val STATUS_ERROR = "Error"
        private const val STATUS_ID = "id"
        private const val STATUS_NUMTESTS = "numtests"
        private const val STATUS_SHORTMSG = "shortMsg"
        private const val STATUS_STACK = "stack"
        private const val STATUS_STREAM = "stream"
        private const val STATUS_TEST = "test"

        private val knownStatus =
            setOf(
                STATUS_CLASS,
                STATUS_CURRENT,
                STATUS_ERROR,
                STATUS_ID,
                STATUS_NUMTESTS,
                STATUS_SHORTMSG,
                STATUS_STACK,
                STATUS_STREAM,
                STATUS_TEST,
            )

        // Status codes copied from frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java.

        /** Test is starting */
        const val STATUS_CODE_START = 1

        /** Test reported status while in progress */
        const val STATUS_CODE_IN_PROGRESS = 2

        /** Test completed successfully */
        const val STATUS_CODE_OK = 0

        /** Test completed with an error (JUnit3 only) */
        const val STATUS_CODE_ERROR = -1

        /** Test completed with a failure */
        const val STATUS_CODE_FAILURE = -2

        /** Test was ignored */
        const val STATUS_CODE_IGNORED = -3

        /** Test completed with an assumption failure */
        const val STATUS_CODE_ASSUMPTION_FAILURE = -4

        /** Error message when no test results were received from the instrumentation */
        private const val ERROR_NO_TEST_RESULTS = "No test results"

        /** Message reported by test runner when some critical failure occurred */
        private const val FATAL_ERROR_MSG = "Fatal exception when running tests"

        /** Message reported by orchestrator when the instrumentation aborted abnormally */
        private const val STREAM_INSTRUMENTATION_PROCESS_CRASHED =
            "Test instrumentation process crashed"

        /** Regex for reported errors (fatal & non-fatal) printed at the end of the instrumentation. */
        private val STREAM_FAILURES_REGEX = Regex("There (?:was|were) \\d+ failure")
    }
}
