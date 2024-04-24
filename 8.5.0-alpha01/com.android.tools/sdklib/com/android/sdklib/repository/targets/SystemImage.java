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
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * {@link ISystemImage} based on a {@link RepoPackage} (either system image, platform, or addon).
 */
public class SystemImage implements ISystemImage {

    /** Directory containing the system image. */
    private final Path mLocation;

    /** Tag of the system image. Used for matching addons and system images, and for filtering. */
    private final ImmutableList<IdDisplay> mTags;

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
            @NonNull Iterable<IdDisplay> tags,
            @Nullable IdDisplay vendor,
            @NonNull String abi,
            @NonNull Path[] skins,
            @NonNull RepoPackage pkg) {
        mLocation = location;
        mTags = ImmutableList.copyOf(tags);
        mVendor = vendor;
        mAbi = abi;
        mSkins = skins;
        mPackage = pkg;
        TypeDetails details = pkg.getTypeDetails();
        assert details instanceof DetailsTypes.ApiDetailsType;
        mAndroidVersion = ((DetailsTypes.ApiDetailsType) details).getAndroidVersion();
    }

    public SystemImage(
            @NonNull Path location,
            @NonNull IdDisplay tag,
            @Nullable IdDisplay vendor,
            @NonNull String abi,
            @NonNull Path[] skins,
            @NonNull RepoPackage pkg) {
        this(location, ImmutableList.of(tag), vendor, abi, skins, pkg);
    }

    @NonNull
    @Override
    public Path getLocation() {
        return mLocation;
    }

    @NonNull
    @Override
    public List<IdDisplay> getTags() {
        return mTags;
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
