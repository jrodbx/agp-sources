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
import java.util.List;

/**
 * {@link IncrementalFileMergerOutput} that delegates execution to another
 * {@link IncrementalFileMergerOutput}. Invoking methods on an instance will delegate execution to
 * the delegate.
 */
public class DelegateIncrementalFileMergerOutput implements IncrementalFileMergerOutput {

    /**
     * Delegate that will receive the calls.
     */
    @NonNull
    private final IncrementalFileMergerOutput delegate;

    /**
     * Creates a new output.
     *
     * @param delegate the delegate
     */
    public DelegateIncrementalFileMergerOutput(@NonNull IncrementalFileMergerOutput delegate) {
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

    @Override
    public void remove(@NonNull String path) {
        delegate.remove(path);
    }

    @Override
    public void create(
            @NonNull String path,
            @NonNull List<IncrementalFileMergerInput> inputs,
            boolean compress) {
        delegate.create(path, inputs, compress);
    }

    @Override
    public void update(
            @NonNull String path,
            @NonNull List<String> prevInputNames,
            @NonNull List<IncrementalFileMergerInput> inputs,
            boolean compress) {
        delegate.update(path, prevInputNames, inputs, compress);
    }
}
