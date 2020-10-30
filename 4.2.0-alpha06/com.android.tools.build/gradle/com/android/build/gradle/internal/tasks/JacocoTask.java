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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.component.BaseCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.tasks.IncrementalChangesUtils;
import com.android.builder.dexing.DexerTool;
import com.android.builder.files.SerializableChange;
import com.android.builder.files.SerializableFileChanges;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;
import org.gradle.workers.WorkQueue;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

@CacheableTask
public abstract class JacocoTask extends NewIncrementalTask {
    @Classpath
    public abstract ConfigurableFileCollection getJacocoAntTaskConfiguration();

    @Nested
    public abstract JarsClasspathInputsWithIdentity getJarsWithIdentity();

    @Incremental
    @Classpath
    public abstract ConfigurableFileCollection getClassesDir();

    @Input
    public abstract Property<Boolean> getForceOutOfProcess();

    @OutputDirectory
    public abstract DirectoryProperty getOutputForDirs();

    @OutputDirectory
    public abstract DirectoryProperty getOutputForJars();

    /** Returns which Jacoco version to use. */
    @NonNull
    public static String getJacocoVersion(@NonNull BaseCreationConfig creationConfig) {
        if (creationConfig.getVariantScope().getDexer() == DexerTool.DX) {
            return JacocoConfigurations.VERSION_FOR_DX;
        } else {
            return creationConfig.getGlobalScope().getExtension().getJacoco().getVersion();
        }
    }

    @Override
    public void doTaskAction(@NonNull InputChanges inputChanges) {
        processDirectories(inputChanges);
        processJars(inputChanges);
    }

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    // META-INF/*.kotlin_module files need to be copied to output so they show up
    // in the intermediate classes jar.
    private static final Pattern KOTLIN_MODULE_PATTERN =
            Pattern.compile("^META-INF/.*\\.kotlin_module$");

    private void processDirectories(InputChanges inputChanges) {
        SerializableFileChanges changes =
                IncrementalChangesUtils.toSerializable(
                        inputChanges.getFileChanges(getClassesDir()));
        Set<SerializableChange> filesToProcess = new HashSet<>(changes.getAddedFiles());
        for (SerializableChange removedFile : changes.getRemovedFiles()) {
            removeFile(removedFile);
        }
        for (SerializableChange modifiedFile : changes.getModifiedFiles()) {
            removeFile(modifiedFile);
            filesToProcess.add(modifiedFile);
        }

        Map<Action, List<SerializableChange>> toProcess = new EnumMap<>(Action.class);
        for (SerializableChange change : filesToProcess) {
            Action action = calculateAction(change.getNormalizedPath());
            if (action == Action.IGNORE) {
                continue;
            }

            List<SerializableChange> byAction = toProcess.getOrDefault(action, new ArrayList<>());
            byAction.add(change);
            toProcess.put(action, byAction);
        }
        WorkQueue workQueue = getWorkQueue();
        workQueue.submit(
                InstrumentDirAction.class,
                params -> {
                    params.initializeFromAndroidVariantTask(this);
                    params.getChangesToProcess().set(toProcess);
                    params.getOutput().set(getOutputForDirs().getAsFile());
                });
    }

    private void processJars(InputChanges inputChanges) {
        Map<File, FileInfo> jarsInfo =
                getJarsWithIdentity().getMappingState(inputChanges).getJarsInfo();

        for (Map.Entry<File, FileInfo> fileToInfo : jarsInfo.entrySet()) {
            FileInfo fileInfo = fileToInfo.getValue();
            if (fileInfo.getHasChanged()) {
                File instrumentedJar =
                        getCorrespondingInstrumentedJar(
                                getOutputForJars().get().getAsFile(),
                                Objects.requireNonNull(fileInfo.getIdentity()));
                try {
                    FileUtils.deleteIfExists(instrumentedJar);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }

                WorkQueue workQueue = getWorkQueue();
                File outputJarsFolder = getOutputForJars().getAsFile().get();

                workQueue.submit(
                        InstrumentJarAction.class,
                        params -> {
                            params.initializeFromAndroidVariantTask(this);
                            params.getRoot().set(fileToInfo.getKey());
                            params.getOutput().set(instrumentedJar);
                        });
            }
        }
    }

