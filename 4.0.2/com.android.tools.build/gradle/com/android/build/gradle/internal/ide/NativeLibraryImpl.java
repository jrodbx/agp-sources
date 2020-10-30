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
import com.android.builder.model.NativeLibrary;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of NativeLibrary that is serializable.
 */
@Immutable
public final class NativeLibraryImpl implements NativeLibrary, Serializable{
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    @NonNull
    private final String toolchainName;
    @NonNull
    private final String abi;
    @NonNull
    private final List<File> cIncludeDirs;
    @NonNull
    private final List<File> cppIncludeDirs;
    @NonNull
    private final List<File> cSystemIncludeDirs;
    @NonNull
    private final List<File> cppSystemIncludeDirs;
    @NonNull
    private final List<String> cDefines;
    @NonNull
    private final List<String> cppDefines;
    @NonNull
    private final List<String> cCompilerFlags;
    @NonNull
    private final List<String> cppCompilerFlags;
    @NonNull
    private final List<File> debuggableLibraryFolders;

    public NativeLibraryImpl(
            @NonNull String name,
            @NonNull String toolchainName,
            @NonNull String abi,
            @NonNull List<File> cIncludeDirs,
            @NonNull List<File> cppIncludeDirs,
            @NonNull List<File> cSystemIncludeDirs,
            @NonNull List<File> cppSystemIncludeDirs,
            @NonNull List<String> cDefines,
            @NonNull List<String> cppDefines,
            @NonNull List<String> cCompilerFlags,
            @NonNull List<String> cppCompilerFlags,
            @NonNull List<File> debuggableLibraryFolders) {
        this.name = name;
        this.toolchainName = toolchainName;
        this.abi = abi;
        this.cIncludeDirs = cIncludeDirs;
        this.cppIncludeDirs = cppIncludeDirs;
        this.cSystemIncludeDirs = cSystemIncludeDirs;
        this.cppSystemIncludeDirs = cppSystemIncludeDirs;
        this.cDefines = cDefines;
        this.cppDefines = cppDefines;
        this.cCompilerFlags = cCompilerFlags;
        this.cppCompilerFlags = cppCompilerFlags;
        this.debuggableLibraryFolders = debuggableLibraryFolders;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getToolchainName() {
        return toolchainName;
    }

    @NonNull
    @Override
    public String getAbi() {
        return abi;
    }

    @NonNull
    @Override
    public List<File> getCIncludeDirs() {
        return cIncludeDirs;
    }

    @NonNull
    @Override
    public List<File> getCppIncludeDirs() {
        return cppIncludeDirs;
    }

    @NonNull
    @Override
    public List<File> getCSystemIncludeDirs() {
        return cSystemIncludeDirs;
    }

    @NonNull
    @Override
    public List<File> getCppSystemIncludeDirs() {
        return cppSystemIncludeDirs;
    }

    @NonNull
    @Override
    public List<String> getCDefines() {
        return cDefines;
    }

    @NonNull
    @Override
    public List<String> getCppDefines() {
        return cppDefines;
    }

    @NonNull
    @Override
    public List<String> getCCompilerFlags() {
        return cCompilerFlags;
    }

    @NonNull
    @Override
    public List<String> getCppCompilerFlags() {
        return cppCompilerFlags;
    }

    @NonNull
    @Override
    public List<File> getDebuggableLibraryFolders() {
        return debuggableLibraryFolders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NativeLibraryImpl that = (NativeLibraryImpl) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(toolchainName, that.toolchainName) &&
                Objects.equals(abi, that.abi) &&
                Objects.equals(cIncludeDirs, that.cIncludeDirs) &&
                Objects.equals(cppIncludeDirs, that.cppIncludeDirs) &&
                Objects.equals(cSystemIncludeDirs, that.cSystemIncludeDirs) &&
                Objects.equals(cppSystemIncludeDirs, that.cppSystemIncludeDirs) &&
                Objects.equals(cDefines, that.cDefines) &&
                Objects.equals(cppDefines, that.cppDefines) &&
                Objects.equals(cCompilerFlags, that.cCompilerFlags) &&
                Objects.equals(cppCompilerFlags, that.cppCompilerFlags) &&
                Objects.equals(debuggableLibraryFolders, that.debuggableLibraryFolders);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(name, toolchainName, abi, cIncludeDirs, cppIncludeDirs, cSystemIncludeDirs,
                        cppSystemIncludeDirs, cDefines, cppDefines, cCompilerFlags,
                        cppCompilerFlags,
                        debuggableLibraryFolders);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("toolchainName", toolchainName)
                .add("abi", abi)
                .add("cIncludeDirs", cIncludeDirs)
                .add("cppIncludeDirs", cppIncludeDirs)
                .add("cSystemIncludeDirs", cSystemIncludeDirs)
                .add("cppSystemIncludeDirs", cppSystemIncludeDirs)
                .add("cDefines", cDefines)
                .add("cppDefines", cppDefines)
                .add("cCompilerFlags", cCompilerFlags)
                .add("cppCompilerFlags", cppCompilerFlags)
                .add("debuggableLibraryFolders", debuggableLibraryFolders)
                .toString();
    }
}
