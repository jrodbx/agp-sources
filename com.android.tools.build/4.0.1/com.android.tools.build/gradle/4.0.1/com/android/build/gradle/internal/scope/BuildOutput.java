/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.api.artifact.ArtifactType;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.gradle.api.file.FileSystemLocation;

/**
 * A build output like a merged manifest file, a jar file, an APK file.
 *
 * <p>Each output is characterized by its type, the intent APK in which this build output will
 * eventually be packaged in and its file location.
 */
// TODO : rename to BuildElement and make it a data class if possible.
public final class BuildOutput implements OutputFile, Serializable {

    @NonNull private final ArtifactType outputType;
    @NonNull private final ApkData apkData;
    // the right abstraction would be Path but it's not serializable so reconstruct the Path
    // instance from its String representation.
    @NonNull private final String path;
    @NonNull private final Map<String, String> properties;

    public BuildOutput(
            @NonNull ArtifactType outputType, @NonNull ApkData apkData, @NonNull File outputFile) {
        this(outputType, apkData, outputFile, ImmutableMap.of());
    }

    public BuildOutput(
            @NonNull ArtifactType outputType,
            @NonNull ApkData apkData,
            @NonNull File outputFile,
            @NonNull Map<String, String> properties) {
        this(outputType, apkData, outputFile.toPath(), properties);
    }

    public BuildOutput(
            @NonNull ArtifactType outputType,
            @NonNull ApkData apkData,
            @NonNull Path outputPath,
            @NonNull Map<String, String> properties) {
        String systemIndependentPath = FileUtils.toSystemIndependentPath(outputPath.toString());
        this.outputType = outputType;
        this.apkData =
                Preconditions.checkNotNull(
                        apkData,
                        "apkData for %s [%s] is null.",
                        outputType.name(),
                        systemIndependentPath);
        this.path = systemIndependentPath;
        this.properties = properties;
    }

    /**
     * Returns information about the APK that will package this build output. If the {@link
     * #getType()} is {@link InternalArtifactType#APK}, this build output is an APK and this will
     * provide metadata information about that APK.
     *
     * @return APK information about the APK in which this build output will be packaged into.
     */
    @NonNull
    public ApkData getApkData() {
        return apkData;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return Paths.get(path).toFile();
    }

    /**
     * Returns the build output type (like an Android manifest file, a merged resource file, etc..)
     *
     * @return the build output type.
     */
    @NonNull
    public ArtifactType<FileSystemLocation> getType() {
        return outputType;
    }

    /**
     * Return the final APK type this build output will be packaged in. If the {@link #getType()} is
     * {@link InternalArtifactType#APK}, this will return the nature of this APK.
     *
     * @return the apk in which this build output will be packaged in.
     */
    @NonNull
    @Override
    public String getOutputType() {
        return apkData.getType().toString();
    }

    /**
     * Returns the list of filter types {@link OutputFile.FilterType} as Strings.
     *
     * @return the filter types as {@link String}
     */
    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return apkData.getFilters()
                .stream()
                .map(FilterData::getFilterType)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return apkData.getFilters();
    }

    @Nullable
    public String getFilter(String filterType) {
        return ApkData.getFilter(apkData.getFilters(), FilterType.valueOf(filterType));
    }

    /**
     * Implemented for compatibility with VariantOutput protocol. Starting in 2.5, there is one
     * BuildOutput (VariantOutput) per output file of a build. There is therefore no notion of a
     * main output file.
     *
     * @return itself.
     */
    @NonNull
    @Deprecated
    @Override
    public OutputFile getMainOutputFile() {
        return this;
    }

    /**
     * Implemented for compatibility only. Returns the list of output for this VariantOutput, which
     * is itself.
     *
     * @return the list of output for this VariantOutput.
     */
    @NonNull
    @Deprecated
    @Override
    public Collection<? extends OutputFile> getOutputs() {
        return ImmutableList.of(this);
    }

    /**
     * Returns the version code, -1 if not set.
     *
     * @return the version code.
     */
    @Override
    public int getVersionCode() {
        return apkData.getVersionCode();
    }

    /**
     * Returns all the dynamic properties that can be attached to this build output. A dynamic
     * property is a free formed key value pair that are attributes of this build output.
     *
     * @return a map of key value pairs.
     */
    @NonNull
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("apkData", apkData)
                .add("path", path)
                .add("properties", Joiner.on(",").join(properties.entrySet()))
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildOutput that = (BuildOutput) o;
        return outputType == that.outputType
                && Objects.equals(properties, that.properties)
                && Objects.equals(apkData, that.apkData)
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputType, apkData, path, properties);
    }

    public Path getOutputPath() {
        return Paths.get(path);
    }
}
