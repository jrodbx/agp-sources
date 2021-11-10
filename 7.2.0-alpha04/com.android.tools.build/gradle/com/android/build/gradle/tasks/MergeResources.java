/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.build.gradle.internal.TaskManager.MergeType.MERGE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_MERGE;
import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_NOT_COMPILED_RES;
import static com.android.ide.common.resources.AndroidAaptIgnoreKt.ANDROID_AAPT_IGNORE;
import static com.google.common.base.Preconditions.checkState;

import android.databinding.tool.LayoutXmlProcessor;
import android.databinding.tool.util.RelativizableFile;
import android.databinding.tool.writer.JavaFileWriter;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.gradle.internal.DependencyResourcesComputer;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.aapt.WorkerExecutorResourceCompilationService;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.databinding.MergingFileLookup;
import com.android.build.gradle.internal.errors.MessageReceiverImpl;
import com.android.build.gradle.internal.res.namespaced.NamespaceRemover;
import com.android.build.gradle.internal.scope.BuildFeatureValues;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.Aapt2Input;
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.Blocks;
import com.android.build.gradle.internal.tasks.NewIncrementalTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.builder.png.VectorDrawableRenderer;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.resources.CopyToOutputDirectoryResourceCompilationService;
import com.android.ide.common.resources.FileValidity;
import com.android.ide.common.resources.GeneratedResourceSet;
import com.android.ide.common.resources.MergedResourceWriter;
import com.android.ide.common.resources.MergedResourceWriterRequest;
import com.android.ide.common.resources.MergingException;
import com.android.ide.common.resources.NoOpResourcePreprocessor;
import com.android.ide.common.resources.RelativeResourceUtils;
import com.android.ide.common.resources.ResourceCompilationService;
import com.android.ide.common.resources.ResourceMerger;
import com.android.ide.common.resources.ResourcePreprocessor;
import com.android.ide.common.resources.ResourceSet;
import com.android.ide.common.resources.SingleFileProcessor;
import com.android.ide.common.vectordrawable.ResourcesNotSupportedException;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBException;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.FileChange;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

@CacheableTask
public abstract class MergeResources extends NewIncrementalTask {
    // ----- PUBLIC TASK API -----

    /** Directory to write the merged resources to */
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getGeneratedPngsOutputDir();

    // ----- PRIVATE TASK API -----

    /**
     * Optional file to write any publicly imported resource types and names to
     */
    private boolean processResources;

    private boolean crunchPng;

    private List<ResourceSet> processedInputs;

    private final FileValidity<ResourceSet> fileValidity = new FileValidity<>();

    private boolean disableVectorDrawables;

    private boolean vectorSupportLibraryIsUsed;

    private Collection<String> generatedDensities;

    @Nullable private File mergedNotCompiledResourcesOutputDirectory;

    private boolean precompileDependenciesResources;

    private ImmutableSet<Flag> flags;

    DependencyResourcesComputer resourcesComputer;

    @Input
    public abstract Property<Boolean> getDataBindingEnabled();

    @Input
    public abstract Property<Boolean> getViewBindingEnabled();

    @Input
    @Optional
    public abstract Property<String> getNamespace();

    @Input
    @Optional
    public abstract Property<Boolean> getUseAndroidX();

    @Nested
    public abstract SourceSetInputs getSourceSetInputs();

    /**
     * Set of absolute paths to resource directories that are located outside of the root project
     * directory when data binding / view binding is enabled.
     *
     * <p>These absolute paths appear in the layout info files generated by data binding, so we have
     * to mark them as @Input to ensure build correctness. (If this set is not empty, this task is
     * still cacheable but not relocatable.)
     *
     * <p>If data binding / view binding is not enabled, this set is empty as it won't be used.
     */
    @Input
    public abstract SetProperty<String> getResourceDirsOutsideRootProjectDir();

    @Input
    public abstract Property<Boolean> getRelativePathsEnabled();

    @Input
    public abstract Property<Boolean> getPseudoLocalesEnabled();

    @Internal
    public abstract Property<Aapt2ThreadPoolBuildService> getAapt2ThreadPoolBuildService();

    @Nested
    public abstract Aapt2Input getAapt2();

    // TODO(141301405): when we compile resources AAPT2 stores the absolute path of the raw
    // resource in the proto (.flat) file, so we need to mark those inputs with absolute
    // path sensitivity (e.g. big merge in app). Otherwise just using the relative path
    // sensitivity is enough (e.g. merges in libraries).
    @Classpath
    @InputFiles
    @Incremental
    public abstract ConfigurableFileCollection getRawLocalResourcesNoProcessRes();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @IgnoreEmptyDirectories
    @Incremental
    public abstract ConfigurableFileCollection getRawLocalResourcesProcessRes();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Incremental
    public abstract ConfigurableFileCollection getLibrarySourceSets();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getGeneratedResDir();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getExtraGeneratedResDir();

