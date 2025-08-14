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
import com.android.builder.core.DefaultVectorDrawablesOptions;
import java.util.Arrays;

public class VectorDrawablesOptions extends DefaultVectorDrawablesOptions
        implements com.android.build.api.dsl.VectorDrawables {

    @Override
    public void generatedDensities(@NonNull String... densities) {
        setGeneratedDensities(Arrays.asList(densities));
    }

    @NonNull
    public static VectorDrawablesOptions copyOf(
            @NonNull com.android.builder.model.VectorDrawablesOptions original) {
        VectorDrawablesOptions options = new VectorDrawablesOptions();

        options.setGeneratedDensities(original.getGeneratedDensities());
        options.setUseSupportLibrary(original.getUseSupportLibrary());

        return options;
    }
}
