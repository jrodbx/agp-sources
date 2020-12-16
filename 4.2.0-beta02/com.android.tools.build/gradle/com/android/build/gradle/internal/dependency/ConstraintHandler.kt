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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.services.StringCachingBuildService
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/**
 * An Action to synchronize a dependency with a runtimeClasspath.
 *
 * This is meant to be passed to [ResolvableDependencies.beforeResolve]
 */
class ConstraintHandler(
    private val srcConfiguration: Configuration,
    private val dependencyHandler: DependencyHandler,
    private val isTest: Boolean,
    private val cachedStringBuildService: Provider<StringCachingBuildService>
) : Action<ResolvableDependencies> {
    override fun execute(resolvableDependencies: ResolvableDependencies) {
        val srcConfigName = srcConfiguration.name

        val configName = resolvableDependencies.name
        val stringCachingService = cachedStringBuildService.get()

        srcConfiguration.incoming.resolutionResult.allDependencies { dependency ->
            if (dependency is ResolvedDependencyResult) {
                val id = dependency.selected.id
                if (id is ModuleComponentIdentifier) {
                    // using a repository with a flatDir to stock local AARs will result in an
                    // external module dependency with no version.
                    if (!id.version.isNullOrEmpty()) {
                        if (!isTest || id.module != "listenablefuture" || id.group != "com.google.guava" || id.version != "1.0") {
                            dependencyHandler.constraints.add(
                                configName,
                                "${id.group}:${id.module}:${id.version}"
                            ) { constraint ->
                                constraint.because(stringCachingService.cacheString("$srcConfigName uses version ${id.version}"))
                                constraint.version { versionConstraint ->
                                    versionConstraint.strictly(id.version)
                                }
                            }
                        }
                    }
                } else if (id is ProjectComponentIdentifier
                    && id.build.isCurrentBuild
                    && dependency.requested is ModuleComponentSelector
                ) {
                    // Requested external library has been replaced with the project dependency, so
                    // add the project dependency to the target configuration, so it can be chosen
                    // instead of the external library as well.
                    // We should avoid doing this for composite builds, so we check if the selected
                    // project is from the current build.
                    dependencyHandler.add(
                        configName,
                        dependencyHandler.project(mapOf("path" to id.projectPath))
                    )
                }
            }
        }
    }
}