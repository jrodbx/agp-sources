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
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.builder.core.ComponentType;
import com.google.common.collect.ImmutableList;

/**
 * Factory for the {@link BaseVariantOutput} for each variant output that will be added to the
 * public API
 */
public class VariantOutputFactory {

    @NonNull private final Class<? extends BaseVariantOutput> targetClass;
    @NonNull private final BaseServices services;
    @Nullable private final BaseVariantImpl deprecatedVariantPublicApi;
    @NonNull private ComponentType componentType;
    @NonNull private final TaskContainer taskContainer;
    @NonNull private final BaseExtension extension;

    public VariantOutputFactory(
            @NonNull Class<? extends BaseVariantOutput> targetClass,
            @NonNull BaseServices services,
            @NonNull BaseExtension extension,
            @Nullable BaseVariantImpl deprecatedVariantPublicApi,
            @NonNull ComponentType componentType,
            @NonNull TaskContainer taskContainer) {
        this.targetClass = targetClass;
        this.services = services;
        this.deprecatedVariantPublicApi = deprecatedVariantPublicApi;
        this.componentType = componentType;
        this.taskContainer = taskContainer;
        this.extension = extension;
    }

    public VariantOutput create(VariantOutputImpl variantApi) {
        BaseVariantOutput variantOutput =
                services.newInstance(
                        targetClass, taskContainer, services, variantApi, componentType);
        extension.getBuildOutputs().add(variantOutput);
        if (deprecatedVariantPublicApi != null) {
            deprecatedVariantPublicApi.addOutputs(ImmutableList.of(variantOutput));
        }
        return variantOutput;
    }
}
