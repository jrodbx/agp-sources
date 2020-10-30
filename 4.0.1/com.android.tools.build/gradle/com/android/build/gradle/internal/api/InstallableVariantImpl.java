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
import com.android.build.api.artifact.ArtifactType;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.InstallableVariant;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.InstallableVariantData;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Implementation of an installable variant.
 */
public abstract class InstallableVariantImpl extends AndroidArtifactVariantImpl implements InstallableVariant {

    protected InstallableVariantImpl(
            @NonNull ObjectFactory objectFactory,
            @NonNull ReadOnlyObjectProvider immutableObjectProvider,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> outputs) {
        super(objectFactory, immutableObjectProvider, outputs);
    }

    @NonNull
    @Override
    public abstract InstallableVariantData getVariantData();

    @Override
    public DefaultTask getInstall() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getInstallProvider()",
                        "variantOutput.getInstall()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);

        if (variantData.getTaskContainer().getInstallTask() != null) {
            return variantData.getTaskContainer().getInstallTask().getOrNull();
        }

        return null;
    }

    @Nullable
    @Override
    public TaskProvider<Task> getInstallProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) getVariantData().getTaskContainer().getInstallTask();
    }

    @Override
    public DefaultTask getUninstall() {
        BaseVariantData variantData = getVariantData();
        variantData
                .getScope()
                .getGlobalScope()
                .getDslScope()
                .getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getUninstallProvider()",
                        "variantOutput.getUninstall()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);

        if (variantData.getTaskContainer().getUninstallTask() != null) {
            return variantData.getTaskContainer().getUninstallTask().getOrNull();
        }

        return null;
    }

    @Nullable
    @Override
    public TaskProvider<Task> getUninstallProvider() {
        // Double cast needed to satisfy the compiler
        //noinspection unchecked
        return (TaskProvider<Task>)
                (TaskProvider<?>) getVariantData().getTaskContainer().getUninstallTask();
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
            @NonNull ArtifactType<? extends FileSystemLocation> artifactType) {
        BuildArtifactsHolder artifacts = getVariantData().getScope().getArtifacts();
        return artifacts.getFinalProductAsFileCollection((InternalArtifactType) artifactType);
    }
}
