/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget;
import com.android.builder.core.DefaultDexOptions;
import java.util.Arrays;
import javax.inject.Inject;

/**
 * DSL object for configuring dx options.
 */
@SuppressWarnings("unused") // Exposed in the DSL.
public class DexOptions extends DefaultDexOptions {

    private final DeprecationReporter deprecationReporter;

    @Inject
    public DexOptions(DeprecationReporter deprecationReporter) {
        this.deprecationReporter = deprecationReporter;
    }

    /** @deprecated ignored */
    @Deprecated
    public boolean getIncremental() {
        deprecationReporter.reportObsoleteUsage(
                "DexOptions.incremental", DeprecationTarget.DEX_OPTIONS);
        return false;
    }

    public void setIncremental(boolean ignored) {
        deprecationReporter.reportObsoleteUsage(
                "DexOptions.incremental", DeprecationTarget.DEX_OPTIONS);
    }

    public void additionalParameters(String... parameters) {
        this.setAdditionalParameters(Arrays.asList(parameters));
    }

    /**
     * @deprecated Dex will always be optimized. Invoking this method has no effect.
     */
    @Deprecated
    public void setOptimize(@SuppressWarnings("UnusedParameters") Boolean optimize) {
        deprecationReporter.reportObsoleteUsage(
                "DexOptions.optimize", DeprecationTarget.DEX_OPTIONS);
    }
}
