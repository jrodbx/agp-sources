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

import com.android.build.gradle.internal.dependency.ResolutionResultProvider
import com.android.build.gradle.internal.ide.v2.ArtifactDependenciesImpl
import com.android.build.gradle.internal.ide.v2.GraphItemImpl
import com.android.build.gradle.internal.ide.v2.UnresolvedDependencyImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.UnresolvedDependency
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
    private val libraryService: LibraryService
) {

    private val unresolvedDependencies = mutableMapOf<String, UnresolvedDependency>()

    fun build(): ArtifactDependencies = ArtifactDependenciesImpl(
        buildGraph(AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH),
        buildGraph(AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH),
        unresolvedDependencies.values.toList()
    )

    private fun buildGraph(
        configType: AndroidArtifacts.ConsumedConfigType,
    ): List<GraphItem> {
        // query for the actual graph, and get the first level children.
        val roots: Set<DependencyResult> = resolutionResultProvider.getResolutionResult(configType).root.dependencies

        // get the artifact first. This is a flat list of items that have been computed
        // to contain information about the actual artifacts (whether they are sub-projects
        // or external dependencies, whether they are java or android, whether they are
        // wrapper local jar/aar, etc...)
        val artifacts = inputs.getAllArtifacts(configType)

        val artifactMap = artifacts.associateBy { it.variant.toKey() }

        // Keep a list of the visited nodes so that we don't revisit them in different branches.
        // This is a map so that we can easy get the matching GraphItem for it,
        val visited = mutableMapOf<ResolvedVariantResult, GraphItem>()

        val items = mutableListOf<GraphItem>()
        // at the top level, there can be a duplicate of all the dependencies if the graph is
        // setup via constraints, which is the case for our compile classpath always as the
        // constraints come from the runtime classpath
        for (dependency in roots.filter { !it.isConstraint }) {
            handleDependency(dependency, visited, artifactMap)?.let {
                items.add(it)
            }
        }

        // handle local Jars. They are not visited via the roots but they are part
        // of the artifacts list.
        val unvisitedArtifacts = artifacts.filter { it.componentIdentifier is OpaqueComponentArtifactIdentifier }

        for (artifact in unvisitedArtifacts) {
            val library = libraryService.getLibrary(artifact)
            items.add(GraphItemImpl(library.key, null, listOf()))
        }

        return items.toList()
    }

    private fun handleDependency(
        dependency: DependencyResult,
        visited: MutableMap<ResolvedVariantResult, GraphItem>,
        artifactMap: Map<VariantKey, ResolvedArtifact>
    ): GraphItem? {

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

        val variant = dependency.resolvedVariant

        // check if we already visited this.
        val graphItem = visited[variant]
        if (graphItem != null) {
            return graphItem
        }

        val variantKey = variant.toKey()
        val artifact = artifactMap[variantKey]

        val library = if (artifact == null) {
            // this can happen when resolving a test graph, as one of the roots will be
            // the same module and this is not included in the other artifact-based API.
            val owner = variant.owner
            if (owner is ProjectComponentIdentifier &&
                inputs.projectPath == owner.projectPath) {

                // create on the fly a ResolvedArtifact around this project
                // and get the matching library item
                libraryService.getLibrary(
                    ResolvedArtifact(
                        variant.owner,
                        variant,
                        variantName = variant.getVariantName() ?: "unknown",
                        artifactFile = File("wont/matter"),
                        extractedFolder = null,
                        publishedLintJar = null,
                        dependencyType = ResolvedArtifact.DependencyType.ANDROID,
                        isWrappedModule = false,
                        buildMapping = inputs.buildMapping
                    )
                )
            } else {
                null
            }
        } else {
            // get the matching library item
            libraryService.getLibrary(artifact)
        }

        if (library != null) {
            // create the GraphItem for the library, starting by recursively computing the children
            val children =
                    dependency.selected.getDependenciesForVariant(variant).mapNotNull {
                        handleDependency(it, visited, artifactMap)
                    }

            return GraphItemImpl(
                library.key,
                null,
                children
            ).also {
                visited[variant] = it
            }
        }

        return null
    }
}
