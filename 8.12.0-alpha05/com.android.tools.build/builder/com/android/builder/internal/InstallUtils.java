/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.builder.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.testing.api.DeviceConnector;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.ILogger;

public class InstallUtils {

    /**
     * Checks whether a device is compatible with a given app minSdkVersion value.
     *
     * @param deviceName Name of the device
     * @param deviceApiLevel API level of the device
     * @param appMinSdkVersion the minSdkVersion of the app
     * @param logger a logger object
     * @param projectPath the project name for logging
     * @param variantName the variant name for logging
     * @return true if the device can run the app
     */
    public static boolean checkDeviceApiLevel(
            @Nullable String deviceName,
            int deviceApiLevel,
            @Nullable String deviceCodeName,
            @NonNull AndroidVersion appMinSdkVersion,
            @NonNull ILogger logger,
            @NonNull String projectPath,
            @NonNull String variantName) {
        if (deviceApiLevel == 0) {
            logger.lifecycle(
                    "Skipping device '%1$s' for '%2$s:%3$s': Unknown API Level",
                    deviceName, projectPath, variantName);
            return false;
        }

        int minSdkVersion = appMinSdkVersion.getApiLevel();

        // Convert codename to API version.
        if (appMinSdkVersion.getCodename() != null) {
            if (deviceCodeName != null) {
                if (!deviceCodeName.equals(appMinSdkVersion.getCodename())) {
                    logger.lifecycle(
                            "Skipping device '%1$s', due to different API preview '%2$s' and"
                                    + " '%3$s'",
                            deviceName, deviceCodeName, appMinSdkVersion.getCodename());
                    return false;
                }
            } else {
                minSdkVersion = SdkVersionInfo.getApiByBuildCode(
                        appMinSdkVersion.getCodename(), true);
            }
        }

        if (minSdkVersion > deviceApiLevel) {
            logger.lifecycle(
                    "Skipping device '%s' for '%s:%s': minSdkVersion [%s] > deviceApiLevel [%d]",
                    deviceName,
                    projectPath,
                    variantName,
                    appMinSdkVersion.getApiString(),
                    deviceApiLevel);

            return false;
        } else {
            return true;
        }
    }

    /**
     * Checks whether a device is compatible with a given app minSdkVersion value.
     *
     * @param device the device
     * @param appMinSdkVersion the minSdkVersion of the app
     * @param logger a logger object
     * @param projectPath the project name for logging
     * @param variantName the variant name for logging
     * @return true if the device can run the app
     */
    public static boolean checkDeviceApiLevel(
            @NonNull DeviceConnector device,
            @NonNull AndroidVersion appMinSdkVersion,
            @NonNull ILogger logger,
            @NonNull String projectPath,
            @NonNull String variantName) {
        return checkDeviceApiLevel(
                device.getName(),
                device.getApiLevel(),
                device.getApiCodeName(),
                appMinSdkVersion,
                logger,
                projectPath,
                variantName);
    }
}
