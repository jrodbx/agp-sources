/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Maps;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Screen density.
 *
 * <p>These values are used in the manifest in the uses-configuration node and in the resource
 * folder names as well as in other places that need to know the density values.
 */
public class Density implements Comparable<Density>, ResourceEnum, Serializable {

    public static final Density XXXHIGH = new Density("xxxhdpi", "XXX-High Density", 640, 18);

    public static final Density XXHIGH = new Density("xxhdpi", "XX-High Density", 480, 16);

    public static final Density XHIGH = new Density("xhdpi", "X-High Density", 320, 8);

    public static final Density HIGH = new Density("hdpi", "High Density", 240, 4);

    public static final Density TV = new Density("tvdpi", "TV Density", 213, 13);

    public static final Density MEDIUM = new Density("mdpi", "Medium Density", 160, 4);

    public static final Density LOW = new Density("ldpi", "Low Density", 120, 4);

    // 0xFFFE is the value used by the framework.
    public static final Density ANYDPI = new Density("anydpi", "Any Density", 0xFFFE, 21);

    // 0xFFFF is the value used by the framework.
    public static final Density NODPI = new Density("nodpi", "No Density", 0xFFFF, 4);

    private static final Density[] values = {
        XXXHIGH, XXHIGH, XHIGH, HIGH, TV, MEDIUM, LOW, ANYDPI, NODPI
    };

    public static final int DEFAULT_DENSITY = MEDIUM.getDpiValue();

    private static final Map<String, Density> densityByValue = Maps.newHashMap();

    private static final Pattern sDensityPattern = Pattern.compile("^(\\d+)dpi$");

    private static final long serialVersionUID = 1L;

    static {
        for (Density density : values) {
            densityByValue.put(density.mValue, density);
        }
    }

    @NonNull private final String mValue;
    @NonNull private final String mDisplayValue;
    private final int mDpi;
    private final int mSince;

    @NotNull
    public static Density create(int dpi) {
        Density density = getEnum(dpi);
        if (density != null) {
            return density;
        }
        return new Density(dpi);
    }

    /**
     * Returns a Density based on a dpi qualifier found in e.g. a FolderConfiguration.
     *
     * @param value one of the predefined values: ldpi, mdpi, hdpi, ... or and arbitrary value of an
     *     natural number followed by "dpi" e.g. "560dpi".
     * @return the returned value is either:
     *     <ul>
     *       <li>one of the predefined Density values defined above
     *       <li>an arbitrary dpi value Density(dpi) where dpi is a natural number
     *       <li>null if the value was not be recognized as a valid Density qualifier
     *     </ul>
     */
    @Nullable
    public static Density create(@NonNull String value) {
        Density density = getEnum(value);
        if (density != null) {
            return density;
        }
        Matcher m = sDensityPattern.matcher(value);
        if (!m.matches()) {
            return null;
        }
        String dpiString = m.group(1);
        try {
            int dpi = Integer.parseInt(dpiString);
            density = Density.getEnum(dpi);
            if (density == null) {
                density = new Density(dpi);
            }
            return density;
        } catch (NumberFormatException e) {
            // looks like the string we extracted wasn't a valid number
            // which really shouldn't happen since the regexp would have failed.
            throw new AssertionError(e);
        }
    }

    public Density(int density) {
        mValue = density + "dpi";
        mDisplayValue = mValue;
        mDpi = density;
        mSince = 1;
    }

    /**
     * Creates a predefined Density instance.
     *
     * @param value a density qualifier defined by the platform: ldpi, mdpi, hdpi, ...
     * @param displayValue a string to present in the UI
     * @param density the density value
     * @param since the Android API level where the density qualifier was added
     */
    private Density(@NonNull String value, @NonNull String displayValue, int density, int since) {
        mValue = value;
        mDisplayValue = displayValue;
        mDpi = density;
        mSince = since;
    }

    @Override
    public int hashCode() {
        return mDpi;
    }

    @Override
    public boolean equals(@Nullable Object otherInstance) {
        if (!(otherInstance instanceof Density)) {
            return false;
        }
        Density other = (Density) otherInstance;
        return mValue.equals(other.mValue)
                && mDisplayValue.equals(other.mDisplayValue)
                && mDpi == other.mDpi
                && mSince == other.mSince;
    }

    @Override
    public String toString() {
        return mDisplayValue;
    }

    @Override
    public int compareTo(@NotNull Density other) {
        return other.mDpi - mDpi;
    }

    @NotNull
    public static Density[] values() {
        return values;
    }

    /**
     * Returns the enum matching the provided qualifier value.
     *
     * @param value The qualifier value.
     * @return the enum for the qualifier value or null if no match was found.
     */
    @Nullable
    public static Density getEnum(@Nullable String value) {
        return densityByValue.get(value);
    }

    /**
     * Returns the enum matching the given DPI value.
     *
     * @param dpiValue The density value.
     * @return the enum for the density value or null if no match was found.
     */
    @Nullable
    public static Density getEnum(int dpiValue) {
        Collection<Density> densities = densityByValue.values();
        for (Density density : densities) {
            if (density.mDpi == dpiValue) {
                return density;
            }
        }
        return null;
    }

    @Override
    @NonNull
    public String getResourceValue() {
        return mValue;
    }

    public int getDpiValue() {
        return mDpi;
    }

    public int since() {
        return mSince;
    }

    @Override
    @NonNull
    public String getShortDisplayValue() {
        return mDisplayValue;
    }

    @Override
    @NonNull
    public String getLongDisplayValue() {
        return mDisplayValue;
    }

    /**
     * Returns all densities which are recommended and valid for a device.
     */
    @NonNull
    public static Set<Density> getRecommendedValuesForDevice() {
        Set<Density> densities = new HashSet<>(densityByValue.values());
        densities.remove(TV);
        densities.remove(NODPI);
        densities.remove(ANYDPI);
        return densities;
    }

    /**
     * Returns true if this density is relevant for app developers (e.g. a density you should
     * consider providing resources for)
     */
    public boolean isRecommended() {
        return densityByValue.containsValue(this) && this != TV;
    }

    public boolean isValidValueForDevice() {
        return this != NODPI && this != ANYDPI; // nodpi/anydpi is not a valid config for devices.
    }

    @Override
    public boolean isFakeValue() {
        return false;
    }
}
