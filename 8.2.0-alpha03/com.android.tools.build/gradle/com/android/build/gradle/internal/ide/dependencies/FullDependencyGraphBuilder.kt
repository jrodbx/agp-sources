/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.dependencies
import com.android.build.gradle.internal.attributes.VariantAttr
import com.android.build.gradle.internal.dependency.AdditionalArtifactType
import com.android.build.gradle.internal.dependency.ResolutionResultProvider
import com.android.build.gradle.internal.ide.dependencies.Graph.AdjacencyList
import com.android.build.gradle.internal.ide.dependencies.Graph.Companion.edges
import com.android.build.gradle.internal.ide.dependencies.Graph.Companion.graphItems
import com.android.build.gradle.internal.ide.dependencies.Graph.GraphItemList
import com.android.build.gradle.internal.ide.v2.ArtifactDependenciesAdjacencyListImpl
import com.android.build.gradle.internal.ide.v2.ArtifactDependenciesImpl
import com.android.build.gradle.internal.ide.v2.GraphItemImpl
import com.android.build.gradle.internal.ide.v2.UnresolvedDependencyImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.testFixtures.isProjectTestFixturesCapability
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.Edge
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.UnresolvedDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import java.io.File

class FullDependencyGraphBuilder(
    private val inputs: ArtifactCollectionsInputs,
    private val resolutionResultProvider: ResolutionResultProvider,
    private val libraryService: LibraryService,
    private val graphEdgeCache: GraphEdgeCache?,
    private val addAdditionalArtifactsInModel: Boolean,
    private val dontBuildRuntimeClasspath: Boolean
) {

    private val unresolvedDependencies = mutableMapOf<String, UnresolvedDependency>()

    fun build(): ArtifactDependencies = ArtifactDependenciesImpl(
        buildGraph(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, buildAdjacencyList = false).graphItems(),
        if (dontBuildRuntimeClasspath) null else buildGraph(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            buildAdjacencyList = false
        ).graphItems(),
        unresolvedDependencies.values.toList()
    )

    fun buildWithAdjacencyList() = ArtifactDependenciesAdjacencyListImpl(
        buildGraph(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, buildAdjacencyList = true).edges(),
        if (dontBuildRuntimeClasspath) null else buildGraph(
            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
            buildAdjacencyList = true
        ).edges(),
        unresolvedDependencies.values.toList()
    )

    private fun buildGraph(
        configType: AndroidArtifacts.ConsumedConfigType,
        buildAdjacencyList: Boolean
    ): Graph {
        // query for the actual graph, and get the first level children.
        val roots: Set<DependencyResult> = resolutionResultProvider.getResolutionResult(configType).root.dependencies

        // get the artifact first. This is a flat list of items that have been computed
        // to contain information about the actual artifacts (whether they are sub-projects
        // or external dependencies, whether they are java or android, whether they are
        // wrapper local jar/aar, etc...)
        val artifacts = inputs.getAllArtifacts(configType)

        val artifactMap = artifacts.associateBy { it.variant.toKey() }

        val javadocArtifacts = resolveAdditionalArtifact(configType, AdditionalArtifactType.JAVADOC)
        val sourceArtifacts = resolveAdditionalArtifact(configType, AdditionalArtifactType.SOURCE)
        val sampleArtifacts = resolveAdditionalArtifact(configType, AdditionalArtifactType.SAMPLE)

        // Keep a list of the visited nodes so that we don't revisit them in different branches.
        // This is a map so that we can easy get the matching GraphItem for it,
        val visited = mutableMapOf<ResolvedVariantResult, GraphItem>()

        val items = mutableListOf<GraphItem>()
        // at the top level, there can be a duplicate of all the dependencies if the graph is
        // setup via constraints, which is the case for our compile classpath always as the
        // constraints come from the runtime classpath
        for (dependency in roots.filter { !it.isConstraint }) {
            handleDependency(dependency, visited, artifactMap,
                    javadocArtifacts, sourceArtifacts, sampleArtifacts)?.let {
                items.add(it)
            }
        }

        // handle local Jars. They are not visited via the roots but they are part
        // of the artifacts list.
        val unvisitedArtifacts = artifacts.filter { it.componentIdentifier is OpaqueComponentArtifactIdentifier }

        for (artifact in unvisitedArtifacts) {
            val library = libraryService.getLibrary(artifact, AdditionalArtifacts(null, null, null))
            items.add(GraphItemImpl(library.key, null))
        }

        return if (buildAdjacencyList) {
            AdjacencyList(edges = buildAdjacencyList(items, graphEdgeCache))
        } else {
            GraphItemList(
                graphItems = items.toList()
            )
        }
    }

    private fun buildAdjacencyList(
        items: MutableList<GraphItem>,
        graphEdgeCache: GraphEdgeCache?
    ): MutableList<Edge> {
        checkNotNull(graphEdgeCache) { "Cache is required when building adjacency list. " }
        val seen = HashSet<String>()
        val queue = ArrayDeque(items.reversed())
        val edges = mutableListOf<Edge>()
        while (queue.isNotEmpty()) {
            val from = queue.removeLast()
            if (seen.add(from.key)) {
                queue.addAll(from.dependencies.reversed())
                edges.addAll(from.dependencies.map { to ->
                    graphEdgeCache.getEdge(from.key, to.key)
                })
                // Self dependencies essentially denote a graph item itself,
                // used primarily for graph items for no dependencies
                edges.add(graphEdgeCache.getEdge(from.key, from.key))
            }
        }
        return edges
    }

    private fun handleDependency(
        dependency: DependencyResult,
        visited: MutableMap<ResolvedVariantResult, GraphItem>,
        artifactMap: Map<VariantKey, ResolvedArtifact>,
        javadocArtifacts: Map<ComponentIdentifier, File>,
        sourceArtifacts: Map<ComponentIdentifier, File>,
        sampleArtifacts: Map<ComponentIdentifier, File>
    ): GraphItem? {
        if (dependency.isConstraint) return null
        if (dependency !is ResolvedDependencyResult) {
            (dependency as? UnresolvedDependencyResult)?.let {
                val name = it.attempted.toString()
                if (!unresolvedDependencies.containsKey(name)) {
                    unresolvedDependencies[name] = UnresolvedDependencyImpl(
                        name,
                        it.failure.cause?.message
                    )
                }
            }
            return null
        }

        // ResolvedVariantResult getResolvedVariant() should not return null, but there seems to be
        // some corner cases when it is null. https://issuetracker.google.com/214259374
        val variant: ResolvedVariantResult? = dependency.resolvedVariant
        if (variant == null) {
            val name = dependency.requested.toString()
            if (!unresolvedDependencies.containsKey(name)) {
                unresolvedDependencies[name] = UnresolvedDependencyImpl(
                    name,
                    "Internal error: ResolvedVariantResult getResolvedVariant() should not return null. https://issuetracker.google.com/214259374"
                )
            }
            return null
        }

        // check if we already visited this.
        val graphItem = visited[variant]
        if (graphItem != null) {
            return graphItem
        }

        val variantKey = variant.toKey()
        val artifact = artifactMap[variantKey]
        val variantDependencies by lazy {
            dependency.selected.getDependenciesForVariant(variant)
        }

        val javadoc = javadocArtifacts[variant.owner]
        val source = sourceArtifacts[variant.owner]
        val sample = sampleArtifacts[variant.owner]
        val additionalArtifacts = AdditionalArtifacts(javadoc, source, sample)

        val library = if (artifact == null) {
            val owner = variant.owner

            // There are 4 (currently known) reasons this can happen:
            // 1. when an artifact is relocated via Gradle's module "available-at" feature.
            // 2. when resolving a test graph, as one of the roots will be the same module and this
            //    is not included in the other artifact-based API.
            // 3. when an external dependency is without artifact file, but with transitive
            //    dependencies
            // 4. when resolving a dynamic-feature dependency graph; e.g., the app module does not
            //    publish an ArtifactType.JAR artifact to runtimeElements
            //
            // In cases 1, 2, and 3, there are still dependencies, so we need to create a library
            // object, and traverse the dependencies.
            //
            // In case 4, we want to ignore the app dependency and any transitive dependencies.
            if (variant.externalVariant.isPresent) {
                // Scenario 1
                libraryService.getLibrary(
                    ResolvedArtifact(
                        owner,
                        variant,
                        variantName = "unknown",
                        artifactFile = null,
                        isTestFixturesArtifact = false,
                        extractedFolder = null,
                        publishedLintJar = null,
                        dependencyType = ResolvedArtifact.DependencyType.RELOCATED_ARTIFACT,
                        isWrappedModule = false,
                        buildMapping = inputs.buildMapping
                    ),
                    additionalArtifacts
                )
            } else if (owner is ProjectComponentIdentifier && inputs.projectPath == owner.projectPath) {
                // Scenario 2
                // create on the fly a ResolvedArtifact around this project
                // and get the matching library item
                libraryService.getLibrary(
                    ResolvedArtifact(
                        owner,
                        variant,
                        variantName = variant.attributes
                            .getAttribute(VariantAttr.ATTRIBUTE)
                            ?.toString()
                            ?: "unknown",
                        artifactFile = File("wont/matter"),
                        isTestFixturesArtifact = variant.capabilities.any {
                            it.isProjectTestFixturesCapability(owner.projectName)
                        },
                        extractedFolder = null,
                        publishedLintJar = null,
                        dependencyType = ResolvedArtifact.DependencyType.ANDROID,
                        isWrappedModule = false,
                        buildMapping = inputs.buildMapping
                    ),
                    additionalArtifacts
                )
            } else if (owner !is ProjectComponentIdentifier && variantDependencies.isNotEmpty()) {
                // Scenario 3
                libraryService.getLibrary(
                    ResolvedArtifact(
                        owner,
                        variant,
                        variantName = "unknown",
                        artifactFile = null,
                        isTestFixturesArtifact = false,
                        extractedFolder = null,
                        publishedLintJar = null,
                        dependencyType = ResolvedArtifact.DependencyType.NO_ARTIFACT_FILE,
                        isWrappedModule = false,
                        buildMapping = inputs.buildMapping
                    ),
                    additionalArtifacts
                )
            } else {
                // Scenario 4 or other unknown scenario
                null
            }
        } else {
            // get the matching library item
            libraryService.getLibrary(artifact, additionalArtifacts)
        }

        if (library != null) {
            // Create GraphItem for the library first and add it to cache in order to avoid cycles.
            // See http://b/232075280.
            val libraryGraphItem = GraphItemImpl(
                library.key,
                null
            ).also {
                visited[variant] = it
            }

            // Now visit children, and add them as dependencies
            variantDependencies.forEach {
                handleDependency(it, visited, artifactMap,
                        javadocArtifacts, sourceArtifacts, sampleArtifacts)?.let { childGraphItem ->
                    libraryGraphItem.addDependency(childGraphItem)
                }
            }
            return libraryGraphItem
        }

        return null
    }

    private fun resolveAdditionalArtifact(
            configType: AndroidArtifacts.ConsumedConfigType,
            additionalArtifactType: AdditionalArtifactType
    ): Map<ComponentIdentifier, File> {
        return if (addAdditionalArtifactsInModel) {
            resolutionResultProvider
                    .getAdditionalArtifacts(configType, additionalArtifactType)
                    .associate { it.variant.owner to it.file }
        } else {
            mapOf()
        }
    }
}

private sealed class Graph {
    data class AdjacencyList(val edges: List<Edge>) : Graph()
    data class GraphItemList(val graphItems: List<GraphItem>) : Graph()


    companion object {
        fun Graph.graphItems() = (this as GraphItemList).graphItems
        fun Graph.edges() = (this as AdjacencyList).edges
    }
}
