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

import com.android.build.gradle.ProguardFiles;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

/**
 * Task to extract the default ProGuard rules from Java resources into files in the build directory.
 */
public class ExtractProguardFiles extends DefaultTask {

    private final ImmutableList<File> generatedFiles;
    private final ProjectLayout projectLayout;

    @Inject
    public ExtractProguardFiles(ProjectLayout projectLayout) {
        this.projectLayout = projectLayout;
        ImmutableList.Builder<File> outputs = ImmutableList.builder();

        for (String name : ProguardFiles.KNOWN_FILE_NAMES) {
            outputs.add(ProguardFiles.getDefaultProguardFile(name, projectLayout));
        }

        this.generatedFiles = outputs.build();
    }

    @OutputFiles
    public List<File> getGeneratedFiles() {
        return generatedFiles;
    }

    @TaskAction
    public void run() throws IOException {
        for (String name : ProguardFiles.KNOWN_FILE_NAMES) {
            File defaultProguardFile = ProguardFiles.getDefaultProguardFile(name, projectLayout);
            if (!defaultProguardFile.isFile()) {
                ProguardFiles.createProguardFile(name, defaultProguardFile);
            }
        }
    }
}
