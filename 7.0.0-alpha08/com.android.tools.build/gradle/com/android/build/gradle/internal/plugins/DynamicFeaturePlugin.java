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
import com.android.build.api.component.impl.TestComponentBuilderImpl;
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.DynamicFeatureAndroidComponentsExtension;
import com.android.build.api.extension.impl.DynamicFeatureAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.impl.DynamicFeatureVariantBuilderImpl;
import com.android.build.api.variant.impl.DynamicFeatureVariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.DynamicFeatureExtension;
import com.android.build.gradle.internal.dsl.DynamicFeatureExtensionImpl;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.DynamicFeatureTaskManager;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.DynamicFeatureVariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.ProjectType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on an optional APK module */
public class DynamicFeaturePlugin
        extends AbstractAppPlugin<
                DynamicFeatureAndroidComponentsExtension,
                DynamicFeatureVariantBuilderImpl,
                DynamicFeatureVariantImpl> {
    @Inject
    public DynamicFeaturePlugin(
            ToolingModelBuilderRegistry registry,
            SoftwareComponentFactory componentFactory,
            BuildEventsListenerRegistry listenerRegistry) {
        super(registry, componentFactory, listenerRegistry);
    }

    @Override
    protected int getProjectType() {
        return AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE;
    }

    @Override
    protected ProjectType getProjectTypeV2() {
        return ProjectType.DYNAMIC_FEATURE;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.DYNAMIC_FEATURE;
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // do nothing
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
        DynamicFeatureExtensionImpl dynamicFeatureExtension =
                dslServices.newDecoratedInstance(
                        DynamicFeatureExtensionImpl.class, dslServices, dslContainers);
        if (globalScope.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            return (BaseExtension)
                    project.getExtensions()
                            .create(
                                    com.android.build.api.dsl.DynamicFeatureExtension.class,
                                    "android",
                                    DynamicFeatureExtension.class,
                                    dslServices,
                                    globalScope,
                                    buildOutputs,
                                    dslContainers.getSourceSetManager(),
                                    extraModelInfo,
                                    dynamicFeatureExtension);
        }

        return project.getExtensions()
                .create(
                        "android",
                        DynamicFeatureExtension.class,
                        dslServices,
                        globalScope,
                        buildOutputs,
                        dslContainers.getSourceSetManager(),
                        extraModelInfo,
                        dynamicFeatureExtension);
    }

    @NonNull
    @Override
    protected DynamicFeatureAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                                    DynamicFeatureVariantBuilderImpl, DynamicFeatureVariantImpl>
                            variantApiOperationsRegistrar) {
        SdkComponents sdkComponents =
                dslServices.newInstance(
                        SdkComponentsImpl.class,
                        dslServices,
                        project.provider(getExtension()::getCompileSdkVersion),
                        project.provider(getExtension()::getBuildToolsRevision),
                        project.provider(getExtension()::getNdkVersion),
                        project.provider(getExtension()::getNdkPath));

        return project.getExtensions()
                .create(
                        DynamicFeatureAndroidComponentsExtension.class,
                        "androidComponents",
                        DynamicFeatureAndroidComponentsExtensionImpl.class,
                        dslServices,
                        sdkComponents,
                        variantApiOperationsRegistrar,
                        getExtension());
    }

    @NonNull
    @Override
    protected DynamicFeatureTaskManager createTaskManager(
            @NonNull
                    List<ComponentInfo<DynamicFeatureVariantBuilderImpl, DynamicFeatureVariantImpl>>
                            variants,
            @NonNull
                    List<ComponentInfo<TestComponentBuilderImpl, TestComponentImpl>> testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension) {
        return new DynamicFeatureTaskManager(
                variants, testComponents, hasFlavors, globalScope, extension);
    }

    @NonNull
    @Override
    protected DynamicFeatureVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new DynamicFeatureVariantFactory(projectServices, globalScope);
    }


}
