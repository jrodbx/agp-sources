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

import com.android.build.api.variant.impl.GradleProperty
import com.android.build.gradle.options.BooleanOption
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import java.io.File
import java.util.concurrent.Callable

class VariantPropertiesApiServicesImpl(
    projectServices: ProjectServices
): BaseServicesImpl(projectServices),
    VariantPropertiesApiServices {
    // list of properties to lock when [.lockProperties] is called.
    private val properties = mutableListOf<HasConfigurableValue>()
    // whether the properties have been locked already
    private var propertiesLockStatus = false

    // flag to know whether to enable compatibility mode for properties that back old API returning the
    // direct value.
    private val compatibilityMode = projectServices.projectOptions[BooleanOption.ENABLE_LEGACY_API]

    override fun <T> propertyOf(type: Class<T>, value: T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: Provider<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> nullablePropertyOf(type: Class<T>, value: Provider<T?>): Property<T?> {
        return initializeNullableProperty(type, "").also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: () -> T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectServices.providerFactory.provider(value))
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> propertyOf(type: Class<T>, value: Callable<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectServices.providerFactory.provider(value))
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> nullablePropertyOf(type: Class<T>, value: T?, id: String): Property<T?> {
        return initializeNullableProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> nullablePropertyOf(type: Class<T>, value: Provider<T?>, id: String): Property<T?> {
        return initializeNullableProperty(type, id).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> listPropertyOf(type: Class<T>, value: Collection<T>): ListProperty<T> {
        return projectServices.objectFactory.listProperty(type).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> setPropertyOf(type: Class<T>, value: Callable<Collection<T>>): SetProperty<T> {
        return projectServices.objectFactory.setProperty(type).also {
            it.set(projectServices.providerFactory.provider(value))
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> setPropertyOf(type: Class<T>, value: Collection<T>): SetProperty<T> {
        return projectServices.objectFactory.setProperty(type).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <K, V> mapPropertyOf(
        keyType: Class<K>,
        valueType: Class<V>,
        value: Map<K, V>
    ): MapProperty<K, V> {
        return projectServices.objectFactory.mapProperty(keyType, valueType).also {
            it.set(value)
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()

            delayedLock(it)
        }
    }

    override fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: T, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            if (!compatibilityMode) {
                it.finalizeValueOnRead()

                // FIXME when Gradle supports this
                // it.preventGet()
            }

            delayedLock(it)
        }
    }

    override fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: Callable<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(projectServices.providerFactory.provider(value))
            if (!compatibilityMode) {
                it.finalizeValueOnRead()

                // FIXME when Gradle supports this
                // it.preventGet()
            }

            delayedLock(it)
        }
    }

    override fun <T> newPropertyBackingDeprecatedApi(type: Class<T>, value: Provider<T>, id: String): Property<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            if (!compatibilityMode) {
                it.finalizeValueOnRead()

                // FIXME when Gradle supports this
                // it.preventGet()
            }


            delayedLock(it)
        }
    }

    override fun <T> newNullablePropertyBackingDeprecatedApi(type: Class<T>, value: Provider<T?>, id: String
    ): Property<T?> {
        return initializeNullableProperty(type, id).also {
            it.set(value)
            if (!compatibilityMode) {
                it.finalizeValueOnRead()

                // FIXME when Gradle supports this
                // it.preventGet()
            }

            delayedLock(it)
        }
    }

    override fun <T> providerOf(type: Class<T>, value: Provider<T>, id: String): Provider<T> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.disallowChanges()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> nullableProviderOf(type: Class<T>, value: Provider<T?>, id: String): Provider<T?> {
        return initializeProperty(type, id).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> setProviderOf(type: Class<T>, value: Provider<out Iterable<T>?>): Provider<Set<T>?> {
        return projectServices.objectFactory.setProperty(type).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> setProviderOf(type: Class<T>, value: Iterable<T>?): Provider<Set<T>?> {
        return projectServices.objectFactory.setProperty(type).also {
            it.set(value)
            it.disallowChanges()
            it.finalizeValueOnRead()

            // FIXME when Gradle supports this
            // it.preventGet()
        }
    }

    override fun <T> provider(callable: Callable<T>): Provider<T> {
        return projectServices.providerFactory.provider(callable)
    }

    override fun <T : Named> named(type: Class<T>, name: String): T =
        projectServices.objectFactory.named(type, name)

    override fun file(file: Any): File = projectServices.fileResolver.invoke(file)

    override fun fileCollection(): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection()

    override fun fileCollection(vararg files: Any): ConfigurableFileCollection =
        projectServices.objectFactory.fileCollection().from(*files)

    override fun fileTree(): ConfigurableFileTree = projectServices.objectFactory.fileTree()

    override fun fileTree(dir: Any): ConfigurableFileTree {
        val result = projectServices.objectFactory.fileTree().setDir(dir)

        // workaround issue in Gradle <=6.3 where setDir does not set dependencies
        // TODO remove when 6.4 ships
        if (dir is Provider<*>) {
            result.builtBy(dir)
        }
        return result
    }

    override fun lockProperties() {
        for (property in properties) {
            property.disallowChanges()
        }
        properties.clear()
        propertiesLockStatus = true
    }

    // register a property to be locked later.
    // if the properties have already been locked, the property is locked right away.
    // (this can happen for objects that are lazily created)
    private fun delayedLock(property: HasConfigurableValue) {
        if (propertiesLockStatus) {
            property.disallowChanges()
        } else {
            properties.add(property)
        }
    }

    private fun <T> initializeProperty(type: Class<T>, id: String): Property<T> {

        return if (projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(
                id,
                projectServices.objectFactory.property(type)
            )
        } else {
            projectServices.objectFactory.property(type)
        }
    }

    private fun <T> initializeNullableProperty(type: Class<T>, id: String): Property<T?> {

        return if (projectOptions[BooleanOption.USE_SAFE_PROPERTIES]) {
            GradleProperty.safeReadingBeforeExecution(
                id,
                projectServices.objectFactory.property(type)
            )
        } else {
            projectServices.objectFactory.property(type)
        }
    }
}
