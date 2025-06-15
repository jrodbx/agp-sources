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

package com.android.build.gradle.internal.api;

import com.android.annotations.Nullable;
import com.android.builder.model.VectorDrawablesOptions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Read-only wrapper around another (@link VectorDrawablesOptions}.
 */
public class ReadOnlyVectorDrawablesOptions implements VectorDrawablesOptions {

    private final VectorDrawablesOptions mOptions;

    public ReadOnlyVectorDrawablesOptions(VectorDrawablesOptions options) {
        mOptions = options;
    }

    @Nullable
    @Override
    public Set<String> getGeneratedDensities() {
        if (mOptions.getGeneratedDensities() == null) {
            return null;
        } else {
            return ImmutableSet.copyOf(mOptions.getGeneratedDensities());
        }
    }

    @Nullable
    @Override
    public Boolean getUseSupportLibrary() {
        return mOptions.getUseSupportLibrary();
    }
}
