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

import com.android.SdkConstants
import com.android.prefs.AndroidLocationsProvider
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.HardwareProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.ILogger
import com.android.utils.StdLogger
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Path
import kotlin.math.min

private const val MAX_SYSTEM_IMAGE_RETRIES = 4
private const val BASE_RETRY_DELAY_SECONDS = 2L
private const val MAX_RETRY_DELAY_SECONDS = 10L

private val avdLocks: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

/**
 * Manages AVDs for the Avd build service inside the Android Gradle Plugin.
 */
class AvdManager(
    private val avdFolder: File,
    private val versionedSdkLoader: Provider<SdkComponentsBuildService.VersionedSdkLoader>,
    private val sdkHandler: AndroidSdkHandler,
    private val androidLocationsProvider: AndroidLocationsProvider,
    private val snapshotHandler: AvdSnapshotHandler
) {

    private val sdkDirectory: File
    get() = versionedSdkLoader.get().sdkDirectoryProvider.get().asFile

    private val logger: ILogger = LoggerWrapper.getLogger(AvdManager::class.java)

    private val avdManager: com.android.sdklib.internal.avd.AvdManager by lazy {
        com.android.sdklib.internal.avd.AvdManager.getInstance(
            sdkHandler,
            sdkHandler.toCompatiblePath(avdFolder),
            logger
        ) ?: throw RuntimeException("Failed to initialize AvdManager.")
    }

    private val deviceManager: DeviceManager by lazy {
        DeviceManager.createInstance(androidLocationsProvider, sdkDirectory.toPath(), logger)
    }

    fun createOrRetrieveAvd(
        imageProvider: Provider<Directory>,
        imageHash: String,
        deviceName: String,
        hardwareProfile: String
    ): File {
        val lock = avdLocks.computeIfAbsent(deviceName) {
            Any()
        }
        synchronized(lock) {
            avdManager.reloadAvds(logger)
            val info = avdManager.getAvd(deviceName, false)
            info?.let {
                logger.info("Device: $deviceName already exists. AVD creation skipped.")
                // already generated the avd
                return info.configFile.toFile()
            }

            val newInfo = createAvd(imageProvider, imageHash, deviceName, hardwareProfile)
            return newInfo?.configFile?.toFile() ?: error("AVD could not be created.")
        }
    }

    internal fun createAvd(
            imageProvider: Provider<Directory>,
            imageHash: String, deviceName: String,
            hardwareProfile: String
    ): AvdInfo? {
        if (!imageProvider.isPresent) {
            throw RuntimeException("Failed to find system image for hash: $imageHash")
        }

        val imageLocation = sdkHandler.toCompatiblePath(imageProvider.get().asFile)
        val systemImage = retrieveSystemImage(sdkHandler, imageLocation)
        systemImage ?: error("System image does not exist at $imageLocation")

        val device = deviceManager.getDevices(DeviceManager.ALL_DEVICES).find {
            it.displayName == hardwareProfile
        } ?: error("Failed to find hardware profile for name: $hardwareProfile")

        val hardwareConfig = defaultHardwareConfig()
        hardwareConfig.putAll(DeviceManager.getHardwareProperties(device))
        EmulatedProperties.restrictDefaultRamSize(hardwareConfig)

        val deviceFolder =
                AvdInfo.getDefaultAvdFolder(avdManager,
                        deviceName,
                        false)

        return avdManager.createAvd(
                deviceFolder,
                deviceName,
                systemImage,
                null,
                null,
                null,
                hardwareConfig,
                device.bootProps,
                device.hasPlayStore(),
                false,
                false,
                logger
        )
    }

    fun loadSnapshotIfNeeded(deviceName: String) {
        val lock = avdLocks.computeIfAbsent(deviceName) {
            Any()
        }
        synchronized(lock) {
            val emulatorProvider = versionedSdkLoader.get().emulatorDirectoryProvider
            val emulatorExecutable = snapshotHandler.getEmulatorExecutable(emulatorProvider)

            if (snapshotHandler.checkSnapshotLoadable(
                    deviceName,
                    emulatorExecutable,
                    avdFolder,
                    logger
                )
            ) {
                logger.verbose("Snapshot already exists for device $deviceName")
                return
            }

            val adbExecutable = versionedSdkLoader.get().adbExecutableProvider.get().asFile

            logger.verbose("Creating snapshot for $deviceName")
            snapshotHandler.generateSnapshot(
                deviceName,
                emulatorExecutable,
                adbExecutable,
                avdFolder,
                logger
            )

            if (snapshotHandler.checkSnapshotLoadable(
                    deviceName,
                    emulatorExecutable,
                    avdFolder,
                    logger
                )
            ) {
                logger.verbose("Verified snapshot created for: $deviceName.")
            }  else {
                error("""
                    Snapshot setup ran successfully, but the snapshot failed to be created. This is
                    likely to a lack of disk space for the snapshot. Try the cleanManagedDevices
                    task with the --unused-only flag to remove any unused devices for this project.
                """.trimIndent())
            }
        }
    }

    /**
     * Returns the names of all avds currently in the shared avd folder.
     */
    fun allAvds(): List<String> {
        avdManager.reloadAvds(logger)
        return avdManager.allAvds.map {
            it.name
        }
    }

    /**
     * Removes all the specified avds.
     *
     * This will delete the specified avds from the shared avd folder and update the avd cache.
     *
     * @param avds names of the avds to be deleted.
     */
    fun deleteAvds(avds: List<String>) {
        avdManager.reloadAvds(logger)
        for(avdName in avds) {
            val avdInfo = avdManager.getAvd(avdName, false)
            if (avdInfo != null) {
                avdManager.deleteAvd(avdInfo, logger)
            } else {
                logger.warning("Failed to delete avd: $avdName.")
            }
        }
    }

    private fun defaultHardwareConfig(): MutableMap<String, String> {
        // Get the defaults of all the user-modifiable properties.
        val emulatorProvider = versionedSdkLoader.get().emulatorDirectoryProvider

        val emulatorLib = if (emulatorProvider.isPresent) {
            emulatorProvider.get().asFile
        } else {
            error(
                "AVD Emulator package is not downloaded. Failed to retrieve hardware defaults" +
                        " for virtual device."
            )
        }

        val libDirectory = File(emulatorLib, SdkConstants.FD_LIB)
        val hardwareDefs = File(libDirectory, SdkConstants.FN_HARDWARE_INI)
        val hwMap =
            HardwareProperties.parseHardwareDefinitions(
                PathFileWrapper(sdkHandler.toCompatiblePath(hardwareDefs)), logger)?:
                    error("Failed to find hardware definitions for emulator.")

        val hwConfigMap = defaultEmulatorPropertiesMap.toMutableMap()

        hwMap.values.forEach {
            val default = it.default
            if (!default.isNullOrEmpty()) {
                hwConfigMap[it.name] = default
            }
        }

        return hwConfigMap
    }

    /**
     * Retrieves the system image from the system image manager.
     *
     * This attempts to retrieve the system image from the file system. The retries are required to
     * mitigate an issue on Windows where the download of the system image has occurred, but the
     * file system has yet to be updated.
     */
    private fun retrieveSystemImage(
        sdkHandler: AndroidSdkHandler,
        imageLocation: Path
    ): com.android.sdklib.ISystemImage? {
        var delay = BASE_RETRY_DELAY_SECONDS
        for (retry in 0..MAX_SYSTEM_IMAGE_RETRIES) {
            val systemImage = sdkHandler.getSystemImageManager(
                LoggerProgressIndicatorWrapper(StdLogger(StdLogger.Level.VERBOSE))
            ).getImageAt(imageLocation)

            if (systemImage != null) {
                return systemImage
            }

            if (retry != MAX_SYSTEM_IMAGE_RETRIES) {
                logger.warning("Failed to to retrieve system image at: $imageLocation " +
                        "Retrying in $delay seconds")
                Thread.sleep(delay * 1000)
                delay = min(delay * BASE_RETRY_DELAY_SECONDS, MAX_RETRY_DELAY_SECONDS)
            }
        }
        return null
    }

    // TODO(b/166641485): Move to a utilites class. Map is pulled from AvdManagerCli
    private val defaultEmulatorPropertiesMap: Map<String, String> =
        mapOf(
            EmulatedProperties.BACK_CAMERA_KEY to AvdCamera.EMULATED.asParameter,
            EmulatedProperties.CPU_CORES_KEY
                    to EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES.toString(),
            EmulatedProperties.CUSTOM_SKIN_FILE_KEY to "_no_skin",
            EmulatedProperties.DEVICE_FRAME_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.FRONT_CAMERA_KEY to AvdCamera.EMULATED.asParameter,
            EmulatedProperties.HAS_HARDWARE_KEYBOARD_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.HOST_GPU_MODE_KEY to GpuMode.AUTO.gpuSetting,
            HardwareProperties.HW_INITIAL_ORIENTATION to "Portrait",
            EmulatedProperties.INTERNAL_STORAGE_KEY
                    to EmulatedProperties.DEFAULT_INTERNAL_STORAGE.toString(),
            EmulatedProperties.NETWORK_LATENCY_KEY to "None",
            EmulatedProperties.NETWORK_SPEED_KEY to "Full",
            EmulatedProperties.SDCARD_SIZE to EmulatedProperties.DEFAULT_SDCARD_SIZE.toString(),
            EmulatedProperties.USE_CHOSEN_SNAPSHOT_BOOT to HardwareProperties.BOOLEAN_NO,
            EmulatedProperties.USE_COLD_BOOT to HardwareProperties.BOOLEAN_NO,
            EmulatedProperties.USE_FAST_BOOT to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.USE_HOST_GPU_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.VM_HEAP_STORAGE_KEY to EmulatedProperties.DEFAULT_HEAP.toString())
}
