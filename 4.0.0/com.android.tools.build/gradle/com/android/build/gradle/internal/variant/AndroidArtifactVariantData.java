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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import java.util.Collections;
import java.util.Set;

/** Data about a variant that produces an android artifact. */
public abstract class AndroidArtifactVariantData extends BaseVariantData {
    private Set<String> compatibleScreens = null;

    protected AndroidArtifactVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull TaskManager taskManager,
            @NonNull VariantScope variantScope,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull ComponentImpl publicVariantApi,
            @NonNull ComponentPropertiesImpl publicVariantPropertiesApi,
            @NonNull VariantSources variantSources) {
        super(
                globalScope,
                taskManager,
                variantScope,
                variantDslInfo,
                publicVariantApi,
                publicVariantPropertiesApi,
                variantSources);
    }

    public void setCompatibleScreens(Set<String> compatibleScreens) {
        this.compatibleScreens = compatibleScreens;
    }

    @NonNull
    public Set<String> getCompatibleScreens() {
        if (compatibleScreens == null) {
            return Collections.emptySet();
        }

        return compatibleScreens;
    }

    public boolean isSigned() {
        return getVariantDslInfo().isSigningReady();
    }
}
