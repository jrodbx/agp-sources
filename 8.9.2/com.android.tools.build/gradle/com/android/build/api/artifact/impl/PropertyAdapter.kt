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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Gradle's [Property] and [ListProperty] are used to store single and multiple files
 * respectively. Unfortunately, they do not have a common interface for services like
 * [Property.set] and [ListProperty.set] so a small adapter is necessary to delegate
 * to the right method and provider a common set of APIs.
 *
 * @param T is either a [FileSystemLocation] subclass or a [List] of [FileSystemLocation]
 */
interface PropertyAdapter<T> {
    /**
     * Sets this [Property] or [ListProperty] with the provider of T or List<T>
     */
    fun set(with: Provider<T>)

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
    fun from(source: PropertyAdapter<T>) {
        set(source.get())
    }
}

/**
 * Implementation of [PropertyAdapter] for a single [FileSystemLocation] element.
 */
class SinglePropertyAdapter<T: FileSystemLocation>(val property: Property<T>)
    : PropertyAdapter<T> {

    override fun disallowChanges() {
        property.disallowChanges()
    }

    override fun get(): Provider<T> {
        return property
    }

    override fun set(with: Provider<T>) {
        property.set(with)
    }
}

/**
 * Implementation of [PropertyAdapter] for multiple [FileSystemLocation] elements
 */
class MultiplePropertyAdapter<T: FileSystemLocation>(val property: ListProperty<T>):
    PropertyAdapter<List<T>> {

    override fun disallowChanges() {
        property.disallowChanges()
    }

    override fun get(): Provider<List<T>> = property

    override fun set(with: Provider<List<T>>) {
        property.set(with)
    }

    /**
     * Empty this collection of [FileSystemLocation] elements.
     */
    fun empty(): MultiplePropertyAdapter<T> {
        property.empty()
        return this
    }

    /**
     * Adds a [Provider] of [FileSystemLocation] to the collection of elements.
     */
    fun add(item: Provider<T>) {
        property.add(item)
    }

    /**
     * Adds a [Provider] of a [List] of [FileSystemLocation] to the collection of
     * elements.
     */
    fun addAll(with: Provider<List<T>>) {
        property.addAll(with)
    }
}
