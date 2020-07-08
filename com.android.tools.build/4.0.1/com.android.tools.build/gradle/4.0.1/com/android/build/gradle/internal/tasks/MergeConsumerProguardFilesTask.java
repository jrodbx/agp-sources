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
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.errors.EvalIssueException;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

/** Configuration action for a merge-Proguard-files task. */
@CacheableTask
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

    @Override
    public void doTaskAction() throws IOException {
        final Project project = getProject();

        // We check for default files unless it's a base feature, which can include default files.
        if (!isBaseModule) {
            ExportConsumerProguardFilesTask.checkProguardFiles(
                    project,
                    isDynamicFeature,
                    getConsumerProguardFiles().getFiles(),
                    errorMessage -> {
                        throw new EvalIssueException(errorMessage);
                    });
        }
        super.doTaskAction();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<MergeConsumerProguardFilesTask> {

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("merge", "ConsumerProguardFiles");
        }

        @NonNull
        @Override
        public Class<MergeConsumerProguardFilesTask> getType() {
            return MergeConsumerProguardFilesTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends MergeConsumerProguardFilesTask> taskProvider) {
            super.handleProvider(taskProvider);

            getVariantScope()
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE.INSTANCE,
                            taskProvider,
                            MergeConsumerProguardFilesTask::getOutputFile,
                            SdkConstants.FN_PROGUARD_TXT);
        }

        @Override
        public void configure(@NonNull MergeConsumerProguardFilesTask task) {
            super.configure(task);
            GlobalScope globalScope = getVariantScope().getGlobalScope();
            Project project = globalScope.getProject();

            task.isBaseModule = getVariantScope().getType().isBaseModule();
            task.isDynamicFeature = getVariantScope().getType().isDynamicFeature();

            task.getConsumerProguardFiles()
                    .from(getVariantScope().getConsumerProguardFilesForFeatures());

            ConfigurableFileCollection inputFiles =
                    project.files(
                            task.getConsumerProguardFiles(),
                            getVariantScope()
                                    .getArtifacts()
                                    .getFinalProduct(GENERATED_PROGUARD_FILE.INSTANCE));
            task.setInputFiles(inputFiles);
        }
    }
}
