/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import java.io.File
import java.util.HashSet
import java.util.function.Consumer

/**
 * Implementation of a [ArtifactCollection] which filters out dependencies not explicitly requested.
 *
 * The main use case for this is building an ArtifactCollection that represents the runtime resource
 * dependencies of a test app.
 *
 * Only resources from dependencies explicitly requested are packaged in
 * the test APK.
 *
 * For example, as in the test AppWithAndroidTestDependencyOnLibTest, given
 * the following.
 *
 * ### :app
 * ```
 * dependencies {
 *   implementation project('library2')
 *   implementation 'com.android.support.constraint:constraint-layout:1.0.2'
 *   androidTestImplementation project('library')
 * }
 * ```
 * ### :library
 * ```
 * dependencies {
 *   implementation 'com.android.support.constraint:constraint-layout:1.0.2'
 * }
 * ```
 * ```
 * |-------------------|---------------------------------------------|
 * | Resources from    | Will be included in                         |
 * |-------------------|---------------------------------------------|
 * | :app main         | Main (by definition)                        |
 * | :app test         | Test (by definition)                        |
 * | :library main     | Test (directly)                             |
 * | :library2 main    | Main (directly) (2)                         |
 * | constraint layout | Main (directly) and test (via :library) (1) |
 * |-----------------------------------------------------------------|
 * ```
 *
 *  1. In 3.0.0-x before beta7, each androidTest configuration now extends
 *     the corresponding main configuration, and all the resource
 *     containing artifacts also present in the application were filtered
 *     out for tests.  This was the right thing to do for classes, but not
 *     for resources, and lead to https://issuetracker.google.com/65175674.
 *
 *  2. The initial fix for the issue in 3.0.0-beta7,
 *     ag/I971a3894b3272a189fa0f54e27d6d3995db88378 was to include all
 *     resources in the test, which bloats the test APK significantly and
 *     broke the corner case of https://issuetracker.google.com/68275433
 *
 * @param dependenciesToFilter  The ArtifactCollection of test resources.
 *         As androidTest extends main, this contains extra resources that do not need to be
 *         packaged in the separate test APK, which will be filtered by this artifact collection.
 * @oaram dependencyRootsToKeep  The unresolved dependencies that were explicitly part of the
 *         android test runtime classpath.
 * @param dependencyGraph  The dependency graph of the resources to filter configuration,
 *         when combined with the dependency roots to keep forms a whitelist of Component
 *         Identifiers, which is then used to
 *                         Used to map the roots to keep to collections of
 *                         component identifiers to keep.
 */
class AndroidTestResourceArtifactCollection(
        private val dependenciesToFilter: ArtifactCollection,
        private val dependencyRootsToKeep: Collection<Dependency>,
        private val dependencyGraph: ResolvableDependencies) : ArtifactCollection {

    /** Should be Set not MutableSet, but the iterator() override below requires it for some reason */
    private val _artifacts: MutableSet<ResolvedArtifactResult> by lazy {
        val requests = dependencyRootsToKeep.mapNotNull(this::toRequest).toSet()

        // Traverse the dependency graph, flatten keeping the sub-graphs that
        // were explicitly requested for the test side.
        val keptComponents = HashSet<ComponentIdentifier>()
        for (dependencyResult in dependencyGraph.resolutionResult.root.dependencies) {
            dependencyResult as ResolvedDependencyResult
            if (requests.contains(toRequest(dependencyResult.selected.id))) {
                collect(keptComponents, dependencyResult)
            }
        }
        val builder = ImmutableSet.builder<ResolvedArtifactResult>()
        dependenciesToFilter.artifacts
                .filter { keptComponents.contains(it.id.componentIdentifier) }
                .forEach { builder.add(it) }
        builder.build()
    }

    private val artifactFilesSet: Set<File> by lazy {
        artifacts.map(ResolvedArtifactResult::getFile).toSet()
    }

    private val _artifactFiles: FileCollection by lazy {
        dependenciesToFilter.artifactFiles.filter { artifactFilesSet.contains(it) }
    }

    override fun getArtifactFiles() = _artifactFiles

    override fun getArtifacts() = _artifacts

    override fun getFailures(): Collection<Throwable> = dependenciesToFilter.failures

    override fun iterator() = _artifacts.iterator()

    override fun forEach(action: Consumer<in ResolvedArtifactResult>) = _artifacts.forEach(action)

    override fun spliterator() = _artifacts.spliterator()

    private fun collect(
            keptComponents: MutableSet<ComponentIdentifier>,
            item: ResolvedDependencyResult) {
        if (!keptComponents.add(item.selected.id)) {
            // Avoid repeatedly traversing the same sub-graphs
            // (Caused the scalability issue in https://issuetracker.google.com/124437190)
            return
        }
        for (dependency in item.selected.dependencies) {
            collect(keptComponents, dependency as ResolvedDependencyResult)
        }
    }

    private fun toRequest(dependency: Dependency): Request? = when (dependency) {
        is ProjectDependency -> Request.RequestProject(dependency.dependencyProject.path)
        is ExternalDependency -> Request.RequestExternal(dependency.group, dependency.name)
    // Local AARs are not supported, so local dependencies can't contain resources.
        is FileCollectionDependency -> null
        else -> throw IllegalArgumentException("unexpected dependency $dependency")
    }

    private fun toRequest(id: ComponentIdentifier): Request? = when (id) {
        is ProjectComponentIdentifier -> Request.RequestProject(id.projectPath)
        is ModuleComponentIdentifier -> Request.RequestExternal(id.group, id.module)
        else -> null
    }

    sealed class Request {
        data class RequestProject(val projectPath: String) : Request()
        data class RequestExternal(val group: String?, val module: String) : Request()
    }
}
