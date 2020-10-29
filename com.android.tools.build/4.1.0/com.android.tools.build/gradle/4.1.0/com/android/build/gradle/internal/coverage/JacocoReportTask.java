/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.internal.coverage;


import com.android.Version;
import com.android.annotations.NonNull;
import com.android.build.api.component.impl.TestComponentPropertiesImpl;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.NonIncrementalTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

/** Simple Jacoco report task that calls the Ant version. */
public abstract class JacocoReportTask extends NonIncrementalTask {

    private FileCollection jacocoClasspath;

    private FileCollection classFileCollection;
    private FileCollection sourceFolders;

    private File reportDir;
    private String reportName;

    private int tabWidth = 4;

    @Deprecated
    public void setCoverageFile(File coverageFile) {
        getLogger().info("JacocoReportTask.setCoverageDir is deprecated and has no effect.");
    }

    // PathSensitivity.NONE since only the contents of the files under the directory matter as input
    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract DirectoryProperty getCoverageDirectories();

    @OutputDirectory
    public File getReportDir() {
        return reportDir;
    }

    public void setReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    @Classpath
    public FileCollection getClassFileCollection() {
        return classFileCollection;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getSourceFolders() {
        return sourceFolders;
    }

    @Input
    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    @Classpath
    public FileCollection getJacocoClasspath() {
        return jacocoClasspath;
    }

    public void setJacocoClasspath(FileCollection jacocoClasspath) {
        this.jacocoClasspath = jacocoClasspath;
    }

    @Input
    public int getTabWidth() {
        return tabWidth;
    }

    public void setTabWidth(int tabWidth) {
        this.tabWidth = tabWidth;
    }

    @Override
    protected void doTaskAction() throws IOException {
        Set<File> coverageFiles =
                getCoverageDirectories()
                        .get()
                        .getAsFileTree()
                        .getFiles()
                        .stream()
                        .filter(File::isFile)
                        .collect(Collectors.toSet());

        if (coverageFiles.isEmpty()) {
            throw new IOException(
                    String.format(
                            "No coverage data to process in directories [%1$s]",
                            getCoverageDirectories().get().getAsFile().getAbsolutePath()));
        }

        getWorkerExecutor()
                .classLoaderIsolation(
                        classpath -> {
                            classpath.getClasspath().from(jacocoClasspath.getFiles());
                        })
                .submit(
                        JacocoReportWorkerAction.class,
                        params -> {
                            params.getCoverageFiles().set(coverageFiles);
                            params.getReportDir().set(getReportDir());
                            params.getClassFolders().set(getClassFileCollection().getFiles());
                            params.getSourceFolders().set(getSourceFolders().getFiles());
                            params.getTabWidth().set(getTabWidth());
                            params.getReportName().set(getReportName());
                        });
    }

    public static class CreationAction
            extends VariantTaskCreationAction<JacocoReportTask, TestComponentPropertiesImpl> {
        @NonNull private final Configuration jacocoAntConfiguration;

        public CreationAction(
                @NonNull TestComponentPropertiesImpl testComponentProperties,
                @NonNull Configuration jacocoAntConfiguration) {
            super(testComponentProperties);
            this.jacocoAntConfiguration = jacocoAntConfiguration;
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName("create", "CoverageReport");
        }

        @NonNull
        @Override
        public Class<JacocoReportTask> getType() {
            return JacocoReportTask.class;
        }

        @Override
        public void handleProvider(@NonNull TaskProvider<JacocoReportTask> taskProvider) {
            super.handleProvider(taskProvider);
            creationConfig.getTaskContainer().setCoverageReportTask(taskProvider);
        }

        @Override
        public void configure(@NonNull JacocoReportTask task) {
            super.configure(task);

            task.setDescription("Creates JaCoCo test coverage report from data gathered on the "
                    + "device.");

            task.setReportName(creationConfig.getName());

            VariantPropertiesImpl testedVariant = creationConfig.getTestedVariant();

            task.jacocoClasspath = jacocoAntConfiguration;

            creationConfig
                    .getArtifacts()
                    .setTaskInputToFinalProduct(
                            InternalArtifactType.CODE_COVERAGE.INSTANCE,
                            task.getCoverageDirectories());

            task.classFileCollection = testedVariant.getArtifacts().getAllClasses();

            task.sourceFolders =
                    creationConfig
                            .getServices()
                            .fileCollection(
                                    (Callable<List<ConfigurableFileTree>>)
                                            testedVariant::getJavaSources);

            task.setReportDir(testedVariant.getPaths().getCoverageReportDir());
        }
    }

    interface JacocoWorkParameters extends WorkParameters {
        SetProperty<File> getCoverageFiles();

        DirectoryProperty getReportDir();

        SetProperty<File> getClassFolders();

        SetProperty<File> getSourceFolders();

        Property<Integer> getTabWidth();

        Property<String> getReportName();
    }

    abstract static class JacocoReportWorkerAction implements WorkAction<JacocoWorkParameters> {
        private static final Logger logger = Logging.getLogger(JacocoReportWorkerAction.class);

        @Inject
        public JacocoReportWorkerAction() {}

        @Override
        public void execute() {
            try {
                generateReport(
                        getParameters().getCoverageFiles().get(),
                        getParameters().getReportDir().getAsFile().get(),
                        getParameters().getClassFolders().get(),
                        getParameters().getSourceFolders().get(),
                        getParameters().getTabWidth().get(),
                        getParameters().getReportName().get());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to generate Jacoco report", e);
            }
        }

        @VisibleForTesting
        static void generateReport(
                @NonNull Collection<File> coverageFiles,
                @NonNull File reportDir,
                @NonNull Collection<File> classFolders,
                @NonNull Collection<File> sourceFolders,
                int tabWidth,
                @NonNull String reportName)
                throws IOException {
            // Load data
            final ExecFileLoader loader = new ExecFileLoader();
            for (File coverageFile : coverageFiles) {
                loader.load(coverageFile);
            }

            SessionInfoStore sessionInfoStore = loader.getSessionInfoStore();
            ExecutionDataStore executionDataStore = loader.getExecutionDataStore();

            // Initialize report generator.
            HTMLFormatter htmlFormatter = new HTMLFormatter();
            htmlFormatter.setOutputEncoding("UTF-8");
            htmlFormatter.setLocale(Locale.US);
            htmlFormatter.setFooterText(
                    "Generated by the Android Gradle plugin "
                            + Version.ANDROID_GRADLE_PLUGIN_VERSION);

            FileMultiReportOutput output = new FileMultiReportOutput(reportDir);
            IReportVisitor htmlReport = htmlFormatter.createVisitor(output);

            XMLFormatter xmlFormatter = new XMLFormatter();
            xmlFormatter.setOutputEncoding("UTF-8");
            OutputStream xmlReportOutput = output.createFile("report.xml");
            try {
                IReportVisitor xmlReport = xmlFormatter.createVisitor(xmlReportOutput);

                final IReportVisitor visitor =
                        new MultiReportVisitor(ImmutableList.of(htmlReport, xmlReport));

                // Generate report
                visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());

                final CoverageBuilder builder = new CoverageBuilder();
                final Analyzer analyzer = new Analyzer(executionDataStore, builder);

                analyzeAll(analyzer, classFolders);

                MultiSourceFileLocator locator = new MultiSourceFileLocator(0);
                for (File folder : sourceFolders) {
                    locator.add(new DirectorySourceFileLocator(folder, "UTF-8", tabWidth));
                }

                final IBundleCoverage bundle = builder.getBundle(reportName);
                visitor.visitBundle(bundle, locator);
                visitor.visitEnd();
            } finally {
                try {
                    xmlReportOutput.close();
                } catch (IOException e) {
                    logger.error("Could not close xml report file", e);
                }
            }
        }

        private static void analyzeAll(
                @NonNull Analyzer analyzer, @NonNull Collection<File> classFolders)
                throws IOException {
            for (final File folder : classFolders) {
                analyze(analyzer, folder, classFolders);
            }
        }

        /**
         * Analyzes code coverage on file if it's a class file, or recursively analyzes descendants
         * if file is a folder.
         *
         * @param analyzer Jacoco Analyzer
         * @param file a file or folder
         * @param originalClassFolders the original collection of class folders to be analyzed;
         *     e.g., this.classFileCollection.getFiles(). This parameter is included to avoid
         *     redundant computation in the case when one of the original class folders is a
         *     descendant of another.
         */
        private static void analyze(
                @NonNull Analyzer analyzer,
                @NonNull File file,
                @NonNull Collection<File> originalClassFolders)
                throws IOException {
            if (file.isDirectory()) {
                final File[] files = file.listFiles();
                if (files != null) {
                    for (final File f : files) {
                        // check that f is not in originalClassFolders to avoid redundant
                        // computation
                        if (!originalClassFolders.contains(f)) {
                            analyze(analyzer, f, originalClassFolders);
                        }
                    }
                }
            } else {
                String name = file.getName();
                if (!name.endsWith(".class")
                        || name.equals("R.class")
                        || name.startsWith("R$")
                        || name.equals("Manifest.class")
                        || name.startsWith("Manifest$")
                        || name.equals("BuildConfig.class")) {
                    return;
                }

                InputStream in = new FileInputStream(file);
                try {
                    analyzer.analyzeClass(in, file.getAbsolutePath());
                } finally {
                    Closeables.closeQuietly(in);
                }
            }
        }
    }
}
