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
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.TaskCategory;
import com.android.utils.FileUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.work.DisableCachingByDefault;

/**
 * This is a stub task.
 *
 * <p>TODO - should compile src/lint/java from src/lint/java and jar it into build/lint/lint.jar
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT, secondaryTaskCategories = {TaskCategory.COMPILATION})
public abstract class LintCompile extends NonIncrementalGlobalTask {

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Override
    protected void doTaskAction() {
        // TODO
        FileUtils.mkdirs(getOutputDirectory().get().getAsFile());
    }

    public static class CreationAction extends GlobalTaskCreationAction<LintCompile> {

        public CreationAction(@NonNull GlobalTaskCreationConfig creationConfig) {
            super(creationConfig);
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
            super.configure(task);

            task.getOutputDirectory()
                    .set(
                            creationConfig
                                    .getServices()
                                    .getProjectInfo()
                                    .intermediatesDirectory("lint"));
        }
    }
}
