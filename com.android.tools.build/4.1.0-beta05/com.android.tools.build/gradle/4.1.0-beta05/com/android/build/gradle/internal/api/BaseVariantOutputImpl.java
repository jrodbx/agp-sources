/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.build.gradle.internal.api.BaseVariantImpl.TASK_ACCESS_DEPRECATION_URL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.api.variant.impl.VariantOutputConfigurationImplKt;
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.ide.FilterDataImpl;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

/**
 * Implementation of the base variant output. This is the base class for items common to apps,
 * test apps, and libraries
 *
 * This is a wrapper around the internal data model, in order to control what is accessible
 * through the external API.
 */
public abstract class BaseVariantOutputImpl implements BaseVariantOutput {

    @NonNull protected final TaskContainer taskContainer;
    @NonNull protected final BaseServices services;
    @NonNull protected final VariantOutputImpl variantOutput;

    protected BaseVariantOutputImpl(
            @NonNull TaskContainer taskContainer,
            @NonNull BaseServices services,
            @NonNull VariantOutputImpl variantOutput) {
        this.taskContainer = taskContainer;
        this.services = services;
        this.variantOutput = variantOutput;
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        throw new UnsupportedOperationException(
                "getMainOutputFile is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public File getOutputFile() {
        throw new UnsupportedOperationException(
                "getOutputFile is no longer supported.  Use getOutputFileName if you need to "
                        + "determine the file name of the output.");
    }

    @NonNull
    @Override
    public ImmutableList<OutputFile> getOutputs() {
        return ImmutableList.of(this);
    }

    @NonNull
    @Override
    public ProcessAndroidResources getProcessResources() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getProcessResourcesProvider()",
                        "variantOutput.getProcessResources()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getProcessAndroidResTask().getOrNull();
    }

    @NonNull
    @Override
    public TaskProvider<ProcessAndroidResources> getProcessResourcesProvider() {
        //noinspection unchecked
        return (TaskProvider<ProcessAndroidResources>) taskContainer.getProcessAndroidResTask();
    }

    @Override
    @NonNull
    public ManifestProcessorTask getProcessManifest() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variantOutput.getProcessManifestProvider()",
                        "variantOutput.getProcessManifest()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getProcessManifestTask().get();
    }

    @NonNull
    @Override
    public TaskProvider<ManifestProcessorTask> getProcessManifestProvider() {
        //noinspection unchecked
        return (TaskProvider<ManifestProcessorTask>) taskContainer.getProcessManifestTask();
    }

    @Nullable
    @Override
    public Task getAssemble() {
        services.getDeprecationReporter()
                .reportDeprecatedApi(
                        "variant.getAssembleProvider()",
                        "variantOutput.getAssemble()",
                        TASK_ACCESS_DEPRECATION_URL,
                        DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getAssembleTask().get();
    }

    @NonNull
    @Override
    public String getName() {
        return variantOutput.getBaseName();
    }

    @NonNull
    @Override
    public String getBaseName() {
        return variantOutput.getBaseName();
    }

    @NonNull
    @Override
    public String getDirName() {
        return VariantOutputConfigurationImplKt.dirName(
                variantOutput.getVariantOutputConfiguration());
    }

    @NonNull
    @Override
    public String getOutputType() {
        if (variantOutput.getOutputType() == VariantOutputConfiguration.OutputType.SINGLE) {
            return OutputType.MAIN.name();
        } else {
            return OutputType.FULL_SPLIT.name();
        }
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return variantOutput.getVariantOutputConfiguration().getFilters().stream()
                .map(FilterConfiguration::getFilterType)
                .map(FilterConfiguration.FilterType::name)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return variantOutput.getVariantOutputConfiguration().getFilters().stream()
                .map(
                        filter ->
                                new FilterDataImpl(
                                        filter.getFilterType().name(), filter.getIdentifier()))
                .collect(Collectors.toList());
    }

    @Nullable
    public String getFilter(String filterType) {
        FilterConfiguration filter =
                variantOutput.getFilter(FilterConfiguration.FilterType.valueOf(filterType));
        return filter != null ? filter.getIdentifier() : null;
    }

    public String getOutputFileName() {
        return variantOutput.getOutputFileName().get();
    }

    public void setOutputFileName(String outputFileName) {
        if (new File(outputFileName).isAbsolute()) {
            throw new GradleException("Absolute path are not supported when setting " +
                    "an output file name");
        }
        variantOutput.getOutputFileName().set(outputFileName);
    }
}
