/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.StringHelper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class LintPerVariantTask extends LintBaseTask implements VariantAwareTask {

    private VariantInputs variantInputs;
    private ConfigurableFileCollection allInputs;
    private boolean fatalOnly;

    private String variantName;

    @Internal
    @NonNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @Optional
    public FileCollection getAllInputs() {
        return allInputs;
    }

    @TaskAction
    public void lint() {
        runLint(new LintPerVariantTaskDescriptor());
    }

    private class LintPerVariantTaskDescriptor extends LintBaseTaskDescriptor {
        @Nullable
        @Override
        public String getVariantName() {
            return LintPerVariantTask.this.getVariantName();
        }

        @Nullable
        @Override
        public VariantInputs getVariantInputs(@NonNull String variantName) {
            assert variantName.equals(getVariantName());
            return variantInputs;
        }

        @NonNull
        @Override
        public Set<String> getVariantNames() {
            return Collections.singleton(variantName);
        }

        @Override
        public boolean isFatalOnly() {
            return fatalOnly;
        }
    }

    public static class CreationAction extends BaseCreationAction<LintPerVariantTask> {

        private final VariantPropertiesImpl variantProperties;
        private final List<? extends VariantPropertiesImpl> allVariants;

        public CreationAction(
                @NonNull VariantPropertiesImpl variantProperties,
                @NonNull List<? extends VariantPropertiesImpl> allVariants) {
            super(variantProperties.getGlobalScope());
            this.variantProperties = variantProperties;
            this.allVariants = allVariants;
        }

        @Override
        @NonNull
        public String getName() {
            return variantProperties.computeTaskName("lint");
        }

        @Override
        @NonNull
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void configure(@NonNull LintPerVariantTask lint) {
            super.configure(lint);

            lint.setVariantName(variantProperties.getName());
            lint.allInputs = globalScope.getProject().files();

            lint.variantInputs = new VariantInputs(variantProperties);
            lint.allInputs.from(lint.variantInputs.getAllInputs());

            for (VariantPropertiesImpl variant : allVariants) {
                addModelArtifactsToInputs(lint.allInputs, variant);
            }

            lint.setDescription(
                    StringHelper.appendCapitalized(
                            "Runs lint on the ", lint.getVariantName(), " build."));
            lint.getEnableGradleWorkers()
                    .set(
                            variantProperties
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.ENABLE_GRADLE_WORKERS));
        }
    }

    public static class VitalCreationAction extends BaseCreationAction<LintPerVariantTask> {

        private final ComponentPropertiesImpl componentProperties;
        private final List<? extends VariantPropertiesImpl> allComponentsWithLint;

        public VitalCreationAction(
                @NonNull ComponentPropertiesImpl componentProperties,
                @NonNull List<? extends VariantPropertiesImpl> allComponentsWithLint) {
            super(componentProperties.getGlobalScope());
            this.componentProperties = componentProperties;
            this.allComponentsWithLint = allComponentsWithLint;
        }

        @NonNull
        @Override
        public String getName() {
            return componentProperties.computeTaskName("lintVital");
        }

        @NonNull
        @Override
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void configure(@NonNull LintPerVariantTask task) {
            super.configure(task);

            task.setVariantName(componentProperties.getName());
            task.allInputs = globalScope.getProject().files();

            task.variantInputs = new VariantInputs(componentProperties);
            task.allInputs.from(task.variantInputs.getAllInputs());

            for (ComponentPropertiesImpl component : allComponentsWithLint) {
                addModelArtifactsToInputs(task.allInputs, component);
            }

            task.fatalOnly = true;
            task.setDescription(
                    "Runs lint on just the fatal issues in the "
                            + task.getVariantName()
                            + " build.");
            task.getEnableGradleWorkers()
                    .set(
                            componentProperties
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.ENABLE_GRADLE_WORKERS));
        }
    }
}
