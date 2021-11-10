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
package com.android.build.gradle

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Lint
import com.android.build.api.extension.impl.DslLifecycleComponentsOperationsRegistrar
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dependency.ModelArtifactCompatibilityRule
import com.android.build.gradle.internal.dsl.LintImpl
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalLibraryBuildService
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintCopyReportTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.TaskCreationServicesImpl
import com.android.build.gradle.internal.services.VariantPropertiesApiServicesImpl
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptionService
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
import org.gradle.api.plugins.JavaPluginConvention
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
    private var lintOptions: LintOptions? = null
    private val dslOperationsRegistrar = DslLifecycleComponentsOperationsRegistrar<LintOptions>()

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

        createExtension(project, dslServices)
        withJavaPlugin(project) { registerTasks(project) }
    }
    private fun registerTasks(project: Project) {
        val javaConvention: JavaPluginConvention = getJavaPluginConvention(project) ?: return
        val customLintChecksConfig = TaskManager.createCustomLintChecksConfig(project)
        val customLintChecks = getLocalCustomLintChecks(customLintChecksConfig)
        registerTasks(
            project,
            javaConvention,
            customLintChecks
        )
        ModelArtifactCompatibilityRule.setUp(project.dependencies.attributesSchema)
    }

    private fun registerTasks(
        project: Project,
        javaConvention: JavaPluginConvention,
        customLintChecks: FileCollection
    ) {
        registerBuildServices(project)
        val artifacts = ArtifactsImpl(project, "global")
        val taskCreationServices: TaskCreationServices = TaskCreationServicesImpl(
            VariantPropertiesApiServicesImpl(projectServices), projectServices
        )
        // Create the 'lint' task before afterEvaluate to avoid breaking existing build scripts that
        // expect it to be present during evaluation
        val lintTask = project.tasks.register("lint", AndroidLintTextOutputTask::class.java)
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME).configure { t: Task -> t.dependsOn(lintTask) }

        // Avoid reading the lintOptions DSL and build directory before the build author can customize them
        project.afterEvaluate {
            dslOperationsRegistrar.executeDslFinalizationBlocks(lintOptions!!)

            lintTask.configure { task ->
                task.configureForStandalone(artifacts, lintOptions!!)
            }
            project.tasks.register("lintReport", AndroidLintTask::class.java) { task ->
                task.description = "Generates the lint report for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaConvention,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_MODEL)
                )
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
                task.configureForStandalone(artifacts, lintOptions!!, fatalOnly = true)
            }
            project.tasks.register("lintVitalReport", AndroidLintTask::class.java) { task ->
                task.description =
                    "Generates the lint report for just the fatal issues for project  `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaConvention,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_MODEL),
                    fatalOnly = true
                )
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
                    javaConvention,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                    artifacts.getOutputPath(InternalArtifactType.LINT_MODEL),
                    autoFix = true
                )
            }
            val lintAnalysisTask = project.tasks.register("lintAnalyze", AndroidLintAnalysisTask::class.java) { task ->
                task.description = "Runs lint analysis for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaConvention,
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
                    javaConvention,
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
                    javaConvention,
                    lintOptions!!,
                    artifacts.getOutputPath(
                        InternalArtifactType.LINT_PARTIAL_RESULTS,
                        AndroidLintAnalysisTask.PARTIAL_RESULTS_DIR_NAME
                    )
                )
            }
            LintModelWriterTask.BaseCreationAction.registerOutputArtifacts(lintModelWriterTask, artifacts)
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

        javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) { mainSourceSet ->
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
                        artifacts.get(InternalArtifactType.LINT_PARTIAL_RESULTS),
                        AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS,
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

    private fun createExtension(project: Project, dslServices: DslServicesImpl) {
        val lintImplClass = androidPluginDslDecorator.decorate(LintImpl::class.java)
        val newLintExtension = project.extensions.create(Lint::class.java, "lint", lintImplClass, dslServices)
        val decoratedLintOptionsClass =
            androidPluginDslDecorator.decorate(LintOptions::class.java)
        lintOptions =
            project.extensions.create("lintOptions", decoratedLintOptionsClass, dslServices, newLintExtension)
        project.extensions.create(
            "lintLifecycle",
            LintLifecycleExtensionImpl::class.java,
            dslOperationsRegistrar
        )
    }

    private fun withJavaPlugin(project: Project, action: Action<Plugin<*>>) {
        project.plugins.withType(JavaBasePlugin::class.java, action)
    }

    private fun getJavaPluginConvention(project: Project): JavaPluginConvention? {
        val convention = project.convention
        val javaConvention = convention.findPlugin(JavaPluginConvention::class.java)
        if (javaConvention == null) {
            project.logger.warn("Cannot apply lint if the java or kotlin Gradle plugins " +
                "have also been applied")
            return null
        }
        return javaConvention
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
            syncIssueReporter, deprecationReporter, objectFactory, project.logger,
            project.providers, project.layout, projectOptions, project.gradle.sharedServices,
            lintFromMaven,
            maxWorkerCount = project.gradle.startParameter.maxWorkerCount, projectInfo = projectInfo
        ) { o: Any -> project.file(o) }
        projectOptions
            .allOptions
            .forEach { (option: Option<*>, value: Any) ->
                projectServices.deprecationReporter.reportOptionIssuesIfAny(option, value)
            }
    }

    private fun registerBuildServices(project: Project) {
        AnalyticsService.RegistrationAction(project).execute()
        val configuratorService =
            AnalyticsConfiguratorService.RegistrationAction(project).execute().get()
        configuratorService.createAnalyticsService(project, listenerRegistry)
        configuratorService.getProjectBuilder(project.path)?.let {
            it
                .setAndroidPluginVersion(ANDROID_GRADLE_PLUGIN_VERSION)
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                .setOptions(AnalyticsUtil.toProto(projectServices.projectOptions))
        }


        val stringCachingService = StringCachingBuildService.RegistrationAction(project).execute()
        val mavenCoordinatesCacheBuildService =
            MavenCoordinatesCacheBuildService.RegistrationAction(project, stringCachingService)
                .execute()
        LibraryDependencyCacheBuildService.RegistrationAction(
            project,
            mavenCoordinatesCacheBuildService
        ).execute()
        GlobalLibraryBuildService.RegistrationAction(project, mavenCoordinatesCacheBuildService)
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
    }
}
