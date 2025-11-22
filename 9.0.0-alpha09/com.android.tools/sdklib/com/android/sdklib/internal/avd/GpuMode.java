/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;

/**
 * Describes the GPU mode settings supported by the emulator.
 */
public enum GpuMode implements ConfigEnum {
    AUTO("auto"),
    HOST("host"),
    SWIFT("software"),
    OFF("off");

    private final String mySetting;

    GpuMode(@NonNull String setting) {
        mySetting = setting;
    }

    @NonNull
    public static GpuMode getSoftwareGpuMode(@NonNull ISystemImage image) {
        return emulatorUsesSwiftFor(image) ? SWIFT : OFF;
    }

    private static boolean emulatorUsesSwiftFor(@NonNull ISystemImage image) {
        Abi abi = Abi.getEnum(image.getPrimaryAbiType());

        return image.getAndroidVersion().getFeatureLevel() > 22
                && abi != null
                && abi.supportsMultipleCpuCores()
                && image.hasGoogleApis();
    }

    @Override
    public String toString() {
        switch (this) {
            case AUTO:
                return "Automatic";
            case HOST:
                return "Hardware";
            case SWIFT:
            case OFF:
            default:
                return "Software";
        }
    }

    public static GpuMode fromGpuSetting(@Nullable String setting) {
        for (GpuMode mode : values()) {
            if (mode.mySetting.equals(setting)) {
                return mode;
            }
        }
        return OFF;
    }

    public String getGpuSetting() {
        return mySetting;
    }

    @Override
    @NonNull
    public String getAsParameter() {
        return getGpuSetting();
    }
}
