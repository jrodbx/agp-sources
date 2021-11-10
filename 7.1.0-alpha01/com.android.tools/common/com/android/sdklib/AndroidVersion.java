/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Objects;
import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <p>
 * Represents the version of a target or device.
 * </p>
 * A version is defined by an API level and an optional code name.
 * <ul><li>Release versions of the Android platform are identified by their API level (integer),
 * (technically the code name for release version is "REL" but this class will return
 * <code>null</code> instead.)</li>
 * <li>Preview versions of the platform are identified by a code name. Their API level
 * is usually set to the value of the previous platform.</li></ul>
 * <p>
 * While this class contains both values, its goal is to abstract them, so that code comparing 2+
 * versions doesn't have to deal with the logic of handle both values.
 * </p>
 * <p>
 * There are some cases where ones may want to access the values directly. This can be done
 * with {@link #getApiLevel()} and {@link #getCodename()}.
 * </p>
 * For generic UI display of the API version, {@link #getApiString()} is to be used.
 */
public final class AndroidVersion implements Comparable<AndroidVersion>, Serializable {
    /**
     * SDK version codes mirroring ones found in Build#VERSION_CODES on Android.
     */
    public static class VersionCodes {
        public static final int BASE = 1;
        public static final int BASE_1_1 = 2;
        public static final int CUPCAKE = 3;
        public static final int DONUT = 4;
        public static final int ECLAIR = 5;
        public static final int ECLAIR_0_1 = 6;
        public static final int ECLAIR_MR1 = 7;
        public static final int FROYO = 8;
        public static final int GINGERBREAD = 9;
        public static final int GINGERBREAD_MR1 = 10;
        public static final int HONEYCOMB = 11;
        public static final int HONEYCOMB_MR1 = 12;
        public static final int HONEYCOMB_MR2 = 13;
        public static final int ICE_CREAM_SANDWICH = 14;
        public static final int ICE_CREAM_SANDWICH_MR1 = 15;
        public static final int JELLY_BEAN = 16;
        public static final int JELLY_BEAN_MR1 = 17;
        public static final int JELLY_BEAN_MR2 = 18;
        public static final int KITKAT = 19;
        public static final int KITKAT_WATCH = 20;
        public static final int LOLLIPOP = 21;
        public static final int LOLLIPOP_MR1 = 22;
        public static final int M = 23;
        public static final int N = 24;
        public static final int N_MR1 = 25;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
    }

    private static final long serialVersionUID = 1L;

    private final int mApiLevel;
    @Nullable
    private final String mCodename;

    /** The default AndroidVersion for minSdkVersion and targetSdkVersion if not specified. */
    public static final AndroidVersion DEFAULT = new AndroidVersion(1, null);

    /** First version to use ART by default. */
    public static final AndroidVersion ART_RUNTIME = new AndroidVersion(21, null);

    /** First version to support 64-bit ABIs. */
    public static final AndroidVersion SUPPORTS_64_BIT = new AndroidVersion(VersionCodes.LOLLIPOP, null);

    /** First version to feature binder's common interface "cmd" for sending shell commands to services. */
    public static final AndroidVersion BINDER_CMD_AVAILABLE = new AndroidVersion(24, null);

    /** First version to allow split apks */
    public static final AndroidVersion ALLOW_SPLIT_APK_INSTALLATION = new AndroidVersion(21, null);

    /** First version to have multi-user support (JB-MR2, API 17) */
    public static final AndroidVersion SUPPORTS_MULTI_USER = new AndroidVersion(17, null);

    /** Minimum API versions that are recommended for use in testing apps */
    public static final int MIN_RECOMMENDED_API = 22;
    public static final int MIN_RECOMMENDED_WEAR_API = 25;

    /** Frist version to support Foldable device */
    public static final int MIN_FOLDABLE_DEVICE_API = 29;

    /** First version to support freeform display */
    public static final int MIN_FREEFORM_DEVICE_API = 30;

    /** First version to support hinge foldable settings */
    public static final int MIN_HINGE_FOLDABLE_DEVICE_API = 30;

    /** First version to support pixel 4a */
    public static final int MIN_PIXEL_4A_DEVICE_API = 30;

    /**
     * Thrown when an {@link AndroidVersion} object could not be created.
     */
    public static final class AndroidVersionException extends Exception {
        private static final long serialVersionUID = 1L;

        public AndroidVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates an {@link AndroidVersion} with the given api level and codename.
     * Codename should be null for a release version, otherwise it's a preview codename.
     */
    public AndroidVersion(int apiLevel, @Nullable String codename) {
        mApiLevel = apiLevel;
        mCodename = sanitizeCodename(codename);
    }

    /**
     * Creates an {@link AndroidVersion} with the given api level of a release version (the codename
     * is null).
     */
    public AndroidVersion(int apiLevel) {
        this(apiLevel, null);
    }

    /**
     * Creates an {@link AndroidVersion} from a string that may be an integer API level or a string
     * codename. <Em>Important</em>: An important limitation of this method is that cannot possible
     * recreate the API level integer from a pure string codename. This is only OK to use if the
     * caller can guarantee that only {@link #getApiString()} will be used later. Wrong things will
     * happen if the caller then tries to resolve the numeric {@link #getApiLevel()}.
     *
     * @param apiOrCodename A non-null API integer or a codename in its "ALL_CAPS" format. "REL" is
     *     notable not a valid codename.
     * @throws AndroidVersionException if the input isn't a pure integer or doesn't look like a
     *     valid string codename.
     */
    public AndroidVersion(@NonNull String apiOrCodename) throws AndroidVersionException {
        int apiLevel = 0;
        String codename = null;
        try {
            apiLevel = Integer.parseInt(apiOrCodename);
        } catch (NumberFormatException ignore) {
            // We don't know the API level. Android platform codenames are all caps.
            // REL is a release-reserved keyword which we can use here.

            if (!SdkConstants.CODENAME_RELEASE.equals(apiOrCodename)) {
                if (Pattern.matches("[A-Z_]+", apiOrCodename)) {
                    codename = apiOrCodename;
                }
            }
        }

        mApiLevel = apiLevel;
        mCodename = sanitizeCodename(codename);

        if (mApiLevel <= 0 && codename == null) {
            throw new AndroidVersionException(
                    "Invalid android API or codename " + apiOrCodename,     //$NON-NLS-1$
                    null);
        }
    }

    /**
     * Returns the api level as an integer.
     * <p>For target that are in preview mode, this can be superseded by
     * {@link #getCodename()}.</p>
     * <p>To display the API level in the UI, use {@link #getApiString()}, which will use the
     * codename if applicable.</p>
     * @see #getCodename()
     * @see #getApiString()
     */
    public int getApiLevel() {
        return mApiLevel;
    }

    /**
     * Returns the API level as an integer. If this is a preview platform, it
     * will return the expected final version of the API rather than the current API
     * level. This is the "feature level" as opposed to the "release level" returned by
     * {@link #getApiLevel()} in the sense that it is useful when you want
     * to check the presence of a given feature from an API, and we consider the feature
     * present in preview platforms as well.
     *
     * @return the API level of this version, +1 for preview platforms
     */
    public int getFeatureLevel() {
        //noinspection VariableNotUsedInsideIf
        return mCodename != null ? mApiLevel + 1 : mApiLevel;
    }

    /**
     * Returns the version code name if applicable, null otherwise.
     * <p>If the codename is non null, then the API level should be ignored, and this should be
     * used as a unique identifier of the target instead.</p>
     */
    @Nullable
    public String getCodename() {
        return mCodename;
    }

    /**
     * Returns a string representing the API level and/or the code name.
     */
    @NonNull
    public String getApiString() {
        if (mCodename != null) {
            return mCodename;
        }

        return Integer.toString(mApiLevel);
    }

    /**
     * Returns whether or not the version is a preview version.
     */
    public boolean isPreview() {
        return mCodename != null;
    }

    /** Checks if the version is having legacy multidex support. */
    public boolean isLegacyMultidex() {
        return this.getFeatureLevel() < 21;
    }

    /**
     * Checks whether a device running a version similar to the receiver can run a project compiled
     * for the given <var>version</var>.
     * <p>
     * Be aware that this is not a perfect test, as other properties could break compatibility
     * despite this method returning true.
     * </p>
     * <p>
     * Nevertheless, when testing if an application can run on a device (where there is no
     * access to the list of optional libraries), this method can give a good indication of whether
     * there is a chance the application could run, or if there's a direct incompatibility.
     * </p>
     */
    public boolean canRun(@NonNull AndroidVersion appVersion) {
        // if the application is compiled for a preview version, the device must be running exactly
        // the same.
        if (appVersion.mCodename != null) {
            return appVersion.mCodename.equals(mCodename);
        }

        // otherwise, we check the api level (note that a device running a preview version
        // will have the api level of the previous platform).
        return mApiLevel >= appVersion.mApiLevel;
    }

    /**
     * Returns <code>true</code> if the AndroidVersion is an API level equals to
     * <var>apiLevel</var>.
     */
    public boolean equals(int apiLevel) {
        return mCodename == null && apiLevel == mApiLevel;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AndroidVersion)) {
            return false;
        }
        AndroidVersion other = (AndroidVersion) obj;
        return mApiLevel == other.mApiLevel && Objects.equal(mCodename, other.mCodename);
    }

    public int hashCode() {
        if (mCodename != null) {
            return mCodename.hashCode();
        }

        // there may be some collisions between the hashcode of the codename and the api level
        // but it's acceptable.
        return mApiLevel;
    }

    /**
     * Returns a string with the API Level and optional codename.
     * Useful for debugging.
     * For display purpose, please use {@link #getApiString()} instead.
     */
    @Override
    public String toString() {
        String s = String.format(Locale.US, "API %1$d", mApiLevel); // $NON-NLS-1$
        if (isPreview()) {
            s += String.format(Locale.US, ", %1$s preview", mCodename); // $NON-NLS-1$
        }
        return s;
    }

    /**
     * Compares this object with the specified object for order. Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * @param o the Object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is
     *         less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(@NonNull AndroidVersion o) {
        return compareTo(o.mApiLevel, o.mCodename);
    }

    public int compareTo(int apiLevel, @Nullable String codename) {
        if (mCodename == null) {
            if (codename != null) {
                if (mApiLevel == apiLevel) {
                    return -1; // same api level but argument is a preview for next version
                }
            }
            return mApiLevel - apiLevel;
        } else {
            // 'this' is a preview
            if (mApiLevel == apiLevel) {
                if (codename == null) {
                    return +1;
                } else {
                    return mCodename.compareTo(codename);    // strange case where the 2 previews
                                                             // have different codename?
                }
            } else {
                return mApiLevel - apiLevel;
            }
        }
    }

    /**
     * Compares this version with the specified API and returns true if this version
     * is greater or equal than the requested API -- that is the current version is a
     * suitable min-api-level for the argument API.
     */
    public boolean isGreaterOrEqualThan(int api) {
        return compareTo(api, null /*codename*/) >= 0;
    }

    /**
     * Sanitizes the codename string according to the following rules:
     * - A codename should be {@code null} for a release version or it should be a non-empty
     *   string for an actual preview.
     * - In input, spacing is trimmed since it is irrelevant.
     * - An empty string or the special codename "REL" means a release version
     *   and is converted to {@code null}.
     *
     * @param codename A possible-null codename.
     * @return Null for a release version or a non-empty codename.
     */
    @Nullable
    private static String sanitizeCodename(@Nullable String codename) {
        if (codename != null) {
            codename = codename.trim();
            if (codename.isEmpty() || SdkConstants.CODENAME_RELEASE.equals(codename)) {
                codename = null;
            }
        }
        return codename;
    }
}
