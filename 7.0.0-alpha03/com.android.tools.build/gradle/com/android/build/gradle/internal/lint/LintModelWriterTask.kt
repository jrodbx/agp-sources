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

import com.android.build.gradle.internal.component.ConsumableCreationConfig
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
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/**
 * Task to write the [LintModelModule] representation of one variant of a Gradle project to disk.
 *
 * When checkDependencies is used in a consuming project, this serialized [LintModelModule] file is
 * read by Lint in consuming projects to get all the information about this variant in project.
 *
 * This is an interim solution, eventually lint will run all checks locally in each project,
 * and export partial results to be interpreted and merged.
 */
abstract class LintModelWriterTask : NonIncrementalTask() {

    @get:Nested
    abstract val projectInputs: ProjectInputs

    @get:Nested
    abstract val variantInputs: VariantInputs

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val module = projectInputs.convertToLintModelModule()
        val variant = variantInputs.toLintModel(module)
        LintModelSerialization.writeModule(
            module = module,
            destination = outputDirectory.get().asFile,
            writeVariants = listOf(variant),
            writeDependencies = true
        )
    }

    fun configureForStandalone(
        project: Project,
        projectOptions: ProjectOptions,
        javaConvention: JavaPluginConvention,
        customLintChecks: FileCollection,
        lintOptions: LintOptions
    ) {
        this.group = JavaBasePlugin.VERIFICATION_GROUP
        this.variantName = ""
        this.analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        this.projectInputs.initializeForStandalone(project, javaConvention, lintOptions)
        this.variantInputs.initializeForStandalone(project, javaConvention, projectOptions, customLintChecks, lintOptions)
    }

    class CreationAction(
        creationConfig: ConsumableCreationConfig
    ) : VariantTaskCreationAction<LintModelWriterTask, ConsumableCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("generate", "LintModel")
        override val type: Class<LintModelWriterTask>
            get() = LintModelWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelWriterTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, LintModelWriterTask::outputDirectory)
                .on(InternalArtifactType.LINT_MODEL)
        }

        override fun configure(task: LintModelWriterTask) {
            super.configure(task)
            // Do not export test sources between projects
            val variantWithoutTests = VariantWithTests(creationConfig, null, null)
            task.projectInputs.initialize(variantWithoutTests)
            task.variantInputs.initialize(
                variantWithoutTests,
                // The artifact produced is only used by lint tasks with checkDependencies=true
                checkDependencies = true
            )
        }
    }
}

