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
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.plugins.DslContainerProvider;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;

/**
 * An implementation of VariantFactory for a project that generates APKs.
 *
 * <p>This can be an app project, or a test-only project, though the default behavior is app.
 */
public abstract class AbstractAppVariantFactory<
                VariantT extends VariantImpl<VariantPropertiesT>,
                VariantPropertiesT extends VariantPropertiesImpl>
        extends BaseVariantFactory<VariantT, VariantPropertiesT> {

    public AbstractAppVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        super(projectServices, globalScope);
    }

    @Override
    @NonNull
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
        return new ApplicationVariantData(
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
        return com.android.build.gradle.internal.api.ApplicationVariantImpl.class;
    }

    @Override
    public void validateModel(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        super.validateModel(model);

        validateVersionCodes(model);

        if (!getVariantType().isDynamicFeature()) {
            return;
        }

        // below is for dynamic-features only.

        IssueReporter issueReporter = projectServices.getIssueReporter();
        for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().isMinifyEnabled()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "Dynamic feature modules cannot set minifyEnabled to true. "
                                + "minifyEnabled is set to true in build type '"
                                + buildType.getBuildType().getName()
                                + "'.\nTo enable minification for a dynamic feature "
                                + "module, set minifyEnabled to true in the base module.");
            }
        }

        // check if any of the build types or flavors have a signing config.
        String message =
                "Signing configuration should not be declared in build types of "
                        + "dynamic-feature. Dynamic-features use the signing configuration "
                        + "declared in the application module.";
        for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
            if (buildType.getBuildType().getSigningConfig() != null) {
                issueReporter.reportWarning(
                        Type.SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE, message);
            }
        }

        message =
                "Signing configuration should not be declared in product flavors of "
                        + "dynamic-feature. Dynamic-features use the signing configuration "
                        + "declared in the application module.";
        for (ProductFlavorData<ProductFlavor> productFlavor : model.getProductFlavors().values()) {
            if (productFlavor.getProductFlavor().getSigningConfig() != null) {

                issueReporter.reportWarning(
                        Type.SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE, message);
            }
        }

        // check if the default config or any of the build types or flavors try to set abiFilters.
        message =
                "abiFilters should not be declared in dynamic-features. Dynamic-features use the "
                        + "abiFilters declared in the application module.";
        if (!model.getDefaultConfigData()
                .getDefaultConfig()
                .getNdkConfig()
                .getAbiFilters()
                .isEmpty()) {
            issueReporter.reportWarning(Type.GENERIC, message);
        }
        for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
            if (!buildType.getBuildType().getNdkConfig().getAbiFilters().isEmpty()) {
                issueReporter.reportWarning(Type.GENERIC, message);
            }
        }
        for (ProductFlavorData<ProductFlavor> productFlavor : model.getProductFlavors().values()) {
            if (!productFlavor.getProductFlavor().getNdkConfig().getAbiFilters().isEmpty()) {
                issueReporter.reportWarning(Type.GENERIC, message);
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
        dslContainers.getSigningConfigContainer().create(DEBUG);
        dslContainers.getBuildTypeContainer().create(DEBUG);
        dslContainers.getBuildTypeContainer().create(RELEASE);
    }

    private void validateVersionCodes(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        IssueReporter issueReporter = projectServices.getIssueReporter();

        Integer versionCode = model.getDefaultConfigData().getDefaultConfig().getVersionCode();
        if (versionCode != null && versionCode < 1) {
            issueReporter.reportError(
                    Type.GENERIC,
                    "android.defaultConfig.versionCode is set to "
                            + versionCode
                            + ", but it should be a positive integer.\n"
                            + "See https://developer.android.com/studio/publish/versioning#appversioning"
                            + " for more information.");
            return;
        }

        for (ProductFlavorData<ProductFlavor> flavorData : model.getProductFlavors().values()) {
            Integer flavorVersionCode = flavorData.getProductFlavor().getVersionCode();
            if (flavorVersionCode == null || flavorVersionCode > 0) {
                return;
            }
            issueReporter.reportError(
                    Type.GENERIC,
                    "versionCode is set to "
                            + flavorVersionCode
                            + " in product flavor "
                            + flavorData.getProductFlavor().getName()
                            + ", but it should be a positive integer.\n"
                            + "See https://developer.android.com/studio/publish/versioning#appversioning"
                            + " for more information.");
        }
    }
}
