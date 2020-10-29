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

package com.android.build.api.variant.impl

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.HasConfigurableValueInternal
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ScalarSupplier
import org.gradle.api.internal.provider.ValueSanitizer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.DisplayName
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Gradle [Property] with added services to enforce safety.
 *
 * This is a miserable hack to work around that fact Gradle will cast the Property objects
 * to internal types.
 *
 * This will provided added services like property names which will help debugging while converting
 * to the new Variant API. It is unclear if this wrapper will remain once conversion is done.
 */
open class GradleProperty<T>(
    private val property: Property<T>) : PropertyInternal<T>, ProviderInternal<T>, Property<T> {

    override fun finalizeValueOnRead() {
        property.finalizeValueOnRead()
    }

    override fun getOrElse(p0: T): T {
        return property.getOrElse(p0)
    }

    override fun disallowChanges() {
        return property.disallowChanges()
    }

    override fun getOrNull(): T? {
        return property.orNull
    }

    override fun isPresent(): Boolean {
        return property.isPresent
    }

    override fun <S : Any?> map(p0: Transformer<out S, in T>): ProviderInternal<S> {
        @Suppress("UNCHECKED_CAST")
        return (property as ProviderInternal<T>).map(p0)
    }

    override fun finalizeValue() {
        property.finalizeValue()
    }

    override fun get(): T {
        return property.get()
    }

    override fun <S : Any?> flatMap(p0: Transformer<out Provider<out S>, in T>): Provider<S> {
        return property.flatMap(p0)
    }

    override fun orElse(p0: T): Provider<T> {
        return property.orElse(p0)
    }

    override fun orElse(p0: Provider<out T>): Provider<T> {
        return property.orElse(p0)
    }

    override fun value(p0: T?): Property<T> {
        return property.value(p0)
    }

    override fun value(p0: Provider<out T>): Property<T> {
        return property.value(p0)
    }

    override fun set(p0: T?) {
        return property.set(p0)
    }

    override fun set(p0: Provider<out T>) {
        return property.set(p0)
    }

    override fun convention(p0: T): Property<T> {
        return property.convention(p0)
    }

    override fun convention(p0: Provider<out T>): Property<T> {
        return property.convention(p0)
    }

    override fun getType(): Class<T>? {
        @Suppress("UNCHECKED_CAST")
        return (property as ProviderInternal<T>).getType()
    }

    override fun visitDependencies(p0: TaskDependencyResolveContext) {
        @Suppress("UNCHECKED_CAST")
        return (property as ProviderInternal<T>).visitDependencies(p0)
    }

    override fun attachProducer(p0: Task) {
        @Suppress("UNCHECKED_CAST")
        (property as PropertyInternal<T>).attachProducer(p0)
    }

    override fun implicitFinalizeValue() {
        (property as HasConfigurableValueInternal).implicitFinalizeValue()
    }

    override fun maybeVisitBuildDependencies(p0: TaskDependencyResolveContext): Boolean {
        @Suppress("UNCHECKED_CAST")
        return (property as ProviderInternal<T>).maybeVisitBuildDependencies(p0)
    }

    override fun setFromAnyValue(p0: Any) {
        @Suppress("UNCHECKED_CAST")
        (property as PropertyInternal<T>).setFromAnyValue(p0)
    }

    override fun attachDisplayName(p0: DisplayName) {
        (property as PropertyInternal<*>).attachDisplayName(p0)
    }

    override fun asSupplier(
        p0: DisplayName,
        p1: Class<in T>,
        p2: ValueSanitizer<in T>
    ): ScalarSupplier<T> {
        @Suppress("UNCHECKED_CAST")
        return (property as ProviderInternal<T>).asSupplier(p0, p1, p2)
    }

    override fun isValueProducedByTask(): Boolean {
        return (property as ProviderInternal<*>).isValueProducedByTask
    }

    override fun visitProducerTasks(p0: Action<in Task>) {
        return (property as ProviderInternal<*>).visitProducerTasks(p0)
    }

    companion object {
        private val inExecutionMode = AtomicBoolean(false)

        fun endOfEvaluation() {
            inExecutionMode.set(true)
        }

        /**
         * A special version of [Property] that does not allow access to any of the [Property.get]
         * methods while in configuration phase.
         */
        fun <T> noReadingBeforeExecution(id: String, property: Property<T>, initialValue: T?, executionMode: AtomicBoolean = inExecutionMode): Property<T> {
            return NoReadingBeforeExecution(id, property, executionMode).also { it.set(initialValue) }
        }

        fun <T> safeReadingBeforeExecution(id: String, property: Property<T>, initialValue: T? = null, executionMode: AtomicBoolean = inExecutionMode): Property<T> {
            return SafeReadingBeforeExecution(id, property, executionMode).also { it.set( initialValue) }
        }

        fun <T> safeReadingBeforeExecution(id: String, property: Property<T>, initialValue: Provider<T>, executionMode: AtomicBoolean = inExecutionMode): Property<T> {
            return SafeReadingBeforeExecution(id, property, executionMode).also { it.set( initialValue) }
        }
    }
}

/**
 * Special [Property] that does not allow reading during configuration phase.
 */
private class NoReadingBeforeExecution<T>(
    private val id: String,
    property: Property<T>,
    private val inExecutionMode: AtomicBoolean): GradleProperty<T>(property) {

    override fun get(): T {
        if (!inExecutionMode.get()) {
            throw RuntimeException("Property.get() method invoked on $id before execution phase")
        }
        return super.get()
    }

    override fun getOrNull(): T? {
        if (!inExecutionMode.get()) {
            throw RuntimeException("Property.getOrNull() method invoked on $id before execution phase")
        }
        return super.getOrNull()
    }

    override fun getOrElse(p0: T): T {
        if (!inExecutionMode.get()) {
            throw RuntimeException("Property.getOrElse() method invoked on $id before execution phase")
        }
        return super.getOrElse(p0)
    }
}

/**
 * A special [Property] that does not allow to do set() with a [Provider] if get() has been called
 * or visa versa. A set() with a value is always permitted.
 *
 * Once configuration phase is finished, all calls are permitted.
 */
private class SafeReadingBeforeExecution<T>(
    private val id: String,
    property: Property<T>,
    private val inExecutionMode: AtomicBoolean): GradleProperty<T>(property) {

    private val providerSet = AtomicBoolean(false)
    private val getIssued = AtomicBoolean(false)

    override fun get(): T {
        if (providerSet.get() && !inExecutionMode.get()) {
            throw RuntimeException("A [Provider] was set of $id property, therefore get() cannot be issued before execution phase")
        }
        getIssued.set(true)
        return super.get()
    }

    override fun getOrElse(p0: T): T {
        if (providerSet.get() && !inExecutionMode.get()) {
            throw RuntimeException("A [Provider] was set of $id property, therefore get() cannot be issued before execution phase")
        }
        getIssued.set(true)
        return super.getOrElse(p0)
    }

    override fun getOrNull(): T? {
        if (providerSet.get() && !inExecutionMode.get()) {
            throw RuntimeException("A [Provider] was set of $id property, therefore get() cannot be issued before execution phase")
        }
        getIssued.set(true)
        return super.getOrNull()
    }

    override fun set(p0: Provider<out T>) {
        if (getIssued.get()) {
            throw RuntimeException("Cannot override $id Property as a get() has already been called")
        }
        providerSet.set(true)
        super.set(p0)
    }
}