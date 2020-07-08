/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.builder.model.BaseBuildOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

/** Default implementation of the variant output minimal model. */
public abstract class DefaultBaseBuildOutput implements BaseBuildOutput, Serializable {

    @NonNull private final String name;
    @NonNull private final Collection<EarlySyncBuildOutput> buildOutputs;

    public DefaultBaseBuildOutput(
            @NonNull String name, @NonNull Collection<EarlySyncBuildOutput> buildOutputSupplier) {
        this.name = name;
        this.buildOutputs = buildOutputSupplier;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Collection<OutputFile> getOutputs() {
        return new ArrayList<>(buildOutputs);
    }
}
