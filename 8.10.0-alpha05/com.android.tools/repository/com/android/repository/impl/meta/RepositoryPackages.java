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

import static com.android.repository.util.RepoPackageUtilKt.getAllRepoPackagePrefixes;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Store of currently-known local and remote packages, in convenient forms.
 */
@XmlTransient
public final class RepositoryPackages {

    /**
     * All the packages that are locally-installed and have a remotely-available update.
     */
    private Set<UpdatablePackage> mUpdatedPkgs = Sets.newTreeSet();

    /**
     * All the packages that are available remotely and don't have an installed version.
     */
    private Set<RemotePackage> mNewPkgs = Sets.newTreeSet();

    /**
     * When this object was created.
     */
    private final long myTimestampMs;

    /**
     * Multimap from all prefixes of {@code path}s (the unique IDs of packages) to
     * {@link LocalPackage}s with that path prefix.
     *
     * For example, if there are packages
     * {@code foo;bar;baz},
     * {@code foo;bar;qux}, and
     * {@code foo;xyzzy},
     * this map will contain
     * {@code foo->[Baz package, Qux package, Xyzzy package]},
     * {@code foo;bar->[Baz package, Qux package]},
     * {@code foo;bar;baz->[Baz package]},
     * {@code foo;bar;qux->[Qux package]},
     * {@code foo;xyzzy->[Xyzzy package]}
     */
    private Multimap<String, LocalPackage> mLocalPackagesByPrefix = TreeMultimap.create();

