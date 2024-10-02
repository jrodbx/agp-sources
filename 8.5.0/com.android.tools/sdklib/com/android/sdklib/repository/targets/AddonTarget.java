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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.PackageParserUtils;
import com.android.sdklib.repository.legacy.LegacyRepoUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.Library;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Represents an add-on target in the SDK. An add-on extends a standard {@link PlatformTarget}.
 */
public class AddonTarget implements IAndroidTarget {
    /** The {@link LocalPackage} from which this target was created. */
    private final LocalPackage mPackage;

    /** The {@link TypeDetails} of {@link #mPackage}. */
    private final DetailsTypes.AddonDetailsType mDetails;

    /** The target on which this addon is based. */
    private final IAndroidTarget mBasePlatform;

    /**
     * All skins included in this target, including those in this addon, the base package, and
     * associated system images.
     */
    private final Path[] mSkins;

    /** The default skin for this package, as (optionally) specified in the package xml. */
    private final Path mDefaultSkin;

    private final List<OptionalLibrary> mAdditionalLibraries;

    /**
     * Constructs a new {@link AddonTarget}.
     *
     * @param p The {@link LocalPackage} containing this target.
     * @param baseTarget The {@link IAndroidTarget} on which this addon is based.
     */
    public AddonTarget(
            @NonNull LocalPackage p,
            @NonNull IAndroidTarget baseTarget,
            @NonNull ProgressIndicator progress) {
        mPackage = p;
        mBasePlatform = baseTarget;
        TypeDetails details = p.getTypeDetails();
        assert details instanceof DetailsTypes.AddonDetailsType;
        mDetails = (DetailsTypes.AddonDetailsType) details;

        // Gather skins for this target. We'll only keep a single skin with each name.
        Map<String, Path> skins = Maps.newHashMap();
        // Collect skins from the base target. This have precedence over system image skins with the
        // same name.
        for (Path skin : baseTarget.getSkins()) {
            skins.put(skin.getFileName().toString(), skin);
        }
        // Finally collect skins from this package itself, which have highest priority.
        for (Path skin :
                PackageParserUtils.parseSkinFolder(
                        p.getLocation().resolve(SdkConstants.FD_SKINS))) {
            skins.put(skin.getFileName().toString(), skin);
        }
        mSkins = skins.values().toArray(new Path[0]);

        String defaultSkinName = mDetails.getDefaultSkin();
        if (defaultSkinName != null) {
            mDefaultSkin = getPath(SKINS).resolve(defaultSkinName);
        } else {
            // No default skin name specified, use the first one from the addon
            // or the default from the platform.
            if (getSkins().length == 1) {
                mDefaultSkin = getSkins()[0];
            } else {
                mDefaultSkin = mBasePlatform.getDefaultSkin();
            }
        }

        mAdditionalLibraries = parseAdditionalLibraries(p, progress);
    }

    @NonNull
    private static List<OptionalLibrary> parseAdditionalLibraries(
            @NonNull LocalPackage p, @NonNull ProgressIndicator progress) {
        DetailsTypes.AddonDetailsType.Libraries libraries = ((DetailsTypes.AddonDetailsType) p
                .getTypeDetails()).getLibraries();
        List<OptionalLibrary> result = Lists.newArrayList();
        if (libraries != null) {
            for (Library library : libraries.getLibrary()) {
                if (library.getLocalJarPath() == null) {
                    // We must be looking at a legacy package. Abort and use the libraries derived
                    // in the old way.
                    return LegacyRepoUtils.parseLegacyAdditionalLibraries(
                            p.getLocation(), progress);
                }
                library.setPackagePath(p.getLocation());
                result.add(library);
            }
        }

        return result;
    }

    @Override
    @NonNull
    public String getLocation() {
        return mPackage.getLocation().toString() + File.separator;
    }

    @Override
    public String getVendor() {
        return mDetails.getVendor().getDisplay();
    }

    @Override
    public String getName() {
        return mDetails.getTag().getDisplay();
    }

    @Override
    public String getFullName() {
        return mPackage.getDisplayName();
    }

