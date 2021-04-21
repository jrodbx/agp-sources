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

package com.android.ide.common.rendering.api;

import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRound;
import com.android.resources.ScreenSize;

/**
 * Hardware configuration for the rendering.
 * This is immutable.
 *
 * @since 9
 */
public class HardwareConfig {

    /** Manufacturer names correspond to the 'hw.device.manufacturer'
     *  field in an AVD's config.ini
     */
    /** Manufacturer used by the generic devices in the device list */
    public static final String MANUFACTURER_GENERIC = "Generic";
    /** Manufacturer used by the Nexus devices in the device list */
    public static final String MANUFACTURER_GOOGLE = "Google";

    /** Device names
     *  These correspond to the 'hw.device.name' field in an AVD's config.ini
     */
    public static final String DEVICE_GALAXY_NEXUS = "Galaxy Nexus";
    public static final String DEVICE_NEXUS_10 = "Nexus 10";
    public static final String DEVICE_NEXUS_4 = "Nexus 4";
    public static final String DEVICE_NEXUS_5 = "Nexus 5";
    public static final String DEVICE_NEXUS_5X = "Nexus 5X";
    public static final String DEVICE_NEXUS_6 = "Nexus 6";
    public static final String DEVICE_NEXUS_6P = "Nexus 6P";
    public static final String DEVICE_NEXUS_7 = "Nexus 7";
    public static final String DEVICE_NEXUS_7_2013 = "Nexus 7 2013";
    public static final String DEVICE_NEXUS_9 = "Nexus 9";
    public static final String DEVICE_NEXUS_ONE = "Nexus One";
    public static final String DEVICE_NEXUS_S = "Nexus S";
    public static final String DEVICE_PIXEL = "pixel";
    public static final String DEVICE_PIXEL_C = "pixel_c";
    public static final String DEVICE_PIXEL_XL = "pixel_xl";

    private final int mScreenWidth;
    private final int mScreenHeight;
    private final Density mDensity;
    private final float mXdpi;
    private final float mYdpi;
    private final ScreenOrientation mOrientation;
    private final ScreenSize mScreenSize;
    private final ScreenRound mScreenRound;

    private final boolean mSoftwareButtons;

    public HardwareConfig(
            int screenWidth,
            int screenHeight,
            Density density,
            float xdpi,
            float ydpi,
            ScreenSize screenSize,
            ScreenOrientation orientation,
            ScreenRound screenRoundness,
            boolean softwareButtons) {
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mDensity = density;
        mXdpi = xdpi;
        mYdpi = ydpi;
        mScreenSize = screenSize;
        mOrientation = orientation;
        mScreenRound = screenRoundness;
        mSoftwareButtons = softwareButtons;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    public Density getDensity() {
        return mDensity;
    }

    public float getXdpi() {
        return mXdpi;
    }

    public float getYdpi() {
        return mYdpi;
    }

    public ScreenSize getScreenSize() {
        return mScreenSize;
    }

    public ScreenOrientation getOrientation() {
        return mOrientation;
    }

    public boolean hasSoftwareButtons() {
        return mSoftwareButtons;
    }

  /**
   * @since 15
   */
  @Nullable
  public ScreenRound getScreenRoundness() {
        return mScreenRound;
    }

    /**
     * @since 15
     */
    @SuppressWarnings({"MethodMayBeStatic", "unused"})
    public int getDensityDpi() {
        throw new UnsupportedOperationException();
    }
}
