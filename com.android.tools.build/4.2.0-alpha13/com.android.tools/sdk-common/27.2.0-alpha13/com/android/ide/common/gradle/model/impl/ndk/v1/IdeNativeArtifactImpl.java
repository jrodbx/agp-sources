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
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeArtifact;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeFile;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class IdeNativeArtifactImpl implements IdeNativeArtifact, Serializable {
    @NonNull private final String myName;
    @NonNull private final String myToolChain;
    @NonNull private final String myGroupName;
    @NonNull private final Collection<IdeNativeFile> mySourceFiles;
    @NonNull private final Collection<File> myExportedHeaders;
    @Nullable private final File myOutputFile;
    @NonNull private final String myAbi;
    @NonNull private final String myTargetName;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeNativeArtifactImpl() {
        myName = "";
        myToolChain = "";
        myGroupName = "";
        mySourceFiles = Collections.emptyList();
        myExportedHeaders = Collections.emptyList();
        myOutputFile = null;
        myAbi = "";
        myTargetName = "";

        myHashCode = 0;
    }

    public IdeNativeArtifactImpl(
            @NonNull String name,
            @NonNull String toolChain,
            @NonNull String groupName,
            @NonNull List<IdeNativeFile> sourceFiles,
            @NonNull List<File> exportedHeaders,
            @NonNull String abi,
            @NonNull String targetName,
            @Nullable File outputFile) {
        myName = name;
        myToolChain = toolChain;
        myGroupName = groupName;
        mySourceFiles = sourceFiles;
        myExportedHeaders = exportedHeaders;
        myAbi = abi;
        myTargetName = targetName;
        myOutputFile = outputFile;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getToolChain() {
        return myToolChain;
    }

    @Override
    @NonNull
    public String getGroupName() {
        return myGroupName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        throw new UnusedModelMethodException("getAssembleTaskName");
    }

    @Override
    @NonNull
    public Collection<IdeNativeFile> getSourceFiles() {
        return mySourceFiles;
    }

    @Override
    @NonNull
    public Collection<File> getExportedHeaders() {
        return myExportedHeaders;
    }

    @Override
    @NonNull
    public String getAbi() {
        return myAbi;
    }

    @Override
    @NonNull
    public String getTargetName() {
        return myTargetName;
    }

    @Override
    @Nullable
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    @NonNull
    public Collection<File> getRuntimeFiles() {
        throw new UnusedModelMethodException("getRuntimeFiles");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeArtifactImpl)) {
            return false;
        }
        IdeNativeArtifactImpl artifact = (IdeNativeArtifactImpl) o;
        return Objects.equals(myName, artifact.myName)
                && Objects.equals(myToolChain, artifact.myToolChain)
                && Objects.equals(myGroupName, artifact.myGroupName)
                && Objects.equals(mySourceFiles, artifact.mySourceFiles)
                && Objects.equals(myExportedHeaders, artifact.myExportedHeaders)
                && Objects.equals(myAbi, artifact.myAbi)
                && Objects.equals(myTargetName, artifact.myTargetName)
                && Objects.equals(myOutputFile, artifact.myOutputFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myName,
                myToolChain,
                myGroupName,
                mySourceFiles,
                myExportedHeaders,
                myAbi,
                myTargetName,
                myOutputFile);
    }

    @Override
    public String toString() {
        return "IdeNativeArtifact{"
                + "myName='"
                + myName
                + '\''
                + ", myToolChain='"
                + myToolChain
                + '\''
                + ", myGroupName='"
                + myGroupName
                + '\''
                + ", mySourceFiles="
                + mySourceFiles
                + ", myExportedHeaders="
                + myExportedHeaders
                + ", myAbi='"
                + myAbi
                + '\''
                + ", myTargetName='"
                + myTargetName
                + '\''
                + ", myOutputFile="
                + myOutputFile
                + "}";
    }
}
