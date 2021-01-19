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
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.TaskManager
import com.android.build.gradle.internal.dependency.AndroidAttributes
import com.android.build.gradle.internal.dsl.DslVariableFactory
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.MavenCoordinatesCacheBuildService
import com.android.build.gradle.internal.ide.v2.GlobalLibraryBuildService
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.publishArtifactToConfiguration
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.LintClassLoaderBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.tasks.LintStandaloneTask
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.SyncOptions
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

/**
 * Plugin for running lint **without** the Android Gradle plugin, such as in a pure Kotlin
 * project.
 */
abstract class LintPlugin : Plugin<Project> {
    private lateinit var projectServices: ProjectServices
    private var dslServices: DslServicesImpl? = null
    private var lintOptions: LintOptions? = null

    @get:Inject
    abstract val listenerRegistry: BuildEventsListenerRegistry

    override fun apply(project: Project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true")

        createProjectServices(project)

        dslServices = DslServicesImpl(
            projectServices, DslVariableFactory(projectServices.issueReporter),
            project.providers.provider { null }
        )

        createExtension(project)
        withJavaPlugin(project) { registerTasks(project) }
    }
    private fun registerTasks(project: Project) {
        val javaConvention: JavaPluginConvention = getJavaPluginConvention(project) ?: return
        val useNewLintModel = LintTaskManager.computeUseNewLintModel(
            project,
            projectServices.projectOptions
        )
        val customLintChecksConfig = TaskManager.createCustomLintChecksConfig(project)
        val customLintChecks = getLocalCustomLintChecks(customLintChecksConfig, dslServices!!, project.path)
        BasePlugin.createLintClasspathConfiguration(project)
        if (useNewLintModel) {
            registerTasks(
                project,
                javaConvention,
                customLintChecks
            )
        } else {
            registerLegacyTasks(
                project,
                javaConvention,
                customLintChecksConfig
            )
        }
    }

    private fun registerTasks(
        project: Project,
        javaConvention: JavaPluginConvention,
        customLintChecks: FileCollection
    ) {
        registerBuildServices(project)
        val lintTask =
            project.tasks.register("lint", AndroidLintTask::class.java) { task ->
                task.description = "Run Android Lint analysis on project `${project.name}`"
                task.configureForStandalone(project, projectServices.projectOptions, javaConvention, customLintChecks, lintOptions!!)
            }
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME).configure { t: Task -> t.dependsOn(lintTask) }

        project.tasks.register("lintVital", AndroidLintTask::class.java) { task ->
            task.description = "Runs lint on just the fatal issues in the project  `${project.name}`"
            task.configureForStandalone(project, projectServices.projectOptions, javaConvention, customLintChecks, lintOptions!!, fatalOnly = true)
        }

        project.tasks.register("lintFix", AndroidLintTask::class.java) { task ->
            task.description = "Runs lint on `${project.name}` and applies any safe suggestions to the source code."
            task.configureForStandalone(project, projectServices.projectOptions, javaConvention, customLintChecks, lintOptions!!, autoFix = true)
        }

        val lintModelWriterTask =
            project.tasks.register("generateLintModel", LintModelWriterTask::class.java) { task ->
                task.configureForStandalone(project, projectServices.projectOptions, javaConvention, customLintChecks, lintOptions!!)
                task.outputDirectory.setDisallowChanges(project.layout.buildDirectory.dir("intermediates/${AndroidArtifacts.ArtifactType.LINT_MODEL}"))
            }

        javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) { mainSourceSet ->
            project.configurations.getByName(mainSourceSet.runtimeElementsConfigurationName) { configuration ->
                val androidLintCategory = projectServices.objectFactory.named(Category::class.java, "android-lint")
                publishArtifactToConfiguration(
                    configuration,
                    lintModelWriterTask.flatMap { it.outputDirectory },
                    AndroidArtifacts.ArtifactType.LINT_MODEL,
                    AndroidAttributes(category = androidLintCategory)
                )
                // We don't want to publish the lint models to repositories. Remove them.
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

    private fun registerLegacyTasks(
        project: Project,
        javaConvention: JavaPluginConvention,
        customLintChecksConfig: Configuration
    ) {
        val projectName = project.name
        val task = registerLegacyTask(
            "lint",
            "Run Android Lint analysis on project `$projectName`",
            project,
            javaConvention,
            customLintChecksConfig
        )
        // Make the check task depend on the lint
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME)
            .configure { t: Task -> t.dependsOn(task) }
        val lintVital = registerLegacyTask(
            "lintVital",
            "Runs lint on just the fatal issues in the project `$projectName`",
            project,
            javaConvention,
            customLintChecksConfig
        )
        lintVital.configure { it.fatalOnly = true }
        registerLegacyTask(
            "lintFix",
            "Runs lint on `$projectName` and applies any safe suggestions to " +
                    "the source code.",
            project,
            javaConvention,
            customLintChecksConfig
        ).configure {
            it.autoFix = true
            it.group = "cleanup"
        }
    }

    private fun createExtension(project: Project) {
        lintOptions = project.extensions.create("lintOptions", LintOptions::class.java, dslServices)
    }

    private fun withJavaPlugin(project: Project, action: Action<Plugin<*>>) {
        project.plugins.withType(JavaBasePlugin::class.java, action)
    }

    private fun registerLegacyTask(
        taskName: String,
        description: String,
        project: Project,
        javaConvention: JavaPluginConvention,
        customLintChecksConfig: Configuration
    ): TaskProvider<LintStandaloneTask> {
        return project.tasks.register(taskName, LintStandaloneTask::class.java) { task ->
            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = description
            task.reportDir = javaConvention.testResultsDir
            task.lintOptions = lintOptions
            task.lintChecks = customLintChecksConfig
            task.outputs.upToDateWhen { false }
            task.lintClassLoader.set(LintClassLoaderBuildService.RegistrationAction(project).execute())
        }
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
            SyncIssueReporterImpl(SyncOptions.getModelQueryMode(projectOptions), logger)
        val deprecationReporter =
            DeprecationReporterImpl(syncIssueReporter, projectOptions, projectPath)
        projectServices = ProjectServices(
            syncIssueReporter, deprecationReporter, objectFactory, project.logger,
            project.providers, project.layout, projectOptions, project.gradle.sharedServices,
            maxWorkerCount = project.gradle.startParameter.maxWorkerCount
        ) { o: Any -> project.file(o) }
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
            SyncOptions.getModelQueryMode(projectServices.projectOptions)
        ).execute()
        SdkComponentsBuildService.RegistrationAction(
            project,
            projectServices.projectOptions,
            project.provider { null },
            project.provider { null },
            project.provider { null },
            project.provider { null },
        ).execute()
        LintFixBuildService.RegistrationAction(project).execute()
    }
}
