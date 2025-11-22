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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.dsl.Cmake;
import java.io.File;
/**
 * Options for managing CMake external native builds.
 *
 * @deprecated Use {@link com.android.build.api.dsl.NdkBuild}
 */
@Deprecated
public interface CoreCmakeOptions extends Cmake {
    @Override
    @Nullable
    File getPath();

    @Override
    void setPath(@NonNull File path);

    @Override
    @Nullable
    File getBuildStagingDirectory();

    @Override
    void setBuildStagingDirectory(@NonNull File buildStagingDirectory);

    /** @return the version of Cmake to use */
    @Override
    @Nullable
    String getVersion();

    /**
     * Sets the configured Cmake version as string.
     *
     * @param version version string
     */
    @Override
    void setVersion(@NonNull String version);
}
