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

package com.android.build.gradle.options;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;

public final class DeploymentDevice {
    private DeploymentDevice() {}

    /**
     * Returns the Android Version for the target device, as injected by studio.
     *
     * @param options the options for the project being built
     * @return the Android version of the deployment device, as injected by studio, or
     *     AndroidVersion.DEFAULT if not set.
     */
    @NonNull
    public static AndroidVersion getDeploymentDeviceAndroidVersion(
            @NonNull ProjectOptions options) {
        Integer apiLevel = options.get(IntegerOption.IDE_TARGET_DEVICE_API);
        if (apiLevel == null) {
            return AndroidVersion.DEFAULT;
        }
        @Nullable String codeName = options.get(StringOption.IDE_TARGET_DEVICE_CODENAME);
        return new AndroidVersion(apiLevel, codeName);
    }
}
