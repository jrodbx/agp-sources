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

package com.android.build.gradle.internal.testing

import com.android.build.api.variant.AndroidVersion
import com.android.build.gradle.internal.test.ApkBundlesFinder
import com.android.build.gradle.internal.test.ApksFinder
import com.android.builder.testing.api.DeviceConfigProvider
import java.io.File
import java.io.Serializable
import java.nio.file.Path

/**
 * Final values for the [TestData] that can be passed to test runners. This should not be used as
 * task input.
 *
 * @property applicationId the application ID of the test APK.
 * @property testedApplicationId the application ID of the APK under testing. This can be null if
 *     there is no such an APK. For example, android-test in library module does not have tested-
 *     application.
 * @property instrumentationTargetPackageId the instrumentation target application id. This is
 *     same value to [applicationId] if it is a self-instrumeting test, otherwise
 *     [testedApplicationId].
 */
data class StaticTestData(
    override val applicationId: String,

    override val testedApplicationId: String?,

    override val instrumentationTargetPackageId: String,

    override val instrumentationRunner: String,

    override val instrumentationRunnerArguments: Map<String, String>,

    override val animationsDisabled: Boolean,

    override val isTestCoverageEnabled: Boolean,

    override val minSdkVersion: AndroidVersion,

    override val isLibrary: Boolean,

    override val flavorName: String,

    override val testApk: File,

    override val testDirectories: List<File?>,

    val testedApks: ApksFinder,

    val privacySandboxApks: ApkBundlesFinder
) : com.android.build.api.instrumentation.StaticTestData, Serializable {

    /**
     *
     * Returns APK files to install based on given density and abis. If none match,
     * empty list is returned.
     */
    override val testedApkFinder: (DeviceConfigProvider) -> List<File>
        get() = testedApks::findApks

    /**
     * TODO: Pending migration from extractApkFilesBypassingBundleTool to getApkFiles (Currently unused)
     *
     * Returns extracted dependency APK files to install for privacy sandbox apps.
     */
    override val privacySandboxInstallBundlesFinder: (DeviceConfigProvider) -> List<List<Path>>
        get() = privacySandboxApks::findBundles

}
