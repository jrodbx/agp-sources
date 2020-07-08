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

package com.android.build.api.artifact.impl

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Common behaviors for a container of an artifact that gets published within the
 * project scope.
 *
 * The container will provide methods to set, get the artifact. The artifact type is
 * always either a [org.gradle.api.file.RegularFile] or a [org.gradle.api.file.Directory].
 * An artifact cardinality is either single or multiple elements of the above type.
 *
 * @param T the artifact type as [FileSystemLocation] subtype for single element artifact
 * or a [List] of the same [FileSystemLocation] subtype for multiple elements artifact.
 */
internal abstract class ArtifactContainer<T>(private val allocator: () -> PropertyAdapter<T>) {

    // this represent the current provider(s) for the artifact.
    internal var current = allocator()

    // property that can be used to inject into consumers of the artifact at any time.
    // it will always represent the final value.
    val final = allocator()

    private val needInitialProducer = AtomicBoolean(true)
    private val hasCustomTransformers = AtomicBoolean(false)

    /**
     * Specific hook for AGP providers to register the initial producer of the artifact.
     */
    abstract fun setInitialProvider(with: Provider<T>)

    /**
     * If another [org.gradle.api.Task] is replacing the initial providers through the
     * Variant API, it is interesting to determine that initial providers are useless since replaced
     * and therefore should probably not even be configured.
     *
     * @return true if the AGP providers will be used when the artifact becomes resolved.
     */
    fun needInitialProducer() = needInitialProducer

    /**
     * @return the final version of the artifact with associated providers to build it.
     */
    fun get(): Provider<T> = final.get()

    /**
     * Returns a protected (no changes allowed) version of the current artifact providers.
     *
     * @return the current version of the artifact providers at the time this call is made.
     * This is very useful for [org.gradle.api.Task] that want to transform an artifact and need the
     * current version as its input while producing the final version.
     */
    @VisibleForTesting
    internal fun getCurrent(): Provider<T>  {
        val property = allocator()
        property.set(current.get())
        property.disallowChanges()
        return property.get()
    }

    /**
     * Replace the current producer(s). Previous producers may still be used to produce [with]
     *
     * @param with the provider that will be the transformed artifact.
     * @return the current provider for the artifact (which will be transformed)
     */
    @Synchronized
    open fun transform(with: Provider<T>): Provider<T> {
        hasCustomTransformers.set(true)
        val oldCurrent = current
        current = allocator()
        current.set(with)
        final.set(current.get())
        return oldCurrent.get()
    }

    /**
     * Replace the current producer(s) for this artifact with a new one.
     */
    @Synchronized
    open fun replace(with: Provider<T>) {
        needInitialProducer.set(false)
        transform(with)
    }

    /**
     * Locks this artifact for any further changes.
     */
    open fun disallowChanges() {
        current.disallowChanges()
        final.disallowChanges()
    }

    /**
     * Returns true if at least one custom provider is registered for this artifact.
     */
    fun hasCustomProviders(): Boolean {
        return hasCustomTransformers.get()
    }
}

/**
 * Specialization of [ArtifactContainer] for single elements of [FileSystemLocation]
 *
 * @param T the single element type, either [org.gradle.api.file.RegularFile] or
 * [org.gradle.api.file.Directory]
 */
internal class SingleArtifactContainer<T: FileSystemLocation>(
    val allocator: () -> SinglePropertyAdapter<T>
) : ArtifactContainer<T>(allocator) {

    private val agpProducer= allocator()

    init {
        current.from(agpProducer)
        final.from(current)
    }

    override fun setInitialProvider(with: Provider<T>) {
        // TODO: should we make this an assertion.
        if (needInitialProducer().compareAndSet(true, false)) {
            agpProducer.set(with)
        }
    }

    override fun disallowChanges() {
        super.disallowChanges()
        agpProducer.disallowChanges()
    }
}
/**
 * Specialization of [ArtifactContainer] for multiple elements of [FileSystemLocation]
 *
 * @param T the multiple elements type, either [org.gradle.api.file.RegularFile] or
 * [org.gradle.api.file.Directory]
 */
internal class MultipleArtifactContainer<T: FileSystemLocation>(
    val allocator: () -> MultiplePropertyAdapter<T>
):
    ArtifactContainer<List<T>>(allocator) {

    // this represent the providers from the AGP.
    private val agpProducers = allocator()

    init {
        current.set(agpProducers.get())
        final.set(current.get())
    }

    override fun setInitialProvider(with: Provider<List<T>>) {
        needInitialProducer().set(false)
        // in theory, we should add those first ?
        agpProducers.addAll(with)
    }

    fun addInitialProvider(item: Provider<T>) {
        needInitialProducer().set(false)
        agpProducers.add(item)
    }

    override fun disallowChanges() {
        super.disallowChanges()
        agpProducers.disallowChanges()
    }
}