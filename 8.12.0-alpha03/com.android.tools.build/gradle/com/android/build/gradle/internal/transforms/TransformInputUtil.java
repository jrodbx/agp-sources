/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility methods for retrieving files from TransformInput.
 */
public class TransformInputUtil {

    /** Return existing jars and directories from the inputs. Deleted ones are omitted. */
    public static Collection<File> getAllFiles(Iterable<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, true, true);
    }

    /** Return existing directories from the inputs. Deleted ones are omitted. */
    public static Collection<File> getDirectories(Iterable<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, true, false);
    }

    static Collection<File> getAllFiles(
            Iterable<TransformInput> transformInputs,
            boolean includeDirectoryInput,
            boolean includeJarInput) {
        ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        for (TransformInput input : transformInputs) {
            if (includeDirectoryInput) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    if (directoryInput.getFile().isDirectory()) {
                        inputFiles.add(directoryInput.getFile());
                    }
                }
            }
            if (includeJarInput) {
                for (JarInput jarInput : input.getJarInputs()) {
                    if (jarInput.getFile().isFile()) {
                        inputFiles.add(jarInput.getFile());
                    }
                }
            }
        }
        return inputFiles.build();
    }

    @NonNull
    public static Map<Status, Set<File>> getByStatus(@NonNull DirectoryInput dir) {
        Map<Status, Set<File>> byStatus =
                dir.getChangedFiles()
                        .entrySet()
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        Map.Entry::getValue,
                                        Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        for (Status status : Status.values()) {
            byStatus.putIfAbsent(status, ImmutableSet.of());
        }

        return byStatus;
    }

    @NonNull
    public static Iterable<TransformInput> getInputAndReferenced(
            @NonNull TransformInvocation invocation) {
        return Iterables.concat(invocation.getInputs(), invocation.getReferencedInputs());
    }

    @NonNull
    public static Set<Path> findChangedPaths(@NonNull Iterable<TransformInput> transformInputs) {
        Set<Path> changedPaths = Sets.newHashSet();
        for (TransformInput input : transformInputs) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Map<Status, Set<File>> byStatus = TransformInputUtil.getByStatus(dirInput);
                for (File modifiedFile :
                        Iterables.concat(
                                byStatus.get(Status.CHANGED),
                                byStatus.get(Status.REMOVED),
                                byStatus.get(Status.ADDED))) {
                    if (modifiedFile.toString().endsWith(SdkConstants.DOT_CLASS)) {
                        changedPaths.add(modifiedFile.toPath());
                    }
                }
            }

            for (JarInput jarInput : input.getJarInputs()) {
                if (jarInput.getStatus() != Status.NOTCHANGED) {
                    changedPaths.add(jarInput.getFile().toPath());
                }
            }
        }
        return changedPaths;
    }
}
