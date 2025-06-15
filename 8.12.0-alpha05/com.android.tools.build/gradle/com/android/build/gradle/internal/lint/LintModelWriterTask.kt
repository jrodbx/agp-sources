/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.NestedComponentCreationConfig
import com.android.build.gradle.internal.component.TestFixturesCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.ANDROID_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.TEST_FIXTURES_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.TEST_FIXTURES_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalArtifactType.UNIT_TEST_LINT_PARTIAL_RESULTS
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.LINT_REPORT_LINT_MODEL
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType.LINT_VITAL_REPORT_LINT_MODEL
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.PrivacySandboxSdkVariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelSerialization
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Task to write the [LintModelModule] representation of one variant of a Gradle project to disk.
 *
 * This serialized [LintModelModule] file is read by Lint in consuming projects to get all the
 * information about this variant in project.
 *
 * Caching disabled by default for this task because the output contains absolute paths.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.LINT)
abstract class LintModelWriterTask : NonIncrementalTask() {

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    /**
     * We care only about this directory's location, not its contents. Gradle's recommendation is to
     * annotate this with @Internal and add a separate String property annotated with @Input which
     * returns the absolute path of the file (https://github.com/gradle/gradle/issues/5789).
     */
    @get:Internal
    abstract val partialResultsDir: DirectoryProperty

    @get:Input
    @get:Optional
    val partialResultsDirPath: Provider<String> = partialResultsDir.locationOnly.map { it.asFile.absolutePath }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val module = projectInputs.convertToLintModelModule()
        val variant =
            variantInputs.toLintModel(
                module,
                partialResultsDir.orNull?.asFile,
                desugaredMethodsFiles = listOf()
            )
        LintModelSerialization.writeModule(
            module = module,
            destination = outputDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    /**
     * If [lintModelArtifactType] is not null, only the corresponding artifact is initialized; if
     * it's null, both the main and test artifacts are initialized.
     *
     * If [testCompileClasspath] is null, the standard "testCompileClasspath" is used; otherwise,
     * [testCompileClasspath] is used in order to properly include the main jar.
     *
     * If [testRuntimeClasspath] is null, the standard "testRuntimeClasspath" is used; otherwise,
     * [testRuntimeClasspath] is used in order to properly include the main jar.
     */
    internal fun configureForStandalone(
        taskCreationServices: TaskCreationServices,
        javaExtension: JavaPluginExtension,
        kotlinExtensionWrapper: KotlinMultiplatformExtensionWrapper?,
        lintOptions: Lint,
        partialResultsDir: File,
        lintModelArtifactType: LintModelArtifactType?,
        fatalOnly: Boolean,
        jvmTargetName: String?,
        testCompileClasspath: Configuration? = null,
        testRuntimeClasspath: Configuration? = null
    ) {
        this.variantName = ""
        this.analyticsService.setDisallowChanges(
            getBuildService(taskCreationServices.buildServiceRegistry)
        )
        this.projectInputs
            .initializeForStandalone(project, javaExtension, lintOptions, LintMode.MODEL_WRITING)
        this.variantInputs
            .initializeForStandalone(
                project,
                this,
                javaExtension,
                kotlinExtensionWrapper,
                taskCreationServices.projectOptions,
                fatalOnly = fatalOnly,
                useModuleDependencyLintModels = true,
                LintMode.MODEL_WRITING,
                lintModelArtifactType,
                jvmTargetName,
                testCompileClasspath,
                testRuntimeClasspath
            )
        this.partialResultsDir.set(partialResultsDir)
        this.partialResultsDir.disallowChanges()
    }

    /**
     * [isForLocalReportTask] should be true only if the lint model is being written for the lint
     * report task in the same module.
     */
    class CreationAction(
        val variant: VariantWithTests,
        private val useModuleDependencyLintModels: Boolean,
        private val fatalOnly: Boolean,
        private val isForLocalReportTask: Boolean
    ) : VariantTaskCreationAction<LintModelWriterTask, ConsumableCreationConfig>(variant.main) {
        private val vitalOrBlank = if (fatalOnly) "Vital" else ""
        private val reportOrBlank = if (isForLocalReportTask) "Report" else ""
        override val name: String
            get() = computeTaskName("generate", "Lint${vitalOrBlank}${reportOrBlank}Model")

        override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            super.handleProvider(taskProvider)
            if (isForLocalReportTask) {
                registerOutputArtifacts(
                    taskProvider,
                    if (fatalOnly) LINT_VITAL_REPORT_LINT_MODEL else LINT_REPORT_LINT_MODEL,
                    creationConfig.artifacts
                )
            } else {
                registerOutputArtifacts(
                    taskProvider,
                    if (fatalOnly) LINT_VITAL_LINT_MODEL else LINT_MODEL,
                    creationConfig.artifacts
                )
            }
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            task.projectInputs.initialize(variant, LintMode.MODEL_WRITING)
            val warnIfProjectTreatedAsExternalDependency =
                isForLocalReportTask && creationConfig.global.lintOptions.checkDependencies
            task.variantInputs.initialize(
                task,
                variant,
                useModuleDependencyLintModels = useModuleDependencyLintModels,
                warnIfProjectTreatedAsExternalDependency,
                LintMode.MODEL_WRITING,
                addBaseModuleLintModel = creationConfig is DynamicFeatureCreationConfig,
                fatalOnly = fatalOnly
            )
            val type = if (fatalOnly) {
                LINT_VITAL_PARTIAL_RESULTS
            } else {
                LINT_PARTIAL_RESULTS
            }
            val partialResultsDir = creationConfig.artifacts.get(type)
            task.partialResultsDir.set(partialResultsDir)
            task.partialResultsDir.disallowChanges()
        }
    }

