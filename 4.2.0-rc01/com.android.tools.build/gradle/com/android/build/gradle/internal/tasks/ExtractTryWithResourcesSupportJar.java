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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.OutputFile;

/**
 * Extracts jar containing classes necessary for try-with-resources support that will be packages in
 * the final APK.
 */
public abstract class ExtractTryWithResourcesSupportJar extends NonIncrementalTask {

    private ConfigurableFileCollection outputLocation;

    @Override
    protected void doTaskAction() throws IOException {
        try (InputStream in =
                DesugarProcessBuilder.class
                        .getClassLoader()
                        .getResourceAsStream("libthrowable_extension.jar")) {
            FileUtils.cleanOutputDir(outputLocation.getSingleFile().getParentFile());
            Files.copy(in, outputLocation.getSingleFile().toPath());
        }
    }

    @OutputFile
    public File getOutputLocation() {
        return outputLocation.getSingleFile();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<
                    ExtractTryWithResourcesSupportJar, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("extractTryWithResourcesSupportJar");
        }

        @NonNull
        @Override
        public Class<ExtractTryWithResourcesSupportJar> getType() {
            return ExtractTryWithResourcesSupportJar.class;
        }

        @Override
        public void configure(
                @NonNull ExtractTryWithResourcesSupportJar task) {
            super.configure(task);
            task.outputLocation =
                    creationConfig.getVariantScope().getTryWithResourceRuntimeSupportJar();
        }
    }
}
