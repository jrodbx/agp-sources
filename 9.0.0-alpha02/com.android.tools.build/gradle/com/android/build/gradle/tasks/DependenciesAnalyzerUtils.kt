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
import com.android.ide.common.resources.usage.getDeclaredAndReferencedResourcesFrom
import com.android.ide.common.resources.usage.getResourcesFromDirectory
import com.android.utils.FileUtils
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
    private val resolvedComponentResult: ResolvedComponentResult,
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
        val dependencyGraph = resolvedComponentResult
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

/** Finds where a class is coming from. */
class ClassFinder(private val classesArtifactCollection : ArtifactCollection) {

    private val classToDependency: Map<String, String> by lazy (this::getClassDependencyMap)

    private fun getClassDependencyMap() : Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (artifact in classesArtifactCollection) {
            val file = artifact.file
            if (file.name != SdkConstants.FN_CLASS_LIST) {
                throw IllegalArgumentException(
                        "${file.name} should have name ${SdkConstants.FN_CLASS_LIST}.")
            }
            file.forEachLine { artifactFileLine ->
                map[artifactFileLine] = artifact.id.componentIdentifier.displayName
            }
        }
        return map
    }

    /** Returns the dependency that contains {@code className} or null if we can't find it. */
    fun find(className: String) : String? = classToDependency[className]

    fun findClassesInDependency(dependencyId: String) =
        classToDependency.filterValues { it == dependencyId }.keys
}

data class ResourceDependencyHolder(
        val resourceName: String,
        var isUsedbyAppModule: Boolean,
        val dependencyUsages: MutableSet<String> = mutableSetOf()
)

class ResourcesFinder(
        private val manifest: File?,
        private val resourceSets: Collection<File>,
        private val resourceSymbolsArtifactCollection: ArtifactCollection) {

    private val resourceToDependency: Map<String, ResourceDependencyHolder>
            by lazy (this::getResourceDependencyMap)

    private fun getResourceDependencyMap(): Map<String, ResourceDependencyHolder> {
        val resourceToDependency = mutableMapOf<String, ResourceDependencyHolder>()

        // Extract resource usages from manifest, if it exists.
        val manifestResources: List<String> = if (manifest != null) {
            getDeclaredAndReferencedResourcesFrom(manifest).map { it.toString() }
        } else {
            emptyList()
        }
        // Extract resources from application modules.
        val usedResources: List<String> = resourceSets
                .flatMap(this::parseResourceSourceSet)
                .plus(manifestResources)

        usedResources.forEach {
            resourceToDependency[it] = ResourceDependencyHolder(it, true)
        }

        // Add library AAR resources to map.
        for (artifact in resourceSymbolsArtifactCollection) {
            val resSymbols = artifact.file
            if (resSymbols.name != SdkConstants.FN_RESOURCE_SYMBOLS) {
                throw IllegalArgumentException(
                        "${resSymbols.name} should have name ${SdkConstants.FN_RESOURCE_SYMBOLS}.")
            }
            resSymbols.readLines().forEach { resourceName ->
                // Get existing ResourceDependencyHolder or create a new instance. Then
                // add the current dependency to dependency usages.
                val resDeps = resourceToDependency.getOrDefault(resourceName,
                        ResourceDependencyHolder(resourceName, false)).apply {
                    dependencyUsages += artifact.id.componentIdentifier.displayName
                }
                resourceToDependency[resourceName] = resDeps
            }
        }

        return resourceToDependency
    }

    /**
     * Returns a list of dependencies which contain the resource, otherwise returns an empty list.
     */
    fun find(resourceId: String): Set<String> =
            resourceToDependency[resourceId]?.dependencyUsages ?: emptySet()

    /**
     * Returns a list of resources which are declared or referenced by the dependency.
     */
    fun findResourcesInDependency(dependencyId: String): Set<ResourceDependencyHolder> = resourceToDependency
            .filterValues { it.dependencyUsages.contains(dependencyId) }
            .values
            .toSet()

    /**
     * Returns a list of dependencies which contains an identical resource in another dependency.
     */
    fun findUsedDependencies(): Set<String> = resourceToDependency
            .filter {
                it.value.isUsedbyAppModule ||
                        checkDependenciesForAppUsages(it.value.dependencyUsages)
            }
            .flatMap { it.value.dependencyUsages }
            .toSet()

    /**
     * Returns a list of dependencies which do not contain an identical resource in another
     * dependency.
     */
    fun findUnUsedDependencies(): Set<String> = resourceSymbolsArtifactCollection
            .map { it.id.componentIdentifier.displayName }
            .minus(findUsedDependencies())
            .toSet()
    /**
     * Gets a list of declared and referenced resources from all files in the given resource set
     * directory.
     */
    private fun parseResourceSourceSet(resourceSourceSet : File) : List<String> =
            getResourcesFromDirectory(resourceSourceSet)

    /**
     * Check if a collection of dependencies have a resource used by an app module.
     */
    private fun checkDependenciesForAppUsages(dependencies: Collection<String>) = dependencies
            .flatMap { findResourcesInDependency(it) }
            .asSequence()
            .filter { it.isUsedbyAppModule }
            .any()

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
                .intersect(graphAnalyzer.renderableDependencies.keys)
                .minus(resourceFinder.findUsedDependencies())
                .sorted()
        val toAdd = graphAnalyzer.findIndirectRequiredDependencies().sorted()

        val report = DependenciesUsageReport(toAdd, toRemove)
        writeToFile(report, destinationFile)
    }

    fun writeMisconfiguredDependencies(destinationFile: File) {
        val apiDependencies = variantDependencies.api?.let {
            variantClasses.getPublicClasses()
                    .mapNotNull { classFinder.find(it) }
                    .intersect(it)
        }

        val misconfiguredDependencies =
                variantDependencies.api?.minus(apiDependencies) ?: emptySet()

        writeToFile(misconfiguredDependencies, destinationFile)
    }

    private fun writeToFile(output: Any, destinationFile: File) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        FileUtils.writeToFile(destinationFile, gson.toJson(output))
    }
}

internal fun extractBuildId(dependency: Dependency) =
        if (dependency.group != null) {
            if (dependency.version != null) {
                "${dependency.group}:${dependency.name}:${dependency.version}"
            } else {
                "${dependency.group}:${dependency.name}"
            }
        } else {
            null
        }
