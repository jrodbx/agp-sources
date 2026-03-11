/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import com.android.SdkConstants
import com.android.io.CancellableFileIo
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.ConfigKey.TAG_IDS
import com.android.sdklib.repository.IdDisplay
import com.android.utils.ILogger
import com.android.utils.asSeparatedListContains
import com.android.utils.mapValuesNotNull
import java.nio.file.Path

/**
 * An immutable structure describing an Android Virtual Device.
 *
 * @property iniFile The path to the AVD's metadata ini file. This is not the config.ini that resides within folderPath, but the file that
 *   resides parallel to the AVD's data folder in the AVD base folder.
 * @property dataFolderPath The path to the data directory, normally with an ".avd" suffix
 * @property systemImage The system image. Can be null if the system image is not found.
 * @property properties The configuration properties (from config.ini). Keys are defined in [ConfigKey].
 * @property userSettings The user settings from user-settings.ini. Keys are defined in [UserSettingsKey].
 * @property environment The environment from environment.ini. Keys are defined in [EnvironmentKey].
 * @property status The error condition of the AVD, or AvdStatus.OK.
 */
data class AvdInfo(
  val iniFile: Path,
  val dataFolderPath: Path,
  val systemImage: ISystemImage?,
  val properties: Map<String, String> = emptyMap(),
  val userSettings: Map<String, String> = emptyMap(),
  val environment: Map<String, String> = emptyMap(),
  val status: AvdStatus = AvdStatus.OK,
) {
  @JvmOverloads
  constructor(
    iniFile: Path,
    dataFolderPath: Path,
    systemImage: ISystemImage?,
    properties: Map<String, String>?,
    userSettings: Map<String, String?>?,
    status: AvdStatus = AvdStatus.OK,
  ) : this(
    iniFile = iniFile,
    dataFolderPath = dataFolderPath,
    systemImage = systemImage,
    properties = properties ?: emptyMap(),
    userSettings = userSettings?.mapValuesNotNull { (k, v) -> v } ?: emptyMap(),
    environment = emptyMap(),
    status = status,
  )

  /** Status for an [AvdInfo]. Indicates whether or not this AVD is valid. */
  enum class AvdStatus {
    /** No error */
    OK,
    /** Missing config.ini file in the AVD data folder */
    ERROR_CONFIG,
    /** Unable to parse config.ini */
    ERROR_PROPERTIES,
    /** The [Device] this AVD is based on is no longer available */
    ERROR_DEVICE_MISSING,
    /** the [SystemImage] this AVD is based on is no longer available */
    ERROR_IMAGE_MISSING,
    /** The AVD's .ini file is corrupted */
    ERROR_CORRUPTED_INI,
  }

  /** A stable ID for the AVD that doesn't change even if the device is renamed */
  val id: String
    get() = dataFolderPath.toString()

  /** The name of the AVD. Do not use this as a device ID; use getId instead. */
  val name: String
    get() = getAvdNameFromFile(this.iniFile)

  /** The name of the AVD for use in UI. */
  val displayName: String
    get() = properties[ConfigKey.DISPLAY_NAME] ?: avdNameToDisplayName(this.name)

  /** The tag id/display of the AVD. */
  val tag: IdDisplay
    get() {
      val id = this.properties[ConfigKey.TAG_ID] ?: return SystemImageTags.DEFAULT_TAG
      val display = this.properties[ConfigKey.TAG_DISPLAY]
      return IdDisplay.create(id, display ?: id)
    }

  val tags: List<IdDisplay>
    get() {
      val ids = properties[TAG_IDS] ?: return listOf(tag)
      val displays = properties[ConfigKey.TAG_DISPLAYNAMES] ?: ""
      val idList = ids.split(',')
      val displayList = displays.split(',')
      return idList.mapIndexed { i, id -> IdDisplay.create(id, displayList.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: id) }
    }

  /** The ABI type of the AVD. */
  val abiType: String?
    get() = this.properties[ConfigKey.ABI_TYPE]

  /** Returns true if this AVD supports Google Play Store */
  fun hasPlayStore(): Boolean {
    val enabled = properties[ConfigKey.PLAYSTORE_ENABLED]
    return "true".equals(enabled, ignoreCase = true) || "yes".equals(enabled, ignoreCase = true)
  }

  val androidVersion: AndroidVersion
    get() = getProperty(ConfigKey.TARGET)?.let { AndroidTargetHash.getPlatformVersion(it) } ?: AndroidVersion.DEFAULT

  val cpuArch: String
    get() = properties[ConfigKey.CPU_ARCH] ?: SdkConstants.CPU_ARCH_ARM

  val deviceManufacturer: String
    get() = properties[ConfigKey.DEVICE_MANUFACTURER] ?: ""

  val deviceName: String
    get() = properties[ConfigKey.DEVICE_NAME] ?: ""

  /** The path to the config.ini file for this AVD. */
  val configFile: Path
    get() = getConfigFile(this.dataFolderPath)

  /** Returns the value of the property with the given name, or null if the AVD doesn't have such property. */
  fun getProperty(propertyName: String): String? {
    return properties[propertyName]
  }

  /** The error message for the AVD, or `null` if [status] is [AvdStatus.OK]. */
  val errorMessage: String?
    get() =
      when (this.status) {
        AvdStatus.ERROR_CONFIG -> "Missing config.ini file in $dataFolderPath"
        AvdStatus.ERROR_PROPERTIES -> "Failed to parse properties from $configFile"
        AvdStatus.ERROR_IMAGE_MISSING -> {
          val tag = if (SystemImageTags.DEFAULT_TAG == this.tag) "" else (this.tag.getDisplay() + " ")
          this.tag
          val image = properties[ConfigKey.IMAGES_1]
          if (image == null) "System image missing in configuration"
          else {
            val path = image.removePrefix("system-images").trim('/', '\\')
            "Missing system image $path."
          }
        }
        AvdStatus.ERROR_DEVICE_MISSING ->
          "${properties[ConfigKey.DEVICE_MANUFACTURER]} ${properties[ConfigKey.DEVICE_NAME]} no longer exists as a device"
        AvdStatus.ERROR_CORRUPTED_INI -> "Corrupted AVD ini file: $iniFile"
        AvdStatus.OK -> null
      }

  fun isSameMetadata(avdInfo: AvdInfo): Boolean {
    return userSettings == avdInfo.userSettings
  }

  fun copyMetadata(other: AvdInfo): AvdInfo {
    return copy(userSettings = other.userSettings)
  }

  /** Checks if the AVD has the given tag in the "tag.ids" property. */
  fun hasTag(tag: String): Boolean {
    val tags = properties[TAG_IDS]
    return tags != null && tags.asSeparatedListContains(tag)
  }

  val isXrHeadsetDevice: Boolean
    get() = hasTag(SystemImageTags.XR_HEADSET_TAG.getId())

  val isAiGlassesDevice: Boolean
    get() = hasTag(SystemImageTags.AI_GLASSES_TAG.getId()) || hasTag(SystemImageTags.DEPRECATED_AI_GLASSES_TAG.getId())

  val isAiGlassesCompatibleDevice: Boolean
    get() = hasTag(SystemImageTags.AI_GLASSES_COMPATIBLE_TAG.getId())

  companion object {
    /** Extracts the name of the AVD from the file name. */
    @JvmStatic
    fun getAvdNameFromFile(file: Path): String {
      val iniFilename = file.fileName.toString()
      return if (iniFilename.lowercase().endsWith(".ini")) iniFilename.dropLast(4) else iniFilename
    }

    /** Formats the AVD name for use in UI. */
    @JvmStatic
    fun avdNameToDisplayName(avdName: String): String {
      return avdName.replace('_', ' ')
    }

    /**
     * Helper method that returns the default AVD folder that would be used for a given AVD name *if and only if* the AVD was created with
     * the default choice.
     *
     * Callers must NOT use this to "guess" the actual folder from an actual AVD since the purpose of the AVD .ini file is to be able to
     * change this folder. Callers should however use this to create a new [AvdInfo] to setup its data folder to the default.
     *
     * The default is `getBaseAvdFolder()/avdname.avd/`.
     *
     * For an actual existing AVD, callers must use [.getDataFolderPath] instead.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     * @param unique Whether to return the default or a unique variation of the default.
     */
    @JvmStatic
    fun getDefaultAvdFolder(manager: AvdManager, avdName: String, unique: Boolean): Path {
      val base = manager.baseAvdFolder
      var result = base.resolve(avdName + AvdManager.AVD_FOLDER_EXTENSION)
      if (unique) {
        var suffix = 0
        while (CancellableFileIo.exists(result)) {
          result = base.resolve("${avdName}_${++suffix}${AvdManager.AVD_FOLDER_EXTENSION}")
        }
      }
      return result
    }

    /**
     * Helper method that returns the .ini [Path] for a given AVD name.
     *
     * The default is `getBaseAvdFolder()/avdname.ini`.
     *
     * @param manager The AVD Manager, used to get the AVD storage path.
     * @param avdName The name of the desired AVD.
     */
    @JvmStatic
    fun getDefaultIniFile(manager: AvdManager, avdName: String): Path {
      return manager.baseAvdFolder.resolve(avdName + AvdManager.INI_EXTENSION)
    }

    /** Helper method that returns the Config file for a given AVD name. */
    fun getConfigFile(path: Path): Path {
      return path.resolve(AvdManager.CONFIG_INI)
    }

    /** Helper method that returns the User Settings Path. */
    @JvmStatic
    fun getUserSettingsPath(dataFolder: Path): Path {
      return dataFolder.resolve(AvdManager.USER_SETTINGS_INI)
    }

    @JvmStatic
    fun parseUserSettingsFile(dataFolder: Path, logger: ILogger?): Map<String, String> {
      val settingsPath = PathFileWrapper(getUserSettingsPath(dataFolder))
      if (settingsPath.exists()) {
        return AvdManager.parseIniFile(settingsPath, logger) ?: emptyMap()
      }
      return emptyMap()
    }
  }
}
