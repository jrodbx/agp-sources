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

package com.android.build.api.instrumentation

import com.android.build.api.variant.AndroidVersion
import com.android.builder.testing.api.DeviceConfigProvider
import org.gradle.api.Incubating
import java.io.File
import java.nio.file.Path

/**
 * Final values for the Android Test data that can be passed to test runners. This should not be
 * used as task input.
 *
 * @property applicationId the application ID of the test APK.
 * @property testedApplicationId the application ID of the APK under testing. This can be null if
 *     there is no such an APK. For example, android-test in library module does not have tested-
 *     application.
 * @property instrumentationTargetPackageId the instrumentation target application id. This is
 *     same value to [applicationId] if it is a self-instrumeting test, otherwise
 *     [testedApplicationId].
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface StaticTestData {

    /**
     * The application ID of the test APK.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val applicationId: String

    /**
     * The application ID of the APK under test.
     *
     * This can be null if there is no such APK. For example, android-test in library
     * module does not have a tested-application.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val testedApplicationId: String?

    /**
     * The instrumentation target application id.
     *
     * If this is a self instrumenting test, then this value will be the same a [applicationId].
     * Otherwise, the instrumentation target will be the same as [testedApplicationId].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val instrumentationTargetPackageId: String

    /**
     * The class name for the Test Instrumentation Runner.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val instrumentationRunner: String

    /**
     * The arguments that should be passed to the [instrumentationRunner].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val instrumentationRunnerArguments: Map<String, String>

    /**
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val animationsDisabled: Boolean

    /**
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val isTestCoverageEnabled: Boolean

    /**
     * The minimum SDK version required to test the given [testApk].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val minSdkVersion: AndroidVersion

    /**
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val isLibrary: Boolean

    /**
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val flavorName: String

    /**
     * The APK containing the tests.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val testApk: File

    /**
     * List of the directories of all test sources in the [testApk].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val testDirectories: List<File?>

    /**
     * Returns APK files to install based on given density and abis. If none match,
     * empty list is returned.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get:Incubating
    val testedApkFinder: (DeviceConfigProvider) -> List<File>

    /**
     * TODO: Pending migration from extractApkFilesBypassingBundleTool to getApkFiles
     *
     * Returns extracted dependency APK files to install for privacy sandbox apps.
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    val privacySandboxInstallBundlesFinder: (DeviceConfigProvider) -> List<List<Path>>
}
