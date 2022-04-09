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

import com.android.build.gradle.internal.services.StringCachingBuildService
import com.android.build.gradle.internal.ide.dependencies.getIdString
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/**
 * Synchronizes this configuration to the specified one, so they resolve to the same dependencies.
 *
 * It does that by leveraging [ResolvableDependencies.beforeResolve].
 */
internal fun Configuration.alignWith(
    srcConfiguration: Configuration,
    dependencyHandler: DependencyHandler,
    isTest: Boolean,
    cachedStringBuildService: Provider<StringCachingBuildService>
) {
    incoming.beforeResolve {
        val srcConfigName = srcConfiguration.name

        val configName = this.name
        val stringCachingService = cachedStringBuildService.get()

        srcConfiguration.incoming.resolutionResult.allDependencies { dependency ->
            if (dependency is ResolvedDependencyResult) {
                val componentIdentifier = dependency.selected.id
                if (componentIdentifier is ModuleComponentIdentifier) {
                    // using a repository with a flatDir to stock local AARs will result in an
                    // external module dependency with no version.
                    if (!componentIdentifier.version.isNullOrEmpty()) {
                        if (!isTest || componentIdentifier.module != "listenablefuture" || componentIdentifier.group != "com.google.guava" || componentIdentifier.version != "1.0") {
                            dependencyHandler.constraints.add(
                                configName,
                                "${componentIdentifier.group}:${componentIdentifier.module}:${componentIdentifier.version}"
                            ) { constraint ->
                                constraint.because(stringCachingService.cacheString("$srcConfigName uses version ${componentIdentifier.version}"))
                                constraint.version { versionConstraint ->
                                    versionConstraint.strictly(componentIdentifier.version)
                                }
                            }
                        }
                    }
                } else if (componentIdentifier is ProjectComponentIdentifier
                    && componentIdentifier.build.isCurrentBuild
                    && dependency.requested is ModuleComponentSelector
                ) {
                    // Requested external library has been replaced with the project dependency,
                    // add the same substitution to the target configuration, so it can be chosen
                    // instead of the external library as well.
                    // We should avoid doing this for composite builds, so we check if the selected
                    // project is from the current build.
                    resolutionStrategy.dependencySubstitution.let { sb ->
                        sb.substitute(dependency.requested)
                            .because(stringCachingService.cacheString(
                                "$srcConfigName uses project ${componentIdentifier.getIdString()}"))
                            .using(sb.project(componentIdentifier.getIdString()))
                    }
                }
            }
        }
    }
}
