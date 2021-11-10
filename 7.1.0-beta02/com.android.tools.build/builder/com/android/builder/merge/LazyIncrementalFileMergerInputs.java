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

package com.android.builder.merge;

import com.android.annotations.NonNull;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.KeyedFileCache;
import com.android.builder.files.RelativeFile;
import com.android.builder.files.RelativeFiles;
import com.android.builder.files.ZipCentralDirectory;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.utils.CachedSupplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Factory methods for {@link LazyIncrementalFileMergerInput}.
 */
public final class LazyIncrementalFileMergerInputs {

    private LazyIncrementalFileMergerInputs() {}

    /**
     * Creates an input from a set of directories or zips without any incremental information.
     *
     * @param name the input set name
     * @param base the directories and zips; no files with the same OS-independent paths may
     * exist when constructing the trees from these elements; because the construction is lazy,
     * duplicate files may be detected later and failures show up later
     * @return the input
     */
    @NonNull
    public static LazyIncrementalFileMergerInput fromNew(
            @NonNull String name,
            @NonNull Set<File> base) {

        ImmutableSet<File> baseI = ImmutableSet.copyOf(base);

        CachedSupplier<Set<RelativeFile>> all = new CachedSupplier<>(() -> load(baseI));

        CachedSupplier<Map<RelativeFile, FileStatus>> upd =
                new CachedSupplier<>(
                        () -> {
                            ImmutableMap.Builder<RelativeFile, FileStatus> builder =
                                    ImmutableMap.builder();
                            all.get().forEach(rf -> builder.put(rf, FileStatus.NEW));
                            return builder.build();
                        });

        return new LazyIncrementalFileMergerInput(name, upd, all);
    }

    /**
     * Loads all relative files in a set of base files or directories.
     *
     * @param base the directories and zips; no files with the same OS-independent paths may
     * exist when constructing the trees from these elements
     * @return the set of relative files
     * @throws DuplicatePathInIncrementalInputException if more than one file with the same
     * OS-independent path exist
     */
    private static ImmutableSet<RelativeFile> load(@NonNull Set<File> base) {
        Set<String> paths = new HashSet<>();
        ImmutableSet.Builder<RelativeFile> builder = ImmutableSet.builder();
        for (File b : base) {
            Set<RelativeFile> files;
            if (b.isFile()) {
                try {
                    files = RelativeFiles.fromZip(new ZipCentralDirectory(b));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else if (b.isDirectory()) {
                files = RelativeFiles.fromDirectory(b);
            } else {
                throw new AssertionError();
            }

            for (RelativeFile rf : files) {
                String p = rf.getRelativePath();
                if (!paths.add(p)) {
                    throw new DuplicatePathInIncrementalInputException(
                            "Duplicate relative path '" + p + "'");
                }
            }

            builder.addAll(files);
        }

        return builder.build();
    }

}
