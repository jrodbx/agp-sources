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
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

/** Delegate for {@link JacocoTask}. */
public class JacocoTaskDelegate {

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    // META-INF/*.kotlin_module files need to be copied to output so they show up
    // in the intermediate classes jar.
    private static final Pattern KOTLIN_MODULE_PATTERN =
            Pattern.compile("^META-INF/.*\\.kotlin_module$");

    @NonNull private final FileCollection jacocoAntTaskConfiguration;
    @NonNull private final Provider<Directory> output;
    @NonNull private final FileCollection inputClasses;
    @NonNull private final WorkerExecutorFacade.IsolationMode isolationMode;
    @NonNull private final Provider<Directory> outputJars;

    public JacocoTaskDelegate(
            @NonNull FileCollection jacocoAntTaskConfiguration,
            @NonNull Provider<Directory> output,
            @NonNull Provider<Directory> outputJars,
            @NonNull FileCollection inputClasses,
            @NonNull WorkerExecutorFacade.IsolationMode isolationMode) {
        this.jacocoAntTaskConfiguration = jacocoAntTaskConfiguration;
        this.output = output;
        this.outputJars = outputJars;
        this.inputClasses = inputClasses;
        this.isolationMode = isolationMode;
    }

    public static class WorkerItemParameter implements Serializable {
        final Map<Action, List<File>> nonIncToProcess;
        final File root;
        final File output;

        public WorkerItemParameter(
                Map<Action, List<File>> nonIncToProcess, File root, File output) {
            this.nonIncToProcess = nonIncToProcess;
            this.root = root;
            this.output = output;
        }
    }

    public void run(@NonNull WorkerExecutorFacade executor, @NonNull IncrementalTaskInputs inputs)
            throws IOException {

        if (inputs.isIncremental()) {
            processIncrementally(executor, inputs);
        } else {
            File outputJarsFolder = outputJars.get().getAsFile();
            FileUtils.cleanOutputDir(output.get().getAsFile());
            FileUtils.cleanOutputDir(outputJarsFolder);
            for (File file : inputClasses.getFiles()) {
                if (file.isDirectory()) {
                    Map<Action, List<File>> nonIncToProcess =
                            getFilesForInstrumentationNonIncrementally(file);
                    WorkerItemParameter parameter =
                            new WorkerItemParameter(
                                    nonIncToProcess, file, output.get().getAsFile());

                    executor.submit(
                            JacocoWorkerAction.class,
                            new WorkerExecutorFacade.Configuration(
                                    parameter,
                                    isolationMode,
                                    jacocoAntTaskConfiguration.getFiles(),
                                    ImmutableList.of()));
                } else { // We expect *.jar files here
                    if (!file.getName().endsWith(SdkConstants.DOT_JAR)) {
                        continue;
                    }
                    executor.submit(
                            JacocoJarWorkerAction.class,
                            new WorkerExecutorFacade.Configuration(
                                    new WorkerItemParameter(null, file, outputJarsFolder),
                                    isolationMode,
                                    jacocoAntTaskConfiguration.getFiles(),
                                    ImmutableList.of()));
                }
            }
        }
    }

    private void processIncrementally(
            @NonNull WorkerExecutorFacade executor, @NonNull IncrementalTaskInputs inputs)
            throws IOException {
        Multimap<Path, Path> basePathToRemove =
                Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);
        Multimap<Path, Path> basePathToProcess =
                Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

        Set<Path> baseDirs = new HashSet<>(inputClasses.getFiles().size());
        for (File file : inputClasses.getFiles()) {
            if (file.isDirectory()) {
                baseDirs.add(file.toPath());
            }
        }

        Set<File> jarsToRemove = new HashSet<>();
        Set<File> jarsToProcess = new HashSet<>();

        inputs.outOfDate(
                info -> {
                    File file = info.getFile();
                    if (file.getName().endsWith(SdkConstants.DOT_JAR)) {
                        if (info.isAdded()) {
                            jarsToProcess.add(file);
                        } else if (info.isModified()) {
                            jarsToRemove.add(file);
                            jarsToProcess.add(file);
                        } else if (info.isRemoved()) {
                            jarsToRemove.add(file);
                        }
                    } else {
                        Path filePath = file.toPath();
                        Path baseDir = findBase(baseDirs, filePath);
                        if (info.isAdded()) {
                            basePathToProcess.put(baseDir, filePath);
                        } else if (info.isModified()) {
                            basePathToRemove.put(baseDir, filePath);
                            basePathToProcess.put(baseDir, filePath);
                        } else if (info.isRemoved()) {
                            basePathToRemove.put(baseDir, filePath);
                        }
                    }
                });
        inputs.removed(
                info -> {
                    File file = info.getFile();
                    if (file.getName().endsWith(SdkConstants.DOT_JAR)) {
                        jarsToRemove.add(file);
                    } else {
                        Path filePath = file.toPath();
                        Path baseDir = findBase(baseDirs, filePath);
                        basePathToRemove.put(baseDir, filePath);
                    }
                });

        // remove old output
        for (Path basePath : basePathToRemove.keys()) {
            for (Path toRemove : basePathToRemove.get(basePath)) {
                Action action = calculateAction(toRemove.toFile(), basePath.toFile());
                if (action == Action.IGNORE) {
                    continue;
                }

                Path outputPath =
                        getOutputPath(basePath, toRemove, output.get().getAsFile().toPath());
                PathUtils.deleteRecursivelyIfExists(outputPath);
            }
        }

