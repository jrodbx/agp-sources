/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.repository.meta.DetailsTypes.AddonDetailsType;
import com.android.sdklib.repository.meta.DetailsTypes.PlatformDetailsType;
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.utils.ILogger;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SystemImageSupplier {
    @NonNull private final RepoManager repoManager;
    @NonNull private final SystemImageManager systemImageManager;
    @NonNull private final ILogger logger;

    public SystemImageSupplier(
            @NonNull RepoManager repoManager,
            @NonNull SystemImageManager systemImageManager,
            @NonNull ILogger logger) {
        this.repoManager = repoManager;
        this.systemImageManager = systemImageManager;
        this.logger = logger;
    }

    @NonNull
    public Iterable<ISystemImage> get() {
        return repoManager.getPackages().getConsolidatedPkgs().values().stream()
                .map(UpdatablePackage::getRepresentative)
                .filter(SystemImageSupplier::hasSystemImage)
                .map(this::from)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static boolean hasSystemImage(@NonNull RepoPackage repoPackage) {
        Object details = repoPackage.getTypeDetails();

        return details instanceof SysImgDetailsType
                || details instanceof PlatformDetailsType
                        && ((PlatformDetailsType) details).getApiLevel() <= 13
                || details instanceof AddonDetailsType
                        && hasSystemImage((AddonDetailsType) details);
    }

    private static boolean hasSystemImage(@NonNull AddonDetailsType details) {
        return details.getVendor().getId().equals("google")
                && SystemImageTags.TAGS_WITH_GOOGLE_API.contains(details.getTag())
                && details.getApiLevel() <= 19;
    }

    @Nullable
    private ISystemImage from(@NonNull RepoPackage repoPackage) {
        if (repoPackage instanceof RemotePackage) {
            return new RemoteSystemImage((RemotePackage) repoPackage);
        }

        if (repoPackage instanceof LocalPackage) {
            return get((LocalPackage) repoPackage);
        }

        logger.warning("%s %s", repoPackage.getPath(), repoPackage.getClass());
        return null;
    }

    @Nullable
    private ISystemImage get(@NonNull LocalPackage localPackage) {
        Collection<SystemImage> images = systemImageManager.getImageMap().get(localPackage);

        switch (images.size()) {
            case 0:
                logger.warning("No system images for %s", localPackage.getPath());
                return null;
            case 1:
                return images.iterator().next();
            default:
                logger.warning(
                        "Multiple images for %. Returning the first.", localPackage.getPath());
                return images.iterator().next();
        }
    }
}
