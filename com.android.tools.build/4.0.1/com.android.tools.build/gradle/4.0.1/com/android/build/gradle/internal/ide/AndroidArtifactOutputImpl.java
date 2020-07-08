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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of AndroidArtifactOutput that is serializable
 */
@Immutable
final class AndroidArtifactOutputImpl implements AndroidArtifactOutput, Serializable {
    private static final long serialVersionUID = 1L;

    private final EarlySyncBuildOutput mainOutput;
    // even if we have pure splits, only one manifest file really matters.
    private final EarlySyncBuildOutput manifestOutput;
    private final Collection<EarlySyncBuildOutput> splitApksOutputs;

    public AndroidArtifactOutputImpl(
            EarlySyncBuildOutput mainOutput, EarlySyncBuildOutput manifestOutput) {
        this(mainOutput, manifestOutput, ImmutableList.of());
    }

    public AndroidArtifactOutputImpl(
            EarlySyncBuildOutput mainApk,
            EarlySyncBuildOutput manifestOutput,
            List<EarlySyncBuildOutput> splitApksOutputs) {
        this.mainOutput = mainApk;
        this.manifestOutput = manifestOutput;
        this.splitApksOutputs = splitApksOutputs;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return getMainOutputFile().getOutputFile();
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        return mainOutput;
    }

    @NonNull
    @Override
    public Collection<OutputFile> getOutputs() {
        ImmutableList.Builder<OutputFile> outputFileBuilder = ImmutableList.builder();
        outputFileBuilder.add(mainOutput);
        splitApksOutputs.forEach(outputFileBuilder::add);
        return outputFileBuilder.build();
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        throw new RuntimeException("Deprecated.");
    }

    @NonNull
    @Override
    public String getOutputType() {
        return mainOutput.getOutputType();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return mainOutput.getFilterTypes();
    }

    @Nullable
    public String getFilter(String filterType) {
        return mainOutput.getFilter(filterType);
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return mainOutput.getFilters();
    }

    @NonNull
    @Override
    public File getGeneratedManifest() {
        return manifestOutput.getOutputFile();
    }

    @Override
    public int getVersionCode() {
        return mainOutput.getVersionCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndroidArtifactOutputImpl that = (AndroidArtifactOutputImpl) o;
        return Objects.equals(mainOutput, that.mainOutput)
                && Objects.equals(manifestOutput, that.manifestOutput)
                && Objects.equals(splitApksOutputs, that.splitApksOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitApksOutputs, manifestOutput, mainOutput);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("output", mainOutput)
                .add("manifest", manifestOutput)
                .add("pure splits", splitApksOutputs)
                .toString();
    }
}
