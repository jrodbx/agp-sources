/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fusedlibrary.FusedLibraryConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.VerificationTask

/**
 * Intended to prevent building a fused library in an invalid state due to misconfiguration
 * of included dependencies.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION, secondaryTaskCategories = [TaskCategory.FUSING])
abstract class FusedLibraryDependencyValidationTask : NonIncrementalGlobalTask(), VerificationTask {

    /**
     * Contains the resolved dependencies specified in the 'include' configuration.
     */
    @get:Input
    abstract val resolvedIncludeDependencies: Property<ResolvedComponentResult>

    /**
     * Output directory to for the task to report up-to-date, contents will always be empty.
     *
     * Output is optional as a dependency is explicitly added for some maven-publish plugin tasks.
     */
    @get:OutputDirectory
    @get:Optional
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        if (ignoreFailures) return
        checkDependencies(resolvedIncludeDependencies.get())
    }

    /**
     * Runs a series of test cases on each dependency of the fused library to prevent
     * misconfigurations.
     */
    private fun checkDependencies(
        includeDependencies: ResolvedComponentResult,
    ) {
        val mergedDependencies: Set<ComponentIdentifier> = includeDependencies.dependencies
            .map { (it as ResolvedDependencyResult).selected.id }
            .toSet()

        val checks: List<Pair<String, (DependencyWithParent) -> ValidationCheck.Result>> = listOf(
            "Databinding is not supported by Fused Library modules" to {
                dependencyWithParent: DependencyWithParent ->
                val id = dependencyWithParent.dependency.selected.id
                if (id is ModuleComponentIdentifier &&
                    id.group in setOf("androidx.databinding", "com.android.databinding")
                ) {
                    ValidationCheck.Result.Invalid(
                        "${id.moduleIdentifier} is not a permitted dependency."
                    )
                } else {
                    ValidationCheck.Result.Valid
                }
            },
            "Require transitive dependency inclusion" to {
                dependencyWithParent: DependencyWithParent ->
                // Check case where :libA <- :libB <- :libC, where only :libA and :libC are included
                // i.e., a not included component must not have a dependency on an included component
                // This check prevents cyclic dependencies.
                val parent = dependencyWithParent.parentId
                val id = dependencyWithParent.dependency.selected.id
                if (id in mergedDependencies && parent !in mergedDependencies) {
                    ValidationCheck.Result.Invalid(
                        "${id.displayName} is included in the fused library .aar, " +
                                "however its parent dependency ${parent.displayName} was not."
                    )
                } else {
                    ValidationCheck.Result.Valid
                }
            }
        )

        doChecks(
            includeDependencies,
            checks.map { ValidationCheck(it.first, it.second) }
        )
    }

    private fun doChecks(
        includeDependencies: ResolvedComponentResult,
        resolvableDependencyChecks: List<ValidationCheck>,
    ) {
        val next = includeDependencies.dependencies
            .map { DependencyWithParent(it as ResolvedDependencyResult, it.selected.id) }
            .toMutableList()
        val seen = mutableSetOf<DependencyWithParent>()
        val failedChecks = mutableMapOf<String, Set<String>>()

        while (next.any()) {
            val dependency = next.first()
            val selected = dependency.dependency.selected

            resolvableDependencyChecks
                .map { it.name to it.getResult(dependency) }
                .filter { (_, result) -> result is ValidationCheck.Result.Invalid }
                .forEach { (checkName, invalid) ->
                    failedChecks[checkName] = (failedChecks[checkName] ?: emptySet()) +
                            (invalid as ValidationCheck.Result.Invalid).message
                }

            seen.add(dependency)
            next.addAll(
                selected.dependencies
                    .map { DependencyWithParent(it as ResolvedDependencyResult, selected.id) }
                    .filterNot { it in seen }
            )
            next.remove(dependency)
        }

        if (failedChecks.none()) return
        val errorStr =
            "Validation failed due to ${failedChecks.size} issue(s) with ${projectPath.get()} dependencies:\n" +
                    failedChecks.keys.joinToString {
                        " [$it]:\n${
                            failedChecks[it]
                                ?.joinToString(prefix = "  * ", separator = "\n  * ")
                        }\n"
                    }
        throw IllegalStateException(errorStr)
    }

    private data class DependencyWithParent(
        val dependency: ResolvedDependencyResult,
        val parentId: ComponentIdentifier
    )

    private class ValidationCheck(
        val name: String,
        val check: (dependency: DependencyWithParent) -> Result
    ) {
        fun getResult(dependencyWithParent: DependencyWithParent): Result {
            return check(dependencyWithParent)
        }

        sealed class Result {
            data object Valid : Result()
            data class Invalid(val message: String) : Result()
        }
    }

    class CreationAction(val creationConfig: FusedLibraryGlobalScope) :
        GlobalTaskCreationAction<FusedLibraryDependencyValidationTask>() {

        override val name: String
            get() = "validateDependencies"
        override val type: Class<FusedLibraryDependencyValidationTask>
            get() = FusedLibraryDependencyValidationTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<FusedLibraryDependencyValidationTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryDependencyValidationTask::outputDirectory
            ).on(FusedLibraryInternalArtifactType.DEPENDENCY_VALIDATION)
        }

        override fun configure(task: FusedLibraryDependencyValidationTask) {
            super.configure(task)
            val includeConfiguration = task.project.configurations
                .getByName(FusedLibraryConstants.INCLUDE_CONFIGURATION_NAME)
            task.resolvedIncludeDependencies.setDisallowChanges(
                includeConfiguration.incoming.resolutionResult.rootComponent
            )
            // b/378080572 will add support for disabling validation if required.
            task.ignoreFailures = false
        }
    }
}
