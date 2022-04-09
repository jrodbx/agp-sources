/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.plugins

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Lint
import com.android.build.api.extension.impl.DslLifecycleComponentsOperationsRegistrar
import com.android.build.gradle.LintLifecycleExtensionImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule
import com.android.build.gradle.internal.dsl.LintImpl
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalSyncService
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintCopyReportTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.lint.LintMode
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.FakeDependencyJarBuildService
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Category
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

/**
 * Plugin for running lint **without** the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
abstract class LintPlugin : Plugin<Project> {
    private lateinit var projectServices: ProjectServices
    private lateinit var dslServices: DslServicesImpl
    private var lintOptions: Lint? = null

    @get:Inject
    abstract val listenerRegistry: BuildEventsListenerRegistry

    override fun apply(project: Project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true")

        createProjectServices(project)

        dslServices = DslServicesImpl(
            projectServices,
            project.providers.provider { null }
        )

        val dslOperationsRegistrar = createExtension(project, dslServices)
        withJavaPlugin(project) { registerTasks(project, dslOperationsRegistrar) }
    }
    private fun registerTasks(project: Project, dslOperationsRegistrar: DslLifecycleComponentsOperationsRegistrar<Lint>) {
        val javaExtension: JavaPluginExtension = getJavaPluginExtension(project) ?: return
        val customLintChecksConfig = BasePlugin.createCustomLintChecksConfig(project)
        val customLintChecks = getLocalCustomLintChecks(customLintChecksConfig)
        registerTasks(
            project,
            javaExtension,
            customLintChecks,
            dslOperationsRegistrar,
        )
        ModelArtifactCompatibilityRule.setUp(project.dependencies.attributesSchema)
    }

    private fun registerTasks(
        project: Project,
        javaExtension: JavaPluginExtension,
        customLintChecks: FileCollection,
        dslOperationsRegistrar: DslLifecycleComponentsOperationsRegistrar<Lint>,
    ) {
        registerBuildServices(project)
        val artifacts = ArtifactsImpl(project, "global")
        val taskCreationServices: TaskCreationServices = TaskCreationServicesImpl(projectServices)
        // Create the 'lint' task before afterEvaluate to avoid breaking existing build scripts that
        // expect it to be present during evaluation
        val lintTask = project.tasks.register("lint", AndroidLintTextOutputTask::class.java)
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME).configure { t: Task -> t.dependsOn(lintTask) }

        // Avoid reading the lintOptions DSL and build directory before the build author can customize them
        project.afterEvaluate {
            dslOperationsRegistrar.executeDslFinalizationBlocks()

            lintTask.configure { task ->
                task.configureForStandalone(taskCreationServices, artifacts, lintOptions!!)
            }
            val updateLintBaselineTask =
                project.tasks.register("updateLintBaseline", AndroidLintTask::class.java) { task ->
                    task.description = "Updates the lint baseline for project `${project.name}`."
                    task.configureForStandalone(
                        taskCreationServices,
                        javaExtension,
                        customLintChecks,
                        lintOptions!!,
                        artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                        artifacts.getOutputPath(InternalArtifactType.LINT_MODEL),
                        LintMode.UPDATE_BASELINE
                    )
                }
            project.tasks.register("lintReport", AndroidLintTask::class.java) { task ->
                task.description = "Generates the lint report for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_MODEL),
                    LintMode.REPORTING
                )
                task.mustRunAfter(updateLintBaselineTask)
            }.also {
                AndroidLintTask.VariantCreationAction.registerLintIntermediateArtifacts(
                    it,
                    artifacts
                )
                AndroidLintTask.SingleVariantCreationAction.registerLintReportArtifacts(
                    it,
                    artifacts,
                    null,
                    project.buildDir.resolve("reports")
                )
            }

            project.tasks.register("lintVital", AndroidLintTextOutputTask::class.java) { task ->
                task.configureForStandalone(
                    taskCreationServices,
                    artifacts,
                    lintOptions!!,
                    fatalOnly = true
                )
            }
            project.tasks.register("lintVitalReport", AndroidLintTask::class.java) { task ->
                task.description =
                    "Generates the lint report for just the fatal issues for project  `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_VITAL_LINT_MODEL),
                    LintMode.REPORTING,
                    fatalOnly = true
                )
                task.mustRunAfter(updateLintBaselineTask)
            }.also {
                AndroidLintTask.VariantCreationAction.registerLintIntermediateArtifacts(
                    it,
                    artifacts,
                    fatalOnly = true
                )
            }

            project.tasks.register("lintFix", AndroidLintTask::class.java) { task ->
                task.description = "Generates the lint report for project `${project.name}` and applies any safe suggestions to the source code."
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_MODEL),
                    LintMode.REPORTING,
                    autoFix = true
                )
                task.mustRunAfter(updateLintBaselineTask)
            }

            val lintAnalysisTask = project.tasks.register("lintAnalyze", AndroidLintAnalysisTask::class.java) { task ->
                task.description = "Runs lint analysis for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!
                )
            }
            AndroidLintAnalysisTask.registerOutputArtifacts(
                lintAnalysisTask,
                InternalArtifactType.LINT_PARTIAL_RESULTS,
                artifacts
            )
            val lintVitalAnalysisTask = project.tasks.register("lintVitalAnalyze", AndroidLintAnalysisTask::class.java) { task ->
                task.description =
                    "Runs lint analysis on just the fatal issues for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    fatalOnly = true
                )
            }
            AndroidLintAnalysisTask.registerOutputArtifacts(
                lintVitalAnalysisTask,
                InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                artifacts
            )
            val lintModelWriterTask = project.tasks.register("generateLintModel", LintModelWriterTask::class.java) { task ->
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    lintOptions!!,
                    artifacts.getOutputPath(
                        InternalArtifactType.LINT_PARTIAL_RESULTS,
                        AndroidLintAnalysisTask.PARTIAL_RESULTS_DIR_NAME
                    ),
                    fatalOnly = false
                )
            }
            LintModelWriterTask.BaseCreationAction
                .registerOutputArtifacts(
                    lintModelWriterTask,
                    InternalArtifactType.LINT_MODEL,
                    artifacts
                )
            val lintVitalModelWriterTask =
                project.tasks.register(
                    "generateLintVitalLintModel",
                    LintModelWriterTask::class.java
                ) { task ->
                    task.configureForStandalone(
                        taskCreationServices,
                        javaExtension,
                        lintOptions!!,
                        artifacts.getOutputPath(
                            InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                            AndroidLintAnalysisTask.PARTIAL_RESULTS_DIR_NAME
                        ),
                        fatalOnly = true
                    )
                }
            LintModelWriterTask.BaseCreationAction
                .registerOutputArtifacts(
                    lintVitalModelWriterTask,
                    InternalArtifactType.LINT_VITAL_LINT_MODEL,
                    artifacts
                )
            val lintModelMetadataWriterTask =
                project.tasks
                    .register("writeLintModelMetadata", LintModelMetadataTask::class.java) { task ->
                        task.configureForStandalone(project)
                    }
            LintModelMetadataTask.registerOutputArtifacts(lintModelMetadataWriterTask, artifacts)
            if (LintTaskManager.needsCopyReportTask(lintOptions!!)) {
                val copyLintReportsTask =
                    project.tasks.register(
                        "copyAndroidLintReports",
                        AndroidLintCopyReportTask::class.java
                    ) { task ->
                        task.configureForStandalone(artifacts, lintOptions!!)
                    }
                lintTask.configure { it.finalizedBy(copyLintReportsTask) }
            }
        }

        javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) { mainSourceSet ->
            listOf(
                mainSourceSet.runtimeElementsConfigurationName,
                mainSourceSet.apiElementsConfigurationName
            ).forEach { configurationName ->
                project.configurations.getByName(configurationName) { configuration ->
                    val androidLintCategory =
                        projectServices.objectFactory.named(Category::class.java, "android-lint")
                    publishArtifactToConfiguration(
                        configuration,
                        artifacts.get(InternalArtifactType.LINT_MODEL),
                        AndroidArtifacts.ArtifactType.LINT_MODEL,
                        AndroidAttributes(category = androidLintCategory)
                    )
                    publishArtifactToConfiguration(
                        configuration,
                        artifacts.get(InternalArtifactType.LINT_VITAL_LINT_MODEL),
                        AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL,
                        AndroidAttributes(category = androidLintCategory)
                    )
                    publishArtifactToConfiguration(
                        configuration,
                        artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                        AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS,
                        AndroidAttributes(category = androidLintCategory)
                    )
                    publishArtifactToConfiguration(
                        configuration,
                        artifacts.get(InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS),
                        AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                        AndroidAttributes(category = androidLintCategory)
                    )
                    publishArtifactToConfiguration(
                        configuration,
                        artifacts.get(InternalArtifactType.LINT_MODEL_METADATA),
                        AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA,
                        AndroidAttributes(category = androidLintCategory)
                    )
                    // We don't want to publish the lint models or partial results to repositories.
                    // Remove them.
                    project.components.all { component: SoftwareComponent ->
                        if (component.name == "java" && component is AdhocComponentWithVariants) {
                            component.withVariantsFromConfiguration(configuration) { variant: ConfigurationVariantDetails ->
                                val category =
                                    variant.configurationVariant.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)
                                if (category == androidLintCategory) {
                                    variant.skip()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createExtension(
        project: Project,
        dslServices: DslServicesImpl
    ): DslLifecycleComponentsOperationsRegistrar<Lint> {
        val lintImplClass = androidPluginDslDecorator.decorate(LintImpl::class.java)
        val lintOptions = project.extensions.create(Lint::class.java, "lint", lintImplClass, dslServices)
        this.lintOptions = lintOptions
        val decoratedLintOptionsClass =
            androidPluginDslDecorator.decorate(LintOptions::class.java)
        // create the old lintOptions DSL that just delegates to the new one anyway.
        project.extensions.create("lintOptions", decoratedLintOptionsClass, dslServices, lintOptions)
        val dslOperationsRegistrar= DslLifecycleComponentsOperationsRegistrar<Lint>(lintOptions)
        project.extensions.create(
            "lintLifecycle",
            LintLifecycleExtensionImpl::class.java,
            dslOperationsRegistrar
        )
        return dslOperationsRegistrar
    }

    private fun withJavaPlugin(project: Project, action: Action<Plugin<*>>) {
        project.plugins.withType(JavaBasePlugin::class.java, action)
    }

    private fun getJavaPluginExtension(project: Project): JavaPluginExtension? {
        val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
        if (javaExtension == null) {
            project.logger.warn("Cannot apply lint if the java or kotlin Gradle plugins " +
                "have not also been applied")
        }
        return javaExtension
    }

    // See BasePlugin
    private fun createProjectServices(project: Project) {
        val objectFactory = project.objects
        val logger = project.logger
        val projectPath = project.path
        val projectOptions = ProjectOptionService.RegistrationAction(project).execute().get()
            .projectOptions
        val syncIssueReporter =
                SyncIssueReporterImpl(
                        SyncOptions.getModelQueryMode(projectOptions),
                        SyncOptions.getErrorFormatMode(projectOptions),
                        logger)
        val deprecationReporter =
            DeprecationReporterImpl(syncIssueReporter, projectOptions, projectPath)
        val projectInfo = ProjectInfo(project)
        val lintFromMaven = LintFromMaven.from(project, projectOptions, syncIssueReporter)
        projectServices = ProjectServices(
            syncIssueReporter,
            deprecationReporter,
            objectFactory,
            project.logger,
            project.providers,
            project.layout,
            projectOptions,
            project.gradle.sharedServices,
            lintFromMaven,
            maxWorkerCount = project.gradle.startParameter.maxWorkerCount,
            projectInfo = projectInfo,
            fileResolver = { o: Any -> project.file(o) },
            configurationContainer = project.configurations,
            dependencyHandler = project.dependencies,
            extraProperties = project.extensions.extraProperties
        )
        projectOptions
            .allOptions
            .forEach { (option: Option<*>, value: Any) ->
                projectServices.deprecationReporter.reportOptionIssuesIfAny(option, value)
            }
    }

    private fun registerBuildServices(project: Project) {
        val projectOptions: ProjectOptions = projectServices.projectOptions
        if (projectOptions.isAnalyticsEnabled) {
            val configuratorService =
                AnalyticsConfiguratorService.RegistrationAction(project).execute().get()
            configuratorService.getProjectBuilder(project.path)?.let {
                it.setAndroidPluginVersion(ANDROID_GRADLE_PLUGIN_VERSION)
                    .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                    .setOptions(AnalyticsUtil.toProto(projectServices.projectOptions))
            }
            AnalyticsService.RegistrationAction(project, configuratorService, listenerRegistry)
                .execute()

        } else {
            NoOpAnalyticsService.RegistrationAction(project).execute()
        }

        val stringCachingService = StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()
        LibraryDependencyCacheBuildService.RegistrationAction(
            project,
            mavenCoordinatesCacheBuildService
        ).execute()
        GlobalSyncService.RegistrationAction(project, mavenCoordinatesCacheBuildService)
            .execute()

        SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
                project,
                SyncOptions.getModelQueryMode(projectServices.projectOptions),
                SyncOptions.getErrorFormatMode(projectServices.projectOptions)
        ).execute()

        AndroidLocationsBuildService.RegistrationAction(project).execute()

        SdkComponentsBuildService.RegistrationAction(
            project,
            projectServices.projectOptions
        ).execute()
        LintFixBuildService.RegistrationAction(project).execute()
        LintClassLoaderBuildService.RegistrationAction(project).execute()

        FakeDependencyJarBuildService.RegistrationAction(project).execute()
    }
}
