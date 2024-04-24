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
package com.android.sdklib.repository.targets;

import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.base.Objects;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * {@link ISystemImage} based on a {@link RepoPackage} (either system image, platform, or addon).
 */
public class SystemImage implements ISystemImage {

    /** Directory containing the system image. */
    private final Path mLocation;

    /**
     * Tag of the system image. Used for matching addons and system images, and for filtering.
     */
    private final IdDisplay mTag;

    /**
     * Vendor of the system image.
     */
    private final IdDisplay mVendor;


    /**
     * Abi (x86, armeabi-v7a, etc) of the system image.
     */
    private final String mAbi;

    /** Skins contained in this system image, or in the platform/addon it's based on. */
    private final Path[] mSkins;

    /**
     * Android API level of this system image.
     */
    private final AndroidVersion mAndroidVersion;

    /**
     * {@link RepoPackage} that contains this system image.
     */
    private final RepoPackage mPackage;

    public SystemImage(
            @NonNull Path location,
            @Nullable IdDisplay tag,
            @Nullable IdDisplay vendor,
            @NonNull String abi,
            @NonNull Path[] skins,
            @NonNull RepoPackage pkg) {
        mLocation = location;
        mTag = tag;
        mVendor = vendor;
        mAbi = abi;
        mSkins = skins;
        mPackage = pkg;
        TypeDetails details = pkg.getTypeDetails();
        assert details instanceof DetailsTypes.ApiDetailsType;
        mAndroidVersion = ((DetailsTypes.ApiDetailsType) details).getAndroidVersion();
    }

    @NonNull
    @Override
    public Path getLocation() {
        return mLocation;
    }

    @NonNull
    @Override
    public IdDisplay getTag() {
        return mTag;
    }

    @Nullable
    @Override
    public IdDisplay getAddonVendor() {
        return mVendor;
    }

    @NonNull
    @Override
    public String getAbiType() {
        return mAbi;
    }

    @NonNull
    @Override
    public Path[] getSkins() {
        return mSkins;
    }

    @Override
    @NonNull
    public AndroidVersion getAndroidVersion() {
        return mAndroidVersion;
    }

    @NonNull
    @Override
    public RepoPackage getPackage() {
        return mPackage;
    }

    @Override
    public boolean obsolete() {
        return mPackage.obsolete();
    }

    @Override
    public boolean hasPlayStore() {
        if (PLAY_STORE_TAG.equals(getTag()) || AUTOMOTIVE_PLAY_STORE_TAG.equals(getTag())) {
            return true;
        }
        // A Wear system image has Play Store if it is
        // a recent API version and is NOT Wear-for-China.
        if (WEAR_TAG.equals(getTag())
                && mAndroidVersion.getApiLevel() >= AndroidVersion.MIN_RECOMMENDED_WEAR_API
                && !getPackage().getPath().contains(WEAR_CN_DIRECTORY)) {
            return true;
        }
        return false;
    }

    @Override
    public int compareTo(@NonNull ISystemImage o) {
        int res =
                Comparator.comparing(ISystemImage::getTag)
                        .thenComparing(ISystemImage::getAbiType)
                        .thenComparing(
                                ISystemImage::getAddonVendor,
                                Comparator.nullsFirst(IdDisplay::compareTo))
                        .thenComparing(ISystemImage::getLocation)
                        .compare(this, o);

        if (res != 0) {
            return res;
        }
        Path[] skins = getSkins();
        Path[] otherSkins = o.getSkins();
        for (int i = 0; i < skins.length && i < otherSkins.length; i++) {
            res = skins[i].compareTo(otherSkins[i]);
            if (res != 0) {
                return res;
            }
        }
        return skins.length - otherSkins.length;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SystemImage)) {
            return false;
        }
        return compareTo((SystemImage) o) == 0;
    }

    public int hashCode() {
        int hashCode = Objects.hashCode(getTag(), getAbiType(), getAddonVendor(), getLocation());
        for (Path f : getSkins()) {
            hashCode *= 37;
            hashCode += f.hashCode();
        }
        return hashCode;
    }

    @NonNull
    @Override
    public Revision getRevision() {
        return mPackage.getVersion();
    }
}
