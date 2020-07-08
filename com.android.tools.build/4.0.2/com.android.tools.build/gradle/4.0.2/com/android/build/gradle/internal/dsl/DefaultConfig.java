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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.resources.Density;
import com.google.common.collect.Sets;
import java.util.Set;
import javax.inject.Inject;

/** DSL object for the defaultConfig object. */
@SuppressWarnings({"WeakerAccess", "unused"}) // Exposed in the DSL.
public class DefaultConfig extends BaseFlavor implements com.android.build.api.dsl.DefaultConfig {
    @Inject
    public DefaultConfig(@NonNull String name, @NonNull DslScope dslScope) {
        super(name, dslScope);
        setDefaultConfigValues();
    }

    private void setDefaultConfigValues() {
        Set<Density> densities = Density.getRecommendedValuesForDevice();
        Set<String> strings = Sets.newHashSetWithExpectedSize(densities.size());
        for (Density density : densities) {
            strings.add(density.getResourceValue());
        }
        getVectorDrawables().setGeneratedDensities(strings);
        getVectorDrawables().setUseSupportLibrary(false);
    }
}
