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
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.process.GradleProcessExecutor;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.internal.compiler.DirectoryWalker;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.repository.Revision;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.process.ExecOperations;

/**
 * Task to compile Shaders.
 *
 * <p>TODO(b/124424292)
 *
 * <p>We can not use gradle worker in this task as we use {@link GradleProcessExecutor} for
 * compiling shader files, which should not be serialized.
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

    private Provider<Revision> buildToolInfoRevisionProvider;

    @Input
    public String getBuildToolsVersion() {
        return buildToolInfoRevisionProvider.get().toString();
    }

    private Provider<File> ndkLocation;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDir();

    @NonNull
    private List<String> defaultArgs = ImmutableList.of();
    private Map<String, List<String>> scopedArgs = ImmutableMap.of();

    @NonNull private final ExecOperations execOperations;

    @Inject
    public ShaderCompile(@NonNull ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    @InputFiles
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

    @Override
    protected void doTaskAction() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir().get().getAsFile();
        FileUtils.cleanOutputDir(destinationDir);

        try (WorkerExecutorFacade workers = getWorkerFacadeWithThreads(false)) {
            compileAllShaderFiles(
                    getSourceDir().get().getAsFile(),
                    destinationDir,
                    defaultArgs,
                    scopedArgs,
                    () -> ndkLocation.get(),
                    new LoggedProcessOutputHandler(new LoggerWrapper(getLogger())),
                    workers);
        }
    }

    /**
     * Compiles all the shader files found in the given source folders.
     *
     * @param sourceFolder the source folder with the merged shaders
     * @param outputDir the output dir in which to generate the output
     * @throws IOException failed
     */
    private void compileAllShaderFiles(
            @NonNull File sourceFolder,
            @NonNull File outputDir,
            @NonNull List<String> defaultArgs,
            @NonNull Map<String, List<String>> scopedArgs,
            @Nullable Supplier<File> ndkLocation,
            @NonNull ProcessOutputHandler processOutputHandler,
            @NonNull WorkerExecutorFacade workers)
            throws IOException {
        checkNotNull(sourceFolder, "sourceFolder cannot be null.");
        checkNotNull(outputDir, "outputDir cannot be null.");

        Supplier<ShaderProcessor> processor =
                () ->
                        new ShaderProcessor(
                                ndkLocation,
                                sourceFolder,
                                outputDir,
                                defaultArgs,
                                scopedArgs,
                                new GradleProcessExecutor(execOperations::exec),
                                processOutputHandler,
                                workers);

        DirectoryWalker.builder()
                .root(sourceFolder.toPath())
                .extensions(
                        ShaderProcessor.EXT_VERT,
                        ShaderProcessor.EXT_TESC,
                        ShaderProcessor.EXT_TESE,
                        ShaderProcessor.EXT_GEOM,
                        ShaderProcessor.EXT_FRAG,
                        ShaderProcessor.EXT_COMP)
                .action(processor)
                .build()
                .walk();
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

    public static class CreationAction extends VariantTaskCreationAction<ShaderCompile> {

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("compile", "Shaders");
        }

        @Override
        @NonNull
        public Class<ShaderCompile> getType() {
            return ShaderCompile.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<? extends ShaderCompile> taskProvider) {
            super.handleProvider(taskProvider);
            getVariantScope()
                    .getArtifacts()
                    .producesDir(
                            InternalArtifactType.SHADER_ASSETS.INSTANCE,
                            taskProvider,
                            ShaderCompile::getOutputDir,
                            "out");
        }

        @Override
        public void configure(@NonNull ShaderCompile task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final VariantDslInfo variantDslInfo = scope.getVariantDslInfo();

            task.ndkLocation = scope.getGlobalScope().getSdkComponents().getNdkFolderProvider();
            scope.getArtifacts()
                    .setTaskInputToFinalProduct(MERGED_SHADERS.INSTANCE, task.getSourceDir());
            task.setDefaultArgs(variantDslInfo.getDefaultGlslcArgs());
            task.setScopedArgs(variantDslInfo.getScopedGlslcArgs());

            task.buildToolInfoRevisionProvider =
                    scope.getGlobalScope().getSdkComponents().getBuildToolsRevisionProvider();
        }
    }
}
