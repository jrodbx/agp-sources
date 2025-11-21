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

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellEnabledDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Runs a Android test command remotely and reports results.
 */
public class RemoteAndroidTestRunner implements IRemoteAndroidTestRunner  {

    /** Represents a status reporter mode in am instrument command options. */
    public enum StatusReporterMode {
        /**
         * Use raw text message to receive status from am instrument command.
         *
         * @deprecated Use {@link #PROTO_STD} for API level 26 and above.
         */
        @Deprecated
        RAW_TEXT("-r", 0, InstrumentationResultParser::new),

        /**
         * Use instrumentationData protobuf status reporter to receive status from am instrument
         * command.
         */
        PROTO_STD("-m", 26, InstrumentationProtoResultParser::new);

        StatusReporterMode(
                String amInstrumentCommandArg,
                int minApiLevel,
                BiFunction<String, Collection<ITestRunListener>, IInstrumentationResultParser>
                        parserFactory) {
            this.amInstrumentCommandArg = amInstrumentCommandArg;
            this.minApiLevel = minApiLevel;
            this.parserFactory = parserFactory;
        }

        private final String amInstrumentCommandArg;
        private final int minApiLevel;
        private final BiFunction<String, Collection<ITestRunListener>, IInstrumentationResultParser>
                parserFactory;

        /**
         * Returns a command line arg for am instrument command to specify this status reporter
         * mode.
         */
        public String getAmInstrumentCommandArg() {
            return amInstrumentCommandArg;
        }

        /**
         * Returns the minimum Android API level which supports this instrumentation status report
         * type.
         */
        public int getMinimumApiLevel() {
            return minApiLevel;
        }

        /**
         * Create the {@link InstrumentationResultParser} that can be used to parse the
         * instrumentation output.
         *
         * @param runName The name of the run to use.
         * @param listeners The listeners where to report the results.
         * @return An instance of {@link InstrumentationResultParser}.
         */
        public IInstrumentationResultParser createInstrumentationResultParser(
                @NonNull String runName, @NonNull Collection<ITestRunListener> listeners) {
            return parserFactory.apply(runName, listeners);
        }
    }

    private final StatusReporterMode mStatusReporterMode;

    private final String mPackageName;
    private final String mRunnerName;
    private IShellEnabledDevice mRemoteDevice;
    // default to no timeout
    private long mMaxTimeoutMs = 0L;
    private long mMaxTimeToOutputResponseMs = 0L;

    private String mRunName = null;

    /** map of name-value instrumentation argument pairs */
    private Map<String, String> mArgMap;
    private IInstrumentationResultParser mParser;

    private static final String LOG_TAG = "RemoteAndroidTest";
    private static final String DEFAULT_RUNNER_NAME = "android.test.InstrumentationTestRunner";

    private static final char CLASS_SEPARATOR = ',';
    private static final char METHOD_SEPARATOR = '#';
    private static final char RUNNER_SEPARATOR = '/';

    // defined instrumentation argument names
    private static final String CLASS_ARG_NAME = "class";
    private static final String LOG_ARG_NAME = "log";
    private static final String DEBUG_ARG_NAME = "debug";
    private static final String COVERAGE_ARG_NAME = "coverage";
    private static final String PACKAGE_ARG_NAME = "package";
    private static final String SIZE_ARG_NAME = "size";
    private static final String DELAY_MSEC_ARG_NAME = "delay_msec";
    private String mRunOptions = "";

    private static final int TEST_COLLECTION_TIMEOUT = 2 * 60 * 1000; //2 min

    /**
     * Creates a remote Android test runner.
     *
     * @param packageName the Android application package that contains the tests to run
     * @param runnerName the instrumentation test runner to execute. If null, will use default
     *     runner
     * @param remoteDevice the Android device to execute tests on
     * @param statusReporterMode the status reporter mode to be used for am instrument command
     */
    public RemoteAndroidTestRunner(
            String packageName,
            String runnerName,
            IShellEnabledDevice remoteDevice,
            StatusReporterMode statusReporterMode) {

        mPackageName = packageName;
        mRunnerName = runnerName;
        mRemoteDevice = remoteDevice;
        mStatusReporterMode = statusReporterMode;
        mArgMap = new Hashtable<String, String>();
    }

