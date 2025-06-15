/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.commands.am.InstrumentationData;
import com.android.ddmlib.Log;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the 'protoStd output mode' results of an instrumentation test run from shell and informs a
 * ITestRunListener of the results.
 *
 * <p>Am instrument command with "-m" option outputs test execution status in binary protobuf format
 * incrementally. The output protobuf message is {@link InstrumentationData.Session}, which has two
 * fields: 1) a repeated field of {@link InstrumentationData.TestStatus}, 2) {@link
 * InstrumentationData.SessionStatus}. The am instrument command outputs test status message
 * before/after each test execution. {@link #addOutput} is invoked with a {@code data} argument
 * which is a serialized bytes of {@code TestStatus}(es). When all tests are done, the command
 * outputs {@code SessionStatus} at last.
 *
 * <p>See
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/cmds/am/proto/instrumentation_data.proto
 * for a complete protobuf definition.
 *
 * <p>{@code InstrumentationProtoResultParser} is a state-machine and states are defined in {@link
 * InstrumentationProtoResultParserState}. The state begins with {@link
 * InstrumentationProtoResultParserState#NOT_STARTED}. When it sees a first test case status, it
 * moves to {@link InstrumentationProtoResultParserState#RUNNING}. The state changes to {@link
 * InstrumentationProtoResultParserState#FINISHED} if all tests are done or {@link
 * InstrumentationProtoResultParserState#CANCELLED} if it is requested by calling {@link #cancel}.
 *
 * <p>Every time a new test status is available, it will be informed to the registered listeners.
 */
public class InstrumentationProtoResultParser implements IInstrumentationResultParser {

    /** An internal state of {@link InstrumentationProtoResultParser}. */
    private enum InstrumentationProtoResultParserState {
        /** Test run is not started. */
        NOT_STARTED(false),
        /** Test is currently running. */
        RUNNING(false),
        /** All test executions are completed. */
        FINISHED(true),
        /** Test run has been cancelled. */
        CANCELLED(true);

        private final boolean mIsTerminalState;

        InstrumentationProtoResultParserState(boolean isTerminalState) {
            mIsTerminalState = isTerminalState;
        }

        public boolean isTerminalState() {
            return mIsTerminalState;
        }
    }

    /** Represents a status of a single test case execution. */
    private static class TestStatus {
        /**
         * The result code of this test case.
         *
         * @see IInstrumentationResultParser.StatusCodes
         */
        private int mTestResultCode;

        /** The logcat output emitted during this test case */
        private StringBuilder mLogcat = new StringBuilder();

        /** A maximum length of logcat message to be stored and reported. */
        private static final int MAX_LOGCAT_LENGTH = 10000;

        /**
         * The test status metrics emitted during the execution of the test case by {@code
         * android.app.Instrumentation#sendStatus}. The insertion order is preserved unless the test
         * emits a same key multiple times. Note that standard keys defined in {@link
         * IInstrumentationResultParser.StatusKeys} should be filtered out of this Map.
         */
        private final LinkedHashMap<String, String> mTestMetrics = new LinkedHashMap<>();

        public TestStatus(int testResultCode) {
            mTestResultCode = testResultCode;
        }

        public void setTestResultCode(int testResultCode) {
            mTestResultCode = testResultCode;
        }

        public int getTestResultCode() {
            return mTestResultCode;
        }

        public void appendLogcat(String logcat) {
            if (mLogcat.length() >= MAX_LOGCAT_LENGTH) {
                return;
            }
            if (mLogcat.length() + logcat.length() < MAX_LOGCAT_LENGTH) {
                mLogcat.append(logcat);
            } else {
                mLogcat.append(logcat.subSequence(0, MAX_LOGCAT_LENGTH - mLogcat.length()));
            }
        }

        public void clearLogcat() {
            mLogcat = new StringBuilder();
        }

        public String getLogcat() {
            return mLogcat.toString();
        }

        public void putTestMetrics(String key, String value) {
            mTestMetrics.put(key, value);
        }

        public void putAllTestMetrics(Map<String, String> testMetrics) {
            mTestMetrics.putAll(testMetrics);
        }

        public Map<String, String> getTestMetrics() {
            return ImmutableMap.copyOf(mTestMetrics);
        }
    }

    /** The tag to be used for logging. */
    private static final String LOG_TAG = "InstrumentationProtoResultParser";

    private final String mRunName;
    private final Collection<ITestRunListener> mListeners;

    /** True if current test run has been canceled by user. */
    private InstrumentationProtoResultParserState mState =
            InstrumentationProtoResultParserState.NOT_STARTED;

    /**
     * A received byte array to be processed and translated into {@link InstrumentationData.Session}
     * message.
     */
    private ByteArrayOutputStream mPendingData = new ByteArrayOutputStream();

    /** The latest test statuses. Uses LinkedHashMap to preserve the insertion order. */
    private final LinkedHashMap<TestIdentifier, TestStatus> mTestStatuses = new LinkedHashMap<>();

    /**
     * A regex patter to be used for finding test execution elapsed time from session output string.
     */
    private final Pattern mTimePattern = Pattern.compile("Time: \\s*([\\d\\,]*[\\d\\.]+)");

    /**
     * Constructs {@link InstrumentationProtoResultParser}.
     *
     * @param runName the test run name to provide to {@link ITestRunListener#testRunStarted}
     * @param listeners informed of test results as the tests are executing
     */
    public InstrumentationProtoResultParser(
            String runName, Collection<ITestRunListener> listeners) {
        mRunName = runName;
        mListeners = listeners;
    }

    /**
     * This method is called every time some new data is available.
     *
     * @param data a serialized data of {@link InstrumentationData.Session} message. If {@code data}
     *     is an incomplete chunk, they are added into an internal buffer and will be processed in
     *     the next {@link #addOutput} call.
     * @param offset an offset of the new data stored in {@code data}
     * @param length bytes of a new data in {@code data} to be processed.
     */
    @Override
    public void addOutput(byte[] data, int offset, int length) {
        if (mState.isTerminalState()) {
            return;
        }

        mPendingData.write(data, offset, length);
        try {
            InstrumentationData.Session session =
                    InstrumentationData.Session.parseFrom(mPendingData.toByteArray());
            mPendingData.reset();
            updateState(session);
        } catch (InvalidProtocolBufferException e) {
            // InvalidProtocolBufferException may happen if a given new output data is incomplete.
            // Let's just skip updating mSession and wait for incoming data.
        }
    }

    private void updateState(InstrumentationData.Session session) {
        for (InstrumentationData.TestStatus status : session.getTestStatusList()) {
            // Notify test run started if this is the initial test state update.
            if (mState == InstrumentationProtoResultParserState.NOT_STARTED) {
                final int numTests =
                        status.getResults()
                                .getEntriesList()
                                .stream()
                                .filter(entry -> entry.getKey().equals(StatusKeys.NUMTESTS))
                                .map(entry -> entry.getValueInt())
                                .findFirst()
                                .orElse(0);
                if (numTests == 0) {
                    return;
                }

                mState = InstrumentationProtoResultParserState.RUNNING;
                for (ITestRunListener listener : mListeners) {
                    listener.testRunStarted(mRunName, numTests);
                }
            }

            String testClassName = "";
            String testMethodName = "";
            int currentTestIndex = -1;
            String stackTrace = "";
            LinkedHashMap<String, String> testMetrics = new LinkedHashMap<>();
            for (InstrumentationData.ResultsBundleEntry entry :
                    status.getResults().getEntriesList()) {
                switch (entry.getKey()) {
                    case StatusKeys.CLASS:
                        testClassName = entry.getValueString();
                        break;
                    case StatusKeys.TEST:
                        testMethodName = entry.getValueString();
                        break;
                    case StatusKeys.STACK:
                        stackTrace = entry.getValueString();
                        break;
                    case StatusKeys.CURRENT:
                        currentTestIndex = entry.getValueInt();
                        break;
                    default:
                        if (!StatusKeys.KNOWN_KEYS.contains(entry.getKey())) {
                            testMetrics.put(
                                    entry.getKey(), getResultsEntryBundleValueInString(entry));
                        }
                }
            }

            // If both test class name and method name are missing, assume the previous test is
            // the current one. This happens if your test sends custom test status by
            // Instrumentation.sendStatus() method.
            // Also, we should ignore reported test ResultCode from sendStatus() call because
            // it is often misused and Activity.RESULT_OK (= -1) is set. -1 means ERROR here
            // which causes ddmlib to fail.
            Optional<Integer> resultCodeOverride = Optional.empty();
            if (isNullOrEmpty(testClassName) && isNullOrEmpty(testMethodName)) {
                Optional<Map.Entry<TestIdentifier, TestStatus>> previousTestStatus =
                        mTestStatuses.entrySet().stream().reduce((first, second) -> second);
                if (previousTestStatus.isPresent()) {
                    testClassName = previousTestStatus.get().getKey().getClassName();
                    testMethodName = previousTestStatus.get().getKey().getTestName();
                    currentTestIndex = previousTestStatus.get().getKey().getTestIndex();
                    resultCodeOverride =
                            Optional.of(previousTestStatus.get().getValue().mTestResultCode);
                } else {
                    testClassName = "";
                    testMethodName = "";
                }
            }

            if (!isNullOrEmpty(testClassName) && !isNullOrEmpty(testMethodName)) {
                updateTestState(
                        testClassName,
                        testMethodName,
                        currentTestIndex,
                        resultCodeOverride.orElse(status.getResultCode()),
                        status.getLogcat(),
                        stackTrace,
                        testMetrics);
            }
        }

        if (session.hasSessionStatus()) {
            mState = InstrumentationProtoResultParserState.FINISHED;

            LinkedHashMap<String, String> testRunMetrics = new LinkedHashMap<>();
            switch (session.getSessionStatus().getStatusCode()) {
                    // System server is crashed during the test execution.
                case SESSION_ABORTED:
                    {
                        String errorMessage = session.getSessionStatus().getErrorText();

                        // Report the test case failure gracefully in case the system server is
                        // crashed during the test case.
                        Map.Entry<TestIdentifier, TestStatus> lastTestCase =
                                mTestStatuses.entrySet().stream().findFirst().orElse(null);
                        if (lastTestCase != null
                                && !IInstrumentationResultParser.StatusCodes.isTerminalState(
                                        lastTestCase.getValue().getTestResultCode())) {
                            for (ITestRunListener listener : mListeners) {
                                listener.testFailed(lastTestCase.getKey(), /*trace=*/ "");
                            }

                            lastTestCase
                                    .getValue()
                                    .putTestMetrics(
                                            StatusKeys.DDMLIB_LOGCAT,
                                            lastTestCase.getValue().getLogcat());
                            for (ITestRunListener listener : mListeners) {
                                listener.testEnded(
                                        lastTestCase.getKey(),
                                        lastTestCase.getValue().getTestMetrics());
                            }
                        }

                        for (ITestRunListener listener : mListeners) {
                            listener.testRunFailed(errorMessage);
                        }
                    }
                    break;

                    // All tests run finishes.
                case SESSION_FINISHED:
                    if (session.getSessionStatus().getResultCode()
                            == IInstrumentationResultParser.SessionResultCodes.ERROR) {
                        String errorMessage =
                                session.getSessionStatus()
                                        .getResults()
                                        .getEntriesList()
                                        .stream()
                                        .filter(entry -> entry.getKey().equals(StatusKeys.SHORTMSG))
                                        .map(entry -> entry.getValueString())
                                        .findFirst()
                                        .orElse("");
                        for (ITestRunListener listener : mListeners) {
                            listener.testRunFailed(errorMessage);
                        }
                    }

                    for (InstrumentationData.ResultsBundleEntry entry :
                            session.getSessionStatus().getResults().getEntriesList()) {
                        if (!StatusKeys.KNOWN_KEYS.contains(entry.getKey())) {
                            testRunMetrics.put(
                                    entry.getKey(), getResultsEntryBundleValueInString(entry));
                        }
                    }
                    break;
                default:
                    /* Unknown status */
                    break;
            }

            long elapsedTime = findElapsedTime(session.getSessionStatus()).orElse(0L);
            ImmutableMap<String, String> immutableTestRunMetrics =
                    ImmutableMap.copyOf(testRunMetrics);
            for (ITestRunListener listener : mListeners) {
                listener.testRunEnded(elapsedTime, immutableTestRunMetrics);
            }
        }
    }

    private static String getResultsEntryBundleValueInString(
            InstrumentationData.ResultsBundleEntry entry) {
        if (entry.hasValueString()) return entry.getValueString();
        if (entry.hasValueInt()) return String.valueOf(entry.getValueInt());
        if (entry.hasValueLong()) return String.valueOf(entry.getValueLong());
        if (entry.hasValueFloat()) return String.valueOf(entry.getValueFloat());
        if (entry.hasValueDouble()) return String.valueOf(entry.getValueDouble());
        if (entry.hasValueBytes()) return entry.getValueBytes().toString();
        if (entry.hasValueBundle()) return entry.getValueBundle().toString();
        return "";
    }

    private void updateTestState(
            String testClassName,
            String testMethodName,
            int currentTestIndex,
            int testResultCode,
            String logcat,
            String stackTrace,
            LinkedHashMap<String, String> testMetrics) {
        TestIdentifier testId = new TestIdentifier(testClassName, testMethodName, currentTestIndex);
        TestStatus status =
                mTestStatuses.computeIfAbsent(
                        testId,
                        id -> {
                            // Notify test case started.
                            for (ITestRunListener listener : mListeners) {
                                listener.testStarted(testId);
                            }
                            return new TestStatus(IInstrumentationResultParser.StatusCodes.START);
                        });

        status.appendLogcat(logcat);
        status.putAllTestMetrics(testMetrics);

        if (status.getTestResultCode() == testResultCode) {
            return;
        }

        status.setTestResultCode(testResultCode);

        switch (testResultCode) {
            case IInstrumentationResultParser.StatusCodes.ASSUMPTION_FAILURE:
                for (ITestRunListener listener : mListeners) {
                    listener.testAssumptionFailure(testId, stackTrace);
                }
                break;

            case IInstrumentationResultParser.StatusCodes.FAILURE:
            case IInstrumentationResultParser.StatusCodes.ERROR:
                for (ITestRunListener listener : mListeners) {
                    listener.testFailed(testId, stackTrace);
                }
                break;

            case IInstrumentationResultParser.StatusCodes.IGNORED:
                for (ITestRunListener listener : mListeners) {
                    listener.testIgnored(testId);
                }
                // If the test status code is IGNORED, don't report logcat message given by
                // "am instrument" command because they are incorrect. (It seems it forgets
                // to clear out logcat message from the previous tests and passes the same
                // logcat message for the ignored tests).
                status.clearLogcat();
                break;
        }

        if (IInstrumentationResultParser.StatusCodes.isTerminalState(testResultCode)) {
            // Add extra test metrics by ddmlib.
            status.putTestMetrics(StatusKeys.DDMLIB_LOGCAT, status.getLogcat());

            for (ITestRunListener listener : mListeners) {
                listener.testEnded(testId, status.getTestMetrics());
            }
        }
    }

    /** Find an elapsed time string ("Time: 1,745.755") in a given session status. */
    private Optional<Long> findElapsedTime(InstrumentationData.SessionStatus sessionStatus) {
        String sessionOutput =
                sessionStatus
                        .getResults()
                        .getEntriesList()
                        .stream()
                        .filter(entry -> entry.getKey().equals(StatusKeys.STREAM))
                        .map(entry -> entry.getValueString())
                        .findFirst()
                        .orElse(null);

        if (isNullOrEmpty(sessionOutput)) {
            return Optional.empty();
        }

        Matcher timeMatcher = mTimePattern.matcher(sessionOutput);
        if (timeMatcher.find()) {
            String timeString = timeMatcher.group(1);
            try {
                float timeSeconds = NumberFormat.getInstance().parse(timeString).floatValue();
                return Optional.of((long) (timeSeconds * 1000));
            } catch (ParseException e) {
                Log.w(LOG_TAG, String.format("Unexpected time format %1$s", timeString));
            }
        }

        Log.w(LOG_TAG, String.format("Elapsed time is missing: %1$s", sessionOutput));
        return Optional.empty();
    }

    @Override
    public void handleTestRunFailed(String errorMsg) {
        if (mState.isTerminalState()) {
            return;
        }
        mState = InstrumentationProtoResultParserState.FINISHED;

        for (ITestRunListener listener : mListeners) {
            listener.testRunFailed(errorMsg);
        }
        for (ITestRunListener listener : mListeners) {
            listener.testRunEnded(0, ImmutableMap.of());
        }
    }

    @Override
    public void flush() {
        /** Noop. We process data immediately. Nothing to flush. */
    }

    @Override
    public void cancel() {
        mState = InstrumentationProtoResultParserState.CANCELLED;
    }

    @Override
    public boolean isCancelled() {
        return mState == InstrumentationProtoResultParserState.CANCELLED;
    }
}
