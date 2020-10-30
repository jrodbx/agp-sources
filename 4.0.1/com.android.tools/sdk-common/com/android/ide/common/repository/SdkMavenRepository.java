/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.ide.common.repository;

import static com.android.SdkConstants.FD_EXTRAS;
import static com.android.SdkConstants.FD_M2_REPOSITORY;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a Maven repository that is shipped with the SDK and located in the {@code extras}
 * folder of the SDK location.
 */
public enum SdkMavenRepository {
    /** The Android repository; contains support lib, app compat, media router, etc. */
    ANDROID("android"),

    /** The Google repository; contains Play Services etc. */
    GOOGLE("google");

    @NonNull private final String mDir;

    SdkMavenRepository(@NonNull String dir) {
        mDir = dir;
    }

    /**
     * Returns the location of the repository within a given SDK home.
     * @param sdkHome the SDK home, or null
     * @param requireExists if true, the location will only be returned if it also exists
     * @return the location of the this repository within a given SDK
     */
    @Nullable
    public File getRepositoryLocation(@Nullable File sdkHome, boolean requireExists,
            @NonNull FileOp fileOp) {
        if (sdkHome != null) {
            File dir = new File(sdkHome, FD_EXTRAS + separator + mDir
                    + separator + FD_M2_REPOSITORY);
            if (!requireExists || fileOp.isDirectory(dir)) {
                return dir;
            }
        }

        return null;
    }

    /**
     * Returns true if the given SDK repository is installed.
     *
     * @param sdkHome the SDK installation location
     * @return true if the repository is installed
     */
    public boolean isInstalled(@Nullable File sdkHome, @NonNull FileOp fileOp) {
        return getRepositoryLocation(sdkHome, true, fileOp) != null;
    }

    /**
     * Returns true if the given SDK repository is installed.
     *
     * @param sdkHandler the SDK to check
     * @return true if the repository is installed
     */
    public boolean isInstalled(@Nullable AndroidSdkHandler sdkHandler) {
        if (sdkHandler != null) {
            ProgressIndicator progress = new ConsoleProgressIndicator();
            RepoManager mgr = sdkHandler.getSdkManager(progress);
            return mgr.getPackages().getLocalPackages().containsKey(getPackageId());
        }

        return false;
    }

    public String getPackageId() {
        return String.format("extras;%s;%s", mDir, FD_M2_REPOSITORY);
    }

    /**
     * Returns the SDK repository which contains the given artifact, of null if a matching directory
     * cannot be found in any SDK directory.
     */
    @Nullable
    public static SdkMavenRepository find(
            @NonNull File sdkLocation,
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull FileOp fileOp) {
        for (SdkMavenRepository repository : values()) {
            File repositoryLocation =
                    repository.getRepositoryLocation(sdkLocation, true, fileOp);

            if (repositoryLocation != null) {
                File artifactIdDirectory =
                        MavenRepositories.getArtifactIdDirectory(repositoryLocation, groupId, artifactId);

                if (fileOp.exists(artifactIdDirectory)) {
                    return repository;
                }
            }
        }

        return null;
    }

    /** The directory name of the repository inside the extras folder */
    @NonNull
    public String getDirName() {
        return mDir;
    }

    /**
     * Given {@link RepoPackage}-style {@link RepoPackage#getPath() path}, get the
     * {@link GradleCoordinate} for the package (assuming it is a maven-style package).
     *
     * @return The {@link GradleCoordinate}, or null if the package is not a maven-style package.
     */
    @Nullable
    public static GradleCoordinate getCoordinateFromSdkPath(@NonNull String path) {
        String prefix = String
          .join(Character.toString(RepoPackage.PATH_SEPARATOR), FD_EXTRAS, FD_M2_REPOSITORY, "");
        if (!path.startsWith(prefix)) {
            return null;
        }
        List<String> components = Lists
          .newArrayList(path.split(Character.toString(RepoPackage.PATH_SEPARATOR)));
        String version = components.remove(components.size() - 1);
        String artifact = components.remove(components.size() - 1);
        String group = String.join(".", components.subList(2, components.size()));
        List<GradleCoordinate.RevisionComponent> revisionComponents = GradleCoordinate
          .parseRevisionNumber(version);
        return new GradleCoordinate(
                group,
                artifact,
                revisionComponents.toArray(new GradleCoordinate.RevisionComponent[0]));
    }

