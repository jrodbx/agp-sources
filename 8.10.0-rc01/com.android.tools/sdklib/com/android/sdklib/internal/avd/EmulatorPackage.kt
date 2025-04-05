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
@file:JvmName("EmulatorPackages")

package com.android.sdklib.internal.avd

import com.android.SdkConstants
import com.android.io.CancellableFileIo
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.HardwareProperties.HardwareProperty
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate

/**
 * Wraps a LocalPackage for an emulator, providing access to its contents and encoding domain
 * knowledge about emulators.
 */
class EmulatorPackage(private val emulator: LocalPackage) {
  val version: Revision
    get() = emulator.version

  val isQemu2: Boolean
    get() = version >= TOOLS_REVISION_WITH_FIRST_QEMU2

  /**
   * Read metadata about hardware properties from hardware-properties.ini in the emulator package.
   */
  fun getHardwareProperties(logger: ILogger): Map<String, HardwareProperty>? {
    val propertiesPath =
      emulator.location.resolve(SdkConstants.FD_LIB).resolve(SdkConstants.FN_HARDWARE_INI)
    return HardwareProperties.parseHardwareDefinitions(PathFileWrapper(propertiesPath), logger)
  }

  @JvmOverloads
  fun getEmulatorFeatures(
    logger: ILogger,
    channel: EmulatorFeaturesChannel = EmulatorFeaturesChannel.RELEASE,
  ): Set<String>? {
    // Fall back from less-stable channels to more-stable channels
    if (channel >= EmulatorFeaturesChannel.CANARY)
      getEmulatorFeatures(EmulatorFeaturesChannel.CANARY.featuresFile, logger)?.let {
        return it
      }
    return getEmulatorFeatures(EmulatorFeaturesChannel.RELEASE.featuresFile, logger)
  }

  private fun getEmulatorFeatures(featuresFile: String, logger: ILogger): Set<String>? {
    val featuresPath = emulator.location.resolve(SdkConstants.FD_LIB).resolve(featuresFile)
    if (Files.exists(featuresPath)) {
      return ProjectProperties.parsePropertyFile(PathFileWrapper(featuresPath), logger)
        ?.filter { it.value == "on" }
        ?.keys
    }
    return null
  }

  private fun getBinaryLocation(filename: String): Path? {
    return emulator.location.resolve(filename).takeIf { CancellableFileIo.exists(it) }
  }

  val emulatorBinary: Path?
    get() = getBinaryLocation(SdkConstants.FN_EMULATOR)

  val emulatorCheckBinary: Path?
    get() = getBinaryLocation(SdkConstants.FN_EMULATOR_CHECK)

  val mkSdCardBinary: Path?
    get() = getBinaryLocation(SdkConstants.mkSdCardCmdName())

  fun getSystemImageUpdateRequiredPredicate(): Predicate<SystemImage> {
    val dependencies = getSystemImageUpdateDependencies()
    return Predicate { systemImage: SystemImage ->
      dependencies.any { it.updateRequired(systemImage) }
    }
  }

  private fun getSystemImageUpdateDependencies(): List<SystemImageUpdateDependency> {
    val version = version
    return when {
      version >= TOOLS_REVISION_25_0_2_RC3 -> SYSTEM_IMAGE_DEPENDENCY_WITH_25_0_2_RC3
      version >= TOOLS_REVISION_WITH_FIRST_QEMU2 -> SYSTEM_IMAGE_DEPENDENCY_WITH_FIRST_QEMU2
      else -> ImmutableList.of()
    }
  }

  /**
   * Indicates if the emulator supports passing parameters in a file via the -studio-params
   * argument.
   */
  fun hasStudioParamsSupport(): Boolean {
    return version >= Revision.parseRevision("26.1.0")
  }
}

enum class EmulatorFeaturesChannel(val featuresFile: String) {
  RELEASE(SdkConstants.FN_ADVANCED_FEATURES),
  CANARY(SdkConstants.FN_ADVANCED_FEATURES_CANARY),
}

object EmulatorAdvancedFeatures {
  const val FAST_BOOT = "FastSnapshotV1"
  const val SCREEN_RECORDING = "ScreenRecording"
  const val VIRTUAL_SCENE = "VirtualScene"
}

fun AndroidSdkHandler.getEmulatorPackage(progress: ProgressIndicator): EmulatorPackage? =
  getLocalPackage(SdkConstants.FD_EMULATOR, progress)?.let { EmulatorPackage(it) }

private class SystemImageUpdateDependency(
  private val featureLevel: Int,
  private val tag: IdDisplay,
  private val requiredMajorRevision: Int,
) {
  fun updateRequired(image: SystemImage): Boolean {
    return updateRequired(
      image.primaryAbiType,
      image.androidVersion.featureLevel,
      image.tag,
      image.revision,
    )
  }

  fun updateRequired(
    abiType: String,
    featureLevel: Int,
    tag: IdDisplay,
    revision: Revision,
  ): Boolean {
    val abi = Abi.getEnum(abiType)
    val isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64

    return isAvdIntel &&
      featureLevel == this.featureLevel &&
      this.tag == tag &&
      revision.major < requiredMajorRevision
  }
}

private val TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("25.0.0 rc1")
private val TOOLS_REVISION_25_0_2_RC3 = Revision.parseRevision("25.0.2 rc3")
private const val MNC_API_LEVEL_23 = 23
private const val LMP_MR1_API_LEVEL_22 = 22

private val SYSTEM_IMAGE_DEPENDENCY_WITH_25_0_2_RC3 =
  listOf(
    SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, SystemImageTags.DEFAULT_TAG, 4),
    SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, SystemImageTags.GOOGLE_APIS_TAG, 4),
    SystemImageUpdateDependency(MNC_API_LEVEL_23, SystemImageTags.DEFAULT_TAG, 8),
    SystemImageUpdateDependency(MNC_API_LEVEL_23, SystemImageTags.GOOGLE_APIS_TAG, 12),
  )

private val SYSTEM_IMAGE_DEPENDENCY_WITH_FIRST_QEMU2 =
  listOf(
    SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, SystemImageTags.DEFAULT_TAG, 2),
    SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, SystemImageTags.GOOGLE_APIS_TAG, 2),
    SystemImageUpdateDependency(MNC_API_LEVEL_23, SystemImageTags.DEFAULT_TAG, 6),
    SystemImageUpdateDependency(MNC_API_LEVEL_23, SystemImageTags.GOOGLE_APIS_TAG, 10),
  )
