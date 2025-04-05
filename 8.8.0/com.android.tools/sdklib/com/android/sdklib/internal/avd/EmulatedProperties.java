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

package com.android.sdklib.internal.avd;

import static com.android.resources.Density.ANYDPI;
import static com.android.resources.Density.NODPI;

import static com.google.common.collect.Comparators.min;

import static java.util.Comparator.comparing;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Storage;

import java.util.Map;

public class EmulatedProperties {
    public static final String BACK_CAMERA_KEY = ConfigKey.CAMERA_BACK;
    public static final String CPU_CORES_KEY = ConfigKey.CPU_CORES;
    public static final String CUSTOM_SKIN_FILE_KEY = ConfigKey.SKIN_PATH;
    public static final String DEVICE_FRAME_KEY = ConfigKey.SHOW_DEVICE_FRAME;
    public static final String FRONT_CAMERA_KEY = ConfigKey.CAMERA_FRONT;
    public static final String HAS_HARDWARE_KEYBOARD_KEY = HardwareProperties.HW_KEYBOARD;
    public static final String HOST_GPU_MODE_KEY = ConfigKey.GPU_MODE;
    public static final String INTERNAL_STORAGE_KEY = ConfigKey.DATA_PARTITION_SIZE;
    public static final String NETWORK_LATENCY_KEY = ConfigKey.NETWORK_LATENCY;
    public static final String NETWORK_SPEED_KEY = ConfigKey.NETWORK_SPEED;
    public static final String RAM_STORAGE_KEY = ConfigKey.RAM_SIZE;
    public static final String SDCARD_SIZE = ConfigKey.SDCARD_SIZE;
    public static final String USE_CHOSEN_SNAPSHOT_BOOT = ConfigKey.FORCE_CHOSEN_SNAPSHOT_BOOT_MODE;
    public static final String USE_COLD_BOOT = ConfigKey.FORCE_COLD_BOOT_MODE;
    public static final String USE_FAST_BOOT = ConfigKey.FORCE_FAST_BOOT_MODE;
    public static final String USE_HOST_GPU_KEY = ConfigKey.GPU_EMULATION;
    public static final String VM_HEAP_STORAGE_KEY = ConfigKey.VM_HEAP_SIZE;

    public static final int MAX_NUMBER_OF_CORES = Integer.max(1, Runtime.getRuntime().availableProcessors() / 2);
    public static final int RECOMMENDED_NUMBER_OF_CORES = Integer.min(4, MAX_NUMBER_OF_CORES);

    public static final Storage DEFAULT_INTERNAL_STORAGE = new Storage(800, Storage.Unit.MiB);
    public static final Storage DEFAULT_HEAP = new Storage(16, Storage.Unit.MiB);
    public static final AvdNetworkSpeed DEFAULT_NETWORK_SPEED = AvdNetworkSpeed.FULL;
    public static final AvdNetworkLatency DEFAULT_NETWORK_LATENCY = AvdNetworkLatency.NONE;
    public static final Storage DEFAULT_SDCARD_SIZE = new Storage(512, Storage.Unit.MiB);

    // The maximum default RAM size; see #defaultRamSize().
    public static final Storage MAX_DEFAULT_RAM_SIZE = new Storage(2, Storage.Unit.GiB);

    /** Limit the RAM size to MAX_DEFAULT_RAM_SIZE */
    public static void restrictDefaultRamSize(@NonNull Map<String, String> deviceConfig) {
        Storage ramSize = Storage.getStorageFromString(deviceConfig.get(RAM_STORAGE_KEY));
        if (ramSize != null && MAX_DEFAULT_RAM_SIZE.lessThan(ramSize)) {
            deviceConfig.put(RAM_STORAGE_KEY, MAX_DEFAULT_RAM_SIZE.toIniString());
        }
    }

    /**
     * Get the default amount of ram to use for an AVD based on the given Device. The user may set a
     * higher amount of RAM if warranted.
     *
     * <p>With modern devices, this is typically a lower limit than the physical hardware, since
     * more than that is usually detrimental to development system performance and most likely not
     * needed by the emulated app (e.g. it's intended to let the hardware run smoothly with lots of
     * services and apps running simultaneously)
     */
    public static Storage defaultRamSize(@NonNull Device device) {
        return min(
                device.getDefaultHardware().getRam(),
                MAX_DEFAULT_RAM_SIZE,
                comparing(Storage::getSize));
    }

    /** Returns the default VM heap size for the given device. */
    public static Storage defaultVmHeapSize(@NonNull Device device) {
        return minimumVmHeapSize(
                ScreenSize.getScreenSize(
                        device.getDefaultHardware().getScreen().getDiagonalLength()),
                device.getDefaultHardware().getScreen().getPixelDensity(),
                Device.isWear(device));
    }

