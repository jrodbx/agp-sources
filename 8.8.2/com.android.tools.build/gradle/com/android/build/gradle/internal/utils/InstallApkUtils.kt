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

import com.android.SdkConstants
import com.android.build.api.variant.DeviceSpec
import com.android.builder.testing.api.DeviceConnector
import com.android.tools.profgen.SDK_LEVEL_FOR_V0_1_5_S
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.io.Files
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.stream.Collectors

/**
 * Get [DeviceSpec] from given [DeviceConnector]
 */
fun getDeviceSpec(device: DeviceConnector): DeviceSpec {
    return DeviceSpec.Builder()
        .setName(device.name)
        .setApiLevel(device.apiLevel)
        .setCodeName(device.apiCodeName)
        .setAbis(device.abis)
        .setSupportsPrivacySandbox(device.supportsPrivacySandbox)
        .setScreenDensity(device.density)
        .build()
}

/**
 * Given a [Directory] [Provider], fetches all the Apks that this [Directory] contains.
 */
fun getFiles(directoryProvider: Provider<Directory>): List<File> {
    if (directoryProvider.isPresent) {
        return directoryProvider.get()
            .asFileTree
            .files
            .stream()
            .filter { file: File ->
                file.getName()
                    .endsWith(SdkConstants.DOT_ANDROID_PACKAGE)
            }
            .collect(Collectors.toList())
    }
    return listOf()
}

/**
 * This method takes the dexMetadataDirectory which is an output of the CompileArtProfileTask
 * and uses its contents to add one or more .dm files to [apkFiles] to be installed on the
 * device. This will only execute if the dexMetadataDirectory exists.
 */
@Throws(IOException::class)
fun addDexMetadataFiles(
    dexMetadataDirectory: Provider<Directory>?,
    apkDirectory: Directory,
    deviceApiLevel: Int,
    apkFiles: MutableList<File>,
    iLogger: ILogger
) {
    val dmDir = dexMetadataDirectory?.getOrNull()
    if (dmDir == null || !dmDir.file(SdkConstants.FN_DEX_METADATA_PROP).asFile.exists()) {
        return
    }
    val dexMetadataProperties = dmDir.file(SdkConstants.FN_DEX_METADATA_PROP).asFile
    val inputStream: InputStream = FileInputStream(dexMetadataProperties)
    val properties = Properties()
    properties.load(inputStream)
    val dmPath = if (deviceApiLevel > SDK_LEVEL_FOR_V0_1_5_S) {
        properties.getProperty(Int.MAX_VALUE.toString())
    } else {
        properties.getProperty(deviceApiLevel.toString())
    }
    if (dmPath == null) {
        iLogger.info("Baseline Profile not found for API level {}", deviceApiLevel)
        return
    }

    if (apkFiles.isNotEmpty()) {
        val fileIndex = File(dmPath).parentFile.name
        val numApks = apkFiles.size
        for (i in 0 until numApks) {
            val apkFileName = apkFiles[i].name
            if (apkFileName.endsWith(".apk")) {
                val apkName = Files.getNameWithoutExtension(apkFileName)
                val renamedBaselineProfile =
                    FileUtils.join(
                        apkDirectory.asFile,
                        SdkConstants.FN_OUTPUT_BASELINE_PROFILES,
                        fileIndex,
                        "$apkName.dm"
                    )
                if (!renamedBaselineProfile.exists()) {
                    iLogger.info("Baseline Profile at {} was not found.",
                        renamedBaselineProfile.absolutePath)
                    return
                }
                apkFiles.add(renamedBaselineProfile)
            }
        }
    }
}
