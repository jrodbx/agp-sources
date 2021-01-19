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

import com.android.annotations.NonNull;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Task to extract the default ProGuard rules from Java resources into files in the build directory.
 */
abstract public class ExtractProguardFiles extends DefaultTask {

    private final ImmutableList<File> generatedFiles;

    private final ProjectLayout projectLayout;

    @Input
    public abstract Property<Boolean> getEnableKeepRClass();

    @Inject
    public ExtractProguardFiles(ProjectLayout projectLayout) {
        this.projectLayout = projectLayout;
        ImmutableList.Builder<File> outputs = ImmutableList.builder();

        for (String name : ProguardFiles.KNOWN_FILE_NAMES) {
            outputs.add(
                    ProguardFiles.getDefaultProguardFile(name, projectLayout.getBuildDirectory()));
        }

        this.generatedFiles = outputs.build();
    }

    @OutputFiles
    public List<File> getGeneratedFiles() {
        return generatedFiles;
    }

    @TaskAction
    protected void run() throws Exception {
        for (String name : ProguardFiles.KNOWN_FILE_NAMES) {
            File defaultProguardFile =
                    ProguardFiles.getDefaultProguardFile(name, projectLayout.getBuildDirectory());
            if (!defaultProguardFile.isFile()) {
                ProguardFiles.createProguardFile(
                        name, defaultProguardFile, getEnableKeepRClass().get());
            }
        }
    }

    public static class CreationAction extends TaskCreationAction<ExtractProguardFiles> {

        private final GlobalScope globalScope;

        public CreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() { return "extractProguardFiles"; }

        @NonNull
        @Override
        public Class<ExtractProguardFiles> getType() { return ExtractProguardFiles.class; }

        @Override
        public void configure(@NonNull ExtractProguardFiles task) {
            task.getEnableKeepRClass().set(
                    !globalScope.getProjectOptions()
                            .get(BooleanOption.ENABLE_R_TXT_RESOURCE_SHRINKING)
            );
        }
    }
}
