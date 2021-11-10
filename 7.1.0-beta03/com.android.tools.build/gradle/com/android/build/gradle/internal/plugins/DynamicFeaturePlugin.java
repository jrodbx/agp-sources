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
import com.android.build.api.component.impl.TestComponentImpl;
import com.android.build.api.component.impl.TestFixturesImpl;
import com.android.build.api.dsl.SdkComponents;
import com.android.build.api.extension.AndroidComponentsExtension;
import com.android.build.api.extension.impl.DynamicFeatureAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.DynamicFeatureAndroidComponentsExtension;
import com.android.build.api.variant.DynamicFeatureVariant;
import com.android.build.api.variant.DynamicFeatureVariantBuilder;
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
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.DynamicFeatureTaskManager;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.DynamicFeatureVariantFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.v2.ide.ProjectType;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;

/** Gradle plugin class for 'application' projects, applied on an optional APK module */
public class DynamicFeaturePlugin
        extends AbstractAppPlugin<
                com.android.build.api.dsl.DynamicFeatureExtension,
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
        if (projectServices.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
            //noinspection unchecked,rawtypes I am so sorry.
            Class<com.android.build.api.dsl.DynamicFeatureExtension> instanceType =
                    (Class) DynamicFeatureExtension.class;
            DynamicFeatureExtension android =
                    (DynamicFeatureExtension)
                            project.getExtensions()
                                    .create(
                                            new TypeOf<
                                                    com.android.build.api.dsl
                                                            .DynamicFeatureExtension>() {},
                                            "android",
                                            instanceType,
                                            dslServices,
                                            globalScope,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            dynamicFeatureExtension);
            project.getExtensions()
                    .add(
                            DynamicFeatureExtension.class,
                            "_internal_legacy_android_extension",
                            android);
            return android;
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

    /**
     * Create typed sub implementation for the extension objects. This has several benefits : 1. do
     * not pollute the user visible definitions with deprecated types. 2. because it's written in
     * Java, it will still compile once the deprecated extension are moved to Level.HIDDEN.
     */
    @SuppressWarnings("deprecation")
    public abstract static class DynamicFeatureAndroidComponentsExtensionImplCompat
            extends DynamicFeatureAndroidComponentsExtensionImpl
            implements AndroidComponentsExtension<
                            com.android.build.api.dsl.DynamicFeatureExtension,
                            DynamicFeatureVariantBuilder,
                            DynamicFeatureVariant>,
                    com.android.build.api.extension.DynamicFeatureAndroidComponentsExtension {

        public DynamicFeatureAndroidComponentsExtensionImplCompat(
                @NotNull DslServices dslServices,
                @NotNull SdkComponents sdkComponents,
                @NotNull
                        VariantApiOperationsRegistrar<
                                        com.android.build.api.dsl.DynamicFeatureExtension,
                                        DynamicFeatureVariantBuilder,
                                        DynamicFeatureVariant>
                                variantApiOperations,
                @NotNull DynamicFeatureExtension DynamicFeatureExtension) {
            super(dslServices, sdkComponents, variantApiOperations, DynamicFeatureExtension);
        }
    }

    @NonNull
    @Override
    protected DynamicFeatureAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull
                    VariantApiOperationsRegistrar<
                                com.android.build.api.dsl.DynamicFeatureExtension,
                                DynamicFeatureVariantBuilderImpl,
                                DynamicFeatureVariantImpl>
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
        DynamicFeatureAndroidComponentsExtension extension =
                project.getExtensions()
                        .create(
                                DynamicFeatureAndroidComponentsExtension.class,
                                "androidComponents",
                                DynamicFeatureAndroidComponentsExtensionImplCompat.class,
                                dslServices,
                                sdkComponents,
                                variantApiOperationsRegistrar,
                                getExtension());

        // register the same extension under a different name with the deprecated extension type.
        // this will allow plugins that use getByType() API to retrieve the old interface and keep
        // binary compatibility. This will become obsolete once old extension packages are removed.
        project.getExtensions()
                .add(
                        com.android.build.api.extension.DynamicFeatureAndroidComponentsExtension
                                .class,
                        "androidComponents_compat_by_type",
                        (com.android.build.api.extension.DynamicFeatureAndroidComponentsExtension)
                                extension);

        return extension;
    }

    @NonNull
    @Override
    protected DynamicFeatureTaskManager createTaskManager(
            @NonNull
                    List<ComponentInfo<DynamicFeatureVariantBuilderImpl, DynamicFeatureVariantImpl>>
                            variants,
            @NonNull List<TestComponentImpl> testComponents,
            @NonNull List<TestFixturesImpl> testFixturesComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo) {
        return new DynamicFeatureTaskManager(
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
    protected DynamicFeatureVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new DynamicFeatureVariantFactory(projectServices, globalScope);
    }


}
