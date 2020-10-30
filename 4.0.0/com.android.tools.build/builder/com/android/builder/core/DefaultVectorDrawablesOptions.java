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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.VectorDrawablesOptions;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Set;

/**
 * Default implementation of {@link VectorDrawablesOptions}.
 */
public class DefaultVectorDrawablesOptions implements VectorDrawablesOptions, Serializable {

    @Nullable
    private Set<String> mGeneratedDensities;

    @Nullable
    private Boolean mUseSupportLibrary;

    @NonNull
    public static DefaultVectorDrawablesOptions copyOf(@NonNull VectorDrawablesOptions original) {
        DefaultVectorDrawablesOptions options = new DefaultVectorDrawablesOptions();

        options.setGeneratedDensities(original.getGeneratedDensities());
        options.setUseSupportLibrary(original.getUseSupportLibrary());

        return options;
    }

    /**
     * Densities used when generating PNGs from vector drawables at build time. For the PNGs to be
     * generated, minimum SDK has to be below 21.
     *
     * <p>If set to an empty collection, all special handling of vector drawables will be
     * disabled.
     *
     * <p>See <a href="http://developer.android.com/guide/practices/screens_support.html">
     * Supporting Multiple Screens</a>.
     */
    @Nullable
    @Override
    public Set<String> getGeneratedDensities() {
        return mGeneratedDensities;
    }

    public void setGeneratedDensities(@Nullable Iterable<String> densities) {
        if (densities == null) {
            mGeneratedDensities = null;
        } else {
            mGeneratedDensities = Sets.newHashSet(densities);
        }
    }

    /**
     * Whether to use runtime support for {@code vector} drawables, instead of build-time support.
     *
     * <p>See <a href="http://developer.android.com/tools/help/vector-asset-studio.html">
     *     Vector Asset Studio</a>.
     */
    @Override
    @Nullable
    public Boolean getUseSupportLibrary() {
        return mUseSupportLibrary;
    }

    public void setUseSupportLibrary(@Nullable Boolean useSupportLibrary) {
        mUseSupportLibrary = useSupportLibrary;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mGeneratedDensities", mGeneratedDensities)
                .add("mUseSupportLibrary", mUseSupportLibrary)
                .toString();
    }
}
