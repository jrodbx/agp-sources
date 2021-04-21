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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.model.ObjectFactory
import org.gradle.api.specs.Spec
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.HashSet
import java.util.function.Consumer

/**
 * Implementation of a [ArtifactCollection] in order to do lazy subtractions.
 *
 * The main use case for this is building an ArtifactCollection that represents the packaged
 * dependencies of a test app, minus the runtime dependencies of the tested app (to avoid duplicated
 * classes during runtime).
 */
class SubtractingArtifactCollection(
    private val mainArtifacts: ArtifactCollection,
    private val removedArtifacts: ArtifactCollection,
    private val objectFactory: ObjectFactory
) : ArtifactCollection {

    private var subtractingArtifactResult = this.SubtractingArtifactResult()

    override fun getArtifactFiles() = subtractingArtifactResult.fileCollection.value

    override fun getArtifacts() = subtractingArtifactResult.artifactResults.value

    override fun getFailures() = mainArtifacts.failures + removedArtifacts.failures

    override fun iterator() = subtractingArtifactResult.artifactResults.value.iterator()

    override fun forEach(action: Consumer<in ResolvedArtifactResult>) = artifacts.forEach(action)

    override fun spliterator() = artifacts.spliterator()

    override fun toString(): String {
        return "SubtractingArtifactCollection(mainArtifacts=$mainArtifacts, " +
                "removedArtifacts=$removedArtifacts)"
    }

    /**
     * Wrapper for the set of [ResolvedArtifactResult]. Gradle configuration caching cannot directly
     * serialize them, so we need to make Gradle not serialize them and instead compute them from
     * other de-serialized properties in the following configuration-cached runs.
     */
    inner class SubtractingArtifactResult : Serializable {
        @Transient
        var artifactResults: Lazy<MutableSet<ResolvedArtifactResult>> = lazy { initArtifactResult() }

        @Transient
        private var artifactFileSet: Lazy<Set<File>> = lazy { initArtifactFileSet() }

        @Transient
        var fileCollection = lazy { initFileCollection() }

        /**
         * Just the [ComponentIdentifier] is not enough, we need to consider the classifier information
         * (see TestWithSameDepAsAppWithClassifier).
         *
         *  TODO(b/132924287): We should be able to instead pass the configuration and call
         *   `configuration.artifactView {  }.artifacts.artifacts`, but this gives artifacts with
         *   different names so the subtraction doesn't work
         *   (The classifier is actually duplicated in the name in some cases)
         */
        private fun initArtifactResult(): MutableSet<ResolvedArtifactResult> {
            val removed = HashSet<ComponentArtifactIdentifier>(removedArtifacts.artifacts.size)
            removedArtifacts.artifacts.mapTo(removed) { it.id }
            return ImmutableSet.copyOf(mainArtifacts.artifacts.filter { !removed.contains(it!!.id) })
        }

        private fun initArtifactFileSet() = ImmutableSet.builder<File>().apply {
            for (artifact in artifactResults.value) {
                add(artifact.file)
            }
        }.build()

        private fun initFileCollection() = objectFactory.fileCollection().from(
            mainArtifacts.artifactFiles.filter { f -> artifactFileSet.value.contains(f) }
        ).builtBy(mainArtifacts.artifactFiles, removedArtifacts.artifactFiles)

        private fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.defaultWriteObject()
        }

        private fun readObject(objectInputStream: ObjectInputStream) {
            objectInputStream.defaultReadObject()
            artifactResults = lazy { initArtifactResult() }
            artifactFileSet = lazy { initArtifactFileSet() }
            fileCollection = lazy { initFileCollection() }
        }
    }
}