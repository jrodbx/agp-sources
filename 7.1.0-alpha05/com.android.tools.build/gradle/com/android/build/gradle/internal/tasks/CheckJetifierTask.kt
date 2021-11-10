/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.attribution.CheckJetifierBuildService
import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.ide.common.attribution.DependencyPath
import com.android.ide.common.attribution.FullDependencyPath
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.internal.deprecation.DeprecatableConfiguration

/**
 * Task to check whether Jetifier is needed for the current project. This is done by checking
 * whether the dependency graphs contain any legacy support libraries.
 *
 * Note: This task is currently not compatible with configuration caching as it requires resolving
 * dependency graphs and as of version 7.1 Gradle does not yet support that use case
 * (https://github.com/gradle/gradle/issues/12871).
 */
abstract class CheckJetifierTask : NonIncrementalGlobalTask() {

    @get:Internal // This task always runs
    abstract val jetifierEnabled: Property<Boolean>

    @get:Internal
    abstract val checkJetifierBuildService: Property<CheckJetifierBuildService>

    class CreationAction(
        globalScope: GlobalScope,
        private val projectOptions: ProjectOptions,
        private val checkJetifierBuildService: Provider<CheckJetifierBuildService>
    ) : GlobalTaskCreationAction<CheckJetifierTask>(globalScope) {

        override val name = "checkJetifier"
        override val type = CheckJetifierTask::class.java

        override fun configure(task: CheckJetifierTask) {
            super.configure(task)

            task.description = "Checks whether Jetifier is needed for the current project"
            task.group = JavaBasePlugin.VERIFICATION_GROUP

            task.jetifierEnabled.setDisallowChanges(projectOptions.get(BooleanOption.ENABLE_JETIFIER))
            task.checkJetifierBuildService.setDisallowChanges(checkJetifierBuildService)

            // This task should always run
            task.outputs.upToDateWhen { false }
        }
    }

    override fun doTaskAction() {
        if (!jetifierEnabled.get()) {
            logger.quiet("Skipping '$path' task as Jetifier is already disabled (${BooleanOption.ENABLE_JETIFIER.propertyName}=false).")
            return
        }

        val result = detect()

        reportToConsole(result)
        checkJetifierBuildService.get().addResult(result)
    }

    private fun detect(): CheckJetifierResult {
        val dependenciesDependingOnSupportLibs = LinkedHashMap<String, FullDependencyPath>()
        for (configuration in project.configurations) {
            for (pathToSupportLib in ConfigurationAnalyzer(configuration).findPathsToSupportLibs()) {
                val directDependency = pathToSupportLib.elements.first()
                // Store only one path, see `CheckJetifierResult`'s kdoc
                dependenciesDependingOnSupportLibs.computeIfAbsent(directDependency) {
                    FullDependencyPath(project.path, configuration.name, pathToSupportLib)
                }
            }
        }
        return CheckJetifierResult(dependenciesDependingOnSupportLibs)
    }

    private fun reportToConsole(result: CheckJetifierResult) {
        if (result.isEmpty()) {
            logger.quiet(
                "Project '${project.path}' does not use any legacy support libraries." +
                        " If this is the case for all other projects, you can disable Jetifier" +
                        " by setting ${BooleanOption.ENABLE_JETIFIER.propertyName}=false in gradle.properties.")
        } else {
            logger.quiet("" +
                    "The following libraries used by project '${project.path}' depend on legacy support libraries." +
                    " To disable Jetifier, you will need to use AndroidX-supported versions of these libraries.")
            logger.quiet("\t" + result.getDisplayString().replace("\n","\n\t"))
        }
    }
}

private class ConfigurationAnalyzer(private val configuration: Configuration) {

    /**
     * Finds direct dependencies of the given configuration that directly/transitively depend on
     * support libraries, or are support libraries themselves
     *
     * @return A list of [DependencyPath] from a direct dependency to a support library. There may
     *     be multiple paths from the direct dependency to a support library, but this method
     *     retains only one path.
     */
    fun findPathsToSupportLibs(): List<DependencyPath> {
        if (!configuration.isCanBeResolved) {
            return emptyList()
        }
        if (configuration is DeprecatableConfiguration && !configuration.canSafelyBeResolved()) {
            return emptyList()
        }

        val directDependencies =
            configuration.incoming.resolutionResult.root.dependencies.filter { dependency ->
                // Skip dependency constraints
                if (dependency.isConstraint) {
                    return@filter false
                }
                // Consider resolved Maven dependencies only
                return@filter (dependency is ResolvedDependencyResult)
                        && (dependency.selected.id is ModuleComponentIdentifier)
            }
        return directDependencies.mapNotNull { computePathToSupportLib(it) }
    }

    /** Caches the results of [computePathToSupportLib]. */
    private val pathsToSupportLibs: MutableMap<DependencyResult, DependencyPath?> = mutableMapOf()

    /** This is to prevent visiting cyclic dependencies in [computePathToSupportLib]. */
    private val dependenciesBeingAnalyzed: MutableSet<DependencyResult> = mutableSetOf()

    /**
     * Computes a [DependencyPath] from the given dependency to a support library. There may be
     * multiple paths, but this method retains only one path.
     *
     * If such a path is not found, this method returns `null`.
     */
    private fun computePathToSupportLib(dependency: DependencyResult): DependencyPath? {
        // Skip unresolved dependencies
        if (dependency !is ResolvedDependencyResult) {
            return null
        }

        if (pathsToSupportLibs.containsKey(dependency)) {
            return pathsToSupportLibs[dependency]
        }

        // This is to prevent visiting cyclic dependencies
        if (dependenciesBeingAnalyzed.contains(dependency)) {
            return null
        }
        dependenciesBeingAnalyzed.add(dependency)

        var dependencyPath: DependencyPath? = null
        if (AndroidXDependencySubstitution.isLegacySupportLibDependency(dependency.requested.displayName)) {
            // Support library found -- don't need to visit its children
            dependencyPath = DependencyPath(listOf(dependency.requested.displayName))
        } else {
            for (childDependency in dependency.selected.dependencies) {
                val childDependencyPath = computePathToSupportLib(childDependency)
                if (childDependencyPath != null) {
                    dependencyPath =
                        DependencyPath(listOf(dependency.selected.id.displayName) + childDependencyPath.elements)
                    // Just need to find one path
                    break
                }
            }
        }

        dependenciesBeingAnalyzed.remove(dependency)
        return dependencyPath.also { pathsToSupportLibs[dependency] = it }
    }
}
