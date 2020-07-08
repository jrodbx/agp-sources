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

package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.Repository;
import com.google.common.annotations.VisibleForTesting;
import javax.xml.bind.JAXBElement;

/**
 * Factory for creating the objects used by the repository framework.
 * Instances of this class can be obtained from any of the objects creatable by this class,
 * or by {@code RepoManager.getCommonModule().createLatestFactory()}.
 *
 * Primarily a superclass for xjc-generated {@code ObjectFactory}s. Most methods shouldn't be
 * needed outside the repository framework.
 */
@SuppressWarnings("MethodMayBeStatic")
public abstract class CommonFactory {
    @NonNull
    public abstract Repository createRepositoryType();

    @NonNull
    public abstract Archive createArchiveType();

    @NonNull
    protected abstract RepoPackageImpl.Archives createArchivesType();

    @NonNull
    public abstract LocalPackageImpl createLocalPackage();

    @NonNull
    public abstract RemotePackageImpl createRemotePackage();

    @NonNull
    protected abstract RevisionType createRevisionType();

    @NonNull
    public abstract Channel createChannelType();

    @NonNull
    public abstract Archive.PatchesType createPatchesType();

    @NonNull
    public abstract JAXBElement<Repository> generateRepository(Repository repo);

    /**
     * Convenience method to create a {@link Channel} with the given numeric id.
     */
    @NonNull
    public Channel createChannelType(int id) {
        Channel res = createChannelType();
        res.setId("channel-" + id);
        return res;
    }

    /**
     * Creates a {@link RevisionType} from the specified {@link Revision}.
     */
    @NonNull
    public RevisionType createRevisionType(Revision revision) {
        RevisionType rt = createRevisionType();
        int[] components = revision.toIntArray(true);
        rt.setMajor(components[0]);
        if (components.length > 1) {
            rt.setMinor(components[1]);
        }
        if (components.length > 2) {
            rt.setMicro(components[2]);
        }
        if (components.length > 3) {
            rt.setPreview(components[3]);
        }
        return rt;
    }

    @NonNull
    public abstract RepoPackageImpl.UsesLicense createLicenseRefType();

    /**
     * Convenience method to create a license with the given id and value.
     */
    @NonNull
    public License createLicenseType(String value, String id) {
        License l = createLicenseType();
        l.setValue(value);
        l.setId(id);
        l.setType("text");
        return l;
    }

    @NonNull
    public abstract License createLicenseType();

    @VisibleForTesting
    @NonNull
    public abstract Dependency createDependencyType();

    /**
     * Creates a {@link Dependency} with the given {@code minRevision} and {@code path}.
     */
    @NonNull
    public Dependency createDependencyType(@Nullable Revision minRevision, @NonNull String path) {
        Dependency d = createDependencyType();
        d.setMinRevision(createRevisionType(minRevision));
        d.setPath(path);
        return d;
    }

    @NonNull
    public abstract RepoPackageImpl.Dependencies createDependenciesType();

    @NonNull
    public abstract Archive.CompleteType createCompleteType();

    @NonNull
    public abstract Archive.PatchType createPatchType();

    public abstract RemotePackageImpl.ChannelRef createChannelRefType();
}
