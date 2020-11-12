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
 * A site from which repository XML files can be downloaded.
 */
public interface RepositorySource {

    /**
     * Gets the {@link SchemaModule}s that are allowed to be used to parse XML from this
     * source.
     */
    @NonNull
    Collection<SchemaModule<?>> getPermittedModules();

    /**
     * @return true if this source is enabled.
     */
    boolean isEnabled();

    /**
     * @param enabled Whether this source should be enabled or disabled.
     */
    void setEnabled(boolean enabled);

    /**
     * @return The user-friendly name for this source.
     */
    @Nullable
    String getDisplayName();

    /**
     * @return The URL from which to download.
     */
    String getUrl();

    /**
     * If an error was encountered loading from this source, it can be set here for display to the
     * user.
     */
    void setFetchError(@Nullable String error);

    /**
     * Gets the error (if any) encountered when fetching content from this source.
     * @return The error, or {@code null} if the load was successful.
     */
    @Nullable
    String getFetchError();

    /**
     * @return The provider that created this source.
     */
    @NonNull
    RepositorySourceProvider getProvider();
}
