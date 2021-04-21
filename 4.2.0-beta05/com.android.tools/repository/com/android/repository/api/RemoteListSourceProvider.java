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
import com.android.repository.impl.sources.RemoteListSourceProviderImpl;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

/**
 * An {@link RepositorySourceProvider} that retrieves {@link RepositorySource}s from a remote
 * location.
 */
public abstract class RemoteListSourceProvider implements RepositorySourceProvider {

    /**
     * Creates a new provider.
     *
     * @param url                    The URL to download from.
     * @param sourceListModule       Extension to the common source list schema, if any, used to
     *                               parse the downloaded xml.
     * @param permittedSchemaModules The {@link SchemaModule}s that are allowed to be used by the
     *                               {@link RepositorySource}s created by this provider, depending
     *                               on the actual type of site.
     * @return The created provider.
     * @throws URISyntaxException If {@code url} is invalid.
     */
    @NonNull
    public static RemoteListSourceProvider create(@NonNull String url,
            @Nullable SchemaModule sourceListModule,
            @NonNull Map<Class<? extends RepositorySource>,
                    Collection<SchemaModule<?>>> permittedSchemaModules)
            throws URISyntaxException {
        return new RemoteListSourceProviderImpl(url, sourceListModule, permittedSchemaModules);
    }

    public interface GenericSite extends RepositorySource {}
}
