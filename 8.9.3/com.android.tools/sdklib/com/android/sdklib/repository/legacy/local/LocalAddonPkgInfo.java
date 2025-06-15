/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.sdklib.repository.legacy.local;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.IdDisplay;

import java.io.File;
import java.util.Locale;
import java.util.Properties;

@SuppressWarnings("MethodMayBeStatic")
/**
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
public class LocalAddonPkgInfo extends LocalPlatformPkgInfo {

    @NonNull
    private final IPkgDesc mAddonDesc;

    LocalAddonPkgInfo(@NonNull LocalSdk localSdk,
            @NonNull File localDir,
            @NonNull Properties sourceProps,
            @NonNull AndroidVersion version,
            @NonNull Revision revision,
            @NonNull IdDisplay vendor,
            @NonNull IdDisplay name) {
        super(localSdk, localDir, sourceProps, version, revision, Revision.NOT_SPECIFIED);
        mAddonDesc = PkgDesc.Builder.newAddon(version, revision, vendor, name).create();
    }

    @NonNull
    @Override
    public IPkgDesc getDesc() {
        return mAddonDesc;
    }

    /**
     * The "path" of an add-on is its Target Hash.
     */
    @Override
    @NonNull
    public String getTargetHash() {
        return getDesc().getPath();
    }

    //-----

    /**
     * Computes a sanitized name-id based on an addon name-display. This is used to provide
     * compatibility with older add-ons that lacks the new fields.
     *
     * @param displayName A name-display field or a old-style name field.
     * @return A non-null sanitized name-id that fits in the {@code [a-zA-Z0-9_-]+} pattern.
     */
    public static String sanitizeDisplayToNameId(@NonNull String displayName) {
        String name = displayName.toLowerCase(Locale.US);
        name = name.replaceAll("[^a-z0-9_-]+", "_");      //$NON-NLS-1$ //$NON-NLS-2$
        name = name.replaceAll("_+", "_");                //$NON-NLS-1$ //$NON-NLS-2$

        // Trim leading and trailing underscores
        if (name.length() > 1) {
            name = name.replaceAll("^_+", "");            //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (name.length() > 1) {
            name = name.replaceAll("_+$", "");            //$NON-NLS-1$ //$NON-NLS-2$
        }
        return name;
    }

}
