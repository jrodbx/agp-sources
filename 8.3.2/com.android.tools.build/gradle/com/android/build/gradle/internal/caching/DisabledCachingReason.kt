/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.caching

object DisabledCachingReason {

    /**
     * Simple merging tasks (tasks that merge files into a directory/jar without any processing)
     * usually do not benefit from caching for 2 reasons:
     *   - Cached output size: Merging tasks usually have large task outputs, which would take up a
     *     lot of space where cached on disk.
     *   - Build speed: Merging the task's inputs directly without caching is usually much faster
     *     than running the task with caching, which includes either merging the task's inputs +
     *     packing the task's outputs or downloading + unpacking the cached outputs (which are
     *     usually large).
     *
     * Examples: Bug 269175904
     *
     * Note: This applies to *simple* merging tasks only. For tasks that also perform significant
     * processing in addition to merging, caching may still be beneficial.
     */
    const val SIMPLE_MERGING_TASK = "Simple merging task"

    /**
     * Tasks that simply copy files from one location to another usually do not benefit from
     * caching.
     */
    const val COPY_TASK = "Copy task"

    /** Tasks that execute quickly usually do not benefit from caching. */
    const val FAST_TASK = "Fast task"
}
