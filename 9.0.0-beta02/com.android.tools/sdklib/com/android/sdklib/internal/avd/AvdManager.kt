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
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import com.android.io.IAbstractFile
import com.android.io.StreamException
import com.android.prefs.AndroidLocationsException
import com.android.repository.api.ConsoleProgressIndicator
import com.android.repository.api.ProgressIndicator
import com.android.repository.io.FileOpUtils
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus
import com.android.sdklib.internal.avd.AvdInfo.Companion.avdNameToDisplayName
import com.android.sdklib.internal.avd.AvdInfo.Companion.getAvdNameFromFile
import com.android.sdklib.internal.avd.AvdInfo.Companion.getDefaultAvdFolder
import com.android.sdklib.internal.avd.AvdInfo.Companion.getDefaultIniFile
import com.android.sdklib.internal.avd.AvdInfo.Companion.parseUserSettingsFile
import com.android.sdklib.internal.avd.AvdNames.cleanAvdName
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.FileUtils
import com.android.utils.GrabProcessOutput
import com.android.utils.GrabProcessOutput.IProcessOutput
import com.android.utils.ILogger
import com.android.utils.PathUtils
import com.google.common.annotations.VisibleForTesting
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Scanner
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors.toList
import kotlin.io.path.deleteIfExists

/**
 * Android Virtual Device Manager to manage AVDs.
 *
 * @property baseAvdFolder The base folder where AVDs are created.
 */
