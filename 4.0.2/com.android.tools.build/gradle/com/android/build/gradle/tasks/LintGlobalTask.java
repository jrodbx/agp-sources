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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public class LintGlobalTask extends LintBaseTask {

    private Map<String, VariantInputs> variantInputMap;
    private ConfigurableFileCollection allInputs;

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @Optional
    public FileCollection getAllInputs() {
        return allInputs;
    }

    @TaskAction
    public void lint() {
        runLint(new LintGlobalTaskDescriptor());
    }

    private class LintGlobalTaskDescriptor extends LintBaseTaskDescriptor {
        public LintGlobalTaskDescriptor() {
        }

        @Nullable
        @Override
        public String getVariantName() {
            return null;
        }

        @Nullable
        @Override
        public VariantInputs getVariantInputs(@NonNull String variantName) {
            return variantInputMap.get(variantName);
        }
    }

    public static class GlobalCreationAction extends BaseCreationAction<LintGlobalTask> {

        private final Collection<VariantScope> variantScopes;

        public GlobalCreationAction(
                @NonNull GlobalScope globalScope, @NonNull Collection<VariantScope> variantScopes) {
            super(globalScope);
            this.variantScopes = variantScopes;
        }

        @NonNull
        @Override
        public String getName() {
            return TaskManager.LINT;
        }

        @NonNull
        @Override
        public Class<LintGlobalTask> getType() {
            return LintGlobalTask.class;
        }

        @Override
        public void configure(@NonNull LintGlobalTask lintTask) {
            super.configure(lintTask);

            lintTask.setDescription("Runs lint on all variants.");

            lintTask.allInputs = getGlobalScope().getProject().files();
            lintTask.variantInputMap =
                    variantScopes
                            .stream()
                            .map(
                                    variantScope -> {
                                        VariantInputs inputs = new VariantInputs(variantScope);
                                        lintTask.allInputs.from(inputs.getAllInputs());
                                        return inputs;
                                    })
                            .collect(Collectors.toMap(VariantInputs::getName, Function.identity()));
        }
    }
}
