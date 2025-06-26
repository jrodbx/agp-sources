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

package com.android.build.gradle.api;

import com.android.annotations.Nullable;

/** A Build variant that supports versioning. */
@Deprecated
public interface VersionedVariant {

    /**
     * Returns the variant versionCode. If the value is not found, then 1 is returned as this
     * is the implicit value that the platform would use.
     *
     * If not output define its own variant override then this is used for all outputs.
     */
    int getVersionCode();

    /**
     * Return the variant versionName or null if none found.
     */
    @Nullable
    String getVersionName();

}
