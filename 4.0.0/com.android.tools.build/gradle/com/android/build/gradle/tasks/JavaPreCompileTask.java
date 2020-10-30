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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.PROCESSED_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.ANNOTATION_PROCESSOR;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskProvider;

/** Tasks to perform necessary action before a JavaCompile. */
@CacheableTask
public abstract class JavaPreCompileTask extends NonIncrementalTask {

    @NonNull private RegularFileProperty processorListFile;

    private ArtifactCollection annotationProcessorConfiguration;

    private AnnotationProcessorOptions annotationProcessorOptions;

    @Inject
    public JavaPreCompileTask(ObjectFactory objectFactory) {
        processorListFile = objectFactory.fileProperty();
    }

    @VisibleForTesting
    void init(
            @NonNull ArtifactCollection annotationProcessorConfiguration,
            @NonNull AnnotationProcessorOptions annotationProcessorOptions) {
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.annotationProcessorOptions = annotationProcessorOptions;
    }

    @NonNull
    @OutputFile
    public RegularFileProperty getProcessorListFile() {
        return processorListFile;
    }

    @Classpath
    public FileCollection getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration.getArtifactFiles();
    }

    @Override
    protected void doTaskAction() {
        try (WorkerExecutorFacade workerExecutor = getWorkerFacadeWithWorkers()) {
            workerExecutor.submit(
                    PreCompileRunnable.class,
                    new PreCompileParams(
                            processorListFile.get().getAsFile(),
                            toSerializable(annotationProcessorConfiguration),
                            annotationProcessorOptions.getClassNames()));
        }
    }

    @NonNull
    private static Collection<SerializableArtifact> toSerializable(
            @NonNull ArtifactCollection artifactCollection) {
        return artifactCollection
                .getArtifacts()
                .stream()
                .map(SerializableArtifact::new)
                .collect(ImmutableList.toImmutableList());
    }

    static class PreCompileParams implements Serializable {
        @NonNull private final File processorListFile;
        @NonNull private final Collection<SerializableArtifact> annotationProcessorConfiguration;
        @NonNull private final List<String> apOptionClassNames;

        public PreCompileParams(
                @NonNull File processorListFile,
                @NonNull Collection<SerializableArtifact> annotationProcessorConfiguration,
                @NonNull List<String> apOptionClassNames) {
            this.processorListFile = processorListFile;
            this.annotationProcessorConfiguration = annotationProcessorConfiguration;
            this.apOptionClassNames = apOptionClassNames;
        }
    }

    public static class PreCompileRunnable implements Runnable {
        @NonNull private final PreCompileParams params;

        @Inject
        public PreCompileRunnable(@NonNull PreCompileParams params) {
            this.params = params;
        }

        @Override
        public void run() {
            Map<String, Boolean> annotationProcessors =
                    JavaCompileUtils.detectAnnotationProcessors(
                            params.apOptionClassNames, params.annotationProcessorConfiguration);
            JavaCompileUtils.writeAnnotationProcessorsToJsonFile(
                    annotationProcessors, params.processorListFile);
        }
    }

    public static class CreationAction extends VariantTaskCreationAction<JavaPreCompileTask> {

        public CreationAction(VariantScope scope) {
            super(scope);
        }

        @NonNull
        @Override
        public String getName() {
            return getVariantScope().getTaskName("javaPreCompile");
        }

        @NonNull
        @Override
        public Class<JavaPreCompileTask> getType() {
            return JavaPreCompileTask.class;
        }

        @Override
        public void handleProvider(
                @NonNull TaskProvider<? extends JavaPreCompileTask> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesFile(
                            InternalArtifactType.ANNOTATION_PROCESSOR_LIST.INSTANCE,
                            taskProvider,
                            JavaPreCompileTask::getProcessorListFile,
                            "annotationProcessors.json");
        }

        @Override
        public void configure(@NonNull JavaPreCompileTask task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            task.init(
                    scope.getArtifactCollection(ANNOTATION_PROCESSOR, ALL, PROCESSED_JAR),
                    scope.getVariantDslInfo()
                            .getJavaCompileOptions()
                            .getAnnotationProcessorOptions());
        }
    }
}
