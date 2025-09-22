/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Provides functionality the resource merger needs for preprocessing resources during merge.
 * Implementations of this interface must be thread-safe.
 */
public interface ResourcePreprocessor extends Serializable {
    /**
     * Returns the paths that should be generated for the given file, which can be empty if the file
     * doesn't need to be preprocessed.
     */
    @NonNull
    Collection<File> getFilesToBeGenerated(@NonNull File original) throws IOException;

    /** Actually generate the file based on the original file. */
    void generateFile(@NonNull File toBeGenerated, @NonNull File original) throws IOException;
}
