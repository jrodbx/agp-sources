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
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeArtifact;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeSettings;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeVariantAbi;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdeNativeVariantAbiImpl implements IdeNativeVariantAbi, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Collection<IdeNativeArtifact> myArtifacts;
    @NonNull private final Collection<IdeNativeToolchain> myToolChains;
    @NonNull private final Collection<IdeNativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    @NonNull private final String myVariantName;
    @NonNull private final String myAbi;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeVariantAbiImpl() {
        myBuildFiles = Collections.emptyList();
        myArtifacts = Collections.emptyList();
        myToolChains = Collections.emptyList();
        mySettings = Collections.emptyList();
        myFileExtensions = Collections.emptyMap();
        myVariantName = "";
        myAbi = "";

        myHashCode = 0;
    }

    public IdeNativeVariantAbiImpl(
            @NonNull List<File> buildFiles,
            @NonNull List<IdeNativeArtifact> artifacts,
            @NonNull List<IdeNativeToolchain> toolChains,
            @NonNull List<IdeNativeSettings> settings,
            @NonNull Map<String, String> fileExtensions,
            @NonNull String variantName,
            @NonNull String abi) {
        myBuildFiles = buildFiles;
        myArtifacts = artifacts;
        myToolChains = toolChains;
        mySettings = settings;
        myFileExtensions = fileExtensions;
        myVariantName = variantName;
        myAbi = abi;
        myHashCode = calculateHashCode();
    }

    @NonNull
    @Override
    public Collection<File> getBuildFiles() {
        return myBuildFiles;
    }

    @NonNull
    @Override
    public Collection<IdeNativeArtifact> getArtifacts() {
        return myArtifacts;
    }

    @NonNull
    @Override
    public Collection<IdeNativeToolchain> getToolChains() {
        return myToolChains;
    }

    @NonNull
    @Override
    public Collection<IdeNativeSettings> getSettings() {
        return mySettings;
    }

    @NonNull
    @Override
    public Map<String, String> getFileExtensions() {
        return myFileExtensions;
    }

    @NonNull
    @Override
    public String getVariantName() {
        return myVariantName;
    }

    @NonNull
    @Override
    public String getAbi() {
        return myAbi;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myBuildFiles,
                myArtifacts,
                myToolChains,
                mySettings,
                myFileExtensions,
                myVariantName,
                myAbi);
    }

    @Override
    public String toString() {
        return "IdeNativeVariantAbi{"
                + "myVariantName="
                + myVariantName
                + "myAbi="
                + myAbi
                + "myBuildFiles="
                + myBuildFiles
                + ", myArtifacts="
                + myArtifacts
                + ", myToolChains="
                + myToolChains
                + ", mySettings="
                + mySettings
                + ", myFileExtensions="
                + myFileExtensions
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdeNativeVariantAbiImpl)) return false;
        IdeNativeVariantAbiImpl that = (IdeNativeVariantAbiImpl) o;
        return Objects.equals(myBuildFiles, that.myBuildFiles)
                && Objects.equals(myArtifacts, that.myArtifacts)
                && Objects.equals(myToolChains, that.myToolChains)
                && Objects.equals(mySettings, that.mySettings)
                && Objects.equals(myFileExtensions, that.myFileExtensions)
                && Objects.equals(myVariantName, that.myVariantName)
                && Objects.equals(myAbi, that.myAbi);
    }
}
