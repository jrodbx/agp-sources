/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
import java.io.File
import java.util.LinkedList

/** Finds used/unused dependencies in our variant. */
class DependencyUsageFinder(
    private val classFinder: ClassFinder,
    private val variantClasses: AnalyzeDependenciesTask.VariantClassesHolder,
    private val variantDependencies: AnalyzeDependenciesTask.VariantDependenciesHolder) {

    /** All the dependencies required across our code base. */
    val requiredDependencies: Set<String> =
        variantClasses.getUsedClasses().mapNotNull { classFinder.find(it) }.toSet()

    /** Dependencies we direct declare and are being used. */
    val usedDirectDependencies: Set<String> =
        variantDependencies.all.intersect(requiredDependencies)

    /** Dependencies we direct declare and are not being used. */
    val unusedDirectDependencies: Set<String> =
        variantDependencies.all.minus(requiredDependencies)
}

/** Find required dependencies that are being included indirectly and would be unreachable if
 *  we remove unused direct dependencies. */
class DependencyGraphAnalyzer(
    private val configuration: Configuration,
    private val depsUsageFinder: DependencyUsageFinder) {

    // Keep a map from dependencyId to the correspondent RenderableDependency
    val renderableDependencies = mapIdsToRenderableDependencies()

    // TODO: Handle 'project' dependencies
    fun findIndirectRequiredDependencies(): Set<String> {
        /* Get the ids of all required dependencies that are:
           - valid (they map to a valid RenderableDependency in the renderableDependencies hashmap)
           - not 'project' dependencies */
        val requiredRenderableDependencies = depsUsageFinder.requiredDependencies
            .asSequence()
            .mapNotNull { renderableDependencies[it] }
            .filterNot { it.id is ProjectComponentIdentifier }
            .map { (it.id as ComponentIdentifier).displayName }.toSet()

        /* From the remaining ones, find those that are still available to the module
           (those that can still be reached in the dependency graph) */
        val accessibleDependencies = findAccessibleDependencies()

        return requiredRenderableDependencies.minus(accessibleDependencies)
    }

    private fun findAccessibleDependencies (): Set<String> {
        // Traverse the dependency tree to find the ones that are still accessible
        val visited = mutableSetOf<String>()
        val queue = LinkedList<String>()

        // Initially, Add all direct dependencies in the Queue
        depsUsageFinder.usedDirectDependencies.forEach {
            if (renderableDependencies.containsKey(it)) {
                queue.add(it)
            }
        }

        // Do a BFS to find the reachable (visited) dependencies
        while (!queue.isEmpty()) {
            val componentIdentifier = queue.pop()
            val dependency = renderableDependencies[componentIdentifier]
            visited.add(componentIdentifier)
            dependency?.children?.forEach {
                val childComponentIdentifier = (it.id as ComponentIdentifier).displayName
                if (!visited.contains(childComponentIdentifier)) {
                    queue.push(childComponentIdentifier)
                }
            }
        }

        return visited
    }

    private fun mapIdsToRenderableDependencies(): Map<String, RenderableDependency> {
        val dependencyGraph = configuration.incoming.resolutionResult.root
        val renderableGraph = RenderableModuleResult(dependencyGraph)
        val renderableDependencies = mutableMapOf<String, RenderableDependency>()

        // Map id to the correspondent RenderableDependency
        renderableGraph.children.forEach {
            val componentIdentifier = it.id as ComponentIdentifier
            renderableDependencies[componentIdentifier.displayName] = it
        }

        return renderableDependencies
    }
}

private class ArtifactFinder(private val externalArtifactCollection: ArtifactCollection) {
    fun getMapByFileName(fileName: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        externalArtifactCollection
                .forEach { artifact ->
                    FileUtils.join(artifact.file, fileName)
                            .forEachLine { artifactFileLine ->
                                map[artifactFileLine] = artifact.id.componentIdentifier.displayName
                            }
                }
        return map
    }
}

/** Finds where a class is coming from. */
class ClassFinder(private val externalArtifactCollection : ArtifactCollection) {

    private val classToDependency: Map<String, String> by lazy {
        val artifactFinder = ArtifactFinder(externalArtifactCollection)
        artifactFinder.getMapByFileName("classes${SdkConstants.DOT_TXT}")
    }

    /** Returns the dependency that contains {@code className} or null if we can't find it. */
    fun find(className: String) = classToDependency[className]

    fun findClassesInDependency(dependencyId: String) =
        classToDependency.filterValues { it == dependencyId }.keys
}

class ResourcesFinder(private val externalArtifactCollection: ArtifactCollection) {

    private val resourceToDependency: Map<String, List<String>> by lazy {
        getMapByFileName("resources_symbols${SdkConstants.DOT_TXT}")
    }

    private fun getMapByFileName(fileName: String): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()

        externalArtifactCollection
                .forEach { artifact ->
                    val resourceSymbols = FileUtils.join(artifact.file, fileName).readLines()
                    resourceSymbols.forEach { artifactFileLine ->
                        val resDeps = map.getOrDefault(artifactFileLine, emptyList())
                        map[artifactFileLine] =
                                resDeps + artifact.id.componentIdentifier.displayName
                    }
                }
        return map
    }

    /**
     * Returns a list of dependencies which contain the resource, otherwise returns an empty list.
     */
    fun find(resourceId: String): List<String> =
            resourceToDependency[resourceId] ?: emptyList()

    /**
     * Returns a list of resources which are declared or referenced by the dependency.
     */
    fun findResourcesInDependency(dependencyId: String) =
            resourceToDependency.filterValues { it.contains(dependencyId) }.keys

    /**
     * Returns a list of dependencies which contains an identical resource in another dependency.
     */
    fun findUsedDependencies(): List<String> =
            resourceToDependency.filter { it.value.size > 1 }.flatMap { it.value }

    /**
     * Returns a list of dependencies which do not contain an identical resource in another
     * dependency.
     */
    fun findUnUsedDependencies(): List<String> =
            resourceToDependency.flatMap { it.value }.minus(findUsedDependencies())
}

data class DependenciesUsageReport (
        @SerializedName("add") val add : List<String>,
        @SerializedName("remove") val remove : List<String>
)

class DependencyUsageReporter(
    private val variantClasses: AnalyzeDependenciesTask.VariantClassesHolder,
    private val variantDependencies: AnalyzeDependenciesTask.VariantDependenciesHolder,
    private val classFinder: ClassFinder,
    private val resourceFinder: ResourcesFinder,
    private val depsUsageFinder: DependencyUsageFinder,
    private val graphAnalyzer: DependencyGraphAnalyzer) {

    fun writeUnusedDependencies(destinationFile: File) {
        val toRemove = depsUsageFinder.unusedDirectDependencies
            .filter { graphAnalyzer.renderableDependencies.containsKey(it) }
        val toAdd = graphAnalyzer.findIndirectRequiredDependencies()

        val report = DependenciesUsageReport(
                add = toAdd.toList(),
                remove = toRemove.minus(resourceFinder.findUsedDependencies())
        )

        writeToFile(report, destinationFile)
    }

    fun writeMisconfiguredDependencies(destinationFile: File) {
        val apiDependencies = variantClasses.getPublicClasses()
            .mapNotNull { classFinder.find(it) }
            .filter { variantDependencies.api.contains(it) }

        val misconfiguredDependencies = variantDependencies.api.minus(apiDependencies)

        writeToFile(misconfiguredDependencies, destinationFile)
    }

    private fun writeToFile(output: Any, destinationFile: File) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        FileUtils.writeToFile(destinationFile, gson.toJson(output))
    }
}