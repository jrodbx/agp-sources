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
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.testing.TestData
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskInputs

class TestApkOutput(variant: TestVariantCreationConfig, val deviceSpec: DeviceSpec) : ApkOutput {
  private val testingApk: Provider<Directory>
  private val apkSources: ApkSources
  private val deviceApkOutput: DeviceApkOutput

  init {
    apkSources = getApkSources(variant)
    deviceApkOutput =
      DefaultDeviceApkOutput(
        apkSources,
        variant.nativeBuildCreationConfig?.supportedAbis,
        variant.minSdk.toSharedAndroidVersion(),
        variant.baseName,
        variant.services.projectInfo.path,
      )
    testingApk = variant.artifacts.get(SingleArtifact.APK)
  }

  override val apkInstallGroups: List<ApkInstallGroup>
    get() = deviceApkOutput.getApks(deviceSpec) + fetchTestingApk()

  fun setTaskInputs(inputs: TaskInputs) {
    val apkInputs = DefaultDeviceApkOutput.getApkInputs(apkSources, deviceSpec) + testingApk
    inputs.files(*apkInputs.toTypedArray()).withNormalizer(ClasspathNormalizer::class.java)
  }

  private fun fetchTestingApk(): ApkInstallGroup {
    val testingApks = listOf(RegularFile { TestData.getTestingApk(testingApk.get()) })
    return DefaultDeviceApkOutput.DefaultApkInstallGroup(testingApks, "Testing Apk")
  }

  private fun getApkSources(variant: TestVariantCreationConfig): ApkSources {
    return ApkSources(variant.allTestedApks)
  }
}
