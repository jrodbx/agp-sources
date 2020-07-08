/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of {@link NativeArtifact}.
 */
@Immutable
public final class NativeArtifactImpl implements NativeArtifact, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final String toolChain;
    @NonNull
    private final String groupName;
    @NonNull
    private final String assembleTaskName;
    @NonNull
    private final Collection<NativeFile> sourceFiles;
    @NonNull
    private final Collection<File> exportedHeaders;
    @NonNull
    private final File outputFile;
    @NonNull
    private final Collection<File> runtimeFiles;
    @NonNull
    private final String abi;
    @NonNull
    private final String targetName;

    public NativeArtifactImpl(
            @NonNull String name,
            @NonNull String toolChain,
            @NonNull String groupName,
            @NonNull String assembleTaskName,
            @NonNull Collection<NativeFile> sourceFiles,
            @NonNull Collection<File> exportedHeaders,
            @NonNull File outputFile,
            @NonNull Collection<File> runtimeFiles,
            @NonNull String abi,
            @NonNull String targetName) {
        this.name = name;
        this.toolChain = toolChain;
        this.groupName = groupName;
        this.assembleTaskName = assembleTaskName;
        this.sourceFiles = sourceFiles;
        this.exportedHeaders = exportedHeaders;
        this.outputFile = outputFile;
        this.runtimeFiles = runtimeFiles;
        this.abi = abi;
        this.targetName = targetName;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public String getToolChain() {
        return toolChain;
    }

    @Override
    @NonNull
    public String getGroupName() {
        return groupName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return assembleTaskName;
    }

    @Override
    @NonNull
    public Collection<NativeFile> getSourceFiles() {
        return sourceFiles;
    }

    @Override
    @NonNull
    public Collection<File> getExportedHeaders() {
        return exportedHeaders;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return outputFile;
    }

    @NonNull
    @Override
    public Collection<File> getRuntimeFiles() {
        return runtimeFiles;
    }

    @Override
    @NonNull
    public String getAbi() {
        return abi;
    }

    @Override
    @NonNull
    public String getTargetName() {
        return targetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeArtifactImpl that = (NativeArtifactImpl) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(toolChain, that.toolChain) &&
                Objects.equals(groupName, that.groupName) &&
                Objects.equals(assembleTaskName, that.assembleTaskName) &&
                Objects.equals(sourceFiles, that.sourceFiles) &&
                Objects.equals(exportedHeaders, that.exportedHeaders) &&
                Objects.equals(outputFile, that.outputFile) &&
                Objects.equals(runtimeFiles, that.runtimeFiles) &&
                Objects.equals(abi, that.abi) &&
                Objects.equals(targetName, that.targetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                toolChain,
                groupName,
                assembleTaskName,
                sourceFiles,
                exportedHeaders,
                outputFile,
                runtimeFiles,
                abi,
                targetName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("Name", name)
                .add("ToolChain", toolChain)
                .add("GroupName", groupName)
                .add("AssembleTaskName", assembleTaskName)
                .add("SourceFilesCount", sourceFiles.size())
                .add("ExportedHeadersSize", exportedHeaders.size())
                .add("OutputFile", outputFile)
                .add("RuntimeFiles", getRuntimeFiles())
                .toString();
    }
}
