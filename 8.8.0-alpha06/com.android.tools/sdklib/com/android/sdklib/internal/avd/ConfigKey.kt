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

/**
 * Keys to config entries in an AVD's config.ini file (which resides within the AVD's data folder).
 */
object ConfigKey {
  /** Charset encoding used by the avd.ini/config.ini. */
  const val ENCODING = "avd.ini.encoding" // $NON-NLS-1$

  /** The first tag id of the AVD's system image */
  const val TAG_ID = "tag.id" // $NON-NLS-1$

  /** The tag ids of the AVD's system image, represented as a comma-separated list */
  const val TAG_IDS = "tag.ids" // $NON-NLS-1$

  /** The first tag display name of the AVD's system image */
  const val TAG_DISPLAY = "tag.display" // $NON-NLS-1$

  /**
   * The display names of the tags of the AVD's system image, represented as a comma-separated list
   */
  const val TAG_DISPLAYNAMES = "tag.displaynames" // $NON-NLS-1$

  /** The ABI of the AVD's system image */
  const val ABI_TYPE = "abi.type" // $NON-NLS-1$

  /** The name of the AVD */
  const val AVD_ID = "AvdId"

  /** Flag indicating if the AVD supports the Play Store */
  const val PLAYSTORE_ENABLED = "PlayStore.enabled"

  /** The CPU architecture of the avd */
  const val CPU_ARCH = "hw.cpu.arch" // $NON-NLS-1$

  /** The CPU model of the avd */
  const val CPU_MODEL = "hw.cpu.model" // $NON-NLS-1$

  /** The number of processors to emulate when SMP is supported. */
  const val CPU_CORES = "hw.cpu.ncore" // $NON-NLS-1$

  /** The manufacturer of the device this avd was based on. */
  const val DEVICE_MANUFACTURER = "hw.device.manufacturer" // $NON-NLS-1$

  /** The name of the device this avd was based on. */
  const val DEVICE_NAME = "hw.device.name" // $NON-NLS-1$

  /** Flag indicating if it's Chrome OS (App Runtime for Chrome). */
  const val ARC = "hw.arc"

  /** The display name of the AVD */
  const val DISPLAY_NAME = "avd.ini.displayname"

  /** Flag indicating if a skin should be rendered around the device. */
  const val SHOW_DEVICE_FRAME = "showDeviceFrame"

  /**
   * The SDK-relative path of the skin folder, if any, or a 320x480 like constant for a numeric skin
   * size.
   *
   * @see NUMERIC_SKIN_SIZE
   */
  const val SKIN_PATH = "skin.path" // $NON-NLS-1$

  /**
   * The SDK-relative path of the skin folder to be selected if skins for this device become
   * enabled.
   */
  const val BACKUP_SKIN_PATH = "skin.path.backup" // $NON-NLS-1$

  /**
   * A UI name for the skin. This config key is ignored by the emulator. It is only used by the SDK
   * manager or tools to give a friendlier name to the skin. If missing, use the [SKIN_PATH] key
   * instead.
   */
  const val SKIN_NAME = "skin.name" // $NON-NLS-1$

  /** Flag indicating if a dynamic skin should be displayed. */
  const val SKIN_DYNAMIC = "skin.dynamic" // $NON-NLS-1$

  /**
   * The path to the sdcard file. If missing, the default name "sdcard.img" will be used for the
   * sdcard, if there's such a file.
   *
   * @see SDCARD_IMG
   */
  const val SDCARD_PATH = "sdcard.path" // $NON-NLS-1$

  /**
   * The size of the SD card. This property is for UI purposes only. It is not used by the emulator.
   *
   * @see SDCARD_SIZE_PATTERN
   * @see parseSdCard
   */
  const val SDCARD_SIZE = "sdcard.size" // $NON-NLS-1$

  /**
   * The first path where the emulator looks for system images. Typically this is the path to the
   * add-on system image or the path to the platform system image if there's no add-on.
   *
   * The emulator looks at [IMAGES_1] before [IMAGES_2].
   */
  const val IMAGES_1 = "image.sysdir.1" // $NON-NLS-1$

  /**
   * The second path where the emulator looks for system images. Typically this is the path to the
   * platform system image.
   *
   * @see IMAGES_1
   */
  const val IMAGES_2 = "image.sysdir.2" // $NON-NLS-1$

  /**
   * The presence of the snapshots file. This property is for UI purposes only. It is not used by
   * the emulator.
   */
  const val SNAPSHOT_PRESENT = "snapshot.present" // $NON-NLS-1$

  /** Flag indicating if hardware OpenGLES emulation is enabled */
  const val GPU_EMULATION = "hw.gpu.enabled" // $NON-NLS-1$

  /** Which software OpenGLES should be used, represented by [GpuMode]. */
  const val GPU_MODE = "hw.gpu.mode"

  /** Speed of the simulated network, represented by [AvdNetworkSpeed]. */
  const val NETWORK_SPEED = "runtime.network.speed"

  /** Latency of the simulated network, represented by [AvdNetworkLatency]. */
  const val NETWORK_LATENCY = "runtime.network.latency"

  /** Flag indicating the emulator should perform a cold boot rather than using a snapshot. */
  const val FORCE_COLD_BOOT_MODE = "fastboot.forceColdBoot"

