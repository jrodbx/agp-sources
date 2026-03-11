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

import com.android.build.api.dsl.Device
import com.android.build.api.dsl.DeviceGroup
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.prefs.AndroidLocationsProvider
import com.android.testing.utils.computeVendorString
import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory

private const val GRADLE_AVD_DIRECTORY_PATH = "gradle/avd"

/** Gets the provider of the avd folder for managed devices across all projects. */
fun getManagedDeviceAvdFolder(
  objectFactory: ObjectFactory,
  providerFactory: ProviderFactory,
  androidLocationsProvider: AndroidLocationsProvider,
): DirectoryProperty =
  objectFactory.directoryProperty().fileProvider(providerFactory.provider { androidLocationsProvider.gradleAvdLocation.toFile() })

fun computeAvdName(device: ManagedVirtualDevice): String =
  computeAvdName(
    device.sdkVersion,
    device.sdkMinorVersion,
    device.sdkExtensionVersion,
    device.systemImageSource,
    device.pageAlignmentSuffix,
    computeAbiFromArchitecture(device),
    device.device,
  )

fun computeAvdName(
  sdkVersion: Int,
  sdkMinorVersion: Int,
  extensionVersion: Int?,
  imageSource: String,
  pageAlignmentSuffix: String,
  abi: String,
  hardwareProfile: String,
): String {
  val sanitizedProfile = sanitizeProfileName(hardwareProfile)
  val version = computeVersionIdentifier(sdkVersion, sdkMinorVersion, extensionVersion)
  val vendor = computeVendorString(imageSource, pageAlignmentSuffix)
  return "dev${version}_${vendor}_${abi}_$sanitizedProfile"
}

fun computeVersionIdentifier(sdkVersion: Int, sdkMinorVersion: Int, extensionVersion: Int?) =
  sdkVersion.toString() +
    if (sdkMinorVersion != 0) "_m$sdkMinorVersion" else "" + if (extensionVersion != null) "_ext$extensionVersion" else ""

fun sanitizeProfileName(hardwareProfile: String) = hardwareProfile.replace(Regex("[() \"]"), "_")

fun setupTaskName(device: Device): String = "${device.name}Setup"

fun managedDeviceAllVariantsTaskName(device: Device): String = "${device.name}Check"

fun managedDeviceGroupAllVariantsTaskName(deviceGroup: DeviceGroup): String = "${deviceGroup.name}GroupCheck"

fun managedDeviceGroupSingleVariantTaskName(creationConfig: InstrumentedTestCreationConfig, deviceGroup: DeviceGroup): String =
  creationConfig.computeTaskNameInternal("${deviceGroup.name}Group")

fun computeAbiFromArchitecture(device: ManagedVirtualDevice): String =
  computeAbiFromArchitecture(device.require64Bit, device.sdkVersion, device.systemImageSource)

fun computeAbiFromArchitecture(require64Bit: Boolean, sdkVersion: Int, vendor: String, cpuArch: CpuArchitecture = osArchitecture): String =
  when {
    cpuArch == CpuArchitecture.ARM || cpuArch == CpuArchitecture.X86_ON_ARM -> "arm64-v8a"
    require64Bit -> "x86_64"
    // Neither system-images;android-30;default;x86 nor system-images;android-26;default;x86
    // exist, but the google images do.
    vendor == "aosp" && sdkVersion in listOf(26, 30) -> "x86_64"
    sdkVersion <= 30 -> "x86"
    else -> "x86_64"
  }
