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

package com.android.build.gradle.internal

import com.android.build.api.dsl.DeviceGroup
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.prefs.AndroidLocationsProvider
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

private const val GRADLE_AVD_DIRECTORY_PATH = "gradle/avd"

/**
 * Gets the provider of the avd folder for managed devices across all projects.
 */
fun getManagedDeviceAvdFolder(
    objectFactory: ObjectFactory,
    providerFactory: ProviderFactory,
    androidLocationsProvider: AndroidLocationsProvider
): DirectoryProperty =
    objectFactory.directoryProperty().fileProvider(providerFactory.provider {
        androidLocationsProvider.gradleAvdLocation.toFile()
    })

fun computeAvdName(device: ManagedVirtualDevice): String =
    computeAvdName(
        device.apiLevel,
        device.systemImageSource,
        device.abi,
        device.device)

fun computeAvdName(
    apiLevel: Int,
    vendor: String,
    abi: String,
    hardwareProfile: String) =
    "dev${apiLevel}_${vendor}_${abi}_${hardwareProfile.replace(' ', '_')}"

fun setupTaskName(device: ManagedVirtualDevice): String = "${device.name}Setup"

fun managedDeviceAllVariantsTaskName(device: ManagedVirtualDevice): String = "${device.name}Check"

fun managedDeviceGroupAllVariantsTaskName(deviceGroup: DeviceGroup): String =
    "${deviceGroup.name}GroupCheck"

fun managedDeviceGroupSingleVariantTaskName(
    creationConfig: VariantCreationConfig, deviceGroup: DeviceGroup): String =
    creationConfig.computeTaskName("${deviceGroup.name}Group")
