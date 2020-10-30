/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl.ndk.v1;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeAndroidProject;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeArtifact;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeSettings;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantInfo;
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
    @NonNull private final Map<String, IdeNativeVariantInfo> myVariantInfos;
    @NonNull private final Collection<IdeNativeArtifact> myArtifacts;
    @NonNull private final Collection<IdeNativeToolchain> myToolChains;
    @NonNull private final Collection<IdeNativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    @Nullable private final Collection<String> myBuildSystems;
    @NonNull private final String myDefaultNdkVersion;
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
        myDefaultNdkVersion = "";
        myApiVersion = 0;

        myHashCode = 0;
    }

    public IdeNativeAndroidProjectImpl(
            @NonNull String modelVersion,
            int apiVersion,
            @NonNull String name,
            @NonNull List<File> buildFiles,
            @NonNull Map<String, IdeNativeVariantInfo> variantInfos,
            @NonNull List<IdeNativeArtifact> artifacts,
            @NonNull List<IdeNativeToolchain> toolChains,
            @NonNull List<IdeNativeSettings> settings,
            @NonNull Map<String, String> fileExtensions,
            @NonNull String defaultNdkVersion,
            @Nullable List<String> buildSystems) {
        myModelVersion = modelVersion;
        myApiVersion = apiVersion;
        myName = name;
        myBuildFiles = buildFiles;
        myVariantInfos = variantInfos;
        myArtifacts = artifacts;
        myToolChains = toolChains;
        mySettings = settings;
        myFileExtensions = fileExtensions;
        myDefaultNdkVersion = defaultNdkVersion;
        myBuildSystems = buildSystems;
        myHashCode = calculateHashCode();
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
    public Map<String, IdeNativeVariantInfo> getVariantInfos() {
        return myVariantInfos;
    }

    @Override
    @NonNull
    public Collection<File> getBuildFiles() {
        return myBuildFiles;
    }

    @Override
    @NonNull
    public Collection<IdeNativeArtifact> getArtifacts() {
        return myArtifacts;
    }

    @Override
    @NonNull
    public Collection<IdeNativeToolchain> getToolChains() {
        return myToolChains;
    }

    @Override
    @NonNull
    public Collection<IdeNativeSettings> getSettings() {
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
        return myBuildSystems;
    }

    @NonNull
    @Override
    public String getDefaultNdkVersion() {
        if (myDefaultNdkVersion != null) {
            return myDefaultNdkVersion;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: NativeAndroidProject.getDefaultNdkVersion()");
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
                && Objects.equals(myBuildSystems, project.myBuildSystems)
                && Objects.equals(myDefaultNdkVersion, project.myDefaultNdkVersion);
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
                myApiVersion,
                myDefaultNdkVersion);
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
                + ", myDefaultNdkVersion="
                + myDefaultNdkVersion
                + "}";
    }
}
