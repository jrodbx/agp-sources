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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import java.util.Collection;

/**
 * This is the scope available to
 * {@link com.android.build.api.transform.Transform}
 */
public interface TransformVariantScope {

    /** Full name of the variant. */
    @NonNull
    String getName();

    @NonNull
    TransformGlobalScope getGlobalScope();

    @NonNull
    String getTaskName(@NonNull String prefix);

    @NonNull
    String getTaskName(@NonNull String prefix, @NonNull String suffix);

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * <p>This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    @NonNull
    String getDirName();

    /**
     * Returns a unique directory name (can include multiple folders) for the variant,
     * based on build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    @NonNull
    Collection<String> getDirectorySegments();
}
