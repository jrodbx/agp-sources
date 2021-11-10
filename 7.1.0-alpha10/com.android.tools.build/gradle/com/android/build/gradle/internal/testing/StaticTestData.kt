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
import com.android.builder.testing.api.DeviceConfigProvider
import com.android.utils.ILogger
import java.io.File

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
    val applicationId: String,

    val testedApplicationId: String?,

    val instrumentationTargetPackageId: String,

    val instrumentationRunner: String,

    val instrumentationRunnerArguments: Map<String, String>,

    val animationsDisabled: Boolean,

    val isTestCoverageEnabled: Boolean,

    val minSdkVersion: AndroidVersion,

    val isLibrary: Boolean,

    val flavorName: String,

    val testApk: File,

    val testDirectories: List<File?>,

    /**
     * Returns APK files to install based on given density and abis. If none match,
     * empty list is returned.
     */
    val testedApkFinder: (DeviceConfigProvider, ILogger) -> List<File>
)
