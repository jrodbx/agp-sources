/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.IdDisplay;
import java.nio.file.Path;
import java.util.List;

/**
 * Describes a system image as used by an {@link IAndroidTarget}.
 * A system image has an installation path, a location type and an ABI type.
 */
public interface ISystemImage extends Comparable<ISystemImage> {

    /** Indicates the type of location for the system image folder in the SDK. */
    enum LocationType {
        /**
         * The system image is located in the legacy platform's {@link SdkConstants#FD_IMAGES}
         * folder.
         * <p>
         * Used by both platform and add-ons.
         */
        IN_LEGACY_FOLDER,

        /**
         * The system image is located in a sub-directory of the platform's
         * {@link SdkConstants#FD_IMAGES} folder, allowing for multiple system
         * images within the platform.
         * <p>
         * Used by both platform and add-ons.
         */
        IN_IMAGES_SUBFOLDER,

        /**
         * The system image is located in the new SDK's {@link SdkConstants#FD_SYSTEM_IMAGES}
         * folder. Supported as of Tools R14 and Repository XSD version 5.
         * <p>
         * Used <em>only</em> by both platform up to Tools R22.6.
         * Supported for add-ons as of Tools R22.8.
         */
        IN_SYSTEM_IMAGE,
    }

    /** A Wear-for-China images must reside a package with this string in its path. */
    String WEAR_CN_DIRECTORY = "android-wear-cn";

    /** Returns the actual location of an installed system image. */
    @NonNull
    Path getLocation();

    /**
     * Returns the first tag of the system image.
     *
     * @deprecated Images may have multiple tags; use getTags() instead
     */
    @Deprecated
    @NonNull
    default IdDisplay getTag() {
        return getTags().stream().findFirst().orElse(SystemImageTags.DEFAULT_TAG);
    }

    /** Returns an immutable list containing the tags of the system image. May be empty. */
    @NonNull
    List<IdDisplay> getTags();

    /** Returns the vendor for an add-on's system image, or null for a platform system-image. */
    @Nullable
    IdDisplay getAddonVendor();

    /**
     * Returns the ABI type.
     * See {@link Abi} for a full list.
     * Cannot be null nor empty.
     */
    @NonNull
    String getAbiType();

    /**
     * Returns the skins embedded in the system image. <br>
     * Only supported by system images using {@link LocationType#IN_SYSTEM_IMAGE}. <br>
     * The skins listed here are merged in the {@link IAndroidTarget#getSkins()} list.
     *
     * @return A non-null skin list, possibly empty.
     */
    @NonNull
    Path[] getSkins();

    /**
     * Returns the revision of this system image.
     */
    @NonNull
    Revision getRevision();

    /**
     * Returns the {@link AndroidVersion} of this system image.
     */
    @NonNull
    AndroidVersion getAndroidVersion();

    /**
     * Returns true if this system image is obsolete.
     */
    boolean obsolete();

    /** Returns true if this system image contains Google APIs. */
    default boolean hasGoogleApis() {
        return SystemImageTags.hasGoogleApi(getTags());
    }

    /** Returns true if this system image supports Google Play Store. */
    default boolean hasPlayStore() {
        // Multi-tagged packages only need this check
        if (getTags().contains(PLAY_STORE_TAG)) {
            return true;
        }
        // Fallback logic for old non-multi-tagged packages
        if (AUTOMOTIVE_PLAY_STORE_TAG.equals(getTag())) {
            return true;
        }
        // A Wear system image has Play Store if it is
        // a recent API version and is NOT Wear-for-China.
        if (WEAR_TAG.equals(getTag())
                && getAndroidVersion().getApiLevel() >= AndroidVersion.MIN_RECOMMENDED_WEAR_API
                && !getPackage().getPath().contains(WEAR_CN_DIRECTORY)) {
            return true;
        }
        return false;
    }

    /** The sdk package containing this system image. */
    @NonNull
    RepoPackage getPackage();
}
