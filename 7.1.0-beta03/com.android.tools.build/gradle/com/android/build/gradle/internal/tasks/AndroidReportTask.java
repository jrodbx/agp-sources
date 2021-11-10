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

package com.android.build.gradle.internal.tasks;

import static com.android.builder.core.BuilderConstants.CONNECTED;
import static com.android.builder.core.BuilderConstants.DEVICE;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS;
import static com.android.builder.core.BuilderConstants.FD_FLAVORS_ALL;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.internal.test.report.CompositeTestResults;
import com.android.build.gradle.internal.test.report.ReportType;
import com.android.build.gradle.internal.test.report.TestReport;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import com.android.builder.core.VariantType;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.work.DisableCachingByDefault;

/** Task doing test report aggregation. */
@DisableCachingByDefault
public abstract class AndroidReportTask extends DefaultTask implements AndroidTestTask {

    private final transient List<AndroidTestTask> subTasks = Lists.newArrayList();

    private final List<File> resultsDirectories = Lists.newArrayList();

    private ReportType reportType;

    private boolean ignoreFailures;

    private boolean testFailed;

    @OutputDirectory
    public abstract DirectoryProperty getReportsDir();

    @Override
    @OutputDirectory
    public abstract DirectoryProperty getResultsDir();

    @Override
    @Internal // This is an output, not an input of this task
    public boolean getTestFailed() {
        return testFailed;
    }

    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    @Input
    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public void addTask(AndroidTestTask task) {
        subTasks.add(task);
        resultsDirectories.add(task.getResultsDir().get().getAsFile());
        this.dependsOn(task);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getResultsDirectories() {
        return resultsDirectories;
    }

    /**
     * Sets that this current task will run and therefore needs to tell its children
     * class to not stop on failures.
     */
    public void setWillRun() {
        for (AndroidTestTask task : subTasks) {
            task.setIgnoreFailures(true);
        }
    }

    @TaskAction
    public void createReport() throws IOException {
        File resultsOutDir = getResultsDir().get().getAsFile();
        File reportOutDir = getResultsDir().get().getAsFile();

        // empty the folders
        FileUtils.cleanOutputDir(resultsOutDir);
        FileUtils.cleanOutputDir(reportOutDir);

        // do the copy.
        copyResults(resultsOutDir);

        // create the report.
        TestReport report = new TestReport(reportType, resultsOutDir, reportOutDir);
        CompositeTestResults compositeTestResults = report.generateReport();

        if (!compositeTestResults.getFailures().isEmpty()) {
            testFailed = true;
            String reportUrl =
                    new ConsoleRenderer().asClickableFileUrl(new File(reportOutDir, "index.html"));
            String message = "There were failing tests. See the report at: " + reportUrl;

            if (getIgnoreFailures()) {
                getLogger().warn(message);
            } else {
                throw new GradleException(message);
            }
        }
    }

    private void copyResults(File reportOutDir) throws IOException {
        List<File> resultDirectories = getResultsDirectories();

        for (File directory : resultDirectories) {
            FileUtils.copyDirectory(directory, reportOutDir);
        }
    }

    public static class CreationAction extends TaskCreationAction<AndroidReportTask> {

        public enum TaskKind { CONNECTED, DEVICE_PROVIDER }

        private final GlobalScope scope;

        private final TaskKind taskKind;

        private final ProjectInfo projectInfo;

        public CreationAction(
                @NonNull GlobalScope scope,
                @NonNull TaskKind taskKind,
                @NonNull ProjectInfo projectInfo) {
            this.scope = scope;
            this.taskKind = taskKind;
            this.projectInfo = projectInfo;
        }

        @NonNull
        @Override
        public String getName() {
            return (taskKind == TaskKind.CONNECTED ? CONNECTED : DEVICE)
                    + VariantType.ANDROID_TEST_SUFFIX;
        }

        @NonNull
        @Override
        public Class<AndroidReportTask> getType() {
            return AndroidReportTask.class;
        }

        @Override
        public void configure(@NonNull AndroidReportTask task) {

            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            task.setDescription((taskKind == TaskKind.CONNECTED) ?
                    "Installs and runs instrumentation tests for all flavors on connected devices.":
                    "Installs and runs instrumentation tests using all Device Providers.");
            task.setReportType(ReportType.MULTI_FLAVOR);

            final String defaultReportsDir =
                    projectInfo.getReportsDir().getAbsolutePath() + "/" + FD_ANDROID_TESTS;
            final String defaultResultsDir =
                    projectInfo.getOutputsDir().getAbsolutePath() + "/" + FD_ANDROID_RESULTS;

            final String subfolderName =
                    taskKind == TaskKind.CONNECTED ? "/connected/" : "/devices/";

            HasConfigurableValuesKt.setDisallowChanges(
                    task.getResultsDir(),
                    task.getProject()
                            .provider(
                                    () -> {
                                        String dir =
                                                scope.getExtension()
                                                        .getTestOptions()
                                                        .getResultsDir();
                                        String rootLocation =
                                                dir != null && !dir.isEmpty()
                                                        ? dir
                                                        : defaultResultsDir;
                                        return projectInfo
                                                .getProject()
                                                .getLayout()
                                                .getProjectDirectory()
                                                .dir(rootLocation + subfolderName + FD_FLAVORS_ALL);
                                    }));

            HasConfigurableValuesKt.setDisallowChanges(
                    task.getReportsDir(),
                    task.getProject()
                            .provider(
                                    () -> {
                                        String dir =
                                                scope.getExtension()
                                                        .getTestOptions()
                                                        .getReportDir();
                                        String rootLocation =
                                                dir != null && !dir.isEmpty()
                                                        ? dir
                                                        : defaultReportsDir;
                                        return projectInfo
                                                .getProject()
                                                .getLayout()
                                                .getProjectDirectory()
                                                .dir(rootLocation + subfolderName + FD_FLAVORS_ALL);
                                    }));
        }
    }
}
