/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins;

import com.android.AndroidProjectTypes;
import com.android.annotations.NonNull;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.dsl.TestAndroidResources;
import com.android.build.api.dsl.TestBuildFeatures;
import com.android.build.api.dsl.TestBuildType;
import com.android.build.api.dsl.TestDefaultConfig;
import com.android.build.api.dsl.TestProductFlavor;
import com.android.build.api.extension.impl.TestAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.TestAndroidComponentsExtension;
import com.android.build.api.variant.TestVariant;
import com.android.build.api.variant.TestVariantBuilder;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.TestApplicationTaskManager;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.component.TestVariantCreationConfig;
import com.android.build.gradle.internal.core.dsl.TestProjectVariantDslInfo;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.TestExtensionImpl;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.VersionedSdkLoaderService;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig;
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.TestVariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.ProjectType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.Collection;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'test' projects. */
public class TestPlugin
        extends BasePlugin<
                TestBuildFeatures,
                TestBuildType,
                TestDefaultConfig,
                TestProductFlavor,
                TestAndroidResources,
                com.android.build.api.dsl.TestExtension,
                TestAndroidComponentsExtension,
                TestVariantBuilder,
                TestProjectVariantDslInfo,
                TestVariantCreationConfig,
                TestVariant> {
    @Inject
    public TestPlugin(
            ToolingModelBuilderRegistry registry,
            SoftwareComponentFactory componentFactory,
            BuildEventsListenerRegistry listenerRegistry) {
        super(registry, componentFactory, listenerRegistry);
    }

    @Override
    protected int getProjectType() {
        return AndroidProjectTypes.PROJECT_TYPE_TEST;
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.TEST;
    }

    @NonNull
    @Override
    protected ExtensionData<
                    TestBuildFeatures,
                    TestBuildType,
                    TestDefaultConfig,
                    TestProductFlavor,
                    TestAndroidResources,
                    com.android.build.api.dsl.TestExtension>
            createExtension(
                    @NonNull DslServices dslServices,
                    @NonNull
                            DslContainerProvider<
                                            DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                                    dslContainers,
                    @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
                    @NonNull ExtraModelInfo extraModelInfo,
                    VersionedSdkLoaderService versionedSdkLoaderService) {
        TestExtensionImpl testExtension =
                dslServices.newDecoratedInstance(
                        TestExtensionImpl.class, dslServices, dslContainers);

        // detects whether we are running the plugin under unit test mode
        boolean forUnitTesting =
                project.getProviders().gradleProperty("_agp_internal_test_mode_").isPresent();

        BootClasspathConfigImpl bootClasspathConfig =
                new BootClasspathConfigImpl(
                        project,
                        getProjectServices(),
                        versionedSdkLoaderService,
                        testExtension,
                        forUnitTesting);

        if (getProjectServices().getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            // noinspection unchecked,rawtypes: Hacks to make the parameterized types make sense
            Class<com.android.build.api.dsl.TestExtension> instanceType =
                    (Class) TestExtension.class;
            TestExtension android =
                    (TestExtension)
                            project.getExtensions()
                                    .create(
                                            new TypeOf<
                                                    com.android.build.api.dsl.TestExtension>() {},
                                            "android",
                                            instanceType,
                                            dslServices,
                                            bootClasspathConfig,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            testExtension);
            project.getExtensions()
                    .add(TestExtension.class, "_internal_legacy_android_extension", android);

            initExtensionFromSettings(testExtension);
            return new ExtensionData<>(android, testExtension, bootClasspathConfig);
        }

        TestExtension android =
                project.getExtensions()
                        .create(
                                "android",
                                TestExtension.class,
                                dslServices,
                                bootClasspathConfig,
                                buildOutputs,
                                dslContainers.getSourceSetManager(),
                                extraModelInfo,
                                testExtension);
        initExtensionFromSettings(android);
        return new ExtensionData<>(android, testExtension, bootClasspathConfig);
    }

    /**
     * Create typed sub implementation for the extension objects. This has several benefits : 1. do
     * not pollute the user visible definitions with deprecated types. 2. because it's written in
     * Java, it will still compile once the deprecated extension are moved to Level.HIDDEN.
     */
    @SuppressWarnings("deprecation")
    public abstract static class TestAndroidComponentsExtensionImplCompat
            extends TestAndroidComponentsExtensionImpl
            implements AndroidComponentsExtension<
                    com.android.build.api.dsl.TestExtension, TestVariantBuilder, TestVariant> {

        public TestAndroidComponentsExtensionImplCompat(
                @NonNull DslServices dslServices,
                @NonNull SdkComponents sdkComponents,
                @NonNull ManagedDeviceRegistry deviceRegistry,
                @NonNull
                        VariantApiOperationsRegistrar<
                                        com.android.build.api.dsl.TestExtension,
                                        TestVariantBuilder,
                                        com.android.build.api.variant.TestVariant>
                                variantApiOperations,
                @NonNull TestExtension libraryExtension) {
            super(
                    dslServices,
                    sdkComponents,
                    deviceRegistry,
                    variantApiOperations,
                    libraryExtension);
        }
    }

    @NonNull
    @Override
    protected TestAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                                    com.android.build.api.dsl.TestExtension,
                                    TestVariantBuilder,
                                    TestVariant>
                            variantApiOperationsRegistrar,
            @NonNull BootClasspathConfig bootClasspathConfig) {
        SdkComponents sdkComponents =
                dslServices.newInstance(
                        SdkComponentsImpl.class,
                        dslServices,
                        project.provider(getExtension()::getCompileSdkVersion),
                        project.provider(getExtension()::getBuildToolsRevision),
                        project.provider(getExtension()::getNdkVersion),
                        project.provider(getExtension()::getNdkPath),
                        // need to keep this fully wrapped in a provider so that we don't initialize
                        // any of the lazy objects too early as they would read the
                        // compileSdkVersion
                        // too early.
                        project.provider(bootClasspathConfig::getBootClasspath));

        // register the same extension under a different name with the deprecated extension type.
        // this will allow plugins that use getByType() API to retrieve the old interface and keep
        // binary compatibility. This will become obsolete once old extension packages are removed.
        TestAndroidComponentsExtension extension =
                project.getExtensions()
                        .create(
                                TestAndroidComponentsExtension.class,
                                "androidComponents",
                                TestAndroidComponentsExtensionImplCompat.class,
                                dslServices,
                                sdkComponents,
                                getManagedDeviceRegistry(),
                                variantApiOperationsRegistrar,
                                getExtension());

        return extension;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.TEST;
    }

    @NonNull
    @Override
    protected TestApplicationTaskManager createTaskManager(
            @NonNull Project project,
            @NonNull
                    Collection<
                                    ? extends
                                            ComponentInfo<
                                                    TestVariantBuilder, TestVariantCreationConfig>>
                            variants,
            @NonNull Collection<? extends TestComponentCreationConfig> testComponents,
            @NonNull Collection<? extends TestFixturesCreationConfig> testFixturesComponents,
            @NonNull GlobalTaskCreationConfig globalTaskCreationConfig,
            @NonNull TaskManagerConfig localConfig,
            @NonNull BaseExtension extension) {
        return new TestApplicationTaskManager(
                project,
                variants,
                testComponents,
                testFixturesComponents,
                globalTaskCreationConfig,
                localConfig,
                extension);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // do nothing
    }

    @NonNull
    @Override
    protected TestVariantFactory createVariantFactory() {
        return new TestVariantFactory(getDslServices());
    }
}
