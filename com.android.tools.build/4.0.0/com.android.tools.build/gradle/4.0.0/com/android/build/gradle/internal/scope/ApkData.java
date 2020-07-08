/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * FIX ME : wrong name but convenient until I can clean up existing classes/namespace.
 *
 * <p>This split represents a Variant output, which can be a main (base) split, a full split, a
 * configuration pure splits. Each variant has one to many of such outputs depending on its
 * configuration.
 *
 * <p>this is used to model outputs of a variant during configuration and it is sometimes altered at
 * execution when new pure splits are discovered.
 */
public abstract class ApkData implements VariantOutput, Comparable<ApkData>, Serializable {

    private static final Comparator<ApkData> COMPARATOR =
            Comparator.nullsLast(
                    Comparator.comparing(ApkData::getType)
                            .thenComparingInt(ApkData::getVersionCode)
                            .thenComparing(
                                    ApkData::getOutputFileName,
                                    Comparator.nullsLast(String::compareTo))
                            .thenComparing(
                                    ApkData::getVersionName,
                                    Comparator.nullsLast(String::compareTo)));

    // TODO : move it to a subclass, we cannot override versions for SPLIT
    public transient com.android.build.api.variant.VariantOutput variantOutput;
    private Integer versionCode = 0;
    private String versionName = null;
    private String outputFileName;


    public ApkData() {}

    public static ApkData of(
            @NonNull VariantOutput.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode) {
        return of(outputType, filters, versionCode, null, null, null, "", "", "");
    }

    public static ApkData of(
            @NonNull VariantOutput.OutputType outputType,
            @NonNull Collection<FilterData> filters,
            int versionCode,
            @Nullable String versionName,
            @Nullable String filterName,
            @Nullable String outputFileName,
            @NonNull String fullName,
            @NonNull String baseName,
            @NonNull String dirName) {
        return new DefaultApkData(
                outputType,
                filters,
                versionCode,
                versionName,
                filterName,
                outputFileName,
                fullName,
                baseName,
                dirName);
    }

    @NonNull
    @Nested
    public Collection<GradleAwareFilterData> getFiltersForGradle() {
        return getFilters()
                .stream()
                .map(it -> (GradleAwareFilterData) it)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @NonNull
    @Override
    @Internal // represented by getFiltersForGradle
    public Collection<FilterData> getFilters() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    @Input
    public Collection<String> getFilterTypes() {
        return getFilters().stream().map(FilterData::getFilterType).collect(Collectors.toList());
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public FilterData getFilter(@NonNull VariantOutput.FilterType filterType) {
        for (FilterData filter : getFilters()) {
            if (VariantOutput.FilterType.valueOf(filter.getFilterType()) == filterType) {
                return filter;
            }
        }
        return null;
    }

    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(getFilters(), VariantOutput.FilterType.valueOf(filterType));
    }

    public boolean requiresAapt() {
        return true;
    }

    @NonNull
    @Input
    public abstract String getBaseName();

    @NonNull
    @Input
    public abstract String getFullName();

    @NonNull
    @Input
    public abstract VariantOutput.OutputType getType();

    @Input
    public boolean isUniversal() {
        return false;
    }

    /**
     * Returns a directory name relative to a variant specific location to save split specific
     * output files or null to use the variant specific folder.
     *
     * @return a directory name of null.
     */
    @NonNull
    @Input
    public abstract String getDirName();

    public void setOutputFileName(@NonNull String outputFileName) {
        this.outputFileName = outputFileName;
    }

    // TODO : We need to remove this from this API and always go directly to the
    // Variant API variantOutput.
    @Input
    @Override
    public int getVersionCode() {
        if (variantOutput != null) {
            return variantOutput.getVersionCode().get();
        }
        return versionCode;
    }

    // TODO : We need to remove this from this API and always go directly to the
    // Variant API variantOutput.
    @Nullable
    @Input
    @Optional
    public String getVersionName() {
        if (variantOutput != null) {
            return variantOutput.getVersionName().getOrNull();
        }
        return versionName;
    }

    @Nullable
    @Input
    @Optional
    public String getOutputFileName() {
        return outputFileName;
    }

    @NonNull
    @Override
    @Internal
    public OutputFile getMainOutputFile() {
        throw new UnsupportedOperationException(
                "getMainOutputFile is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    @Internal
    public Collection<? extends OutputFile> getOutputs() {
        throw new UnsupportedOperationException(
                "getOutputs is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", getType())
                .add("fullName", getFullName())
                .add("filters", getFilters())
                .add("versionCode", getVersionCode())
                .add("versionName", getVersionName())
                .toString();
    }

    @NonNull
    @Override
    @Input
    public String getOutputType() {
        return getType().name();
    }

    // FIX-ME: we can have more than one value, especially for languages...
    // so far, we will return things like "fr,fr-rCA" for a single value.
    @Nullable
    public static String getFilter(
            Collection<FilterData> filters, OutputFile.FilterType filterType) {

        for (FilterData filter : filters) {
            if (VariantOutput.FilterType.valueOf(filter.getFilterType()) == filterType) {
                return filter.getIdentifier();
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ApkData that = (ApkData) o;
        return getVersionCode() == that.getVersionCode()
                && Objects.equals(outputFileName, that.outputFileName)
                && Objects.equals(getVersionName(), that.getVersionName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVersionCode(), getVersionName(), outputFileName);
    }

    @Override
    public int compareTo(ApkData other) {
        return COMPARATOR.compare(this, other);
    }

    @Nullable
    @Optional
    @Input
    public abstract String getFilterName();

    public void setVariantOutput(VariantOutputImpl variantOutput) {
        this.variantOutput = variantOutput;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        versionCode = getVersionCode();
        versionName = getVersionName();
        out.defaultWriteObject();
    }
}
