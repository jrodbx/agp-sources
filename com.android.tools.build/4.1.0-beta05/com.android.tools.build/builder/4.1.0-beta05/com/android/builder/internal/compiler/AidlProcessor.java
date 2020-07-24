/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.internal.compiler;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.compiling.DependencyFileProcessor;
import com.android.builder.internal.incremental.DependencyData;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.android.repository.io.FileOpUtils;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A Source File processor for AIDL files. This compiles each aidl file found by the SourceSearcher.
 */
public class AidlProcessor implements DirectoryWalker.FileAction {

    private final String mAidlExecutable;
    @NonNull
    private final String mFrameworkLocation;
    @NonNull private final Iterable<File> mImportFolders;
    @NonNull
    private final File mSourceOutputDir;
    @Nullable
    private final File mPackagedOutputDir;
    @NonNull private final Collection<String> mPackageWhiteList;
    @NonNull
    private final DependencyFileProcessor mDependencyFileProcessor;
    @NonNull
    private final ProcessExecutor mProcessExecutor;
    @NonNull
    private  final ProcessOutputHandler mProcessOutputHandler;

    public AidlProcessor(
            @NonNull String aidlExecutable,
            @NonNull String frameworkLocation,
            @NonNull Iterable<File> importFolders,
            @NonNull File sourceOutputDir,
            @Nullable File packagedOutputDir,
            @Nullable Collection<String> packageWhiteList,
            @NonNull DependencyFileProcessor dependencyFileProcessor,
            @NonNull ProcessExecutor processExecutor,
            @NonNull ProcessOutputHandler processOutputHandler) {
        mAidlExecutable = aidlExecutable;
        mFrameworkLocation = frameworkLocation;
        mImportFolders = importFolders;
        mSourceOutputDir = sourceOutputDir;
        mPackagedOutputDir = packagedOutputDir;
        if (packageWhiteList == null) {
            mPackageWhiteList = ImmutableSet.of();
        } else {
            mPackageWhiteList = Collections.unmodifiableSet(Sets.newHashSet(packageWhiteList));
        }
        mDependencyFileProcessor = dependencyFileProcessor;
        mProcessExecutor = processExecutor;
        mProcessOutputHandler = processOutputHandler;
    }

    @Override
    public void call(@NonNull Path startDir, @NonNull Path inputFilePath) throws IOException {
        ProcessInfoBuilder builder = new ProcessInfoBuilder();

        builder.setExecutable(mAidlExecutable);

        builder.addArgs("-p" + mFrameworkLocation);
        builder.addArgs("-o" + mSourceOutputDir.getAbsolutePath());

        // add all the library aidl folders to access parcelables that are in libraries
        for (File f : mImportFolders) {
            builder.addArgs("-I" + f.getAbsolutePath());
        }

        // create a temp file for the dependency
        File depFile = File.createTempFile("aidl", ".d");
        builder.addArgs("-d" + depFile.getAbsolutePath());

        builder.addArgs(inputFilePath.toAbsolutePath().toString());

        ProcessResult result = mProcessExecutor.execute(
                builder.createProcess(), mProcessOutputHandler);

        try {
            result.rethrowFailure().assertNormalExitValue();
        } catch (ProcessException pe) {
            throw new IOException(pe);
        }

        String relativeInputFile =
                FileUtils.toSystemIndependentPath(
                        FileOpUtils.makeRelative(
                                startDir.toFile(), inputFilePath.toFile(), FileOpUtils.create()));

        // send the dependency file to the processor.
        DependencyData data = mDependencyFileProcessor.processFile(depFile);

        if (data != null) {
            // As of build tools 29.0.2, Aidl no longer produces an empty list of output files
            // so we need to check each file in it for content and delete the empty java files
            boolean isParcelable = true;

            List<String> outputFiles = data.getOutputFiles();

            if (!outputFiles.isEmpty()) {
                for (String path : outputFiles) {
                    List<String> outputFileContent =
                            Files.readLines(new File(path), StandardCharsets.UTF_8);
                    String emptyFileLine =
                            "// This file is intentionally left blank as placeholder for parcel declaration.";
                    if (outputFileContent.size() <= 2
                            && outputFileContent.get(0).equals(emptyFileLine)) {
                        FileUtils.delete(new File(path));
                    } else {
                        isParcelable = false;
                    }
                }
            }

            boolean isWhiteListed = mPackageWhiteList.contains(relativeInputFile);

            if (mPackagedOutputDir != null && (isParcelable || isWhiteListed)) {
                // looks like a parcelable or is white-listed.
                // Store it in the secondary output of the DependencyData object.

                File destFile = new File(mPackagedOutputDir, relativeInputFile);
                //noinspection ResultOfMethodCallIgnored
                destFile.getParentFile().mkdirs();
                Files.copy(inputFilePath.toFile(), destFile);
                data.addSecondaryOutputFile(destFile.getPath());
            }
        }

        FileUtils.delete(depFile);
    }

}
