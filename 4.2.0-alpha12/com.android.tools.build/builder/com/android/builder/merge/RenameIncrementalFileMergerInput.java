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

package com.android.builder.merge;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.FileStatus;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import java.io.InputStream;
import java.util.function.Function;

/**
 * {@link IncrementalFileMergerInput} that renames files in another input according to a renaming
 * function. This means that the actual paths of the files reported have been transformed.
 *
 * <p>For example, a rename input that prepends {@code a/} to the files created over an input
 * that has file {@code b}, will report having file {@code a/b}. Opening file {@code a/b} will
 * effectively open file {@code b}.
 *
 * <p>The rename input will effectively create a view over another input in which files have been
 * renamed according to an injective function. Because name transformation needs to occur in both
 * directions, for example, it is necessary to transform {@code b} into {@code a/b} and {@code a/b}
 * back into {@code b}, the rename input needs to receive two functions: the transformation function
 * and its inverse.
 *
 * <p>Both rename and inverse rename functions are assumed to be stable, that is, the
 * transformations should always yield the same output for the same input.
 */
public class RenameIncrementalFileMergerInput extends DelegateIncrementalFileMergerInput {

    /**
     * Rename function.
     */
    @NonNull
    private final Function<String, String> rename;

    /**
     * Inverse rename function.
     */
    @NonNull
    private final Function<String, String> inverseRename;

    /**
     * As renames are performed, we cache them for future reference.
     */
    @NonNull
    private final BiMap<String, String> renameCache;

    /**
     * Creates a new input.
     *
     * @param input the input that serves as input for this one
     * @param rename the function that renames paths as they are reported by {@code input} to
     * whatever this input should return as paths; the function should be bijective
     * @param inverseRename the function that provides the inverse of {@code rename}
     */
    public RenameIncrementalFileMergerInput(
            @NonNull IncrementalFileMergerInput input,
            @NonNull Function<String, String> rename,
            @NonNull Function<String, String> inverseRename) {
        super(input);

        this.rename = rename;
        this.inverseRename = inverseRename;
        renameCache = HashBiMap.create();
    }

    /**
     * Obtains the name a path should be renamed to.
     *
     * @param source the source path to rename
     * @return the destination path
     */
    @NonNull
    private String directRename(@NonNull String source) {
        return renameCache.computeIfAbsent(source, rename);
    }

    /**
     * Obtains the original name of a renamed path
     *
     * @param destination the destination path
     * @return the source path
     */
    @NonNull
    private String inverseRename(@NonNull String destination) {
        return renameCache.inverse().computeIfAbsent(destination, inverseRename);
    }

    @NonNull
    @Override
    public ImmutableSet<String> getUpdatedPaths() {
        return super.getUpdatedPaths().stream()
                .map(this::directRename)
                .collect(ImmutableCollectors.toImmutableSet());
    }

    @NonNull
    @Override
    public ImmutableSet<String> getAllPaths() {
        return super.getAllPaths().stream()
                .map(this::directRename)
                .collect(ImmutableCollectors.toImmutableSet());
    }

    @Nullable
    @Override
    public FileStatus getFileStatus(@NonNull String path) {
        return super.getFileStatus(inverseRename(path));
    }

    @NonNull
    @Override
    public InputStream openPath(@NonNull String path) {
        return super.openPath(inverseRename(path));
    }
}
