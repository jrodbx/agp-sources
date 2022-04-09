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

package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_COMPILE_ONLY;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_TESTED_APKS;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.dsl.BuildFeatures;
import com.android.build.api.dsl.CommonExtension;
import com.android.build.api.dsl.DataBinding;
import com.android.build.api.dsl.TestBuildFeatures;
import com.android.build.api.dsl.TestExtension;
import com.android.build.api.variant.ComponentIdentity;
import com.android.build.api.variant.TestVariantBuilder;
import com.android.build.api.variant.impl.GlobalVariantBuilderConfig;
import com.android.build.api.variant.impl.TestVariantBuilderImpl;
import com.android.build.api.variant.impl.TestVariantImpl;
import com.android.build.gradle.internal.component.AndroidTestCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.component.TestVariantCreationConfig;
import com.android.build.gradle.internal.component.UnitTestCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.core.VariantSources;
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo;
import com.android.build.gradle.internal.core.dsl.TestFixturesComponentDslInfo;
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo;
import com.android.build.gradle.internal.core.dsl.UnitTestComponentDslInfo;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ModulePropertyKeys;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.plugins.DslContainerProvider;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.TestFixturesBuildFeaturesValuesImpl;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.services.VariantBuilderServices;
import com.android.build.gradle.internal.services.VariantServices;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.ComponentType;
import com.android.builder.core.ComponentTypeImpl;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;

