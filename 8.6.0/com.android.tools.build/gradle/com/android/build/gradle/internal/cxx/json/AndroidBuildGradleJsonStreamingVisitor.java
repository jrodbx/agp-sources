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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;

/** Abstract base class that defines the visitor called by AndroidBuildGradleJsonStreamingParser. */
public abstract class AndroidBuildGradleJsonStreamingVisitor {

    protected void beginStringTable() {}

    protected void endStringTable() {}

    protected void beginLibrary(@NonNull String libraryName) {}

    protected void endLibrary() {}

    protected void beginLibraryFile() {}

    protected void endLibraryFile() {}

    protected void beginToolchain(@NonNull String toolchain) {}

    protected void endToolchain() {}

    protected void visitStringTableEntry(int index, @NonNull String value) {}

    protected void visitBuildFile(@NonNull String buildFile) {}

    protected void visitLibraryAbi(@NonNull String abi) {}

    protected void visitLibraryArtifactName(@NonNull String artifact) {}

    protected void visitLibraryBuildCommandComponents(
            @NonNull List<String> buildCommandComponents) {}

    protected void visitLibraryBuildType(@NonNull String buildType) {}

    protected void visitLibraryOutput(@Nullable String output) {}

    protected void visitLibraryToolchain(@NonNull String toolchain) {}

    protected void visitLibraryGroupName(@NonNull String groupName) {}

    protected void visitToolchainCCompilerExecutable(@NonNull String executable) {}

    protected void visitToolchainCppCompilerExecutable(@NonNull String executable) {}

    protected void visitLibraryFileFlags(@NonNull String flags) {}

    protected void visitLibraryFileFlagsOrdinal(@NonNull Integer flagsOrdinal) {}

    protected void visitLibraryFileSrc(@NonNull String src) {}

    protected void visitLibraryFileWorkingDirectory(@NonNull String workingDirectory) {}

    protected void visitLibraryFileWorkingDirectoryOrdinal(
            @NonNull Integer workingDirectoryOrdinal) {}

    protected void visitCleanCommandsComponents(@NonNull List<String> cleanCommandComponents) {}

    protected void visitBuildTargetsCommandComponents(
            @NonNull List<String> buildTargetsCommandComponents) {}

    protected void visitCFileExtensions(@NonNull String buildFile) {}

    protected void visitCppFileExtensions(@NonNull String buildFile) {}

    protected void visitLibraryRuntimeFile(@NonNull String runtimeFile) {}

}
