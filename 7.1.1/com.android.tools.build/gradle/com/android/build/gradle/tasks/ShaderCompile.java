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

import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_SHADERS;
import static com.android.build.gradle.internal.utils.HasConfigurableValuesKt.setDisallowChanges;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.NdkHandlerInput;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.SdkComponentsKt;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.process.ExecOperations;

/**
 * Task to compile Shaders.
 */
@CacheableTask
public abstract class ShaderCompile extends NonIncrementalTask {

    private static final PatternSet PATTERN_SET = new PatternSet()
            .include("**/*." + ShaderProcessor.EXT_VERT)
            .include("**/*." + ShaderProcessor.EXT_TESC)
            .include("**/*." + ShaderProcessor.EXT_TESE)
            .include("**/*." + ShaderProcessor.EXT_GEOM)
            .include("**/*." + ShaderProcessor.EXT_FRAG)
            .include("**/*." + ShaderProcessor.EXT_COMP);

    @Input
    public abstract Property<Revision> getBuildToolInfoRevisionProvider();

    @Internal
    public abstract Property<SdkComponentsBuildService> getSdkBuildService();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @Nested
    public abstract NdkHandlerInput getNdkHandlerInput();

    @NonNull
    private List<String> defaultArgs = ImmutableList.of();
    private Map<String, List<String>> scopedArgs = ImmutableMap.of();

    private final int maxWorkerCount =
            getProject().getGradle().getStartParameter().getMaxWorkerCount();

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public FileTree getSourceFiles() {
        File sourceDirFile = getSourceDir().get().getAsFile();
        FileTree src = null;
        if (sourceDirFile.isDirectory()) {
            src = getProject().files(sourceDirFile).getAsFileTree().matching(PATTERN_SET);
        }
        return src == null ? getProject().files().getAsFileTree() : src;
    }

    /**
     * Compiles all the shader files found in the given source folders by collecting all files to
     * process first, and then launching worker actions.
     */
    @Override
    protected void doTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir().get().getAsFile();
        FileUtils.cleanOutputDir(destinationDir);

        List<ProcessingRequest> processingRequests = new ArrayList<>();
        DirectoryWalker.FileAction collector =
                (root, file) ->
                        processingRequests.add(new ProcessingRequest(root.toFile(), file.toFile()));

        DirectoryWalker.builder()
                .root(getSourceDir().getAsFile().get().toPath())
                .extensions(
                        ShaderProcessor.EXT_VERT,
                        ShaderProcessor.EXT_TESC,
                        ShaderProcessor.EXT_TESE,
                        ShaderProcessor.EXT_GEOM,
                        ShaderProcessor.EXT_FRAG,
                        ShaderProcessor.EXT_COMP)
                .action(collector)
                .build()
                .walk();

