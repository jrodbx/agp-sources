/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.gradle.internal.core.Abi;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;

public class AbiSplitOptions extends SplitOptions implements com.android.build.api.dsl.AbiSplit {

    private boolean universalApk = false;

    @Override
    protected Set<String> getDefaultValues() {
        Set<String> values = Sets.newHashSet();
        for (Abi abi : Abi.getDefaultValues()) {
            values.add(abi.getTag());
        }
        return values;
    }

    @Override
    protected ImmutableSet<String> getAllowedValues() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Abi abi : Abi.values()) {
            builder.add(abi.getTag());
        }
        return builder.build();
    }

    @Override
    public boolean isUniversalApk() {
        return universalApk;
    }

    @Override
    public void setUniversalApk(boolean universalApk) {
        this.universalApk = universalApk;
    }

    /**
     * Returns the list of actual abi filters, each value of the collection is guaranteed to be non
     * null and of the possible API value.
     * @param allFilters list of applicable filters.
     */
    @NonNull
    public static ImmutableSet<String> getAbiFilters(@NonNull Set<String> allFilters) {
        ImmutableSet.Builder<String> filters = ImmutableSet.builder();
        for (@Nullable String abi : allFilters) {
            // use object equality since abi can be null.
            //noinspection StringEquality
            if (abi != OutputFile.NO_FILTER) {
                filters.add(abi);
            }
        }
        return filters.build();
    }
}
