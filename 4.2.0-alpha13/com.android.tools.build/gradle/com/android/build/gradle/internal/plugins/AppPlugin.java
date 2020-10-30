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
import com.android.build.api.component.impl.TestComponentBuilderImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.dsl.ApplicationExtension;
import com.android.build.api.extension.ApplicationAndroidComponentsExtension;
import com.android.build.api.extension.impl.ApplicationAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.OperationsRegistrar;
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
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.ApplicationTaskManager;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.ProjectType;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin
        extends AbstractAppPlugin<
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
                        projectServices.getIssueReporter(),
                        getProjectType()));
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
        if (globalScope.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            return (BaseExtension)
                    project.getExtensions()
                            .create(
                                    ApplicationExtension.class,
                                    "android",
                                    BaseAppModuleExtension.class,
                                    dslServices,
                                    globalScope,
                                    buildOutputs,
                                    dslContainers.getSourceSetManager(),
                                    extraModelInfo,
                                    new ApplicationExtensionImpl(dslServices, dslContainers));
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
                        new ApplicationExtensionImpl(dslServices, dslContainers));
    }

    @NonNull
    @Override
    protected ApplicationAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    OperationsRegistrar<ApplicationVariantBuilderImpl>
                            variantBuilderOperationsRegistrar,
            @NonNull OperationsRegistrar<ApplicationVariantImpl> variantOperationsRegistrar) {
        return project.getExtensions()
                .create(
                        ApplicationAndroidComponentsExtension.class,
                        "androidComponents",
                        ApplicationAndroidComponentsExtensionImpl.class,
                        dslServices,
                        variantBuilderOperationsRegistrar,
                        variantOperationsRegistrar);
    }

    @NonNull
    @Override
    protected ApplicationTaskManager createTaskManager(
            @NonNull
                    List<ComponentInfo<ApplicationVariantBuilderImpl, ApplicationVariantImpl>>
                            variants,
            @NonNull
                    List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension) {
        return new ApplicationTaskManager(
                variants, testComponents, hasFlavors, globalScope, extension);
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
