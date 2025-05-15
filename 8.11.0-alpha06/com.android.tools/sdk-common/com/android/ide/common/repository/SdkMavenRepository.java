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
import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Dependency;
import com.android.ide.common.gradle.Module;
import com.android.ide.common.gradle.RichVersion;
import com.android.ide.common.gradle.Version;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import com.android.repository.api.ConsoleProgressIndicator;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.google.common.collect.Lists;
import java.nio.file.Path;
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
     *
     * @param sdkHome the SDK home, or null
     * @param requireExists if true, the location will only be returned if it also exists
     * @return the location of the this repository within a given SDK
     */
    @Nullable
    public Path getRepositoryLocation(@Nullable Path sdkHome, boolean requireExists) {
        if (sdkHome != null) {
            Path dir = sdkHome.resolve(FD_EXTRAS + separator + mDir + separator + FD_M2_REPOSITORY);
            if (!requireExists || CancellableFileIo.isDirectory(dir)) {
                return dir;
            }
        }

        return null;
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
            @NonNull Path sdkLocation, @NonNull String groupId, @NonNull String artifactId) {
        for (SdkMavenRepository repository : values()) {
            Path repositoryLocation = repository.getRepositoryLocation(sdkLocation, true);

            if (repositoryLocation != null) {
                Path artifactIdDirectory =
                        MavenRepositories.getArtifactIdDirectory(
                                repositoryLocation, groupId, artifactId);

                if (CancellableFileIo.exists(artifactIdDirectory)) {
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
}