    /**
     * Alternate constructor. Uses default {@code statusReporterMode}.
     *
     * @param packageName the Android application package that contains the tests to run
     * @param runnerName the instrumentation test runner to execute. If null, will use default
     *     runner
     * @param remoteDevice the Android device to execute tests on
     */
    public RemoteAndroidTestRunner(
            String packageName, String runnerName, IShellEnabledDevice remoteDevice) {
        this(packageName, runnerName, remoteDevice, StatusReporterMode.RAW_TEXT);
    }

    /**
     * Alternate constructor. Uses default instrumentation runner.
     *
     * @param packageName the Android application package that contains the tests to run
     * @param remoteDevice the Android device to execute tests on
     */
    public RemoteAndroidTestRunner(String packageName,
                                   IShellEnabledDevice remoteDevice) {
        this(packageName, null, remoteDevice);
    }

    @Override
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public String getRunnerName() {
        if (mRunnerName == null) {
            return DEFAULT_RUNNER_NAME;
        }
        return mRunnerName;
    }

    /** Returns the complete instrumentation component path. */
    protected String getRunnerPath() {
        return getPackageName() + RUNNER_SEPARATOR + getRunnerName();
    }

    @Override
    public void setClassName(String className) {
        // The class name may contain the dollar sign, so needs to be quoted.
        addInstrumentationArg(CLASS_ARG_NAME, "'" + className + "'");
    }

    @Override
    public void setClassNames(String[] classNames) {
        StringBuilder classArgBuilder = new StringBuilder();

        for (int i = 0; i < classNames.length; i++) {
            if (i != 0) {
                classArgBuilder.append(CLASS_SEPARATOR);
            }
            classArgBuilder.append(classNames[i]);
        }
        setClassName(classArgBuilder.toString());
    }

    @Override
    public void setMethodName(String className, String testName) {
        setClassName(className + METHOD_SEPARATOR + testName);
    }

    @Override
    public void setTestPackageName(String packageName) {
        addInstrumentationArg(PACKAGE_ARG_NAME, packageName);
    }

    @Override
    public void addInstrumentationArg(String name, String value) {
        if (name == null || value == null) {
            throw new IllegalArgumentException("name or value arguments cannot be null");
        }
        mArgMap.put(name, value);
    }

