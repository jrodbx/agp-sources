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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.InputStream;
import java.util.function.Predicate;

/**
 * {@link IncrementalFileMergerInput} that filters input based on a predicate over the accepted
 * paths. This input will effectively create a view with a subset of another input.
 */
public class FilterIncrementalFileMergerInput extends DelegateIncrementalFileMergerInput {

    /**
     * Predicate that evaluates paths accepted by the filter.
     */
    @NonNull
    private final Predicate<String> pathsAccepted;

    /**
     * Creates a new incremental input based on another input filtering all files whose
     * OS-independent path are not accepted by the provided predicate.
     *
     * @param input the input to filter
     * @param pathsAccepted predicate that accepts only paths that should be in the output
     */
    public FilterIncrementalFileMergerInput(
            @NonNull IncrementalFileMergerInput input,
            @NonNull Predicate<String> pathsAccepted) {
        super(input);

        this.pathsAccepted = pathsAccepted;
    }

    @NonNull
    @Override
    public ImmutableSet<String> getUpdatedPaths() {
        return super.getUpdatedPaths().stream()
                .filter(pathsAccepted)
                .collect(ImmutableCollectors.toImmutableSet());
    }

    @NonNull
    @Override
    public ImmutableSet<String> getAllPaths() {
        return super.getAllPaths().stream()
                .filter(pathsAccepted)
                .collect(ImmutableCollectors.toImmutableSet());
    }

    @Nullable
    @Override
    public FileStatus getFileStatus(@NonNull String path) {
        if (pathsAccepted.test(path)) {
            return super.getFileStatus(path);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public InputStream openPath(@NonNull String path) {
        Preconditions.checkArgument(pathsAccepted.test(path));
        return super.openPath(path);
    }
}
