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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeCmakeOptions;
import com.android.build.gradle.internal.dsl.CoreExternalNativeNdkBuildOptions;

/** Implementation of CoreExternalNativeBuildOptions used to merge multiple configs together. */
public class MergedExternalNativeBuildOptions
        implements CoreExternalNativeBuildOptions, MergedOptions<CoreExternalNativeBuildOptions> {

    @NonNull
    private final MergedExternalNativeNdkBuildOptions ndkBuild =
            new MergedExternalNativeNdkBuildOptions();
    @NonNull
    private final MergedExternalNativeCmakeOptions cmake = new MergedExternalNativeCmakeOptions();

    @Override
    public void reset() {
        ndkBuild.reset();
        cmake.reset();
    }

    @Override
    public void append(@NonNull CoreExternalNativeBuildOptions options) {
        ndkBuild.append(options.getExternalNativeNdkBuildOptions());
        cmake.append(options.getExternalNativeCmakeOptions());
    }

    @Nullable
    @Override
    public CoreExternalNativeNdkBuildOptions getExternalNativeNdkBuildOptions() {
        return ndkBuild;
    }

    @Nullable
    @Override
    public CoreExternalNativeCmakeOptions getExternalNativeCmakeOptions() {
        return cmake;
    }
}
