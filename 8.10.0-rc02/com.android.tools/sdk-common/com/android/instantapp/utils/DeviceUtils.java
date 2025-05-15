/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.instantapp.utils;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;

/** Adb utils common to multiple other packages in instant app context. */
public class DeviceUtils {

    public static boolean isPostO(@NonNull IDevice device) {
        // This will accept both O (API 26) and O Previews (API 25 codename Oreo).
        return device.getVersion().isAtLeast(25, "O");
    }

    @Nullable
    public static String getOsBuildType(@NonNull IDevice device) {
        return device.getProperty("ro.build.tags");
    }
}