    class PrivacySandboxCreationAction(
        private val variantScope: PrivacySandboxSdkVariantScope,
        private val fatalOnly: Boolean,
        private val projectOptions: ProjectOptions,
    ) : PrivacySandboxSdkVariantTaskCreationAction<LintModelWriterTask>() {
        private val vitalOrBlank = if (fatalOnly) "Vital" else ""
        override val name: String
            get() = "generateLint${vitalOrBlank}ReportModel"

        override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            super.handleProvider(taskProvider)
            registerOutputArtifacts(
                taskProvider,
                if (fatalOnly) LINT_VITAL_REPORT_LINT_MODEL else LINT_REPORT_LINT_MODEL,
                variantScope.artifacts
            )
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            task.projectInputs.initialize(variantScope, LintMode.MODEL_WRITING)
            task.variantInputs.initialize(
                task,
                variantScope,
                projectOptions,
                true,
                LintMode.MODEL_WRITING,
                fatalOnly)
            val type = if (fatalOnly) {
                LINT_VITAL_PARTIAL_RESULTS
            } else {
                LINT_PARTIAL_RESULTS
            }
            val partialResultsDir = variantScope.artifacts.get(type)
            task.partialResultsDir.set(partialResultsDir)
            task.partialResultsDir.disallowChanges()
        }
    }

    /**
     * [isMainModelForLocalReportTask] should be true only if (1) creationConfig is not a nested
     * creation config and (2) the lint model is being written for the lint report task in the same
     * module.
     */
    class PerComponentCreationAction(
        creationConfig: ComponentCreationConfig,
        private val useModuleDependencyLintModels: Boolean,
        private val fatalOnly: Boolean,
        private val isMainModelForLocalReportTask: Boolean
    ) : VariantTaskCreationAction<LintModelWriterTask, ComponentCreationConfig>(creationConfig) {

        override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        private val vitalOrBlank = if (fatalOnly) "Vital" else ""
        private val reportOrBlank = if (isMainModelForLocalReportTask) "Report" else ""
        override val name: String
            get() = computeTaskName("generate", "Lint${vitalOrBlank}${reportOrBlank}Model")

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig
                }
            if (isMainModelForLocalReportTask) {
                registerOutputArtifacts(
                    taskProvider,
                    if (fatalOnly) LINT_VITAL_REPORT_LINT_MODEL else LINT_REPORT_LINT_MODEL,
                    creationConfig.artifacts
                )
            } else {
                val artifactType =
                    when (creationConfig) {
                        is HostTestCreationConfig -> UNIT_TEST_LINT_MODEL
                        is DeviceTestCreationConfig -> ANDROID_TEST_LINT_MODEL
                        is TestFixturesCreationConfig -> TEST_FIXTURES_LINT_MODEL
                        else -> if (fatalOnly) {
                            LINT_VITAL_LINT_MODEL
                        } else {
                            LINT_MODEL
                        }
                    }
                registerOutputArtifacts(taskProvider, artifactType, mainVariant.artifacts)
            }
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            val mainVariant =
                if (creationConfig is NestedComponentCreationConfig) {
                    creationConfig.mainVariant
                } else {
                    creationConfig as VariantCreationConfig
                }
            task.projectInputs.initialize(mainVariant, LintMode.MODEL_WRITING)
            val warnIfProjectTreatedAsExternalDependency =
                isMainModelForLocalReportTask && creationConfig.global.lintOptions.checkDependencies
            task.variantInputs.initialize(
                task,
                mainVariant,
                creationConfig as? HostTestCreationConfig,
                creationConfig as? DeviceTestCreationConfig,
                creationConfig as? TestFixturesCreationConfig,
                creationConfig.services,
                mainVariant.name,
                useModuleDependencyLintModels = useModuleDependencyLintModels,
                warnIfProjectTreatedAsExternalDependency,
                lintMode = LintMode.MODEL_WRITING,
                addBaseModuleLintModel = creationConfig is DynamicFeatureCreationConfig,
                fatalOnly = fatalOnly,
                includeMainArtifact = creationConfig is VariantCreationConfig,
                isPerComponentLintAnalysis = true
            )
            val partialResultsDir =
                mainVariant.artifacts.get(
                    when (creationConfig) {
                        is HostTestCreationConfig -> UNIT_TEST_LINT_PARTIAL_RESULTS
                        is DeviceTestCreationConfig -> ANDROID_TEST_LINT_PARTIAL_RESULTS
                        is TestFixturesCreationConfig -> TEST_FIXTURES_LINT_PARTIAL_RESULTS
                        else -> if (fatalOnly) {
                            LINT_VITAL_PARTIAL_RESULTS
                        } else {
                            LINT_PARTIAL_RESULTS
                        }
                    }
                )
            task.partialResultsDir.set(partialResultsDir)
            task.partialResultsDir.disallowChanges()
        }
    }

    companion object {
        fun registerOutputArtifacts(
            taskProvider: TaskProvider<LintModelWriterTask>,
            internalArtifactType: InternalArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts
                .setInitialProvider(taskProvider, LintModelWriterTask::outputDirectory)
                .on(internalArtifactType)
        }

        fun registerOutputArtifacts(
            taskProvider: TaskProvider<LintModelWriterTask>,
            internalArtifactType: InternalMultipleArtifactType<Directory>,
            artifacts: ArtifactsImpl
        ) {
            artifacts.use(taskProvider)
                .wiredWith(LintModelWriterTask::outputDirectory)
                .toAppendTo(internalArtifactType)
        }
    }
}