        if (!processingRequests.isEmpty()) {
            File glslcLocation =
                    ShaderProcessor.getGlslcLocation(
                            getSdkBuildService()
                                    .get()
                                    .versionedNdkHandler(getNdkHandlerInput())
                                    .getNdkPlatform()
                                    .getOrThrow()
                                    .getNdkDirectory());

            HashMap<Integer, List<ProcessingRequest>> buckets = new HashMap<>();
            int ord = 0;
            for (ProcessingRequest processingRequest : processingRequests) {
                int bucketId = (ord++) % maxWorkerCount;
                List<ProcessingRequest> bucket = buckets.getOrDefault(bucketId, new ArrayList<>());
                bucket.add(processingRequest);
                buckets.put(bucketId, bucket);
            }

            for (List<ProcessingRequest> bucket : buckets.values()) {
                if (!bucket.isEmpty()) {
                    getWorkerExecutor()
                            .noIsolation()
                            .submit(
                                    WorkAction.class,
                                    params -> {
                                        params.initializeFromAndroidVariantTask(this);
                                        params.getSourceFolder().set(getSourceDir());
                                        params.getOutputFolder().set(getOutputDir().dir("shaders"));
                                        params.getDefaultArgs().set(defaultArgs);
                                        params.getScopedArgs().set(scopedArgs);
                                        params.getGlslcLocation().set(glslcLocation);
                                        params.getRequests().set(bucket);
                                    });
                }
            }
        }
    }

    public static class ProcessingRequest implements Serializable {
        public final File root;
        public final File file;

        public ProcessingRequest(File root, File file) {
            this.root = root;
            this.file = file;
        }
    }

    public abstract static class WorkAction extends ProfileAwareWorkAction<WorkAction.Params> {
        public abstract static class Params extends ProfileAwareWorkAction.Parameters {
            public abstract DirectoryProperty getSourceFolder();

            public abstract DirectoryProperty getOutputFolder();

            public abstract Property<File> getGlslcLocation();

            public abstract ListProperty<String> getDefaultArgs();

            public abstract MapProperty<String, List<String>> getScopedArgs();

            public abstract ListProperty<ProcessingRequest> getRequests();
        }

        @Inject
        public ExecOperations getExecOperations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void run() {
            for (ProcessingRequest processingRequest : getParameters().getRequests().get()) {
                new ShaderProcessor.ShaderProcessorRunnable(
                                new ShaderProcessor.ShaderProcessorParams(
                                        getParameters().getSourceFolder().getAsFile().get(),
                                        getParameters().getOutputFolder().getAsFile().get(),
                                        getParameters().getDefaultArgs().get(),
                                        getParameters().getScopedArgs().get(),
                                        new GradleProcessExecutor(getExecOperations()::exec),
                                        new LoggedProcessOutputHandler(
                                                LoggerWrapper.getLogger(WorkAction.class)),
                                        processingRequest.root.toPath(),
                                        processingRequest.file.toPath(),
                                        getParameters().getGlslcLocation().get()))
                        .run();
            }
        }
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @NonNull
    @Input
    public List<String> getDefaultArgs() {
        return defaultArgs;
    }

    public void setDefaultArgs(@NonNull List<String> defaultArgs) {
        this.defaultArgs = ImmutableList.copyOf(defaultArgs);
    }

    @NonNull
    @Input
    public Map<String, List<String>> getScopedArgs() {
        return scopedArgs;
    }

    public void setScopedArgs(@NonNull Map<String, List<String>> scopedArgs) {
        this.scopedArgs = ImmutableMap.copyOf(scopedArgs);
    }

    public static class CreationAction
            extends VariantTaskCreationAction<ShaderCompile, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
        }

        @Override
        @NonNull
        public String getName() {
            return computeTaskName("compile", "Shaders");
        }

        @Override
        @NonNull
        public Class<ShaderCompile> getType() {
            return ShaderCompile.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<ShaderCompile> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig
                    .getArtifacts()
                    .setInitialProvider(taskProvider, ShaderCompile::getOutputDir)
                    .withName("out")
                    .on(InternalArtifactType.SHADER_ASSETS.INSTANCE);
        }

        @Override
        public void configure(@NonNull ShaderCompile task) {
            super.configure(task);
            final VariantDslInfo variantDslInfo = creationConfig.getVariantDslInfo();

            setDisallowChanges(
                    task.getSdkBuildService(),
                    BuildServicesKt.getBuildService(
                            creationConfig.getServices().getBuildServiceRegistry(),
                            SdkComponentsBuildService.class));

            setDisallowChanges(
                    task.getBuildToolInfoRevisionProvider(),
                    creationConfig.getGlobalScope().getExtension().getBuildToolsRevision());

            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(MERGED_SHADERS.INSTANCE, task.getSourceDir());
            task.setDefaultArgs(variantDslInfo.getDefaultGlslcArgs());
            task.setScopedArgs(variantDslInfo.getScopedGlslcArgs());
            SdkComponentsKt.initialize(task.getNdkHandlerInput(), creationConfig);
        }
    }
}
