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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.api.variant.impl.HasAndroidTest;
import com.android.build.api.variant.impl.HasUnitTest;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.internal.api.ApkVariantOutputImpl;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantOutputImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.api.UnitTestVariantImpl;
import com.android.build.gradle.internal.component.AndroidTestCreationConfig;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.UnitTestCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.crash.ExternalApiUsageException;
import com.android.build.gradle.internal.dsl.VariantOutputFactory;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;

/**
 * Factory to create ApiObject from VariantData.
 */
public class ApiObjectFactory {
    @NonNull private final BaseExtension extension;
    @NonNull private final VariantFactory<?, ?, ?> variantFactory;
    @NonNull private final DslServices dslServices;

    @NonNull
    private final ReadOnlyObjectProvider readOnlyObjectProvider = new ReadOnlyObjectProvider();

    public ApiObjectFactory(
            @NonNull BaseExtension extension,
            @NonNull VariantFactory<?, ?, ?> variantFactory,
            @NonNull DslServices dslServices) {
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.dslServices = dslServices;
    }

    public BaseVariantImpl create(@NonNull VariantCreationConfig variant) {
        BaseVariantData variantData = variant.getOldVariantApiLegacySupport().getVariantData();

        BaseVariantImpl variantApi =
                variantFactory.createVariantApi(variant, variantData, readOnlyObjectProvider);
        if (variantApi == null) {
            return null;
        }

        if (variantFactory.getComponentType().getHasTestComponents()) {

            AndroidTestCreationConfig androidTestVariantProperties = null;

            if (variant instanceof HasAndroidTest) {
                androidTestVariantProperties = ((HasAndroidTest) variant).getAndroidTest();
            }

            if (androidTestVariantProperties != null) {
                TestVariantImpl androidTestVariant =
                        dslServices.newInstance(
                                TestVariantImpl.class,
                                androidTestVariantProperties
                                        .getOldVariantApiLegacySupport()
                                        .getVariantData(),
                                androidTestVariantProperties,
                                variantApi,
                                dslServices,
                                readOnlyObjectProvider,
                                dslServices.domainObjectContainer(VariantOutput.class));
                createVariantOutput(androidTestVariantProperties, androidTestVariant);

                ((TestedAndroidConfig) extension).getTestVariants().add(androidTestVariant);
                ((TestedVariant) variantApi).setTestVariant(androidTestVariant);
            }

            UnitTestCreationConfig unitTestVariantProperties = null;

            if (variant instanceof HasUnitTest) {
                unitTestVariantProperties = ((HasUnitTest) variant).getUnitTest();
            }

            if (unitTestVariantProperties != null) {
                UnitTestVariantImpl unitTestVariant =
                        dslServices.newInstance(
                                UnitTestVariantImpl.class,
                                unitTestVariantProperties
                                        .getOldVariantApiLegacySupport()
                                        .getVariantData(),
                                unitTestVariantProperties,
                                variantApi,
                                dslServices,
                                readOnlyObjectProvider,
                                dslServices.domainObjectContainer(VariantOutput.class));

                ((TestedAndroidConfig) extension).getUnitTestVariants().add(unitTestVariant);
                ((TestedVariant) variantApi).setUnitTestVariant(unitTestVariant);
            }
        }

        createVariantOutput(variant, variantApi);

        try {
            // Only add the variant API object to the domain object set once it's been fully
            // initialized.
            extension.addVariant(variantApi);
        } catch (Throwable t) {
            // Adding variant to the collection will trigger user-supplied callbacks
            throw new ExternalApiUsageException(t);
        }

        return variantApi;
    }

    private void createVariantOutput(
            @NonNull ComponentCreationConfig component, @NonNull BaseVariantImpl variantApi) {

        VariantOutputFactory variantOutputFactory =
                new VariantOutputFactory(
                        (component.getComponentType().isAar())
                                ? LibraryVariantOutputImpl.class
                                : ApkVariantOutputImpl.class,
                        dslServices,
                        extension,
                        variantApi,
                        component.getComponentType(),
                        component.getTaskContainer());

        component
                .getOldVariantApiLegacySupport()
                .getOutputs()
                .forEach(
                        // pass the new api variant output object so the override method can
                        // delegate to the new location.
                        variantOutputFactory::create);
    }
}
