/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.testing;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.impl.VariantApiExtensionsKt;
import com.android.builder.internal.InstallUtils;
import com.android.builder.testing.api.DeviceConfigProvider;
import com.android.builder.testing.api.DeviceConfigProviderImpl;
import com.android.builder.testing.api.DeviceConnector;
import com.android.builder.testing.api.DeviceException;
import com.android.builder.testing.api.TestException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.ide.common.workers.WorkerExecutorException;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Common code for {@link TestRunner} implementations. */
public abstract class BaseTestRunner implements TestRunner {

    @Nullable protected final File splitSelectExec;
    @NonNull protected final ProcessExecutor processExecutor;
    @NonNull protected final ExecutorServiceAdapter executor;

    public BaseTestRunner(
            @Nullable File splitSelectExec,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ExecutorServiceAdapter executor) {
        this.splitSelectExec = splitSelectExec;
        this.processExecutor = checkNotNull(processExecutor);
        this.executor = executor;
    }

    private static void generateXmlOutputForNoDevices(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull File resultsDir,
            @NonNull ILogger logger,
            int totalDevices,
            Map<DeviceConnector, ImmutableList<File>> availableDevices) {
        CustomTestRunListener fakeRunListener =
                new CustomTestRunListener("TestRunner", projectName, variantName, logger);
        fakeRunListener.setReportDir(resultsDir);

        // create a fake test output
        Map<String, String> emptyMetrics = Collections.emptyMap();
        TestIdentifier fakeTest =
                new TestIdentifier(
                        variantName,
                        totalDevices == 0
                                ? ": No devices connected."
                                : ": No compatible devices connected.");
        fakeRunListener.testStarted(fakeTest);
        fakeRunListener.testFailed(
                fakeTest,
                String.format(
                        Locale.US,
                        "Found %d connected device(s), %d of which were compatible.",
                        totalDevices,
                        availableDevices.size()));
        fakeRunListener.testEnded(fakeTest, emptyMetrics);

        // end the run to generate the XML file.
        fakeRunListener.testRunEnded(0, emptyMetrics);
    }

    private static void generateXmlOutputForUnauthorizedDevices(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull File resultsDir,
            @NonNull ILogger logger,
            int unauthorizedDevices) {
        CustomTestRunListener fakeRunListener =
                new CustomTestRunListener("TestRunner", projectName, variantName, logger);
        fakeRunListener.setReportDir(resultsDir);

        // create a fake test output
        Map<String, String> emptyMetrics = Collections.emptyMap();
        TestIdentifier fakeTest = new TestIdentifier(variantName, ": found unauthorized devices.");
        fakeRunListener.testStarted(fakeTest);
        fakeRunListener.testFailed(
                fakeTest,
                String.format(Locale.US, "Found %d unauthorized device(s).", unauthorizedDevices));
        fakeRunListener.testEnded(fakeTest, emptyMetrics);

        // end the run to generate the XML file.
        fakeRunListener.testRunEnded(0, emptyMetrics);
    }

    @Override
    public boolean runTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull StaticTestData testData,
            @NonNull Set<File> helperApks,
            @NonNull List<? extends DeviceConnector> deviceList,
            int timeoutInMs,
            @NonNull Collection<String> installOptions,
            @NonNull File resultsDir,
            boolean additionalTestOutputEnabled,
            @Nullable File additionalTestOutputDir,
            @NonNull File coverageDir,
            @NonNull ILogger logger)
            throws TestException {

        int totalDevices = deviceList.size();
        int unauthorizedDevices = 0;
        Map<DeviceConnector, ImmutableList<File>> apksForDevice = new HashMap<>();
        for (DeviceConnector device : deviceList) {
            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                if (InstallUtils.checkDeviceApiLevel(
                        device,
                        VariantApiExtensionsKt.toSharedAndroidVersion(testData.getMinSdkVersion()),
                        logger,
                        projectName,
                        variantName)) {

                    DeviceConfigProvider deviceConfigProvider;
                    try {
                        deviceConfigProvider = new DeviceConfigProviderImpl(device);
                    } catch (DeviceException e) {
                        throw new TestException(e);
                    }

                    // now look for a matching output file
                    List<File> testedApks = ImmutableList.of();
                    if (!testData.isLibrary()) {
                        testedApks =
                                testData.getTestedApkFinder().invoke(deviceConfigProvider, logger);

                        if (testedApks.isEmpty()) {
                            logger.info(
                                    "Skipping device '%1$s' for '%2$s:%3$s': No matching output file",
                                    device.getName(), projectName, variantName);
                            continue;
                        }
                    }
                    apksForDevice.put(device, ImmutableList.copyOf(testedApks));
                }
            } else {
                unauthorizedDevices++;
            }
        }

        if (totalDevices == 0 || apksForDevice.isEmpty()) {
            generateXmlOutputForNoDevices(
                    projectName, variantName, resultsDir, logger, totalDevices, apksForDevice);
            return false;
        } else {
            if (unauthorizedDevices > 0) {
                generateXmlOutputForUnauthorizedDevices(
                        projectName, variantName, resultsDir, logger, unauthorizedDevices);
            }

            List<TestResult> results =
                    scheduleTests(
                            projectName,
                            variantName,
                            testData,
                            apksForDevice,
                            helperApks,
                            timeoutInMs,
                            installOptions,
                            resultsDir,
                            additionalTestOutputEnabled,
                            additionalTestOutputDir,
                            coverageDir,
                            logger);

            try {
                executor.await();
            } catch (WorkerExecutorException e) {
                e.getCauses().forEach(cause -> logger.error(cause, null));
                return false;
            }

            boolean success = unauthorizedDevices == 0;

            // check if one test failed.
            for (TestResult result : results) {
                if (result.getTestResult() == TestResult.Result.FAILED) {
                    success = false;
                }
            }
            return success;
        }
    }

    @NonNull
    protected abstract List<TestResult> scheduleTests(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull StaticTestData testData,
            @NonNull Map<DeviceConnector, ImmutableList<File>> apksForDevice,
            @NonNull Set<File> helperApks,
            int timeoutInMs,
            @NonNull Collection<String> installOptions,
            @NonNull File resultsDir,
            boolean additionalTestOutputEnabled,
            @Nullable File additionalTestOutputDir,
            @NonNull File coverageDir,
            @NonNull ILogger logger);

    public static class TestResult {
        public enum Result {
            SUCCEEDED,
            FAILED
        }

        Result testResult;

        public void setTestResult(Result testResult) {
            this.testResult = testResult;
        }

        public Result getTestResult() {
            return testResult;
        }
    }
}
