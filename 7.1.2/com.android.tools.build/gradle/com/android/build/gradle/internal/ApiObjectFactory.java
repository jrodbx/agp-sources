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

import static com.android.builder.core.VariantTypeImpl.ANDROID_TEST;
import static com.android.builder.core.VariantTypeImpl.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.build.VariantOutput;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.internal.api.ApkVariantOutputImpl;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.LibraryVariantOutputImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.TestVariantImpl;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.api.UnitTestVariantImpl;
import com.android.build.gradle.internal.crash.ExternalApiUsageException;
import com.android.build.gradle.internal.dsl.VariantOutputFactory;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;

/**
 * Factory to create ApiObject from VariantData.
 */
public class ApiObjectFactory {
    @NonNull private final BaseExtension extension;
    @NonNull private final VariantFactory<?, ?> variantFactory;
    @NonNull private final GlobalScope globalScope;

    @NonNull
    private final ReadOnlyObjectProvider readOnlyObjectProvider = new ReadOnlyObjectProvider();

    public ApiObjectFactory(
            @NonNull BaseExtension extension,
            @NonNull VariantFactory<?, ?> variantFactory,
            @NonNull GlobalScope globalScope) {
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.globalScope = globalScope;
    }

    public BaseVariantImpl create(@NonNull VariantImpl variant) {
        BaseVariantData variantData = variant.getVariantData();

        BaseVariantImpl variantApi =
                variantFactory.createVariantApi(
                        globalScope, variant, variantData, readOnlyObjectProvider);
        if (variantApi == null) {
            return null;
        }

        if (variantFactory.getVariantType().getHasTestComponents()) {
            BaseServices services = variantFactory.getServicesForOldVariantObjectsOnly();

            ComponentImpl androidTestVariantProperties =
                    variant.getTestComponents().get(ANDROID_TEST);

            if (androidTestVariantProperties != null) {
                TestVariantImpl androidTestVariant =
                        services.newInstance(
                                TestVariantImpl.class,
                                androidTestVariantProperties.getVariantData(),
                                androidTestVariantProperties,
                                variantApi,
                                services,
                                readOnlyObjectProvider,
                                services.getProjectInfo()
                                        .getProject()
                                        .container(VariantOutput.class));
                createVariantOutput(androidTestVariantProperties, androidTestVariant);

                ((TestedAndroidConfig) extension).getTestVariants().add(androidTestVariant);
                ((TestedVariant) variantApi).setTestVariant(androidTestVariant);
            }

            ComponentImpl unitTestVariantProperties = variant.getTestComponents().get(UNIT_TEST);

            if (unitTestVariantProperties != null) {
                UnitTestVariantImpl unitTestVariant =
                        services.newInstance(
                                UnitTestVariantImpl.class,
                                unitTestVariantProperties.getVariantData(),
                                unitTestVariantProperties,
                                variantApi,
                                services,
                                readOnlyObjectProvider,
                                services.getProjectInfo()
                                        .getProject()
                                        .container(VariantOutput.class));

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
            @NonNull ComponentImpl component, @NonNull BaseVariantImpl variantApi) {

        final BaseServices services = variantFactory.getServicesForOldVariantObjectsOnly();
        VariantOutputFactory variantOutputFactory =
                new VariantOutputFactory(
                        (component.getVariantType().isAar())
                                ? LibraryVariantOutputImpl.class
                                : ApkVariantOutputImpl.class,
                        services,
                        extension,
                        variantApi,
                        component.getVariantType(),
                        component.getTaskContainer());

        component
                .getOutputs()
                .forEach(
                        // pass the new api variant output object so the override method can
                        // delegate to the new location.
                        variantOutputFactory::create);
    }
}
