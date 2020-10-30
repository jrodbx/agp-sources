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
import com.google.common.io.Closer;
import java.io.InputStream;
import java.util.List;

/**
 * Algorithm to merge streams. See {@link StreamMergeAlgorithms} for some commonly-used algorithms.
 */
public interface StreamMergeAlgorithm {

    /**
     * Merges the given streams.
     *
     * @param path the OS-independent path being merged
     * @param streams the source streams; must contain at least one element
     * @param closer the closer that will close the source streams and the merged stream (an
     *     implementation of this method will register the streams to be closed with this closer)
     * @return the merged stream
     */
    @NonNull
    InputStream merge(
            @NonNull String path, @NonNull List<InputStream> streams, @NonNull Closer closer);
}
