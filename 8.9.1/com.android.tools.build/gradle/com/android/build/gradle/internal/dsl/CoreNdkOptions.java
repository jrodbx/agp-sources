/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import java.util.Set;

/** @deprecated See {@link com.android.build.api.dsl.Ndk} */
@Deprecated
public interface CoreNdkOptions {

    @Nullable
    String getModuleName();

    @Nullable
    String getcFlags();

    @Nullable
    List<String> getLdLibs();

    @NonNull
    Set<String> getAbiFilters();

    @Nullable
    String getStl();

    @Nullable
    Integer getJobs();

    @Nullable
    String getDebugSymbolLevel();
}