    private void removeFile(SerializableChange fileToRemove) {
        Action action = calculateAction(fileToRemove.getNormalizedPath());
        if (action == Action.IGNORE) {
            return;
        }
        Path outputPath =
                getOutputForDirs()
                        .get()
                        .getAsFile()
                        .toPath()
                        .resolve(fileToRemove.getNormalizedPath());
        try {
            PathUtils.deleteRecursivelyIfExists(outputPath);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private WorkQueue getWorkQueue() {
        if (getForceOutOfProcess().get()) {
            return getWorkerExecutor()
                    .processIsolation(
                            spec -> spec.getClasspath().from(getJacocoAntTaskConfiguration()));
        } else {
            return getWorkerExecutor()
                    .classLoaderIsolation(
                            spec -> spec.getClasspath().from(getJacocoAntTaskConfiguration()));
        }
    }

    private static Action calculateAction(@NonNull String inputRelativePath) {
        for (Pattern pattern : Action.COPY.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.COPY;
            }
        }
        for (Pattern pattern : Action.INSTRUMENT.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.INSTRUMENT;
            }
        }
        return Action.IGNORE;
    }

    /** The possible actions which can happen to an input file */
    enum Action {

        /** The file is just copied to the transform output. */
        COPY(KOTLIN_MODULE_PATTERN),

        /** The file is ignored. */
        IGNORE(),

        /** The file is instrumented and added to the transform output. */
        INSTRUMENT(CLASS_PATTERN);

        private final ImmutableList<Pattern> patterns;

        /**
         * @param patterns Patterns are compared to files' relative paths to determine if they
         *     undergo the corresponding action.
         */
        Action(@NonNull Pattern... patterns) {
            ImmutableList.Builder<Pattern> builder = new ImmutableList.Builder<>();
            for (Pattern pattern : patterns) {
                Preconditions.checkNotNull(pattern);
                builder.add(pattern);
            }
            this.patterns = builder.build();
        }

        @NonNull
        ImmutableList<Pattern> getPatterns() {
            return patterns;
        }
    }

