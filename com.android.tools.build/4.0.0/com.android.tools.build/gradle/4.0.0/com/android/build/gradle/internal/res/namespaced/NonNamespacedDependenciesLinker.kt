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

import com.android.annotations.concurrency.GuardedBy
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.res.namespaced.DependenciesGraph.Node
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
/**
 * Responsible for handling the linking of namespaced, compiled resources in to static
 * libraries.
 *
 * All libraries start in the [backlog] list, blocked on all of their dependencies (
 * see[NonNamespacedDependenciesLinker.QueuedBlockedNode])
 * To start all the libraries that are not blocked (leaves of the dependency graph) are submitted
 * for processing.
 *
 * When a library is finished linking, it no longer blocks any of the other libraries in the backlog
 * and then any libraries that are unblocked are scheduled for processing.
 *
 * Once the backlog is empty, the main link() method is unblocks and returns, rethrowing any error.
 */
class NonNamespacedDependenciesLinker(
    graph: DependenciesGraph,
    private val compiled: Map<Node, File>,
    private val outputStaticLibrariesDirectory: File,
    private val intermediateDirectory: File,
    private val pool: ForkJoinPool,
    private val aapt2ServiceKey: Aapt2DaemonServiceKey,
    private val aaptOptions: AaptOptions = AaptOptions(),
    private val errorFormatMode: SyncOptions.ErrorFormatMode,
    private val androidJarPath: String
) {

    /**
     * The libraries that should be linked, but are blocked on other libraries being linked first
     *
     * Initially, this will contain all libraries.
     */
    @GuardedBy("this")
    private val backlog =
        mutableListOf<QueuedBlockedNode>()
    @GuardedBy("this")
    private var failure: Exception? = null
    @GuardedBy("this")
    private val work: MutableList<ForkJoinTask<*>> = mutableListOf()
    /**
     * Set once all the libraries have been submitted for execution and removed from the backlog.
     */
    private val workSubmittedFuture =
        SettableFuture.create<List<ForkJoinTask<*>>>()

    init {
        /** Add all libraries to link to the backlog, blocked on all their dependencies. */
        graph.allNodes.forEach { node ->
            backlog.add(
                QueuedBlockedNode(
                    queued = node,
                    blockedOn = HashSet(node.dependencies)
                )
            )
        }
    }

    class QueuedBlockedNode(val queued: Node, val blockedOn: MutableSet<Node>)

    /** Submit worker actions to compile the resources for all AARs that don't have a static library */

    fun link() {
        /* At this point, either
         * 1. There are some QueuedDependencyNodes ready to be processed
         *    (leaves in the dependency graph), or
         * 2. There is nothing to do and this will complete trivially.
         */
        enqueueReady()

        // Wait for all the jobs to be submitted
        val workToWaitFor = workSubmittedFuture.get()

        // And wait for them to complete
        for (forkJoinTask in workToWaitFor) {
            forkJoinTask.get()
        }

        getFailure()?.let { throw RuntimeException("Failed to link resources within timeout.", it) }
    }

    @Synchronized
    private fun notifyCompletion(dependencyNode: Node) {
        backlog.forEach { it.blockedOn.remove(dependencyNode) }
        enqueueReady()
    }

    /**
     * Remove any nodes in the backlog
     */
    @Synchronized
    private fun enqueueReady() {
        if (backlog.isEmpty()) {
            workSubmittedFuture.set(ImmutableList.copyOf(work))
            return
        }
        val wereBlocked = ArrayList<QueuedBlockedNode>(backlog)
        backlog.clear()
        for (candidate in wereBlocked) {
            if (candidate.blockedOn.isEmpty()) {
                work.add(pool.submit { process(candidate.queued) })
            } else {
                backlog.add(candidate)
            }
        }
        if (backlog.isEmpty()) {
            workSubmittedFuture.set(ImmutableList.copyOf(work))
        }
    }

    @Synchronized
    private fun failed(e: Exception) {
        // Abort the rest of the processing
        failure = e
        backlog.clear()
        workSubmittedFuture.set(ImmutableList.copyOf(work))
    }

    @Synchronized
    private fun getFailure(): Exception? {
        return failure
    }

    /** Called from worker threads */
    private fun process(node: Node) {
        if (getType(node) == NodeType.REMOTE_NON_NAMESPACED) {
            try {
                link(node)
            } catch (e: Exception) {
                failed(e)
                return
            }
        } // Otherwise trivially complete.
        notifyCompletion(node)
    }

    private fun link(node: Node) {
        val staticLibraryDependencies =
            node.transitiveDependencies
                .filter { getType(it) != NodeType.JAR }
                .map { getStaticLibLocation(it) }
        val resourceOutputApk = getStaticLibLocation(node)
        Files.createDirectories(resourceOutputApk.toPath().parent)
        val resourceDirs = ImmutableList.copyOf(listOfNotNull(compiled[node]))
        val request = AaptPackageConfig(
            androidJarPath = androidJarPath,
            manifestFile = node.getFile(AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST)!!,
            options = aaptOptions,
            resourceDirs = resourceDirs,
            variantType = VariantTypeImpl.LIBRARY,
            staticLibrary = true,
            intermediateDir = intermediateDirectory,
            imports = staticLibraryDependencies.toImmutableList(),
            resourceOutputApk = resourceOutputApk
        )
        Aapt2LinkRunnable(
            Aapt2LinkRunnable.Params(
                aapt2ServiceKey,
                request,
                errorFormatMode
            )
        ).run()
    }

    private fun getStaticLibLocation(node: Node): File =
        when (getType(node)) {
            NodeType.REMOTE_NAMESPACED ->
                node.getFile(AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)!!
            NodeType.REMOTE_NON_NAMESPACED ->
                File(outputStaticLibrariesDirectory, getAutoNamespacedLibraryFileName(node.id))
            NodeType.LOCAL ->
                error("Invalid dependency graph.")
            NodeType.JAR ->
                error("No static library for libraries with no resources.")
        }
}

fun getType(node: DependenciesGraph.Node): NodeType {
    if (node.id is ProjectComponentIdentifier) {
        return NodeType.LOCAL
    }
    if (node.getFile(AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY) != null) {
        return NodeType.REMOTE_NAMESPACED
    }
    if (node.getFile(AndroidArtifacts.ArtifactType.NON_NAMESPACED_MANIFEST) != null) {
        return NodeType.REMOTE_NON_NAMESPACED
    }
    if (node.getFile(AndroidArtifacts.ArtifactType.ANDROID_RES) == null) {
        return NodeType.JAR
    }
    error("Manifest missing for ${node.id.displayName}")
}

enum class NodeType {
    LOCAL,
    REMOTE_NAMESPACED,
    REMOTE_NON_NAMESPACED,
    JAR
}
