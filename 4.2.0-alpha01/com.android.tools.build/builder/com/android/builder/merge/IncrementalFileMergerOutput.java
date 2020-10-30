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
import java.util.List;

/**
 * Output of a merge operation. The output receives notifications of the operations that need to be
 * performed to execute the merge.
 *
 * <p>Operations on the output should only be done once the inputs have been open
 * (see {@link IncrementalFileMergerInput#open()}.
 *
 * <p>Outputs need to be open before any operations can be performed and need to be closed to
 * ensure all changes have been persisted.
 *
 * <p>In general, an output obtained from {@link IncrementalFileMergerOutputs} is used.
 */
public interface IncrementalFileMergerOutput extends OpenableCloseable {

    /**
     * A path needs to be removed from the output.
     *
     * @param path the OS-independent path to remove
     */
    void remove(@NonNull String path);

    /**
     * A path needs to be created.
     *
     * @param path the OS-independent path
     * @param inputs the inputs where the paths exists and that should be combined to generate the
     *     output; the inputs are provided in the same order they were provided to {@link
     *     IncrementalFileMerger#merge(List, IncrementalFileMergerOutput,
     *     IncrementalFileMergerState)}
     * @param compress whether the data will be compressed
     */
    void create(
            @NonNull String path,
            @NonNull List<IncrementalFileMergerInput> inputs,
            boolean compress);

    /**
     * A path needs to be updated.
     *
     * @param path the OS-independent path
     * @param prevInputNames the previous inputs used to create or update the path
     * @param inputs the inputs where the paths exists and that should be combined to generate the
     *     output; the inputs are provided in the same order they were provided to {@link
     *     IncrementalFileMerger#merge(List, IncrementalFileMergerOutput,
     *     IncrementalFileMergerState)}
     * @param compress whether the data will be compressed
     */
    void update(
            @NonNull String path,
            @NonNull List<String> prevInputNames,
            @NonNull List<IncrementalFileMergerInput> inputs,
            boolean compress);
}
