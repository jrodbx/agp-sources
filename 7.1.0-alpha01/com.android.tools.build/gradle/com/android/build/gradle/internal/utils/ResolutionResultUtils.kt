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
 * Returns a [ComponentPath] starting from the root of a [ResolutionResult] to this component.
 *
 * Note that there can be multiple paths from root to the component, and this method returns only
 * the first path (as returned by Gradle). In practice, this path is usually the shortest path, but
 * there is no guarantee from the Gradle API.
 */
fun ResolvedComponentResult.getPathFromRoot(): ComponentPath {
    val components = mutableListOf(this)
    var current = this
    while (current.dependents.isNotEmpty()) {
        // Select only the first parent of this component
        val parent = current.dependents.first().from
        components.add(parent)
        if (parent == current) {
            // It's possible that parent == current. For example, if configuration
            // `debugAndroidTestRuntimeClasspath` of `project :foo` has a dependency on
            // `project :foo`, then parent == current == `project: foo`.
            // In that case, parent should be root, and we can stop here.
            break
        } else {
            current = parent
        }
    }
    // Reverse the list as we want the root to be the first element
    components.reverse()
    return ComponentPath(components)
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
