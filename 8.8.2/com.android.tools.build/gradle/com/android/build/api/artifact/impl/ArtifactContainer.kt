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

import com.android.build.api.artifact.Artifact
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
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
internal abstract class ArtifactContainer<T, U>(private val allocator: () -> U) where U: PropertyAdapter<T> {

    // this represents the current provider(s) for the artifact.
    protected var current = allocator()

    // property that can be used to inject into consumers of the artifact at any time.
    // it will always represent the final value.
    protected val final = allocator()

    private val needInitialProducer = AtomicBoolean(true)

    // Collections are used together to accumulate all providers.
    // At the end initialTaskProviders+transformationTaskProviders
    // will have list of providers in order of execution.
    // Providers are used for automatically set dependencies based on the
    // [InternalArtifactType.finalizingArtifact] annotation attribute.
    protected val initialTaskProviders = mutableListOf<TaskProvider<*>>()
    protected val transformationTaskProviders = mutableListOf<TaskProvider<*>>()

    fun getFinalProvider() = (initialTaskProviders + transformationTaskProviders).lastOrNull()

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

    fun getTaskProviders(): List<TaskProvider<*>> =
        (initialTaskProviders + transformationTaskProviders).toList()

    internal fun getInitialTaskProviders(): List<TaskProvider<*>> = initialTaskProviders.toList()
    internal fun getTransformationTaskProviders(): List<TaskProvider<*>> = initialTaskProviders.toList()

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
        property.from(current)
        property.disallowChanges()
        return property.get()
    }

    /**
     * Replace the current producer(s). Previous producers may still be used to produce [with]
     *
     * @param taskProvider the task provider for the task producing the [with]. Mainly provided for
     * bookkeeping reasons, not strictly required for wiring.
     * @param with the provider that will be the transformed artifact.
     * @return the current provider for the artifact (which will be transformed)
     */
    @Synchronized
    open fun transform(taskProvider: TaskProvider<*>, with: Provider<T>): Provider<T> {
        transformationTaskProviders.add(taskProvider)
        return updateProvider(with)
    }

    private fun updateProvider(with: Provider<T>): Provider<T>{
        val oldCurrent = current
        current = allocator()
        current.set(with)

        final.from(current)
        return oldCurrent.get()
    }

    /**
     * Replace the current producer(s) for this artifact with a new one.
     *
     * @param taskProvider the task provider for the task producing the [with]. Mainly provided for
     * bookkeeping reasons, not strictly required for wiring.
     * @param with the provider that will be the transformed artifact.
     */
    @Synchronized
    open fun replace(taskProvider: TaskProvider<*>, with: Provider<T>) {
        needInitialProducer.set(false)
        initialTaskProviders.clear()
        initialTaskProviders.add(taskProvider)
        updateProvider(with)
    }

    /**
     * Locks this artifact for any further changes.
     */
    open fun disallowChanges() {
        current.disallowChanges()
        final.disallowChanges()
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
) : ArtifactContainer<T, SinglePropertyAdapter<T>>(allocator) {

    private val agpProducer = allocator()

    init {
        current.from(agpProducer)
        final.from(current)
    }

    var namingContext: ArtifactNamingContext? = null

    private fun runForNonInitializedProvider(f: () -> Unit) {
        if (needInitialProducer().compareAndSet(true, false)) {
            f()
        }
    }

    /**
     * Specific hook for AGP providers to register the initial producer of the artifact as
     * well as folder/file naming.
     *
     * @param taskProvider the task provider for the task producing the [with]. Mainly provided for
     * bookkeeping reasons, not strictly required for wiring.
     * @param with the provider that will be the transformed artifact.
     * @param initialNamingContext location file naming data
     */
    fun initArtifactContainer(taskProvider: TaskProvider<*>,
        with: Provider<T>,
        initialNamingContext: ArtifactNamingContext) {
        namingContext = initialNamingContext

        runForNonInitializedProvider {
            agpProducer.set(with)
            initialTaskProviders.add(taskProvider)
        }
    }

    /**
     * Copies initial and transformation providers from another [ArtifactContainer]
     */
    fun transferFrom(from: SingleArtifactContainer<T>) {
        runForNonInitializedProvider {
            agpProducer.set(from.final.get())
            initialTaskProviders.addAll(from.initialTaskProviders)
            transformationTaskProviders.addAll(from.transformationTaskProviders)
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
    ArtifactContainer<List<T>, MultiplePropertyAdapter<T>>(allocator) {

    // this represents the providers from the AGP.
    private val agpProducers = allocator()

    init {
        current.from(agpProducers)
        final.from(current)
    }

    fun addInitialProvider(taskProvider: List<TaskProvider<*>>, with: Provider<List<T>>) {
        needInitialProducer().set(false)
        // in theory, we should add those first ?
        agpProducers.addAll(with)
        initialTaskProviders.addAll(taskProvider)
    }

    /**
     * Copies only initial providers from given [MultipleArtifactContainer]
     */
    fun addInitialProvider(from: MultipleArtifactContainer<T>) {
        needInitialProducer().set(false)
        agpProducers.addAll(from.final.get())
        initialTaskProviders.addAll(from.getInitialTaskProviders())
    }

    fun addInitialProvider(taskProvider: TaskProvider<*>, item: Provider<T>) {
        needInitialProducer().set(false)
        agpProducers.add(item)
        initialTaskProviders.add(taskProvider)
    }

    fun addInitialProvider(item: Provider<T>) {
        needInitialProducer().set(false)
        agpProducers.add(item)
    }

    /**
     * Copies initial and transformation providers from 'source'
     */
    fun transferFrom(source: ArtifactsImpl, from: Artifact.Single<T>) {
        needInitialProducer().set(false)
        source.getArtifactContainer(from).let { artifactContainer ->
            agpProducers.add(artifactContainer.get())
            initialTaskProviders.addAll(artifactContainer.getInitialTaskProviders())
            transformationTaskProviders.addAll(artifactContainer.getTransformationTaskProviders())
        }
    }

    override fun disallowChanges() {
        super.disallowChanges()
        agpProducers.disallowChanges()
    }
}
