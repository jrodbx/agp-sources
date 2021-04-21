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

package com.android.build.gradle.internal.packaging;

/**
 * User's setting for a particular archive entry. This is expressed in the build.gradle
 * DSL and used by this filter to determine file merging behaviors.
 */
public enum PackagingFileAction {
    /**
     * No action was described for archive entry.
     */
    NONE,

    /**
     * Merge all archive entries with the same archive path.
     */
    MERGE,

    /**
     * Pick to first archive entry with that archive path (not stable).
     */
    PICK_FIRST,

    /**
     * Exclude all archive entries with that archive path.
     */
    EXCLUDE
}
