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
import com.google.common.base.Preconditions;
import com.google.common.io.Closer;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

/**
 * File merge algorithms.
 */
public final class StreamMergeAlgorithms {

    /**
     * Utility class: no constructor.
     */
    private StreamMergeAlgorithms() {}

    /**
     * Algorithm that copies the content of the first stream.
     *
     * @return the algorithm
     */
    @NonNull
    public static StreamMergeAlgorithm pickFirst() {
        return (@NonNull String path, @NonNull List<InputStream> from, @NonNull Closer closer) -> {
            Preconditions.checkArgument(!from.isEmpty(), "from.isEmpty()");
            from.forEach(closer::register);
            return from.get(0);
        };
    }

    /**
     * Algorithm that concatenates streams ensuring each stream ends with a UNIX newline character.
     *
     * @return the algorithm
     */
    @NonNull
    public static StreamMergeAlgorithm concat() {
        return (@NonNull String path, @NonNull List<InputStream> from, @NonNull Closer closer) -> {
            InputStream mergedStream = new CombinedInputStream(from, true);
            closer.register(mergedStream);
            return mergedStream;
        };
    }

    /**
     * Algorithm that only accepts one input, failing with {@link DuplicateRelativeFileException}
     * if invoked with more than one input.
     *
     * @return the algorithm
     */
    public static StreamMergeAlgorithm acceptOnlyOne() {
        return (@NonNull String path, @NonNull List<InputStream> from, @NonNull Closer closer) -> {
            Preconditions.checkArgument(!from.isEmpty(), "from.isEmpty()");
            from.forEach(closer::register);
            if (from.size() > 1) {
                throw new DuplicateRelativeFileException(path, from.size());
            }
            return from.get(0);
        };
    }

    /**
     * Algorithm that selects another algorithm based on a function that is applied to the file's
     * path.
     *
     * @param select the algorithm-selection function; this function will determine which algorithm
     * to use based on the OS-independent path of the file to merge
     * @return the select algorithm
     */
    @NonNull
    public static StreamMergeAlgorithm select(@NonNull
            Function<String, StreamMergeAlgorithm> select) {
        return (@NonNull String path, @NonNull List<InputStream> from, @NonNull Closer closer) -> {
            StreamMergeAlgorithm algorithm = select.apply(path);
            assert algorithm != null;
            return algorithm.merge(path, from, closer);
        };
    }
}
