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
import com.android.build.gradle.internal.lint.AndroidLintGlobalTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.KotlinMultiplatformExtensionWrapper
import com.android.build.gradle.internal.lint.LintFixBuildService
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.lint.LintMode
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.LintTaskManager
import com.android.build.gradle.internal.lint.getLocalCustomLintChecks
import com.android.build.gradle.internal.profile.AnalyticsConfiguratorService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsUtil
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.getAttributes
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.LINT_REPORT_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.LINT_VITAL_REPORT_LINT_MODEL
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
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.options.BooleanOption.LINT_ANALYSIS_PER_COMPONENT
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.android.tools.lint.model.LintModelArtifactType
import com.android.utils.usLocaleCapitalize
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import javax.inject.Inject

const val LINT_PLUGIN_ID = "com.android.lint"

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
            project.providers.provider { null },
            null
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
        ModelArtifactCompatibilityRule.setUp(project.dependencies.attributesSchema, false)
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
        val lintTask =
            project.tasks.register("lint", AndroidLintGlobalTask::class.java) {
                it.group = JavaBasePlugin.VERIFICATION_GROUP
                it.description = "Runs lint for project `${project.name}`"
            }
        project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(lintTask)
        val lintFixTask =
            project.tasks.register("lintFix", AndroidLintGlobalTask::class.java) {
                it.group = JavaBasePlugin.VERIFICATION_GROUP
                it.description =
                    "Generates the lint report for project `${project.name}` and applies any safe suggestions to the source code."
            }
        val updateLintBaselineTask =
            project.tasks.register("updateLintBaseline", AndroidLintGlobalTask::class.java) {
                it.group = JavaBasePlugin.VERIFICATION_GROUP
                it.description = "Updates the lint baseline for project `${project.name}`."
            }
        val lintVitalTask =
            project.tasks.register("lintVital", AndroidLintGlobalTask::class.java) {
                it.group = JavaBasePlugin.VERIFICATION_GROUP
                it.description =
                    "Generates the lint report for just the fatal issues for project `${project.name}`"
            }

        // Avoid reading the lintOptions DSL and build directory before the build author can customize them
        project.afterEvaluate {
            dslOperationsRegistrar.executeDslFinalizationBlocks()

            // kotlinExtensionWrapper will be null if the KotlinMultiplatformExtension is not on the
            // runtime classpath.
            val kotlinExtensionWrapper = try {
                // This will throw an (ignored) ClassNotFoundException if
                // KotlinMultiplatformExtension is not on the runtime classpath.
                Class.forName("org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension")
                val kotlinExtension =
                    project.extensions.findByName("kotlin") as? KotlinMultiplatformExtension
                kotlinExtension?.let { KotlinMultiplatformExtensionWrapper(it) }
            } catch (ignored: ClassNotFoundException) {
                null
            }
            val lintTextOutputTask =
                project.tasks.register("lintJvm", AndroidLintTextOutputTask::class.java) { task ->
                    task.configureForStandalone(taskCreationServices, artifacts, lintOptions!!)
                }
            lintTask.dependsOn(lintTextOutputTask)
            val isPerComponentLintAnalysis =
                kotlinExtensionWrapper != null
                        || taskCreationServices.projectOptions.get(LINT_ANALYSIS_PER_COMPONENT)
            val updateLintBaselineJvmTask =
                project.tasks.register("updateLintBaselineJvm", AndroidLintTask::class.java) { task ->
                    task.description = "Updates the JVM lint baseline for project `${project.name}`."
                    task.configureForStandalone(
                        taskCreationServices,
                        javaExtension,
                        customLintChecks,
                        lintOptions!!,
                        artifacts.getAll(InternalMultipleArtifactType.LINT_PARTIAL_RESULTS),
                        artifacts.getAll(LINT_REPORT_LINT_MODEL),
                        if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                            artifacts.getAll(
                                InternalMultipleArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
                            )
                        } else {
                            null
                        },
                        if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                            artifacts.getAll(InternalMultipleArtifactType.UNIT_TEST_LINT_MODEL)
                        } else {
                            null
                        },
                        LintMode.UPDATE_BASELINE
                    )
                }
            updateLintBaselineTask.dependsOn(updateLintBaselineJvmTask)

            project.tasks.register("lintReportJvm", AndroidLintTask::class.java) { task ->
                task.description = "Generates the JVM lint report for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.getAll(InternalMultipleArtifactType.LINT_PARTIAL_RESULTS),
                    artifacts.getAll(LINT_REPORT_LINT_MODEL),
                    if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                        artifacts.getAll(
                            InternalMultipleArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
                        )
                    } else {
                        null
                    },
                    if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                        artifacts.getAll(InternalMultipleArtifactType.UNIT_TEST_LINT_MODEL)
                    } else {
                        null
                    },
                    LintMode.REPORTING
                )
                task.mustRunAfter(updateLintBaselineJvmTask)
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

            val lintVitalJvmTask =
                project.tasks.register("lintVitalJvm", AndroidLintTextOutputTask::class.java) { task ->
                    task.configureForStandalone(
                        taskCreationServices,
                        artifacts,
                        lintOptions!!,
                        fatalOnly = true
                    )
                }
            lintVitalTask.dependsOn(lintVitalJvmTask)
            project.tasks.register("lintVitalReportJvm", AndroidLintTask::class.java) { task ->
                task.description =
                    "Generates the JVM lint report for just the fatal issues for project `${project.name}`"
                task.configureForStandalone(
                    taskCreationServices,
                    javaExtension,
                    customLintChecks,
                    lintOptions!!,
                    artifacts.getAll(InternalMultipleArtifactType.LINT_VITAL_PARTIAL_RESULTS),
                    artifacts.getAll(LINT_VITAL_REPORT_LINT_MODEL),
                    unitTestPartialResults = null,
                    unitTestLintModel = null,
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

            val lintFixJvmTask =
                project.tasks.register("lintFixJvm", AndroidLintTask::class.java) { task ->
                    task.description =
                        "Generates the JVM lint report for project `${project.name}` and applies any safe suggestions to the source code."
                    task.configureForStandalone(
                        taskCreationServices,
                        javaExtension,
                        customLintChecks,
                        lintOptions!!,
                        artifacts.getAll(InternalMultipleArtifactType.LINT_PARTIAL_RESULTS),
                        artifacts.getAll(LINT_REPORT_LINT_MODEL),
                        if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                            artifacts.getAll(
                                InternalMultipleArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
                            )
                        } else {
                            null
                        },
                        if (isPerComponentLintAnalysis && lintOptions!!.ignoreTestSources.not()) {
                            artifacts.getAll(InternalMultipleArtifactType.UNIT_TEST_LINT_MODEL)
                        } else {
                            null
                        },
                        LintMode.REPORTING,
                        autoFix = true
                    )
                    task.mustRunAfter(updateLintBaselineJvmTask)
                }
            lintFixTask.dependsOn(lintFixJvmTask)

            val jvmTargetNames: List<String> =
                kotlinExtensionWrapper?.kotlinExtension
                    ?.targets
                    ?.filter {
                        it.platformType == KotlinPlatformType.jvm
                                && it.name != KotlinMultiplatformAndroidPlugin.ANDROID_TARGET_NAME
                    }
                    ?.map { it.name }
                    ?: listOf("jvm")

            if (isPerComponentLintAnalysis) {
                for (jvmTargetName in jvmTargetNames) {
                    val lintAnalysisMainTask =
                        project.tasks.register(
                            "lintAnalyze${jvmTargetName.usLocaleCapitalize()}Main",
                            AndroidLintAnalysisTask::class.java
                        ) { task ->
                            task.description =
                                "Runs JVM lint analysis for main component of project `${project.name}`"
                            task.configureForStandalone(
                                taskCreationServices,
                                javaExtension,
                                kotlinExtensionWrapper,
                                customLintChecks,
                                lintOptions!!,
                                LintModelArtifactType.MAIN,
                                fatalOnly = false,
                                jvmTargetName
                            )
                        }
                    AndroidLintAnalysisTask.registerOutputArtifacts(
                        lintAnalysisMainTask,
                        InternalMultipleArtifactType.LINT_PARTIAL_RESULTS,
                        artifacts
                    )
                    publishLintArtifact(
                        project,
                        lintAnalysisMainTask.flatMap { it.partialResultsDirectory },
                        AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS,
                        javaExtension,
                        kotlinExtensionWrapper,
                        jvmTargetName
                    )
                    if (lintOptions!!.ignoreTestSources.not()) {
                        val lintAnalysisTestTask =
                            project.tasks.register(
                                "lintAnalyze${jvmTargetName.usLocaleCapitalize()}Test",
                                AndroidLintAnalysisTask::class.java
                            ) { task ->
                                task.description =
                                    "Runs JVM lint analysis for test component of project `${project.name}`"
                                task.configureForStandalone(
                                    taskCreationServices,
                                    javaExtension,
                                    kotlinExtensionWrapper,
                                    customLintChecks,
                                    lintOptions!!,
                                    LintModelArtifactType.UNIT_TEST,
                                    fatalOnly = false,
                                    jvmTargetName
                                )
                            }
                        AndroidLintAnalysisTask.registerOutputArtifacts(
                            lintAnalysisTestTask,
                            InternalMultipleArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS,
                            artifacts
                        )
                        publishLintArtifact(
                            project,
                            lintAnalysisTestTask.flatMap { it.partialResultsDirectory },
                            AndroidArtifacts.ArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS,
                            javaExtension,
                            kotlinExtensionWrapper,
                            jvmTargetName
                        )
                    }
                    val lintVitalAnalysisMainTask =
                        project.tasks.register(
                            "lintVitalAnalyze${jvmTargetName.usLocaleCapitalize()}Main",
                            AndroidLintAnalysisTask::class.java
                        ) { task ->
                            task.description =
                                "Runs JVM lint analysis on just the fatal issues for main component of project `${project.name}`"
                            task.configureForStandalone(
                                taskCreationServices,
                                javaExtension,
                                kotlinExtensionWrapper,
                                customLintChecks,
                                lintOptions!!,
                                LintModelArtifactType.MAIN,
                                fatalOnly = true,
                                jvmTargetName
                            )
                        }
                    AndroidLintAnalysisTask.registerOutputArtifacts(
                        lintVitalAnalysisMainTask,
                        InternalMultipleArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                        artifacts
                    )
                    publishLintArtifact(
                        project,
                        lintVitalAnalysisMainTask.flatMap { it.partialResultsDirectory },
                        AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                        javaExtension,
                        kotlinExtensionWrapper,
                        jvmTargetName
                    )
                }
            } else {
                val lintAnalysisTask =
                    project.tasks.register(
                        "lintAnalyzeJvm",
                        AndroidLintAnalysisTask::class.java
                    ) { task ->
                        task.description = "Runs JVM lint analysis for project `${project.name}`"
                        task.configureForStandalone(
                            taskCreationServices,
                            javaExtension,
                            kotlinExtensionWrapper = null,
                            customLintChecks,
                            lintOptions!!,
                            lintModelArtifactType = null,
                            fatalOnly = false,
                            jvmTargetName = null
                        )
                    }
                AndroidLintAnalysisTask.registerOutputArtifacts(
                    lintAnalysisTask,
                    InternalMultipleArtifactType.LINT_PARTIAL_RESULTS,
                    artifacts
                )
                publishLintArtifact(
                    project,
                    lintAnalysisTask.flatMap { it.partialResultsDirectory },
                    AndroidArtifacts.ArtifactType.LINT_PARTIAL_RESULTS,
                    javaExtension,
                    kotlinExtensionWrapper = null,
                    jvmTargetName = null
                )
                val lintVitalAnalysisTask =
                    project.tasks.register(
                        "lintVitalAnalyzeJvm",
                        AndroidLintAnalysisTask::class.java
                    ) { task ->
                        task.description =
                            "Runs JVM lint analysis on just the fatal issues for project `${project.name}`"
                        task.configureForStandalone(
                            taskCreationServices,
                            javaExtension,
                            kotlinExtensionWrapper = null,
                            customLintChecks,
                            lintOptions!!,
                            lintModelArtifactType = null,
                            fatalOnly = true,
                            jvmTargetName = null
                        )
                    }
                AndroidLintAnalysisTask.registerOutputArtifacts(
                    lintVitalAnalysisTask,
                    InternalMultipleArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                    artifacts
                )
                publishLintArtifact(
                    project,
                    lintVitalAnalysisTask.flatMap { it.partialResultsDirectory },
                    AndroidArtifacts.ArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                    javaExtension,
                    kotlinExtensionWrapper = null,
                    jvmTargetName = null
                )
            }
            if (isPerComponentLintAnalysis) {
                for (jvmTargetName in jvmTargetNames) {
                    val lintModelWriterMainTask =
                        project.tasks.register(
                            "generate${jvmTargetName.usLocaleCapitalize()}MainLintModel",
                            LintModelWriterTask::class.java
                        ) { task ->
                            task.configureForStandalone(
                                taskCreationServices,
                                javaExtension,
                                kotlinExtensionWrapper,
                                lintOptions!!,
                                artifacts.getOutputPath(
                                    InternalMultipleArtifactType.LINT_PARTIAL_RESULTS,
                                    "lintAnalyze${jvmTargetName.usLocaleCapitalize()}Main"
                                ),
                                LintModelArtifactType.MAIN,
                                fatalOnly = false,
                                jvmTargetName
                            )
                        }
                    LintModelWriterTask.registerOutputArtifacts(
                        lintModelWriterMainTask,
                        LINT_REPORT_LINT_MODEL,
                        artifacts
                    )
                    publishLintArtifact(
                        project,
                        lintModelWriterMainTask.flatMap { it.outputDirectory },
                        AndroidArtifacts.ArtifactType.LINT_MODEL,
                        javaExtension,
                        kotlinExtensionWrapper,
                        jvmTargetName
                    )
                    if (lintOptions!!.ignoreTestSources.not()) {
                        val lintModelWriterTestTask =
                            project.tasks.register(
                                "generate${jvmTargetName.usLocaleCapitalize()}TestLintModel",
                                LintModelWriterTask::class.java
                            ) { task ->
                                task.configureForStandalone(
                                    taskCreationServices,
                                    javaExtension,
                                    kotlinExtensionWrapper,
                                    lintOptions!!,
                                    artifacts.getOutputPath(
                                        InternalMultipleArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS,
                                        "lintAnalyze${jvmTargetName.usLocaleCapitalize()}Test"
                                    ),
                                    LintModelArtifactType.UNIT_TEST,
                                    fatalOnly = false,
                                    jvmTargetName
                                )
                            }
                        LintModelWriterTask.registerOutputArtifacts(
                            lintModelWriterTestTask,
                            InternalMultipleArtifactType.UNIT_TEST_LINT_MODEL,
                            artifacts
                        )
                        publishLintArtifact(
                            project,
                            lintModelWriterTestTask.flatMap { it.outputDirectory },
                            AndroidArtifacts.ArtifactType.UNIT_TEST_LINT_MODEL,
                            javaExtension,
                            kotlinExtensionWrapper,
                            jvmTargetName
                        )
                    }
                    val lintVitalModelWriterMainTask =
                        project.tasks.register(
                            "generateLintVital${jvmTargetName.usLocaleCapitalize()}MainLintModel",
                            LintModelWriterTask::class.java
                        ) { task ->
                            task.configureForStandalone(
                                taskCreationServices,
                                javaExtension,
                                kotlinExtensionWrapper,
                                lintOptions!!,
                                artifacts.getOutputPath(
                                    InternalMultipleArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                                    "lintVitalAnalyze${jvmTargetName.usLocaleCapitalize()}Main"
                                ),
                                LintModelArtifactType.MAIN,
                                fatalOnly = true,
                                jvmTargetName
                            )
                        }
                    LintModelWriterTask.registerOutputArtifacts(
                        lintVitalModelWriterMainTask,
                        LINT_VITAL_REPORT_LINT_MODEL,
                        artifacts
                    )
                    publishLintArtifact(
                        project,
                        lintVitalModelWriterMainTask.flatMap { it.outputDirectory },
                        AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL,
                        javaExtension,
                        kotlinExtensionWrapper,
                        jvmTargetName
                    )
                }
            } else {
                val lintModelWriterTask =
                    project.tasks.register(
                        "generateJvmLintModel",
                        LintModelWriterTask::class.java
                    ) { task ->
                        task.configureForStandalone(
                            taskCreationServices,
                            javaExtension,
                            kotlinExtensionWrapper = null,
                            lintOptions!!,
                            artifacts.getOutputPath(
                                InternalMultipleArtifactType.LINT_PARTIAL_RESULTS,
                                "lintAnalyzeJvm"
                            ),
                            lintModelArtifactType = null,
                            fatalOnly = false,
                            jvmTargetName = null
                        )
                    }
                LintModelWriterTask.registerOutputArtifacts(
                    lintModelWriterTask,
                    LINT_REPORT_LINT_MODEL,
                    artifacts
                )
                publishLintArtifact(
                    project,
                    lintModelWriterTask.flatMap { it.outputDirectory },
                    AndroidArtifacts.ArtifactType.LINT_MODEL,
                    javaExtension,
                    kotlinExtensionWrapper = null,
                    jvmTargetName = null
                )
                val lintVitalModelWriterTask =
                    project.tasks.register(
                        "generateLintVitalJvmLintModel",
                        LintModelWriterTask::class.java
                    ) { task ->
                        task.configureForStandalone(
                            taskCreationServices,
                            javaExtension,
                            kotlinExtensionWrapper = null,
                            lintOptions!!,
                            artifacts.getOutputPath(
                                InternalMultipleArtifactType.LINT_VITAL_PARTIAL_RESULTS,
                                "lintVitalAnalyzeJvm"
                            ),
                            lintModelArtifactType = null,
                            fatalOnly = true,
                            jvmTargetName = null
                        )
                    }
                LintModelWriterTask.registerOutputArtifacts(
                    lintVitalModelWriterTask,
                    LINT_VITAL_REPORT_LINT_MODEL,
                    artifacts
                )
                publishLintArtifact(
                    project,
                    lintVitalModelWriterTask.flatMap { it.outputDirectory },
                    AndroidArtifacts.ArtifactType.LINT_VITAL_LINT_MODEL,
                    javaExtension,
                    kotlinExtensionWrapper = null,
                    jvmTargetName = null
                )
            }
            val lintModelMetadataWriterTask =
                project.tasks
                    .register(
                        "writeJvmLintModelMetadata",
                        LintModelMetadataTask::class.java
                    ) { task ->
                        task.configureForStandalone(project)
                    }
            LintModelMetadataTask.registerOutputArtifacts(lintModelMetadataWriterTask, artifacts)
            if (isPerComponentLintAnalysis) {
                for (jvmTargetName in jvmTargetNames) {
                    publishLintArtifact(
                        project,
                        lintModelMetadataWriterTask.flatMap { it.outputFile },
                        AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA,
                        javaExtension,
                        kotlinExtensionWrapper,
                        jvmTargetName
                    )
                }
            } else {
                publishLintArtifact(
                    project,
                    lintModelMetadataWriterTask.flatMap { it.outputFile },
                    AndroidArtifacts.ArtifactType.LINT_MODEL_METADATA,
                    javaExtension,
                    kotlinExtensionWrapper = null,
                    jvmTargetName = null
                )
            }
            if (LintTaskManager.needsCopyReportTask(lintOptions!!)) {
                val copyLintReportsTask =
                    project.tasks.register(
                        "copyJvmLintReports",
                        AndroidLintCopyReportTask::class.java
                    ) { task ->
                        task.configureForStandalone(artifacts, lintOptions!!)
                    }
                lintTextOutputTask.configure { it.finalizedBy(copyLintReportsTask) }
            }

            // We don't want to publish the lint models or partial results to repositories.
            // Remove them.
            if (kotlinExtensionWrapper == null) {
                javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME) { mainSourceSet ->
                    listOf(
                        mainSourceSet.runtimeElementsConfigurationName,
                        mainSourceSet.apiElementsConfigurationName
                    ).forEach { configurationName ->
                        project.configurations.getByName(configurationName) { configuration ->
                            project.components.all { component: SoftwareComponent ->
                                if (component.name == "java" && component is AdhocComponentWithVariants) {
                                    component.withVariantsFromConfiguration(configuration) { variant: ConfigurationVariantDetails ->
                                        val category =
                                            variant.configurationVariant.attributes.getAttribute(
                                                Category.CATEGORY_ATTRIBUTE
                                            )
                                        if (category?.name == Category.VERIFICATION) {
                                            variant.skip()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun publishLintArtifact(
        project: Project,
        provider: Provider<FileSystemLocation>,
        artifactType: AndroidArtifacts.ArtifactType,
        javaExtension: JavaPluginExtension,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper?,
        jvmTargetName: String?
    ) {
        val configurationNames: List<String> = if (kotlinExtensionWrapper == null) {
            val mainSourceSet = javaExtension.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            listOf(
                mainSourceSet.runtimeElementsConfigurationName,
                mainSourceSet.apiElementsConfigurationName
            )
        } else {
            val jvmTarget =
                jvmTargetName?.let { kotlinExtensionWrapper.kotlinExtension.targets.findByName(it) }
            val jvmMainCompilation = jvmTarget?.compilations?.findByName("main")
            if (jvmMainCompilation != null) {
                listOf(
                    jvmTarget.apiElementsConfigurationName,
                    jvmTarget.runtimeElementsConfigurationName
                )
            } else {
                emptyList()
            }
        }
        configurationNames.forEach { configurationName ->
            project.configurations.getByName(configurationName) { configuration ->
                publishArtifactToConfiguration(
                    configuration,
                    provider,
                    artifactType,
                    artifactType.getAttributes { type, name ->
                        projectServices.objectFactory.named(type, name)
                    }
                )
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
            null,
            maxWorkerCount = project.gradle.startParameter.maxWorkerCount,
            projectInfo = projectInfo,
            fileResolver = { o: Any -> project.file(o) },
            configurationContainer = project.configurations,
            dependencyHandler = project.dependencies,
            extraProperties = project.extensions.extraProperties,
            emptyTaskCreator = { name -> project.tasks.register(name) },
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
