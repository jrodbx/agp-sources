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
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.ButtonType;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ---- Device Display Helpers ----

    private static final Pattern GENERIC_PATTERN =
            Pattern.compile("(\\d+\\.?\\d*)\" (.+?)( \\(.*Nexus.*\\))?"); //$NON-NLS-1$

    /**
     * Returns a user-displayable description of the given Nexus device
     * @param device the device to check
     * @return the label
     * @see #isNexus(com.android.sdklib.devices.Device)
     */
    @NonNull
    public static String getNexusLabel(@NonNull Device device) {
        String name = device.getDisplayName();
        Screen screen = device.getDefaultHardware().getScreen();
        float length = (float) screen.getDiagonalLength();
        // Round dimensions to the nearest tenth
        length = Math.round(10 * length) / 10.0f;
        return String.format(Locale.US, "%1$s (%3$s\", %2$s)",
                name, getResolutionString(device), Float.toString(length));
    }

    /**
     * Returns a user-displayable description of the given Nexus device.
     * Optimized for display in menus where multiple other devices are also
     * listed.
     *
     * @param device the device to check
     * @return the label
     * @see #isNexus(com.android.sdklib.devices.Device)
     */
    @NonNull
    public static String getNexusMenuLabel(@NonNull Device device) {
        String name = device.getDisplayName();
        if (isTv(device)) {
            // Example: 1080p, 1920x1080, xhdpi (TV)
            if (name.startsWith("Android TV (")) {
                name = name.substring("Android TV (".length());
                if (name.endsWith(")")) {
                    name = name.substring(0, name.length() - 1);
                }
            }
            return String.format(Locale.US, "%1$s, %2$s (TV)", name, getResolutionString(device));
        } else if (isWear(device)) {
            // Example: 320x200, tvpdi (Round Chin)
            if (name.startsWith("Android Wear ")) {
                name = name.substring("Android Wear ".length());
            }
            return String.format(Locale.US, "%1$s (%2$s)", getResolutionString(device), name);
        } else {
            // Example: 5.2", 1920x1080, 420dpi (Nexus 5X)
            if (name.endsWith(")")) {
                // Drop parentheses, e.g. Nexus 7 (2012) to Nexus 7 2012
                name = name.replace("(", "").replace(")", "");
            }
            Screen screen = device.getDefaultHardware().getScreen();
            float length = (float) screen.getDiagonalLength();
            // Round dimensions to the nearest tenth
            length = Math.round(10 * length) / 10.0f;
            return String.format(Locale.US, "%1$s, %2$s (%3$s)", Float.toString(length),
                                 getResolutionString(device), name);
        }
    }

    /**
     * Returns a user-displayable description of the given generic device
     * @param device the device to check
     * @return the label
     * @see #isGeneric(Device)
     */
    @NonNull
    public static String getGenericLabel(@NonNull Device device) {
        // * Use the same precision for all devices (all but one specify decimals)
        // * Add some leading space such that the dot ends up roughly in the
        //   same space
        // * Add in screen resolution and density
        String name = device.getDisplayName();
        Matcher matcher = GENERIC_PATTERN.matcher(name);
        if (matcher.matches()) {
            String size = matcher.group(1);
            String n = matcher.group(2);
            int dot = size.indexOf('.');
            if (dot == -1) {
                size += ".0";
                dot = size.length() - 2;
            }
            for (int i = 0; i < 2 - dot; i++) {
                size = ' ' + size;
            }
            name = size + "\" " + n;
        }

        return String.format(Locale.US, "%1$s (%2$s)", name,
                getResolutionString(device));
    }

    /**
     * Returns a user displayable screen resolution string for the given device
     * @param device the device to look up the string for
     * @return a user displayable string
     */
    @NonNull
    public static String getResolutionString(@NonNull Device device) {
        Screen screen = device.getDefaultHardware().getScreen();
        return String.format(Locale.US,
                "%1$d \u00D7 %2$d, %3$s", // U+00D7: Unicode multiplication sign
                screen.getXDimension(),
                screen.getYDimension(),
                screen.getPixelDensity().getResourceValue());
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
     * Whether the given device is a wear device
     */
    public static boolean isWear(@Nullable Device device) {
        return device != null && "android-wear".equals(device.getTagId());
    }

    /**
     * Whether the given device is an Android Things device
     */
    public static boolean isThings(@Nullable Device device) {
        return device != null && "android-things".equals(device.getTagId());
    }

    /**
     * Whether the given device is a TV device
     */
    public static boolean isTv(@Nullable Device device) {
        return device != null && "android-tv".equals(device.getTagId());
    }

    /** Whether the given device is an Automotive device */
    public static boolean isAutomotive(@Nullable Device device) {
        return device != null && "android-automotive".equals(device.getTagId());
    }

    /**
     * Whether the given device appears to be a mobile device (e.g. not wear, tv, auto, etc)
     */
    public static boolean isMobile(@Nullable Device device) {
        return !isTv(device) && !isWear(device) && !isThings(device) && !isAutomotive(device);
    }

    /**
     * Returns the rank of the given nexus device. This can be used to order
     * the devices chronologically.
     *
     * @param device the device to look up the rank for
     * @return the rank of the device
     */
    public static int nexusRank(Device device) {
        String id = device.getId();
        switch (id) {
            case DEVICE_NEXUS_ONE:
                return 1;
            case DEVICE_NEXUS_S:
                return 2;
            case DEVICE_GALAXY_NEXUS:
                return 3;
            case DEVICE_NEXUS_7:
                return 4; // 2012 version
            case DEVICE_NEXUS_10:
                return 5;
            case DEVICE_NEXUS_4:
                return 6;
            case DEVICE_NEXUS_7_2013:
                return 7;
            case DEVICE_NEXUS_5:
                return 8;
            case DEVICE_NEXUS_9:
                return 9;
            case DEVICE_NEXUS_6:
                return 10;
            case DEVICE_PIXEL_C:
                return 11;
            case DEVICE_NEXUS_6P:
                return 12;
            case DEVICE_NEXUS_5X:
                return 13;
            case DEVICE_PIXEL:
                return 14;
            case DEVICE_PIXEL_XL:
                return 15;
            default:
                return 100; // devices released in the future?
        }
    }

    /**
     * Sorts the given list of Nexus devices according to rank
     * @param list the list to sort
     */
    public static void sortNexusListByRank(@NonNull List<Device> list) {
        Collections.sort(list, (device1, device2) -> {
            // Sort by screen size
            // Descending order of age
            return nexusRank(device2) - nexusRank(device1);
        });
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
