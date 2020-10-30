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

import static com.android.SdkConstants.FN_LINT_JAR;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * Task that takes the configuration result, and check that it's correct.
 *
 * <p>Then copies it in the build folder to (re)publish it. This is not super efficient but because
 * publishing is done at config time when we don't know yet what lint.jar file we're going to
 * publish, we have to do this.
 */
public abstract class PrepareLintJar extends DefaultTask {
    public static final String NAME = "prepareLintJar";

    private FileCollection lintChecks;

    @Classpath
    public FileCollection getLintChecks() {
        return lintChecks;
    }

    @OutputFile
    public abstract RegularFileProperty getOutputLintJar();

    @TaskAction
    public void prepare() throws IOException {
        // there could be more than one files if the dependency is on a sub-projects that
        // publishes its compile dependencies. Rather than query getSingleFile and fail with
        // a weird message, do a manual check
        Set<File> files = lintChecks.getFiles();
        if (files.size() > 1) {
            throw new RuntimeException(
                    "Found more than one jar in the '"
                            + VariantDependencies.CONFIG_NAME_LINTCHECKS
                            + "' configuration. Only one file is supported. If using a separate Gradle project, make sure compilation dependencies are using compileOnly");
        }

        if (!files.isEmpty()) {
            File outputDir = getOutputLintJar().get().getAsFile();
            FileUtils.deleteRecursivelyIfExists(outputDir);
            FileUtils.mkdirs(outputDir.getParentFile());
            Files.copy(Iterables.getOnlyElement(files), outputDir);
        }
    }

    public static class CreationAction extends TaskCreationAction<PrepareLintJar> {

        @NonNull private final GlobalScope scope;

        public CreationAction(@NonNull GlobalScope scope) {
            this.scope = scope;
        }

        @NonNull
        @Override
        public String getName() {
            return NAME;
        }

        @NonNull
        @Override
        public Class<PrepareLintJar> getType() {
            return PrepareLintJar.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends PrepareLintJar> taskProvider) {
            super.handleProvider(taskProvider);
            scope.getArtifacts()
                    .producesFile(
                            InternalArtifactType.LINT_JAR.INSTANCE,
                            taskProvider,
                            PrepareLintJar::getOutputLintJar,
                            FN_LINT_JAR);
        }

        @Override
        public void configure(@NonNull PrepareLintJar task) {
            task.lintChecks = scope.getLocalCustomLintChecks();
        }
    }
}
