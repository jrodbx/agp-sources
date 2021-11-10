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
import com.google.common.collect.ImmutableSet;
import java.io.InputStream;

/**
 * {@link IncrementalFileMergerInput} that delegates all operations to another
 * {@link IncrementalFileMergerInput}. This can be used as a base class for extending inputs.
 * This class will delegate all methods to the delegate input.
 */
public class DelegateIncrementalFileMergerInput implements IncrementalFileMergerInput {

    /**
     * The instance to delegate to.
     */
    @NonNull
    private final IncrementalFileMergerInput delegate;

    /**
     * Creates a new input.
     *
     * @param delegate the delegate calls to
     */
    public DelegateIncrementalFileMergerInput(@NonNull IncrementalFileMergerInput delegate) {
        this.delegate = delegate;
    }

    @Override
    public void open() {
        delegate.open();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @NonNull
    @Override
    public ImmutableSet<String> getUpdatedPaths() {
        return delegate.getUpdatedPaths();
    }

    @NonNull
    @Override
    public ImmutableSet<String> getAllPaths() {
        return delegate.getAllPaths();
    }

    @NonNull
    @Override
    public String getName() {
        return delegate.getName();
    }

    @Nullable
    @Override
    public FileStatus getFileStatus(@NonNull String path) {
        return delegate.getFileStatus(path);
    }

    @NonNull
    @Override
    public InputStream openPath(@NonNull String path) {
        return delegate.openPath(path);
    }
}
