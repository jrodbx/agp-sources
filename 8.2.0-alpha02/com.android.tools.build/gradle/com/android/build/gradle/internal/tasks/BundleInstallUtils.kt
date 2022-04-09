/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("BundleInstallUtils")
package com.android.build.gradle.internal.tasks

import com.android.builder.testing.api.DeviceConfigProvider
import com.android.bundle.Devices
import com.android.tools.build.bundletool.commands.ExtractApksCommand
import com.android.zipflinger.ZipArchive
import com.google.common.collect.ImmutableSet
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

internal fun getDeviceSpec(device: DeviceConfigProvider): Devices.DeviceSpec {
    return Devices.DeviceSpec.newBuilder().also { spec ->
        device.apiLevel.takeIf { it > 0 }?.let { spec.sdkVersion = it }
        device.apiCodeName?.let { spec.codename = it }
        device.abis.takeIf { it.isNotEmpty() }?.let { spec.addAllSupportedAbis(it) }
        device.density.takeIf { it > 0 }?.let { spec.screenDensity = it }
        device.languageSplits?.let { spec.addAllSupportedLocales(it) }
    }.build()
}

internal fun getApkFiles(
    apkBundle: Path,
    device: DeviceConfigProvider,
    moduleName: String? = null
): List<Path> {
    // get the device info to create the APKs
    val tempFolder: Path = Files.createTempDirectory("apkSelect")

    val deviceSpec: Devices.DeviceSpec = getDeviceSpec(device)

    val command = ExtractApksCommand
        .builder()
        .setApksArchivePath(apkBundle)
        .setDeviceSpec(deviceSpec)
        .setOutputDirectory(tempFolder)

    moduleName?.let { command.setModules(ImmutableSet.of(it)) }

    // create the APKs
    return command.build().execute()
}
// This is required until there is a new version of bundletool capable of extracting it via the
// method above.
internal fun extractApkFilesBypassingBundleTool(apkBundle: Path): List<Path> {
    val tempFolder: Path = Files.createTempDirectory(apkBundle.nameWithoutExtension)
    val outputFile = tempFolder.resolve("extracted-apk.apk")
    ZipArchive(apkBundle).use {
        it.getInputStream("standalones/standalone.apk").use { inputStream ->
            Files.copy(inputStream, outputFile)
        }
    }
    return listOf(outputFile)
}