    @Override
    public void removeInstrumentationArg(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name argument cannot be null");
        }
        mArgMap.remove(name);
    }

    @Override
    public void addBooleanArg(String name, boolean value) {
        addInstrumentationArg(name, Boolean.toString(value));
    }

    @Override
    public void setLogOnly(boolean logOnly) {
        addBooleanArg(LOG_ARG_NAME, logOnly);
    }

    @Override
    public void setDebug(boolean debug) {
        addBooleanArg(DEBUG_ARG_NAME, debug);
    }

    @Override
    public void setAdditionalTestOutputLocation(String additionalTestDataPath) {
        addInstrumentationArg("additionalTestOutputDir", additionalTestDataPath);
    }

    @Override
    public void setCoverage(boolean coverage) {
        addBooleanArg(COVERAGE_ARG_NAME, coverage);
    }

    @Override
    public void setCoverageReportLocation(String reportPath) {
        addInstrumentationArg("coverageFile", reportPath);
    }

    @Override
    public CoverageOutput getCoverageOutputType() {
        return CoverageOutput.FILE;
    }

    @Override
    public void setTestSize(TestSize size) {
        addInstrumentationArg(SIZE_ARG_NAME, size.getRunnerValue());
    }

    @Override
    public void setTestCollection(boolean collect) {
        if (collect) {
            // skip test execution
            setLogOnly(true);
            // force a timeout for test collection
            setMaxTimeToOutputResponse(TEST_COLLECTION_TIMEOUT, TimeUnit.MILLISECONDS);
            if (getApiLevel() < 16 ) {
                // On older platforms, collecting tests can fail for large volume of tests.
                // Insert a small delay between each test to prevent this
                addInstrumentationArg(DELAY_MSEC_ARG_NAME, "15" /* ms */);
            }
        } else {
            setLogOnly(false);
            // restore timeout to its original set value
            setMaxTimeToOutputResponse(mMaxTimeToOutputResponseMs, TimeUnit.MILLISECONDS);
            if (getApiLevel() < 16 ) {
                // remove delay
                removeInstrumentationArg(DELAY_MSEC_ARG_NAME);
            }
        }
    }

    /**
     * Attempts to retrieve the Api level of the Android device
     * @return the api level or -1 if the communication with the device wasn't successful
     */
    private int getApiLevel() {
        try {
            return Integer.parseInt(mRemoteDevice.getSystemProperty(
                    IDevice.PROP_BUILD_API_LEVEL).get());
        } catch (Exception e) {
            return -1;
        }
    }

    @Deprecated
    @Override
    public void setMaxtimeToOutputResponse(int maxTimeToOutputResponse) {
        setMaxTimeToOutputResponse(maxTimeToOutputResponse, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setMaxTimeToOutputResponse(long maxTimeToOutputResponse, TimeUnit maxTimeUnits) {
        mMaxTimeToOutputResponseMs = maxTimeUnits.toMillis(maxTimeToOutputResponse);
    }

    @Override
    public void setMaxTimeout(long maxTimeout, TimeUnit maxTimeUnits) {
        mMaxTimeoutMs = maxTimeUnits.toMillis(maxTimeout);
    }

    @Override
    public void setRunName(String runName) {
        mRunName = runName;
    }

    @Override
    public void run(ITestRunListener... listeners)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        run(Arrays.asList(listeners));
    }

    @Override
    public void run(Collection<ITestRunListener> listeners)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        final String runCaseCommandStr = getAmInstrumentCommand();
        Log.i(LOG_TAG, String.format("Running %1$s on %2$s", runCaseCommandStr,
                mRemoteDevice.getName()));
        String runName = mRunName == null ? mPackageName : mRunName;
        mParser = createParser(runName, listeners);

        try {
            mRemoteDevice.executeShellCommand(
                    runCaseCommandStr,
                    mParser,
                    mMaxTimeoutMs,
                    mMaxTimeToOutputResponseMs,
                    TimeUnit.MILLISECONDS);
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("IOException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getName()));
            // rely on parser to communicate results to listeners
            mParser.handleTestRunFailed(e.toString());
            throw e;
        } catch (ShellCommandUnresponsiveException e) {
            Log.w(LOG_TAG, String.format(
                    "ShellCommandUnresponsiveException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getName()));
            mParser.handleTestRunFailed(
                    String.format(
                            "Failed to receive adb shell test output within %1$d ms. Test may have "
                                    + "timed out, or adb connection to device became unresponsive",
                            mMaxTimeToOutputResponseMs));
            throw e;
        } catch (TimeoutException e) {
            Log.w(LOG_TAG, String.format(
                    "TimeoutException when running tests %1$s on %2$s", getPackageName(),
                    mRemoteDevice.getName()));
            mParser.handleTestRunFailed(e.toString());
            throw e;
        } catch (AdbCommandRejectedException e) {
            Log.w(LOG_TAG, String.format(
                    "AdbCommandRejectedException %1$s when running tests %2$s on %3$s",
                    e.toString(), getPackageName(), mRemoteDevice.getName()));
            mParser.handleTestRunFailed(e.toString());
            throw e;
        }
    }

    /**
     * Create the {@link InstrumentationResultParser} that will be used to parse the instrumentation
     * output.
     *
     * @param runName The name of the run to use.
     * @param listeners The listeners where to report the results.
     * @return An instance of {@link InstrumentationResultParser}.
     */
    @NonNull
    public IInstrumentationResultParser createParser(
            @NonNull String runName, @NonNull Collection<ITestRunListener> listeners) {
        return mStatusReporterMode.createInstrumentationResultParser(runName, listeners);
    }

    @NonNull
    public String getAmInstrumentCommand() {
        return String.format(
                "am instrument -w %1$s %2$s %3$s %4$s",
                mStatusReporterMode.getAmInstrumentCommandArg(),
                getRunOptions(),
                getArgsCommand(),
                getRunnerPath());
    }

    /**
     * Returns options for the am instrument command.
     */
    @NonNull public String getRunOptions() {
        return mRunOptions;
    }

    /**
     * Sets options for the am instrument command.
     * See com/android/commands/am/Am.java for full list of options.
     */
    public void setRunOptions(@NonNull String options) {
        mRunOptions = options;
    }

    @Override
    public void cancel() {
        if (mParser != null) {
            mParser.cancel();
        }
    }

    /**
     * Returns the full instrumentation command line syntax for the provided instrumentation
     * arguments. Returns an empty string if no arguments were specified.
     */
    protected String getArgsCommand() {
        StringBuilder commandBuilder = new StringBuilder();
        for (Entry<String, String> argPair : mArgMap.entrySet()) {
            final String argCmd = String.format(" -e %1$s %2$s", argPair.getKey(),
                    argPair.getValue());
            commandBuilder.append(argCmd);
        }
        return commandBuilder.toString();
    }
}
