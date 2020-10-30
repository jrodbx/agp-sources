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
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.AppModelBuilder;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.variant.ApplicationVariantFactory;
import com.android.build.gradle.internal.variant.VariantModel;
import com.android.build.gradle.options.ProjectOptions;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** Gradle plugin class for 'application' projects, applied on the base application module */
public class AppPlugin extends AbstractAppPlugin {
    @Inject
    public AppPlugin(
            ToolingModelBuilderRegistry registry, SoftwareComponentFactory componentFactory) {
        super(registry, componentFactory);
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
                        taskManager,
                        (BaseAppModuleExtension) extension,
                        extraModelInfo,
                        syncIssueHandler,
                        getProjectType()));
    }

    @Override
    @NonNull
    protected Class<? extends AppExtension> getExtensionClass() {
        return BaseAppModuleExtension.class;
    }

    @NonNull
    @Override
    protected AppExtension createExtension(
            @NonNull DslScope dslScope,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull DefaultConfig defaultConfig,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
        return project.getExtensions()
                .create(
                        "android",
                        getExtensionClass(),
                        dslScope,
                        projectOptions,
                        globalScope,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo,
                        new ApplicationExtensionImpl(
                                globalScope.getDslScope(),
                                buildTypeContainer,
                                defaultConfig,
                                productFlavorContainer,
                                signingConfigContainer));
    }

    private static class DeprecatedConfigurationAction implements Action<Dependency> {
        @NonNull private final String newDslElement;
        @NonNull private final String configName;
        @NonNull private final DeprecationReporter deprecationReporter;
        @NonNull private final DeprecationReporter.DeprecationTarget target;
        private boolean warningPrintedAlready = false;

        public DeprecatedConfigurationAction(
                @NonNull String newDslElement,
                @NonNull String configName,
                @NonNull DeprecationReporter deprecationReporter,
                @NonNull DeprecationReporter.DeprecationTarget target) {
            this.newDslElement = newDslElement;
            this.configName = configName;
            this.deprecationReporter = deprecationReporter;
            this.target = target;
        }

        @Override
        public void execute(@NonNull Dependency dependency) {
            if (!warningPrintedAlready) {
                warningPrintedAlready = true;
                deprecationReporter.reportDeprecatedConfiguration(
                        newDslElement, configName, target);
            }
        }
    }

    @NonNull
    @Override
    protected ApplicationVariantFactory createVariantFactory(@NonNull GlobalScope globalScope) {
        return new ApplicationVariantFactory(globalScope);
    }
}
