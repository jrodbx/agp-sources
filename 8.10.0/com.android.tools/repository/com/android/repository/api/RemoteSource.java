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

import javax.xml.bind.annotation.XmlTransient;

/**
 * A {@code RepositorySource} that was created by a {@link RemoteListSourceProvider} and superclass
 * for xjc-generated classes used to parse the sites list xml. Generated classes provide the url and
 * UI name; the permitted extensions must be set by the provider. If you implement a new site type,
 * it should extend siteType in repo-sites-common-N.xsd, and thus transitively extend this class.
 */
@SuppressWarnings("MethodMayBeStatic")
@XmlTransient
public abstract class RemoteSource implements RepositorySource {

    private Collection<SchemaModule<?>> mPermittedSchemaModules = null;

    // TODO: refactor into RepositorySource, along with the fetching logic that sets it.
    private String mFetchError;

    @XmlTransient
    private RepositorySourceProvider mProvider;

    /**
     * Sets the list of modules allowed to be used when parsing XML fetched from this source.
     */
    public void setPermittedSchemaModules(@NonNull Collection<SchemaModule<?>> modules) {
        mPermittedSchemaModules = modules;
    }

    /**
     * @return The list of schema modules allowed to be used when parsing XML fetched from this
     * source.
     */
    @Override
    @NonNull
    public Collection<SchemaModule<?>> getPermittedModules() {
        if (mPermittedSchemaModules == null) {
            throw new UnsupportedOperationException(
                    "Tried to fetch permitted modules before they were initialized");
        }
        return mPermittedSchemaModules;
    }

    /**
     * {@inheritDoc}
     *
     * Currently not implemented.
     */
    @Override
    public boolean isEnabled() {
        // TODO (this isn't persistently implemented currently either).
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * Currently not implemented.
     */
    @Override
    public void setEnabled(boolean enabled) {
        // TODO
    }

    @Override
    @NonNull
    public abstract String getUrl();

    public void setUrl(@NonNull String url) {
        // Stub
    }

    @Override
    public void setFetchError(@Nullable String error) {
        mFetchError = error;
    }

    @Override
    @Nullable
    public String getFetchError() {
        return mFetchError;
    }

    @Override
    @NonNull
    public RepositorySourceProvider getProvider() {
        return mProvider;
    }

    public void setProvider(@NonNull RepositorySourceProvider provider) {
        mProvider = provider;
    }

    @Override
    @Nullable
    public String getDisplayName() {
        // Stub. Implementation for compatibility with old versions.
        return getName();
    }

    @Nullable
    public String getName() {
        return null;
    }

}
