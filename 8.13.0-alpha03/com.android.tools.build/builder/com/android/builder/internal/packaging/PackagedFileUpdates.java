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

package com.android.builder.internal.packaging;

import com.android.annotations.NonNull;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.resources.FileStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilities to handle {@link PackagedFileUpdate} objects.
 */
final class PackagedFileUpdates {

    /**
     * Creates a list of {@link PackagedFileUpdate} based on a {@link Map} of {@link RelativeFile}
     * to {@link FileStatus}. The returned list will contain one entry per entry in the input map in
     * a 1-1 match.
     *
     * @param map the incremental relative file set, a {@link Map} of {@link RelativeFile} to {@link
     *     FileStatus}.
     * @return the list of {@link PackagedFileUpdate}
     */
    @NonNull
    static List<PackagedFileUpdate> fromIncrementalRelativeFileSet(
            @NonNull Map<RelativeFile, FileStatus> map) {
        List<PackagedFileUpdate> updates = new ArrayList<>();
        for (Map.Entry<RelativeFile, FileStatus> entry : map.entrySet()) {
            updates.add(
                    new PackagedFileUpdate(
                            entry.getKey(), entry.getKey().getRelativePath(), entry.getValue()));
        }

        return updates;
    }
}