class AvdManager
private constructor(
  private val sdkHandler: AndroidSdkHandler,
  val baseAvdFolder: Path,
  private val deviceManager: DeviceManager,
  private val log: ILogger,
) {
  @GuardedBy("allAvdList") private val allAvdList = mutableListOf<AvdInfo>()

  @GuardedBy("allAvdList") private var validAvdList: List<AvdInfo>? = null

  private val sdkLocation = checkNotNull(sdkHandler.location) { "Local SDK path not set!" }

  init {
    try {
      buildAvdList(allAvdList)
    } catch (e: AndroidLocationsException) {
      log.warning("Constructing AvdManager: %s", e.message)
    }
  }

  /**
   * Returns all the existing AVDs.
   *
   * @return a newly allocated array containing all the AVDs.
   */
  val allAvds: List<AvdInfo>
    get() {
      synchronized(allAvdList) {
        return allAvdList.toMutableList()
      }
    }

  /** Returns all the valid AVDs. */
  val validAvds: List<AvdInfo>
    get() {
      synchronized(allAvdList) {
        if (validAvdList == null) {
          validAvdList = allAvdList.filter { it.status == AvdStatus.OK }
        }
        return validAvdList!!
      }
    }

  /**
   * Returns the [AvdInfo] matching the given <var>name</var>.
   *
   * The search is case-insensitive on Windows.
   *
   * @param name the name of the AVD to return
   * @param validAvdOnly if `true`, only look through the list of valid AVDs.
   * @return the matching AvdInfo or `null` if none were found.
   */
  fun getAvd(name: String, validAvdOnly: Boolean): AvdInfo? {
    val ignoreCase = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
    val list = if (validAvdOnly) this.validAvds else this.allAvds
    return list.firstOrNull { it.name.equals(name, ignoreCase) }
  }

  /**
   * Returns the [AvdInfo] with the given <var>display name</var>.
   *
   * @return the matching AvdInfo or `null` if none were found.
   */
  fun findAvdWithDisplayName(displayName: String): AvdInfo? {
    return allAvds.firstOrNull { it.displayName == displayName }
  }

  /** Returns the [AvdInfo] with the given id (path of the AVD folder), or null if not found. */
  fun findAvdWithFolder(avdFolder: Path): AvdInfo? {
    return allAvds.firstOrNull { it.dataFolderPath == avdFolder }
  }

  /**
   * Returns the process ID of the emulator running for the given AVD, or zero if the AVD is not
   * running.
   */
  @Slow
  fun getPid(avd: AvdInfo): Long {
    val pid = getPid(avd, "hardware-qemu.ini.lock")
    if (pid != 0L) {
      return pid
    }

    return getPid(avd, "userdata-qemu.img.lock")
  }

  private fun getPid(avd: AvdInfo, element: String): Long {
    val file = resolveLockFile(avd, element)
    try {
      return Scanner(file).use { scanner ->
        scanner.useDelimiter("\u0000")
        scanner.nextLong()
      }
    } catch (_: NoSuchFileException) {
      log.info("%s not found for %s", file, avd.name)
    } catch (e: IOException) {
      log.error(e, "avd = %s, file = %s", avd.name, file)
    } catch (e: NoSuchElementException) {
      log.error(e, "avd = %s, file = %s", avd.name, file)
    }
    return 0
  }

  /** Deletes lock files from the AVD directory if they exist there. */
  @Slow
  fun deleteLockFiles(avd: AvdInfo) {
    deleteLockFile(avd, "hardware-qemu.ini.lock")
    deleteLockFile(avd, "userdata-qemu.img.lock")
  }

  private fun deleteLockFile(avd: AvdInfo, element: String) {
    val file = resolveLockFile(avd, element)
    try {
      Files.deleteIfExists(file)
    } catch (e: IOException) {
      log.error(e, "Unable to delete %s", file)
    }
  }

  @VisibleForTesting
  fun resolveLockFile(avd: AvdInfo, element: String): Path {
    val path = baseAvdFolder.resolve(avd.dataFolderPath).resolve(element)

    // path is a file on Linux and macOS. On Windows it's a directory. Return the path to the
    // pid file under it.
    if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
      return path.resolve("pid")
    }

    return path
  }

  /**
   * Reloads the AVD list.
   *
   * @throws AndroidLocationsException if there's an error finding the location of the AVD folder.
   */
  @Slow
  @Throws(AndroidLocationsException::class)
  fun reloadAvds() {
    sdkHandler.clearSystemImageManagerCache()
    // Build the list in a temp list first, in case the method throws an exception.
    // It's better than deleting the whole list before reading the new one.
    val allList = mutableListOf<AvdInfo>()
    buildAvdList(allList)

    synchronized(allAvdList) {
      allAvdList.clear()
      allAvdList.addAll(allList)
      validAvdList = null
    }
  }

  /**
   * Reloads a single AVD but does not update the list.
   *
   * @param avdInfo an existing AVD
   * @return an updated AVD
   */
  @Slow
  fun reloadAvd(avdInfo: AvdInfo): AvdInfo {
    val newInfo = parseAvdInfo(avdInfo.iniFile)
    synchronized(allAvdList) {
      val index = allAvdList.indexOf(avdInfo)
      if (index >= 0) {
        // Update the existing list of AVDs, unless the original AVD is not found, in which
        // case someone else may already have updated the list.
        replaceAvd(avdInfo, newInfo)
      }
    }
    return newInfo
  }

  /**
   * Initializes an AvdBuilder based on a Device. This is used to set defaults for a device that is
   * under construction for the first time.
   */
  fun createAvdBuilder(device: Device): AvdBuilder {
    val avdName = uniquifyAvdName(cleanAvdName(device.displayName))
    val avdFolder = getDefaultAvdFolder(this, avdName, true)
    return AvdBuilder(baseAvdFolder.resolve("$avdName.ini"), avdFolder, device).apply {
      initializeFromDevice()
      displayName = uniquifyDisplayName(device.displayName)
    }
  }

  /**
   * Creates an AVD from the given AvdBuilder.
   *
   * @return the resulting AvdInfo
   * @throws AvdManagerException if the creation failed
   */
  @Slow
  @Throws(AvdManagerException::class)
  fun createAvd(builder: AvdBuilder): AvdInfo {
    require(Files.notExists(builder.avdFolder)) { "AVD already exists" }
    return createOrEditAvd(builder)
  }

  /**
   * Edits an existing AVD (passed as AvdInfo) using the definition from the given AvdBuilder. If
   * the AVD name or data folder path have changed, move the existing AVD to the new location before
   * applying the edits.
   *
   * @return the resulting AvdInfo
   * @throws AvdManagerException if the editing failed.
   */
  @Slow
  @Throws(AvdManagerException::class)
  fun editAvd(avdInfo: AvdInfo, builder: AvdBuilder): AvdInfo {
    if (avdInfo.name != builder.avdName || avdInfo.dataFolderPath != builder.avdFolder) {
      moveAvd(avdInfo, builder.avdName, builder.avdFolder)
    }
    return createOrEditAvd(builder)
  }

  /**
   * Creates a copy of an existing AVD (passed as AvdInfo) using the definition from the given
   * AvdBuilder. This copies the full contents of the old directory, including files generated by
   * the emulator such as snapshots and hardware-qemu.ini, then writes the new config from the
   * supplied builder on top of it.
   *
   * @return the resulting AvdInfo
   * @throws AvdManagerException if the duplication failed.
   */
  @Slow
  @Throws(AvdManagerException::class)
  fun duplicateAvd(avdInfo: AvdInfo, builder: AvdBuilder): AvdInfo {
    require(avdInfo.name != builder.avdName) { "Old and new name are the same" }
    require(avdInfo.dataFolderPath != builder.avdFolder) { "Old and new path are the same" }
    checkNotNull(builder.systemImage) { "systemImage is required" }

    duplicateAvd(avdInfo.dataFolderPath, builder.avdFolder, builder.avdName, builder.systemImage!!)

    return createOrEditAvd(builder)
  }

  @Throws(AvdManagerException::class)
  private fun createOrEditAvd(builder: AvdBuilder): AvdInfo {
    val avdName = checkNotNull(builder.avdName) { "avdName is required" }
    require(avdName == cleanAvdName(avdName)) {
      "AVD name \"$avdName\" contains invalid characters"
    }
    return createAvd(
      avdFolder = checkNotNull(builder.avdFolder) { "avdFolder is required" },
      avdName = avdName,
      systemImage = checkNotNull(builder.systemImage) { "systemImage is required" },
      skin = builder.skin,
      sdcard = builder.sdCard,
      hardwareConfig = builder.configProperties(),
      userSettings = builder.userSettings,
      bootProps = builder.device.bootProps,
      environment = builder.environment(),
      deviceHasPlayStore = builder.device.hasPlayStore(),
      removePrevious = false,
      editExisting = true,
    )
  }

  /**
   * Creates a new AVD. It is expected that there is no existing AVD with this name already.
   *
   * @param avdFolder the data folder for the AVD. It will be created as needed. Unless you want to
   *   locate it in a specific directory, the ideal default is [AvdInfo.getDefaultAvdFolder].
   * @param avdName the name of the AVD
   * @param systemImage the system image of the AVD
   * @param skin the skin to use, if specified. Can be null.
   * @param sdcard the parameter value for the sdCard. Can be null. This is either a path to an
   *   existing sdcard image or a sdcard size (\d+, \d+K, \dM).
   * @param hardwareConfig the hardware setup for the AVD. Can be null to use defaults.
   * @param userSettings optional settings for the AVD. Can be null.
   * @param bootProps the optional boot properties for the AVD. Can be null.
   * @param environment configuration of the simulated environment of an XR device
   * @param removePrevious If true remove any previous files.
   * @param editExisting If true, edit an existing AVD, changing only the minimum required. This
   *   won't remove files unless required or unless `removePrevious` is set.
   * @return The new [AvdInfo] in case of success (which has just been added to the internal list)
   * @throws AvdManagerException if AVD creation fails
   */
  @Slow
  @Throws(AvdManagerException::class)
  fun createAvd(
    avdFolder: Path,
    avdName: String,
    systemImage: ISystemImage,
    skin: Skin? = null,
    sdcard: SdCard? = null,
    hardwareConfig: Map<String, String>? = null,
    userSettings: Map<String, String>? = null,
    bootProps: Map<String, String>? = null,
    environment: Map<String, String>? = null,
    deviceHasPlayStore: Boolean = false,
    removePrevious: Boolean = false,
    editExisting: Boolean = false,
  ): AvdInfo {
    validateEnvironment(environment)

    val hardwareConfig = hardwareConfig?.toMutableMap() ?: mutableMapOf()
    var avdFolder = avdFolder
    var editExisting = editExisting
    var iniFile: Path? = null
    var needCleanup = false
    try {
      var newAvdInfo: AvdInfo? = null
      val configValues = mutableMapOf<String, String>()
      if (!CancellableFileIo.exists(avdFolder)) {
        // create the AVD folder.
        Files.createDirectories(avdFolder)
        inhibitCopyOnWrite(avdFolder, log)
        // We're not editing an existing AVD.
        editExisting = false
      } else if (removePrevious) {
        // AVD already exists and removePrevious is set, try to remove the
        // directory's content first (but not the directory itself).
        try {
          deleteContentOf(avdFolder)
          inhibitCopyOnWrite(avdFolder, log)
        } catch (e: SecurityException) {
          log.warning("Failed to delete %1\$s: %2\$s", avdFolder.toAbsolutePath(), e)
        }
      } else if (!editExisting) {
        // The AVD already exists, we want to keep it, and we're not
        // editing it. We must be making a copy. Duplicate the folder.
        val oldAvdFolderPath = avdFolder.toAbsolutePath().toString()
        val destAvdFolder = avdFolder.parent.resolve(avdName + AVD_FOLDER_EXTENSION)
        newAvdInfo = duplicateAvd(avdFolder, destAvdFolder, avdName, systemImage)
        avdFolder = baseAvdFolder.resolve(newAvdInfo.dataFolderPath)
        configValues.putAll(newAvdInfo.properties)
        // If the hardware config includes an SD Card path in the old directory,
        // update the path to the new directory
        val oldSdCardPath = hardwareConfig[ConfigKey.SDCARD_PATH]
        if (oldSdCardPath != null && oldSdCardPath.startsWith(oldAvdFolderPath)) {
          // The hardware config points to the old directory. Substitute the new
          // directory.
          hardwareConfig[ConfigKey.SDCARD_PATH] =
            oldSdCardPath.replace(oldAvdFolderPath, newAvdInfo.dataFolderPath.toString())
        }
      }

      setImagePathProperties(systemImage, configValues)

      // Tag and abi type
      val tag = systemImage.tag
      configValues[ConfigKey.TAG_ID] = tag.id
      configValues[ConfigKey.TAG_DISPLAY] = tag.display
      configValues[ConfigKey.TAG_IDS] = systemImage.tags.joinToString(",") { it.id }
      configValues[ConfigKey.TAG_DISPLAYNAMES] = systemImage.tags.joinToString(",") { it.display }
      configValues[ConfigKey.ABI_TYPE] = systemImage.primaryAbiType
      configValues[ConfigKey.PLAYSTORE_ENABLED] =
        (deviceHasPlayStore && systemImage.hasPlayStore()).toString()
      configValues[ConfigKey.ARC] = (SystemImageTags.CHROMEOS_TAG == tag).toString()

      // Add the hardware config to the config file. We copy values from the following
      // sources, in order, with later sources overriding earlier ones:
      // 1. The hardware.ini file supplied by the system image, if present
      // 2. The hardware.ini file supplied by the skin, if present
      // 3. The hardwareConfig argument (i.e. user-supplied settings)
      // 4. The system image CPU architecture
      addSystemImageHardwareConfig(systemImage, configValues)
      if (skin != null) {
        addSkin(skin, configValues)
      }
      configValues.putAll(hardwareConfig)
      addCpuArch(systemImage, configValues)

      // Store the Android version (target hash) in config.ini, not just the metadata ini
      configValues[ConfigKey.TARGET] = systemImage.androidVersion.getPlatformHashString()

      // We've done as much work as we can without writing to disk. Now start writing the
      // .ini files, creating the SD card (if necessary), copying userdata.img, etc. After
      // this point, we will delete the AVD if something goes wrong, since it will be in an
      // unknown state.
      iniFile = createAvdIniFile(avdName, avdFolder, removePrevious, systemImage.androidVersion)

      needCleanup = true

      createAvdUserdata(systemImage, avdFolder)

      if (sdcard != null) {
        configValues.putAll(sdcard.configEntries())
      }
      if (sdcard is InternalSdCard) {
        createAvdSdCard(sdcard, editExisting, avdFolder)
      }

      // Finally write configValues to config.ini
      writeIniFile(avdFolder.resolve(CONFIG_INI), configValues, true)

      if (userSettings != null) {
        try {
          writeIniFile(avdFolder.resolve(USER_SETTINGS_INI), userSettings, true)
        } catch (e: IOException) {
          log.warning(
            "Could not write user settings file (at %1\$s): %2\$s",
            avdFolder.resolve(USER_SETTINGS_INI).toString(),
            e,
          )
        }
      }

      if (bootProps != null && bootProps.isNotEmpty()) {
        val bootPropsFile: Path = avdFolder.resolve(BOOT_PROP)
        writeIniFile(bootPropsFile, bootProps, false)
      }

      val updatedEnvironment: Map<String, String>
      val environmentIniPath: Path = avdFolder.resolve(ENVIRONMENT_INI)
      if (environment != null && environment.isNotEmpty()) {
        updatedEnvironment = environment.toMutableMap()
        // The environment will contain either image or video, not both.
        copyEnvironment(updatedEnvironment, EnvironmentKey.IMAGE, avdFolder)
        copyEnvironment(updatedEnvironment, EnvironmentKey.VIDEO, avdFolder)
        writeIniFile(environmentIniPath, updatedEnvironment, false)
      } else {
        updatedEnvironment = mutableMapOf()
        environmentIniPath.deleteIfExists()
        deleteContentOf(avdFolder.resolve(ENVIRONMENT_DIR))
      }

      val oldAvdInfo = getAvd(avdName, false /*validAvdOnly*/)

      if (newAvdInfo == null) {
        newAvdInfo =
          createAvdInfoObject(
            systemImage,
            removePrevious,
            editExisting,
            iniFile,
            avdFolder,
            oldAvdInfo,
            configValues,
            userSettings ?: mutableMapOf(),
            updatedEnvironment,
          )
      }

      if (
        (removePrevious || editExisting) &&
          oldAvdInfo != null &&
          (oldAvdInfo.dataFolderPath != newAvdInfo.dataFolderPath)
      ) {
        log.warning("Removing previous AVD directory at %s", oldAvdInfo.dataFolderPath)
        // Remove the old data directory
        try {
          PathUtils.deleteRecursivelyIfExists(baseAvdFolder.resolve(oldAvdInfo.dataFolderPath))
        } catch (_: IOException) {
          log.warning("Failed to delete %1\$s: %2\$s", oldAvdInfo.dataFolderPath)
        }
      }

      needCleanup = false
      return newAvdInfo
    } catch (e: Exception) {
      when (e) {
        is SecurityException,
        is AndroidLocationsException,
        is IOException ->
          throw AvdManagerException("An error occurred while creating AVD: " + e.message, e)
        else -> throw e
      }
    } finally {
      if (needCleanup) {
        if (iniFile != null) {
          try {
            PathUtils.deleteRecursivelyIfExists(iniFile)
          } catch (_: IOException) {}
        }

        try {
          PathUtils.deleteRecursivelyIfExists(avdFolder)
        } catch (e: Exception) {
          log.warning("Failed to delete %1\$s: %2\$s", avdFolder.toAbsolutePath(), e)
        }
      }
    }
  }

  private fun validateEnvironment(environment: Map<String, String>?) {
    environment ?: return
    val keys = listOf(EnvironmentKey.IMAGE, EnvironmentKey.VIDEO)
    require(keys.count(environment::containsKey) <= 1) {
      "Expected at most one of ${keys.joinToString()}"
    }
  }

  /**
   * Copies a background file (image or video) to the AVD directory. Updates the environment to use
   * the path relative to the AVD directory.
   *
   * @param environment the environment configuration
   * @param key the EnvironmentKey to check and update
   * @param avdFolder the AVD's data folder
   * @throws AvdManagerException if the copy fails
   */
  @Throws(AvdManagerException::class)
  private fun copyEnvironment(
    environment: MutableMap<String, String>,
    key: String,
    avdFolder: Path,
  ) {
    val value = environment[key]
    if (value != null) {
      val source = avdFolder.fileSystem.getPath(value)
      if (source.isAbsolute) {
        // An absolute path indicates an environment file that should be copied to the AVD folder.
        val environmentDir = avdFolder.resolve(ENVIRONMENT_DIR)
        Files.createDirectories(environmentDir)
        deleteContentOf(environmentDir)
        val destination = environmentDir.resolve(source.fileName)
        try {
          if (source != destination) {
            FileUtils.copyFile(source, destination)
          }
          environment[key] = avdFolder.relativize(destination).toString()
        } catch (e: IOException) {
          throw AvdManagerException("Unable to copy background to AVD directory", e)
        }
      } else if (!Files.exists(avdFolder.resolve(source))) {
        // A relative path means that the environment should already be present.
        log.warning("$key $source not present in $avdFolder")
      }
    }
  }

  /** Checks if the given file is one of the files created at the AVD creation time. */
  fun isFoundationalAvdFile(file: Path, avd: AvdInfo): Boolean {
    val avdFolder = avd.dataFolderPath
    if (!file.startsWith(avdFolder)) {
      return false // Outside AVD directory.
    }
    val relative = avdFolder.relativize(file).toString()
    return relative == CONFIG_INI ||
      relative == SDCARD_IMG ||
      relative == USER_SETTINGS_INI ||
      relative == BOOT_PROP ||
      relative == ENVIRONMENT_DIR ||
      relative == ENVIRONMENT_INI ||
      relative == USERDATA_IMG ||
      relative == avd.environment[EnvironmentKey.IMAGE] ||
      relative == avd.environment[EnvironmentKey.VIDEO]
  }

  /**
   * Duplicates an existing AVD. Update the 'config.ini' and 'hardware-qemu.ini' files to reference
   * the new name and path.
   *
   * @param avdFolder the data folder of the AVD to be duplicated
   * @param newAvdName name of the new copy
   * @param systemImage system image that the AVD uses
   */
  @Throws(AvdManagerException::class)
  private fun duplicateAvd(
    avdFolder: Path,
    destAvdFolder: Path,
    newAvdName: String,
    systemImage: ISystemImage,
  ): AvdInfo {
    try {
      inhibitCopyOnWrite(destAvdFolder, log)

      val progInd: ProgressIndicator = ConsoleProgressIndicator()
      progInd.setText("Copying files")
      progInd.isIndeterminate = true
      FileOpUtils.recursiveCopy(
        avdFolder,
        destAvdFolder,
        false,
        { path -> !path.fileName.endsWith(".lock") }, // Do not copy *.lock files
        progInd,
      )

      // Modify the ID and display name in the new config.ini
      val configIni: Path = destAvdFolder.resolve(CONFIG_INI)
      var configVals = parseIniFile(PathFileWrapper(configIni), log) ?: mutableMapOf()
      val userSettingsVals = parseUserSettingsFile(destAvdFolder, log)
      val environment = parseEnvironmentFile(destAvdFolder, log)
      configVals[ConfigKey.AVD_ID] = newAvdName
      configVals[ConfigKey.DISPLAY_NAME] = newAvdName
      writeIniFile(configIni, configVals, true)

      // Update the AVD name and paths in the new copies of config.ini and hardware-qemu.ini
      val origAvdName = avdFolder.fileName.toString().replace(".avd", "")
      val origAvdFolder = avdFolder.toAbsolutePath().toString()
      val newAvdFolder = destAvdFolder.toAbsolutePath().toString()

      updateNameAndIniPaths(configIni, origAvdName, origAvdFolder, newAvdName, newAvdFolder)?.let {
        configVals = it
      }

      val hwQemu: Path = destAvdFolder.resolve(HARDWARE_QEMU_INI)
      updateNameAndIniPaths(hwQemu, origAvdName, origAvdFolder, newAvdName, newAvdFolder)

      // Create <AVD name>.ini
      val metadataIniFile =
        createAvdIniFile(newAvdName, destAvdFolder, false, systemImage.androidVersion)

      // Create an AVD object from these files
      return AvdInfo(
        iniFile = metadataIniFile,
        dataFolderPath = destAvdFolder,
        systemImage = systemImage,
        properties = configVals,
        userSettings = userSettingsVals,
        environment = environment,
        status = AvdStatus.OK,
      )
    } catch (e: AndroidLocationsException) {
      throw AvdManagerException("An error occurred while duplicating an AVD: " + e.message, e)
    } catch (e: IOException) {
      throw AvdManagerException("An error occurred while duplicating an AVD: " + e.message, e)
    }
  }

  /**
   * Modifies an ini file to switch values from an old AVD name and path to a new AVD name and path.
   * Values that are `oldName` are switched to `newName` Values that start with `oldPath` are
   * modified to start with `newPath`
   *
   * @return the updated ini settings
   */
  @Throws(IOException::class)
  private fun updateNameAndIniPaths(
    iniFile: Path,
    oldName: String,
    oldPath: String,
    newName: String,
    newPath: String,
  ): MutableMap<String, String>? {
    val iniVals: MutableMap<String, String>? = parseIniFile(PathFileWrapper(iniFile), log)
    if (iniVals != null) {
      for ((key, origIniValue) in iniVals) {
        if (origIniValue == oldName) {
          iniVals[key] = newName
        }
        if (origIniValue.startsWith(oldPath)) {
          val newIniValue = origIniValue.replace(oldPath, newPath)
          iniVals[key] = newIniValue
        }
      }
      writeIniFile(iniFile, iniVals, true)
    }
    return iniVals
  }

  /**
   * Returns the path to the target image's folder as a relative path to the SDK.
   *
   * @throws AvdManagerException if the image folder is empty or does not exist, does not contain a
   *   system image, or is not located within the current SDK.
   */
  @Throws(AvdManagerException::class)
  private fun getImageRelativePath(systemImage: ISystemImage): String {
    val folder = systemImage.location
    var imageFullPath = folder.toAbsolutePath().toString()

    // make this path relative to the SDK location
    val sdkLocation = sdkLocation.toAbsolutePath().toString()
    if (!imageFullPath.startsWith(sdkLocation)) {
      throw AvdManagerException("The selected system image ($imageFullPath) is not inside the SDK.")
    }

    val list: List<String>
    try {
      CancellableFileIo.list(folder).use { contents ->
        list =
          contents
            .map { it.fileName.toString() }
            .filter { IMAGE_NAME_PATTERN.matcher(it).matches() }
            .collect(toList())
      }
    } catch (e: IOException) {
      throw AvdManagerException("Unable to read contents of system image at $folder.", e)
    }
    if (list.isNotEmpty()) {
      // Remove the SDK root path, e.g. /sdk/dir1/dir2 -> /dir1/dir2
      imageFullPath = imageFullPath.substring(sdkLocation.length)
      // The path is relative, so it must not start with a file separator
      val separator = folder.fileSystem.separator
      if (imageFullPath.startsWith(separator)) {
        imageFullPath = imageFullPath.substring(separator.length)
      }
      // For compatibility with previous versions, we denote folders
      // by ending the path with file separator
      if (!imageFullPath.endsWith(separator)) {
        imageFullPath += separator
      }

      return imageFullPath
    }

    throw AvdManagerException("System image not found in $folder.")
  }

  /**
   * Creates the metadata ini file for an AVD.
   *
   * @param avdName the basename of the metadata ini file of the AVD.
   * @param avdFolder path for the data folder of the AVD.
   * @param removePrevious True if an existing ini file should be removed.
   * @throws AndroidLocationsException if there's a problem getting android root directory.
   * @throws IOException if [Files.delete] fails.
   */
  @Throws(AndroidLocationsException::class, IOException::class)
  private fun createAvdIniFile(
    avdName: String,
    avdFolder: Path,
    removePrevious: Boolean,
    version: AndroidVersion,
  ): Path {
    val iniFile = getDefaultIniFile(this, avdName)

    if (removePrevious) {
      if (CancellableFileIo.isRegularFile(iniFile)) {
        Files.delete(iniFile)
      } else if (CancellableFileIo.isDirectory(iniFile)) {
        try {
          PathUtils.deleteRecursivelyIfExists(iniFile)
        } catch (_: IOException) {}
      }
    }

    val absPath = avdFolder.toAbsolutePath().toString()
    var relPath: String? = null
    val androidFolder =
      sdkHandler.androidFolder
        ?: throw AndroidLocationsException(
          "Can't locate Android SDK installation directory for the AVD .ini file."
        )
    val androidPath = androidFolder.toAbsolutePath().toString() + File.separator
    if (absPath.startsWith(androidPath)) {
      // Compute the AVD path relative to the android path.
      relPath = absPath.substring(androidPath.length)
    }

    val values = mutableMapOf<String, String>()
    if (relPath != null) {
      values[MetadataKey.REL_PATH] = relPath
    }
    values[MetadataKey.ABS_PATH] = absPath
    values[MetadataKey.TARGET] = version.getPlatformHashString()
    writeIniFile(iniFile, values, true)

    return iniFile
  }

  /**
   * Creates the metadata ini file for an AVD.
   *
   * @param info of the AVD.
   * @throws AndroidLocationsException if there's a problem getting android root directory.
   * @throws IOException if [Files.delete] fails.
   */
  @Throws(AndroidLocationsException::class, IOException::class)
  private fun createAvdIniFile(info: AvdInfo): Path {
    return createAvdIniFile(
      info.name,
      baseAvdFolder.resolve(info.dataFolderPath),
      false,
      info.androidVersion,
    )
  }

  /**
   * Actually deletes the files of an existing AVD.
   *
   * This also remove it from the manager's list, The caller does not need to call [.removeAvd]
   * afterwards.
   *
   * This method is designed to somehow work with an unavailable AVD, that is an AVD that could not
   * be loaded due to some error. That means this method still tries to remove the AVD ini file or
   * its folder if it can be found. An error will be output if any of these operations fail.
   *
   * @param avdInfo the information on the AVD to delete
   * @return True if the AVD was deleted with no error.
   */
  @Slow
  fun deleteAvd(avdInfo: AvdInfo): Boolean {
    try {
      var error = false

      var f = avdInfo.iniFile
      try {
        Files.deleteIfExists(f)
      } catch (_: IOException) {
        log.warning("Failed to delete %1\$s", f)
        error = true
      }

      val path = avdInfo.dataFolderPath
      f = baseAvdFolder.resolve(path)
      try {
        PathUtils.deleteRecursivelyIfExists(f)
      } catch (exception: IOException) {
        log.warning("Failed to delete %1\$s", f)
        val writer = StringWriter()
        exception.printStackTrace(PrintWriter(writer))
        log.warning(writer.toString())
        error = true
      }

      removeAvd(avdInfo)

      if (error) {
        log.info("\nAVD '%1\$s' deleted with errors. See errors above.", avdInfo.name)
      } else {
        log.info("\nAVD '%1\$s' deleted.", avdInfo.name)
        return true
      }
    } catch (e: SecurityException) {
      log.warning("Failed to delete AVD: %1\$s", e)
    }
    return false
  }

  /**
   * Moves and/or renames an existing AVD and its files. This also change it in the manager's list.
   *
   * The caller should make sure the name or path given are valid, do not exist and are actually
   * different than current values.
   *
   * @param avdInfo the information on the AVD to move.
   * @param newAvdName the new name of the AVD if non-null.
   * @param newAvdFolder the new data folder if non null.
   * @throws AvdManagerException if moving the AVD fails
   */
  @Slow
  @Throws(AvdManagerException::class)
  fun moveAvd(avdInfo: AvdInfo, newAvdName: String?, newAvdFolder: Path?) {
    try {
      if (newAvdFolder != null) {
        val f = baseAvdFolder.resolve(avdInfo.dataFolderPath)
        log.info("Moving '%1\$s' to '%2\$s'.\n", avdInfo.dataFolderPath, newAvdFolder)
        try {
          Files.move(f, baseAvdFolder.resolve(newAvdFolder))
        } catch (e: IOException) {
          throw AvdManagerException(
            "Failed to move '${avdInfo.dataFolderPath}' to '$newAvdFolder'.",
            e,
          )
        }

        // update AVD info
        val info =
          AvdInfo(
            iniFile = avdInfo.iniFile,
            dataFolderPath = newAvdFolder,
            systemImage = avdInfo.systemImage,
            properties = avdInfo.properties,
            userSettings = avdInfo.userSettings,
            environment = avdInfo.environment,
            status = AvdStatus.OK,
          )
        replaceAvd(avdInfo, info)

        // update the ini file
        try {
          createAvdIniFile(info)
        } catch (e: IOException) {
          throw AvdManagerException("Failed to create '${avdInfo.iniFile}'.", e)
        }
      }

      if (newAvdName != null) {
        val oldMetadataIniFile = avdInfo.iniFile
        val newMetadataIniFile = getDefaultIniFile(this, newAvdName)

        log.warning("Moving '%1\$s' to '%2\$s'.", oldMetadataIniFile, newMetadataIniFile)
        try {
          Files.move(oldMetadataIniFile, newMetadataIniFile)
        } catch (exception: IOException) {
          throw AvdManagerException(
            "Failed to move '$oldMetadataIniFile' to '$newMetadataIniFile'.",
            exception,
          )
        }

        // update AVD info
        val info =
          AvdInfo(
            iniFile = avdInfo.iniFile,
            dataFolderPath = avdInfo.dataFolderPath,
            systemImage = avdInfo.systemImage,
            properties = avdInfo.properties,
            userSettings = avdInfo.userSettings,
            environment = avdInfo.environment,
            status = AvdStatus.OK,
          )
        replaceAvd(avdInfo, info)
      }

      log.info("AVD '%1\$s' moved.\n", avdInfo.name)
    } catch (e: AndroidLocationsException) {
      throw AvdManagerException(e.message, e)
    }
  }

  /**
   * Recursively deletes a folder's content (but not the folder itself).
   *
   * @throws SecurityException like [File.delete] does if file/folder is not writable.
   */
  @Throws(SecurityException::class)
  private fun deleteContentOf(folder: Path): Boolean {
    var success = true
    try {
      Files.newDirectoryStream(folder).use { entries ->
        for (entry in entries) {
          try {
            PathUtils.deleteRecursivelyIfExists(entry)
          } catch (_: IOException) {
            success = false
          }
        }
      }
    } catch (_: IOException) {
      return false
    }
    return success
  }

  /**
   * Returns a list of files that are potential AVD ini files.
   *
   * This lists the $HOME/.android/avd/<name>.ini files. Such files are properties file than then
   * indicate where the AVD folder is located.
   *
   * Note: the method is to be considered private. It is made protected so that unit tests can
   * easily override the AVD root.
   *
   * @return A new [Path] array or null. The array might be empty.
   * @throws AndroidLocationsException if there's a problem getting android root directory. </name>
   */
  @Throws(AndroidLocationsException::class)
  private fun buildAvdFilesList(): List<Path> {
    // ensure folder validity.
    if (CancellableFileIo.isRegularFile(this.baseAvdFolder)) {
      throw AndroidLocationsException(
        "${baseAvdFolder.toAbsolutePath()} is a regular file; expected a directory."
      )
    } else if (CancellableFileIo.notExists(this.baseAvdFolder)) {
      // folder is not there, we create it and return
      try {
        Files.createDirectories(this.baseAvdFolder)
      } catch (e: IOException) {
        throw AndroidLocationsException(
          "Unable to create AVD home directory: " + baseAvdFolder.toAbsolutePath(),
          e,
        )
      }
      return emptyList()
    }

    return try {
      CancellableFileIo.list(this.baseAvdFolder).use { contents ->
        contents
          .filter { path ->
            INI_NAME_PATTERN.matcher(path.fileName.toString()).matches() &&
              Files.isRegularFile(path)
          }
          .collect(toList())
      }
    } catch (_: IOException) {
      emptyList()
    }
  }

  /**
   * Computes the internal list of available AVDs
   *
   * @param allList the list to contain all the AVDs
   * @throws AndroidLocationsException if there's a problem getting android root directory.
   */
  @Throws(AndroidLocationsException::class)
  private fun buildAvdList(allList: MutableList<AvdInfo>) {
    val avds = buildAvdFilesList()
    for (avd in avds) {
      val info = parseAvdInfo(avd)
      if (!allList.contains(info)) {
        allList.add(info)
      }
    }
  }

  /**
   * Parses an AVD .ini file to create an [AvdInfo].
   *
   * @param metadataIniFile The path to the AVD .ini file
   * @return A new [AvdInfo] with an [AvdStatus] indicating whether this AVD is valid or not.
   */
  @VisibleForTesting
  @Slow
  fun parseAvdInfo(metadataIniFile: Path): AvdInfo {
    val metadata = parseIniFile(PathFileWrapper(metadataIniFile), log)

    var avdFolder: Path? = null
    if (metadata != null) {
      avdFolder = metadata[MetadataKey.ABS_PATH]?.let { metadataIniFile.resolve(it) }
      if (avdFolder == null || !CancellableFileIo.isDirectory(baseAvdFolder.resolve(avdFolder))) {
        // Try to fallback on the relative path, if present.
        val relPath = metadata[MetadataKey.REL_PATH]
        if (relPath != null) {
          val f = sdkHandler.androidFolder?.resolve(relPath) ?: sdkHandler.toCompatiblePath(relPath)
          if (CancellableFileIo.isDirectory(f)) {
            avdFolder = f
          }
        }
      }
    }
    if (avdFolder == null || !CancellableFileIo.isDirectory(baseAvdFolder.resolve(avdFolder))) {
      // Corrupted .ini file
      return AvdInfo(
        iniFile = metadataIniFile,
        dataFolderPath = metadataIniFile,
        systemImage = null,
        status = AvdStatus.ERROR_CORRUPTED_INI,
      )
    }

    val progress: LoggerProgressIndicatorWrapper =
      object : LoggerProgressIndicatorWrapper(log) {
        override fun logVerbose(s: String) {
          // Skip verbose messages
        }
      }

    // load the AVD properties.
    val configIniPath = baseAvdFolder.resolve(avdFolder).resolve(CONFIG_INI)
    val configIniFile = PathFileWrapper(configIniPath).takeIf { it.exists() }
    if (configIniFile == null) {
      log.warning("Missing file '%1\$s'.", configIniPath.toAbsolutePath().toString())
    }
    var properties: MutableMap<String, String>? = configIniFile?.let { parseIniFile(it, log) }

    // Check if the value of image.sysdir.1 is valid.
    val imageSysDir: String? = properties?.get(ConfigKey.IMAGES_1)
    val sysImage: ISystemImage? =
      imageSysDir?.let {
        sdkHandler.getSystemImageManager(progress).getImageAt(sdkLocation.resolve(it))
      }

    // Get the device status if this AVD is associated with a device
    var deviceStatus: DeviceManager.DeviceStatus? = null
    var updateHashV2 = false
    if (properties != null) {
      val deviceName = properties[ConfigKey.DEVICE_NAME]
      val deviceManufacturer = properties[ConfigKey.DEVICE_MANUFACTURER]
      if (deviceName != null && deviceManufacturer != null) {
        val device = deviceManager.getDevice(deviceName, deviceManufacturer)
        if (device == null) {
          deviceStatus = DeviceManager.DeviceStatus.MISSING
        } else {
          deviceStatus = DeviceManager.DeviceStatus.EXISTS

          val hashV2 = properties[ConfigKey.DEVICE_HASH_V2]
          if (hashV2 == null) {
            updateHashV2 = true
          } else {
            val newHashV2 = DeviceManager.hasHardwarePropHashChanged(device, hashV2)
            if (newHashV2 != null) {
              properties[ConfigKey.DEVICE_HASH_V2] = newHashV2
              updateHashV2 = true
            }
          }

          val hashV1 = properties[ConfigKey.DEVICE_HASH_V1]
          if (hashV1 != null) {
            // will recompute a hash v2 and save it below
            properties.remove(ConfigKey.DEVICE_HASH_V1)
          }
        }
      }
    }

    // TODO: What about missing sdcard, skins, etc?
    val status: AvdStatus =
      when {
        configIniFile == null -> AvdStatus.ERROR_CONFIG
        properties == null || imageSysDir == null -> AvdStatus.ERROR_PROPERTIES
        deviceStatus == DeviceManager.DeviceStatus.MISSING -> AvdStatus.ERROR_DEVICE_MISSING
        sysImage == null && !isDirectoryOutsideSdkDirectory(imageSysDir) -> {
          // SdkHandler is aware only of system images located under the SDK directory.
          AvdStatus.ERROR_IMAGE_MISSING
        }
        else -> AvdStatus.OK
      }

    if (properties == null) {
      properties = mutableMapOf()
    }

    // Copy the target to the properties
    if (!properties.containsKey(ConfigKey.TARGET)) {
      val target = metadata?.get(MetadataKey.TARGET)
      if (target != null) {
        properties[ConfigKey.TARGET] = target
      }
    }

    // Set the "tag.ids" property if it is not present but the "tag.id" property is.
    if (!properties.containsKey(ConfigKey.TAG_IDS)) {
      val tagId = properties[ConfigKey.TAG_ID]
      if (!tagId.isNullOrEmpty()) {
        properties[ConfigKey.TAG_IDS] = tagId
      }
    }

    // Set the "display.name" property if it is not present.
    // This can happen when the emulator has been created using the avdmanager cli tool
    if (!properties.containsKey(ConfigKey.DISPLAY_NAME)) {
      val avdName = getAvdNameFromFile(metadataIniFile)
      val displayName = avdNameToDisplayName(avdName)
      properties[ConfigKey.DISPLAY_NAME] = displayName
    }

    val userSettings = parseUserSettingsFile(avdFolder, log)
    val environment = parseEnvironmentFile(avdFolder, log)
    val info =
      AvdInfo(metadataIniFile, avdFolder, sysImage, properties, userSettings, environment, status)

    if (updateHashV2) {
      try {
        return updateDeviceChanged(info) ?: info
      } catch (_: IOException) {}
    }

    return info
  }

  private fun isDirectoryOutsideSdkDirectory(imageSysDir: String): Boolean {
    val dir = Paths.get(imageSysDir)
    if (!dir.isAbsolute) {
      return false
    }
    return !dir.startsWith(sdkLocation) && Files.isDirectory(dir)
  }

  /**
   * Removes an [AvdInfo] from the internal list.
   *
   * @param avdInfo The [AvdInfo] to remove.
   * @return true if this [AvdInfo] was present and has been removed.
   */
  fun removeAvd(avdInfo: AvdInfo): Boolean {
    synchronized(allAvdList) {
      if (allAvdList.remove(avdInfo)) {
        validAvdList = null
        return true
      }
    }

    return false
  }

  @Slow
  @Throws(IOException::class)
  fun updateAvd(avd: AvdInfo, newProperties: Map<String, String>): AvdInfo {
    // now write the config file
    writeIniFile(baseAvdFolder.resolve(avd.dataFolderPath).resolve(CONFIG_INI), newProperties, true)

    // finally create a new AvdInfo for this unbroken avd and add it to the list.
    // instead of creating the AvdInfo object directly we reparse it, to detect other possible
    // errors
    // FIXME: We may want to create this AvdInfo by reparsing the AVD instead. This could detect
    // other errors.
    val newAvd = avd.copy(properties = newProperties)

    replaceAvd(avd, newAvd)

    return newAvd
  }

  /**
   * Updates the device-specific part of an AVD ini.
   *
   * @param avd the AVD to update.
   * @return The new AVD on success.
   */
  @Slow
  @Throws(IOException::class)
  fun updateDeviceChanged(avd: AvdInfo): AvdInfo? {
    // Overwrite the properties derived from the device and nothing else
    val properties: MutableMap<String, String> = avd.properties.toMutableMap()

    val d = deviceManager.getDevice(avd)
    if (d == null) {
      log.warning("Base device information incomplete or missing.")
      return null
    }

    // The device has a RAM size, but we don't want to use it.
    // Instead, we'll keep the AVD's existing RAM size setting.
    val deviceHwProperties = DeviceManager.getHardwareProperties(d)
    deviceHwProperties.remove(ConfigKey.RAM_SIZE)
    properties.putAll(deviceHwProperties)
    try {
      return updateAvd(avd, properties)
    } catch (e: IOException) {
      log.warning("Failed to update AVD device profile: %1\$s", e)
      return null
    }
  }

  /**
   * Sets the paths to the system images in a properties map.
   *
   * @param image the system image for this avd.
   * @param properties the properties in which to set the paths.
   * @throws AvdManagerException if the system image cannot be found
   */
  @Throws(AvdManagerException::class)
  private fun setImagePathProperties(image: ISystemImage, properties: MutableMap<String, String>) {
    properties[ConfigKey.IMAGES_1] = getImageRelativePath(image)
    properties.remove(ConfigKey.IMAGES_2)
  }

  /**
   * Replaces an old [AvdInfo] with a new one in the lists storing them.
   *
   * @param oldAvd the [AvdInfo] to remove.
   * @param newAvd the [AvdInfo] to add.
   */
  private fun replaceAvd(oldAvd: AvdInfo, newAvd: AvdInfo?) {
    synchronized(allAvdList) {
      allAvdList.remove(oldAvd)
      allAvdList.add(newAvd!!)
      validAvdList = null
    }
  }

  /**
   * For old system images, copies userdata.img from the system image to the AVD. Does nothing for
   * new system images which contain a "data" folder.
   */
  @Throws(IOException::class, AvdManagerException::class)
  private fun createAvdUserdata(systemImage: ISystemImage, avdFolder: Path) {
    // Copy userdata.img from system-images to the *.avd directory
    val imageFolder = systemImage.location
    val userdataSrc: Path = imageFolder.resolve(USERDATA_IMG)

    if (CancellableFileIo.notExists(userdataSrc)) {
      if (CancellableFileIo.isDirectory(imageFolder.resolve(DATA_FOLDER))) {
        // Because this image includes a data folder, a
        // userdata.img file is not needed. Don't signal
        // an error.
        // (The emulator will access the 'data' folder directly;
        //  we do not need to copy it over.)
        return
      }

      throw AvdManagerException(
        "Unable to find a '$USERDATA_IMG' file for ABI ${systemImage.primaryAbiType} to copy into the AVD folder."
      )
    }

    val userdataDest: Path = avdFolder.resolve(USERDATA_IMG)

    if (CancellableFileIo.notExists(userdataDest)) {
      FileUtils.copyFile(userdataSrc, userdataDest)

      if (CancellableFileIo.notExists(userdataDest)) {
        throw AvdManagerException("Unable to create '$userdataDest' file in the AVD folder.")
      }
    }
  }

  /**
   * Add the CPU architecture of the system image to the AVD configuration.
   *
   * @param systemImage the system image of the AVD
   * @param values settings for the AVD
   */
  @Throws(AvdManagerException::class)
  private fun addCpuArch(systemImage: ISystemImage, values: MutableMap<String, String>) {
    val abiType = systemImage.primaryAbiType
    val abi =
      Abi.getEnum(abiType)
        ?: throw AvdManagerException(
          "ABI $abiType is not supported by this version of the SDK Tools"
        )

    var arch = abi.cpuArch
    // Chrome OS image is a special case: the system image
    // is actually x86_64 while the android container inside
    // it is x86. We have to set it x86_64 to let it boot
    // under android emulator.
    if (
      arch == SdkConstants.CPU_ARCH_INTEL_ATOM && SystemImageTags.CHROMEOS_TAG == systemImage.tag
    ) {
      arch = SdkConstants.CPU_ARCH_INTEL_ATOM64
    }
    values[ConfigKey.CPU_ARCH] = arch
    values[ConfigKey.CPU_ARCH] =
      when {
        arch == SdkConstants.CPU_ARCH_INTEL_ATOM &&
          SystemImageTags.CHROMEOS_TAG == systemImage.tag -> SdkConstants.CPU_ARCH_INTEL_ATOM64
        else -> arch
      }

    abi.cpuModel?.let { values[ConfigKey.CPU_MODEL] = it }
  }

  /** Adds parameters for the given skin to the AVD config. */
  @Throws(AvdManagerException::class)
  private fun addSkin(skin: Skin, values: MutableMap<String, String>) {
    val skinName = skin.name
    val skinPath: String

    when (skin) {
      is OnDiskSkin -> {
        val path = skin.path
        if (CancellableFileIo.notExists(path)) {
          throw AvdManagerException("Skin '$skinName' does not exist at $path.")
        }

        skinPath = path.toString()

        // If the skin contains a hardware.ini, add its contents to the AVD config.
        val skinHardwareFile = PathFileWrapper(path.resolve(HARDWARE_INI))
        if (skinHardwareFile.exists()) {
          val skinHardwareConfig = ProjectProperties.parsePropertyFile(skinHardwareFile, log)

          if (skinHardwareConfig != null) {
            values.putAll(skinHardwareConfig)
          }
        }
      }
      is GenericSkin -> skinPath = skinName
    }

    // Set skin.name for display purposes in the AVD manager and
    // set skin.path for use by the emulator.
    values[ConfigKey.SKIN_NAME] = skinName
    values[ConfigKey.SKIN_PATH] = skinPath
  }

  /**
   * Creates an SD card for the AVD. Any existing card will be replaced with a new one, unless the
   * card is already the right size and editExisting is set.
   *
   * @param sdcard the spec of the card to create
   * @param editExisting true if modifying an existing AVD
   * @param avdFolder where the AVDs live
   */
  @Throws(AvdManagerException::class)
  private fun createAvdSdCard(sdcard: InternalSdCard, editExisting: Boolean, avdFolder: Path) {
    if (baseAvdFolder.fileSystem != FileSystems.getDefault()) {
      // We don't have a real filesystem, so we won't be able to run the tool. Skip.
      return
    }

    val sdcardFile: Path = avdFolder.resolve(SDCARD_IMG)
    try {
      if (CancellableFileIo.size(sdcardFile) == sdcard.size && editExisting) {
        // There's already an sdcard file with the right size and we're
        // not overriding it... so don't remove it.
        log.info("SD Card already present with same size, was not changed.\n")
        return
      }
    } catch (_: NoSuchFileException) {} catch (exception: IOException) {
      throw AvdManagerException("An error occurred reading the SD card image.", exception)
    }

    val path = sdcardFile.toAbsolutePath().toString()

    // execute mksdcard with the proper parameters.
    val progress: LoggerProgressIndicatorWrapper =
      object : LoggerProgressIndicatorWrapper(log) {
        override fun logVerbose(s: String) {
          // Skip verbose messages
        }
      }
    val p =
      sdkHandler.getEmulatorPackage(progress)
        ?: throw AvdManagerException("The Android Emulator is not installed.")

    val mkSdCard =
      p.mkSdCardBinary
        ?: throw AvdManagerException(
          "Unable to find \"${SdkConstants.mkSdCardCmdName()}\" in the Android Emulator at \"${p.location}\"."
        )

    if (!createSdCard(log, mkSdCard.toAbsolutePath().toString(), sdcard.sizeSpec(), path)) {
      // mksdcard's error output has been written to the log.
      throw AvdManagerException("Failed to create SD card in the AVD folder.")
    }
  }

  /**
   * Read the system image's hardware.ini into the provided Map.
   *
   * @param systemImage the system image of the AVD
   * @param values mutable Map to add the values to
   */
  private fun addSystemImageHardwareConfig(
    systemImage: ISystemImage,
    values: MutableMap<String, String>,
  ) {
    val sysImgHardwareFile = PathFileWrapper(systemImage.location.resolve(HARDWARE_INI))
    if (sysImgHardwareFile.exists()) {
      ProjectProperties.parsePropertyFile(sysImgHardwareFile, log)?.let { values.putAll(it) }
    }
  }

  /**
   * Creates an AvdInfo object from the new AVD.
   *
   * @param systemImage the system image of the AVD
   * @param removePrevious true if the existing AVD should be deleted
   * @param editExisting true if modifying an existing AVD
   * @param metadataIniFile the .ini file of this AVD
   * @param avdFolder where the AVD resides
   * @param oldAvdInfo configuration of the old AVD
   * @param values a map of the AVD's info
   */
  private fun createAvdInfoObject(
    systemImage: ISystemImage,
    removePrevious: Boolean,
    editExisting: Boolean,
    metadataIniFile: Path,
    avdFolder: Path,
    oldAvdInfo: AvdInfo?,
    values: Map<String, String>,
    userSettings: Map<String, String>,
    environment: Map<String, String>,
  ): AvdInfo {
    // create the AvdInfo object, and add it to the list

    val theAvdInfo =
      AvdInfo(
        iniFile = metadataIniFile,
        dataFolderPath = avdFolder,
        systemImage = systemImage,
        properties = values,
        userSettings = userSettings,
        environment = environment,
        status = AvdStatus.OK,
      )

    synchronized(allAvdList) {
      if (oldAvdInfo != null && (removePrevious || editExisting)) {
        allAvdList.remove(oldAvdInfo)
      }
      allAvdList.add(theAvdInfo)
      validAvdList = null
    }
    return theAvdInfo
  }

  companion object {
    private val INI_LINE_PATTERN: Pattern = Pattern.compile("^([a-zA-Z0-9._-]+)\\s*=\\s*(.*)\\s*$")

    const val AVD_FOLDER_EXTENSION: String = ".avd"

    /** Pattern to match pixel-sized skin "names", e.g. "320x480". */
    val NUMERIC_SKIN_SIZE: Pattern = Pattern.compile("([0-9]{2,})x([0-9]{2,})")

    const val DATA_FOLDER: String = "data"
    const val USERDATA_IMG: String = "userdata.img"
    const val USERDATA_QEMU_IMG: String = "userdata-qemu.img"
    const val SNAPSHOTS_DIRECTORY: String = "snapshots"
    const val USER_SETTINGS_INI: String = "user-settings.ini" // $NON-NLS-1$

    private const val BOOT_PROP = "boot.prop"
    const val ENVIRONMENT_DIR = "environment"
    const val ENVIRONMENT_INI = "environment.ini"
    const val CONFIG_INI: String = "config.ini"
    private const val HARDWARE_QEMU_INI = "hardware-qemu.ini"
    private const val SDCARD_IMG = "sdcard.img"

    const val INI_EXTENSION: String = ".ini"
    private val INI_NAME_PATTERN: Pattern = Pattern.compile("(.+)\\.ini$", Pattern.CASE_INSENSITIVE)

    private val IMAGE_NAME_PATTERN: Pattern =
      Pattern.compile("(.+)\\.img$", Pattern.CASE_INSENSITIVE)

    const val HARDWARE_INI: String = "hardware.ini"

    @JvmStatic
    fun createInstance(
      sdkHandler: AndroidSdkHandler,
      baseAvdFolder: Path,
      deviceManager: DeviceManager,
      log: ILogger,
    ): AvdManager {
      return AvdManager(sdkHandler, baseAvdFolder, deviceManager, log)
    }

    fun parseEnvironmentFile(dataFolder: Path, logger: ILogger?): Map<String, String> {
      val environmentPath = PathFileWrapper(dataFolder.resolve(ENVIRONMENT_INI))
      if (environmentPath.exists()) {
        // We always write this in UTF-8.
        parseIniFileImpl(environmentPath, logger, Charsets.UTF_8)?.let {
          return it
        }
      }
      return mutableMapOf()
    }

    /**
     * Parses a property file and returns a map of the content.
     *
     * If the file is not present, null is returned with no error messages sent to the log.
     *
     * Charset encoding will be either the system's default or the one specified by the
     * [ConfigKey.ENCODING] key if present.
     *
     * @param propFile the property file to parse
     * @param logger the ILogger object receiving warning/error from the parsing.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    @JvmStatic
    @Slow
    fun parseIniFile(propFile: IAbstractFile, logger: ILogger?): MutableMap<String, String>? {
      return parseIniFileImpl(propFile, logger, null)
    }

    /**
     * Implementation helper for the [.parseIniFile] method. Don't call this one directly.
     *
     * @param propFile the property file to parse
     * @param log the ILogger object receiving warning/error from the parsing.
     * @param charset When a specific charset is specified, this will be used as-is. When null, the
     *   default charset will first be used and if the key [ConfigKey.ENCODING] is found the parsing
     *   will restart using that specific charset.
     * @return the map of (key,value) pairs, or null if the parsing failed.
     */
    private fun parseIniFileImpl(
      propFile: IAbstractFile,
      log: ILogger?,
      charset: Charset?,
    ): MutableMap<String, String>? {
      var currentCharset = charset
      try {
        val canChangeCharset = currentCharset == null
        if (currentCharset == null) {
          currentCharset = StandardCharsets.ISO_8859_1
        }
        BufferedReader(InputStreamReader(propFile.contents, currentCharset)).use { reader ->
          val map = mutableMapOf<String, String>()
          reader.useLines { lines ->
            for (line in lines) {
              val trimmedLine = line.trim()
              if (trimmedLine.isNotEmpty() && trimmedLine[0] != '#') {
                val m: Matcher = INI_LINE_PATTERN.matcher(trimmedLine)
                if (m.matches()) {
                  // Note: we do NOT escape values.
                  val key = m.group(1)
                  val value = m.group(2)

                  // If we find the charset encoding and it's not the same one and
                  // it's a valid one, re-read the file using that charset.
                  if (
                    canChangeCharset &&
                      ConfigKey.ENCODING == key &&
                      currentCharset.name() != value &&
                      Charset.isSupported(value)
                  ) {
                    return parseIniFileImpl(propFile, log, Charset.forName(value))
                  }

                  map[key] = value
                } else {
                  log?.warning(
                    "Error parsing '%1\$s': \"%2\$s\" is not a valid syntax",
                    propFile.osLocation,
                    trimmedLine,
                  )
                  return null
                }
              }
            }
          }
          return map
        }
      } catch (e: Exception) {
        when (e) {
          is FileNotFoundException -> {
            // this should not happen since we usually test the file existence before
            // calling the method. Return null below.
          }
          is IOException,
          is StreamException -> {
            log?.warning("Error parsing '%1\$s': %2\$s.", propFile.osLocation, e.message)
          }
          else -> throw e
        }
      }

      return null
    }

    /**
     * (Linux only) Sets the AVD folder to not be "Copy on Write"
     *
     * CoW at the file level conflicts with QEMU's explicit CoW operations and can hurt Emulator
     * performance. NOTE: The "chatter +C" command does not impact existing files in the folder.
     * Thus this method should be called before the folder is populated. This method is "best
     * effort." Common failures are silently ignored. Other failures are logged and ignored.
     *
     * @param avdFolder where the AVD's files will be written
     * @param log the log object to receive action logs
     */
    private fun inhibitCopyOnWrite(avdFolder: Path, log: ILogger) {
      if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_LINUX) {
        return
      }
      try {
        val chattrProcess =
          Runtime.getRuntime().exec(arrayOf("chattr", "+C", avdFolder.toAbsolutePath().toString()))

        val errorOutput = mutableListOf<String>()

        GrabProcessOutput.grabProcessOutput(
          chattrProcess,
          GrabProcessOutput.Wait.WAIT_FOR_READERS,
          object : IProcessOutput {
            override fun out(line: String?) {}

            override fun err(line: String?) {
              // Don't complain if this command is not supported. That just means
              // that the file system is not 'btrfs', and it does not support Copy
              // on Write. So we're happy.
              if (line != null && !line.startsWith("chattr: Operation not supported")) {
                errorOutput.add(line)
              }
            }
          },
          null,
          null,
        )

        if (errorOutput.isNotEmpty()) {
          log.warning("Failed 'chattr' for %1\$s:", avdFolder.toAbsolutePath().toString())
          for (error in errorOutput) {
            log.warning(" -- %1\$s", error)
          }
        }
      } catch (e: InterruptedException) {
        log.warning("Failed 'chattr' for %1\$s: %2\$s", avdFolder.toAbsolutePath().toString(), e)
      } catch (e: TimeoutException) {
        log.warning("Failed 'chattr' for %1\$s: %2\$s", avdFolder.toAbsolutePath().toString(), e)
      } catch (e: IOException) {
        log.warning("Failed 'chattr' for %1\$s: %2\$s", avdFolder.toAbsolutePath().toString(), e)
      }
    }
  }
}

/**
 * Writes a .ini file from a set of properties, using UTF-8 encoding. The keys are sorted. The file
 * should be read back later by [.parseIniFile].
 *
 * @param iniFile The file to generate.
 * @param values The properties to place in the ini file. If a value is null, the key will be
 *   omitted.
 * @param addEncoding When true, add a property [ConfigKey.ENCODING] indicating the encoding used to
 *   write the file.
 * @throws IOException if [FileWriter] fails to open, write or close the file.
 */
@Throws(IOException::class)
internal fun writeIniFile(iniFile: Path, values: Map<String, String?>, addEncoding: Boolean) {
  val charset = StandardCharsets.UTF_8
  OutputStreamWriter(Files.newOutputStream(iniFile), charset).use { writer ->
    val finalValues =
      if (addEncoding) {
        // Write down the charset we're using in case we want to use it later.
        values + (ConfigKey.ENCODING to charset.name())
      } else {
        values
      }

    for (key in finalValues.keys.sorted()) {
      val value = finalValues[key]
      if (value != null) {
        writer.write("$key=$value\n")
      }
    }
  }
}
