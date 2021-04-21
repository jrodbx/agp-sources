/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("InstrumentationTestAnalytics")

package com.android.build.gradle.internal.test

import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.builder.model.TestOptions
import com.android.Version
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.recordTestLibrary
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestLibraries
import com.google.wireless.android.sdk.stats.TestRun
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

fun recordOkTestRun(
    dependencies: ArtifactCollection,
    execution: TestOptions.Execution,
    coverageEnabled: Boolean,
    testCount: Int,
    analyticsService: AnalyticsService
) {
    recordTestRun(
        dependencies = dependencies,
        execution = execution,
        coverageEnabled = coverageEnabled,
        testCount = testCount,
        infrastructureCrashed = false,
        analyticsService = analyticsService
    )
}

fun recordCrashedTestRun(
    dependencies: ArtifactCollection,
    execution: TestOptions.Execution,
    coverageEnabled: Boolean,
    analyticsService: AnalyticsService
) {
    recordTestRun(
        dependencies = dependencies,
        execution = execution,
        coverageEnabled = coverageEnabled,
        testCount = 0,
        infrastructureCrashed = true,
        analyticsService = analyticsService
    )
}

private fun recordTestRun(
    dependencies: ArtifactCollection,
    execution: TestOptions.Execution,
    coverageEnabled: Boolean,
    testCount: Int,
    infrastructureCrashed: Boolean,
    analyticsService: AnalyticsService
) {
    val run = TestRun.newBuilder().apply {
        testInvocationType = TestRun.TestInvocationType.GRADLE_TEST
        numberOfTestsExecuted = testCount
        testKind = TestRun.TestKind.INSTRUMENTATION_TEST
        crashed = infrastructureCrashed
        gradleVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION
        codeCoverageEnabled = coverageEnabled
        testLibraries = gatherTestLibraries(dependencies)
        testExecution = AnalyticsUtil.toProto(execution)
    }.build()

    analyticsService.recordEvent(
        AndroidStudioEvent.newBuilder().apply {
            category = AndroidStudioEvent.EventCategory.TESTS
            kind = AndroidStudioEvent.EventKind.TEST_RUN
            testRun = run
            javaProcessStats = CommonMetricsData.javaProcessStats
            jvmDetails = CommonMetricsData.jvmDetails
            productDetails = AnalyticsUtil.getProductDetails()
        }
    )
}

private fun gatherTestLibraries(dependencies: ArtifactCollection): TestLibraries {
    return TestLibraries.newBuilder().also { testLibraries ->
        dependencies.artifacts.forEach { resolvedArtifact ->
            val id = resolvedArtifact.id.componentIdentifier
            if (id is ModuleComponentIdentifier) {
                testLibraries.recordTestLibrary(id.group, id.module, id.version)
            }
        }
    }.build()
}