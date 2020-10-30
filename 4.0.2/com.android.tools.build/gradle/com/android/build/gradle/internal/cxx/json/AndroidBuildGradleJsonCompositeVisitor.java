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
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;

/** Visitor over Android Gradle JSon that delegates to a list of visitors. */
public class AndroidBuildGradleJsonCompositeVisitor extends AndroidBuildGradleJsonStreamingVisitor {
    private final List<AndroidBuildGradleJsonStreamingVisitor> visitors = Lists.newArrayList();

    public AndroidBuildGradleJsonCompositeVisitor(
            @NonNull AndroidBuildGradleJsonStreamingVisitor... visitors) {
        super();
        Collections.addAll(this.visitors, visitors);
    }

    @Override
    protected void beginStringTable() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::beginStringTable);
    }

    @Override
    protected void endStringTable() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::endStringTable);
    }

    @Override
    protected void beginLibrary(@NonNull String libraryName) {
        visitors.forEach(parser -> parser.beginLibrary(libraryName));
    }

    @Override
    protected void endLibrary() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::endLibrary);
    }

    @Override
    protected void beginLibraryFile() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::beginLibraryFile);
    }

    @Override
    protected void endLibraryFile() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::endLibraryFile);
    }

    @Override
    protected void beginToolchain(@NonNull String toolchain) {
        visitors.forEach(parser -> parser.beginToolchain(toolchain));
    }

    @Override
    protected void endToolchain() {
        visitors.forEach(AndroidBuildGradleJsonStreamingVisitor::endToolchain);
    }

    @Override
    protected void visitStringTableEntry(int index, @NonNull String value) {
        visitors.forEach(parser -> parser.visitStringTableEntry(index, value));
    }

    @Override
    protected void visitBuildFile(@NonNull String buildFile) {
        visitors.forEach(parser -> parser.visitBuildFile(buildFile));
    }

    @Override
    protected void visitLibraryAbi(@NonNull String abi) {
        visitors.forEach(parser -> parser.visitLibraryAbi(abi));
    }

    @Override
    protected void visitLibraryArtifactName(@NonNull String artifact) {
        visitors.forEach(parser -> parser.visitLibraryArtifactName(artifact));
    }

    @Override
    protected void visitLibraryBuildCommand(@NonNull String buildCommand) {
        visitors.forEach(parser -> parser.visitLibraryBuildCommand(buildCommand));
    }

    @Override
    protected void visitLibraryBuildType(@NonNull String buildType) {
        visitors.forEach(parser -> parser.visitLibraryBuildType(buildType));
    }

    @Override
    protected void visitLibraryOutput(@NonNull String output) {
        visitors.forEach(parser -> parser.visitLibraryOutput(output));
    }

    @Override
    protected void visitLibraryToolchain(@NonNull String toolchain) {
        visitors.forEach(parser -> parser.visitLibraryToolchain(toolchain));
    }

    @Override
    protected void visitLibraryGroupName(@NonNull String groupName) {
        visitors.forEach(parser -> parser.visitLibraryGroupName(groupName));
    }

    @Override
    protected void visitToolchainCCompilerExecutable(@NonNull String executable) {
        visitors.forEach(parser -> parser.visitToolchainCCompilerExecutable(executable));
    }

    @Override
    protected void visitToolchainCppCompilerExecutable(@NonNull String executable) {
        visitors.forEach(parser -> parser.visitToolchainCppCompilerExecutable(executable));
    }

    @Override
    protected void visitLibraryFileFlags(@NonNull String flags) {
        visitors.forEach(parser -> parser.visitLibraryFileFlags(flags));
    }

    @Override
    protected void visitLibraryFileFlagsOrdinal(@NonNull Integer flagsOrdinal) {
        visitors.forEach(parser -> parser.visitLibraryFileFlagsOrdinal(flagsOrdinal));
    }

    @Override
    protected void visitLibraryFileSrc(@NonNull String src) {
        visitors.forEach(parser -> parser.visitLibraryFileSrc(src));
    }

    @Override
    protected void visitLibraryFileWorkingDirectory(@NonNull String workingDirectory) {
        visitors.forEach(parser -> parser.visitLibraryFileWorkingDirectory(workingDirectory));
    }

    @Override
    protected void visitLibraryFileWorkingDirectoryOrdinal(
            @NonNull Integer workingDirectoryOrdinal) {
        visitors.forEach(
                parser -> parser.visitLibraryFileWorkingDirectoryOrdinal(workingDirectoryOrdinal));
    }

    @Override
    protected void visitBuildTargetsCommand(@NonNull String buildTargetsCommand) {
        visitors.forEach(parser -> parser.visitBuildTargetsCommand(buildTargetsCommand));
    }

    @Override
    protected void visitCleanCommands(@NonNull String cleanCommand) {
        visitors.forEach(parser -> parser.visitCleanCommands(cleanCommand));
    }

    @Override
    protected void visitCFileExtensions(@NonNull String buildFile) {
        visitors.forEach(parser -> parser.visitCFileExtensions(buildFile));
    }

    @Override
    protected void visitCppFileExtensions(@NonNull String buildFile) {
        visitors.forEach(parser -> parser.visitCppFileExtensions(buildFile));
    }

    @Override
    protected void visitLibraryRuntimeFile(@NonNull String runtimeFile) {
        visitors.forEach(parser -> parser.visitLibraryRuntimeFile(runtimeFile));
    }
}
