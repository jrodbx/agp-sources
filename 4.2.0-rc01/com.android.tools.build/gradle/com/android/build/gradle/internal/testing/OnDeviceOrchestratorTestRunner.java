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
import com.android.builder.model.TestOptions;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.workers.ExecutorServiceAdapter;
import com.google.common.base.Preconditions;
import java.io.File;

/** Implementation of {@link TestRunner} that invokes tests through Odo. */
public class OnDeviceOrchestratorTestRunner extends SimpleTestRunner {

    @NonNull private final TestOptions.Execution execution;

    public OnDeviceOrchestratorTestRunner(
            @Nullable File splitSelectExec,
            @NonNull ProcessExecutor processExecutor,
            @NonNull TestOptions.Execution execution,
            @NonNull ExecutorServiceAdapter executor) {
        super(splitSelectExec, processExecutor, executor);
        Preconditions.checkArgument(
                execution == TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR
                        || execution == TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR);
        this.execution = execution;
    }

    @NonNull
    @Override
    protected RemoteAndroidTestRunner createRemoteAndroidTestRunner(
            @NonNull StaticTestData testData, @NonNull DeviceConnector device) {
        return new AndroidTestOrchestratorRemoteAndroidTestRunner(
                testData.getApplicationId(),
                testData.getInstrumentationRunner(),
                device,
                execution == TestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR);
    }
}