    /**
     * Multimap from all prefixes of {@code path}s (the unique IDs of packages) to
     * {@link RemotePackage}s with that path prefix.
     *
     * @see #mLocalPackagesByPrefix for examples.
     */
    private Multimap<String, RemotePackage> mRemotePackagesByPrefix = TreeMultimap.create();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link UpdatablePackage}, including all
     * packages installed or available.
     */
    private Map<String, UpdatablePackage> mConsolidatedPkgs = Maps.newTreeMap();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link LocalPackage}, including all
     * installed packages.
     */
    private Map<String, LocalPackage> mLocalPackages = Maps.newHashMap();

    /**
     * Map from {@code path} (the unique ID of a package) to {@link RemotePackage}. There may be
     * more than one version of the same {@link RemotePackage} available, for example if there is a
     * stable and a preview version available.
     */
    private Map<String, RemotePackage> mRemotePackages = Maps.newTreeMap();

    private final Object mLock = new Object();

    public RepositoryPackages() {
        myTimestampMs = System.currentTimeMillis();
    }

    public RepositoryPackages(@NonNull List<LocalPackage> localPkgs,
            @NonNull List<RemotePackage> remotePkgs) {
        this();
        setLocalPkgInfos(localPkgs);
        setRemotePkgInfos(remotePkgs);
    }

    /**
     * Returns the timestamp (in {@link System#currentTimeMillis()} time) when this object was
     * created.
     */
    public long getTimestampMs() {
        return myTimestampMs;
    }

    /**
     * Returns the set of packages that have local updates available.
     *
     * @return A non-null, possibly empty Set of update candidates.
     */
    @NonNull
    public Set<UpdatablePackage> getUpdatedPkgs() {
        Set<UpdatablePackage> result = mUpdatedPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mUpdatedPkgs;
            }
        }
        return result;
    }

    /**
     * Returns the set of new remote packages that are not locally present and that the user could
     * install.
     *
     * @return A non-null, possibly empty Set of new install candidates.
     */
    @NonNull
    public Set<RemotePackage> getNewPkgs() {
        Set<RemotePackage> result = mNewPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mNewPkgs;
            }
        }
        return result;
    }

    /**
     * Returns a map of package install ids to {@link UpdatablePackage}s representing all known
     * local and remote packages. Remote packages corresponding to local packages will be
     * represented by a single item containing both the local and remote info. {@see
     * IPkgDesc#getInstallId()}
     */
    @NonNull
    public Map<String, UpdatablePackage> getConsolidatedPkgs() {
        Map<String, UpdatablePackage> result = mConsolidatedPkgs;
        if (result == null) {
            synchronized (mLock) {
                computeUpdates();
                result = mConsolidatedPkgs;
            }
        }
        return result;
    }

    /**
     * Returns a map of {@code path} (the unique ID of a package) to {@link LocalPackage}, for all
     * packages currently installed.
     */
    @NonNull
    public Map<String, LocalPackage> getLocalPackages() {
        return mLocalPackages;
    }

    /**
     * Returns a {@link Map} from {@code path} (the unique ID of a package) to
     * {@link RemotePackage}.
     */
    @NonNull
    public Map<String, RemotePackage> getRemotePackages() {
        return mRemotePackages;
    }

    @NonNull
    public Collection<LocalPackage> getLocalPackagesForPrefix(
            @Nullable String pathPrefix) {
        return mLocalPackagesByPrefix.get(pathPrefix);
    }

    @NonNull
    public Collection<RemotePackage> getRemotePackagesForPrefix(
            @Nullable String pathPrefix) {
        return mRemotePackagesByPrefix.get(pathPrefix);
    }

    /**
     * Sets the collection of known {@link LocalPackage}s, and recomputes the list of updates and
     * new packages, if {@link RemotePackage}s have been set.
     */
    public void setLocalPkgInfos(@NonNull Collection<LocalPackage> packages) {
        synchronized (mLock) {
            mLocalPackages = mapByPath(packages);
            invalidate();
            mLocalPackagesByPrefix = computePackagePrefixes(mLocalPackages);
        }
    }

    /**
     * Sets the collection of known {@link RemotePackage}s, and recomputes the list of updates and
     * new packages, if {@link LocalPackage}s have been set.
     */
    public void setRemotePkgInfos(@NonNull Collection<RemotePackage> packages) {
        synchronized (mLock) {
            mRemotePackages = mapByPath(packages);
            invalidate();
            mRemotePackagesByPrefix = computePackagePrefixes(mRemotePackages);
        }
    }

    @NonNull
    private static <T extends RepoPackage> Map<String, T> mapByPath(
      @NonNull Collection<T> packages) {
        return ImmutableMap.copyOf(
                packages.stream()
                        .collect(Collectors.toMap(RepoPackage::getPath, Function.identity())));
    }

    private void invalidate() {
        mConsolidatedPkgs = null;
        mNewPkgs = null;
        mUpdatedPkgs = null;
    }

    private void computeUpdates() {
        Map<String, UpdatablePackage> newConsolidatedPkgs = Maps.newTreeMap();
        Set<UpdatablePackage> updates = Sets.newHashSet();
        for (String path : mLocalPackages.keySet()) {
            LocalPackage local = mLocalPackages.get(path);
            UpdatablePackage updatable = new UpdatablePackage(local);
            newConsolidatedPkgs.put(path, updatable);
            if (mRemotePackages.containsKey(path)) {
                updatable.setRemote(mRemotePackages.get(path));
                if (updatable.isUpdate()) {
                    updates.add(updatable);
                }
            }
        }
        Set<RemotePackage> news = Sets.newHashSet();
        for (String path : mRemotePackages.keySet()) {
            if (!newConsolidatedPkgs.containsKey(path)) {
                RemotePackage remote = mRemotePackages.get(path);
                news.add(remote);
                UpdatablePackage updatable = new UpdatablePackage(remote);
                newConsolidatedPkgs.put(path, updatable);
            }
        }
        mNewPkgs = news;
        mUpdatedPkgs = updates;
        mConsolidatedPkgs = newConsolidatedPkgs;
    }

    private static <P extends RepoPackage> Multimap<String, P> computePackagePrefixes(
            Map<String, ? extends P> packages) {
        Multimap<String, P> packagesByPrefix = TreeMultimap.create();
        for (Map.Entry<String, ? extends P> entry : packages.entrySet()) {
            String path = entry.getKey();
            P p = entry.getValue();
            List<String> prefixes = getAllRepoPackagePrefixes(path);
            for (String prefix : prefixes) {
                packagesByPrefix.put(prefix, p);
            }
        }
        return packagesByPrefix;
    }

}