    /**
     * Given a collection of {@link RepoPackage}s, find the one that best matches the given
     * {@link GradleCoordinate} (that is, the one that corresponds to the maven artifact with the
     * same version, or the highest package matching a coordinate with +).
     *
     * @return The best package, or {@code null} if none was found.
     */
    @Nullable
    public static RepoPackage findBestPackageMatching(@NonNull GradleCoordinate coordinate,
      @NonNull Collection<? extends RepoPackage> packages) {
        RepoPackage result = null;
        GradleCoordinate resultCoordinate = null;
        for (RepoPackage p : packages) {
            GradleCoordinate test = getCoordinateFromSdkPath(p.getPath());
            if (test != null && test.matches(coordinate) && (resultCoordinate == null
              || GradleCoordinate.COMPARE_PLUS_LOWER.compare(test, resultCoordinate) > 0)) {
                result = p;
                resultCoordinate = test;
            }
        }
        return result;
    }

    /**
     * Finds the latest installed version of the SDK package identified by the given
     * {@link GradleCoordinate}. Preview versions will only be included if the given coordinate is
     * a preview.
     * E.g. if {@code coordinate} is {@code com.android.support.constraint:constraint-layout:1.0.0}
     * and {@code com.android.support.constraint:constraint-layout:1.1.0} and
     * {@code com.android.support.constraint:constraint-layout:1.2.0-alpha1} are also installed, the
     * SDK package
     * {@code extras;m2repository;com;android;support;constraint;constraint-layout;1.1.0} will be
     * returned, since the provided coordinate is not a preview. If
     * {@code com.android.support.constraint:constraint-layout:1.0.0-alpha1} is passed in,
     * {@code extras;m2repository;com;android;support;constraint;constraint-layout;1.1.0-alpha1}
     * will be returned.
     *
     * @param coordinate The {@link GradleCoordinate} identifying the artifact we're interested in.
     * @param sdkHandler {@link AndroidSdkHandler} instance.
     * @param filter The version filter that has to be satisfied
     * @param progress {@link ProgressIndicator}, for logging.
     * @return The {@link LocalPackage} with the same {@code groupId} and {@code artifactId} as the
     * given {@code coordinate} and the highest version.
     */
    @Nullable
    public static LocalPackage findLatestLocalVersion(@NonNull GradleCoordinate coordinate,
            @NonNull AndroidSdkHandler sdkHandler,
            @Nullable Predicate<GradleVersion> filter,
            @NonNull ProgressIndicator progress) {
        if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) {
            return null;
        }
        String prefix = DetailsTypes.MavenType.getRepositoryPath(
                coordinate.getGroupId(), coordinate.getArtifactId(), null);
        Predicate<Revision> revisionFilter = filter == null ? null
                : (revision) -> filter.test(revisionToGradleVersion(revision));
        return sdkHandler.getLatestLocalPackageForPrefix(
                prefix, revisionFilter,  coordinate.isPreview(), GradleCoordinate::parseVersionOnly,
                GradleCoordinate.COMPARE_PLUS_LOWER, progress);
    }

    @NonNull
    private static GradleVersion revisionToGradleVersion(Revision revision) {
        return GradleVersion.parse(revision.toString("-"));
    }

    /**
     * Like {@link #findLatestLocalVersion}, but for available {@link RemotePackage}s.
     */
    @Nullable
    public static RemotePackage findLatestRemoteVersion(@NonNull GradleCoordinate coordinate,
            @NonNull AndroidSdkHandler sdkHandler,
            @Nullable Predicate<GradleVersion> filter,
            @NonNull ProgressIndicator progress) {
        if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) {
            return null;
        }
        String prefix = DetailsTypes.MavenType.getRepositoryPath(
                coordinate.getGroupId(), coordinate.getArtifactId(), null);
        return sdkHandler.getLatestRemotePackageForPrefix(
                prefix, coordinate.isPreview(), GradleCoordinate::parseVersionOnly,
                GradleCoordinate.COMPARE_PLUS_LOWER, progress);
    }

    /**
     * Like {@link #findLatestLocalVersion}, but returns the most recent package available either
     * locally or remotely.
     */
    @Nullable
    public static RepoPackage findLatestVersion(@NonNull GradleCoordinate coordinate,
            @NonNull AndroidSdkHandler sdkHandler,
            @Nullable Predicate<GradleVersion> filter,
            @NonNull ProgressIndicator progress) {
        LocalPackage local = findLatestLocalVersion(coordinate, sdkHandler, filter, progress);
        RemotePackage remote = findLatestRemoteVersion(coordinate, sdkHandler, filter, progress);
        if (local == null) {
            return remote;
        }
        if (remote == null) {
            return local;
        }
        GradleCoordinate localCoordinate = getCoordinateFromSdkPath(local.getPath());
        GradleCoordinate remoteCoordinate = getCoordinateFromSdkPath(remote.getPath());
        if (GradleCoordinate.COMPARE_PLUS_LOWER.compare(localCoordinate, remoteCoordinate) < 0) {
            return remote;
        }
        return local;
    }
}
