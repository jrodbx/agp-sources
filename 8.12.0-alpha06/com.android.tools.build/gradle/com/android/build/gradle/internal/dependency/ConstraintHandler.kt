/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ConstraintHandler")
package com.android.build.gradle.internal.dependency

import com.android.builder.errors.IssueReporter
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

internal fun checkConfigurationAlignments(
        destConfiguration: Configuration,
        srcConfiguration: Configuration,
        issueReporter: IssueReporter,
        buildFile: File) {
    destConfiguration.incoming.afterResolve { dest ->
        val moduleToProjectSubstitutionMap =
                srcConfiguration.incoming.resolutionResult.allDependencies
                        .filterIsInstance<ResolvedDependencyResult>()
                        .filter {
                            it.selected.id is ProjectComponentIdentifier && it.requested is ModuleComponentSelector
                        }
                        .associate {
                            (it.requested as ModuleComponentSelector).moduleIdentifier to
                                    (it.selected.id as ProjectComponentIdentifier).projectPath
                        }
        // In that case assert the substitution was also done in the aligned configuration
        val neededAndroidTestProjectDeps = dest.resolutionResult.allDependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .filter { dependency ->
                    val componentIdentifier = dependency.selected.id
                    // Not aligned if a module has not been substituted for a project in the srcConfig
                    componentIdentifier is ModuleComponentIdentifier &&
                            componentIdentifier.moduleIdentifier in moduleToProjectSubstitutionMap
                }
                .map { dependency ->
                    val componentIdentifier =
                            dependency.selected.id as ModuleComponentIdentifier
                    moduleToProjectSubstitutionMap[componentIdentifier.moduleIdentifier]
                }.toSet()

        if (neededAndroidTestProjectDeps.none()) {
            return@afterResolve
        }
        val dependencyNumber =
                if (neededAndroidTestProjectDeps.size > 1) "dependencies" else "dependency"
        issueReporter.reportError(
                IssueReporter.Type.DEPENDENCY_INTERNAL_CONFLICT,
                RuntimeException("Unable to align dependencies in configurations " +
                        "'${srcConfiguration.name}' and '${dest.name}', as both require " +
                        "${neededAndroidTestProjectDeps.joinToString(", ") { "'project $it'" }}.\n" +
                        "[Recommended action] Add the following $dependencyNumber to $buildFile: \n${
                            neededAndroidTestProjectDeps.joinToString("\n") { "androidTestImplementation(project(\"$it\"))" }
                        }"
                )
        )
    }
}