    @NonNull
    private static ResourceCompilationService getResourceProcessor(
            @NonNull MergeResources mergeResourcesTask,
            ImmutableSet<Flag> flags,
            boolean processResources,
            Aapt2Input aapt2Input) {
        // If we received the flag for removing namespaces we need to use the namespace remover to
        // process the resources.
        if (flags.contains(Flag.REMOVE_RESOURCE_NAMESPACES)) {
            return NamespaceRemover.INSTANCE;
        }

        // If we're not removing namespaces and there's no need to compile the resources, return a
        // no-op resource processor.
        if (!processResources) {
            return CopyToOutputDirectoryResourceCompilationService.INSTANCE;
        }

        return new WorkerExecutorResourceCompilationService(
                mergeResourcesTask.getProjectPath(),
                mergeResourcesTask.getPath(),
                mergeResourcesTask.getWorkerExecutor(),
                mergeResourcesTask.getAnalyticsService(),
                aapt2Input);
    }

    @Internal
    @NonNull
    public WorkerExecutorFacade getAaptWorkerFacade() {
        return Workers.INSTANCE.withGradleWorkers(getProjectPath().get(), getPath(), getWorkerExecutor(), getAnalyticsService());
    }

    @NonNull
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getDataBindingLayoutInfoOutFolder();

    private SyncOptions.ErrorFormatMode errorFormatMode;

    @Internal
    public abstract Property<String> getAaptEnv();

    @Internal
    public abstract DirectoryProperty getProjectRootDir();

