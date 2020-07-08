/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.api.component.impl.ComponentPropertiesImpl;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.dexing.DexerTool;
import com.android.ide.common.workers.WorkerExecutorFacade;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

@CacheableTask
public abstract class JacocoTask extends AndroidVariantTask {
    private FileCollection jacocoAntTaskConfiguration;
    private FileCollection inputClasses;
    private JacocoTaskDelegate delegate;
    private WorkerExecutorFacade.IsolationMode isolationMode;

    @Classpath
    public FileCollection getJacocoAntTaskConfiguration() {
        return jacocoAntTaskConfiguration;
    }

    @Classpath
    public FileCollection getInputClasses() {
        return inputClasses;
    }

    @Input
    public WorkerExecutorFacade.IsolationMode getIsolationMode() {
        return isolationMode;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @OutputDirectory
    public abstract DirectoryProperty getOutputJars();

    /** Returns which Jacoco version to use. */
    @NonNull
    public static String getJacocoVersion(@NonNull ComponentPropertiesImpl componentProperties) {
        if (componentProperties.getVariantScope().getDexer() == DexerTool.DX) {
            return JacocoConfigurations.VERSION_FOR_DX;
        } else {
            return componentProperties.getGlobalScope().getExtension().getJacoco().getVersion();
        }
    }

    @TaskAction
    public void run(@NonNull IncrementalTaskInputs inputs) {
        // TODO extend NewIncrementalTask when moved to new API so that we can remove the manual
        // call to recordTaskAction
        recordTaskAction(
                () -> {
                    try (WorkerExecutorFacade workers = getWorkerFacadeWithWorkers()) {
                        delegate.run(workers, inputs);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return null;
                });
    }

    public static class CreationAction
            extends VariantTaskCreationAction<JacocoTask, ComponentPropertiesImpl> {

        public CreationAction(@NonNull ComponentPropertiesImpl componentProperties) {
            super(componentProperties);
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("jacoco");
        }

        @NonNull
        @Override
        public Class<JacocoTask> getType() {
            return JacocoTask.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<JacocoTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig
                    .getArtifacts()
                    .setInitialProvider(taskProvider, JacocoTask::getOutput)
                    .withName("out")
                    .on(InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES.INSTANCE);

            creationConfig
                    .getArtifacts()
                    .setInitialProvider(taskProvider, JacocoTask::getOutputJars)
                    .withName("out")
                    .on(InternalArtifactType.JACOCO_INSTRUMENTED_JARS.INSTANCE);
        }

        @Override
        public void configure(@NonNull JacocoTask task) {
            super.configure(task);

            task.inputClasses = creationConfig.getArtifacts().getAllClasses();
            task.jacocoAntTaskConfiguration =
                    JacocoConfigurations.getJacocoAntTaskConfiguration(
                            creationConfig.getGlobalScope().getProject(),
                            getJacocoVersion(creationConfig));
            task.isolationMode =
                    creationConfig
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.FORCE_JACOCO_OUT_OF_PROCESS)
                            ? WorkerExecutorFacade.IsolationMode.PROCESS
                            : WorkerExecutorFacade.IsolationMode.CLASSLOADER;
            task.delegate =
                    new JacocoTaskDelegate(
                            task.jacocoAntTaskConfiguration,
                            task.getOutput(),
                            task.getOutputJars(),
                            task.inputClasses,
                            task.isolationMode);
        }
    }
}
