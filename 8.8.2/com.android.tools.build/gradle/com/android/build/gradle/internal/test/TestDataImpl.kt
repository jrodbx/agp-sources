/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.test

import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input

/**
 * Implementation of [TestData] on top of a [DeviceTestCreationConfig]
 */
class TestDataImpl(
    namespace: Provider<String>,
    testConfig: DeviceTestCreationConfig,
    testApkDir: Provider<Directory>,
    testedApksDir: Provider<Directory>?,
    privacySandboxSdkApks: FileCollection?,
    privacySandboxCompatSdkApksDir: Provider<Directory>?,
    additionalSdkSupportedApkSplits: Provider<Directory>?,
    extraInstrumentationTestRunnerArgs: Map<String, String>
) : AbstractTestDataImpl(
    namespace,
    testConfig,
    testApkDir,
    testedApksDir,
    privacySandboxSdkApks,
    privacySandboxCompatSdkApksDir,
    additionalSdkSupportedApkSplits,
    extraInstrumentationTestRunnerArgs
) {
    @get: Input
    override val supportedAbis: Set<String> =
        testConfig.nativeBuildCreationConfig?.supportedAbis ?: emptySet()


    override val libraryType =
        testConfig.services.provider { testConfig.mainVariant.componentType.isAar }
}
