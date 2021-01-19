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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Represents a resource output from a variant configuration.
 *
 * Depending on split requirements, there can be more than one output from aapt tool and each
 * output file is represented by an instance of this class.
 */
public class ApkOutputFile implements OutputFile, Serializable {

    @NonNull private final Collection<FilterData> filters;
    @NonNull private final Collection<String> filterTypes;
    @NonNull private final OutputFile.OutputType outputType;
    @NonNull private final Callable<File> outputFile;
    private final int versionCode;

    public ApkOutputFile(
            @NonNull OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @NonNull Callable<File> outputFile,
            int versionCode) {
        this.outputType = outputType;
        this.outputFile = outputFile;
        this.filters = filters;
        ImmutableList.Builder<String> filterTypes = ImmutableList.builder();
        for (FilterData filter : filters) {
            filterTypes.add(filter.getFilterType());
        }
        this.filterTypes = filterTypes.build();
        this.versionCode = versionCode;
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    @Override
    public Collection<? extends OutputFile> getOutputs() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    public OutputFile.OutputType getType() {
        return outputType;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        try {
            return outputFile.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @NonNull
    public Collection<FilterData> getFilters() {
        return filters;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("OutputType", outputType)
                .add(
                        "Filters",
                        Joiner.on(',')
                                .join(
                                        filters,
                                        (Function<FilterData, String>)
                                                splitData ->
                                                        '['
                                                                + splitData.getFilterType()
                                                                + ':'
                                                                + splitData.getIdentifier()
                                                                + ']'))
                .add("File", getOutputFile().getAbsolutePath())
                .toString();
    }

    @NonNull
    @Override
    public String getOutputType() {
        return outputType.name();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return filterTypes;
    }

    @Nullable
    public String getFilterByType(FilterType filterType) {
        for (FilterData filter : filters) {
            if (filter.getFilterType().equals(filterType.name())) {
                return filter.getIdentifier();
            }
        }
        return null;
    }

    /**
     * Returns the split identifier (like "hdpi" for a density split) given the split dimension.
     * @param filterType the string representation of {@code SplitType} split dimension used to
     *                   create the APK.
     * @return the split identifier or null if there was not split of that dimension.
     */
    @Nullable
    public String getFilter(String filterType) {
        return getFilterByType(FilterType.valueOf(filterType));
    }

    @Override
    public int getVersionCode() {
        return versionCode;
    }
}
