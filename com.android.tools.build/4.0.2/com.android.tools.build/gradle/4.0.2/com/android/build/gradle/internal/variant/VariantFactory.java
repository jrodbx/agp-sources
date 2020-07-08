/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantDslInfoImpl;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.VariantType;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;

/**
 * Interface for Variant Factory.
 *
 * <p>While VariantManager is the general variant management, implementation of this interface
 * provides variant type (app, lib) specific implementation.
 */
public interface VariantFactory {

    @NonNull
    VariantImpl createVariantObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo);

    @NonNull
    UnitTestImpl createUnitTestObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo);

    @NonNull
    AndroidTestImpl createAndroidTestObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantDslInfo variantDslInfo);

    @NonNull
    VariantPropertiesImpl createVariantPropertiesObject(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantScope variantScope);

    @NonNull
    UnitTestPropertiesImpl createUnitTestProperties(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantScope variantScope);

    @NonNull
    AndroidTestPropertiesImpl createAndroidTestProperties(
            @NonNull ComponentIdentity componentIdentity, @NonNull VariantScope variantScope);

    @NonNull
    BaseVariantData createVariantData(
            @NonNull VariantScope variantScope,
            @NonNull VariantDslInfoImpl variantDslInfo,
            @NonNull VariantImpl publicVariantApi,
            @NonNull VariantPropertiesImpl publicVariantPropertiesApi,
            @NonNull VariantSources variantSources,
            @NonNull TaskManager taskManager);

    @NonNull
    Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData);

    @Nullable
    default BaseVariantImpl createVariantApi(
            @NonNull ObjectFactory objectFactory,
            @NonNull BaseVariantData variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        Class<? extends BaseVariantImpl> implementationClass =
                getVariantImplementationClass(variantData);

        return objectFactory.newInstance(
                implementationClass,
                variantData,
                objectFactory,
                readOnlyObjectProvider,
                variantData
                        .getScope()
                        .getGlobalScope()
                        .getProject()
                        .container(VariantOutput.class));
    }

    @NonNull
    VariantType getVariantType();

    boolean hasTestScope();

    /**
     * Fail if the model is configured incorrectly.
     *
     * @param model the non-null model to validate, as implemented by the VariantManager.
     * @throws org.gradle.api.GradleException when the model does not validate.
     */
    void validateModel(@NonNull VariantInputModel model);

    void preVariantWork(Project project);

    void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs);
}
