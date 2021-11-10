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
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestFixturesImpl;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.AndroidComponentsExtension;
import com.android.build.api.extension.impl.ApplicationAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import com.android.build.api.variant.ApplicationVariant;
import com.android.build.api.variant.ApplicationVariantBuilder;
import com.android.build.api.variant.impl.ApplicationVariantBuilderImpl;
import com.android.build.api.variant.impl.ApplicationVariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.AppModelBuilder;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.ApplicationTaskManager;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.v2.ide.ProjectType;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin
        extends AbstractAppPlugin<
                com.android.build.api.dsl.ApplicationExtension,
                ApplicationAndroidComponentsExtension,
                ApplicationVariantBuilderImpl,
                ApplicationVariantImpl> {
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
            @NonNull ToolingModelBuilderRegistry registry,
            @NonNull GlobalScope globalScope,
            @NonNull VariantModel variantModel,
            @NonNull BaseExtension extension,
            @NonNull ExtraModelInfo extraModelInfo) {
        registry.register(
                new AppModelBuilder(
                        globalScope,
                        variantModel,
                        (BaseAppModuleExtension) extension,
                        extraModelInfo,
                        projectServices.getProjectOptions(),
                        projectServices.getIssueReporter(),
                        getProjectType(),
                        projectServices.getProjectInfo()));
    }

    @NonNull
    @Override
    protected BaseExtension createExtension(
            @NonNull DslServices dslServices,
            @NonNull GlobalScope globalScope,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        ApplicationExtensionImpl applicationExtension =
                dslServices.newDecoratedInstance(ApplicationExtensionImpl.class, dslServices, dslContainers);
        if (projectServices.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
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
                                            globalScope,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            applicationExtension);
            project.getExtensions()
                    .add(
                            BaseAppModuleExtension.class,
                            "_internal_legacy_android_extension",
                            android);
            return android;
        }

        return project.getExtensions()
                .create(
                        "android",
                        BaseAppModuleExtension.class,
                        dslServices,
                        globalScope,
                        buildOutputs,
                        dslContainers.getSourceSetManager(),
                        extraModelInfo,
                        applicationExtension);
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
                            ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant>,
                    com.android.build.api.extension.ApplicationAndroidComponentsExtension {

        public ApplicationAndroidComponentsExtensionImplCompat(
                @NotNull DslServices dslServices,
                @NotNull SdkComponents sdkComponents,
                @NotNull
                        VariantApiOperationsRegistrar<
                                        ApplicationExtension,
                                        ApplicationVariantBuilder,
                                        ApplicationVariant>
                                variantApiOperations,
                @NotNull ApplicationExtension applicationExtension) {
            super(dslServices, sdkComponents, variantApiOperations, applicationExtension);
        }
    }

    @NonNull
    @Override
    protected ApplicationAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                            com.android.build.api.dsl.ApplicationExtension,
                            ApplicationVariantBuilderImpl,
                            ApplicationVariantImpl>
                        variantApiOperationsRegistrar) {
        SdkComponents sdkComponents =
                dslServices.newInstance(
                        SdkComponentsImpl.class,
                        dslServices,
                        project.provider(getExtension()::getCompileSdkVersion),
                        project.provider(getExtension()::getBuildToolsRevision),
                        project.provider(getExtension()::getNdkVersion),
                        project.provider(getExtension()::getNdkPath),
                        project.provider(globalScope::getBootClasspath));

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
                                variantApiOperationsRegistrar,
                                getExtension());

        // register the same extension under a different name with the deprecated extension type.
        // this will allow plugins that use getByType() API to retrieve the old interface and keep
        // binary compatibility. This will become obsolete once old extension packages are removed.
        project.getExtensions()
                .add(
                        com.android.build.api.extension.ApplicationAndroidComponentsExtension.class,
                        "androidComponents_compat_by_type",
                        (com.android.build.api.extension.ApplicationAndroidComponentsExtension)
                                extension);

        return extension;
    }

    @NonNull
    @Override
    protected ApplicationTaskManager createTaskManager(
            @NonNull
                    List<ComponentInfo<ApplicationVariantBuilderImpl, ApplicationVariantImpl>>
                            variants,
            @NonNull List<TestComponentImpl> testComponents,
            @NonNull List<TestFixturesImpl> testFixturesComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo) {
        return new ApplicationTaskManager(
                variants,
                testComponents,
                testFixturesComponents,
                hasFlavors,
                projectOptions,
                globalScope,
                extension,
                projectInfo);
    }

    @NonNull
    @Override
    protected ApplicationVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new ApplicationVariantFactory(projectServices, globalScope);
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.APPLICATION;
    }
}
