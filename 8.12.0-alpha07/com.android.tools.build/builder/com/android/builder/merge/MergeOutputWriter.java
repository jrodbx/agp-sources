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
import java.io.InputStream;

/**
 * Writes the output of a merge. The output is provided on a path-by-path basis.
 *
 * <p>Writers need to be open before any operations can be performed and need to be closed to
 * ensure all changes have been persisted.
 *
 * <p>See {@link MergeOutputWriters} for some common implementations.
 */
public interface MergeOutputWriter extends OpenableCloseable {

    /**
     * Removes a path from the output.
     *
     * @param path the path to remove
     */
    void remove(@NonNull String path);

    /**
     * Creates a new path in the output.
     *
     * @param path the path to create
     * @param data the path's data
     * @param compress whether the data will be compressed
     */
    void create(@NonNull String path, @NonNull InputStream data, boolean compress);

    /**
     * Replaces a path's contents with new contents.
     *
     * @param path the path to replace
     * @param data the new path's data
     * @param compress whether the data will be compressed
     */
    void replace(@NonNull String path, @NonNull InputStream data, boolean compress);
}
