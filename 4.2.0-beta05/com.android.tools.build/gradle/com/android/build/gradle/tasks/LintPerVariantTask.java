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
import com.android.build.api.component.impl.ComponentImpl;
import com.android.build.api.variant.impl.VariantImpl;
import com.android.build.gradle.internal.tasks.VariantAwareTask;
import com.android.utils.StringHelper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Internal;

public abstract class LintPerVariantTask extends LintBaseTask implements VariantAwareTask {

    private VariantInputs variantInputs;
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

    @Override
    protected void doTaskAction() {
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

        private final VariantImpl variant;
        private final List<? extends VariantImpl> allVariants;

        public CreationAction(
                @NonNull VariantImpl variant, @NonNull List<? extends VariantImpl> allVariants) {
            super(variant.getGlobalScope());
            this.variant = variant;
            this.allVariants = allVariants;
        }

        @Override
        @NonNull
        public String getName() {
            return variant.computeTaskName("lint");
        }

        @Override
        @NonNull
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void configure(@NonNull LintPerVariantTask lint) {
            super.configure(lint);

            lint.setVariantName(variant.getName());
            ConfigurableFileCollection allInputs = globalScope.getProject().files();

            lint.variantInputs = new VariantInputs(variant);
            allInputs.from(lint.variantInputs.getAllInputs());

            for (VariantImpl variant : allVariants) {
                addModelArtifactsToInputs(allInputs, variant);
            }
            lint.dependsOn(allInputs);

            lint.setDescription(
                    StringHelper.appendCapitalized(
                            "Runs lint on the ", lint.getVariantName(), " build."));
        }
    }

    public static class VitalCreationAction extends BaseCreationAction<LintPerVariantTask> {

        private final ComponentImpl component;
        private final List<? extends VariantImpl> allComponentsWithLint;

        public VitalCreationAction(
                @NonNull ComponentImpl component,
                @NonNull List<? extends VariantImpl> allComponentsWithLint) {
            super(component.getGlobalScope());
            this.component = component;
            this.allComponentsWithLint = allComponentsWithLint;
        }

        @NonNull
        @Override
        public String getName() {
            return component.computeTaskName("lintVital");
        }

        @NonNull
        @Override
        public Class<LintPerVariantTask> getType() {
            return LintPerVariantTask.class;
        }

        @Override
        public void configure(@NonNull LintPerVariantTask task) {
            super.configure(task);

            task.setVariantName(component.getName());
            ConfigurableFileCollection allInputs = globalScope.getProject().files();

            task.variantInputs = new VariantInputs(component);
            allInputs.from(task.variantInputs.getAllInputs());

            for (ComponentImpl component : allComponentsWithLint) {
                addModelArtifactsToInputs(allInputs, component);
            }
            task.dependsOn(allInputs);

            task.fatalOnly = true;
            task.setDescription(
                    "Runs lint on just the fatal issues in the "
                            + task.getVariantName()
                            + " build.");
        }
    }
}
