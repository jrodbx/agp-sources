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
     * (Simple) Merging tasks usually do not need caching because:
     *   - Merging is equivalent to unpacking from the cache, but without the overhead of
     *     downloads + packing + maintenance.
     *   - Their task outputs are usually large, which would consume a lot of space.
     * Examples: Bug 269175904
     *
     * Note: This applies to *simple* merging tasks only. For tasks that do more than just merging
     * (e.g., processing the inputs), caching may still be beneficial.
     */
    const val SIMPLE_MERGING_TASK = "Tasks that merge files into a directory/jar without further processing usually do not benefit from caching." +
            " Also, their outputs are usually large, which would consume a lot of space when cached."

    const val COPY_TASK = "Tasks that simply copy files from one location to another usually do not benefit from caching."

    const val FAST_TASK = "Tasks that execute quickly usually do not benefit from caching."
}