    protected void doFullTaskAction() throws IOException, JAXBException {
        ResourcePreprocessor preprocessor = getPreprocessor();
        File incrementalFolder = getIncrementalFolder().get().getAsFile();

        // this is full run, clean the previous outputs
        File destinationDir = getOutputDir().get().getAsFile();
        FileUtils.cleanOutputDir(destinationDir);
        if (getDataBindingLayoutInfoOutFolder().isPresent()) {
            FileUtils.deleteDirectoryContents(
                    getDataBindingLayoutInfoOutFolder().get().getAsFile());
        }

        List<ResourceSet> resourceSets =
                getConfiguredResourceSets(preprocessor, getAaptEnv().getOrNull());

        // create a new merger and populate it with the sets.
        ResourceMerger merger = new ResourceMerger(getMinSdk().get());
        MergingLog mergingLog = null;
        if (getBlameLogOutputFolder().isPresent()) {
            File blameLogFolder = getBlameLogOutputFolder().get().getAsFile();
            FileUtils.cleanOutputDir(blameLogFolder);
            mergingLog = new MergingLog(blameLogFolder);
        }

        try (WorkerExecutorFacade workerExecutorFacade = getAaptWorkerFacade();
                ResourceCompilationService resourceCompiler =
                        getResourceProcessor(
                                this,
                                flags,
                                processResources,
                                getAapt2())) {

            SingleFileProcessor dataBindingLayoutProcessor = maybeCreateLayoutProcessor();

            Blocks.recordSpan(
                    getPath(),
                    GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_1,
                    getAnalyticsService().get(),
                    () -> {
                        for (ResourceSet resourceSet : resourceSets) {
                            resourceSet.loadFromFiles(new LoggerWrapper(getLogger()));
                            merger.addDataSet(resourceSet);
                        }
                    });

            File publicFile =
                    getPublicFile().isPresent() ? getPublicFile().get().getAsFile() : null;

            Map<String, String> sourceSetPaths =
                    getRelativeSourceSetMap(resourceSets, destinationDir, incrementalFolder);

            MergedResourceWriter writer =
                    new MergedResourceWriter(
                            new MergedResourceWriterRequest(
                                    workerExecutorFacade,
                                    destinationDir,
                                    publicFile,
                                    mergingLog,
                                    preprocessor,
                                    resourceCompiler,
                                    incrementalFolder,
                                    dataBindingLayoutProcessor,
                                    mergedNotCompiledResourcesOutputDirectory,
                                    getPseudoLocalesEnabled().get(),
                                    getCrunchPng(),
                                    sourceSetPaths));

            Blocks.recordSpan(
                    getPath(),
                    GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_2,
                    getAnalyticsService().get(),
                    () -> merger.mergeData(writer, false /*doCleanUp*/));

            Blocks.recordSpan(
                    getPath(),
                    GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_3,
                    getAnalyticsService().get(),
                    () -> {
                        if (dataBindingLayoutProcessor != null) {
                            dataBindingLayoutProcessor.end();
                        }
                    });

            // No exception? Write the known state.
            Blocks.recordSpan(
                    getPath(),
                    GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_4,
                    getAnalyticsService().get(),
                    () -> merger.writeBlobTo(incrementalFolder, writer, false));
        } catch (Exception e) {
            MergingException.findAndReportMergingException(
                    e, new MessageReceiverImpl(errorFormatMode, getLogger()));
            try {
                throw e;
            } catch (MergingException mergingException) {
                merger.cleanBlob(incrementalFolder);
                throw new ResourceException(mergingException.getMessage(), mergingException);
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Check if the changed file is filtered out from the input to be compiled in compile library
     * resources task, if it is then it should be ignored.
     */
    private boolean isFilteredOutLibraryResource(File changedFile) {
        FileCollection localLibraryResources = getLibrarySourceSets();
        File parentFile = changedFile.getParentFile();
        if (parentFile.getName().startsWith(FD_RES_VALUES)) {
            return false;
        }
        for (File resDir : localLibraryResources.getFiles()) {
            if (parentFile.getAbsolutePath().startsWith(resDir.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void doTaskAction(@NonNull InputChanges changedInputs) {
        if (!changedInputs.isIncremental()) {
            try {
                getLogger().info("[MergeResources] Inputs are non-incremental full task action.");
                doFullTaskAction();
            } catch (IOException | JAXBException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        ResourcePreprocessor preprocessor = getPreprocessor();
        File incrementalFolder = getIncrementalFolder().get().getAsFile();

        ConfigurableFileCollection rawLocalResources =
                processResources
                        ? getRawLocalResourcesProcessRes()
                        : getRawLocalResourcesNoProcessRes();
        Iterable<FileChange> rawResourceChanges = changedInputs.getFileChanges(rawLocalResources);
        Iterable<FileChange> libraryResourceChanges =
                changedInputs.getFileChanges(getLibrarySourceSets());
        if (!rawResourceChanges.iterator().hasNext()
                && !libraryResourceChanges.iterator().hasNext()) {
            return;
        }
        // create a merger and load the known state.
        ResourceMerger merger = new ResourceMerger(getMinSdk().get());
        try {
            if (!merger.loadFromBlob(
                    incrementalFolder, true /*incrementalState*/, getAaptEnv().getOrNull())) {
                getLogger()
                        .info("[MergeResources] Blob cannot be loaded causing a full task action.");
                doFullTaskAction();
                return;
            }

            for (ResourceSet resourceSet : merger.getDataSets()) {
                resourceSet.setPreprocessor(preprocessor);
            }

            List<ResourceSet> resourceSets =
                    getConfiguredResourceSets(preprocessor, getAaptEnv().getOrNull());

            // compare the known state to the current sets to detect incompatibility.
            // This is in case there's a change that's too hard to do incrementally. In this case
            // we'll simply revert to full build.
            if (!merger.checkValidUpdate(resourceSets)) {
                getLogger().info("Changed Resource sets: full task run!");
                doFullTaskAction();
                return;
            }

            // The incremental process is the following:
            // Loop on all the changed files, find which ResourceSet it belongs to, then ask
            // the resource set to update itself with the new file.
            for (FileChange entry : rawResourceChanges) {
                if (!precompileDependenciesResources
                        || !isFilteredOutLibraryResource(entry.getFile())) {
                    if (!tryUpdateResourceSetsWithChangedFile(merger, entry)) {
                        doFullTaskAction();
                        return;
                    }
                }
            }
            for (FileChange entry : libraryResourceChanges) {
                if (!precompileDependenciesResources
                        || !isFilteredOutLibraryResource(entry.getFile())) {
                    if (!tryUpdateResourceSetsWithChangedFile(merger, entry)) {
                        doFullTaskAction();
                        return;
                    }
                }
            }

            MergingLog mergingLog =
                    getBlameLogOutputFolder().isPresent()
                            ? new MergingLog(getBlameLogOutputFolder().get().getAsFile())
                            : null;

            try (WorkerExecutorFacade workerExecutorFacade = getAaptWorkerFacade();
                    ResourceCompilationService resourceCompiler =
                            getResourceProcessor(
                                    this,
                                    flags,
                                    processResources,
                                    getAapt2())) {

                SingleFileProcessor dataBindingLayoutProcessor = maybeCreateLayoutProcessor();

                File publicFile =
                        getPublicFile().isPresent() ? getPublicFile().get().getAsFile() : null;
                File destinationDir = getOutputDir().get().getAsFile();
                Map<String, String> sourceSetPaths =
                        getRelativeSourceSetMap(resourceSets, destinationDir, incrementalFolder);

                MergedResourceWriter writer =
                        new MergedResourceWriter(
                                new MergedResourceWriterRequest(
                                        workerExecutorFacade,
                                        destinationDir,
                                        publicFile,
                                        mergingLog,
                                        preprocessor,
                                        resourceCompiler,
                                        incrementalFolder,
                                        dataBindingLayoutProcessor,
                                        mergedNotCompiledResourcesOutputDirectory,
                                        getPseudoLocalesEnabled().get(),
                                        getCrunchPng(),
                                        sourceSetPaths));

                merger.mergeData(writer, false /*doCleanUp*/);

                if (dataBindingLayoutProcessor != null) {
                    dataBindingLayoutProcessor.end();
                }

                // No exception? Write the known state.
                merger.writeBlobTo(incrementalFolder, writer, false);
            }
        } catch (Exception e) {
            MergingException.findAndReportMergingException(
                    e, new MessageReceiverImpl(errorFormatMode, getLogger()));
            try {
                throw e;
            } catch (MergingException mergingException) {
                merger.cleanBlob(incrementalFolder);
                throw new ResourceException(mergingException.getMessage(), mergingException);
            } catch (JAXBException | IOException runTimeException) {
                throw new RuntimeException(runTimeException);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        } finally {
            cleanup();
        }
    }

    private boolean tryUpdateResourceSetsWithChangedFile(ResourceMerger merger, FileChange entry)
            throws MergingException {
        File changedFile = entry.getFile();

        merger.findDataSetContaining(changedFile, fileValidity);
        if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
            getLogger()
                    .info(
                            "[MergeResources] "
                                    + changedFile.getAbsolutePath()
                                    + " has an unknown file status, requiring full task run.");
            return false;
        } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
            if (!fileValidity
                    .getDataSet()
                    .updateWith(
                            fileValidity.getSourceFile(),
                            changedFile,
                            IncrementalChangesUtils.toSerializable(entry.getChangeType()),
                            new LoggerWrapper(getLogger()))) {
                getLogger()
                        .info(
                                String.format(
                                        "[MergeResources] Failed to process %s event! "
                                                + "Requires full task run",
                                        entry.getChangeType()));
                return false;
            }
        }
        return true;
    }

    @NonNull
    private Map<String, String> getRelativeSourceSetMap(
            List<ResourceSet> resourceSets, File destinationDir, File incrementalFolder) {
        if (!getRelativePathsEnabled().get()) {
            return Collections.emptyMap();
        }
        List<File> sourceSets = Lists.newArrayList();
        for (ResourceSet sourceSet : resourceSets) {
            sourceSets.addAll(sourceSet.getSourceFiles());
        }
        if (getGeneratedPngsOutputDir().isPresent()) {
            sourceSets.add(getGeneratedPngsOutputDir().get().getAsFile());
        }
        sourceSets.add(destinationDir);
        sourceSets.add(FileUtils.join(incrementalFolder, SdkConstants.FD_MERGED_DOT_DIR));
        sourceSets.add(FileUtils.join(incrementalFolder, SdkConstants.FD_STRIPPED_DOT_DIR));
        return RelativeResourceUtils.getIdentifiedSourceSetMap(
                sourceSets, getNamespace().get(), getProjectPath().get());
    }

    @Nullable
    private SingleFileProcessor maybeCreateLayoutProcessor() {
        if (!getDataBindingEnabled().get() && !getViewBindingEnabled().get()) {
            return null;
        }

        LayoutXmlProcessor.OriginalFileLookup fileLookup;
        if (getBlameLogOutputFolder().isPresent()) {
            fileLookup = new MergingFileLookup(getBlameLogOutputFolder().get().getAsFile());
        } else {
            fileLookup = file -> null;
        }

        final LayoutXmlProcessor processor =
                new LayoutXmlProcessor(
                        getNamespace().get(),
                        new JavaFileWriter() {
                            // These methods are not supposed to be used, they are here only because
                            // the superclass requires an implementation of it.
                            // Whichever is calling this method is probably using it incorrectly
                            // (see stacktrace).
                            @Override
                            public void writeToFile(String canonicalName, String contents) {
                                throw new UnsupportedOperationException(
                                        "Not supported in this mode");
                            }

                            @Override
                            public void deleteFile(String canonicalName) {
                                throw new UnsupportedOperationException(
                                        "Not supported in this mode");
                            }
                        },
                        fileLookup,
                        getUseAndroidX().get());

        return new SingleFileProcessor() {

            private LayoutXmlProcessor getProcessor() {
                return processor;
            }

            @Override
            public boolean processSingleFile(
                    @NonNull File inputFile,
                    @NonNull File outputFile,
                    @Nullable Boolean inputFileIsFromDependency)
                    throws Exception {
                // Data binding doesn't need/want to process layout files that come
                // from dependencies (see bug 132637061).
                if (inputFileIsFromDependency == Boolean.TRUE) {
                    return false;
                }

                // For cache relocatability, we want to use relative paths, but we
                // can do that only for resource files that are located within the
                // root project directory.
                File rootProjectDir = getProjectRootDir().getAsFile().get();
                RelativizableFile normalizedInputFile;
                if (FileUtils.isFileInDirectory(inputFile, rootProjectDir)) {
                    // Check that the input file's absolute path has NOT been
                    // annotated as @Input via this task's
                    // getResourceDirsOutsideRootProjectDir() input file property.
                    checkState(
                            !resourceIsInResourceDirs(
                                    inputFile, getResourceDirsOutsideRootProjectDir().get()),
                            inputFile.getAbsolutePath() + " should not be annotated as @Input");

                    // The base directory of the relative path has to be the root
                    // project directory, not the current project directory, because
                    // the consumer on the IDE side relies on that---see bug
                    // 128643036.
                    normalizedInputFile =
                            RelativizableFile.fromAbsoluteFile(
                                    inputFile.getCanonicalFile(), rootProjectDir);
                    checkState(normalizedInputFile.getRelativeFile() != null);
                } else {
                    // Check that the input file's absolute path has been annotated
                    // as @Input via this task's
                    // getResourceDirsOutsideRootProjectDir() input file property.
                    checkState(
                            resourceIsInResourceDirs(
                                    inputFile, getResourceDirsOutsideRootProjectDir().get()),
                            inputFile.getAbsolutePath() + " is not annotated as @Input");

                    normalizedInputFile =
                            RelativizableFile.fromAbsoluteFile(inputFile.getCanonicalFile(), null);
                    checkState(normalizedInputFile.getRelativeFile() == null);
                }
                return getProcessor()
                        .processSingleFile(
                                normalizedInputFile,
                                outputFile,
                                getViewBindingEnabled().get(),
                                getDataBindingEnabled().get());
            }

            /**
             * Returns `true` if the given resource file is located inside one of the resource
             * directories.
             */
            private boolean resourceIsInResourceDirs(
                    @NonNull File resFile, @NonNull Set<String> resDirs) {
                return resDirs.stream()
                        .anyMatch(resDir -> FileUtils.isFileInDirectory(resFile, new File(resDir)));
            }

            @Override
            public void processRemovedFile(File file) {
                getProcessor().processRemovedFile(file);
            }

            @Override
            public void processFileWithNoDataBinding(@NonNull File file) {
                getProcessor().processFileWithNoDataBinding(file);
            }

            @Override
            public void end() throws JAXBException {
                getProcessor()
                        .writeLayoutInfoFiles(
                                getDataBindingLayoutInfoOutFolder().get().getAsFile());
            }
        };
    }

    private static class MergeResourcesVectorDrawableRenderer extends VectorDrawableRenderer {

        public MergeResourcesVectorDrawableRenderer(
                int minSdk,
                boolean supportLibraryIsUsed,
                File outputDir,
                Collection<Density> densities,
                Supplier<ILogger> loggerSupplier) {
            super(minSdk, supportLibraryIsUsed, outputDir, densities, loggerSupplier);
        }

        @Override
        public void generateFile(@NonNull File toBeGenerated, @NonNull File original)
                throws IOException {
            try {
                super.generateFile(toBeGenerated, original);
            } catch (ResourcesNotSupportedException e) {
                // Add gradle-specific error message.
                throw new GradleException(
                        String.format(
                                "Can't process attribute %1$s=\"%2$s\": references to other"
                                        + " resources are not supported by build-time PNG"
                                        + " generation.\n"
                                        + "%3$s\n"
                                        + "See http://developer.android.com/tools/help/vector-asset-studio.html"
                                        + " for details.",
                                e.getName(),
                                e.getValue(),
                                getPreprocessingReasonDescription(original)));
            }
        }
    }

    /**
     * Only one pre-processor for now. The code will need slight changes when we add more.
     */
    @NonNull
    private ResourcePreprocessor getPreprocessor() {
        if (disableVectorDrawables) {
            // If the user doesn't want any PNGs, leave the XML file alone as well.
            return NoOpResourcePreprocessor.INSTANCE;
        }

        Collection<Density> densities =
                getGeneratedDensities().stream().map(Density::getEnum).collect(Collectors.toList());

        return new MergeResourcesVectorDrawableRenderer(
                getMinSdk().get(),
                vectorSupportLibraryIsUsed,
                this.getGeneratedPngsOutputDir().get().getAsFile(),
                densities,
                LoggerWrapper.supplierFor(MergeResources.class));
    }

    @NonNull
    private List<ResourceSet> getConfiguredResourceSets(
            ResourcePreprocessor preprocessor, @Nullable String aaptEnv) {
        // It is possible that this get called twice in case the incremental run fails and reverts
        // back to full task run. Because the cached ResourceList is modified we don't want
        // to recompute this twice (plus, why recompute it twice anyway?)
        if (processedInputs == null) {
            processedInputs = resourcesComputer.compute(precompileDependenciesResources, aaptEnv);
            List<ResourceSet> generatedSets = new ArrayList<>(processedInputs.size());

            for (ResourceSet resourceSet : processedInputs) {
                resourceSet.setPreprocessor(preprocessor);
                ResourceSet generatedSet = new GeneratedResourceSet(resourceSet, aaptEnv);
                resourceSet.setGeneratedSet(generatedSet);
                generatedSets.add(generatedSet);
            }

            // We want to keep the order of the inputs. Given inputs:
            // (A, B, C, D)
            // We want to get:
            // (A-generated, A, B-generated, B, C-generated, C, D-generated, D).
            // Therefore, when later in {@link DataMerger} we look for sources going through the
            // list backwards, B-generated will take priority over A (but not B).
            // A real life use-case would be if an app module generated resource overrode a library
            // module generated resource (existing not in generated but bundled dir at this stage):
            // (lib, app debug, app main)
            // We will get:
            // (lib generated, lib, app debug generated, app debug, app main generated, app main)
            for (int i = 0; i < generatedSets.size(); ++i) {
                processedInputs.add(2 * i, generatedSets.get(i));
            }
        }

        return processedInputs;
    }

    /**
     * Releases resource sets not needed anymore, otherwise they will waste heap space for the
     * duration of the build.
     *
     * <p>This might be called twice when an incremental build falls back to a full one.
     */
    private void cleanup() {
        fileValidity.clear();
        processedInputs = null;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Input
    public boolean getCrunchPng() {
        return crunchPng;
    }

    @Input
    public boolean getProcessResources() {
        return processResources;
    }

    @Optional
    @OutputFile
    public abstract RegularFileProperty getPublicFile();

    // Synthetic input: the validation flag is set on the resource sets in CreationAction.execute.
    @Input
    public boolean isValidateEnabled() {
        return resourcesComputer.getValidateEnabled();
    }

    // the optional blame output folder for the case where the task generates it.
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getBlameLogOutputFolder();

    @Input
    public Collection<String> getGeneratedDensities() {
        return generatedDensities;
    }

    @Input
    public abstract Property<Integer> getMinSdk();

    @Input
    public boolean isVectorSupportLibraryUsed() {
        return vectorSupportLibraryIsUsed;
    }

    @Nullable
    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getMergedNotCompiledResourcesOutputDirectory();

    @Input
    public String getFlags() {
        return flags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getIncrementalFolder();

    public static class CreationAction
            extends VariantTaskCreationAction<MergeResources, ComponentCreationConfig> {
        @NonNull private final TaskManager.MergeType mergeType;
        @Nullable private final File mergedNotCompiledOutputDirectory;
        private final boolean includeDependencies;
        private final boolean processResources;
        private final boolean processVectorDrawables;
        @NonNull private final ImmutableSet<Flag> flags;
        private final boolean isLibrary;

        public CreationAction(
                @NonNull ComponentCreationConfig creationConfig,
                @NonNull TaskManager.MergeType mergeType,
                @Nullable File mergedNotCompiledOutputDirectory,
                boolean includeDependencies,
                boolean processResources,
                @NonNull ImmutableSet<Flag> flags,
                boolean isLibrary) {
            super(creationConfig);
            this.mergeType = mergeType;
            this.mergedNotCompiledOutputDirectory = mergedNotCompiledOutputDirectory;
            this.includeDependencies = includeDependencies;
            this.processResources = processResources;
            this.processVectorDrawables = flags.contains(Flag.PROCESS_VECTOR_DRAWABLES);
            this.flags = flags;
            this.isLibrary = isLibrary;
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName(mergeType.name().toLowerCase(Locale.ENGLISH), "Resources");
        }

        @NonNull
        @Override
        public Class<MergeResources> getType() {
            return MergeResources.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<MergeResources> taskProvider) {
            super.handleProvider(taskProvider);
            // In LibraryTaskManager#createMergeResourcesTasks, there are actually two
            // MergeResources tasks sharing the same task type (MergeResources) and CreationAction
            // code: packageResources with mergeType == PACKAGE, and mergeResources with
            // mergeType == MERGE. Since the following line of code is called for each task, the
            // latter one wins: The mergeResources task with mergeType == MERGE is the one that is
            // finally registered in the current scope.
            // Filed https://issuetracker.google.com//110412851 to clean this up at some point.
            creationConfig.getTaskContainer().setMergeResourcesTask(taskProvider);

            ArtifactsImpl artifacts = creationConfig.getArtifacts();
            artifacts.setInitialProvider(taskProvider, MergeResources::getOutputDir)
                    .on(mergeType.getOutputType());

            if (mergedNotCompiledOutputDirectory != null) {
                artifacts.setInitialProvider(taskProvider, MergeResources::getMergedNotCompiledResourcesOutputDirectory)
                        .atLocation(mergedNotCompiledOutputDirectory.getPath())
                        .on(MERGED_NOT_COMPILED_RES.INSTANCE);
            }

            artifacts.setInitialProvider(taskProvider, MergeResources::getDataBindingLayoutInfoOutFolder)
                    .withName("out")
                    .on(
                            mergeType == MERGE
                                    ? DATA_BINDING_LAYOUT_INFO_TYPE_MERGE.INSTANCE
                                    : DATA_BINDING_LAYOUT_INFO_TYPE_PACKAGE.INSTANCE);

            // only the full run with dependencies generates the blame folder
            if (includeDependencies) {
                artifacts.setInitialProvider(taskProvider, MergeResources::getBlameLogOutputFolder)
                        .withName("out")
                        .on(InternalArtifactType.MERGED_RES_BLAME_FOLDER.INSTANCE);
            }

            VariantPathHelper paths = creationConfig.getPaths();
            artifacts.setInitialProvider(taskProvider, MergeResources::getGeneratedPngsOutputDir)
                    .atLocation(mergeResources -> paths.getGeneratedPngsOutputDir());
        }

        @Override
        public void configure(@NonNull MergeResources task) {
            super.configure(task);

            VariantScope variantScope = creationConfig.getVariantScope();
            VariantPathHelper paths = creationConfig.getPaths();

            HasConfigurableValuesKt.setDisallowChanges(
                    task.getNamespace(), creationConfig.getNamespace());
            task.getMinSdk()
                    .set(
                            task.getProject()
                                    .provider(
                                            () -> creationConfig.getMinSdkVersion().getApiLevel()));
            task.getMinSdk().disallowChanges();

            task.getIncrementalFolder().set(paths.getIncrementalDir(getName()));
            task.processResources = processResources;
            task.crunchPng = variantScope.isCrunchPngs();

            VectorDrawablesOptions vectorDrawablesOptions =
                    creationConfig.getVariantDslInfo().getVectorDrawables();
            task.generatedDensities = vectorDrawablesOptions.getGeneratedDensities();
            if (task.generatedDensities == null) {
                task.generatedDensities = Collections.emptySet();
            }

            task.disableVectorDrawables =
                    !processVectorDrawables || task.generatedDensities.isEmpty();

            // TODO: When support library starts supporting gradients (http://b/62421666), remove
            // the vectorSupportLibraryIsUsed field and set disableVectorDrawables when
            // the getUseSupportLibrary method returns TRUE.
            task.vectorSupportLibraryIsUsed =
                    Boolean.TRUE.equals(vectorDrawablesOptions.getUseSupportLibrary());

            task.resourcesComputer = new DependencyResourcesComputer();
            if (!task.disableVectorDrawables) {
                task.getGeneratedPngsOutputDir().set(paths.getGeneratedPngsOutputDir());
            }
            ArtifactCollection libraryArtifacts =
                    includeDependencies
                            ? creationConfig
                                    .getVariantDependencies()
                                    .getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
                            : null;

            FileCollection microApk =
                    creationConfig
                            .getServices()
                            .fileCollection(
                                    creationConfig
                                            .getArtifacts()
                                            .get(InternalArtifactType.MICRO_APK_RES.INSTANCE));
            task.getSourceSetInputs().initialise(creationConfig, task, includeDependencies);
            if (includeDependencies) {
                task.getLibrarySourceSets()
                        .setFrom(task.getSourceSetInputs().getLibrarySourceSets());
            }
            task.getGeneratedResDir().setFrom(task.getSourceSetInputs().getGeneratedResDir());
            task.getExtraGeneratedResDir()
                    .setFrom(task.getSourceSetInputs().getExtraGeneratedResDir());
            task.resourcesComputer.initFromVariantScope(
                    creationConfig, task.getSourceSetInputs(), microApk, libraryArtifacts);

            final BuildFeatureValues features = creationConfig.getBuildFeatures();
            final boolean isDataBindingEnabled = features.getDataBinding();
            boolean isViewBindingEnabled = features.getViewBinding();

            HasConfigurableValuesKt.setDisallowChanges(
                    task.getDataBindingEnabled(), isDataBindingEnabled);
            HasConfigurableValuesKt.setDisallowChanges(
                    task.getViewBindingEnabled(), isViewBindingEnabled);

            if (isDataBindingEnabled || isViewBindingEnabled) {
                HasConfigurableValuesKt.setDisallowChanges(
                        task.getUseAndroidX(),
                        creationConfig
                                .getServices()
                                .getProjectOptions()
                                .get(BooleanOption.USE_ANDROID_X));
            }

            task.mergedNotCompiledResourcesOutputDirectory = mergedNotCompiledOutputDirectory;

            task.getPseudoLocalesEnabled().set(creationConfig.getPseudoLocalesEnabled());
            task.getPseudoLocalesEnabled().disallowChanges();
            task.flags = flags;

            task.errorFormatMode =
                    SyncOptions.getErrorFormatMode(
                            creationConfig.getServices().getProjectOptions());

            task.precompileDependenciesResources =
                    mergeType.equals(MERGE)
                            && !isLibrary
                            && creationConfig.isPrecompileDependenciesResourcesEnabled();

            task.getResourceDirsOutsideRootProjectDir()
                    .set(
                            task.getProject()
                                    .provider(
                                            () ->
                                                    getResourcesDirsOutsideRoot(
                                                            task,
                                                            isDataBindingEnabled,
                                                            isViewBindingEnabled)));
            task.getResourceDirsOutsideRootProjectDir().disallowChanges();

            task.dependsOn(creationConfig.getTaskContainer().getResourceGenTask());

            if (processResources) {
                task.getRawLocalResourcesProcessRes()
                        .setFrom(task.getSourceSetInputs().getResourceSourceSets());
            } else {
                task.getRawLocalResourcesNoProcessRes()
                        .setFrom(task.getSourceSetInputs().getResourceSourceSets());
            }
            task.getRawLocalResourcesProcessRes().disallowChanges();
            task.getRawLocalResourcesNoProcessRes().disallowChanges();

            HasConfigurableValuesKt.setDisallowChanges(
                    task.getAapt2ThreadPoolBuildService(),
                    BuildServicesKt.getBuildService(
                            creationConfig.getServices().getBuildServiceRegistry(),
                            Aapt2ThreadPoolBuildService.class));
            creationConfig.getServices().initializeAapt2Input(task.getAapt2());
            task.getAaptEnv()
                    .set(
                            creationConfig
                                    .getServices()
                                    .getGradleEnvironmentProvider()
                                    .getEnvVariable(ANDROID_AAPT_IGNORE));
            task.getProjectRootDir().set(task.getProject().getRootDir());

            task.getRelativePathsEnabled()
                    .set(
                            creationConfig
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.ENABLE_SOURCE_SET_PATHS_MAP));
        }

        @NonNull
        private static Set<String> getResourcesDirsOutsideRoot(
                @NonNull MergeResources task,
                boolean isDataBindingEnabled,
                boolean isViewBindingEnabled)
                throws IOException {
            Set<String> resourceDirsOutsideRootProjectDir = new HashSet<>();
            if (!isDataBindingEnabled && !isViewBindingEnabled) {
                // This set is used only when data binding / view binding is enabled
                return resourceDirsOutsideRootProjectDir;
            }

            // In this task, data binding doesn't process layout files that come from dependencies
            // (see the code at processSingleFile()). Therefore, we'll look at resources in the
            // current sub-project only (via task.getLocalResources()). These resources are usually
            // located inside the root project directory, but some of them may come from custom
            // resource sets that are outside of the root project directory, so we need to collect
            // the latter set.
            File rootProjectDir = task.getProject().getRootDir();
            ConfigurableFileCollection resourceSourceSets =
                    task.processResources
                            ? task.getRawLocalResourcesProcessRes()
                            : task.getRawLocalResourcesNoProcessRes();

            for (File resDir : resourceSourceSets.getFiles()) {
                if (!FileUtils.isFileInDirectory(resDir, rootProjectDir)) {
                    resourceDirsOutsideRootProjectDir.add(resDir.getCanonicalPath());
                }
            }
            return resourceDirsOutsideRootProjectDir;
        }
    }

    public enum Flag {
        REMOVE_RESOURCE_NAMESPACES,
        PROCESS_VECTOR_DRAWABLES,
    }
}
