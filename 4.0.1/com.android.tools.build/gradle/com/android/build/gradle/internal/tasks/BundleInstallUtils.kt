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
import com.google.common.base.Joiner
import com.google.common.collect.ImmutableSet
import com.google.protobuf.util.JsonFormat
import java.nio.file.Files
import java.nio.file.Path

internal fun getDeviceJson(device: DeviceConfigProvider): Path {

    val api = device.apiCodeName ?: device.apiLevel
    val density = device.density
    val abis = device.abis
    val languages: Set<String>? = device.languageSplits

    return Files.createTempFile("apkSelect", "").apply {
        var json = "{\n" +
                "  \"supportedAbis\": [${abis.joinToString()}],\n" +
                "  \"screenDensity\": $density,\n" +
                "  \"sdkVersion\": $api"

        if (languages != null && !languages.isEmpty()) {
            json = "$json,\n  \"supportedLocales\": [ ${Joiner.on(',').join(languages)} ]\n"
        }

        json = "$json}"

        Files.write(this, json.toByteArray(Charsets.UTF_8))
    }
}

internal fun getApkFiles(
    apkBundle: Path,
    device: DeviceConfigProvider,
    moduleName: String? = null
): List<Path> {
    // get the device info to create the APKs
    val jsonFile = getDeviceJson(device)
    val tempFolder: Path = Files.createTempDirectory("apkSelect")

    val builder: Devices.DeviceSpec.Builder = Devices.DeviceSpec.newBuilder()

    Files.newBufferedReader(jsonFile, Charsets.UTF_8).use {
        JsonFormat.parser().merge(it, builder)
    }

    val command = ExtractApksCommand
        .builder()
        .setApksArchivePath(apkBundle)
        .setDeviceSpec(builder.build())
        .setOutputDirectory(tempFolder)

    moduleName?.let { command.setModules(ImmutableSet.of(it)) }

    // create the APKs
    return command.build().execute()
}