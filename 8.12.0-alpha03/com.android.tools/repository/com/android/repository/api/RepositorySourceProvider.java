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

import java.util.List;

/**
 * A source of {@link RepositorySource}s.
 */
public interface RepositorySourceProvider {

    /**
     * Gets the {@link RepositorySource}s from this provider.
     *
     * @param downloader   The {@link Downloader}, if required by this provider.
     * @param logger       A {@link ProgressIndicator} to be used for showing progress and logging.
     * @param forceRefresh If true, this provider should refresh its list of sources, rather than
     *                     using a cached version.
     */
    @NonNull
    List<RepositorySource> getSources(@Nullable Downloader downloader,
            @NonNull ProgressIndicator logger, boolean forceRefresh);

    /**
     * Add a source to this provider, if this provider is editable. Changes will be reflected in
     * {@link #getSources(Downloader, ProgressIndicator, boolean)}, but not
     * persisted until {@link #save(ProgressIndicator)} is called.
     *
     * @param source The source to add.
     * @return {@code true} if the source was successfully added, {@code false} otherwise.
     */
    boolean addSource(@NonNull RepositorySource source);

    /**
     * @return {@code true} if this provider can be edited (that is, it has a facility for saving
     * and loading changes), {@code false} otherwise.
     */
    boolean isModifiable();

    /**
     * If any changes have been made, persist them.
     */
    void save(@NonNull ProgressIndicator progress);

    /**
     * Remove the given source from this provider, if this provider is editable.
     * @see #addSource(RepositorySource)
     * @param source The source to remove.
     * @return {@code true} if the source was successfully removed, {@code false} otherwise.
     */
    boolean removeSource(@NonNull RepositorySource source);
}
