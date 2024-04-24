/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.fusedlibrary

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.specs.Spec

class SegregatingConstraintHandler(
    private val incomingConfiguration: Configuration,
    private val targetConfiguration: Configuration,
    private val mergeSpec: Spec<ComponentIdentifier>,
    private val project: Project,
): Action<ResolvableDependencies> {

    override fun execute(
        resolvableDependencies: ResolvableDependencies,
    ) {
        println("constrained include : BEFORE RESOLVED ${incomingConfiguration.name} -> ${targetConfiguration.name}")
        incomingConfiguration.incoming.resolutionResult.allDependencies { dependency ->
            when (dependency) {
                is ModuleDependency -> println("ModuleDep !")
                is ResolvedDependencyResult -> {
                    if (mergeSpec.isSatisfiedBy(dependency.selected.id)) {
                        println("Removing merged dependency : ${dependency.selected.id}")
                    } else {
                        println("Keeping un-merged dependency : ${dependency.selected.id}")
                        project.dependencies.add(
                            targetConfiguration.name,
                            dependency.selected.id.toString()
                        )
                    }
                }
                else -> println("other : ${dependency.javaClass}")
            }
        }
    }
}
