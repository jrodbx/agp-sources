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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

/**
 * Implementation of CoreExternalNativeCmakeOptions used to merge multiple configs together.
 */
public class MergedExternalNativeCmakeOptions implements CoreExternalNativeCmakeOptions {
    @NonNull
    private final List<String> arguments = Lists.newArrayList();
    @NonNull
    private final List<String> cFlags = Lists.newArrayList();
    @NonNull
    private final List<String> cppFlags = Lists.newArrayList();
    @NonNull
    private final Set<String> abiFilters = Sets.newHashSet();
    @NonNull
    private final Set<String> targets = Sets.newHashSet();

    public void reset() {
        arguments.clear();
        cFlags.clear();
        cppFlags.clear();
        abiFilters.clear();
        targets.clear();
    }

    public void append(@NonNull CoreExternalNativeCmakeOptions options) {
        arguments.addAll(options.getArguments());
        cFlags.addAll(options.getcFlags());
        cppFlags.addAll(options.getCppFlags());
        abiFilters.addAll(options.getAbiFilters());
        targets.addAll(options.getTargets());
    }

    @NonNull
    @Override
    public List<String> getArguments() {
        return arguments;
    }

    @NonNull
    @Override
    public List<String> getcFlags() {
        return cFlags;
    }

    @NonNull
    @Override
    public List<String> getCppFlags() {
        return cppFlags;
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @NonNull
    @Override
    public Set<String> getTargets() {
        return targets;
    }
}

