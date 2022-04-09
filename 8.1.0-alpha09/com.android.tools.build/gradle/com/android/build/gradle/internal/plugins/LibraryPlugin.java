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
import com.android.build.api.dsl.LibraryBuildFeatures;
import com.android.build.api.dsl.LibraryExtension;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.impl.LibraryAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.LibraryAndroidComponentsExtension;
import com.android.build.api.variant.LibraryVariant;
import com.android.build.api.variant.LibraryVariantBuilder;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.component.LibraryCreationConfig;
import com.android.build.gradle.internal.component.TestComponentCreationConfig;
import com.android.build.gradle.internal.component.TestFixturesCreationConfig;
import com.android.build.gradle.internal.core.dsl.LibraryVariantDslInfo;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.LibraryExtensionImpl;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.VersionedSdkLoaderService;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig;
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfigImpl;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskManagerConfig;
import com.android.build.gradle.internal.testing.ManagedDeviceRegistry;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.LibraryVariantFactory;
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

/** Gradle plugin class for 'library' projects. */
public class LibraryPlugin
        extends BasePlugin<
                LibraryBuildFeatures,
                com.android.build.api.dsl.LibraryBuildType,
                com.android.build.api.dsl.LibraryDefaultConfig,
                com.android.build.api.dsl.LibraryProductFlavor,
                LibraryExtension,
                LibraryAndroidComponentsExtension,
                LibraryVariantBuilder,
                LibraryVariantDslInfo,
                LibraryCreationConfig,
                LibraryVariant> {

    @Inject
    public LibraryPlugin(
            ToolingModelBuilderRegistry registry,
            SoftwareComponentFactory componentFactory,
            BuildEventsListenerRegistry listenerRegistry) {
        super(registry, componentFactory, listenerRegistry);
    }

    @NonNull
    @Override
    protected ExtensionData<
            LibraryBuildFeatures,
            com.android.build.api.dsl.LibraryBuildType,
            com.android.build.api.dsl.LibraryDefaultConfig,
            com.android.build.api.dsl.LibraryProductFlavor,
            LibraryExtension> createExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo,
            VersionedSdkLoaderService versionedSdkLoaderService) {
        LibraryExtensionImpl libraryExtension =
                dslServices.newDecoratedInstance(
                        LibraryExtensionImpl.class, dslServices, dslContainers);

        // detects whether we are running the plugin under unit test mode
        boolean forUnitTesting =
                project.getProviders().gradleProperty("_agp_internal_test_mode_").isPresent();

        BootClasspathConfigImpl bootClasspathConfig =
                new BootClasspathConfigImpl(
                        project,
                        getProjectServices(),
                        versionedSdkLoaderService,
                        libraryExtension,
                        forUnitTesting);

        if (getProjectServices().getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            // noinspection unchecked,rawtypes: Hacks to make the parameterized types make sense
            Class<LibraryExtension> instanceType =
                    (Class) com.android.build.gradle.LibraryExtension.class;
            com.android.build.gradle.LibraryExtension android =
                    (com.android.build.gradle.LibraryExtension)
                            (Object)
                                    project.getExtensions()
                                            .create(
                                                    new TypeOf<
                                                            com.android.build.api.dsl
                                                                    .LibraryExtension>() {},
                                                    "android",
                                                    instanceType,
                                                    dslServices,
                                                    bootClasspathConfig,
                                                    buildOutputs,
                                                    dslContainers.getSourceSetManager(),
                                                    extraModelInfo,
                                                    libraryExtension);
            project.getExtensions()
                    .add(
                            com.android.build.gradle.LibraryExtension.class,
                            "_internal_legacy_android_extension",
                            android);

            initExtensionFromSettings(libraryExtension);

            return new ExtensionData<>(android, libraryExtension, bootClasspathConfig);
        }

        com.android.build.gradle.LibraryExtension android =
                project.getExtensions()
                        .create(
                                "android",
                                com.android.build.gradle.LibraryExtension.class,
                                dslServices,
                                bootClasspathConfig,
                                buildOutputs,
                                dslContainers.getSourceSetManager(),
                                extraModelInfo,
                                libraryExtension);
        initExtensionFromSettings(android);

        return new ExtensionData<>(android, libraryExtension, bootClasspathConfig);
    }

    /**
     * Create typed sub implementation for the extension objects. This has several benefits : 1. do
     * not pollute the user visible definitions with deprecated types. 2. because it's written in
     * Java, it will still compile once the deprecated extension are moved to Level.HIDDEN.
     */
    @SuppressWarnings("deprecation")
    public abstract static class LibraryAndroidComponentsExtensionImplCompat
            extends LibraryAndroidComponentsExtensionImpl
            implements AndroidComponentsExtension<
                    LibraryExtension, LibraryVariantBuilder, LibraryVariant> {

        public LibraryAndroidComponentsExtensionImplCompat(
                @NonNull DslServices dslServices,
                @NonNull SdkComponents sdkComponents,
                @NonNull ManagedDeviceRegistry deviceRegistry,
                @NonNull
                        VariantApiOperationsRegistrar<
                                        LibraryExtension, LibraryVariantBuilder, LibraryVariant>
                                variantApiOperations,
                @NonNull com.android.build.gradle.LibraryExtension libraryExtension) {
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
    protected LibraryAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                                    LibraryExtension, LibraryVariantBuilder, LibraryVariant>
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
        LibraryAndroidComponentsExtension extension =
                project.getExtensions()
                        .create(
                                LibraryAndroidComponentsExtension.class,
                                "androidComponents",
                                LibraryAndroidComponentsExtensionImplCompat.class,
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
        return GradleBuildProject.PluginType.LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryVariantFactory createVariantFactory() {
        return new LibraryVariantFactory(getDslServices());
    }

    @Override
    protected int getProjectType() {
        return AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryTaskManager createTaskManager(
            @NonNull Project project,
            @NonNull
                    Collection<
                                    ? extends
                                            ComponentInfo<
                                                    LibraryVariantBuilder, LibraryCreationConfig>>
                            variants,
            @NonNull Collection<? extends TestComponentCreationConfig> testComponents,
            @NonNull Collection<? extends TestFixturesCreationConfig> testFixturesComponents,
            @NonNull GlobalTaskCreationConfig globalTaskCreationConfig,
            @NonNull TaskManagerConfig localConfig,
            @NonNull BaseExtension extension) {
        return new LibraryTaskManager(
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
    }

    @Override
    protected boolean isPackagePublished() {
        return true;
    }
}
