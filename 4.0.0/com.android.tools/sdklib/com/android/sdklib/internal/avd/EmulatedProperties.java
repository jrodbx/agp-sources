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


import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Storage;

import java.util.Map;

public class EmulatedProperties {
    public static final String BACK_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_BACK;
    public static final String CPU_CORES_KEY = AvdManager.AVD_INI_CPU_CORES;
    public static final String CUSTOM_SKIN_FILE_KEY = AvdManager.AVD_INI_SKIN_PATH;
    public static final String DEVICE_FRAME_KEY = "showDeviceFrame";
    public static final String FRONT_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_FRONT;
    public static final String HAS_HARDWARE_KEYBOARD_KEY = HardwareProperties.HW_KEYBOARD;
    public static final String HOST_GPU_MODE_KEY = AvdManager.AVD_INI_GPU_MODE;
    public static final String INTERNAL_STORAGE_KEY = AvdManager.AVD_INI_DATA_PARTITION_SIZE;
    public static final String NETWORK_LATENCY_KEY = "runtime.network.latency";
    public static final String NETWORK_SPEED_KEY = "runtime.network.speed";
    public static final String RAM_STORAGE_KEY = AvdManager.AVD_INI_RAM_SIZE;
    public static final String SDCARD_SIZE = AvdManager.AVD_INI_SDCARD_SIZE;
    public static final String USE_CHOSEN_SNAPSHOT_BOOT = AvdManager.AVD_INI_FORCE_CHOSEN_SNAPSHOT_BOOT_MODE;
    public static final String USE_COLD_BOOT = AvdManager.AVD_INI_FORCE_COLD_BOOT_MODE;
    public static final String USE_FAST_BOOT = AvdManager.AVD_INI_FORCE_FAST_BOOT_MODE;
    public static final String USE_HOST_GPU_KEY = AvdManager.AVD_INI_GPU_EMULATION;
    public static final String VM_HEAP_STORAGE_KEY = AvdManager.AVD_INI_VM_HEAP_SIZE;

    public static final int MAX_NUMBER_OF_CORES = Integer.max(1, Runtime.getRuntime().availableProcessors() / 2);
    public static final int RECOMMENDED_NUMBER_OF_CORES = Integer.min(4, MAX_NUMBER_OF_CORES);

    public static final Storage DEFAULT_INTERNAL_STORAGE = new Storage(800, Storage.Unit.MiB);
    public static final Storage DEFAULT_HEAP = new Storage(16, Storage.Unit.MiB);
    public static final AvdNetworkSpeed DEFAULT_NETWORK_SPEED = AvdNetworkSpeed.FULL;
    public static final AvdNetworkLatency DEFAULT_NETWORK_LATENCY = AvdNetworkLatency.NONE;
    public static final Storage DEFAULT_SDCARD_SIZE = new Storage(512, Storage.Unit.MiB);

    // Limit the default RAM size. Although the physical device probably has more RAM than this,
    // using more than this amount is usually detrimental to development system performance and
    // most likely is not needed by the emulated app. The value here is intended to let the
    // hardware run smoothly with lots of services and apps running simultaneously.
    public static final Storage MAX_DEFAULT_RAM_SIZE = new Storage(1536, Storage.Unit.MiB);

    /**
     * Return the default RAM size. This is a function of the screen size.
     * (See external/qemu/android/android-emu/android/main-common.c)
     */
    @NonNull
    public static Storage defaultRamStorage(int numPixels) {
        int ramInMb;

        if (numPixels <= 250_000) {
            ramInMb = 96;
        } else if (numPixels <= 500_000) {
            ramInMb = 128;
        } else {
            ramInMb = 256;
        }
        return new Storage(ramInMb, Storage.Unit.MiB);
    }

    /**
     * Limit the RAM size to MAX_DEFAULT_RAM_SIZE
     */
    public static void restrictDefaultRamSize(@NonNull Map<String, String>deviceConfig) {
        Storage ramSize = Storage.getStorageFromString(deviceConfig.get(RAM_STORAGE_KEY));
        if (ramSize != null && MAX_DEFAULT_RAM_SIZE.lessThan(ramSize)) {
            deviceConfig.put(RAM_STORAGE_KEY, MAX_DEFAULT_RAM_SIZE.toIniString());
        }
    }

