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
package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.IdDisplay;

import java.util.Collection;

public final class DeviceSystemImageMatcher {

    private DeviceSystemImageMatcher() {}

    public static boolean matches(@NonNull Device device, @NonNull ISystemImage image) {
        Collection<IdDisplay> tags = image.getTags();

        if (image.hasPlayStore() && !device.hasPlayStore()) {
            return false;
        }

        int apiLevel = image.getAndroidVersion().getApiLevel();
        if (device.getAllSoftware().stream()
                .noneMatch(
                        software ->
                                software.getMinSdkLevel() <= apiLevel
                                        && apiLevel <= software.getMaxSdkLevel())) {
            return false;
        }

        if (!Device.isTablet(device) && SystemImageTags.isTabletImage(tags)) {
            return false;
        }

        Object id = device.getTagId();

        if (id == null || id.equals(SystemImageTags.DEFAULT_TAG.getId())) {
            return !SystemImageTags.isWearImage(tags)
                    && !SystemImageTags.isDesktopImage(tags)
                    && !SystemImageTags.isTvImage(tags)
                    && !SystemImageTags.isAutomotiveImage(tags)
                    && !tags.contains(SystemImageTags.CHROMEOS_TAG);
        }

        if (id.equals(SystemImageTags.ANDROID_TV_TAG.getId())
                || id.equals(SystemImageTags.GOOGLE_TV_TAG.getId())) {
            return SystemImageTags.isTvImage(tags);
        }

        return tags.stream().map(IdDisplay::getId).anyMatch(i -> i.equals(id));
    }
}
