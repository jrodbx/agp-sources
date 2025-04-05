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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.RepositorySourceProvider;
import com.android.repository.api.SettingsController;

import java.util.Map;

/**
 * A facility for loading {@link RepoPackage}s that are available for download.
 */
public interface RemoteRepoLoader {

    /**
     * Fetches the remote packages.
     *
     * @param progress   {@link ProgressIndicator} for logging and showing progress (TODO).
     * @param downloader The {@link Downloader} to use for {@link RepositorySourceProvider}s to use
     *                   if needed.
     * @param settings   The {@link SettingsController} for {@link RepositorySourceProvider}s to use
     *                   if needed.
     * @return A map of install paths to {@link RemotePackage}s. The remote package will be the most
     * recent version of the package in a channel at least as stable as the one specified by {@link
     * SettingsController#getChannel()}.
     */
    @NonNull
    Map<String, RemotePackage> fetchPackages(@NonNull ProgressIndicator progress,
            @NonNull Downloader downloader, @Nullable SettingsController settings);
}
