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

import static com.google.common.base.Preconditions.checkNotNull;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * Represents the version of a target or device.
 * </p>
 * A version is defined by an API level, an optional code name, and an optional extension level.
 * <ul><li>Release versions of the Android platform are identified by their API level (integer), and
 * extension level if present.
 * (technically the code name for release version is "REL" but this class will return
 * <code>null</code> instead.)</li>
 * <li>Preview versions of the platform are identified by a code name. Their API level
 * is usually set to the value of the previous platform.</li></ul>
 * <p>
 * While this class contains all values, its goal is to abstract them, so that code comparing 2+
 * versions doesn't have to deal with the logic of handling all values.
 * </p>
 * <p>
 * There are some cases where ones may want to access the values directly. This can be done
 * with {@link #getApiLevel()}, {@link #getCodename()}, {@link #getExtensionLevel()},
 * and {@link #isBaseExtension()}.
 * </p>
 * For generic UI display of the API version, {@link #getApiString()} is to be used.
 */
public final class AndroidVersion implements Comparable<AndroidVersion>, Serializable {
    /**
     * SDK version codes mirroring ones found in Build#VERSION_CODES on Android.
     */
    @SuppressWarnings("unused")
    public static class VersionCodes {
        public static final int UNDEFINED = 0;
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
        public static final int S_V2 = 32;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
    }

    /**
     * Starting with Android "S", every new release has a base <a
     * href="https://developer.android.com/guide/sdk-extensions">SDK extension</a> version, i.e. the
     * minimum SDK extension version number supported by that release.
     */
    public enum ApiBaseExtension {
        S(31, 1),
        S_V2(32, 1),
        TIRAMISU(33, 3),
        UPSIDE_DOWN_CAKE(34, 7),
        VANILLA_ICE_CREAM(35, 13),
        BAKLAVA(36, 17),
        ;

        private final int myApi;

        private final int myExtension;

        ApiBaseExtension(int api, int extension) {
            myApi = api;
            myExtension = extension;
        }

        public int getApi() {
            return myApi;
        }

        public int getExtension() {
            return myExtension;
        }
    }

    public static final Pattern PREVIEW_PATTERN = Pattern.compile("^[A-Z][0-9A-Za-z_]*$");
    public static final Pattern API_LEVEL_PATTERN =
            Pattern.compile("(\\d+)(\\.(\\d+))?(-ext(\\d+))?");

    private static final long serialVersionUID = 1L;

    private final int mApiLevel;

    private final int mApiMinorLevel;

    @Nullable
    private final String mCodename;
    @Nullable
    private final Integer mExtensionLevel;

    private final boolean mIsBaseExtension;

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

    /** First version to support foldable devices */
    public static final int MIN_FOLDABLE_DEVICE_API = 29;

    /** First version of the Android Emulator system image to support foldable devices */
    public static final int MIN_EMULATOR_FOLDABLE_DEVICE_API = 34;

    /** First version to support freeform display */
    public static final int MIN_FREEFORM_DEVICE_API = 30;

    /** First version to support hinge foldable settings */
    public static final int MIN_HINGE_FOLDABLE_DEVICE_API = 30;

    /** First version to support pixel 4a */
    public static final int MIN_PIXEL_4A_DEVICE_API = 30;

    /** First version to support TV 4K display */
    public static final int MIN_4K_TV_API = 31;

    /** First version to support Resizable device */
    public static final int MIN_RESIZABLE_DEVICE_API = 34;

    /** Last version of Android with supported 32-bit system images. */
    public static final int MAX_32_BIT_API = 30;

    /** First version to support rectangular Wear display */
    public static final int MIN_RECTANGULAR_WEAR_API = 28;

    /**
     * If the apiLevel is at least MIN_MAJOR_WITH_EXPLICIT_MINOR, the version string will always
     * include the minor version, even if it is zero.
     *
     * <p>In other words, the expected sequence of versions currently encoded is:
     *
     * <ul>
     *   <li>35
     *   <li>36 (minor is not included in the version string)
     *   <li>36.1 (the first version with a minor version)
     *   <li>37.0 (minor version is always included in the version string for 37 and above)
     *   <li>37.1
     * </ul>
     *
     * See AndroidVersionTest.testMinorVersionNormalization
     */
    private static final int MIN_API_FOR_EXPLICIT_MINOR = 37;

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
     * Creates an {@link AndroidVersion} with the given api level of a release version (the codename
     * is null).
     */
    public AndroidVersion(int apiLevel) {
        this(apiLevel, null);
    }

    public AndroidVersion(int apiLevel, int apiMinorLevel) {
        this(apiLevel, apiMinorLevel, null, null, true);
    }

    /**
     * Creates an {@link AndroidVersion} with the given api level and codename.
     * Codename should be null for a release version, otherwise it's a preview codename.
     */
    public AndroidVersion(int apiLevel, @Nullable String codename) {
        this(apiLevel, codename, null, true);
    }

    /**
     * Creates an {@link AndroidVersion} with the given api level, codename, and extension level.
     * Codename should be null for a release version, otherwise it's a preview codename.
     */
    public AndroidVersion(int apiLevel,
            @Nullable String codename,
            @Nullable Integer extensionLevel,
            boolean isBaseExtension) {
        this(apiLevel, 0, codename, extensionLevel, isBaseExtension);
    }

    public AndroidVersion(int apiLevel,
            int apiMinorLevel,
            @Nullable String codename,
            @Nullable Integer extensionLevel,
            boolean isBaseExtension) {
        if (!isBaseExtension) {
            checkNotNull(extensionLevel, "extensionLevel required when isBaseExtension is false");
        }
        mApiLevel = apiLevel;
        mApiMinorLevel = apiMinorLevel;
        mCodename = sanitizeCodename(codename);
        mExtensionLevel = extensionLevel;
        mIsBaseExtension = isBaseExtension;
    }

    /**
     * Returns an AndroidVersion with the same API level and codename, and a base extension level,
     * e.g. "33-ext4" would become "33". This will set the extensionLevel property based on the API
     * level if known (although, since it is the base extension level, it will not be rendered in
     * getApiStringWithExtension()).
     */
    public AndroidVersion withBaseExtensionLevel() {
        int baseExtensionLevel = getBaseExtensionLevel(mApiLevel);
        return new AndroidVersion(mApiLevel, mApiMinorLevel, mCodename, baseExtensionLevel <= 0 ? null : baseExtensionLevel, true);
    }

    /**
     * Returns this AndroidVersion with the same API level and codename and the specified extension level,
     * e.g. new AndroidVersion(33).withExtensionLevel(4).getApiStringWithExtension() would be "33-ext4".
     */
    public AndroidVersion withExtensionLevel(int extensionLevel) {
        return new AndroidVersion(mApiLevel, mApiMinorLevel, mCodename, extensionLevel, extensionLevel == getBaseExtensionLevel(mApiLevel));
    }

    /**
     * Creates an {@link AndroidVersion} from a string that may be an integer API level or a string
     * codename. <Em>Important</em>: An important limitation of this method is that it cannot
     * possibly recreate the API level integer from a pure string codename. This is only OK to use
     * if the caller can guarantee that only {@link #getApiString()} will be used later.
     * {@link #getApiLevel()} will return 0.
     *
     * SdkVersionInfo.getVersion() can be used to get a valid AndroidVersion from known codenames,
     * and should be preferred.
     *
     * @param apiString an API string that could have been produced by getApiStringWithExtension()
     * @throws IllegalArgumentException if the input doesn't match API_LEVEL_PATTERN or
     *   PREVIEW_PATTERN
     */
    public static AndroidVersion fromString(@NonNull String apiString) {
        try {
            Matcher matcher = API_LEVEL_PATTERN.matcher(apiString);
            if (matcher.matches()) {
                int majorVersion = Integer.parseInt(matcher.group(1));
                int minorVersion = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
                Integer extensionLevel = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : null;
                boolean isBaseExtension = extensionLevel == null || extensionLevel <= getBaseExtensionLevel(majorVersion);
                return new AndroidVersion(
                        majorVersion, minorVersion,
                        null,
                        extensionLevel,
                        isBaseExtension);
            }
        } catch (NumberFormatException ignore) {}

        String codename = sanitizeCodename(apiString);
        if (codename == null || !PREVIEW_PATTERN.matcher(codename).matches()) {
            throw new IllegalArgumentException("Invalid android API or codename " + apiString);
        }

        return new AndroidVersion(0, codename);
    }

  /**
   * Returns the API major level as an integer.
   *
   * <p>For preview versions, this can be superseded by {@link #getCodename()}.
   *
   * <p>To display the API level in the UI, use {@link #getApiStringWithExtension()} or {@link
   * #getApiStringWithoutExtension()}, which will use the codename if applicable, and include the
   * minor version.
   *
   * @see #getCodename()
   * @see #getApiString()
   */
  public int getApiLevel() {
        return mApiLevel;
    }

    /**
     * Returns the API minor level as an integer.
     */
    public int getApiMinorLevel() {
        return mApiMinorLevel;
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
     * <p>If the codename is non-null, then the API level should be ignored, and this should be
     * used as a unique identifier of the target instead.</p>
     */
    @Nullable
    public String getCodename() {
        return mCodename;
    }

    /**
     * Returns a string representing the API level and/or the code name.
     *
     * <p>Note that this does not handle extension level.
     *
     * @deprecated Use either {@link #getApiStringWithExtension()}
     */
    @NonNull
    @Deprecated
    public String getApiString() {
        return getApiStringWithoutExtension();
    }

    /**
     * Returns a string representing the API level and/or the code name.
     *
     * <p>This does not include the SDK Extension level.
     *
     * @see #getApiStringWithExtension
     */
    @NonNull
    public String getApiStringWithoutExtension() {
        if (mCodename != null) {
            return mCodename;
        }

        return getApiLevelString();
    }

    /**
     * Returns just the API level (major and minor if applicable) as a string.
     */
    @NonNull
    private String getApiLevelString() {
        return mApiLevel >= MIN_API_FOR_EXPLICIT_MINOR || mApiMinorLevel > 0
                ? mApiLevel + "." + mApiMinorLevel
                : Integer.toString(mApiLevel);
    }

    @NonNull
    public String getApiStringWithExtension() {
        if (mCodename != null) {
            return mCodename;
        }

        if (mIsBaseExtension) {
            return getApiStringWithoutExtension();
        }

        return String.format(
                Locale.US, "%1$s-ext%2$d", getApiStringWithoutExtension(), mExtensionLevel);
    }

    /**
     * Returns the extension level if known.
     */
    @Nullable
    public Integer getExtensionLevel() {
        return mExtensionLevel;
    }

    /**
     * Returns whether this AndroidVersion is the base extension for the API level.
     */
    public boolean isBaseExtension() {
        return mIsBaseExtension;
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
        return API_LEVEL_ORDERING.compare(this, appVersion) >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AndroidVersion)) {
            return false;
        }
        AndroidVersion other = (AndroidVersion) obj;
        return mApiLevel == other.mApiLevel
               && mApiMinorLevel == other.mApiMinorLevel
               && Objects.equals(mCodename, other.mCodename)
               && ((mIsBaseExtension && other.mIsBaseExtension) || Objects.equals(mExtensionLevel,
                                                                                  other.mExtensionLevel));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mApiLevel, mApiMinorLevel, mCodename, mIsBaseExtension ? 0 : mExtensionLevel);
    }

    /**
     * Returns a string with the API Level and optional codename. Intended for debugging. For
     * display purposes, please use {@code AndroidVersionUtils.getDisplayApiString(AndroidVersion)}
     * or {@code AndroidVersionUtils.getFullApiName}
     */
    @Override
    public String toString() {
        String s = "API " + getApiLevelString();
        if (isPreview()) {
            s += String.format(Locale.US, ", %1$s preview", mCodename);
        }
        if (mExtensionLevel != null) {
            s += String.format(Locale.US, ", extension level %1$s", mExtensionLevel);
        }
        return s;
    }

    /** Comparator that looks at API level and codename only, not extension level. */
    public static final Comparator<AndroidVersion> API_LEVEL_ORDERING =
            comparing(AndroidVersion::getApiLevel)
                    .thenComparing(AndroidVersion::getApiMinorLevel)
                    .thenComparing(AndroidVersion::getCodename, nullsFirst(naturalOrder()));

    /** Comparator used to implement the natural order for this class. */
    private static final Comparator<AndroidVersion> ORDERING =
            API_LEVEL_ORDERING
                    .thenComparing(AndroidVersion::getNonBaseExtensionLevel, nullsFirst(naturalOrder()));

    @Override
    public int compareTo(@NonNull AndroidVersion o) {
        return ORDERING.compare(this, o);
    }

    @Nullable
    private Integer getNonBaseExtensionLevel() {
        // The presence or absence of an extension level is irrelevant if both
        // AndroidVersions are the base extension. We assume that if an AndroidVersion has an
        // extension level specified, it is at least equal to the base extension level.
        return isBaseExtension() ? null : getExtensionLevel();
    }

    /**
     * Returns true if this version is equal to or newer than the given API level.
     */
    public boolean isAtLeast(int apiLevel) {
        return isAtLeast(apiLevel, null);
    }

    /**
     * Returns true if this version is equal to or newer than the given API level. If a codename is
     * given, then this version must also either be strictly greater than the given api level,
     * or must have a codename that is greater than the given codename (by string comparison).
     *
     * This is typically used to check if a version is at least a preview for a certain API level,
     * e.g. to check if this version contains "O" APIs: isAtLeast(VersionCodes.O - 1, "O")
     */
    public boolean isAtLeast(int apiLevel, @Nullable String codename) {
        return compareTo(new AndroidVersion(apiLevel, codename)) >= 0;
    }

    /**
     * Compares this version with the specified API and returns true if this version
     * is greater or equal than the requested API -- that is the current version is a
     * suitable min-api-level for the argument API.
     *
     * @deprecated use isAtLeast
     */
    @Deprecated
    public boolean isGreaterOrEqualThan(int api) {
        return isAtLeast(api);
    }

    /**
     * Returns the base extension level of the given API version, i.e. the extension level at
     * release.
     */
    public static int getBaseExtensionLevel(int api) {

        ApiBaseExtension[] values = ApiBaseExtension.values();
        for (ApiBaseExtension value : values) {
            if (value.getApi() == api) {
                return value.getExtension();
            }
        }
        return 0;
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
