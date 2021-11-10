/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.plugins;

import static com.android.builder.core.BuilderConstants.FD_ANDROID_RESULTS;
import static com.android.builder.core.BuilderConstants.FD_ANDROID_TESTS;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;

import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.errors.DeprecationReporterImpl;
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl;
import com.android.build.gradle.internal.lint.LintFromMaven;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.internal.services.DslServicesImpl;
import com.android.build.gradle.internal.services.ProjectServices;
import com.android.build.gradle.internal.tasks.AndroidReportTask;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.test.report.ReportType;
import com.android.build.gradle.options.ProjectOptionService;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.utils.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.tasks.TaskCollection;

/**
 * Gradle plugin class for 'reporting' projects.
 *
 * This is mostly used to aggregate reports from subprojects.
 *
 */
class ReportingPlugin implements org.gradle.api.Plugin<Project> {

    private TestOptions extension;

    public ReportingPlugin() {}

    @Override
    public void apply(final Project project) {
        // make sure this project depends on the evaluation of all sub projects so that
        // it's evaluated last.
        project.evaluationDependsOnChildren();

        ProjectOptions projectOptions =
                new ProjectOptionService.RegistrationAction(project)
                        .execute()
                        .get()
                        .getProjectOptions();

        SyncIssueReporterImpl syncIssueHandler =
                new SyncIssueReporterImpl(
                        SyncOptions.getModelQueryMode(projectOptions),
                        SyncOptions.getErrorFormatMode(projectOptions),
                        project.getLogger());

        DeprecationReporterImpl deprecationReporter =
                new DeprecationReporterImpl(syncIssueHandler, projectOptions, project.getPath());
        LintFromMaven lintFromMaven = LintFromMaven.from(project, projectOptions, syncIssueHandler);

        ProjectServices projectServices =
                new ProjectServices(
                        syncIssueHandler,
                        deprecationReporter,
                        project.getObjects(),
                        project.getLogger(),
                        project.getProviders(),
                        project.getLayout(),
                        projectOptions,
                        project.getGradle().getSharedServices(),
                        lintFromMaven,
                        null,
                        project.getGradle().getStartParameter().getMaxWorkerCount(),
                        new ProjectInfo(project),
                        project::file);

        DslServices dslServices =
                new DslServicesImpl(
                        projectServices,
                        project.getProviders().provider(() -> null));

        extension = project.getExtensions().create("android", TestOptions.class, dslServices);

        final AndroidReportTask mergeReportsTask = project.getTasks().create("mergeAndroidReports",
                AndroidReportTask.class);
        mergeReportsTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        mergeReportsTask.setDescription("Merges all the Android test reports from the sub "
                + "projects.");
        mergeReportsTask.setReportType(ReportType.MULTI_PROJECT);

        mergeReportsTask
                .getResultsDir()
                .set(
                        project.provider(
                                () -> {
                                    String resultsDir = extension.getResultsDir();
                                    if (resultsDir == null) {
                                        return project.getLayout()
                                                .getBuildDirectory()
                                                .dir(FD_ANDROID_RESULTS)
                                                .get();
                                    } else {
                                        return project.getLayout()
                                                .getProjectDirectory()
                                                .dir(resultsDir);
                                    }
                                }));

        mergeReportsTask
                .getReportsDir()
                .set(
                        project.provider(
                                () -> {
                                    String reportsDir = extension.getReportDir();
                                    if (reportsDir == null) {
                                        return project.getLayout()
                                                .getBuildDirectory()
                                                .dir(FileUtils.join(FD_REPORTS, FD_ANDROID_TESTS))
                                                .get();
                                    } else {
                                        return project.getLayout()
                                                .getProjectDirectory()
                                                .dir(reportsDir);
                                    }
                                }));

        // gather the subprojects
        project.afterEvaluate(prj -> {
            for (Project p : prj.getSubprojects()) {
                TaskCollection<AndroidReportTask> tasks = p.getTasks().withType(
                        AndroidReportTask.class);
                for (AndroidReportTask task : tasks) {
                    mergeReportsTask.addTask(task);
                }

                TaskCollection<DeviceProviderInstrumentTestTask> tasks2 =
                        p.getTasks().withType(DeviceProviderInstrumentTestTask.class);
                for (DeviceProviderInstrumentTestTask task : tasks2) {
                    mergeReportsTask.addTask(task);
                }
            }
        });

        // If gradle is launched with --continue, we want to run all tests and generate an
        // aggregate report (to help with the fact that we may have several build variants).
        // To do that, the "mergeAndroidReports" task (which does the aggregation) must always
        // run even if one of its dependent task (all the testFlavor tasks) fails, so we make
        // them ignore their error.
        // We cannot do that always: in case the test task is not going to run, we do want the
        // individual testFlavor tasks to fail.
        if (project.getGradle().getStartParameter().isContinueOnFailure()) {
            project.getGradle().getTaskGraph().whenReady(taskExecutionGraph -> {
                if (taskExecutionGraph.hasTask(mergeReportsTask)) {
                    mergeReportsTask.setWillRun();
                }
            });
        }
    }
}
