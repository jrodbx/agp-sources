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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * External API wrapper around the {@link TestVariantData}, for unit testing variants.
 */
public class UnitTestVariantImpl extends BaseVariantImpl implements UnitTestVariant {

    @NonNull
    private final TestVariantData variantData;
    @NonNull
    private final TestedVariant testedVariant;

    @Inject
    public UnitTestVariantImpl(
            @NonNull TestVariantData variantData,
            @NonNull ComponentImpl component,
            @NonNull TestedVariant testedVariant,
            @NonNull BaseServices services,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(component, services, readOnlyObjectProvider, outputs);

        this.variantData = variantData;
        this.testedVariant = testedVariant;
    }

    @NonNull
    @Override
    protected BaseVariantData getVariantData() {
        return variantData;
    }

    @NonNull
    @Override
    public TestedVariant getTestedVariant() {
        return testedVariant;
    }
}
