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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.IdDisplay;

import java.nio.file.Path;
import java.util.List;

/**
 * Describes a system image as used by an {@link IAndroidTarget}. A system image has an installation
 * path, a location type and an ABI type.
 */
public interface ISystemImage {
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

    /** Returns the vendor for an add-on system image, or null for a platform system-image. */
    @Nullable
    IdDisplay getAddonVendor();

    /**
     * Returns the primary natively supported ABI type. See {@link Abi} for a full list of valid
     * values. Cannot be null nor empty.
     */
    @NonNull
    String getPrimaryAbiType();

    /**
     * Returns the natively supported ABI types. See {@link Abi} for a full list of valid values.
     * Cannot be null nor empty.
     */
    @NonNull
    List<String> getAbiTypes();

    /**
     * Returns the supported translated ABI types. See {@link Abi} for a full list of valid values.
     * Cannot be null.
     */
    @NonNull
    List<String> getTranslatedAbiTypes();

    /**
     * Returns the skins embedded in the system image. Only supported by images in the SDK
     * system-images directory. The skins listed here are merged in the {@link
     * IAndroidTarget#getSkins()} list.
     *
     * @return A non-null skin list, possibly empty.
     */
    @NonNull
    List<Path> getSkins();

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
        return SystemImageTags.hasGooglePlay(getTags(), getAndroidVersion(), getPackage());
    }

    /** The sdk package containing this system image. */
    @NonNull
    RepoPackage getPackage();
}
