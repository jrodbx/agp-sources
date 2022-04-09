/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.Artifact;
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstallableVariant;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.services.VariantPropertiesApiServices;
import com.android.build.gradle.internal.variant.ApkVariantData;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Implementation of an installable variant.
 */
public abstract class InstallableVariantImpl extends AndroidArtifactVariantImpl implements InstallableVariant {

    protected InstallableVariantImpl(
            @NonNull ComponentImpl component,
            @NonNull VariantPropertiesApiServices services,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(component, services, immutableObjectProvider, outputs);
    }

    @NonNull
    @Override
    public abstract ApkVariantData getVariantData();

    @Override
    public DefaultTask getInstall() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getInstallProvider()",
                        "variantOutput.getInstall()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);

        if (component.getTaskContainer().getInstallTask() != null) {
            return component.getTaskContainer().getInstallTask().getOrNull();
        }

        return null;
    }

    @Nullable
    @Override
    public TaskProvider<Task> getInstallProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>) (TaskProvider<?>) component.getTaskContainer().getInstallTask();
    }

    @Override
    public DefaultTask getUninstall() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getUninstallProvider()",
                        "variantOutput.getUninstall()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);

        if (component.getTaskContainer().getUninstallTask() != null) {
            return component.getTaskContainer().getUninstallTask().getOrNull();
        }

        return null;
    }

    @Nullable
    @Override
    public TaskProvider<Task> getUninstallProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) component.getTaskContainer().getUninstallTask();
    }

    /**
     * Semi Private APIs that we share with friends until a public API is available.
     *
     * <p>Provides a facility to retrieve the final version of an artifact type.
     *
     * @param artifactType requested artifact type.
     * @return a {@see Provider} of a {@see FileCollection} for this artifact type, possibly empty.
     */
    @NonNull
    @Incubating
    public Provider<FileCollection> getFinalArtifact(
            @NonNull Artifact.Single<? extends FileSystemLocation> artifactType) {
        return component
                .getServices()
                .provider(
                        () ->
                                component
                                        .getServices()
                                        .fileCollection(
                                                component.getArtifacts().get(artifactType)));
    }
}
