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
package com.android.sdklib.internal.avd

import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Storage
import java.nio.file.Path
import kotlin.io.path.name

/**
 * AvdBuilder is a mutable class for reading and editing the definition of an AVD.
 *
 * An AVD is written to disk as a set of config files, which consist of key / value string pairs.
 * Part of the role of this class is to handle the conversion of those strings to and from
 * higher-level types stored in fields.
 *
 * Some fields are translated directly into a single config key/value entry. This is the easy case.
 * Some are translated into several key/value entries. The most troublesome, however, are those
 * which can become an arbitrary number of entries: Device expands into many entries determined by
 * its XML definition as well as code, and a system image or skin may import arbitrary entries from
 * a file stored on disk.
 *
 * Given this, when reading an AVD from disk, it is difficult to know whether a config entry
 * originated from a Device, system image, skin, or explicit user setting, particularly if the
 * config was made by a different version of Studio.
 *
 * Thus, we do not read config properties from disk other than the ones explicitly defined here. If
 * the Device, skin, or system image is changed, we want to be sure that we are not including
 * properties defined by the previous device. This also means that if a newer version of Studio
 * creates a device with new config entries, editing it will not preserve those properties.
 *
 * User settings and boot props are simpler than the config.ini case: boot.props are determined
 * entirely by the Device, and user settings are independent of other fields.
 */
class AvdBuilder(var metadataIniPath: Path, var avdFolder: Path, var device: Device) {
  var avdName: String
    get() = metadataIniPath.name.removeSuffix(".ini")
    set(name) {
      metadataIniPath = metadataIniPath.resolveSibling(name + ".ini")
    }

  var displayName: String = ""

  var systemImage: ISystemImage? = null

  var sdCard: SdCard? = null
  var skin: Skin? = null

  var showDeviceFrame = true
  var screenOrientation: ScreenOrientation = ScreenOrientation.PORTRAIT

  var cpuCoreCount: Int = 1
  var ram: Storage = Storage(0)
  var vmHeap: Storage = Storage(0)
  var internalStorage: Storage = Storage(0)

  var frontCamera: AvdCamera = AvdCamera.NONE
  var backCamera: AvdCamera = AvdCamera.NONE

  var gpuMode: GpuMode = GpuMode.OFF

  var enableKeyboard: Boolean = true

  var networkLatency = AvdNetworkLatency.NONE
  var networkSpeed = AvdNetworkSpeed.FULL

  var bootMode: BootMode = QuickBoot

  val userSettings = mutableMapOf<String, String>()

  val androidVersion: AndroidVersion?
    get() = systemImage?.androidVersion

  /** Sets the properties of this AvdBuilder based on its Device. */
  fun initializeFromDevice() {
    // Read the default values for the properties that we can edit.
    binding.read(this, DeviceManager.getHardwareProperties(device))

    // Default to a skin based on the device screen
    skin =
      device.getScreenSize(device.defaultState.orientation)?.let {
        GenericSkin(it.width, it.height)
      }

    ram = EmulatedProperties.defaultRamSize(device)
    vmHeap = EmulatedProperties.defaultVmHeapSize(device)
    internalStorage = EmulatedProperties.defaultInternalStorage(device)
  }

  /** Returns a Map representing the contents of config.ini. */
  fun configProperties(): Map<String, String> {
    val properties = mutableMapOf<String, String>()
    properties.putAll(defaultConfigKeys)
    properties.putAll(DeviceManager.getHardwareProperties(device))
    properties[ConfigKey.GPU_EMULATION] = if (gpuMode == GpuMode.OFF) "no" else "yes"
    properties[ConfigKey.AVD_ID] = avdName
    properties.putAll(bootMode.properties())
    binding.write(this, properties)
    return properties
  }

  companion object {
    /**
     * Values that we unconditionally set before setting other properties. They may be overridden by
     * Device-determined properties. If they need to be configurable, they can be converted to
     * fields.
     */
    val defaultConfigKeys = mapOf(ConfigKey.SKIN_DYNAMIC to "yes")

    internal val binding =
      CompositeBinding(
        AvdBuilder::displayName bindToKey ConfigKey.DISPLAY_NAME,
        AvdBuilder::screenOrientation bindToKey HardwareProperties.HW_INITIAL_ORIENTATION,
        AvdBuilder::showDeviceFrame bindToKey ConfigKey.SHOW_DEVICE_FRAME,
        AvdBuilder::cpuCoreCount bindToKey ConfigKey.CPU_CORES,
        AvdBuilder::ram bindVia StorageConverter() toKey ConfigKey.RAM_SIZE,
        AvdBuilder::vmHeap bindVia StorageConverter() toKey ConfigKey.VM_HEAP_SIZE,
        AvdBuilder::internalStorage bindVia
          StorageConverter(defaultUnit = Storage.Unit.B) toKey
          ConfigKey.DATA_PARTITION_SIZE,
        AvdBuilder::frontCamera bindToKey ConfigKey.CAMERA_FRONT,
        AvdBuilder::backCamera bindToKey ConfigKey.CAMERA_BACK,
        AvdBuilder::gpuMode bindToKey ConfigKey.GPU_MODE,
        AvdBuilder::enableKeyboard bindToKey HardwareProperties.HW_KEYBOARD,
        AvdBuilder::networkLatency bindToKey ConfigKey.NETWORK_LATENCY,
        AvdBuilder::networkSpeed bindToKey ConfigKey.NETWORK_SPEED,
      )

    /** Creates an AvdBuilder for editing an existing AVD. */
    fun createForExistingDevice(device: Device, avdInfo: AvdInfo): AvdBuilder {
      return AvdBuilder(avdInfo.iniFile, avdInfo.dataFolderPath, device).apply {
        systemImage = avdInfo.systemImage

        sdCard = sdCardFromConfig(avdInfo.properties)
        skin = skinFromConfig(avdInfo.properties)

        bootMode = BootMode.fromProperties(avdInfo.properties)
        binding.read(this, avdInfo.properties)

        userSettings.putAll(avdInfo.userSettings)
        userSettings.remove(ConfigKey.ENCODING)
      }
    }
  }
}
