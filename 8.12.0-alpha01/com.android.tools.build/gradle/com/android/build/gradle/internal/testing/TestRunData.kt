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

package com.android.build.gradle.internal.testing

import java.io.File
import org.gradle.api.file.Directory
import java.io.Serializable

/**
 * Implementation of the TestRunData for use by
 * Custom Managed Device Types
 */
data class TestRunData(

    override val variantName: String,

    override val testRunId: String,

    override val deviceName: String,

    override val outputDirectory: Directory,

    override val coverageOutputDirectory: Directory,

    override val additionalTestOutputDir: Directory?,

    override val additionalInstallOptions: List<String>,

    override val helperApks: Set<File>,

    override val projectPath: String,

    override val testData: StaticTestData
): com.android.build.api.instrumentation.manageddevice.TestRunData, Serializable
