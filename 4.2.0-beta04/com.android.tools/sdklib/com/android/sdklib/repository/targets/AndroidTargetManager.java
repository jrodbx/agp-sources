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
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOp;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * Finds and allows access to all {@link IAndroidTarget}s in a given SDK.
 */
public class AndroidTargetManager {

    /**
     * Cache of the {@link IAndroidTarget}s we created from platform and addon packages.
     */
    private Map<LocalPackage, IAndroidTarget> mTargets;

    private final FileOp mFop;

    private final AndroidSdkHandler mSdkHandler;

    /**
     * Map of package paths to errors encountered while loading creating the target.
     */
    private Map<String, String> mLoadErrors;

    private static final Comparator<LocalPackage> TARGET_COMPARATOR =
      Comparator
        .<LocalPackage, AndroidVersion>comparing(localPackage ->
          ((DetailsTypes.ApiDetailsType) localPackage.getTypeDetails()).getAndroidVersion())
        .thenComparing(LocalPackage::getPath)
        .thenComparing(localPackage -> localPackage.getTypeDetails().getClass().getName());

    /**
     * Create a manager using the new {@link AndroidSdkHandler}/{@link RepoManager} mechanism for
     * finding packages.
     */
    public AndroidTargetManager(@NonNull AndroidSdkHandler handler, @NonNull FileOp fop) {
        mSdkHandler = handler;
        mFop = fop;
    }

    /**
     * Returns the targets (platforms and addons) that are available in the SDK, sorted in
     * ascending order by API level.
     */
    @NonNull
    public Collection<IAndroidTarget> getTargets(@NonNull ProgressIndicator progress) {
        return getTargetMap(progress).values();
    }

    @NonNull
    private Map<LocalPackage, IAndroidTarget> getTargetMap(@NonNull ProgressIndicator progress) {
        if (mTargets == null) {
            Map<String, String> newErrors = Maps.newHashMap();
            RepoManager manager = mSdkHandler.getSdkManager(progress);
            Map<AndroidVersion, PlatformTarget> platformTargets = Maps.newHashMap();
            BiMap<IAndroidTarget, LocalPackage> tempTargetToPackage = HashBiMap.create();
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.PlatformDetailsType) {
                    try {
                        PlatformTarget target = new PlatformTarget(p, mSdkHandler, mFop, progress);
                        AndroidVersion androidVersion = target.getVersion();
                        // If we've already seen a platform with this version, replace the existing
                        // with this if this is the "real" package (with the expected path).
                        // Otherwise, don't create a duplicate.
                        PlatformTarget existing = platformTargets.get(androidVersion);
                        if (existing == null ||
                                p.getPath().equals(DetailsTypes.getPlatformPath(androidVersion))) {
                            if (existing != null) {
                                tempTargetToPackage.remove(existing);
                            }
                            platformTargets.put(androidVersion, target);
                            tempTargetToPackage.put(target, p);
                        }
                    } catch (IllegalArgumentException e) {
                        newErrors.put(p.getPath(), e.getMessage());
                    }
                }
            }
            for (LocalPackage p : manager.getPackages().getLocalPackages().values()) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.AddonDetailsType) {
                    AndroidVersion addonVersion =
                            ((DetailsTypes.AddonDetailsType)details).getAndroidVersion();
                    PlatformTarget baseTarget = platformTargets.get(addonVersion);
                    if (baseTarget != null) {
                        tempTargetToPackage.put(new AddonTarget(p, baseTarget,
                          mSdkHandler.getSystemImageManager(progress), progress, mFop), p);
                    }
                }
            }
            Map<LocalPackage, IAndroidTarget> result = Maps.newTreeMap(TARGET_COMPARATOR);
            result.putAll(tempTargetToPackage.inverse());
            for (LocalPackage p :
              manager.getPackages().getLocalPackagesForPrefix(SdkConstants.FD_ANDROID_SOURCES)) {
                TypeDetails details = p.getTypeDetails();
                if (details instanceof DetailsTypes.ApiDetailsType) {
                    PlatformTarget target = platformTargets.get(
                            ((DetailsTypes.ApiDetailsType)details).getAndroidVersion());
                    if (target != null) {
                        target.setSources(p.getLocation());
                    }
                }
            }
            mTargets = result;
            mLoadErrors = newErrors;
        }
        return mTargets;
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    @Nullable
    public IAndroidTarget getTargetFromHashString(@Nullable String hash,
            @NonNull ProgressIndicator progress) {
        if (hash != null) {
            for (IAndroidTarget target : getTargets(progress)) {
                if (target != null && hash.equals(AndroidTargetHash.getTargetHashString(target))) {
                    return target;
                }
            }
        }
        return null;
    }

    /**
     * Returns first target found with API level no lower than the minimum provided.
     * @param minimumApiLevel minimum api level desired for target.
     * @param progress progress indicator.
     * @return a matching {@link IAndroidTarget} or null
     */
    @Nullable
    public IAndroidTarget getTargetOfAtLeastApiLevel(
            int minimumApiLevel, @NonNull ProgressIndicator progress) {
        for (IAndroidTarget target : getTargets(progress)) {
            if (target.getVersion().getApiLevel() >= minimumApiLevel) {
                return target;
            }
        }
        return null;
    }

    /**
     * Returns the error, if any, encountered when error creating a target for a package.
     */
    @Nullable
    public String getErrorForPackage(@NonNull String path) {
        // We assume we're in here because it's known that there's some error. If mLoadErrors
        // is null we must reload, so we can say exactly what the problem was (if it persists).
        if (mLoadErrors == null) {
            getTargetMap(new ConsoleProgressIndicator());
        }
        return mLoadErrors.get(path);
    }

    @Nullable
    public IAndroidTarget getTargetFromPackage(@NonNull LocalPackage p,
      @NonNull ProgressIndicator progress) {
        return getTargetMap(progress).get(p);
    }
}
