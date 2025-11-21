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

// Utilities to examine a ResolutionResult
@file:JvmName("ResolutionResultUtils")

package com.android.build.gradle.internal.utils

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedComponentResult

/**
 * Returns module components in this [ResolutionResult] ([ResolvedComponentResult]s that have a
 * [ModuleComponentIdentifier]) which satisfy the given filter.
 *
 * Note that there is no guarantee on the order of this list, but in practice Gradle usually makes
 * sure that it is deterministic.
 */
fun ResolutionResult.getModuleComponents(
        moduleComponentFilter: (ModuleComponentIdentifier) -> Boolean = { true }
): List<ResolvedComponentResult> {
    return this.allComponents.filter { component ->
        val id = component.id
        id is ModuleComponentIdentifier && moduleComponentFilter(id)
    }
}

/**
 * Returns a [ComponentPath] starting from the root of this [ResolutionResult] to the given
 * component.
 *
 * Note that there can be multiple paths, and this method returns only one of the paths. The result
 * is stable across invocations.
 */
fun ResolutionResult.getPathToComponent(component: ResolvedComponentResult): ComponentPath {
    // Note that there may be cyclic dependencies (see bug 184406667), so we'll need to perform a
    // graph search. Below we'll use standard Depth-First Search (Breadth-First Search would be
    // slower in this case).
    // Also, we'll find a path from component to root as it's faster, and we'll reconstruct the path
    // from root to component after that.
    var foundPathToRoot: List<ResolvedComponentResult>? = null
    fun findPathToRoot(
        nodeToVisit: ResolvedComponentResult,
        path: MutableList<ResolvedComponentResult>,
        visitedNodes: MutableSet<ResolvedComponentResult>
    ) {
        if (nodeToVisit in visitedNodes) return
        path.add(nodeToVisit)
        visitedNodes.add(nodeToVisit)

        // We need to compare by ResolvedComponentResult.id because Gradle may create different
        // objects with the same id (e.g., each invocation of ResolutionResult.allComponents returns
        // a set of new objects, but their ids are the same across invocations).
        if (nodeToVisit.id == root.id) {
            foundPathToRoot = path.toList()
        } else {
            nodeToVisit.dependents.forEach {
                findPathToRoot(it.from, path, visitedNodes)
                if (foundPathToRoot != null) return@forEach
            }
        }
        path.removeLast()
    }

    findPathToRoot(component, mutableListOf(), mutableSetOf())

    checkNotNull(foundPathToRoot) {
        "Unable to find a path from root (${root.id.displayName}) to ${component.id.displayName}"
    }
    return ComponentPath(foundPathToRoot!!.reversed())
}

/**
 * A path consisting of [ResolvedComponentResult]s starting from the root of a [ResolutionResult] to
 * a component.
 */
class ComponentPath(
        val components: List<ResolvedComponentResult>
) {

    /**
     * Returns a string describing this [ComponentPath].
     *
     * Note that the root of the [ResolutionResult] may not be in a user-friendly format (e.g.,
     * ":<project-name>:unspecified"), so the caller of this method can provide a
     * `rootDisplayNameOverride` (e.g., "Project <name>" or "Configuration <name>") to substitute
     * it.
     *
     * @param rootDisplayNameOverride a display name substitute for the root of the [ResolutionResult]
     */
    fun getPathString(
            rootDisplayNameOverride: String? = null,
            separator: String = " -> "
    ): String {
        val path = this.components.map { it.id.displayName }
        val adjustedPath = if (rootDisplayNameOverride != null) {
            listOf(rootDisplayNameOverride) + path.drop(1)
        } else {
            path
        }
        return adjustedPath.joinToString(separator)
    }
}
