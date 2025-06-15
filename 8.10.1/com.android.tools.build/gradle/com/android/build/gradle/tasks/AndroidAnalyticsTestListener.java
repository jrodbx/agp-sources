/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.test.TestsAnalytics.recordCrashedUnitTestRun;
import static com.android.build.gradle.internal.test.TestsAnalytics.recordOkUnitTestRun;

import com.android.build.gradle.internal.profile.AnalyticsService;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

public class AndroidAnalyticsTestListener implements TestListener {

    private final ArtifactCollection dependencies;
    private final boolean coverageEnabled;
    private final AnalyticsService analyticsService;
    private TestFilter testFilter;
    private long totalTests;
    private boolean isIdeInvocation;

    public AndroidAnalyticsTestListener(
            ArtifactCollection dependencies,
            boolean coverageEnabled,
            AnalyticsService analyticsService,
            TestFilter testFilter,
            boolean isIdeInvocation) {
        this.dependencies = dependencies;
        this.coverageEnabled = coverageEnabled;
        this.analyticsService = analyticsService;
        this.testFilter = testFilter;
        this.isIdeInvocation = isIdeInvocation;
    }

    @Override
    public void beforeSuite(TestDescriptor testDescriptor) {}

    @Override
    public void afterSuite(TestDescriptor testDescriptor, TestResult testResult) {
        if (testDescriptor.getParent() == null) {
            // If the test threw an exception that is due to a failure, do not record it
            // as a crashed test.
            // Otherwise, if no test was executed while it should have,
            // then we record as a crashed test.
            if (this.totalTests == 0L
                    && testResult.getResultType() != TestResult.ResultType.FAILURE
                    && (!testFilter.getIncludePatterns().isEmpty()
                            || !testFilter.getExcludePatterns().isEmpty())) {
                recordCrashedUnitTestRun(
                        dependencies,
                        coverageEnabled,
                        analyticsService,
                        isIdeInvocation
                );
            }

            // Only log the test run if there was any test that was run. otherwise, it means that no
            // test was found for the run Configuration.
            if (totalTests != 0L) {
                recordOkUnitTestRun(
                        dependencies,
                        coverageEnabled,
                        (int) totalTests,
                        analyticsService,
                        isIdeInvocation
                );
            }
        }
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {}

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult testResult) {
        this.totalTests += testResult.getTestCount();
    }
}
