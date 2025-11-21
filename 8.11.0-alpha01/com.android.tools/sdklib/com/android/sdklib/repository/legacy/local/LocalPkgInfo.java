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
import com.android.annotations.Nullable;
import com.android.repository.api.RepoManager;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;

import java.io.File;
import java.util.Properties;

/**
 * Information about a locally installed package.
 * <p>
 * Local package information is retrieved via the {@link LocalSdk} object.
 * Clients should not need to create instances of {@link LocalPkgInfo} directly.
 * Instead please use the {@link LocalSdk} methods to parse and retrieve packages.
 * <p>
 *
 * @deprecated This is part of the old SDK manager framework. Use
 * {@link AndroidSdkHandler}/{@link RepoManager} and associated classes instead.
 */
@Deprecated
public abstract class LocalPkgInfo
        implements Comparable<LocalPkgInfo> {

    private final LocalSdk mLocalSdk;
    private final File mLocalDir;
    private final Properties mSourceProperties;

    private String mLoadError;

    protected LocalPkgInfo(@NonNull LocalSdk localSdk, @NonNull File localDir, @NonNull Properties sourceProps) {
        mLocalSdk = localSdk;
        mLocalDir = localDir;
        mSourceProperties = sourceProps;
    }

    //---- Attributes ----

    @NonNull
    public LocalSdk getLocalSdk() {
        return mLocalSdk;
    }

    @NonNull
    public File getLocalDir() {
        return mLocalDir;
    }

    @NonNull
    public Properties getSourceProperties() {
        return mSourceProperties;
    }

    @Nullable
    public String getLoadError() {
        return mLoadError;
    }

    // ----

    /**
     * Returns the {@link IPkgDesc} describing this package.
     */
    @NonNull
    public abstract IPkgDesc getDesc();


    //---- Ordering ----

    /**
     * Comparison is solely done based on the {@link IPkgDesc}.
     * <p>
     * Other local attributes (local directory, source properties)
     * are <em>not used</em> in the comparison. Consequently {@link #compareTo(LocalPkgInfo)}
     * does not match {@link #equals(Object)} and the {@link #hashCode()} properties.
     */
    @Override
    public int compareTo(@NonNull LocalPkgInfo o) {
        return getDesc().compareTo(o.getDesc());
    }

    /**
     * String representation for debugging purposes.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(this.getClass().getSimpleName()).append(' ');
        builder.append(getDesc().toString());
        builder.append('>');
        return builder.toString();
    }

    /**
     * Computes a hash code specific to this instance based on the underlying
     * {@link IPkgDesc} but also specific local properties such a local directory,
     * and actual source properties.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getDesc() == null) ? 0 : getDesc().hashCode());
        result = prime * result + ((mLocalDir == null) ? 0 : mLocalDir.hashCode());
        result = prime * result + ((mSourceProperties == null) ? 0 : mSourceProperties.hashCode());
        return result;
    }

    /**
     * Computes object equality to this instance based on the underlying
     * {@link IPkgDesc} but also specific local properties such a local directory,
     * update available and actual source properties. This is different from
     * the behavior of {@link #compareTo(LocalPkgInfo)} which only uses the
     * {@link IPkgDesc} for ordering.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof LocalPkgInfo)) {
            return false;
        }
        LocalPkgInfo other = (LocalPkgInfo)obj;

        if (!getDesc().equals(other.getDesc())) {
            return false;
        }
        if (mLocalDir == null) {
            if (other.mLocalDir != null) {
                return false;
            }
        }
        else if (!mLocalDir.equals(other.mLocalDir)) {
            return false;
        }
        if (mSourceProperties == null) {
            if (other.mSourceProperties != null) {
                return false;
            }
        }
        else if (!mSourceProperties.equals(other.mSourceProperties)) {
            return false;
        }
        return true;
    }


    //---- Package Management ----

    void appendLoadError(@NonNull String format, Object... params) {
        String loadError = String.format(format, params);
        if (mLoadError == null) {
            mLoadError = loadError;
        }
        else {
            mLoadError = mLoadError + '\n' + loadError;
        }
    }

    @NonNull
    public String getListDescription() {
        return getDesc().getListDescription();
    }
}

