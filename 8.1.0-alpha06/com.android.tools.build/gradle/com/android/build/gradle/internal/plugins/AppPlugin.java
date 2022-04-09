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

import com.android.annotations.NonNull;
import com.android.build.api.dsl.ApplicationBuildFeatures;
import com.android.build.api.dsl.ApplicationBuildType;
import com.android.build.api.dsl.ApplicationDefaultConfig;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.ApplicationProductFlavor;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.impl.ApplicationAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.ApplicationVariant;
import com.android.build.api.variant.ApplicationVariantBuilder;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.AppModelBuilder;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.component.ApplicationCreationConfig;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo;
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.VersionedSdkLoaderService;
import com.android.build.gradle.internal.tasks.ApplicationTaskManager;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig;
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.ProjectType;
import java.util.Collection;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin
        extends AbstractAppPlugin<
                ApplicationBuildFeatures,
                ApplicationBuildType,
                ApplicationDefaultConfig,
                ApplicationProductFlavor,
                com.android.build.api.dsl.ApplicationExtension,
                ApplicationAndroidComponentsExtension,
                ApplicationVariantBuilder,
                ApplicationVariantDslInfo,
                ApplicationCreationConfig,
                ApplicationVariant> {
    @Inject
    public AppPlugin(
            ToolingModelBuilderRegistry registry,
            SoftwareComponentFactory componentFactory,
            BuildEventsListenerRegistry listenerRegistry) {
        super(registry, componentFactory, listenerRegistry);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
    }

    @Override
    protected void registerModelBuilder(
            @NonNull Project project,
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull VariantModel variantModel,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new AppModelBuilder(
                        project, variantModel, (BaseAppModuleExtension) extension, extraModelInfo));
    }

    @NonNull
    @Override
    protected ExtensionData<
            ApplicationBuildFeatures,
            ApplicationBuildType,
            ApplicationDefaultConfig,
            ApplicationProductFlavor,
            ApplicationExtension> createExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull VersionedSdkLoaderService versionedSdkLoaderService) {
        ApplicationExtensionImpl applicationExtension =
                dslServices.newDecoratedInstance(ApplicationExtensionImpl.class, dslServices, dslContainers);

        // detects whether we are running the plugin under unit test mode
        boolean forUnitTesting =
                project.getProviders().gradleProperty("_agp_internal_test_mode_").isPresent();

        BootClasspathConfigImpl bootClasspathConfig =
                new BootClasspathConfigImpl(
                        project,
                        getProjectServices(),
                        versionedSdkLoaderService,
                        applicationExtension,
                        forUnitTesting);

        if (getProjectServices().getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            // noinspection unchecked,rawtypes: Hacks to make the parameterized types make sense
            Class<ApplicationExtension> instanceType = (Class) BaseAppModuleExtension.class;
            BaseAppModuleExtension android =
                    (BaseAppModuleExtension)
                            project.getExtensions()
                                    .create(
                                            new TypeOf<ApplicationExtension>() {},
                                            "android",
                                            instanceType,
                                            dslServices,
                                            bootClasspathConfig,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            applicationExtension);
            project.getExtensions()
                    .add(
                            BaseAppModuleExtension.class,
                            "_internal_legacy_android_extension",
                            android);

            initExtensionFromSettings(applicationExtension);

            return new ExtensionData<>(android, applicationExtension, bootClasspathConfig);
        }

        BaseAppModuleExtension android =
                project.getExtensions()
                        .create(
                                "android",
                                BaseAppModuleExtension.class,
                                dslServices,
                                bootClasspathConfig,
                                buildOutputs,
                                dslContainers.getSourceSetManager(),
                                extraModelInfo,
                                applicationExtension);
        initExtensionFromSettings(android);
        return new ExtensionData<>(android, applicationExtension, bootClasspathConfig);
    }

    /**
     * Create typed sub implementation for the extension objects. This has several benefits : 1. do
     * not pollute the user visible definitions with deprecated types. 2. because it's written in
     * Java, it will still compile once the deprecated extension are moved to Level.HIDDEN.
     */
    @SuppressWarnings("deprecation")
    public abstract static class ApplicationAndroidComponentsExtensionImplCompat
            extends ApplicationAndroidComponentsExtensionImpl
            implements AndroidComponentsExtension<
                    ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> {

        public ApplicationAndroidComponentsExtensionImplCompat(
                @NonNull DslServices dslServices,
                @NonNull SdkComponents sdkComponents,
                @NonNull ManagedDeviceRegistry deviceRegistry,
                @NonNull
                        VariantApiOperationsRegistrar<
                                        ApplicationExtension,
                                        ApplicationVariantBuilder,
                                        ApplicationVariant>
                                variantApiOperations,
                @NonNull ApplicationExtension applicationExtension) {
            super(
                    dslServices,
                    sdkComponents,
                    deviceRegistry,
                    variantApiOperations,
                    applicationExtension);
        }
    }

    @NonNull
    @Override
    protected ApplicationAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                                    ApplicationExtension,
                                    ApplicationVariantBuilder,
                                    ApplicationVariant>
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

        // register under the new interface for kotlin, groovy will find both the old and new
        // interfaces through the implementation class.
        //noinspection deprecation
        ApplicationAndroidComponentsExtension extension =
                project.getExtensions()
                        .create(
                                ApplicationAndroidComponentsExtension.class,
                                "androidComponents",
                                ApplicationAndroidComponentsExtensionImplCompat.class,
                                dslServices,
                                sdkComponents,
                                new ManagedDeviceRegistry(getExtension().getTestOptions()),
                                variantApiOperationsRegistrar,
                                getExtension());

        return extension;
    }

    @NonNull
    @Override
    protected ApplicationTaskManager createTaskManager(
            @NonNull Project project,
            @NonNull
                    Collection<
                                    ? extends
                                            ComponentInfo<
                                                    ApplicationVariantBuilder,
                                                    ApplicationCreationConfig>>
                            variants,
            @NonNull Collection<? extends TestComponentCreationConfig> testComponents,
            @NonNull Collection<? extends TestFixturesCreationConfig> testFixturesComponents,
            @NonNull GlobalTaskCreationConfig globalTaskCreationConfig,
            @NonNull TaskManagerConfig localConfig,
            @NonNull BaseExtension extension) {
        return new ApplicationTaskManager(
                project,
                variants,
                testComponents,
                testFixturesComponents,
                globalTaskCreationConfig,
                localConfig,
                extension);
    }

    @NonNull
    @Override
    protected ApplicationVariantFactory createVariantFactory() {
        return new ApplicationVariantFactory(getDslServices());
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.APPLICATION;
    }
}
