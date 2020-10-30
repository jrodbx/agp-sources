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
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import org.gradle.api.model.ObjectFactory;

/**
 * Factory to create ApiObject from VariantData.
 */
public class ApiObjectFactory {
    @NonNull private final BaseExtension extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull private final ObjectFactory objectFactory;

    @NonNull
    private final ReadOnlyObjectProvider readOnlyObjectProvider = new ReadOnlyObjectProvider();

    public ApiObjectFactory(
            @NonNull BaseExtension extension,
            @NonNull VariantFactory variantFactory,
            @NonNull ObjectFactory objectFactory) {
        this.extension = extension;
        this.variantFactory = variantFactory;
        this.objectFactory = objectFactory;
    }

    public BaseVariantImpl create(BaseVariantData variantData) {
        if (variantData.getType().isTestComponent()) {
            // Testing variants are handled together with their "owners".
            createVariantOutput(variantData, null);
            return null;
        }

        BaseVariantImpl variantApi =
                variantFactory.createVariantApi(
                        objectFactory,
                        variantData,
                        readOnlyObjectProvider);
        if (variantApi == null) {
            return null;
        }

        if (variantFactory.hasTestScope()) {
            TestVariantData androidTestVariantData =
                    ((TestedVariantData) variantData).getTestVariantData(ANDROID_TEST);

            if (androidTestVariantData != null) {
                TestVariantImpl androidTestVariant =
                        objectFactory.newInstance(
                                TestVariantImpl.class,
                                androidTestVariantData,
                                variantApi,
                                objectFactory,
                                readOnlyObjectProvider,
                                variantData
                                        .getScope()
                                        .getGlobalScope()
                                        .getProject()
                                        .container(VariantOutput.class));
                createVariantOutput(androidTestVariantData, androidTestVariant);

                ((TestedAndroidConfig) extension).getTestVariants().add(androidTestVariant);
                ((TestedVariant) variantApi).setTestVariant(androidTestVariant);
            }

            TestVariantData unitTestVariantData =
                    ((TestedVariantData) variantData).getTestVariantData(UNIT_TEST);
            if (unitTestVariantData != null) {
                UnitTestVariantImpl unitTestVariant =
                        objectFactory.newInstance(
                                UnitTestVariantImpl.class,
                                unitTestVariantData,
                                variantApi,
                                objectFactory,
                                readOnlyObjectProvider,
                                variantData
                                        .getScope()
                                        .getGlobalScope()
                                        .getProject()
                                        .container(VariantOutput.class));

                ((TestedAndroidConfig) extension).getUnitTestVariants().add(unitTestVariant);
                ((TestedVariant) variantApi).setUnitTestVariant(unitTestVariant);
            }
        }

        createVariantOutput(variantData, variantApi);

        try {
            // Only add the variant API object to the domain object set once it's been fully
            // initialized.
            extension.addVariant(variantApi, variantData.getScope());
        } catch (Throwable t) {
            // Adding variant to the collection will trigger user-supplied callbacks
            throw new ExternalApiUsageException(t);
        }

        return variantApi;
    }

    private void createVariantOutput(BaseVariantData variantData, BaseVariantImpl variantApi) {
        variantData.variantOutputFactory =
                new VariantOutputFactory(
                        (variantData.getType().isAar())
                                ? LibraryVariantOutputImpl.class
                                : ApkVariantOutputImpl.class,
                        objectFactory,
                        extension,
                        variantApi,
                        variantData.getTaskContainer(),
                        variantData
                                .getScope()
                                .getGlobalScope()
                                .getDslScope()
                                .getDeprecationReporter());

        variantData
                .getPublicVariantPropertiesApi()
                .getOutputs()
                .forEach(
                        variantOutput ->
                                // pass the new api variant output object so the override method can
                                // delegate to the new location.
                                variantData.variantOutputFactory.create(variantOutput));
    }
}
