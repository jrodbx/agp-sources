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

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Gradle's [Property] and [ListProperty] are used to store single and multiple files
 * respectively. Unfortunately, they do not have a common interface for services like
 * [Property.set] and [ListProperty.set] so a small adapter is necessary to delegate
 * to the right method and provider a common set of APIs.
 *
 * @param FileTypeT the type of [FileSystemLocation], a file or a directory
 * @param T is either a [FileSystemLocation] subclass (so basically the same as [FileTypeT] or
 * a [List] of [FileTypeT]
 */
interface PropertyAdapter<FileTypeT, T> {
    /**
     * Sets this [Property] or [ListProperty] with single provider of [FileTypeT]
     */
    fun set(with: Provider<FileTypeT>)

    /**
     * @return a [Provider] of a [FileSystemLocation] or a [Provider] or a [List] of
     * [FileSystemLocation]
     */
    fun get(): Provider<T>

    /**
     * Disallow further changes on this property.
     */
    fun disallowChanges()

    /**
     * Convenience method to set this property value from another [PropertyAdapter]'s value.
     */
    fun from(source: PropertyAdapter<FileTypeT, T>)
}

/**
 * Implementation of [PropertyAdapter] for a single [FileSystemLocation] element.
 */
class SinglePropertyAdapter<FileTypeT: FileSystemLocation>(val property: FileSystemLocationProperty<FileTypeT>)
    : PropertyAdapter<FileTypeT, FileTypeT> {

    override fun disallowChanges() {
        property.disallowChanges()
    }

    override fun get(): Provider<FileTypeT> {
        return property
    }

    override fun set(with: Provider<FileTypeT>) {
        property.set(with)
    }

    override fun from(source: PropertyAdapter<FileTypeT, FileTypeT>) {
        set(source.get())
    }

    fun locationOnly(): Provider<FileTypeT> {
        return property.locationOnly
    }
}

/**
 * Implementation of [PropertyAdapter] for multiple [FileSystemLocation] elements
 */
class MultiplePropertyAdapter<FileTypeT: FileSystemLocation>(
    val property: ListProperty<FileTypeT>,
    val propertyAllocator: () -> FileSystemLocationProperty<FileTypeT>,
):
    PropertyAdapter<FileTypeT, List<FileTypeT>> {

    private val locationsOnly = mutableListOf<Provider<FileTypeT>>()

    override fun disallowChanges() {
        property.disallowChanges()
    }

    override fun get(): Provider<List<FileTypeT>> = property

    override fun set(with: Provider<FileTypeT>) {
        property.empty()
        add(with)
        locationsOnly.add(propertyAllocator().also {
            it.set(with)
        }.locationOnly)
    }

    /**
     * Empty this collection of [FileSystemLocation] elements.
     */
    fun empty(): MultiplePropertyAdapter<FileTypeT> {
        property.empty()
        locationsOnly.clear()
        return this
    }

    /**
     * Adds a [Provider] of [FileSystemLocation] to the collection of elements.
     */
    fun add(item: Provider<FileTypeT>) {
        property.add(item)
        locationsOnly.add(propertyAllocator().also {
            it.set(item)
        }.locationOnly)
    }

    /**
     * Adds a [Provider] of a [List] of [FileSystemLocation] to the collection of
     * elements.
     */
    fun addAll(with: Provider<List<FileTypeT>>) {
        property.addAll(with)
    }

    override fun from(source: PropertyAdapter<FileTypeT, List<FileTypeT>>) {
        property.empty()
        property.set(source.get())
        source as MultiplePropertyAdapter<FileTypeT>
        locationsOnly.clear()
        locationsOnly.addAll(source.locationsOnly)
    }

    fun locationOnly(): List<Provider<FileTypeT>> = locationsOnly
}