    @Override
    public String getClasspathName() {
        return String.format("%1$s [%2$s]", getName(), mBasePlatform.getClasspathName());
    }

    @Override
    public String getShortClasspathName() {
        return String.format("%1$s [%2$s]", getName(), mBasePlatform.getVersionName());
    }

    @NonNull
    @Override
    public AndroidVersion getVersion() {
        return mDetails.getAndroidVersion();
    }

    @Override
    public String getVersionName() {
        return mBasePlatform.getVersionName();
    }

    @Override
    public int getRevision() {
        return mPackage.getVersion().getMajor();
    }

    @Override
    public boolean isPlatform() {
        return false;
    }

    @Override
    public IAndroidTarget getParent() {
        return mBasePlatform;
    }

    @Override
    @NonNull
    public Path getPath(int pathId) {
        Path installPath = mPackage.getLocation();
        switch (pathId) {
            case SKINS:
                return installPath.resolve(SdkConstants.OS_SKINS_FOLDER);
            case DOCS:
                return installPath
                        .resolve(SdkConstants.FD_DOCS)
                        .resolve(SdkConstants.FD_DOCS_REFERENCE);

            default:
                return mBasePlatform.getPath(pathId);
        }
    }

    @Override
    public BuildToolInfo getBuildToolInfo() {
        return mBasePlatform.getBuildToolInfo();
    }

    @Override
    @NonNull
    public List<String> getBootClasspath() {
        return mBasePlatform.getBootClasspath();
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getOptionalLibraries() {
        return mBasePlatform.getOptionalLibraries();
    }

    @Override
    @NonNull
    public List<OptionalLibrary> getAdditionalLibraries() {
        return mAdditionalLibraries;
    }

    @Override
    public boolean hasRenderingLibrary() {
        return false;
    }

    @Override
    @NonNull
    public Path[] getSkins() {
        return mSkins;
    }

    @Override
    @Nullable
    public Path getDefaultSkin() {
        return mDefaultSkin;
    }

    @Override
    public String[] getPlatformLibraries() {
        return mBasePlatform.getPlatformLibraries();
    }

    @Override
    public String getProperty(String name) {
        return mBasePlatform.getProperty(name);
    }

    @Override
    public Map<String, String> getProperties() {
        return mBasePlatform.getProperties();
    }

    @Override
    public boolean canRunOn(IAndroidTarget target) {
        // basic test
        if (target == this) {
            return true;
        }

        // The receiver is an add-on. There are 2 big use cases: The add-on has libraries
        // or the add-on doesn't (in which case we consider it a platform).
        if (!getAdditionalLibraries().isEmpty()) {
            // the only targets that can run the receiver are the same add-on in the same or later
            // versions.
            // first check: vendor/name
            if (!getVendor().equals(target.getVendor()) || !getName().equals(target.getName())) {
                return false;
            }

            // now check the version. At this point since we checked the add-on part,
            // we can revert to the basic check on version/codename which are done by the
            // base platform already.
        }
        return mBasePlatform.canRunOn(target);
    }

    @Override
    public String hashString() {
        return getVendor() + ":" + getName() + ":" + mBasePlatform.getVersion().getApiString();
    }

    @Override
    public int compareTo(@NonNull IAndroidTarget target) {
        // Quick check.
        if (this == target) {
            return 0;
        }

        int versionDiff = getVersion().compareTo(target.getVersion());

        // Only if the versions are the same do we care about platform/add-ons.
        if (versionDiff == 0) {
            // Platforms go before add-ons.
            if (target.isPlatform()) {
                return 1;
            } else {
                AddonTarget targetAddOn = (AddonTarget) target;

                // Both are add-ons of the same version. Compare per vendor then by name.
                int vendorDiff = getVendor().compareTo(targetAddOn.getVendor());
                if (vendorDiff == 0) {
                    return getName().compareTo(targetAddOn.getName());
                } else {
                    return vendorDiff;
                }
            }

        }

        return versionDiff;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AddonTarget && compareTo((AddonTarget)obj) == 0;
    }

    @Override
    public int hashCode() {
        return hashString().hashCode();
    }
}
