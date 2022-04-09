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
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.DisableCachingByDefault;

/**
 * Task that writes the FeatureSplitDeclaration file and publish it for other modules to consume.
 */
@DisableCachingByDefault
public abstract class FeatureSplitDeclarationWriterTask extends NonIncrementalTask {

    @VisibleForTesting String uniqueIdentifier;

    @Input
    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    @Input
    public abstract Property<String> getNamespace();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Override
    protected void doTaskAction() throws IOException {
        FeatureSplitDeclaration declaration =
                new FeatureSplitDeclaration(uniqueIdentifier, getNamespace().get());
        declaration.save(getOutputDirectory().get().getAsFile());
    }

    public static class CreationAction
            extends VariantTaskCreationAction<
                    FeatureSplitDeclarationWriterTask, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("feature", "Writer");
        }

        @NonNull
        @Override
        public Class<FeatureSplitDeclarationWriterTask> getType() {
            return FeatureSplitDeclarationWriterTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<FeatureSplitDeclarationWriterTask> taskProvider) {
            super.handleProvider(taskProvider);

            creationConfig
                    .getArtifacts()
                    .setInitialProvider(
                            taskProvider, FeatureSplitDeclarationWriterTask::getOutputDirectory)
                    .withName("out")
                    .on(InternalArtifactType.METADATA_FEATURE_DECLARATION.INSTANCE);
        }

        @Override
        public void configure(
                @NonNull FeatureSplitDeclarationWriterTask task) {
            super.configure(task);

            task.uniqueIdentifier = task.getProject().getPath();
            task.getNamespace().set(creationConfig.getNamespace());
            task.getNamespace().disallowChanges();
        }
    }
}
