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
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.utils.FileUtils;
import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * This is a stub task.
 *
 * <p>TODO - should compile src/lint/java from src/lint/java and jar it into build/lint/lint.jar
 */
public class LintCompile extends DefaultTask {

    private File outputDirectory;

    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @TaskAction
    public void compile() {
        // TODO
        FileUtils.mkdirs(getOutputDirectory());
    }


    public static class CreationAction extends TaskCreationAction<LintCompile> {

        private final GlobalScope globalScope;

        public CreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        @Override
        public String getName() {
            return "compileLint";
        }

        @NonNull
        @Override
        public Class<LintCompile> getType() {
            return LintCompile.class;
        }

        @Override
        public void configure(@NonNull LintCompile task) {
            task.setOutputDirectory(new File(globalScope.getIntermediatesDir(), "lint"));
        }
    }
}
