/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.TransformInput;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;

/**
 * Immutable version of {@link TransformInput}.
 */
class ImmutableTransformInput implements TransformInput {

    private File optionalRootLocation;
    @NonNull
    private final Collection<JarInput> jarInputs;
    @NonNull
    private final Collection<DirectoryInput> directoryInputs;

    ImmutableTransformInput(
            @NonNull Collection<JarInput> jarInputs,
            @NonNull Collection<DirectoryInput> directoryInputs,
            @Nullable File optionalRootLocation) {
        this.jarInputs = ImmutableList.copyOf(jarInputs);
        this.directoryInputs = ImmutableList.copyOf(directoryInputs);
        this.optionalRootLocation = optionalRootLocation;
    }

    @NonNull
    @Override
    public Collection<JarInput> getJarInputs() {
        return jarInputs;
    }

    @NonNull
    @Override
    public Collection<DirectoryInput> getDirectoryInputs() {
        return directoryInputs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("rootLocation", optionalRootLocation)
                .add("jarInputs", jarInputs)
                .add("folderInputs", directoryInputs)
                .toString();
    }
}
