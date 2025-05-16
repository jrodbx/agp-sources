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
 * A simple {@link RepositorySource}.
 */
public class SimpleRepositorySource implements RepositorySource {

    /**
     * The URL for this source.
     */
    private final String mUrl;

    /**
     * The user-friendly name for this source.
     */
    private final String mDisplayName;

    /**
     * Whether this source is enabled.
     * TODO: persist this value.
     */
    private boolean mEnabled;

    /**
     * The {@link SchemaModule}s allowed to be used when parsing the xml downloaded from
     * this source.
     */
    private final Collection<SchemaModule<?>> mAllowedModules;

    /**
     * Any error that occurred when fetching this source.
     */
    private String mError;

    /**
     * The {@link RepositorySourceProvider} that created this source.
     */
    private final RepositorySourceProvider mProvider;

    /**
     * Constructor
     *
     * @param url The URL this source will fetch from.
     * @param displayName The user-friendly name for this source
     * @param enabled Whether this source is enabled.
     * @param allowedModules The {@link SchemaModule}s allowed to be used when parsing the xml
     *                       downloaded from this source.
     * @param provider The {@link RepositorySourceProvider} that created this source.
     */
    public SimpleRepositorySource(@NonNull String url,
                                  @Nullable String displayName,
                                  boolean enabled,
                                  @NonNull Collection<SchemaModule<?>> allowedModules,
                                  @NonNull RepositorySourceProvider provider) {
        mProvider = provider;
        mUrl = url.trim();
        mDisplayName = displayName;
        mEnabled = enabled;
        mAllowedModules = allowedModules;
    }


    @Override
    @NonNull
    public Collection<SchemaModule<?>> getPermittedModules() {
        return mAllowedModules;
    }

    @Override
    public boolean isEnabled() {
        return mEnabled;
    }
    @Override
    public void setEnabled(boolean enabled) {
        if (getProvider().isModifiable()) {
            mEnabled = enabled;
        }
    }

    @Override
    @Nullable
    public String getDisplayName() {
        return mDisplayName;
    }

    @Override
    @NonNull
    public String getUrl() {
        return mUrl;
    }

    /**
     * Returns a debug string representation of this object. Not for user display.
     */
    @Override
    @NonNull
    public String toString() {
        return String.format("<RepositorySource URL='%1$s' Name='%2$s'>", mUrl, mDisplayName);
    }

    @Override
    public void setFetchError(@Nullable String error) {
        mError = error;
    }

    @Override
    @Nullable
    public String getFetchError() {
        return mError;
    }

    @Override
    @NonNull
    public RepositorySourceProvider getProvider() {
        return mProvider;
    }
}
