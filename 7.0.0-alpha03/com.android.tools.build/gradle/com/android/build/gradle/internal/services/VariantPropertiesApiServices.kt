/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.services

import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import java.io.File
import java.util.concurrent.Callable

/**
 * Services for the VariantProperties API objects.
 *
 * This contains whatever is needed by all the variant properties objects.
 *
 * This is meant to be used only by the variant properties api objects. Other stages of the plugin
 * will use different services objects.
 */
interface VariantPropertiesApiServices:
    BaseServices {

    /**
     * Creates a new property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> propertyOf(type: Class<T>, value: T, id: String = ""): Property<T>

    /**
     * Creates a new property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> propertyOf(type: Class<T>, value: Provider<T>, id: String = ""): Property<T>

    /**
     * Creates a new nullable property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> nullablePropertyOf(type: Class<T>, value: Provider<T?>): Property<T?>

    /**
     * Creates a new property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> propertyOf(type: Class<T>, value: () -> T, id: String = ""): Property<T>

    /**
     * Creates a new property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> propertyOf(type: Class<T>, value: Callable<T>, id: String = ""): Property<T>

    /**
     * Creates a new nullable property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> nullablePropertyOf(type: Class<T>, value: T?, id: String = ""): Property<T?>

    /**
     * Creates a new nullable property.
     *
     * This should be used for properties used in the new API. If the property is backing an
     * old API that returns T, use [VariantPropertiesApiServices.newPropertyBackingDeprecatedApi]
     *
     * The property will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> nullablePropertyOf(type: Class<T>, value: Provider<T?>, id: String = ""): Property<T?>

    /**
     * Creates a new [ListProperty].
     *
     * This should be used for properties used in the new API.
     *
     * The [ListProperty] will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> listPropertyOf(type: Class<T>, value: Collection<T>): ListProperty<T>

    /**
     * Creates a new [SetProperty].
     *
     * This should be used for properties used in the new API.
     *
     * The [SetProperty] will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <T> setPropertyOf(type: Class<T>, value: Callable<Collection<T>>): SetProperty<T>

    fun <T> setPropertyOf(type: Class<T>, value: Collection<T>): SetProperty<T>

    /**
     * Creates a new [MapProperty].
     *
     * This should be used for properties used in the new API.
     *
     * The [MapProperty] will be marked as [Property.finalizeValueOnRead], and will be locked
     * with [Property.disallowChanges] after the variant API(s) have run.
     */
    fun <K, V> mapPropertyOf(keyType: Class<K>, valueType: Class<V>, value: Map<K, V>): MapProperty<K, V>

    /**
     * Creates a new property that is backing an old API returning T.
     *
     * By default this property is memoized with [Property.finalizeValueOnRead] but access
     * to the old API getter will require disabling memoization
     *
     * The property will be locked with [Property.disallowChanges] after the variant API(s) have
     * run.
     */
    fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: T, id: String = ""): Property<T>

    /**
     * Creates a new property that is backing an old API returning T.
     *
     * By default this property is memoized with [Property.finalizeValueOnRead] but access
     * to the old API getter will require disabling memoization
     *
     * The property will be locked with [Property.disallowChanges] after the variant API(s) have
     * run.
     */
    fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: Callable<T>, id: String = ""): Property<T>

    /**
     * Creates a new property that is backing an old API returning T.
     *
     * By default this property is memoized with [Property.finalizeValueOnRead] but access
     * to the old API getter will require disabling memoization
     *
     * The property will be locked with [Property.disallowChanges] after the variant API(s) have
     * run.
     */
    fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: Provider<T>, id: String = ""): Property<T>

    /**
     * Creates a new property that is backing an old API returning T.
     *
     * By default this property is memoized with [Property.finalizeValueOnRead] but access
     * to the old API getter will require disabling memoization
     *
     * The property will be locked with [Property.disallowChanges] after the variant API(s) have
     * run.
     */
    fun <T> newNullablePropertyBackingDeprecatedApi(type: Class<T>, value: Provider<T?>, id: String = ""): Property<T?>

    /**
     * Creates a memoized Provider around the given provider
     */
    fun <T> providerOf(type: Class<T>, value: Provider<T>, id: String = ""): Provider<T>

    /**
     * Creates a memoized Provider around the given provider
     */
    fun <T> nullableProviderOf(type: Class<T>, value: Provider<T?>, id: String = ""): Provider<T?>

    fun <T> setProviderOf(type: Class<T>, value: Provider<out Iterable<T>?>): Provider<Set<T>?>
    fun <T> setProviderOf(type: Class<T>, value: Iterable<T>?): Provider<Set<T>?>

    fun <T> provider(callable: Callable<T>): Provider<T>

    fun <T : Named> named(type: Class<T>, name: String): T

    fun file(file: Any): File

    fun fileCollection(): ConfigurableFileCollection
    fun fileCollection(vararg files: Any): ConfigurableFileCollection
    fun fileTree(): ConfigurableFileTree
    fun fileTree(dir: Any): ConfigurableFileTree

    fun lockProperties()
}