  const val FORCE_CHOSEN_SNAPSHOT_BOOT_MODE = "fastboot.forceChosenSnapshotBoot"

  const val FORCE_FAST_BOOT_MODE = "fastboot.forceFastBoot"

  const val CHOSEN_SNAPSHOT_FILE = "fastboot.chosenSnapshotFile"

  /** How to emulate the front facing camera, represented by [AvdCamera]. */
  const val CAMERA_FRONT = "hw.camera.front" // $NON-NLS-1$

  /** How to emulate the rear facing camera, represented by [AvdCamera] */
  const val CAMERA_BACK = "hw.camera.back" // $NON-NLS-1$

  /** The amount of RAM the emulated device should have */
  const val RAM_SIZE = "hw.ramSize"

  /** The amount of memory available to applications by default */
  const val VM_HEAP_SIZE = "vm.heapSize"

  /** The size of the data partition */
  const val DATA_PARTITION_SIZE = "disk.dataPartition.size"

  /**
   * The hash of the device this AVD is based on.
   *
   * This old hash is deprecated and shouldn't be used anymore. It represents the Device.hashCode()
   * and is not stable across implementations.
   *
   * @see DEVICE_HASH_V2
   */
  const val DEVICE_HASH_V1 = "hw.device.hash"

  /**
   * The hash of the device hardware properties actually present in the config.ini. This replaces
   * [DEVICE_HASH_V1].
   *
   * To find this hash, use `DeviceManager.getHardwareProperties(device).get(DEVICE_HASH_V2)`.
   */
  const val DEVICE_HASH_V2 = "hw.device.hash2"

  /** The Android display settings file */
  const val DISPLAY_SETTINGS_FILE = "display.settings.xml"

  /** The hinge settings */
  const val HINGE = "hw.sensor.hinge"

  const val HINGE_COUNT = "hw.sensor.hinge.count"

  const val HINGE_TYPE = "hw.sensor.hinge.type"

  const val HINGE_SUB_TYPE = "hw.sensor.hinge.sub_type"

  const val HINGE_RANGES = "hw.sensor.hinge.ranges"

  const val HINGE_DEFAULTS = "hw.sensor.hinge.defaults"

  const val HINGE_AREAS = "hw.sensor.hinge.areas"

  const val POSTURE_LISTS = "hw.sensor.posture_list"

  const val FOLD_AT_POSTURE = "hw.sensor.hinge.fold_to_displayRegion.0.1_at_posture"

  const val HINGE_ANGLES_POSTURE_DEFINITIONS = "hw.sensor.hinge_angles_posture_definitions"

  /** The resizable settings */
  const val RESIZABLE_CONFIG = "hw.resizable.configs"

  /** The rollable settings */
  const val ROLL = "hw.sensor.roll"

  const val ROLL_COUNT = "hw.sensor.roll.count"

  const val ROLL_RANGES = "hw.sensor.roll.ranges"

  const val ROLL_DEFAULTS = "hw.sensor.roll.defaults"

  const val ROLL_RADIUS = "hw.sensor.roll.radius"

  const val ROLL_DIRECTION = "hw.sensor.roll.direction"

  // Settings for Android Automotive instrument cluster display
  const val CLUSTER_WIDTH = "hw.display6.width"

  const val CLUSTER_HEIGHT = "hw.display6.height"

  const val CLUSTER_DENSITY = "hw.display6.density"

  const val CLUSTER_FLAG = "hw.display6.flag"

  // Settings for Android Automotive distant display
  const val DISTANT_DISPLAY_WIDTH = "hw.display7.width"

  const val DISTANT_DISPLAY_HEIGHT = "hw.display7.height"

  const val DISTANT_DISPLAY_DENSITY = "hw.display7.density"

  const val DISTANT_DISPLAY_FLAG = "hw.display7.flag"

  const val ROLL_RESIZE_1_AT_POSTURE = "hw.sensor.roll.resize_to_displayRegion.0.1_at_posture"

  const val ROLL_RESIZE_2_AT_POSTURE = "hw.sensor.roll.resize_to_displayRegion.0.2_at_posture"

  const val ROLL_RESIZE_3_AT_POSTURE = "hw.sensor.roll.resize_to_displayRegion.0.3_at_posture"

  const val ROLL_PERCENTAGES_POSTURE_DEFINITIONS = "hw.sensor.roll_percentages_posture_definitions"

  // The following properties beginning with "image.androidVersion" are derived from
  // [MetadataKey.TARGET] and stored in AvdInfo during parsing; they are not present in config.ini.

  /** The API level of this AVD. Derived from the target hash. */
  const val ANDROID_API = "image.androidVersion.api"

  /** The Sdk Extension level of this AVD. Derived from the target hash. */
  const val ANDROID_EXTENSION = "image.androidVersion.extension"

  /** Flag indicating if the AVD's target SDK Extension is the base extension */
  const val ANDROID_IS_BASE_EXTENSION = "image.androidVersion.isBaseExtension"

  /** The API codename of this AVD. Derived from the target hash. */
  const val ANDROID_CODENAME = "image.androidVersion.codename"

  const val ANDROID_EXTENSION_LEVEL = "image.androidVersion.ext"
}