    public abstract static class InstrumentDirAction
            extends ProfileAwareWorkAction<InstrumentDirAction.Parameters> {

        public abstract static class Parameters extends ProfileAwareWorkAction.Parameters {
            public abstract MapProperty<Action, List<SerializableChange>> getChangesToProcess();

            public abstract Property<File> getOutput();
        }

        @NonNull
        private static final LoggerWrapper logger =
                LoggerWrapper.getLogger(InstrumentDirAction.class);

        @Override
        public void run() {
            Map<Action, List<SerializableChange>> inputs =
                    getParameters().getChangesToProcess().get();
            File outputDir = getParameters().getOutput().get();

            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            for (SerializableChange toInstrument :
                    inputs.getOrDefault(Action.INSTRUMENT, ImmutableList.of())) {
                logger.info("Instrumenting file: " + toInstrument.getFile().getAbsolutePath());
                try (InputStream inputStream =
                        Files.asByteSource(toInstrument.getFile()).openBufferedStream()) {
                    byte[] instrumented =
                            instrumenter.instrument(inputStream, toInstrument.toString());
                    File outputFile = new File(outputDir, toInstrument.getNormalizedPath());
                    Files.createParentDirs(outputFile);
                    Files.write(instrumented, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Unable to instrument file with Jacoco: " + toInstrument.getFile(), e);
                }
            }

            for (SerializableChange toCopy : inputs.getOrDefault(Action.COPY, ImmutableList.of())) {
                File outputFile = new File(outputDir, toCopy.getNormalizedPath());
                try {
                    Files.createParentDirs(outputFile);
                    Files.copy(toCopy.getFile(), outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to copy file: " + toCopy.getFile(), e);
                }
            }
        }
    }

    public abstract static class InstrumentJarAction
            extends ProfileAwareWorkAction<InstrumentJarAction.Parameters> {

        public abstract static class Parameters extends ProfileAwareWorkAction.Parameters {
            public abstract Property<File> getRoot();

            public abstract Property<File> getOutput();
        }

        @NonNull
        private static final LoggerWrapper logger =
                LoggerWrapper.getLogger(InstrumentJarAction.class);

        @Override
        public void run() {
            File inputJar = getParameters().getRoot().get();
            logger.info("Instrumenting jar: " + inputJar.getAbsolutePath());
            File instrumentedJar = getParameters().getOutput().get();
            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            try (ZipOutputStream outputZip =
                    new ZipOutputStream(
                            new BufferedOutputStream(new FileOutputStream(instrumentedJar)))) {
                try (ZipFile zipFile = new ZipFile(inputJar)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        Action entryAction = calculateAction(entryName);
                        if (entryAction == Action.IGNORE) {
                            continue;
                        }
                        InputStream classInputStream = zipFile.getInputStream(entry);
                        byte[] data;
                        if (entryAction == Action.INSTRUMENT) {
                            data = instrumenter.instrument(classInputStream, entryName);
                        } else { // just copy
                            data = ByteStreams.toByteArray(classInputStream);
                        }
                        ZipEntry nextEntry = new ZipEntry(entryName);
                        // Any negative time value sets ZipEntry's xdostime to DOSTIME_BEFORE_1980
                        // constant.
                        nextEntry.setTime(-1L);
                        outputZip.putNextEntry(nextEntry);
                        outputZip.write(data);
                        outputZip.closeEntry();
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Unable to instrument file with Jacoco: " + inputJar, e);
            }
        }
    }

    private static File getCorrespondingInstrumentedJar(
            @NonNull File outputFolder, @NonNull String identity) {
        return new File(outputFolder, identity + SdkConstants.DOT_JAR);
    }

    public static class CreationAction
            extends VariantTaskCreationAction<JacocoTask, VariantCreationConfig> {

        public CreationAction(@NonNull VariantCreationConfig creationConfig) {
            super(creationConfig);
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
                    .setInitialProvider(taskProvider, JacocoTask::getOutputForDirs)
                    .withName("out")
                    .on(InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES.INSTANCE);

            creationConfig
                    .getArtifacts()
                    .setInitialProvider(taskProvider, JacocoTask::getOutputForJars)
                    .withName("out")
                    .on(InternalArtifactType.JACOCO_INSTRUMENTED_JARS.INSTANCE);
        }

        static class FilterJarsOnly implements Spec<File> {
            @Override
            public boolean isSatisfiedBy(File file) {
                return file.getName().endsWith(SdkConstants.DOT_JAR);
            }
        }

        static class FilterNonJarsOnly implements Spec<File> {
            @Override
            public boolean isSatisfiedBy(File file) {
                return !file.getName().endsWith(SdkConstants.DOT_JAR);
            }
        }

        @Override
        public void configure(@NonNull JacocoTask task) {
            super.configure(task);

            task.getJarsWithIdentity()
                    .getInputJars()
                    .from(
                            creationConfig
                                    .getArtifacts()
                                    .getAllClasses()
                                    .filter(new FilterJarsOnly()));
            task.getClassesDir()
                    .from(
                            creationConfig
                                    .getArtifacts()
                                    .getAllClasses()
                                    .filter(new FilterNonJarsOnly()));
            task.getJacocoAntTaskConfiguration()
                    .from(
                            JacocoConfigurations.getJacocoAntTaskConfiguration(
                                    task.getProject(), getJacocoVersion(creationConfig)));
            task.getForceOutOfProcess()
                    .set(
                            creationConfig
                                    .getServices()
                                    .getProjectOptions()
                                    .get(BooleanOption.FORCE_JACOCO_OUT_OF_PROCESS));
        }
    }
}
