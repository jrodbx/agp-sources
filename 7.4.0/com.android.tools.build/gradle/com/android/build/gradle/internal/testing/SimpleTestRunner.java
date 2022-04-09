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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Basic {@link TestRunner} running tests on all devices. */
public class SimpleTestRunner extends BaseTestRunner {

    public SimpleTestRunner(
            @Nullable File splitSelectExec,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ExecutorServiceAdapter executor) {
        super(splitSelectExec, processExecutor, executor);
    }

    @Override
    @NonNull
    protected List<TestResult> scheduleTests(
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
            @NonNull ILogger logger) {
        List<TestResult> results = new ArrayList<>();
        for (Map.Entry<DeviceConnector, ImmutableList<File>> apks : apksForDevice.entrySet()) {
            DeviceConnector device = apks.getKey();
            TestResult result = new TestResult();
            results.add(result);
            SimpleTestRunnable.SimpleTestParams simpleTestParams =
                    new SimpleTestRunnable.SimpleTestParams(
                            device,
                            projectName,
                            createRemoteAndroidTestRunner(testData, device),
                            variantName,
                            apks.getValue(),
                            testData,
                            helperApks,
                            resultsDir,
                            additionalTestOutputEnabled,
                            additionalTestOutputDir,
                            coverageDir,
                            timeoutInMs,
                            installOptions,
                            logger,
                            result);
            executor.submit(new SimpleTestRunnable(simpleTestParams));
        }
        return results;
    }

    @NonNull
    protected RemoteAndroidTestRunner createRemoteAndroidTestRunner(
            @NonNull StaticTestData testData, DeviceConnector device) {
        return new RemoteAndroidTestRunner(
                testData.getApplicationId(), testData.getInstrumentationRunner(), device);
    }
}
