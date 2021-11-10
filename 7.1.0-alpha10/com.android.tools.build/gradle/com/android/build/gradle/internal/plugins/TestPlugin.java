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
import com.android.build.api.extension.impl.TestAndroidComponentsExtensionImpl;
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar;
import com.android.build.api.variant.TestAndroidComponentsExtension;
import com.android.build.api.variant.TestVariant;
import com.android.build.api.variant.TestVariantBuilder;
import com.android.build.api.variant.impl.TestVariantBuilderImpl;
import com.android.build.api.variant.impl.TestVariantImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.TestExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.TestApplicationTaskManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SdkComponentsImpl;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.TestExtensionImpl;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.TestVariantFactory;
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

/** Gradle plugin class for 'test' projects. */
public class TestPlugin
        extends BasePlugin<
                    com.android.build.api.dsl.TestExtension,
                    TestAndroidComponentsExtension,
                    TestVariantBuilderImpl,
                    TestVariantImpl> {
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
    protected BaseExtension createExtension(
            @NonNull DslServices dslServices,
            @NonNull GlobalScope globalScope,
            @NonNull
                    DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig>
                            dslContainers,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo) {
        TestExtensionImpl testExtension =
                dslServices.newDecoratedInstance(
                        TestExtensionImpl.class, dslServices, dslContainers);
        if (projectServices.getProjectOptions().get(BooleanOption.USE_NEW_DSL_INTERFACES)) {
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
                                            globalScope,
                                            buildOutputs,
                                            dslContainers.getSourceSetManager(),
                                            extraModelInfo,
                                            testExtension);
            project.getExtensions()
                    .add(TestExtension.class, "_internal_legacy_android_extension", android);
            return android;
        }

        return project.getExtensions()
                .create(
                        "android",
                        TestExtension.class,
                        dslServices,
                        globalScope,
                        buildOutputs,
                        dslContainers.getSourceSetManager(),
                        extraModelInfo,
                        testExtension);
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
                            com.android.build.api.dsl.TestExtension,
                            TestVariantBuilder,
                            TestVariant>,
                    com.android.build.api.extension.TestAndroidComponentsExtension {

        public TestAndroidComponentsExtensionImplCompat(
                @NonNull DslServices dslServices,
                @NonNull SdkComponents sdkComponents,
                @NonNull
                        VariantApiOperationsRegistrar<
                                        com.android.build.api.dsl.TestExtension,
                                        TestVariantBuilder,
                                        com.android.build.api.variant.TestVariant>
                                variantApiOperations,
                @NonNull TestExtension libraryExtension) {
            super(dslServices, sdkComponents, variantApiOperations, libraryExtension);
        }
    }

    @NonNull
    @Override
    protected TestAndroidComponentsExtension createComponentExtension(
            @NonNull DslServices dslServices,
            @NonNull VariantApiOperationsRegistrar<
                        com.android.build.api.dsl.TestExtension,
                        TestVariantBuilderImpl,
                        TestVariantImpl>
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
                                variantApiOperationsRegistrar,
                                getExtension());

        // register under the new interface for kotlin, groovy will find both the old and new
        // interfaces through the implementation class.
        project.getExtensions()
                .add(
                        com.android.build.api.extension.TestAndroidComponentsExtension.class,
                        "androidComponents_compat_by_type",
                        (com.android.build.api.extension.TestAndroidComponentsExtension) extension);

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
            @NonNull List<ComponentInfo<TestVariantBuilderImpl, TestVariantImpl>> variants,
            @NonNull List<TestComponentImpl> testComponents,
            @NonNull List<TestFixturesImpl> testFixturesComponents,
            boolean hasFlavors,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull ProjectInfo projectInfo) {
        return new TestApplicationTaskManager(
                variants,
                testComponents,
                testFixturesComponents,
                hasFlavors,
                projectOptions,
                globalScope,
                extension,
                projectInfo);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
        // do nothing
    }

    @NonNull
    @Override
    protected TestVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new TestVariantFactory(projectServices, globalScope);
    }
}