    /**
     * Returns the "minimum application memory" as defined by the Android 14 Compatibility
     * Definition Document, section 3.7, "Runtime Compatibility."
     */
    @NonNull
    public static Storage minimumVmHeapSize(
            @NonNull ScreenSize screenSize, @NonNull Density screenDensity, boolean isWear) {
        int vmHeapSize;

        // Treat ANYDPI and NODPI as Density.MEDIUM (160 dpi).
        if (screenDensity == ANYDPI || screenDensity == NODPI) {
            screenDensity = Density.MEDIUM;
        }

        if (isWear) {
            if (screenDensity.getDpiValue() <= 220) {
                vmHeapSize = 32;
            } else if (screenDensity.getDpiValue() <= 280) {
                vmHeapSize = 36;
            } else if (screenDensity.getDpiValue() <= 360) {
                vmHeapSize = 48;
            } else if (screenDensity.getDpiValue() <= 400) {
                vmHeapSize = 56;
            } else if (screenDensity.getDpiValue() <= 440) {
                vmHeapSize = 64;
            } else if (screenDensity.getDpiValue() <= 480) {
                vmHeapSize = 88;
            } else if (screenDensity.getDpiValue() <= 560) {
                vmHeapSize = 112;
            } else {
                vmHeapSize = 156;
            }
        } else {
            switch(screenSize) {
                default:
                case SMALL:
                case NORMAL:
                    if (screenDensity.getDpiValue() <= 160) {
                        vmHeapSize = 32;
                    } else if (screenDensity.getDpiValue() <= 280) {
                        vmHeapSize = 48;
                    } else if (screenDensity.getDpiValue() <= 360) {
                        vmHeapSize = 80;
                    } else if (screenDensity.getDpiValue() <= 400) {
                        vmHeapSize = 96;
                    } else if (screenDensity.getDpiValue() <= 420) {
                        vmHeapSize = 112;
                    } else if (screenDensity.getDpiValue() <= 480) {
                        vmHeapSize = 128;
                    } else if (screenDensity.getDpiValue() <= 560) {
                        vmHeapSize = 192;
                    } else {
                        vmHeapSize = 256;
                    }
                    break;
                case LARGE:
                    if (screenDensity.getDpiValue() <= 120) {
                        vmHeapSize = 32;
                    } else if (screenDensity.getDpiValue() <= 160) {
                        vmHeapSize = 48;
                    } else if (screenDensity.getDpiValue() <= 240) {
                        vmHeapSize = 80;
                    } else if (screenDensity.getDpiValue() <= 280) {
                        vmHeapSize = 96;
                    } else if (screenDensity.getDpiValue() <= 320) {
                        vmHeapSize = 128;
                    } else if (screenDensity.getDpiValue() <= 360) {
                        vmHeapSize = 160;
                    } else if (screenDensity.getDpiValue() <= 400) {
                        vmHeapSize = 192;
                    } else if (screenDensity.getDpiValue() <= 420) {
                        vmHeapSize = 228;
                    } else if (screenDensity.getDpiValue() <= 480) {
                        vmHeapSize = 256;
                    } else if (screenDensity.getDpiValue() <= 560) {
                        vmHeapSize = 384;
                    } else {
                        vmHeapSize = 512;
                    }
                    break;
                case XLARGE:
                    if (screenDensity.getDpiValue() <= 120) {
                        vmHeapSize = 48;
                    } else if (screenDensity.getDpiValue() <= 160) {
                        vmHeapSize = 80;
                    } else if (screenDensity.getDpiValue() <= 240) {
                        vmHeapSize = 96;
                    } else if (screenDensity.getDpiValue() <= 280) {
                        vmHeapSize = 144;
                    } else if (screenDensity.getDpiValue() <= 320) {
                        vmHeapSize = 192;
                    } else if (screenDensity.getDpiValue() <= 360) {
                        vmHeapSize = 240;
                    } else if (screenDensity.getDpiValue() <= 400) {
                        vmHeapSize = 288;
                    } else if (screenDensity.getDpiValue() <= 420) {
                        vmHeapSize = 336;
                    } else if (screenDensity.getDpiValue() <= 480) {
                        vmHeapSize = 384;
                    } else if (screenDensity.getDpiValue() <= 560) {
                        vmHeapSize = 576;
                    } else {
                        vmHeapSize = 768;
                    }
                    break;
            }
        }
        return new Storage(vmHeapSize, Storage.Unit.MiB);
    }

    public static Storage defaultInternalStorage(@NonNull Device device) {
        return DEFAULT_INTERNAL_STORAGE;
    }
}
