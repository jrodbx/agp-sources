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
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl
import com.android.build.api.variant.impl.toSharedAndroidVersion
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.builder.internal.InstallUtils
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.TaskInputs

class DynamicFeatureApkOutput(variant: DynamicFeatureCreationConfig, val deviceSpec: DeviceSpec) : ApkOutput {
  private val minSdkVersion = variant.minSdk.toSharedAndroidVersion()
  private val projectPath = variant.services.projectInfo.path
  private val variantName = variant.baseName
  private val bundleFile: Provider<RegularFile>
  private val viaBundleDeviceApkOutput: DeviceApkOutput
  private val supportedAbis = variant.nativeBuildCreationConfig?.supportedAbis
  private val logger: Logger = Logging.getLogger(DynamicFeatureVariantImpl::class.java)
  private val iLogger = LoggerWrapper(logger)
  private val mainApkArtifact = variant.artifacts.get(SingleArtifact.APK)

  init {
    val apkBundles =
      variant.variantDependencies
        .getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH, ArtifactScope.PROJECT, AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE)
        .artifactFiles
    bundleFile = apkBundles.elements.map { RegularFile { it.single().asFile } }
    viaBundleDeviceApkOutput = ViaBundleDeviceApkOutput(bundleFile, minSdkVersion, variantName, projectPath)
  }

  fun setInputs(inputs: TaskInputs) {
    inputs.files(mainApkArtifact, bundleFile).withNormalizer(ClasspathNormalizer::class.java)
  }

  override val apkInstallGroups: List<ApkInstallGroup>
    get() {
      val installGroups = mutableListOf<ApkInstallGroup>()
      val baseApkInstallGroup = viaBundleDeviceApkOutput.getApks(deviceSpec)
      installGroups.addAll(baseApkInstallGroup)
      if (
        InstallUtils.checkDeviceApiLevel(
          deviceSpec.name,
          deviceSpec.apiLevel,
          deviceSpec.codeName,
          minSdkVersion,
          iLogger,
          projectPath,
          variantName,
        )
      ) {
        val apkFiles = DefaultDeviceApkOutput.getMainApks(mainApkArtifact.get(), supportedAbis, deviceSpec)
        val featureApkInstallGroup =
          DefaultDeviceApkOutput.DefaultApkInstallGroup(
            apks = apkFiles.map { RegularFile { it } },
            description = "Dynamic feature Apk Group",
          )
        installGroups.add(featureApkInstallGroup)
      }
      return installGroups
    }
}
