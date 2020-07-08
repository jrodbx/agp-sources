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

package com.android.build.gradle.internal.res.namespaced

import com.google.common.annotations.VisibleForTesting
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.utils.FileUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import java.io.File

typealias ArtifactFiles = Map<ArtifactType, ImmutableMap<String, ImmutableCollection<File>>>

/**
 * Represents a graph of dependencies, with custom Nodes capable of fetching artifact files.
 * For examples of usages look at the DependenciesGraphTest.
 */
class DependenciesGraph(val rootNodes: ImmutableSet<Node>, val allNodes: ImmutableSet<Node>) {

    companion object {
        /**
         * Creates an instance of the DependenciesGraph based on the given ResolvableDependencies
         * object.
         * For examples of usages look at the DependenciesGraphTest.
         *
         * @param dependencies resolvable dependencies (can be local or remote)
         * @param artifacts an optional map that stores a mapping of component ID to artifact file
         *          for an ArtifactType
         */
        fun create(
            dependencies: ResolvableDependencies,
            artifacts: ArtifactFiles = ImmutableMap.of()
        ): DependenciesGraph {
            return create(
                dependencies.resolutionResult.root.dependencies,
                artifacts,
                HashMap()
            )
        }

        @VisibleForTesting
        fun create(
            roots: Iterable<DependencyResult>,
            artifacts: ArtifactFiles = ImmutableMap.of(),
            foundNodes: MutableMap<String, Node> = HashMap(),
            sanitizedNames: MutableSet<String> = HashSet()
        ): DependenciesGraph {
            val rootNodes = mutableSetOf<Node>()
            // We can have multiple roots. Collect nodes starting from each of them.
            for (dependency in roots) {
                dependency as ResolvedDependencyResult
                val node = collect(dependency, foundNodes, sanitizedNames, artifacts)
                rootNodes.add(node)
            }
            return DependenciesGraph(
                ImmutableSet.copyOf(rootNodes),
                ImmutableSet.copyOf(foundNodes.values)
            )
        }

        private fun collect(
            dependencyResult: DependencyResult,
            foundNodes: MutableMap<String, Node>,
            usedSanitizedNames: MutableSet<String>,
            artifacts: ArtifactFiles
        ): Node {
            dependencyResult as ResolvedDependencyResult
            if (foundNodes.contains(dependencyResult.selected.id.displayName)) {
                return foundNodes[dependencyResult.selected.id.displayName]!!
            }
            try {
                // Visit all children of the node and collect them. If a child has already been
                // visited, it will be stored in the 'foundNodes' map.
                val dependencies = ArrayList<DependenciesGraph.Node>()
                for (dependency in dependencyResult.selected.dependencies) {
                    dependency as ResolvedDependencyResult
                    collect(dependency, foundNodes, usedSanitizedNames, artifacts)
                    dependencies.add(foundNodes[dependency.selected.id.displayName]!!)
                }
                return Node(
                    dependencyResult.selected.id,
                    getUniqueSanitizedDependencyName(
                        dependencyResult.selected.id.displayName,
                        usedSanitizedNames
                    ),
                    ImmutableSet.copyOf(dependencies),
                    artifacts
                ).also {
                    check(foundNodes.put(dependencyResult.selected.id.displayName, it) == null)
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    "Failed rewriting node ${dependencyResult.selected.id.displayName}.",
                    e
                )
            }
        }

        private fun getUniqueSanitizedDependencyName(
            name: String, usedNames: MutableSet<String>
        ): String {
            var sanitizedName = FileUtils.sanitizeFileName(name)
            // This is unlikely, but if there is a collision, add more _ charaters until it is unique.
            while (usedNames.contains(sanitizedName)) {
                sanitizedName += "_"
            }
            usedNames.add(sanitizedName)
            return sanitizedName
        }
    }

    /**
     * Class for storing information about a dependency node.
     */
    class Node constructor(
        val id: ComponentIdentifier,
        val sanitizedName: String,
        val dependencies: ImmutableSet<Node>,
        artifactFiles: ArtifactFiles
    ) {
        val artifacts: ImmutableMap<ArtifactType, ImmutableCollection<File>>
        private val transitiveArtifactCache: LoadingCache<ArtifactType, ImmutableList<File>>
        val transitiveDependencies: ImmutableSet<Node> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            ImmutableSet.builder<Node>().apply {
                addAll(dependencies)
                dependencies.forEach { addAll(it.transitiveDependencies) }
            }.build()
        }

        init {
            val builder = ImmutableMap.builder<ArtifactType, ImmutableCollection<File>>()
            for (type in artifactFiles.keys) {
                val file = artifactFiles[type]!![id.displayName]
                if (file != null) {
                    builder.put(type, file)
                }
            }
            artifacts = builder.build()
            transitiveArtifactCache = CacheBuilder.newBuilder().build(
                object : CacheLoader<ArtifactType, ImmutableList<File>>() {
                    override fun load(artifactType: ArtifactType) =
                        computeTransitiveFiles(artifactType)
                })
        }

        fun getFile(type: ArtifactType): File? {
            return getFiles(type)?.single()
        }

        fun getFiles(type: ArtifactType): ImmutableCollection<File>? {
            return artifacts[type]
        }

        fun getTransitiveFiles(type: ArtifactType): ImmutableList<File> {
            return transitiveArtifactCache.getUnchecked(type)
        }

        private fun computeTransitiveFiles(type: ArtifactType): ImmutableList<File> {
            val builder = ArrayList<File>()
            // Add ourselves first, if we contain the file.
            val file = getFile(type)
            if (file != null) {
                builder.add(file)
            }
            // We have only the immediate children, go through them in alphabetical order to make it
            // more deterministic.
            val children = dependencies.asList()
            children.sortedBy { it.id.displayName }
            for (child in children) {
                for (childFile in child.getTransitiveFiles(type)) {
                    if (!builder.contains(childFile)) {
                        builder.add(childFile)
                    }
                }
            }
            return ImmutableList.copyOf(builder)
        }

        override fun toString(): String {
            return id.displayName
        }
    }
}
