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

import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.dsl.BuildFeatures;
import com.android.build.api.dsl.LibraryBuildFeatures;
import com.android.build.api.variant.impl.LibraryVariantImpl;
import com.android.build.api.variant.impl.LibraryVariantPropertiesImpl;
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.plugins.DslContainerProvider;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.VariantApiServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.core.VariantTypeImpl;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.google.common.collect.ImmutableList;

public class LibraryVariantFactory
        extends BaseVariantFactory<LibraryVariantImpl, LibraryVariantPropertiesImpl> {

    public LibraryVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        super(projectServices, globalScope);
    }

    @NonNull
    @Override
    public LibraryVariantImpl createVariantObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices) {
        return projectServices
                .getObjectFactory()
                .newInstance(
                        LibraryVariantImpl.class,
                        variantDslInfo,
                        componentIdentity,
                        variantApiServices);
    }

    @NonNull
    @Override
    public LibraryVariantPropertiesImpl createVariantPropertiesObject(
            @NonNull LibraryVariantImpl variant,
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull BaseVariantData variantData,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices) {
        LibraryVariantPropertiesImpl variantProperties =
                projectServices
                        .getObjectFactory()
                        .newInstance(
                                LibraryVariantPropertiesImpl.class,
                                componentIdentity,
                                buildFeatures,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                transformManager,
                                variantPropertiesApiServices,
                                taskCreationServices,
                                globalScope);

        // create default output
        String name =
                globalScope.getProjectBaseName()
                        + "-"
                        + variantProperties.getBaseName()
                        + "."
                        + BuilderConstants.EXT_LIB_ARCHIVE;
        variantProperties.addVariantOutput(
                new VariantOutputConfigurationImpl(false, ImmutableList.of()), name);

        return variantProperties;
    }

    @NonNull
    @Override
    public BuildFeatureValues createBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures, @NonNull ProjectOptions projectOptions) {

        if (buildFeatures instanceof LibraryBuildFeatures) {
            LibraryBuildFeatures features = (LibraryBuildFeatures) buildFeatures;

            Boolean androidResources = features.getAndroidResources();
            if (androidResources == null) {
                androidResources =
                        projectOptions.get(BooleanOption.BUILD_FEATURE_ANDROID_RESOURCES);
            }

            Boolean dataBinding = features.getDataBinding();
            if (dataBinding == null) {
                dataBinding = projectOptions.get(BooleanOption.BUILD_FEATURE_DATABINDING);
            }

            Boolean mlModelBinding = features.getMlModelBinding();
            if (mlModelBinding == null) {
                mlModelBinding = projectOptions.get(BooleanOption.BUILD_FEATURE_MLMODELBINDING);
            }

            return new BuildFeatureValuesImpl(
                    buildFeatures,
                    androidResources,
                    dataBinding && androidResources,
                    mlModelBinding,
                    projectOptions);
        } else {
            throw new RuntimeException("buildFeatures not of type DynamicFeatureBuildFeatures");
        }
    }

    @NonNull
    @Override
    public BuildFeatureValues createTestBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures,
            @NonNull DataBindingOptions dataBindingOptions,
            @NonNull ProjectOptions projectOptions) {
        // no difference with the main component
        return createBuildFeatureValues(buildFeatures, projectOptions);
    }

    @NonNull
    @Override
    public BaseVariantData createVariantData(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantPropertiesApiServices services,
            @NonNull GlobalScope globalScope,
            @NonNull MutableTaskContainer taskContainer) {
        return new LibraryVariantData(
                componentIdentity,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                services,
                globalScope,
                taskContainer);
    }


    @Override
    @NonNull
    public Class<? extends BaseVariantImpl> getVariantImplementationClass(
            @NonNull BaseVariantData variantData) {
        return com.android.build.gradle.internal.api.LibraryVariantImpl.class;
    }

    @NonNull
    @Override
    public VariantType getVariantType() {
        return VariantTypeImpl.LIBRARY;
    }

    /** * Prevent customization of applicationId or applicationIdSuffix. */
    @Override
    public void validateModel(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        super.validateModel(model);

        IssueReporter issueReporter = projectServices.getIssueReporter();
        DefaultConfig defaultConfig = model.getDefaultConfigData().getDefaultConfig();

        if (defaultConfig.getApplicationId() != null) {
            String applicationId = defaultConfig.getApplicationId();
            issueReporter.reportError(
                    Type.GENERIC,
                    "Library projects cannot set applicationId. "
                            + "applicationId is set to '"
                            + applicationId
                            + "' in default config.",
                    applicationId);
        }

        if (defaultConfig.getApplicationIdSuffix() != null) {
            String applicationIdSuffix = defaultConfig.getApplicationIdSuffix();
            issueReporter.reportError(
                    Type.GENERIC,
                    "Library projects cannot set applicationIdSuffix. "
                            + "applicationIdSuffix is set to '"
                            + applicationIdSuffix
                            + "' in default config.",
                    applicationIdSuffix);
        }

        for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().getApplicationIdSuffix() != null) {
                String applicationIdSuffix = buildType.getBuildType().getApplicationIdSuffix();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. "
                                + "applicationIdSuffix is set to '"
                                + applicationIdSuffix
                                + "' in build type '"
                                + buildType.getBuildType().getName()
                                + "'.",
                        applicationIdSuffix);
            }
        }
        for (ProductFlavorData<ProductFlavor> productFlavor : model.getProductFlavors().values()) {
            if (productFlavor.getProductFlavor().getApplicationId() != null) {
                String applicationId = productFlavor.getProductFlavor().getApplicationId();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationId. "
                                + "applicationId is set to '"
                                + applicationId
                                + "' in flavor '"
                                + productFlavor.getProductFlavor().getName()
                                + "'.",
                        applicationId);
            }

            if (productFlavor.getProductFlavor().getApplicationIdSuffix() != null) {
                String applicationIdSuffix =
                        productFlavor.getProductFlavor().getApplicationIdSuffix();
                issueReporter.reportError(
                        Type.GENERIC,
                        "Library projects cannot set applicationIdSuffix. "
                                + "applicationIdSuffix is set to '"
                                + applicationIdSuffix
                                + "' in flavor '"
                                + productFlavor.getProductFlavor().getName()
                                + "'.",
                        applicationIdSuffix);
            }
        }
    }

    @Override
    public void createDefaultComponents(
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        SigningConfig signingConfig = dslContainers.getSigningConfigContainer().create(DEBUG);
        dslContainers.getBuildTypeContainer().create(DEBUG);
        dslContainers.getBuildTypeContainer().create(RELEASE);
        dslContainers.getDefaultConfig().setSigningConfig(signingConfig);
    }
}
