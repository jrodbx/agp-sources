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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Represents a (revisionless) package, either local, remote, or both. If both a local and remote
 * package are specified, they should represent exactly the same package, excepting the revision.
 * That is, the result of installing the remote package should be (a possibly updated version of)
 * the local package.
 */
public class UpdatablePackage implements Comparable<UpdatablePackage> {

    private LocalPackage mLocalPackage;

    private RemotePackage mRemotePackage;

    public UpdatablePackage(@NonNull LocalPackage localPackage) {
        init(localPackage, null);
    }

    public UpdatablePackage(@NonNull RemotePackage remotePackage) {
        init(null, remotePackage);
    }

    public UpdatablePackage(@NonNull LocalPackage localPackage,
            @NonNull RemotePackage remotePackage) {
        init(localPackage, remotePackage);
    }

    private void init(@Nullable LocalPackage localPkg, @Nullable RemotePackage remotePkg) {
        assert localPkg != null || remotePkg != null;
        mLocalPackage = localPkg;
        if (remotePkg != null) {
            setRemote(remotePkg);
        }
    }

    /**
     * Sets the remote package for this {@code UpdatablePackage}.
     */
    public void setRemote(@NonNull RemotePackage remote) {
        mRemotePackage = remote;
    }

    @Nullable
    public LocalPackage getLocal() {
        return mLocalPackage;
    }

    @Nullable
    public RemotePackage getRemote() {
        return mRemotePackage;
    }

    public boolean hasRemote() {
        return getRemote() != null;
    }

    public boolean hasLocal() {
        return mLocalPackage != null;
    }

    @Override
    public int compareTo(@NonNull UpdatablePackage o) {
        return getRepresentative().compareTo(o.getRepresentative());
    }

    /**
     * Gets a {@link RepoPackage} (either local or remote) corresponding to this updatable package.
     * This will be the local package if there is one, and the remote otherwise.
     */
    @NonNull
    public RepoPackage getRepresentative() {
        if (hasLocal()) {
            return mLocalPackage;
        }
        // getRemote() must be non-null if there's no local
        //noinspection ConstantConditions
        return getRemote();
    }

    /**
     * @return {@code true} if this package is installed and a newer version is available.
     */
    public boolean isUpdate() {
        RemotePackage remote = getRemote();
        return mLocalPackage != null && remote != null
                && mLocalPackage.getVersion().compareTo(remote.getVersion()) < 0;
    }

    /**
     * @return The {@link RepoPackage#getPath() path} of the local and/or remote package.
     */
    public String getPath() {
        return getRepresentative().getPath();
    }
}
