/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/**
 * implementation of the {@link LibraryVariant} interface around a
 * {@link LibraryVariantData} object.
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public class LibraryVariantImpl extends BaseVariantImpl implements LibraryVariant, TestedVariant {

    @NonNull
    private final LibraryVariantData variantData;
    @Nullable
    private TestVariant testVariant = null;
    @Nullable
    private UnitTestVariant unitTestVariant = null;

    @Inject
    public LibraryVariantImpl(
            @NonNull LibraryVariantData variantData,
            @NonNull ObjectFactory objectFactory,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, readOnlyObjectProvider, outputs);
        this.variantData = variantData;
    }

    @Override
    @NonNull
    protected BaseVariantData getVariantData() {
        return variantData;
    }

    @Override
    public void setTestVariant(@Nullable TestVariant testVariant) {
        this.testVariant = testVariant;
    }

    @Override
    @Nullable
    public TestVariant getTestVariant() {
        return testVariant;
    }

    @Override
    @Nullable
    public UnitTestVariant getUnitTestVariant() {
        return unitTestVariant;
    }

    @Override
    public void setUnitTestVariant(@Nullable UnitTestVariant unitTestVariant) {
        this.unitTestVariant = unitTestVariant;
    }

    /** Returns the Library AAR packaging task. */
    @Override
    @Nullable
    public Zip getPackageLibrary() {
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getPackageLibraryProvider()",
                        "variant.getPackageLibrary()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return variantData.getTaskContainer().getBundleLibraryTask().getOrNull();
    }

    @Nullable
    @Override
    public TaskProvider<Zip> getPackageLibraryProvider() {
        //noinspection unchecked
        return (TaskProvider<Zip>) variantData.getTaskContainer().getBundleLibraryTask();
    }
}
