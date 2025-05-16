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
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Parsed representation of a repository xml file, including packages and licenses.
 * Primarily stubs to be overridden by xjc-generated classes.
 */
@SuppressWarnings("MethodMayBeStatic")
@XmlTransient
public abstract class Repository {

    /**
     * @return The {@link License}s included in this repository. In general licenses should be
     * obtained from {@link RepoPackage}s, not directly from the repository (as they might not
     * even apply to any packages.
     */
    @VisibleForTesting
    @NonNull
    public abstract List<License> getLicense();

    /**
     * Convenience method to add a {@link License} to this repository.
     */
    public void addLicense(@NonNull License l) {
        getLicense().add(l);
    }

    /**
     * @return The {@link Channel}s included in this repository. In general licenses should be
     * obtained from {@link RepoPackage}s, not directly from the repository (as they might not
     * even apply to any packages.
     */
    @NonNull
    public abstract List<Channel> getChannel();

    /**
     * Convenience method to add a {@link Channel} to this repository.
     */
    public void addChannel(@NonNull Channel c) {
        getChannel().add(c);
    }

    @NonNull
    public abstract CommonFactory createFactory();

    @NonNull
    public List<RemotePackageImpl> getRemotePackage() {
        // Stub.
        return ImmutableList.of();
    }

    public void setLocalPackage(@Nullable LocalPackageImpl p) {
        // Stub
    }

    @Nullable
    public LocalPackage getLocalPackage() {
        // Stub.
        return null;
    }
}
