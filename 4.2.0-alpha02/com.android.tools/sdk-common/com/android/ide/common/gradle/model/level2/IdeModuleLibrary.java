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
package com.android.ide.common.gradle.model.level2;

import static com.android.ide.common.gradle.model.level2.IdeLibraryFactory.defaultValueIfNotPresent;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeModel;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

/** Creates a deep copy of {@link Library} of type LIBRARY_MODULE. */
public final class IdeModuleLibrary implements IdeLibrary {
    @NonNull private final String myArtifactAddress;
    @Nullable private final String myBuildId;
    @Nullable private final String myProjectPath;
    @Nullable private final String myVariant;

    private final boolean myIsProvided;
    private final int myType;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeModuleLibrary() {
        myArtifactAddress = "";
        myBuildId = null;
        myProjectPath = null;
        myVariant = null;
        myIsProvided = false;
        myType = 0;

        myHashCode = 0;
    }

    public IdeModuleLibrary(@NonNull AndroidLibrary library, @NonNull String artifactAddress) {
        myType = LIBRARY_MODULE;
        myArtifactAddress = artifactAddress;
        myBuildId = IdeModel.copyNewProperty(library::getBuildId, null);
        myProjectPath = IdeModel.copyNewProperty(library::getProject, null);
        myVariant = IdeModel.copyNewProperty(library::getProjectVariant, null);
        myIsProvided = defaultValueIfNotPresent(() -> library.isProvided(), false);
        myHashCode = calculateHashCode();
    }

    IdeModuleLibrary(@NonNull JavaLibrary library, @NonNull String artifactAddress) {
        myType = LIBRARY_MODULE;
        myArtifactAddress = artifactAddress;
        myBuildId = IdeModel.copyNewProperty(library::getBuildId, null);
        myProjectPath = IdeModel.copyNewProperty(library::getProject, null);
        myIsProvided = defaultValueIfNotPresent(() -> library.isProvided(), false);
        myVariant = null;
        myHashCode = calculateHashCode();
    }

    @VisibleForTesting
    public IdeModuleLibrary(
            @NonNull String projectPath,
            @NonNull String artifactAddress,
            @Nullable String buildId) {
        myType = LIBRARY_MODULE;
        myArtifactAddress = artifactAddress;
        myBuildId = buildId;
        myProjectPath = projectPath;
        myVariant = null;
        myIsProvided = false;
        myHashCode = calculateHashCode();
    }

    @Override
    public int getType() {
        return myType;
    }

    @Override
    @NonNull
    public String getArtifactAddress() {
        return myArtifactAddress;
    }

    @Override
    @NonNull
    public File getArtifact() {
        throw unsupportedMethodForModuleLibrary("getArtifact()");
    }

    @Nullable
    @Override
    public String getBuildId() {
        return myBuildId;
    }

    @Override
    @Nullable
    public String getProjectPath() {
        return myProjectPath;
    }

    @Override
    @Nullable
    public String getVariant() {
        return myVariant;
    }

    @Override
    @NonNull
    public File getFolder() {
        throw unsupportedMethodForModuleLibrary("getFolder");
    }

    @Override
    @NonNull
    public String getManifest() {
        throw unsupportedMethodForModuleLibrary("getManifest");
    }

    @Override
    @NonNull
    public String getJarFile() {
        throw unsupportedMethodForModuleLibrary("getJarFile");
    }

    @Override
    @NonNull
    public String getCompileJarFile() {
        throw unsupportedMethodForModuleLibrary("getCompileJarFile");
    }

    @Override
    @NonNull
    public String getResFolder() {
        throw unsupportedMethodForModuleLibrary("getResFolder");
    }

    @Nullable
    @Override
    public File getResStaticLibrary() {
        throw unsupportedMethodForModuleLibrary("getResStaticLibrary");
    }

    @Override
    @NonNull
    public String getAssetsFolder() {
        throw unsupportedMethodForModuleLibrary("getAssetsFolder");
    }

    @Override
    @NonNull
    public Collection<String> getLocalJars() {
        throw unsupportedMethodForModuleLibrary("getLocalJars");
    }

    @Override
    @NonNull
    public String getJniFolder() {
        throw unsupportedMethodForModuleLibrary("getJniFolder");
    }

    @Override
    @NonNull
    public String getAidlFolder() {
        throw unsupportedMethodForModuleLibrary("getAidlFolder");
    }

    @Override
    @NonNull
    public String getRenderscriptFolder() {
        throw unsupportedMethodForModuleLibrary("getRenderscriptFolder");
    }

    @Override
    @NonNull
    public String getProguardRules() {
        throw unsupportedMethodForModuleLibrary("getProguardRules");
    }

    @Override
    @NonNull
    public String getLintJar() {
        throw unsupportedMethodForModuleLibrary("getLintJar");
    }

    @Override
    @NonNull
    public String getExternalAnnotations() {
        throw unsupportedMethodForModuleLibrary("getExternalAnnotations");
    }

    @Override
    @NonNull
    public String getPublicResources() {
        throw unsupportedMethodForModuleLibrary("getPublicResources");
    }

    @Override
    @NonNull
    public String getSymbolFile() {
        throw unsupportedMethodForModuleLibrary("getSymbolFile");
    }

    @Override
    public boolean isProvided() {
        return myIsProvided;
    }

    @NonNull
    private static UnsupportedOperationException unsupportedMethodForModuleLibrary(
            @NonNull String methodName) {
        return new UnsupportedOperationException(
                methodName + "() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeModuleLibrary)) {
            return false;
        }
        IdeModuleLibrary that = (IdeModuleLibrary) o;
        return myType == that.myType
                && Objects.equals(myArtifactAddress, that.myArtifactAddress)
                && Objects.equals(myProjectPath, that.myProjectPath)
                && Objects.equals(myBuildId, that.myBuildId)
                && Objects.equals(myVariant, that.myVariant)
                && Objects.equals(myIsProvided, that.myIsProvided);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myType, myArtifactAddress, myBuildId, myProjectPath, myVariant, myIsProvided);
    }

    @Override
    public String toString() {
        return "IdeModuleLibrary{"
                + "myType="
                + myType
                + ", myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myBuildId='"
                + myBuildId
                + '\''
                + ", myProjectPath='"
                + myProjectPath
                + '\''
                + ", myVariant='"
                + myVariant
                + '\''
                + ", myIsProvided='"
                + myIsProvided
                + '\''
                + '}';
    }
}
