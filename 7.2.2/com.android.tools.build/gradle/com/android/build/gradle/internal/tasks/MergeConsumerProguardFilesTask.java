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

package com.android.build.gradle.internal.tasks;

import static com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import com.android.builder.errors.EvalIssueException;
import java.io.IOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;

/**
 * Configuration action for a merge-Proguard-files task.
 *
 * <p>Caching disabled by default for this task in line with behavior of parent class.
 *
 * @see MergeFileTask
 */
@DisableCachingByDefault
public abstract class MergeConsumerProguardFilesTask extends MergeFileTask {

    private boolean isDynamicFeature;
    private boolean isBaseModule;

    @Input
    public boolean getIsDynamicFeature() {
        return isDynamicFeature;
    }

    @Input
    public boolean getIsBaseModule() {
        return isBaseModule;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getConsumerProguardFiles();

    @Internal("only for task execution")
    public abstract DirectoryProperty getBuildDirectory();

    @Override
    public void doTaskAction() throws IOException {
        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseModule) {
            ExportConsumerProguardFilesTask.checkProguardFiles(
                    getBuildDirectory(),
                    isDynamicFeature,
                    getConsumerProguardFiles().getFiles(),
                    errorMessage -> {
                        throw new EvalIssueException(errorMessage);
                    });
        }
        super.doTaskAction();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<
                    MergeConsumerProguardFilesTask, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("merge", "ConsumerProguardFiles");
        }

        @NonNull
        @Override
        public Class<MergeConsumerProguardFilesTask> getType() {
            return MergeConsumerProguardFilesTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<MergeConsumerProguardFilesTask> taskProvider) {
            super.handleProvider(taskProvider);

            creationConfig
                    .getArtifacts()
                    .setInitialProvider(taskProvider, MergeConsumerProguardFilesTask::getOutputFile)
                    .withName(SdkConstants.FN_PROGUARD_TXT)
                    .on(InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE.INSTANCE);
        }

        @Override
        public void configure(
                @NonNull MergeConsumerProguardFilesTask task) {
            super.configure(task);
            task.isBaseModule = creationConfig.getVariantType().isBaseModule();
            task.isDynamicFeature = creationConfig.getVariantType().isDynamicFeature();

            task.getConsumerProguardFiles()
                    .from(creationConfig.getVariantScope().getConsumerProguardFilesForFeatures());

            ConfigurableFileCollection inputFiles =
                    creationConfig
                            .getServices()
                            .fileCollection(
                                    task.getConsumerProguardFiles(),
                                    creationConfig
                                            .getArtifacts()
                                            .get(GENERATED_PROGUARD_FILE.INSTANCE));
            task.getInputFiles().from(inputFiles);
            task.getInputFiles().disallowChanges();
            HasConfigurableValuesKt.setDisallowChanges(
                    task.getBuildDirectory(), task.getProject().getLayout().getBuildDirectory());
        }
    }
}
