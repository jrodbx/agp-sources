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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.NativeVariantInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdeNativeAndroidProjectImpl implements IdeNativeAndroidProject, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Map<String, NativeVariantInfo> myVariantInfos;
    @NonNull private final Collection<NativeArtifact> myArtifacts;
    @NonNull private final Collection<NativeToolchain> myToolChains;
    @NonNull private final Collection<NativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    @Nullable private final Collection<String> myBuildSystems;
    private final int myApiVersion;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeAndroidProjectImpl() {
        myModelVersion = "";
        myName = "";
        myBuildFiles = Collections.emptyList();
        myVariantInfos = Collections.emptyMap();
        myArtifacts = Collections.emptyList();
        myToolChains = Collections.emptyList();
        mySettings = Collections.emptyList();
        myFileExtensions = Collections.emptyMap();
        myBuildSystems = Collections.emptyList();
        myApiVersion = 0;

        myHashCode = 0;
    }

    public IdeNativeAndroidProjectImpl(@NonNull NativeAndroidProject project) {
        this(project, new ModelCache());
    }

    @VisibleForTesting
    IdeNativeAndroidProjectImpl(
            @NonNull NativeAndroidProject project, @NonNull ModelCache modelCache) {
        myModelVersion = project.getModelVersion();
        myApiVersion = project.getApiVersion();
        myName = project.getName();
        myBuildFiles = ImmutableList.copyOf(project.getBuildFiles());
        myVariantInfos = copyVariantInfos(project, modelCache);
        myArtifacts =
                IdeModel.copy(
                        project.getArtifacts(),
                        modelCache,
                        artifact -> new IdeNativeArtifact(artifact, modelCache));
        myToolChains =
                IdeModel.copy(
                        project.getToolChains(),
                        modelCache,
                        toolchain -> new IdeNativeToolchain(toolchain));
        mySettings =
                IdeModel.copy(
                        project.getSettings(),
                        modelCache,
                        settings -> new IdeNativeSettings(settings));
        myFileExtensions = ImmutableMap.copyOf(project.getFileExtensions());
        myBuildSystems = copyBuildSystems(project);
        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Map<String, NativeVariantInfo> copyVariantInfos(
            @NonNull NativeAndroidProject project, @NonNull ModelCache modelCache) {
        try {
            return IdeModel.copy(
                    project.getVariantInfos(),
                    modelCache,
                    variantInfo ->
                            new IdeNativeVariantInfo(
                                    variantInfo.getAbiNames(),
                                    Objects.requireNonNull(
                                            IdeModel.copyNewProperty(
                                                    () -> variantInfo.getBuildRootFolderMap(),
                                                    Collections.emptyMap()))));
        } catch (UnsupportedOperationException e) {
            return Maps.newHashMap();
        }
    }

    @Nullable
    private static Collection<String> copyBuildSystems(@NonNull NativeAndroidProject project) {
        try {
            return ImmutableList.copyOf(project.getBuildSystems());
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @NonNull
    @Override
    public Map<String, NativeVariantInfo> getVariantInfos() {
        return myVariantInfos;
    }

    @Override
    @NonNull
    public Collection<File> getBuildFiles() {
        return myBuildFiles;
    }

    @Override
    @NonNull
    public Collection<NativeArtifact> getArtifacts() {
        return myArtifacts;
    }

    @Override
    @NonNull
    public Collection<NativeToolchain> getToolChains() {
        return myToolChains;
    }

    @Override
    @NonNull
    public Collection<NativeSettings> getSettings() {
        return mySettings;
    }

    @Override
    @NonNull
    public Map<String, String> getFileExtensions() {
        return myFileExtensions;
    }

    @Override
    @NonNull
    public Collection<String> getBuildSystems() {
        if (myBuildSystems != null) {
            return myBuildSystems;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: NativeAndroidProject.getBuildSystems()");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeAndroidProjectImpl)) {
            return false;
        }
        IdeNativeAndroidProjectImpl project = (IdeNativeAndroidProjectImpl) o;
        return myApiVersion == project.myApiVersion
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myBuildFiles, project.myBuildFiles)
                && Objects.equals(myVariantInfos, project.myVariantInfos)
                && Objects.equals(myArtifacts, project.myArtifacts)
                && Objects.equals(myToolChains, project.myToolChains)
                && Objects.equals(mySettings, project.mySettings)
                && Objects.equals(myFileExtensions, project.myFileExtensions)
                && Objects.equals(myBuildSystems, project.myBuildSystems);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myName,
                myBuildFiles,
                myVariantInfos,
                myArtifacts,
                myToolChains,
                mySettings,
                myFileExtensions,
                myBuildSystems,
                myApiVersion);
    }

    @Override
    public String toString() {
        return "IdeNativeAndroidProject{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myBuildFiles="
                + myBuildFiles
                + ", myArtifacts="
                + myArtifacts
                + ", myToolChains="
                + myToolChains
                + ", mySettings="
                + mySettings
                + ", myFileExtensions="
                + myFileExtensions
                + ", myBuildSystems="
                + myBuildSystems
                + ", myApiVersion="
                + myApiVersion
                + "}";
    }

    public static class FactoryImpl implements Factory {
        @Override
        @NonNull
        public IdeNativeAndroidProject create(@NonNull NativeAndroidProject project) {
            return new IdeNativeAndroidProjectImpl(project);
        }
    }
}
