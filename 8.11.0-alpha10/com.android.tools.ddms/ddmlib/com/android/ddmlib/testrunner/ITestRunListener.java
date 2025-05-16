/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ddmlib.testrunner;

import java.util.Map;

/**
 * Receives event notifications during instrumentation test runs.
 * <p>
 * Patterned after org.junit.runner.notification.RunListener
 * <p>
 * The sequence of calls will be:
 * <ul>
 * <li> testRunStarted
 * <li> testStarted
 * <li> [testFailed]
 * <li> [testAssumptionFailure]
 * <li> [testIgnored]
 * <li> testEnded
 * <li> ....
 * <li> [testRunFailed]
 * <li> testRunEnded
 * </ul>
 */
public interface ITestRunListener {

    /**
     * Reports the start of a test run.
     *
     * @param runName the test run name
     * @param testCount total number of tests in test run
     */
    void testRunStarted(String runName, int testCount);

    /**
     * Reports the start of an individual test case.
     *
     * @param test identifies the test
     */
    void testStarted(TestIdentifier test);

    /**
     * Reports the failure of a individual test case.
     *
     * <p>Will be called between testStarted and testEnded.
     *
     * @param test identifies the test
     * @param trace stack trace of failure
     */
    void testFailed(TestIdentifier test, String trace);

    /**
     * Called when an atomic test flags that it assumes a condition that is
     * false
     *
     * @param test identifies the test
     * @param trace stack trace of failure
     */
    void testAssumptionFailure(TestIdentifier test, String trace);

    /**
     * Called when a test will not be run, generally because a test method is annotated
     * with org.junit.Ignore.
     *
     * @param test identifies the test
     */
    void testIgnored(TestIdentifier test);

    /**
     * Reports the execution end of an individual test case.
     *
     * <p>If {@link #testFailed} was not invoked, this test passed. Also returns any key/value
     * metrics which may have been emitted during the test case's execution.
     *
     * @param test identifies the test
     * @param testMetrics a {@link Map} of the metrics emitted during the execution of the test case
     *     by {@code android.app.Instrumentation#sendStatus}. The insertion order is preserved
     *     unless you emit a same key multiple times. Note that standard keys defined in {@link
     *     IInstrumentationResultParser.StatusKeys} are filtered out of this Map. Ddmlib may add
     *     extra test metrics defined in {@link IInstrumentationResultParser.StatusKeys}.
     */
    void testEnded(TestIdentifier test, Map<String, String> testMetrics);

    /**
     * Reports test run failed to complete due to a fatal error.
     *
     * @param errorMessage {@link String} describing reason for run failure.
     */
    void testRunFailed(String errorMessage);

    /**
     * Reports test run stopped before completion due to a user request.
     *
     * @param elapsedTime device reported elapsed time, in milliseconds
     * @deprecated This callback is never be invoked. To be deleted.
     */
    @Deprecated
    void testRunStopped(long elapsedTime);

    /**
     * Reports end of test run.
     *
     * @param elapsedTime device reported elapsed time, in milliseconds
     * @param runMetrics a {@link Map} of the metrics emitted during the execution of the test case
     *     by {@code android.app.Instrumentation#addResults}. The insertion order is preserved
     *     unless you emit a same key multiple times. Note that standard keys defined in {@link
     *     IInstrumentationResultParser.StatusKeys} are filtered out of this Map. Ddmlib may add
     *     extra test metrics defined in {@link IInstrumentationResultParser.StatusKeys}.
     */
    void testRunEnded(long elapsedTime, Map<String, String> runMetrics);
}
