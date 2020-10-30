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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollectionsInputs
import com.android.build.gradle.internal.ide.dependencies.LibraryDependencyCacheBuildService
import com.android.build.gradle.internal.ide.dependencies.getDependencyGraphBuilder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.tools.lint.model.LintModelDependencies
import com.android.tools.lint.model.LintModelSerialization
import com.google.common.collect.ImmutableMap
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/**
 * Task to write the [LintModelDependencies] representation of the variant dependencies on disk.
 *
 * This serialized [LintModelDependencies] file is then consumed by Lint in consuming projects to get
 * all the information about this variant's dependencies.
 */
abstract class LintModelDependenciesWriterTask : NonIncrementalTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val libraryDependencyCacheBuildService: Property<LibraryDependencyCacheBuildService>

    @get:Nested
    abstract val artifactCollectionsInputs: Property<ArtifactCollectionsInputs>

    @get:Input
    val variantNameInput: String
        get() = variantName

    override fun doTaskAction() {
        val modelBuilder = LintDependencyModelBuilder(
            libraryDependencyCacheBuildService.get().localJarCache,
            artifactCollectionsInputs.get().mavenCoordinatesCache.get().cache
        )
        val graph = getDependencyGraphBuilder()
        val issueReporter = object : IssueReporter() {
            override fun reportIssue(
                type: Type,
                severity: Severity,
                exception: EvalIssueException
            ) {
                if (severity == Severity.ERROR) {
                    throw exception
                }
            }

            override fun hasIssue(type: Type) = false
        }

        graph.createDependencies(
            modelBuilder = modelBuilder,
            artifactCollectionsProvider = artifactCollectionsInputs.get(),
            withFullDependency = true,
            buildMapping = ImmutableMap.of(),
            issueReporter = issueReporter
        )

        val model: LintModelDependencies = modelBuilder.createModel()

        val adapter =
            LintModelSerialization.LintModelSerializationFileAdapter(
                outputDirectory.get().asFile
            )

        LintModelSerialization.writeDependencies(model, adapter, variantName)

        LintModelSerialization.writeLibraries(
            model.getLibraryResolver(),
            adapter,
            variantName
        )
    }

    class CreationAction(
        creationConfig: VariantCreationConfig
    ) : VariantTaskCreationAction<LintModelDependenciesWriterTask, VariantCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("generate", "DependenciesForLint")
        override val type: Class<LintModelDependenciesWriterTask>
            get() = LintModelDependenciesWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<LintModelDependenciesWriterTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(
                    taskProvider,
                    LintModelDependenciesWriterTask::outputDirectory
                )
                .on(InternalArtifactType.LINT_MODEL_DEPENDENCIES)
        }

        override fun configure(task: LintModelDependenciesWriterTask) {
            super.configure(task)
            task.libraryDependencyCacheBuildService.setDisallowChanges(
                getBuildService(creationConfig.services.buildServiceRegistry)
            )
            task.artifactCollectionsInputs.set(
                ArtifactCollectionsInputs(
                    variantDependencies = creationConfig.variantDependencies,
                    projectPath = creationConfig.globalScope.project.path,
                    variantName = creationConfig.name,
                    runtimeType = ArtifactCollectionsInputs.RuntimeType.FULL,
                    mavenCoordinatesCache = getBuildService(creationConfig.services.buildServiceRegistry)
                )
            )
        }
    }
}