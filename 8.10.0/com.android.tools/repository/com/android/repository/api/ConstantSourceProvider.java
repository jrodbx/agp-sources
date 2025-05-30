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
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

/**
 * A {@link RepositorySourceProvider} that provides a single source allowing a single set of schema
 * modules.
 */
public class ConstantSourceProvider implements RepositorySourceProvider {

    private final RepositorySource mSource;

    public ConstantSourceProvider(@NonNull String url, @NonNull String uiName,
            @NonNull Collection<SchemaModule<?>> permittedSchemaModules) {
        // TODO: persist enabled state (this isn't done currently either). Probably it will be
        //       in the file that stores locally-added sites.
        mSource = new SimpleRepositorySource(url, uiName, true, permittedSchemaModules, this);
    }

    @Override
    @NonNull
    public List<RepositorySource> getSources(@Nullable Downloader downloader,
            @Nullable ProgressIndicator indicator, boolean forceRefresh) {
        return ImmutableList.of(mSource);
    }

    @Override
    public boolean addSource(@NonNull RepositorySource source) {
        return false;
    }

    @Override
    public boolean isModifiable() {
        return false;
    }

    @Override
    public void save(@NonNull ProgressIndicator progress) {
        // nothing since it's not modifiable
    }

    @Override
    public boolean removeSource(@NonNull RepositorySource source) {
        return false;
    }
}
