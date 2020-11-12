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
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.variant.impl.LibraryVariantImpl;
import com.android.build.api.variant.impl.LibraryVariantPropertiesImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LibraryTaskManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.LibraryExtensionImpl;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.variant.ComponentInfo;
import com.android.build.gradle.internal.variant.LibraryVariantFactory;
import com.android.builder.profile.Recorder;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'library' projects. */
public class LibraryPlugin extends BasePlugin<LibraryVariantImpl, LibraryVariantPropertiesImpl> {

    @Inject
    public LibraryPlugin(
            ToolingModelBuilderRegistry registry, SoftwareComponentFactory componentFactory) {
        super(registry, componentFactory);
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
        return project.getExtensions()
                .create(
                        "android",
                        getExtensionClass(),
                        dslServices,
                        globalScope,
                        buildOutputs,
                        dslContainers.getSourceSetManager(),
                        extraModelInfo,
                        new LibraryExtensionImpl(dslServices, dslContainers));
    }

    @NonNull
    protected Class<? extends BaseExtension> getExtensionClass() {
        return LibraryExtension.class;
    }

    @NonNull
    @Override
    protected GradleBuildProject.PluginType getAnalyticsPluginType() {
        return GradleBuildProject.PluginType.LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryVariantFactory createVariantFactory(
            @NonNull ProjectServices projectServices, @NonNull GlobalScope globalScope) {
        return new LibraryVariantFactory(projectServices, globalScope);
    }

    @Override
    protected int getProjectType() {
        return AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
    }

    @NonNull
    @Override
    protected LibraryTaskManager createTaskManager(
            @NonNull List<ComponentInfo<LibraryVariantImpl, LibraryVariantPropertiesImpl>> variants,
            @NonNull
                    List<
                                    ComponentInfo<
                                            TestComponentImpl<
                                                    ? extends TestComponentPropertiesImpl>,
                                            TestComponentPropertiesImpl>>
                            testComponents,
            boolean hasFlavors,
            @NonNull GlobalScope globalScope,
            @NonNull BaseExtension extension,
            @NonNull Recorder recorder) {
        return new LibraryTaskManager(
                variants, testComponents, hasFlavors, globalScope, extension, recorder);
    }

    @Override
    protected void pluginSpecificApply(@NonNull Project project) {
    }

    @Override
    protected boolean isPackagePublished() {
        return true;
    }
}
