/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.NdkOptions;
import com.android.build.gradle.internal.dsl.NdkOptions.DebugSymbolLevel;
import com.google.common.collect.Lists;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Implementation of NdkConfig used to merge multiple configs together. */
public class MergedNdkConfig implements CoreNdkOptions, MergedOptions<CoreNdkOptions> {

    private String moduleName;
    private String cFlags;
    private List<String> ldLibs;
    private final Set<String> abiFilters = new HashSet<>(0);
    private String stl;
    private Integer jobs;
    private String debugSymbolLevel;

    @Override
    public void reset() {
        moduleName = null;
        cFlags = null;
        ldLibs = null;
        abiFilters.clear();
        debugSymbolLevel = null;
    }

    @Override
    @Nullable
    public String getModuleName() {
        return moduleName;
    }

    @Override
    @Nullable
    public String getcFlags() {
        return cFlags;
    }

    @Override
    @Nullable
    public List<String> getLdLibs() {
        return ldLibs;
    }

    @Override
    @NonNull
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @Override
    @Nullable
    public String getStl() {
        return stl;
    }

    @Override
    @Nullable
    public Integer getJobs() {
        return jobs;
    }

    @Override
    @Nullable
    public String getDebugSymbolLevel() {
        return debugSymbolLevel;
    }

    @Override
    public void append(@NonNull CoreNdkOptions ndkConfig) {
        // override
        if (ndkConfig.getModuleName() != null) {
            moduleName = ndkConfig.getModuleName();
        }

        if (ndkConfig.getStl() != null) {
            stl = ndkConfig.getStl();
        }

        if (ndkConfig.getJobs() != null) {
            jobs = ndkConfig.getJobs();
        }

        if (ndkConfig.getDebugSymbolLevel() != null) {
            debugSymbolLevel = ndkConfig.getDebugSymbolLevel();
        }

        // append
        abiFilters.addAll(ndkConfig.getAbiFilters());

        if (cFlags == null) {
            cFlags = ndkConfig.getcFlags();
        } else if (ndkConfig.getcFlags() != null && !ndkConfig.getcFlags().isEmpty()) {
            cFlags = cFlags + " " + ndkConfig.getcFlags();
        }

        if (ndkConfig.getLdLibs() != null) {
            if (ldLibs == null) {
                ldLibs = Lists.newArrayListWithCapacity(ndkConfig.getLdLibs().size());
            }
            ldLibs.addAll(ndkConfig.getLdLibs());
        }
    }
}
