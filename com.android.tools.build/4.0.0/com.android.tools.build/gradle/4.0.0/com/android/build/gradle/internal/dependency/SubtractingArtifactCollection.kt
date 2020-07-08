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
import java.io.File
import java.util.HashSet
import java.util.Spliterator
import java.util.function.Consumer
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection

/**
 * Implementation of a [ArtifactCollection] in order to do lazy subtractions.
 *
 * The main use case for this is building an ArtifactCollection that represents the packaged
 * dependencies of a test app, minus the runtime dependencies of the tested app (to avoid duplicated
 * classes during runtime).
 */
class SubtractingArtifactCollection(
    private val mainArtifacts: ArtifactCollection,
    private val removedArtifacts: ArtifactCollection
) : ArtifactCollection {

    /**
     * Just the [ComponentIdentifier] is not enough, we need to consider the classifier information
     * (see TestWithSameDepAsAppWithClassifier).
     *
     *  TODO(b/132924287): We should be able to instead pass the configuration and call
     *   `configuration.artifactView {  }.artifacts.artifacts`, but this gives artifacts with
     *   different names so the subtraction doesn't work
     *   (The classifier is actually duplicated in the name in some cases)
     */
    private val artifactResults: MutableSet<ResolvedArtifactResult> by lazy {
        val removed = HashSet<ComponentArtifactIdentifier>(removedArtifacts.artifacts.size)
        removedArtifacts.artifacts.mapTo(removed) { it.id }

        ImmutableSet.copyOf(mainArtifacts.artifacts.filter { !removed.contains(it!!.id) })
    }

    private val artifactFileSet: Set<File> by lazy {
        ImmutableSet.builder<File>().apply {
            for (artifact in artifactResults) {
                add(artifact.file)
            }
        }.build()
    }

    private val fileCollection: FileCollection =
        mainArtifacts.artifactFiles.filter { file -> artifactFileSet.contains(file) }

    override fun getArtifactFiles() = fileCollection

    override fun getArtifacts() = artifactResults

    override fun getFailures() = mainArtifacts.failures + removedArtifacts.failures

    override fun iterator() = artifactResults.iterator()

    override fun forEach(action: Consumer<in ResolvedArtifactResult>) = artifacts.forEach(action)

    override fun spliterator() = artifacts.spliterator()

    override fun toString(): String {
        return "SubtractingArtifactCollection(mainArtifacts=$mainArtifacts, " +
                "removedArtifacts=$removedArtifacts)"
    }
}
