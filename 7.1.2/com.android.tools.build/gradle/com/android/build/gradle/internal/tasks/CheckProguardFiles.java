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
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.ProguardFiles.ProguardFile;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault
public abstract class CheckProguardFiles extends NonIncrementalTask {

    @Override
    protected void doTaskAction() {
        // Below we assume new postprocessing DSL is used, since otherwise TaskManager does not
        // create this task.

        Map<File, ProguardFile> oldFiles = new HashMap<>();
        oldFiles.put(
                ProguardFiles.getDefaultProguardFile(
                                ProguardFile.OPTIMIZE.fileName, getBuildDirectory())
                        .getAbsoluteFile(),
                ProguardFile.OPTIMIZE);
        oldFiles.put(
                ProguardFiles.getDefaultProguardFile(
                                ProguardFile.DONT_OPTIMIZE.fileName, getBuildDirectory())
                        .getAbsoluteFile(),
                ProguardFile.DONT_OPTIMIZE);

        for (RegularFile regularFile : getProguardFiles().get()) {
            File file = regularFile.getAsFile();
            if (oldFiles.containsKey(file.getAbsoluteFile())) {
                String name = oldFiles.get(file.getAbsoluteFile()).fileName;
                throw new InvalidUserDataException(
                        name
                                + " should not be used together with the new postprocessing DSL. "
                                + "The new DSL includes sensible settings by default, you can override this "
                                + "using `postprocessing { proguardFiles = []}`");
            }
        }
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ListProperty<RegularFile> getProguardFiles();

    // the extracted proguard files are probably also part of the proguardFiles but we need to set
    // the dependency explicitly so Gradle can track it properly.
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract DirectoryProperty getExtractedProguardFile();

    @Internal("only for task execution")
    public abstract DirectoryProperty getBuildDirectory();

    public static class CreationAction
            extends VariantTaskCreationAction<CheckProguardFiles, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("check", "ProguardFiles");
        }

        @NonNull
        @Override
        public Class<CheckProguardFiles> getType() {
            return CheckProguardFiles.class;
        }

        @Override
        public void configure(@NonNull CheckProguardFiles task) {
            super.configure(task);

            task.getProguardFiles().set(creationConfig.getProguardFiles());
            task.getExtractedProguardFile()
                    .set(
                            creationConfig
                                    .getGlobalScope()
                                    .getGlobalArtifacts()
                                    .get(InternalArtifactType.DEFAULT_PROGUARD_FILES.INSTANCE));
            task.getProguardFiles().disallowChanges();
            HasConfigurableValuesKt.setDisallowChanges(
                    task.getBuildDirectory(), task.getProject().getLayout().getBuildDirectory());
        }
    }
}
