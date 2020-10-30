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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/** Creates a deep copy of {@link Library} of type LIBRARY_JAVA. */
public final class IdeJavaLibrary implements Library, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myArtifactAddress;
    @NonNull private final File myArtifactFile;
    private final int myType;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeJavaLibrary() {
        myArtifactAddress = "";
        //noinspection ConstantConditions
        myArtifactFile = null;
        myType = 0;

        myHashCode = 0;
    }

    IdeJavaLibrary(@NonNull String artifactAddress, @NonNull File artifactFile) {
        myType = LIBRARY_JAVA;
        myArtifactAddress = artifactAddress;
        myArtifactFile = artifactFile;
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
        return myArtifactFile;
    }

    @Override
    @Nullable
    public String getVariant() {
        throw unsupportedMethodForJavaLibrary("getVariant");
    }

    @Nullable
    @Override
    public String getBuildId() {
        throw unsupportedMethodForJavaLibrary("getBuildId");
    }

    @Override
    @NonNull
    public String getProjectPath() {
        throw unsupportedMethodForJavaLibrary("getProjectPath");
    }

    @Override
    @NonNull
    public File getFolder() {
        throw unsupportedMethodForJavaLibrary("getFolder");
    }

    @Override
    @NonNull
    public String getManifest() {
        throw unsupportedMethodForJavaLibrary("getManifest");
    }

    @Override
    @NonNull
    public String getJarFile() {
        throw unsupportedMethodForJavaLibrary("getJarFile");
    }

    @Override
    @NonNull
    public String getCompileJarFile() {
        throw unsupportedMethodForJavaLibrary("getCompileJarFile");
    }

    @Override
    @NonNull
    public String getResFolder() {
        throw unsupportedMethodForJavaLibrary("getResFolder");
    }

    @Nullable
    @Override
    public File getResStaticLibrary() {
        throw unsupportedMethodForJavaLibrary("getResStaticLibrary");
    }

    @Override
    @NonNull
    public String getAssetsFolder() {
        throw unsupportedMethodForJavaLibrary("getAssetsFolder");
    }

    @Override
    @NonNull
    public Collection<String> getLocalJars() {
        throw unsupportedMethodForJavaLibrary("getLocalJars");
    }

    @Override
    @NonNull
    public String getJniFolder() {
        throw unsupportedMethodForJavaLibrary("getJniFolder");
    }

    @Override
    @NonNull
    public String getAidlFolder() {
        throw unsupportedMethodForJavaLibrary("getAidlFolder");
    }

    @Override
    @NonNull
    public String getRenderscriptFolder() {
        throw unsupportedMethodForJavaLibrary("getRenderscriptFolder");
    }

    @Override
    @NonNull
    public String getProguardRules() {
        throw unsupportedMethodForJavaLibrary("getProguardRules");
    }

    @Override
    @NonNull
    public String getLintJar() {
        throw unsupportedMethodForJavaLibrary("getLintJar");
    }

    @Override
    @NonNull
    public String getExternalAnnotations() {
        throw unsupportedMethodForJavaLibrary("getExternalAnnotations");
    }

    @Override
    @NonNull
    public String getPublicResources() {
        throw unsupportedMethodForJavaLibrary("getPublicResources");
    }

    @Override
    @NonNull
    public String getSymbolFile() {
        throw unsupportedMethodForJavaLibrary("getSymbolFile");
    }

    @NonNull
    private static UnsupportedOperationException unsupportedMethodForJavaLibrary(
            @NonNull String methodName) {
        return new UnsupportedOperationException(
                methodName + "() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeJavaLibrary)) {
            return false;
        }
        IdeJavaLibrary that = (IdeJavaLibrary) o;
        return myType == that.myType
                && Objects.equals(myArtifactAddress, that.myArtifactAddress)
                && Objects.equals(myArtifactFile, that.myArtifactFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myType, myArtifactAddress, myArtifactFile);
    }

    @Override
    public String toString() {
        return "IdeJavaLibrary{"
                + "myType="
                + myType
                + ", myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myArtifactFile="
                + myArtifactFile
                + '}';
    }
}
