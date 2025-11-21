/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.common.rendering;

import static com.android.ide.common.rendering.api.HardwareConfig.*;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.ButtonType;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import java.util.Collections;
import java.util.List;

/**
 * Helper method to create a {@link HardwareConfig} object.
 *
 * The base data comes from a {@link Device} object, with additional data provided on the helper
 * object.
 *
 * Since {@link HardwareConfig} is immutable, this allows creating one in several (optional)
 * steps more easily.
 *
 */
public class HardwareConfigHelper {

    @NonNull
    private final Device mDevice;
    @NonNull
    private ScreenOrientation mScreenOrientation = ScreenOrientation.PORTRAIT;

    // optional
    private int mMaxRenderWidth = -1;
    private int mMaxRenderHeight = -1;
    private int mOverrideRenderWidth = -1;
    private int mOverrideRenderHeight = -1;

    /**
     * Creates a new helper for a given device.
     * @param device the device to provide the base data.
     */
    public HardwareConfigHelper(@NonNull Device device) {
        mDevice = device;
    }

    /**
     * Sets the orientation of the config.
     * @param screenOrientation the orientation.
     * @return this (such that chains of setters can be stringed together)
     */
    @NonNull
    public HardwareConfigHelper setOrientation(@NonNull ScreenOrientation screenOrientation) {
        mScreenOrientation = screenOrientation;
        return this;
    }

    /**
     * Overrides the width and height to be used during rendering.
     *
     * A value of -1 will make the rendering use the normal width and height coming from the
     * {@link Device} object.
     *
     * @param overrideRenderWidth the width in pixels of the layout to be rendered
     * @param overrideRenderHeight the height in pixels of the layout to be rendered
     * @return this (such that chains of setters can be stringed together)
     */
    @NonNull
    public HardwareConfigHelper setOverrideRenderSize(int overrideRenderWidth,
            int overrideRenderHeight) {
        mOverrideRenderWidth = overrideRenderWidth;
        mOverrideRenderHeight = overrideRenderHeight;
        return this;
    }

    /**
     * Sets the max width and height to be used during rendering.
     *
     * A value of -1 will make the rendering use the normal width and height coming from the
     * {@link Device} object.
     *
     * @param maxRenderWidth the max width in pixels of the layout to be rendered
     * @param maxRenderHeight the max height in pixels of the layout to be rendered
     * @return this (such that chains of setters can be stringed together)
     */
    @NonNull
    public HardwareConfigHelper setMaxRenderSize(int maxRenderWidth, int maxRenderHeight) {
        mMaxRenderWidth = maxRenderWidth;
        mMaxRenderHeight = maxRenderHeight;
        return this;
    }

    /**
     * Creates and returns the HardwareConfig object.
     * @return the config
     */
    @SuppressWarnings("SuspiciousNameCombination") // Deliberately swapping orientations
    @NonNull
    public HardwareConfig getConfig() {
        Screen screen = mDevice.getDefaultHardware().getScreen();

        // compute width and height to take orientation into account.
        int x = screen.getXDimension();
        int y = screen.getYDimension();
        int width, height;

        if (x > y) {
            if (mScreenOrientation == ScreenOrientation.LANDSCAPE) {
                width = x;
                height = y;
            } else {
                width = y;
                height = x;
            }
        } else {
            if (mScreenOrientation == ScreenOrientation.LANDSCAPE) {
                width = y;
                height = x;
            } else {
                width = x;
                height = y;
            }
        }

        if (mOverrideRenderHeight != -1) {
            width = mOverrideRenderWidth;
        }

        if (mOverrideRenderHeight != -1) {
            height = mOverrideRenderHeight;
        }

        if (mMaxRenderWidth != -1) {
            width = mMaxRenderWidth;
        }

        if (mMaxRenderHeight != -1) {
            height = mMaxRenderHeight;
        }

        return new HardwareConfig(
                width,
                height,
                screen.getPixelDensity(),
                (float) screen.getXdpi(),
                (float) screen.getYdpi(),
                screen.getSize(),
                mScreenOrientation,
                mDevice.getDefaultHardware().getScreen().getScreenRound(),
                mDevice.getDefaultHardware().getButtonType() == ButtonType.SOFT);
    }

    /**
     * Returns true if the given device is a generic device
     * @param device the device to check
     * @return true if the device is generic
     */
    public static boolean isGeneric(@NonNull Device device) {
        return MANUFACTURER_GENERIC.equals(device.getManufacturer());
    }

    /**
     * Returns true if the given device is a Nexus device
     * @param device the device to check
     * @return true if the device is a Nexus
     */
    public static boolean isNexus(@NonNull Device device) {
        return MANUFACTURER_GOOGLE.equals(device.getManufacturer());
    }

    /**
     * Sorts the given list of Nexus devices according to rank
     * @param list the list to sort
     */
    public static void sortDevicesByScreenSize(@NonNull List<Device> list) {
        Collections.sort(list, (device1, device2) -> {

            Screen screen1 = device1.getDefaultHardware().getScreen();
            float length1 = (float) screen1.getDiagonalLength();

            Screen screen2 = device2.getDefaultHardware().getScreen();
            float length2 = (float) screen2.getDiagonalLength();

            return (int) Math.signum(length1 - length2);
        });
    }
}
