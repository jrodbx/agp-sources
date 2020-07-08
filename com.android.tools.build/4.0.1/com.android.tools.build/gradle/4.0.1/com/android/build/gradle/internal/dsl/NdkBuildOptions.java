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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.model.CoreNdkBuildOptions;
import java.io.File;
import javax.inject.Inject;

/** See {@link com.android.build.api.dsl.NdkBuildOptions} */
public class NdkBuildOptions
        implements CoreNdkBuildOptions, com.android.build.api.dsl.NdkBuildOptions {
    @NonNull private final DslScope dslScope;

    @Nullable
    private File path;

    @Nullable private File buildStagingDirectory;

    @Inject
    public NdkBuildOptions(@NonNull DslScope dslScope) {
        this.dslScope = dslScope;
    }

    @Nullable
    @Override
    public File getPath() {
        return this.path;
    }

    public void setPath(@NonNull Object path) {
        this.path = dslScope.file(path);
    }

    @Override
    public void setPath(@NonNull File path) {
        this.path = path;
    }

    @Override
    public void path(@NonNull Object path) {
        this.path = dslScope.file(path);
    }

    @Nullable
    @Override
    public File getBuildStagingDirectory() {
        return buildStagingDirectory;
    }

    @Override
    public void setBuildStagingDirectory(@Nullable File buildStagingDirectory) {
        this.buildStagingDirectory = dslScope.file(buildStagingDirectory);
    }

    public void setBuildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = dslScope.file(buildStagingDirectory);
    }

    @Override
    public void buildStagingDirectory(@Nullable Object buildStagingDirectory) {
        this.buildStagingDirectory = dslScope.file(buildStagingDirectory);
    }
}
