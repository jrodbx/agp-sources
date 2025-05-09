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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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

    /** Native ABIs (x86, armeabi-v7a, etc.) of the system image. */
    private final List<String> mAbis;

    /** Translated ABIs (x86, armeabi-v7a, etc.) of the system image. */
    private final List<String> mTranslatedAbis;

    /** Skins contained in this system image, or in the platform/addon it's based on. */
    private final ImmutableList<Path> mSkins;

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
            @NonNull List<String> abis,
            @NonNull List<String> translatedAbis,
            @NonNull List<Path> skins,
            @NonNull RepoPackage pkg) {
        Preconditions.checkArgument(!abis.isEmpty());
        mLocation = location;
        mTags = ImmutableList.copyOf(tags);
        mVendor = vendor;
        mAbis = ImmutableList.copyOf(abis);
        mTranslatedAbis = ImmutableList.copyOf(translatedAbis);
        mSkins = ImmutableList.copyOf(skins);
        mPackage = pkg;
        TypeDetails details = pkg.getTypeDetails();
        assert details instanceof DetailsTypes.ApiDetailsType;
        mAndroidVersion = ((DetailsTypes.ApiDetailsType) details).getAndroidVersion();
    }

    public SystemImage(
            @NonNull Path location,
            @NonNull IdDisplay tag,
            @Nullable IdDisplay vendor,
            @NonNull List<String> abi,
            @NonNull List<String> translatedAbi,
            @NonNull List<Path> skins,
            @NonNull RepoPackage pkg) {
        this(location, ImmutableList.of(tag), vendor, abi, translatedAbi, skins, pkg);
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
    public String getPrimaryAbiType() {
        return mAbis.get(0);
    }

    @NonNull
    @Override
    public List<String> getAbiTypes() {
        return mAbis;
    }

    @NonNull
    @Override
    public List<String> getTranslatedAbiTypes() {
        return mTranslatedAbis;
    }

    @NonNull
    @Override
    public List<Path> getSkins() {
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
    public boolean equals(@Nullable Object object) {
        if (!(object instanceof SystemImage)) {
            return false;
        }

        SystemImage image = (SystemImage) object;

        return mLocation.equals(image.mLocation)
                && mTags.equals(image.mTags)
                && Objects.equals(mVendor, image.mVendor)
                && mAbis.equals(image.mAbis)
                && mTranslatedAbis.equals(image.mTranslatedAbis)
                && mSkins.equals(image.mSkins)
                && mAndroidVersion.equals(image.mAndroidVersion)
                && mPackage.equals(image.mPackage);
    }

    public int hashCode() {
        return Objects.hash(
                mLocation,
                mTags,
                mVendor,
                mAbis,
                mTranslatedAbis,
                mSkins,
                mAndroidVersion,
                mPackage);
    }

    @NonNull
    @Override
    public String toString() {
        return mPackage.getDisplayName();
    }

    @NonNull
    @Override
    public Revision getRevision() {
        return mPackage.getVersion();
    }
}
