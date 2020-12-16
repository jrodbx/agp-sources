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

package com.android.build.gradle.internal.utils

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Visitor of a [ResolutionResult] dependency graph which exposes the path leading to each visited
 * dependency (see [visitDependency]).
 */
abstract class PathAwareDependencyGraphVisitor(

        /**
         * Whether to visit a dependency only once if it appears multiple times in the dependency
         * graph.
         */
        private val visitOnlyOnce: Boolean = false
) {
    private val visitedDependencies: MutableSet<ResolvedComponentResult>? = if (visitOnlyOnce) {
        mutableSetOf()
    } else null

    fun visit(resolutionResult: ResolutionResult) {
        visitDependencyRecursively(resolutionResult.root, listOf())
    }

    @VisibleForTesting
    internal fun visitDependencyRecursively(dependency: ResolvedComponentResult, parentPath: List<ResolvedComponentResult>) {
        if (visitOnlyOnce && !visitedDependencies!!.add(dependency)) {
            return
        }

        visitDependency(dependency, parentPath)

        val dependencyPath = parentPath + dependency
        for (childDependency in dependency.dependencies) {
            visitDependencyRecursively((childDependency as ResolvedDependencyResult).selected, dependencyPath)
        }
    }

    /**
     * Visits a dependency.
     *
     * @param dependency the dependency being visited
     * @param parentPath the list of dependencies from the root of the dependency graph leading up
     *     to the parent of the currently visited dependency
     */
    abstract fun visitDependency(dependency: ResolvedComponentResult, parentPath: List<ResolvedComponentResult>)
}