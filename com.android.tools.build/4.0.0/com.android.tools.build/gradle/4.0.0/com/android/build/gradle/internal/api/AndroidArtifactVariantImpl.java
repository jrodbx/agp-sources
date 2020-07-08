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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.AndroidArtifactVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.variant.AndroidArtifactVariantData;
import com.android.builder.model.SigningConfig;
import java.util.Set;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

/**
 * Implementation of the {@link AndroidArtifactVariant} interface around a
 * {@link AndroidArtifactVariantData} object.
 */
public abstract class AndroidArtifactVariantImpl extends BaseVariantImpl implements AndroidArtifactVariant {

    protected AndroidArtifactVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, immutableObjectProvider, outputs);
    }

    @NonNull
    @Override
    protected abstract AndroidArtifactVariantData getVariantData();

    @Override
    public SigningConfig getSigningConfig() {
        return readOnlyObjectProvider.getSigningConfig(
                getVariantData().getVariantDslInfo().getSigningConfig());
    }

    @Override
    public boolean isSigningReady() {
        return getVariantData().isSigned();
    }

    @Nullable
    @Override
    public String getVersionName() {
        return getVariantData().getVariantDslInfo().getVersionName();
    }

    @Override
    public int getVersionCode() {
        return getVariantData().getVariantDslInfo().getVersionCode();
    }

    @NonNull
    @Override
    public Set<String> getCompatibleScreens() {
        return getVariantData().getCompatibleScreens();
    }
}
