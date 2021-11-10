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
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.ProjectOptions
import com.android.tools.lint.model.LintModelModule
import com.android.tools.lint.model.LintModelSerialization
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Task to write the [LintModelModule] representation of one variant of a Gradle project to disk.
 *
 * When checkDependencies is used in a consuming project, this serialized [LintModelModule] file is
 * read by Lint in consuming projects to get all the information about this variant in project.
 */
abstract class LintModelWriterTask : NonIncrementalTask() {

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    // Ideally, we'd annotate this property with @Input because we only care about its location, not
    // its contents, but task validation prohibits annotating a File property with @Input. The
    // suggested workaround is to instead annotate the File property with @Internal and add a
    // separate String property annotated with @Input which returns the absolute path of the file
    // (https://github.com/gradle/gradle/issues/5789).
    @get:Internal
    lateinit var partialResultsDir: File
        private set

    @get:Input
    lateinit var partialResultsDirPath: String
        private set

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val module = projectInputs.convertToLintModelModule()
        val variant = variantInputs.toLintModel(module, partialResultsDir)
        LintModelSerialization.writeModule(
            module = module,
            destination = outputDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    internal fun configureForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        javaConvention: JavaPluginConvention,
        lintOptions: LintOptions,
        partialResultsDir: File
    ) {
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        this.projectInputs.initializeForStandalone(project, javaConvention, lintOptions)
        // The artifact produced is only used by lint tasks with checkDependencies=true
        this.variantInputs.initializeForStandalone(project, javaConvention, projectOptions, checkDependencies=true)
        this.partialResultsDir = partialResultsDir
        this.partialResultsDirPath = partialResultsDir.absolutePath
    }

    class LintCreationAction(
        creationConfig: ConsumableCreationConfig,
        checkDependencies: Boolean = true
    ) : BaseCreationAction(creationConfig, checkDependencies) {

        override val useLintVitalPartialResults: Boolean
            get() = false

        override val name: String
            get() = computeTaskName("generate", "LintModel")
    }

    class LintVitalCreationAction(
        creationConfig: ConsumableCreationConfig,
        checkDependencies: Boolean = false
    ) : BaseCreationAction(creationConfig, checkDependencies) {

        override val useLintVitalPartialResults: Boolean
            get() = true

        override val name: String
            get() = computeTaskName("generate", "LintVitalLintModel")
    }

    abstract class BaseCreationAction(
        creationConfig: ConsumableCreationConfig,
        private val checkDependencies: Boolean
    ) : VariantTaskCreationAction<LintModelWriterTask, ConsumableCreationConfig>(
        creationConfig
    ) {
        abstract val useLintVitalPartialResults: Boolean

        final override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            super.handleProvider(taskProvider)
            if (useLintVitalPartialResults) {
                creationConfig.artifacts
                    .setInitialProvider(taskProvider, LintModelWriterTask::outputDirectory)
                    .on(InternalArtifactType.LINT_VITAL_LINT_MODEL)
            } else {
                registerOutputArtifacts(taskProvider, creationConfig.artifacts)
            }
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            // Do not export test sources between projects
            val variantWithoutTests = VariantWithTests(creationConfig, null, null)
            task.projectInputs.initialize(variantWithoutTests)
            task.variantInputs.initialize(
                variantWithoutTests,
                checkDependencies = checkDependencies,
                warnIfProjectTreatedAsExternalDependency = false,
                addBaseModuleLintModel = creationConfig is DynamicFeatureCreationConfig
            )
            task.partialResultsDir =
                creationConfig.artifacts.getOutputPath(
                    if (useLintVitalPartialResults) {
                        InternalArtifactType.LINT_VITAL_PARTIAL_RESULTS
                    } else {
                        InternalArtifactType.LINT_PARTIAL_RESULTS
                    },
                    AndroidLintAnalysisTask.PARTIAL_RESULTS_DIR_NAME
                )
            task.partialResultsDirPath = task.partialResultsDir.absolutePath
        }

        companion object {
            fun registerOutputArtifacts(
                taskProvider: TaskProvider<LintModelWriterTask>,
                artifacts: ArtifactsImpl
            ) {
                artifacts
                    .setInitialProvider(taskProvider, LintModelWriterTask::outputDirectory)
                    .on(InternalArtifactType.LINT_MODEL)
            }
        }
    }
}

