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
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

/** Default implementation of the {@link InstantAppVariantBuildOutput}. */
public class DefaultInstantAppVariantBuildOutput
        implements InstantAppVariantBuildOutput, Serializable {

    @NonNull private final String name;
    @NonNull private final String applicationId;
    @NonNull private final BuildOutput buildOutput;
    @NonNull private final Collection<EarlySyncBuildOutput> featureOutputs;

    public DefaultInstantAppVariantBuildOutput(
            @NonNull String name,
            @NonNull String applicationId,
            @NonNull BuildOutput buildOutputSupplier,
            @NonNull Collection<EarlySyncBuildOutput> featureOutputsSupplier) {
        this.name = name;
        this.applicationId = applicationId;
        this.buildOutput = buildOutputSupplier;
        this.featureOutputs = featureOutputsSupplier;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    @Override
    public OutputFile getOutput() {
        return buildOutput;
    }

    @NonNull
    @Override
    public Collection<OutputFile> getFeatureOutputs() {
        return new ArrayList<>(featureOutputs);
    }
}