    /**
     * Set the default VM heap size.
     * This is based on the Android CDD minimums for each screen size and density.
     */
    @NonNull
    public static Storage calculateDefaultVmHeapSize(@NonNull ScreenSize screenSize, @NonNull Density screenDensity, boolean isWear) {
        int vmHeapSize;

        // These values are taken from Android 8.1 Compatibility Definition,
        // dated December 5, 2017, section 3.7, "Runtime Compatibility."
        // (Here I treat ANYDPI and NODPI as MEDIUM.)

        if (isWear) {
            switch(screenDensity) {
                default:
                case LOW:
                case ANYDPI:
                case NODPI:
                case MEDIUM:
                case TV:        vmHeapSize =  32; break;
                case HIGH:
                case DPI_260:
                case DPI_280:   vmHeapSize =  36; break;
                case XHIGH:
                case DPI_300:
                case DPI_340:
                case DPI_360:   vmHeapSize =  48; break;
                case DPI_400:   vmHeapSize =  56; break;
                case DPI_420:
                case DPI_440:   vmHeapSize =  64; break;
                case XXHIGH:    vmHeapSize =  88; break;
                case DPI_560:   vmHeapSize = 112; break;
                case XXXHIGH:   vmHeapSize = 154; break;
            }
        } else {
            switch(screenSize) {
                default:
                case SMALL:
                case NORMAL:
                    switch(screenDensity) {
                        default:
                        case LOW:
                        case ANYDPI:
                        case NODPI:
                        case MEDIUM:    vmHeapSize =  32; break;
                        case TV:
                        case HIGH:
                        case DPI_260:
                        case DPI_280:   vmHeapSize =  48; break;
                        case XHIGH:
                        case DPI_300:
                        case DPI_340:
                        case DPI_360:   vmHeapSize =  80; break;
                        case DPI_400:   vmHeapSize =  96; break;
                        case DPI_420:
                        case DPI_440:   vmHeapSize = 112; break;
                        case XXHIGH:    vmHeapSize = 128; break;
                        case DPI_560:   vmHeapSize = 192; break;
                        case XXXHIGH:   vmHeapSize = 256; break;
                    }
                    break;
                case LARGE:
                    switch(screenDensity) {
                        default:
                        case LOW:       vmHeapSize =  32; break;
                        case ANYDPI:
                        case NODPI:
                        case MEDIUM:    vmHeapSize =  48; break;
                        case TV:
                        case HIGH:      vmHeapSize =  80; break;
                        case DPI_260:
                        case DPI_280:   vmHeapSize =  96; break;
                        case DPI_300:
                        case XHIGH:     vmHeapSize = 128; break;
                        case DPI_340:
                        case DPI_360:   vmHeapSize = 160; break;
                        case DPI_400:   vmHeapSize = 192; break;
                        case DPI_420:
                        case DPI_440:   vmHeapSize = 228; break;
                        case XXHIGH:    vmHeapSize = 256; break;
                        case DPI_560:   vmHeapSize = 384; break;
                        case XXXHIGH:   vmHeapSize = 512; break;
                    }
                    break;
                case XLARGE:
                    switch(screenDensity) {
                        default:
                        case LOW:       vmHeapSize =  48; break;
                        case ANYDPI:
                        case NODPI:
                        case MEDIUM:    vmHeapSize =  80; break;
                        case TV:
                        case HIGH:      vmHeapSize =  96; break;
                        case DPI_260:
                        case DPI_280:   vmHeapSize = 144; break;
                        case DPI_300:
                        case XHIGH:     vmHeapSize = 192; break;
                        case DPI_340:
                        case DPI_360:   vmHeapSize = 240; break;
                        case DPI_400:   vmHeapSize = 288; break;
                        case DPI_420:
                        case DPI_440:   vmHeapSize = 336; break;
                        case XXHIGH:    vmHeapSize = 384; break;
                        case DPI_560:   vmHeapSize = 576; break;
                        case XXXHIGH:   vmHeapSize = 768; break;
                    }
                    break;
            }
        }
        return new Storage(vmHeapSize, Storage.Unit.MiB);
    }
}
