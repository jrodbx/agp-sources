/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.instrumentation.manageddevice

import com.android.build.api.variant.AndroidVersion
import com.android.builder.testing.api.DeviceConfigProvider
import java.io.File
import org.gradle.api.Incubating

/**
 * Interface for retrieving all test data for a given test invocation for use by the
 * [DeviceTestRunTaskAction].
 *
 * This is supplied to the [DeviceTestRunTaskAction] as
 * a part of the [DeviceTestRunParameters.testRunData]
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface StaticTestData {

    /**
     * The application ID of the test APK.
     */
    @get: Incubating
    val applicationId: String

    /**
     * The application ID of the APK under test.
     *
     * This can be null if there is no such APK. For example, android-test in library
     * module does not have a tested-application.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testedApplicationId: String?

    /**
     * The instrumentation target application id.
     *
     * If this is a self instrumenting test, then this value will be the same a [applicationId].
     * Otherwise, the instrumentation target will be the same as [testedApplicationId].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val instrumentationTargetPackageId: String

    /**
     * The APK containing the tests.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testApk: File

    /**
     * The class name for the Test Instrumentation Runner.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val instrumentationRunner: String

    /**
     * The arguments that should be passed to the [InstrumentationRunner].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val instrumentationRunnerArguments: Map<String, String>

    /**
     * The minimum SDK version required to test the given [testApk].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val minSdkVersion: AndroidVersion

    /**
     * The description of the APK under test.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testedDescription: String

    /**
     * List of the directories of all test sources in the [testApk].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testDirectories: List<File?>

    /**
     * Returns the APK files to install based on given Device Configuration (such as abi and
     * screen density).
     *
     * If no such APK can be found, an empty list is returned.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testedApkFinder: (DeviceConfigProvider) -> List<File>
}
