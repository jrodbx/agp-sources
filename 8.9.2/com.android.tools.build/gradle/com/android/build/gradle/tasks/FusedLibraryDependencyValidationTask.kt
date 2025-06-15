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

import com.android.build.gradle.internal.dsl.ModulePropertyKey
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
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
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
abstract class FusedLibraryDependencyValidationTask : NonIncrementalGlobalTask() {

    /**
     * Contains the resolved dependencies specified in the 'include' configuration.
     */
    @get:Input
    abstract val resolvedIncludeDependencies: Property<ResolvedComponentResult>

    /**
     * Controls whether this task causes a build failure if there is a check failure.
     */
    @get:Input
    abstract val ignoreFailures: Property<Boolean>

    /**
     * Output directory to for the task to report up-to-date, contents will always be empty.
     *
     * Output is optional as a dependency is explicitly added for some maven-publish plugin tasks.
     */
    @get:OutputDirectory
    @get:Optional
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        if (ignoreFailures.get()) return
        checkDependencies(resolvedIncludeDependencies.get())
    }

    /**
     * Runs a series of test cases on each dependency of the fused library to prevent
     * misconfigurations.
     */
    private fun checkDependencies(
        includeDependencies: ResolvedComponentResult,
    ) {
        val mergedDependencies: Set<ComponentIdentifier?> = includeDependencies.dependencies
            .map {
                if (it is ResolvedDependencyResult) {
                    it.selected.id
                } else {
                    null
                }
            }
            .toSet()

        val checks: List<Pair<String, (DependencyResultWithParentId) -> ValidationCheck.Result>> = listOf(
            "Unresolved Dependencies" to {
                when (it.dependency) {
                    is UnresolvedDependencyResult -> {
                        ValidationCheck.Result.Invalid(
                            it.dependency.failure.message
                                ?: it.dependency.failure.stackTraceToString()
                        )
                    }
                    is ResolvedDependencyResult -> {
                        ValidationCheck.Result.Valid
                    }
                    else -> {
                        ValidationCheck.Result.DidNotComplete(
                            "${it.dependency.javaClass} is not supported by this check.")
                    }
                }
            },
            "Databinding is not supported by Fused Library modules" to {
                when (it.dependency) {
                    is ResolvedDependencyResult -> {
                        val id = it.dependency.selected.id
                        if (id is ModuleComponentIdentifier &&
                            id.group in setOf("androidx.databinding", "com.android.databinding")
                        ) {
                            ValidationCheck.Result.Invalid(
                                "${id.moduleIdentifier} is not a permitted dependency."
                            )
                        } else {
                            ValidationCheck.Result.Valid
                        }
                    }
                    else -> {
                        ValidationCheck.Result.DidNotComplete(
                            "${it.dependency.javaClass} is not supported by this check.")
                    }
                }
            },
            "Require transitive dependency inclusion" to {
                when (it.dependency) {
                    is ResolvedDependencyResult -> {
                        // Check case where :libA <- :libB <- :libC, where only :libA and :libC are included
                        // i.e., a not included component must not have a dependency on an included component
                        // This check prevents cyclic dependencies.
                        val parent = it.parentId
                        val id = it.dependency.selected.id
                        if (id in mergedDependencies && parent !in mergedDependencies) {
                            ValidationCheck.Result.Invalid(
                                "${id.displayName} is included in the fused library .aar, " +
                                        "however its parent dependency ${parent?.displayName} was not."
                            )
                        } else {
                            ValidationCheck.Result.Valid
                        }
                    }
                    else -> {
                        ValidationCheck.Result.DidNotComplete(
                            "${it.dependency.javaClass} is not supported by this check."
                        )
                    }
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
            .map {
                if (it is ResolvedDependencyResult) {
                    DependencyResultWithParentId(it, it.selected.id)
                } else {
                    DependencyResultWithParentId(it, null)
                }
            }
            .toMutableList()
        val seen = mutableSetOf<DependencyResultWithParentId>()
        val failedChecks = mutableMapOf<String, Set<String>>()
        val notSupportedChecks = mutableMapOf<String, Set<String>>()

        while (next.any()) {
            val dependencyWithParentId = next.first()
            val dependency = dependencyWithParentId.dependency

            resolvableDependencyChecks
                .map { it.name to it.getResult(dependencyWithParentId) }
                .forEach { (checkName, result) ->
                    when (result) {
                        is ValidationCheck.Result.Invalid -> {
                            failedChecks[checkName] =
                                (notSupportedChecks[checkName] ?: emptySet()) +
                                        result.message
                        }
                        is ValidationCheck.Result.DidNotComplete -> {
                            notSupportedChecks[checkName] =
                                (notSupportedChecks[checkName] ?: emptySet()) +
                                        result.reason
                        }
                        is ValidationCheck.Result.Valid -> {}
                    }

                }

            seen.add(dependencyWithParentId)
            if (dependency is ResolvedDependencyResult) {
                next.addAll(
                    dependency.selected.dependencies
                        .map { DependencyResultWithParentId(it, dependency.selected.id) }
                        .filterNot { it in seen }
                )
            }
            next.remove(dependencyWithParentId)
        }

        if (failedChecks.none()) return
        val errorStr =
            "Validation failed due to ${failedChecks.size} issue(s) with ${projectPath.get()} dependencies:\n" +
                    failedChecks.keys.joinToString {
                        " [$it]:\n${
                            failedChecks[it]
                                ?.joinToString(prefix = "  * ", separator = "\n  * ")
                        }\n"
                    } + if (notSupportedChecks.any())
                "The following checks did not finish:\n" + notSupportedChecks.keys.joinToString(
                    separator = ""
                ) {
                    " [$it]:\n${
                        notSupportedChecks[it]
                            ?.joinToString(prefix = "  * ", separator = "\n  * ")
                    }\n"
                } else ""
        throw IllegalStateException(errorStr)
    }

    private data class DependencyResultWithParentId(
        val dependency: DependencyResult,
        val parentId: ComponentIdentifier?
    )

    private class ValidationCheck(
        val name: String,
        val check: (dependency: DependencyResultWithParentId) -> Result
    ) {
        fun getResult(dependencyResultWithParentId: DependencyResultWithParentId): Result {
            return check(dependencyResultWithParentId)
        }

        sealed class Result {
            // The check condition is true. No action from the user required.
            data object Valid : Result()
            // The check condition is false. A message should provide the user details of the
            // dependency impacted and options for the user to resolve.
            data class Invalid(val message: String) : Result()
            // The check did not reach a conclusion if the dependency is valid or not.
            data class DidNotComplete(val reason: String) : Result()
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
            task.ignoreFailures.setDisallowChanges(
                creationConfig.experimentalProperties.map {
                    !ModulePropertyKey.BooleanWithDefault.FUSED_LIBRARY_VALIDATE_DEPENDENCIES
                        .getValue(creationConfig.experimentalProperties.get())
                }
            )
        }
    }
}