        File outputJarsFolder = outputJars.get().getAsFile();
        for (File jarToRemove : jarsToRemove) {
            File instrumentedJar = getCorrespondingInstrumentedJar(outputJarsFolder, jarToRemove);
            FileUtils.delete(instrumentedJar);
        }

        // process changes
        for (Path basePath : basePathToProcess.keySet()) {
            Map<Action, List<File>> toProcess = new EnumMap<>(Action.class);
            for (Path changed : basePathToProcess.get(basePath)) {
                Action action = calculateAction(changed.toFile(), basePath.toFile());
                if (action == Action.IGNORE) {
                    continue;
                }

                List<File> byAction = toProcess.getOrDefault(action, new ArrayList<>());
                byAction.add(changed.toFile());
                toProcess.put(action, byAction);
            }

            executor.submit(
                    JacocoWorkerAction.class,
                    new WorkerExecutorFacade.Configuration(
                            new WorkerItemParameter(
                                    toProcess, basePath.toFile(), output.get().getAsFile()),
                            isolationMode,
                            jacocoAntTaskConfiguration.getFiles(),
                            ImmutableList.of()));
        }

        for (File jarToProcess : jarsToProcess) {
            executor.submit(
                    JacocoJarWorkerAction.class,
                    new WorkerExecutorFacade.Configuration(
                            new WorkerItemParameter(null, jarToProcess, outputJarsFolder),
                            isolationMode,
                            jacocoAntTaskConfiguration.getFiles(),
                            ImmutableList.of()));
        }
    }

    @NonNull
    private static Path findBase(@NonNull Set<Path> baseDirs, @NonNull Path file) {
        for (Path baseDir : baseDirs) {
            if (file.startsWith(baseDir)) {
                return baseDir;
            }
        }

        throw new RuntimeException(
                String.format(
                        "Unable to find base directory for %s. List of base dirs: %s",
                        file,
                        baseDirs.stream().map(Path::toString).collect(Collectors.joining(","))));
    }

    @NonNull
    private static Path getOutputPath(
            @NonNull Path baseDir, @NonNull Path inputFile, @NonNull Path outputBaseDir) {
        Path relativePath = baseDir.relativize(inputFile);
        return outputBaseDir.resolve(relativePath);
    }

    @NonNull
    private static Map<Action, List<File>> getFilesForInstrumentationNonIncrementally(
            @NonNull File inputDir) {
        Map<Action, List<File>> toProcess = Maps.newHashMap();
        Iterable<File> files = FileUtils.getAllFiles(inputDir);
        for (File inputFile : files) {
            Action fileAction = calculateAction(inputFile, inputDir);
            switch (fileAction) {
                case COPY:
                    // fall through
                case INSTRUMENT:
                    List<File> actionFiles = toProcess.getOrDefault(fileAction, new ArrayList<>());
                    actionFiles.add(inputFile);
                    toProcess.put(fileAction, actionFiles);
                    break;
                case IGNORE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError("Unsupported Action: " + fileAction);
            }
        }
        return toProcess;
    }

    private static Action calculateAction(@NonNull File inputFile, @NonNull File inputDir) {
        final String inputRelativePath =
                FileUtils.toSystemIndependentPath(
                        FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
        return calculateAction(inputRelativePath);
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
    private enum Action {

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

    private static class JacocoWorkerAction implements Runnable {
        @NonNull
        private static final LoggerWrapper logger =
                LoggerWrapper.getLogger(JacocoWorkerAction.class);

        @NonNull private Map<Action, List<File>> inputs;
        @NonNull private File inputDir;
        @NonNull private File outputDir;

        @Inject
        public JacocoWorkerAction(@NonNull WorkerItemParameter workerItemParameter) {
            this.inputs = workerItemParameter.nonIncToProcess;
            this.inputDir = workerItemParameter.root;
            this.outputDir = workerItemParameter.output;
        }

        @Override
        public void run() {
            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            logger.info("Processing entries from input dir: " + inputDir.getAbsolutePath());
            for (File toInstrument : inputs.getOrDefault(Action.INSTRUMENT, ImmutableList.of())) {
                logger.info("Instrumenting file: " + toInstrument.getAbsolutePath());
                try (InputStream inputStream =
                        Files.asByteSource(toInstrument).openBufferedStream()) {
                    byte[] instrumented =
                            instrumenter.instrument(inputStream, toInstrument.toString());
                    File outputFile =
                            new File(outputDir, FileUtils.relativePath(toInstrument, inputDir));
                    Files.createParentDirs(outputFile);
                    Files.write(instrumented, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Unable to instrument file with Jacoco: " + toInstrument, e);
                }
            }

            for (File toCopy : inputs.getOrDefault(Action.COPY, ImmutableList.of())) {
                File outputFile = new File(outputDir, FileUtils.relativePath(toCopy, inputDir));
                try {
                    Files.createParentDirs(outputFile);
                    Files.copy(toCopy, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to copy file: " + toCopy, e);
                }
            }
        }
    }

    private static class JacocoJarWorkerAction implements Runnable {
        @NonNull private File inputJar;
        @NonNull private File outputDir;

        @Inject
        public JacocoJarWorkerAction(@NonNull WorkerItemParameter workerItemParameter) {
            this.inputJar = workerItemParameter.root;
            this.outputDir = workerItemParameter.output;
        }

        @Override
        public void run() {
            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            File instrumentedJar = getCorrespondingInstrumentedJar(outputDir, inputJar);
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
            @NonNull File outputFolder, @NonNull File file) {
        return new File(
                outputFolder,
                Hashing.sha256()
                                .hashBytes(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8))
                                .toString()
                        + SdkConstants.DOT_JAR);
    }
}
