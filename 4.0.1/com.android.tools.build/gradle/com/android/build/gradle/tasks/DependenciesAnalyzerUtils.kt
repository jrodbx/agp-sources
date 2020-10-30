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
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult
import java.io.File
import java.util.LinkedList
import java.util.zip.ZipFile

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

/** Finds where a class is coming from. */
class ClassFinder(private val externalArtifactCollection: ArtifactCollection) {

    private val classToDependency: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        externalArtifactCollection
            .asSequence()
            .filter { it.file.name.endsWith(SdkConstants.DOT_JAR) }
            .forEach { artifact ->
                val classNamesInJar = getClassFilesInJar(artifact.file)
                classNamesInJar.forEach { artifactClass ->
                    map[artifactClass] = artifact.id.componentIdentifier.displayName
                }
            }
        map
    }

    /** Returns the dependency that contains {@code className} or null if we can't find it. */
    fun find(className: String) = classToDependency[className]

    fun findClassesInDependency(dependencyId: String) =
        classToDependency.filterValues { it == dependencyId }.keys

    private fun getClassFilesInJar(jarFile: File): List<String> {
        val classes = mutableListOf<String>()

        val zipFile = ZipFile(jarFile)

        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                classes.add(entry.name)
            }
        }

        return classes
    }
}

class DependencyUsageReporter(
    private val variantClasses: AnalyzeDependenciesTask.VariantClassesHolder,
    private val variantDependencies: AnalyzeDependenciesTask.VariantDependenciesHolder,
    private val classFinder: ClassFinder,
    private val depsUsageFinder: DependencyUsageFinder,
    private val graphAnalyzer: DependencyGraphAnalyzer) {

    // TODO: update the analyzer to detect dependencies in resources

    // Temporary workaround: A list of common prefixes from libraries that are not yet detected
    // by the analyzer. such as the Kotlin libraries, or popular libraries that are often
    // used in resources but not referenced in classes.
    private val dontReportPrefixSet = setOf(
        // Kotlin libraries are added by default and should not be removed
        "org.jetbrains.kotlin:",
        // Some libraries that are often used exclusively in resource files
        "androidx.gridlayout:gridlayout:",
        "androidx.appcompat:appcompat:",
        "de.hdodenhof:circleimageview:")

    fun writeUnusedDependencies(destinationFile: File) {
        val toRemove = depsUsageFinder.unusedDirectDependencies
            .filter { graphAnalyzer.renderableDependencies.containsKey(it) }
        val toAdd = graphAnalyzer.findIndirectRequiredDependencies()

        val dontReportTemp = mutableListOf<String>()
        toRemove.forEach { dependency ->
            dontReportPrefixSet.forEach {
                if (dependency.startsWith(it)) {
                    dontReportTemp.add(dependency)
                }
            }
        }

        val report = mapOf("remove" to toRemove.minus(dontReportTemp), "add" to toAdd)

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