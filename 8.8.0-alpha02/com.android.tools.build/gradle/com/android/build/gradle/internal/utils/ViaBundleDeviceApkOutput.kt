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

import com.android.build.api.variant.ApkInstallGroup
import com.android.build.api.variant.DeviceSpec
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.utils.DefaultDeviceApkOutput.DefaultSdkApkInstallGroup
import com.android.builder.internal.InstallUtils
import com.android.bundle.Devices
import com.android.sdklib.AndroidVersion
import com.google.common.collect.Lists
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File
import java.nio.file.Path
import java.util.stream.Collectors

class ViaBundleDeviceApkOutput(
    private val apkBundle: Provider<RegularFile>,
    private val minSdkVersion: AndroidVersion,
    private val privacySandboxSdkApks: FileCollection,
    private val variantName: String,
    private val projectPath: String,
    private val apkFetcher: BundleApkFetcher = object: BundleApkFetcher {}
) : DeviceApkOutput {
    private val iLogger: LoggerWrapper = LoggerWrapper.getLogger(ViaBundleDeviceApkOutput::class.java)

    override fun getApks(deviceSpec: DeviceSpec): List<ApkInstallGroup> {
        val apkInstallGroups = mutableListOf<ApkInstallGroup>()
        if (InstallUtils.checkDeviceApiLevel(deviceSpec.name, deviceSpec.apiLevel, deviceSpec.codeName,
                minSdkVersion, iLogger, projectPath, variantName)
        ) {
            val privacySandboxSdksPresent =
                !privacySandboxSdkApks.isEmpty
            if (privacySandboxSdksPresent && deviceSpec.supportsPrivacySandbox) {
                privacySandboxSdkApks.files.forEach { file: File ->
                    val sdkApkFiles = apkFetcher.getPrivacySandboxSdkApkFiles(
                        file.toPath()).stream()
                        .map { RegularFile { it } }
                        .collect(Collectors.toUnmodifiableList())
                    apkInstallGroups.add(DefaultSdkApkInstallGroup({ file }, sdkApkFiles))
                }
            }
            val apkBuiltArtifacts: List<Path> = buildList {
                add(apkBundle.get().asFile.toPath())
            }
            val spec = Devices.DeviceSpec.newBuilder().also { spec ->
                deviceSpec.apiLevel.takeIf { it > 0 }?.let { spec.sdkVersion = it }
                deviceSpec.codeName?.let {  spec.codename = it  }
                deviceSpec.abis.takeIf { it.isNotEmpty() }?.let { spec.addAllSupportedAbis(it) }
                deviceSpec.screenDensity.takeIf { it > 0 }?.let { spec.screenDensity = it }
            }.build()
            var apkFiles: MutableList<RegularFile> = Lists.newLinkedList()
            val bundleApkFiles = apkFetcher.getApkFiles(apkBuiltArtifacts, spec).map { RegularFile { it.toFile() } }
            apkFiles.addAll(bundleApkFiles)
            apkInstallGroups.add(DefaultDeviceApkOutput.DefaultApkInstallGroup(apkFiles, "Apks from Main Bundle"))
        }
        return apkInstallGroups
    }
}
