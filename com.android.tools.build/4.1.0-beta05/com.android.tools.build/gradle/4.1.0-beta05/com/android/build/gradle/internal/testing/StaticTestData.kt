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

import com.android.builder.testing.api.DeviceConfigProvider
import com.android.sdklib.AndroidVersion
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import java.io.File

data class StaticTestData (
    val applicationId: String,

    val testedApplicationId: String?,

    val instrumentationRunner: String,

    val instrumentationRunnerArguments: Map<String, String>,

    val animationsDisabled: Boolean,

    val isTestCoverageEnabled: Boolean,

    val minSdkVersion: AndroidVersion,

    val isLibrary: Boolean,

    val flavorName: String,

    val testApk: File,

    val testDirectories: List<File?>,

    val testedApks: (deviceConfigProvider: DeviceConfigProvider, logger: ILogger) -> List<File>
)