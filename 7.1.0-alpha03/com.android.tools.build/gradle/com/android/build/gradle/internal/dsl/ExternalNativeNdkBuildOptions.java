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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class ExternalNativeNdkBuildOptions
        implements CoreExternalNativeNdkBuildOptions,
                com.android.build.api.dsl.ExternalNativeNdkBuildOptions {
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

    @Inject
    public ExternalNativeNdkBuildOptions() {}

    @NonNull
    @Override
    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(@NonNull List<String> arguments) {
        this.arguments.addAll(arguments);
    }

    @Override
    public void arguments(@NonNull String... arguments) {
        Collections.addAll(this.arguments, arguments);
    }

    @NonNull
    @Override
    public List<String> getCFlags() {
        return cFlags;
    }

    public void setCFlags(@NonNull List<String> cFlags) {
        this.cFlags.addAll(cFlags);
    }

    @NonNull
    @Override
    public List<String> getcFlags() {
        return getCFlags();
    }

    public void setcFlags(@NonNull List<String> flags) {
        setCFlags(flags);
    }

    @Override
    public void cFlags(@NonNull String... flags) {
        Collections.addAll(this.cFlags, flags);
    }

    @NonNull
    @Override
    public List<String> getCppFlags() {
        return cppFlags;
    }

    public void setCppFlags(@NonNull List<String> flags) {
        this.cppFlags.addAll(flags);
    }

    @Override
    public void cppFlags(@NonNull String... flags) {
        Collections.addAll(this.cppFlags, flags);
    }

    @NonNull
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(@NonNull Set<String> abiFilters) {
        this.abiFilters.addAll(abiFilters);
    }

    @Override
    public void abiFilters(@NonNull String... abiFilters) {
        Collections.addAll(this.abiFilters, abiFilters);
    }

    @NonNull
    @Override
    public Set<String> getTargets() {
        return targets;
    }

    public void setTargets(@NonNull Set<String> targets) {
        this.targets.addAll(targets);
    }

    @Override
    public void targets(@NonNull String... targets) {
        Collections.addAll(this.targets, targets);
    }

    public void _initWith(CoreExternalNativeNdkBuildOptions that) {
        setArguments(that.getArguments());
        setcFlags(that.getcFlags());
        setCppFlags(that.getCppFlags());
        setAbiFilters(that.getAbiFilters());
        setTargets(that.getTargets());
    }
}
