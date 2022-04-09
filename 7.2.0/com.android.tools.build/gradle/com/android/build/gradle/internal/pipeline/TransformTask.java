/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.profile.AnalyticsUtil;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.profile.Recorder;
import com.android.ide.common.util.ReferenceHolder;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import com.google.wireless.android.sdk.stats.GradleTransformExecution;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.workers.WorkerExecutor;

/** A task running a transform. */
@CacheableTask
public abstract class TransformTask extends StreamBasedTask {

    private Transform transform;
    Collection<SecondaryFile> secondaryFiles = null;
    List<FileCollection> secondaryInputFiles = null;

    @Internal
    public Transform getTransform() {
        return transform;
    }

    @Input
    @NonNull
    public Set<? super QualifiedContent.Scope> getScopes() {
        return transform.getScopes();
    }

    @Input
    @NonNull
    public Set<? super QualifiedContent.Scope> getReferencedScopes() {
        return transform.getReferencedScopes();
    }

    @Input
    @NonNull
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return transform.getInputTypes();
    }

    @InputFiles
    // Use ABSOLUTE to be safe, the API that this method calls is deprecated anyway.
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @Optional
    public Collection<File> getOldSecondaryInputs() {
        //noinspection deprecation: Needed for backward compatibility.
        return transform.getSecondaryFileInputs();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<FileCollection> getSecondaryFileInputs() {
        return secondaryInputFiles;
    }

    @OutputFiles
    public Map<String, File> getOtherFileOutputs() {

        ImmutableMap.Builder<String, File> builder = new ImmutableMap.Builder<>();
        int index = 0;
        for (File outputFile : transform.getSecondaryFileOutputs()) {
            builder.put("otherFileOutput" + Integer.toString(++index), outputFile);
        }

        return builder.build();
    }

    @OutputDirectory
    @Optional
    @NonNull
    public abstract DirectoryProperty getOutputDirectory();

    @OutputFile
    @Optional
    @NonNull
    public abstract RegularFileProperty getOutputFile();

    @OutputDirectories
    public Map<String, File> getOtherFolderOutputs() {
        ImmutableMap.Builder<String, File> builder = new ImmutableMap.Builder<>();
        int index = 0;
        for (File outputFolder : transform.getSecondaryDirectoryOutputs()) {
            builder.put("otherFolderOutput" + Integer.toString(++index), outputFolder);
        }

        return builder.build();
    }

    @Input
    public Map<String, Object> getOtherInputs() {
        return transform.getParameterInputs();
    }

    @TaskAction
    void transform(final IncrementalTaskInputs incrementalTaskInputs)
            throws IOException, TransformException, InterruptedException {

        final ReferenceHolder<List<TransformInput>> consumedInputs = ReferenceHolder.empty();
        final ReferenceHolder<List<TransformInput>> referencedInputs = ReferenceHolder.empty();
        final ReferenceHolder<Boolean> isIncremental = ReferenceHolder.empty();
        final ReferenceHolder<Collection<SecondaryInput>> changedSecondaryInputs =
                ReferenceHolder.empty();

        isIncremental.setValue(transform.isIncremental() && incrementalTaskInputs.isIncremental());

        GradleTransformExecution preExecutionInfo =
                GradleTransformExecution.newBuilder()
                        .setType(AnalyticsUtil.getTransformType(transform.getClass()).getNumber())
                        .setIsIncremental(isIncremental.getValue())
                        .setTransformClassName(transform.getClass().getName())
                        .build();

        AnalyticsService analyticsService = getAnalyticsService().get();
        analyticsService.recordBlock(
                ExecutionType.TASK_TRANSFORM_PREPARATION,
                preExecutionInfo,
                getProjectPath().get(),
                getVariantName(),
                new Recorder.VoidBlock() {
                    @Override
                    public void call() {

                        Map<File, Status> changedMap = Maps.newHashMap();
                        Set<File> removedFiles = Sets.newHashSet();
                        if (isIncremental.getValue()) {
                            // gather the changed files first.
                            gatherChangedFiles(
                                    getLogger(), incrementalTaskInputs, changedMap, removedFiles);

                            // and check against secondary files, which disables
                            // incremental mode.
                            isIncremental.setValue(checkSecondaryFiles(changedMap, removedFiles));
                        }

                        if (isIncremental.getValue()) {
                            // ok create temporary incremental data
                            List<IncrementalTransformInput> incInputs =
                                    createIncrementalInputs(consumedInputStreams);
                            List<IncrementalTransformInput> incReferencedInputs =
                                    createIncrementalInputs(referencedInputStreams);

                            // then compare to changed list and create final Inputs
                            if (isIncremental.setValue(
                                    updateIncrementalInputsWithChangedFiles(
                                            incInputs,
                                            incReferencedInputs,
                                            changedMap,
                                            removedFiles))) {
                                consumedInputs.setValue(convertToImmutable(incInputs));
                                referencedInputs.setValue(convertToImmutable(incReferencedInputs));
                            }
                        }

                        // at this point if we do not have incremental mode, got with
                        // default TransformInput with no inc data.
                        if (!isIncremental.getValue()) {
                            consumedInputs.setValue(
                                    computeNonIncTransformInput(consumedInputStreams));
                            referencedInputs.setValue(
                                    computeNonIncTransformInput(referencedInputStreams));
                            changedSecondaryInputs.setValue(ImmutableList.of());
                        } else {
                            // gather all secondary input changes.
                            changedSecondaryInputs.setValue(
                                    gatherSecondaryInputChanges(changedMap, removedFiles));
                        }
                    }
                });

        GradleTransformExecution executionInfo =
                preExecutionInfo.toBuilder().setIsIncremental(isIncremental.getValue()).build();

        analyticsService.recordBlock(
                ExecutionType.TASK_TRANSFORM,
                executionInfo,
                getProjectPath().get(),
                getVariantName(),
                new Recorder.VoidBlock() {
                    @Override
                    public void call() throws Exception {
                        Context context =
                                new Context() {
                                    @Override
                                    public LoggingManager getLogging() {
                                        return TransformTask.this.getLogging();
                                    }

                                    @Override
                                    public File getTemporaryDir() {
                                        return TransformTask.this.getTemporaryDir();
                                    }

                                    @Override
                                    public String getPath() {
                                        return TransformTask.this.getPath();
                                    }

                                    @Override
                                    public String getProjectName() {
                                        return TransformTask.this.getProjectPath().get();
                                    }

                                    @NonNull
                                    @Override
                                    public String getVariantName() {
                                        return TransformTask.this.getVariantName();
                                    }

                                    @NonNull
                                    @Override
                                    public WorkerExecutor getWorkerExecutor() {
                                        return TransformTask.this.getWorkerExecutor();
                                    }
                                };
                        transform.transform(
                                new TransformInvocationBuilder(context)
                                        .addInputs(consumedInputs.getValue())
                                        .addReferencedInputs(referencedInputs.getValue())
                                        .addSecondaryInputs(changedSecondaryInputs.getValue())
                                        .addOutputProvider(
                                                outputStream != null
                                                        ? outputStream.asOutput()
                                                        : null)
                                        .setIncrementalMode(isIncremental.getValue())
                                        .build());

                        if (outputStream != null) {
                            outputStream.save();
                        }
                    }
                });
    }

    private Collection<SecondaryInput> gatherSecondaryInputChanges(
            Map<File, Status> changedMap, Set<File> removedFiles) {

        ImmutableList.Builder<SecondaryInput> builder = ImmutableList.builder();
        for (final SecondaryFile secondaryFile : getAllSecondaryInputs()) {
            for (File file : getSecondaryInputFiles(secondaryFile)) {
                final Status status = changedMap.containsKey(file)
                        ? changedMap.get(file)
                        : removedFiles.contains(file)
                                ? Status.REMOVED
                                : Status.NOTCHANGED;

                builder.add(new SecondaryInput() {
                    @Override
                    public SecondaryFile getSecondaryInput() {
                        return secondaryFile;
                    }

                    @Override
                    public Status getStatus() {
                        return status;
                    }
                });
            }
        }
        return builder.build();
    }

    /**
     * Returns a list of non incremental TransformInput.
     * @param streams the streams.
     * @return a list of non-incremental TransformInput matching the content of the streams.
     */
    @NonNull
    private static List<TransformInput> computeNonIncTransformInput(
            @NonNull Collection<TransformStream> streams) {
        return streams.stream()
                .map(TransformStream::asNonIncrementalInput)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of IncrementalTransformInput for all the inputs.
     */
    @NonNull
    private static List<IncrementalTransformInput> createIncrementalInputs(
            @NonNull Collection<TransformStream> streams) {
        return streams.stream()
                .map(TransformStream::asIncrementalInput)
                .collect(Collectors.toList());
    }

    private synchronized Collection<SecondaryFile> getAllSecondaryInputs() {
        if (secondaryFiles == null) {
            ImmutableList.Builder<SecondaryFile> builder = ImmutableList.builder();
            builder.addAll(transform.getSecondaryFiles());
            //noinspection deprecation
            builder.addAll(
                    transform
                            .getSecondaryFileInputs()
                            .stream()
                            .map(SecondaryFile::nonIncremental)
                            .iterator());
            secondaryFiles = builder.build();
        }
        return secondaryFiles;
    }

    private static void gatherChangedFiles(
            @NonNull Logger logger,
            @NonNull IncrementalTaskInputs incrementalTaskInputs,
            @NonNull final Map<File, Status> changedFileMap,
            @NonNull final Set<File> removedFiles) {
        logger.info("Transform inputs calculations based on following changes");
        incrementalTaskInputs.outOfDate(inputFileDetails -> {
            logger.info(inputFileDetails.getFile().getAbsolutePath() + ":"
                    + IntermediateFolderUtils.inputFileDetailsToStatus(inputFileDetails));
            if (inputFileDetails.isAdded()) {
                changedFileMap.put(inputFileDetails.getFile(), Status.ADDED);
            } else if (inputFileDetails.isModified()) {
                changedFileMap.put(inputFileDetails.getFile(), Status.CHANGED);
            }
        });

        incrementalTaskInputs.removed(
                inputFileDetails -> {
                        logger.info(inputFileDetails.getFile().getAbsolutePath() + ":REMOVED");
                        removedFiles.add(inputFileDetails.getFile());
                });
    }

    private boolean checkSecondaryFiles(
            @NonNull Map<File, Status> changedMap,
            @NonNull Set<File> removedFiles) {

        for (SecondaryFile secondaryFile : getAllSecondaryInputs()) {
            Set<File> files = getSecondaryInputFiles(secondaryFile);
            if ((!Sets.intersection(files, changedMap.keySet()).isEmpty()
                    || !Sets.intersection(files, removedFiles).isEmpty())
                    && !secondaryFile.supportsIncrementalBuild()) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private Set<File> getSecondaryInputFiles(SecondaryFile secondaryFile) {
        Set<File> secondaryInputFiles = Sets.newHashSet();
        FileCollection secondaryInputFileCollection = secondaryFile.getFileCollection();
        if (secondaryInputFileCollection != null) {
            secondaryInputFiles.addAll(secondaryInputFileCollection.getFiles());
        } else {
            secondaryInputFiles.add(secondaryFile.getFile());
        }
        return secondaryInputFiles;
    }

    private boolean isSecondaryFile(File file) {
        for (FileCollection secondaryFileInput : getSecondaryFileInputs()) {
            if (secondaryFileInput.contains(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean updateIncrementalInputsWithChangedFiles(
            @NonNull List<IncrementalTransformInput> consumedInputs,
            @NonNull List<IncrementalTransformInput> referencedInputs,
            @NonNull Map<File, Status> changedFilesMap,
            @NonNull Set<File> removedFiles) {

        // we're going to concat both list multiple times, and the Iterators API ultimately put
        // all the iterators to concat in a list. So let's reuse a list.
        List<Iterator<IncrementalTransformInput>> iterators = Lists.newArrayListWithCapacity(2);

        Splitter splitter = Splitter.on(File.separatorChar);

        final Sets.SetView<? super QualifiedContent.Scope> scopes =
                Sets.union(transform.getScopes(), transform.getReferencedScopes());
        final Set<QualifiedContent.ContentType> inputTypes = transform.getInputTypes();

        // start with the removed files as they carry the risk of removing incremental mode.
        // If we detect such a case, we stop immediately.
        for (File removedFile : removedFiles) {
            List<String> removedFileSegments = Lists.newArrayList(
                    splitter.split(removedFile.getAbsolutePath()));

            Iterator<IncrementalTransformInput> iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);

            boolean found = false;
            while (iterator.hasNext()) {
                IncrementalTransformInput next = iterator.next();
                if (next.checkRemovedJarFile(scopes, inputTypes, removedFile, removedFileSegments)
                        || next.checkRemovedFolderFile(
                                scopes, inputTypes, removedFile, removedFileSegments)) {
                    found = true;
                    break;
                }
            }

            if (!found && !isSecondaryFile(removedFile)) {
                // this deleted file breaks incremental because we cannot figure out where it's
                // coming from and what types/scopes is associated with it.
                return false;
            }
        }

        // now handle the added/changed files.

        for (Map.Entry<File, Status> entry : changedFilesMap.entrySet()) {
            File changedFile = entry.getKey();
            Status changedStatus = entry.getValue();

            // first go through the jars first as it's a faster check.
            Iterator<IncrementalTransformInput> iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);
            boolean found = false;
            while (iterator.hasNext()) {
                if (iterator.next().checkForJar(changedFile, changedStatus)) {
                    // we can skip to the next changed file.
                    found = true;
                    break;
                }
            }

            if (found) {
                continue;
            }

            // now go through the folders. First get a segment list for the path.
            iterator = getConcatIterator(consumedInputs,
                    referencedInputs, iterators);
            List<String> changedSegments = Lists.newArrayList(
                    splitter.split(changedFile.getAbsolutePath()));

            while (iterator.hasNext()) {
                if (iterator.next().checkForFolder(changedFile, changedSegments, changedStatus)) {
                    // we can skip to the next changed file.
                    break;
                }
            }
        }

        return true;
    }

    @NonNull
    private static Iterator<IncrementalTransformInput> getConcatIterator(
            @NonNull List<IncrementalTransformInput> consumedInputs,
            @NonNull List<IncrementalTransformInput> referencedInputs,
            List<Iterator<IncrementalTransformInput>> iterators) {
        iterators.clear();
        iterators.add(consumedInputs.iterator());
        iterators.add(referencedInputs.iterator());
        return Iterators.concat(iterators.iterator());
    }

    @NonNull
    private static List<TransformInput> convertToImmutable(
            @NonNull List<IncrementalTransformInput> inputs) {
        return inputs.stream()
                .map(IncrementalTransformInput::asImmutable)
                .collect(Collectors.toList());
    }

    public static class CreationAction<T extends Transform>
            extends TaskCreationAction<TransformTask> {

        @NonNull
        private final String variantName;
        @NonNull
        private final String taskName;
        @NonNull
        private final T transform;
        @NonNull
        private Collection<TransformStream> consumedInputStreams;
        @NonNull
        private Collection<TransformStream> referencedInputStreams;
        @Nullable
        private IntermediateStream outputStream;

        CreationAction(
                @NonNull String variantName,
                @NonNull String taskName,
                @NonNull T transform,
                @NonNull Collection<TransformStream> consumedInputStreams,
                @NonNull Collection<TransformStream> referencedInputStreams,
                @Nullable IntermediateStream outputStream) {
            this.variantName = variantName;
            this.taskName = taskName;
            this.transform = transform;
            this.consumedInputStreams = consumedInputStreams;
            this.referencedInputStreams = referencedInputStreams;
            this.outputStream = outputStream;
        }

        @NonNull
        @Override
        public String getName() {
            return taskName;
        }

        @NonNull
        @Override
        public Class<TransformTask> getType() {
            return TransformTask.class;
        }

        @Override
        public void configure(@NonNull TransformTask task) {
            task.transform = transform;
            transform.setOutputDirectory(task.getOutputDirectory());
            transform.setOutputFile(task.getOutputFile());
            task.consumedInputStreams = consumedInputStreams;
            task.referencedInputStreams = referencedInputStreams;
            task.outputStream = outputStream;
            if (outputStream != null) {
                task.getStreamOutputFolder()
                        .fileProvider(task.getProject().provider(outputStream::getRootLocation));
            }
            task.setVariantName(variantName);
            boolean cachingEnabled = transform.isCacheable();
            task.getOutputs()
                    .cacheIf(
                            "Transform "
                                    + transform.getClass().getName()
                                    + " declares itself as cacheable",
                            (Spec<? super Task> & Serializable) (t -> cachingEnabled));
            task.registerConsumedAndReferencedStreamInputs();
            task.getProjectPath().set(task.getProject().getPath());
            task.secondaryInputFiles =
                    transform.getSecondaryFiles().stream()
                            .map(
                                    secondaryFile ->
                                            secondaryFile.getFileCollection(task.getProject()))
                            .collect(Collectors.toList());
            task.getAnalyticsService().set(BuildServicesKt.getBuildService(
                    task.getProject().getGradle().getSharedServices(), AnalyticsService.class));
        }
    }
}