/** Customization of {@link AbstractAppVariantFactory} for test-only projects. */
public class TestVariantFactory
        extends AbstractAppVariantFactory<
                TestVariantBuilder, TestProjectVariantDslInfo, TestVariantCreationConfig> {

    public TestVariantFactory(@NonNull DslServices dslServices) {
        super(dslServices);
    }

    @NonNull
    @Override
    public TestVariantBuilder createVariantBuilder(
            @NonNull GlobalVariantBuilderConfig globalVariantBuilderConfig,
            @NonNull ComponentIdentity componentIdentity,
            @NonNull TestProjectVariantDslInfo variantDslInfo,
            @NonNull VariantBuilderServices variantBuilderServices) {
        return dslServices.newInstance(
                TestVariantBuilderImpl.class,
                globalVariantBuilderConfig,
                variantDslInfo,
                componentIdentity,
                variantBuilderServices);
    }

    @NonNull
    @Override
    public TestVariantCreationConfig createVariant(
            @NonNull TestVariantBuilder variantBuilder,
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull TestProjectVariantDslInfo variantDslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull BaseVariantData variantData,
            @NonNull MutableTaskContainer taskContainer,
            @NonNull VariantServices variantServices,
            @NonNull TaskCreationServices taskCreationServices,
            @NonNull GlobalTaskCreationConfig globalConfig) {
        return dslServices.newInstance(
                TestVariantImpl.class,
                variantBuilder,
                buildFeatures,
                variantDslInfo,
                variantDependencies,
                variantSources,
                paths,
                artifacts,
                variantData,
                taskContainer,
                variantServices,
                taskCreationServices,
                globalConfig);
    }

    @NonNull
    @Override
    public BuildFeatureValues createBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures, @NonNull ProjectOptions projectOptions) {
        if (buildFeatures instanceof TestBuildFeatures) {
            return new BuildFeatureValuesImpl(
                    buildFeatures,
                    projectOptions,
                    false /* dataBindingOverride */,
                    false /* mlModelBindingOverride */);
        } else {
            throw new RuntimeException("buildFeatures not of type TestBuildFeatures");
        }
    }

    @NonNull
    @Override
    public BuildFeatureValues createTestFixturesBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures,
            @NonNull ProjectOptions projectOptions,
            boolean androidResourcesEnabled) {
        if (buildFeatures instanceof TestBuildFeatures) {
            return new TestFixturesBuildFeaturesValuesImpl(
                    buildFeatures,
                    projectOptions,
                    androidResourcesEnabled,
                    false /* dataBindingOverride */,
                    false /* mlModelBindingOverride */);
        } else {
            throw new RuntimeException("buildFeatures not of type TestBuildFeatures");
        }
    }

    @NonNull
    @Override
    public BuildFeatureValues createUnitTestBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures,
            @NonNull DataBinding dataBinding,
            @NonNull ProjectOptions projectOptions,
            boolean includeAndroidResources) {
        throw new RuntimeException("cannot instantiate test build features in test plugin");
    }

    @NonNull
    @Override
    public BuildFeatureValues createAndroidTestBuildFeatureValues(
            @NonNull BuildFeatures buildFeatures,
            @NonNull DataBinding dataBinding,
            @NonNull ProjectOptions projectOptions) {
        throw new RuntimeException("cannot instantiate test build features in test plugin");
    }

    @NonNull
    @Override
    public TestFixturesCreationConfig createTestFixtures(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull TestFixturesComponentDslInfo dslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull MutableTaskContainer taskContainer,
            @NonNull VariantCreationConfig mainVariant,
            @NonNull VariantServices variantServices,
            @NonNull TaskCreationServices taskCreationServices,
            @NonNull GlobalTaskCreationConfig globalConfig) {
        throw new RuntimeException("cannot instantiate test-fixtures properties in test plugin");
    }

    @NonNull
    @Override
    public UnitTestCreationConfig createUnitTest(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull UnitTestComponentDslInfo dslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull TestVariantData variantData,
            @NonNull MutableTaskContainer taskContainer,
            @NonNull VariantCreationConfig testedVariant,
            @NonNull VariantServices variantServices,
            @NonNull TaskCreationServices taskCreationServices,
            @NonNull GlobalTaskCreationConfig globalConfig) {
        throw new RuntimeException("cannot instantiate unit-test properties in test plugin");
    }

    @NonNull
    @Override
    public AndroidTestCreationConfig createAndroidTest(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull BuildFeatureValues buildFeatures,
            @NonNull AndroidTestComponentDslInfo dslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantSources variantSources,
            @NonNull VariantPathHelper paths,
            @NonNull ArtifactsImpl artifacts,
            @NonNull TestVariantData variantData,
            @NonNull MutableTaskContainer taskContainer,
            @NonNull VariantCreationConfig testedVariant,
            @NonNull VariantServices variantServices,
            @NonNull TaskCreationServices taskCreationServices,
            @NonNull GlobalTaskCreationConfig globalConfig) {
        throw new RuntimeException("cannot instantiate android-test properties in test plugin");
    }

    @Override
    public void preVariantCallback(
            @NonNull Project project,
            @NonNull CommonExtension<?, ?, ?, ?> dslExtension,
            @NonNull
                    VariantInputModel<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            model) {
        super.preVariantCallback(project, dslExtension, model);

        TestExtension testExtension = (TestExtension) dslExtension;

        String path = testExtension.getTargetProjectPath();
        if (path == null) {
            throw new GradleException(
                    "targetProjectPath cannot be null in test project " + project.getName());
        }

        DependencyHandler handler = project.getDependencies();
        Map<String, String> projectNotation = ImmutableMap.of("path", path);
        // Add the tested project to compileOnly. This cannot be 'implementation' because of the
        // following:
        //
        // The tested project itself only publishes to api, however its transitive library module
        // dependencies are published to both api and runtime elements and would be seen in our
        // RuntimeClasspath here otherwise.

        // TODO, we should do this after we created the variant object, not before.
        if (!ModulePropertyKeys.SELF_INSTRUMENTING.getValueAsBoolean(
                testExtension.getExperimentalProperties())) {
            handler.add(CONFIG_NAME_COMPILE_ONLY, handler.project(projectNotation));
        }

        // Create a custom configuration that will be used to consume only the APK from the
        // tested project's RuntimeElements published configuration.
        Configuration testedApks = project.getConfigurations().maybeCreate(CONFIG_NAME_TESTED_APKS);
        testedApks.setCanBeConsumed(false);
        testedApks.setCanBeResolved(false);
        handler.add(CONFIG_NAME_TESTED_APKS, handler.project(projectNotation));
    }

    @Override
    public void createDefaultComponents(@NonNull DslContainerProvider dslContainers) {
        // don't call super as we don't want the default app version.
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        dslContainers.getSigningConfigContainer().create(BuilderConstants.DEBUG);
        dslContainers.getBuildTypeContainer().create(BuilderConstants.DEBUG);
    }

    @NonNull
    @Override
    public ComponentType getComponentType() {
        return ComponentTypeImpl.TEST_APK;
    }
}
