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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.tasks.factory.AbstractCompilesUtil.ANDROID_APT_PLUGIN_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.component.ComponentIdentity;
import com.android.build.api.component.impl.AndroidTestImpl;
import com.android.build.api.component.impl.AndroidTestPropertiesImpl;
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.component.impl.UnitTestImpl;
import com.android.build.api.component.impl.UnitTestPropertiesImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.api.BaseVariantImpl;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.services.BaseServicesImpl;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.VariantApiServices;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.errors.IssueReporter;
import com.android.builder.errors.IssueReporter.Type;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Project;

/** Common superclass for all {@link VariantFactory} implementations. */
public abstract class BaseVariantFactory<
                VariantT extends VariantImpl<VariantPropertiesT>,
                VariantPropertiesT extends VariantPropertiesImpl>
        implements VariantFactory<VariantT, VariantPropertiesT> {

    @NonNull protected final ProjectServices projectServices;
    @NonNull protected final GlobalScope globalScope;
    @Deprecated @NonNull private final BaseServices servicesForOldVariantObjectsOnly;

    public BaseVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        this.projectServices = projectServices;
        this.globalScope = globalScope;
        servicesForOldVariantObjectsOnly = new BaseServicesImpl(projectServices);
    }

    @NonNull
    @Override
    public UnitTestImpl createUnitTestObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices) {
        return projectServices
                .getObjectFactory()
                .newInstance(
                        UnitTestImpl.class, variantDslInfo, componentIdentity, variantApiServices);
    }

    @NonNull
    @Override
    public AndroidTestImpl createAndroidTestObject(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantApiServices variantApiServices) {
        return projectServices
                .getObjectFactory()
                .newInstance(
                        AndroidTestImpl.class,
                        variantDslInfo,
                        componentIdentity,
                        variantApiServices);
    }

    @NonNull
    @Override
    public UnitTestPropertiesImpl createUnitTestProperties(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantPropertiesImpl testedVariantProperties,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices) {
        UnitTestPropertiesImpl unitTestProperties =
                projectServices
                        .getObjectFactory()
                        .newInstance(
                                UnitTestPropertiesImpl.class,
                                componentIdentity,
                                buildFeatures,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                testedVariantProperties,
                                transformManager,
                                variantPropertiesApiServices,
                                taskCreationServices,
                                globalScope);

        unitTestProperties.addVariantOutput(
                new VariantOutputConfigurationImpl(false, ImmutableList.of()), null);

        return unitTestProperties;
    }

    @NonNull
    @Override
    public AndroidTestPropertiesImpl createAndroidTestProperties(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull VariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull VariantScope variantScope,
            @NonNull TestVariantData variantData,
            @NonNull VariantPropertiesImpl testedVariantProperties,
            @NonNull TransformManager transformManager,
            @NonNull VariantPropertiesApiServices variantPropertiesApiServices,
            @NonNull TaskCreationServices taskCreationServices) {
        AndroidTestPropertiesImpl androidTestProperties =
                projectServices
                        .getObjectFactory()
                        .newInstance(
                                AndroidTestPropertiesImpl.class,
                                componentIdentity,
                                buildFeatures,
                                variantDslInfo,
                                variantDependencies,
                                variantSources,
                                paths,
                                artifacts,
                                variantScope,
                                variantData,
                                testedVariantProperties,
                                transformManager,
                                variantPropertiesApiServices,
                                taskCreationServices,
                                globalScope);

        androidTestProperties.addVariantOutput(
                new VariantOutputConfigurationImpl(false, ImmutableList.of()), null);

        return androidTestProperties;
    }

    @Override
    @Nullable
    public BaseVariantImpl createVariantApi(
            @NonNull GlobalScope globalScope,
            @NonNull ComponentPropertiesImpl componentProperties,
            @NonNull BaseVariantData variantData,
            @NonNull ReadOnlyObjectProvider readOnlyObjectProvider) {
        Class<? extends BaseVariantImpl> implementationClass =
                getVariantImplementationClass(variantData);

        return projectServices
                .getObjectFactory()
                .newInstance(
                        implementationClass,
                        variantData,
                        componentProperties,
                        servicesForOldVariantObjectsOnly,
                        readOnlyObjectProvider,
                        globalScope.getProject().container(VariantOutput.class));
    }

    @Deprecated
    @NonNull
    public BaseServices getServicesForOldVariantObjectsOnly() {
        return servicesForOldVariantObjectsOnly;
    }

    @Override
    public void preVariantWork(Project project) {
        if (project.getPluginManager().hasPlugin(ANDROID_APT_PLUGIN_NAME)) {
            projectServices
                    .getIssueReporter()
                    .reportError(
                            Type.INCOMPATIBLE_PLUGIN,
                            "android-apt plugin is incompatible with the Android Gradle plugin.  "
                                    + "Please use 'annotationProcessor' configuration "
                                    + "instead.",
                            "android-apt");
        }
    }

    @Override
    public void validateModel(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        validateBuildConfig(model);
        validateResValues(model);
    }

    void validateBuildConfig(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        Boolean buildConfig = globalScope.getExtension().getBuildFeatures().getBuildConfig();
        if (buildConfig == null) {
            buildConfig =
                    projectServices
                            .getProjectOptions()
                            .get(BooleanOption.BUILD_FEATURE_BUILDCONFIG);
        }

        if (!buildConfig) {
            IssueReporter issueReporter = projectServices.getIssueReporter();

            if (!model.getDefaultConfigData().getDefaultConfig().getBuildConfigFields().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom BuildConfig fields, but the feature is disabled.");
            }

            for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData<ProductFlavor> productFlavor :
                    model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getBuildConfigFields().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom BuildConfig fields, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }

    void validateResValues(
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        Boolean resValues = globalScope.getExtension().getBuildFeatures().getResValues();
        if (resValues == null) {
            resValues =
                    projectServices.getProjectOptions().get(BooleanOption.BUILD_FEATURE_RESVALUES);
        }

        if (!resValues) {
            IssueReporter issueReporter = projectServices.getIssueReporter();

            if (!model.getDefaultConfigData().getDefaultConfig().getResValues().isEmpty()) {
                issueReporter.reportError(
                        Type.GENERIC,
                        "defaultConfig contains custom resource values, but the feature is disabled.");
            }

            for (BuildTypeData<BuildType> buildType : model.getBuildTypes().values()) {
                if (!buildType.getBuildType().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Build Type '%s' contains custom resource values, but the feature is disabled.",
                                    buildType.getBuildType().getName()));
                }
            }

            for (ProductFlavorData<ProductFlavor> productFlavor :
                    model.getProductFlavors().values()) {
                if (!productFlavor.getProductFlavor().getResValues().isEmpty()) {
                    issueReporter.reportError(
                            Type.GENERIC,
                            String.format(
                                    "Product Flavor '%s' contains custom resource values, but the feature is disabled.",
                                    productFlavor.getProductFlavor().getName()));
                }
            }
        }
    }
}
