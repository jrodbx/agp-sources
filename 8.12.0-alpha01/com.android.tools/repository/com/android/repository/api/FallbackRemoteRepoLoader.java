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
import java.util.Collection;

/**
 * An implementation of a remote repo parser to use to try to parse and xml file if the normal
 * mechanism doesn't. If one is provided to RepoManager,
 * {@link #parseLegacyXml}
 * will be run on every xml file retrieved from a {@link RepositorySource} that isn't recognized by
 * the normal mechanism.
 */
public interface FallbackRemoteRepoLoader {

    /**
     * Parses an xml file into {@link RemotePackage}s.
     *
     * @param xml The {@link RepositorySource} to read from.
     * @return The parsed packages, null if none are found (due to an error).
     */
    @Nullable
    Collection<RemotePackage> parseLegacyXml(
            @NonNull RepositorySource xml,
            @NonNull Downloader downloader,
            @Nullable SettingsController settings,
            @NonNull ProgressIndicator progress);
}
