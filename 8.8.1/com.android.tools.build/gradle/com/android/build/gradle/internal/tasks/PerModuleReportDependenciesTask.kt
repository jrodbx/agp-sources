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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.tools.build.libraries.metadata.AppDependencies
import com.google.protobuf.ByteString
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

/** Task that publishes the app dependencies proto for each app or dynamic feature module. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.BUNDLE_PACKAGING)
abstract class PerModuleReportDependenciesTask : NonIncrementalTask() {

    @get:Internal
    abstract val runtimeClasspathArtifacts: Property<ArtifactCollection>

    @Input
    abstract fun getRootComponent(): Property<ResolvedComponentResult>

    // Don't use @Classpath here as @Classpath ignores some of the contents whereas the output of
    // this task contains the hashes of the entire contents.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val runtimeClasspathFiles: FileCollection by lazy { runtimeClasspathArtifacts.get().artifactFiles }

    @get:OutputFile
    abstract val dependencyReport: RegularFileProperty

    @get:Input
    abstract val moduleName: Property<String>

    @get:Nested
    abstract val projectRepositories: ListProperty<InternalRepositoryMetadata>

    // Intermediate data class, for use with @Nested
    sealed class InternalRepositoryMetadata(@get:Input val name: String) {

        class Maven(name: String, @get:Input val url: URI) : InternalRepositoryMetadata(name)
        class Ivy(name: String, @get:Input val url: URI) : InternalRepositoryMetadata(name)
    }

    private fun getFileDigest(file: File): ByteString {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
    }

    override fun doTaskAction() {
        val componentDigestMap = runtimeClasspathArtifacts.get().groupBy(
            keySelector = { artifact -> artifact.id.componentIdentifier },
            valueTransform = { artifact -> artifact.file }
        )
            .mapValues { (id, fileList) ->
                val fileSet = fileList.toSet()
                if (fileSet.size > 1)
                    logger.warn("Component ${id.displayName} maps to multiple files")
                getFileDigest(fileSet.first())
            }

        // Declare local classes used for intermediate data representations
        class InternalGraphNode(
            val id: ComponentIdentifier,
            val neighborIdList: List<ComponentIdentifier>,
            val repoIndex: Int?,
        )
        class InternalLibraryMetadata(
            id: ComponentIdentifier,
            val sha256: ByteString?,
            val repoIndex: Int?,
            ) {
            val mavenLibrary: MavenLibraryData? = (id as? ModuleComponentIdentifier)?.let {
                MavenLibraryData(
                    groupId = it.group,
                    artifactId = it.module,
                    version = it.version,
                )
            }
            inner class MavenLibraryData(
                val groupId: String,
                val artifactId: String,
                val version: String,
            )
        }
        class InternalLibraryDependenciesMetadata(
            val libIndex: Int,
            val libDepIndices: List<Int>,
        )

        // Build a map of repoName->Index
        val repoNameToIndexMap =
            projectRepositories.get().associateIndexed { index, it -> it.name to index }

        // Build the graph representation, partitioning into "Project" and "Library" nodes
        //   and removing all edges (dependencies) that point to Projects


        val allComponents = mutableSetOf<ResolvedComponentResult>()
        collectAll(getRootComponent().get(), allComponents)

        val (projectNodes, libraryNodesPartialList) = allComponents.asSequence()
            .map { component ->
                InternalGraphNode(
                    id = component.id,
                    neighborIdList = component.dependencies
                        .filterIsInstance<ResolvedDependencyResult>()
                        .map { it.selected.id }
                        .filter { it !is ProjectComponentIdentifier },
                    repoIndex = try {
                        repoNameToIndexMap[(component as? ResolvedComponentResultInternal)?.repositoryName]
                    } catch (e: Exception) {
                        null
                    }
                )
            }
            .distinctBy { it.id }
            .partition { it.id is ProjectComponentIdentifier }

        // Calculate the list of IDs which correspond to an Artifact, but not to a Node in the graph
        //   This includes direct dependencies on files (like Jar files)
        val unusedIds = componentDigestMap.keys.minus(libraryNodesPartialList.map { it.id })

        // Add a node for each unused ID
        val libraryNodes = libraryNodesPartialList +
                unusedIds.asSequence().map { unusedId -> InternalGraphNode(unusedId, listOf(), null) }

        // Build an intermediate sequence of InternalLibraryMetadata objects
        val intermediateLibraries = libraryNodes.asSequence().map { node ->
            InternalLibraryMetadata(
                id = node.id,
                sha256 = componentDigestMap[node.id],
                repoIndex = node.repoIndex
            )
        }

        // Build a map of ComponentId->Index
        val idToIndexMap = libraryNodes.associateIndexed { index, it -> it.id to index}
        // Build an intermediate sequence of InternalLibraryDependenciesMetadata objects
        val intermediateLibraryDependencies = libraryNodes.asSequence().map { node ->
            InternalLibraryDependenciesMetadata(
                libIndex = idToIndexMap[node.id]!!,
                libDepIndices = node.neighborIdList.mapNotNull { id -> idToIndexMap[id] }
            )
        }.filter { it.libDepIndices.isNotEmpty() }

        // Compute the set of "direct dependencies" for the ModuleDependencies proto
        val directDepIndices =
            projectNodes
                .asSequence()
                .flatMap { it.neighborIdList.asSequence() } // all Libraries upon which a Project depends directly
                .plus(unusedIds) // all Libraries not accounted for in the original graph
                .mapNotNull { id -> idToIndexMap[id] }
                .toSet()

        // Convert internal object representations to Protobuf
        val appDependenciesProto = AppDependencies.newBuilder().apply {
            intermediateLibraries.forEach { libObj ->
                addLibraryBuilder().apply {
                    if (libObj.mavenLibrary != null)
                        mavenLibraryBuilder.apply {
                            groupId = libObj.mavenLibrary.groupId
                            artifactId = libObj.mavenLibrary.artifactId
                            version = libObj.mavenLibrary.version
                        }
                    if (libObj.repoIndex != null) {
                        repoIndexBuilder.value = libObj.repoIndex
                    }
                    if (libObj.sha256 != null) {
                        digestsBuilder.sha256 = libObj.sha256
                    }
                }
            }
            intermediateLibraryDependencies.forEach { libDepObj ->
                addLibraryDependenciesBuilder().apply {
                    libraryIndex = libDepObj.libIndex
                    addAllLibraryDepIndex(libDepObj.libDepIndices)
                }
            }
            projectRepositories.get().forEach { repoObj ->
                addRepositoriesBuilder().apply {
                    when (repoObj) {
                        is InternalRepositoryMetadata.Maven ->
                            mavenRepoBuilder.url = repoObj.url.toString()
                        is InternalRepositoryMetadata.Ivy ->
                            ivyRepoBuilder.url = repoObj.url.toString()
                    }
                }
            }
            addModuleDependenciesBuilder().apply {
                moduleName = this@PerModuleReportDependenciesTask.moduleName.get()
                addAllDependencyIndex(directDepIndices)
            }
        }.build()

        // Write the final Protobuf to a file
        FileOutputStream(dependencyReport.get().asFile).use { appDependenciesProto.writeTo(it) }
    }

    private fun collectAll(
        node: ResolvedComponentResult,
        allComponents: MutableSet<ResolvedComponentResult>
    ) {
        if (allComponents.add(node)) {
            for (dependency in node.dependencies) {
                if (dependency is ResolvedDependencyResult) {
                    collectAll(dependency.selected, allComponents)
                }
            }
        }
    }

    class CreationAction(
        creationConfig: ApkCreationConfig
    ) : VariantTaskCreationAction<PerModuleReportDependenciesTask, ApkCreationConfig>(
        creationConfig
    ) {

        companion object {

            internal fun Iterable<ArtifactRepository>.toInternalRepoMetadataList() =
                mapNotNull { repo ->
                    when (repo) {
                        is MavenArtifactRepository ->
                            InternalRepositoryMetadata.Maven(repo.name, repo.url)
                                .takeUnless { it.url.scheme == "file" }
                        is IvyArtifactRepository ->
                            InternalRepositoryMetadata.Ivy(repo.name, repo.url)
                                .takeUnless { it.url.scheme == "file" }
                        else ->
                            return@mapNotNull null // Drop any other types of repositories, such as flatDirectory
                    }
                }
        }

        override val name: String = computeTaskName("collect", "Dependencies")
        override val type: Class<PerModuleReportDependenciesTask> =
            PerModuleReportDependenciesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PerModuleReportDependenciesTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PerModuleReportDependenciesTask::dependencyReport
            ).withName("dependencies.pb").on(SingleArtifact.METADATA_LIBRARY_DEPENDENCIES_REPORT)
        }

        override fun configure(
            task: PerModuleReportDependenciesTask
        ) {
            super.configure(task)
            task.runtimeClasspathArtifacts.set(
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                    // Query for JAR instead of PROCESSED_JAR as this task works with unprocessed (a/j)ars
                    AndroidArtifacts.ArtifactType.AAR_OR_JAR
                )
            )
            @Suppress("UnstableApiUsage")
            task.getRootComponent().set(creationConfig.variantDependencies.runtimeClasspath.incoming.resolutionResult.rootComponent)

            val projectRepositories = task.project.repositories

            val repositoryHandlerProvider: Provider<RepositoryHandler> = try {
                // this is using private Gradle APIs, so it may break in the future Gradle versions
                val dependencyResolutionManagement = (task.project.gradle as GradleInternal)
                    .settings
                    .dependencyResolutionManagement

                // If repositoriesMode == PREFER_PROJECT, prioritize projectRepositories
                dependencyResolutionManagement.repositoriesMode.map {
                    if (it == RepositoriesMode.PREFER_PROJECT && projectRepositories.isNotEmpty())
                        projectRepositories
                    else
                        dependencyResolutionManagement.repositories
                }
            } catch (ignored: Throwable) {
                // fall back to using projectRepositories
                creationConfig
                    .services
                    .provider { projectRepositories }
            }

            task.projectRepositories.setDisallowChanges(repositoryHandlerProvider.map { it.toInternalRepoMetadataList() })

            if (creationConfig is DynamicFeatureCreationConfig) {
                task.moduleName.setDisallowChanges(creationConfig.featureName)
            } else {
                task.moduleName.setDisallowChanges("base")
            }
        }
    }
}

// Wrapper for [Iterable.associate] with semantics like [Iterable.mapIndexed]
private inline fun <T, K, V> Iterable<T>.associateIndexed(transform: (index: Int, T) -> Pair<K, V>): Map<K, V> {
    var index = 0
    return associate { transform(index++, it) }
}
