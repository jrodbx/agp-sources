/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.utils

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApkInstallGroup
import com.android.build.api.variant.ApkOutput
import com.android.build.api.variant.DeviceSpec
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.testing.TestData
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskInputs

class DeviceTestApkOutput(targetVariant: VariantCreationConfig, testVariant: DeviceTestCreationConfig, deviceSpec: DeviceSpec): ApkOutput {
    private val testingApk = testVariant.artifacts.get(SingleArtifact.APK)
    private val mainVariantApkOutput = DefaultApkOutput(targetVariant, deviceSpec)

    override val apkInstallGroups: List<ApkInstallGroup>
    get() {
        return mainVariantApkOutput.apkInstallGroups + fetchTestingApkOutput()
    }

    fun setInputs(inputs: TaskInputs) {
        mainVariantApkOutput.setInputs(inputs)
        inputs.files(testingApk)
            .withNormalizer(ClasspathNormalizer::class.java)
    }

    private fun fetchTestingApkOutput(): List<ApkInstallGroup> {
        val testingApks = listOf(RegularFile { TestData.getTestingApk(testingApk.get()) })
        return listOf(DefaultDeviceApkOutput.DefaultApkInstallGroup(
            testingApks, "Testing Apk"))
    }
}
