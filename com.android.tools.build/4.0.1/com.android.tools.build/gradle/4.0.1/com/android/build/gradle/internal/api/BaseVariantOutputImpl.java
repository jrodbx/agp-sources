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
import com.android.build.api.variant.impl.VariantOutputImpl;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.ApkData;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.tasks.ManifestProcessorTask;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
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
    @NonNull protected final DeprecationReporter deprecationReporter;
    @NonNull protected final ApkData apkData;
    @NonNull protected final VariantOutputImpl variantOutput;

    protected BaseVariantOutputImpl(
            @NonNull TaskContainer taskContainer,
            @NonNull DeprecationReporter deprecationReporter,
            @NonNull VariantOutputImpl variantOutput) {
        this.apkData = variantOutput.getApkData();
        this.taskContainer = taskContainer;
        this.deprecationReporter = deprecationReporter;
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
    protected ApkData getApkData() {
        return apkData;
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
        deprecationReporter.reportDeprecatedApi(
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
        deprecationReporter.reportDeprecatedApi(
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
        deprecationReporter.reportDeprecatedApi(
                "variant.getAssembleProvider()",
                "variantOutput.getAssemble()",
                TASK_ACCESS_DEPRECATION_URL,
                DeprecationReporter.DeprecationTarget.TASK_ACCESS_VIA_VARIANT);
        return taskContainer.getAssembleTask().get();
    }

    @NonNull
    @Override
    public String getName() {
        return getApkData().getBaseName();
    }

    @NonNull
    @Override
    public String getBaseName() {
        return getApkData().getBaseName();
    }

    @NonNull
    @Override
    public String getDirName() {
        return getApkData().getDirName();
    }

    @NonNull
    @Override
    public String getOutputType() {
        return getApkData().getOutputType();
    }

    @NonNull
    @Override
    public Collection<String> getFilterTypes() {
        return getApkData().getFilterTypes();
    }

    @NonNull
    @Override
    public Collection<FilterData> getFilters() {
        return getApkData().getFilters();
    }

    @Nullable
    public String getFilter(String filterType) {
        return getApkData().getFilter(filterType);
    }

    public String getOutputFileName() {
        return apkData.getOutputFileName();
    }

    public void setOutputFileName(String outputFileName) {
        if (new File(outputFileName).isAbsolute()) {
            throw new GradleException("Absolute path are not supported when setting " +
                    "an output file name");
        }
        apkData.setOutputFileName(outputFileName);
    }
}
