/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.featuresplit;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task that writes the FeatureSplitDeclaration file and publish it for other modules to consume.
 */
public abstract class FeatureSplitDeclarationWriterTask extends NonIncrementalTask {

    @VisibleForTesting String uniqueIdentifier;

    @Input
    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    @Input
    public abstract Property<String> getApplicationId();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Override
    protected void doTaskAction() throws IOException {
        FeatureSplitDeclaration declaration =
                new FeatureSplitDeclaration(uniqueIdentifier, getApplicationId().get());
        declaration.save(getOutputDirectory().get().getAsFile());
    }

    public static class CreationAction
            extends VariantTaskCreationAction<FeatureSplitDeclarationWriterTask> {

        public CreationAction(@NonNull VariantScope variantScope) {
            super(variantScope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("feature", "Writer");
        }

        @NonNull
        @Override
        public Class<FeatureSplitDeclarationWriterTask> getType() {
            return FeatureSplitDeclarationWriterTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends FeatureSplitDeclarationWriterTask> taskProvider) {
            super.handleProvider(taskProvider);

            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.METADATA_FEATURE_DECLARATION.INSTANCE,
                            taskProvider,
                            FeatureSplitDeclarationWriterTask::getOutputDirectory,
                            "out");
        }

        @Override
        public void configure(@NonNull FeatureSplitDeclarationWriterTask task) {
            super.configure(task);

            final VariantScope variantScope = getVariantScope();
            final Project project = variantScope.getGlobalScope().getProject();
            task.uniqueIdentifier = project.getPath();
            task.getApplicationId()
                    .set(
                            project.provider(
                                    variantScope.getVariantDslInfo()::getOriginalApplicationId));
            task.getApplicationId().disallowChanges();
        }
    }
}
