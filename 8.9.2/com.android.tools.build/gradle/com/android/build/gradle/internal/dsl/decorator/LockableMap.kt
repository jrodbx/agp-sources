/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.AgpDslLockedException
import com.android.build.gradle.internal.dsl.Lockable
import java.util.AbstractMap
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function
import javax.inject.Inject

class LockableMap<K, V> @Inject constructor(
    private val name: String
) : AbstractMap<K, V>(), MutableMap<K, V>, Lockable {

    private val delegate: MutableMap<K, V> = mutableMapOf()

    private var lockableEntrySet: LockableSet<MutableMap.MutableEntry<K, V>>? = null

    private var locked = false

    override fun lock() {
        locked = true
        lockableEntrySet?.lock()
    }

    private inline fun <R> check(action: () -> R): R {
        if (locked) {
            throw AgpDslLockedException(
                "It is too late to modify ${name.removePrefix("_")}\n" +
                        "It has already been read to configure this project.\n" +
                        "Consider either moving this call to be during evaluation,\n" +
                        "or using the variant API."
            )
        }
        return action.invoke()
    }

    // java map methods that mutate the state

    override fun put(key: K, value: V): V? = check {
        delegate.put(key, value)
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            if (lockableEntrySet == null) {
                lockableEntrySet = LockableSet("${name}_entrySet", delegate.entries).also {
                    if (locked) {
                        it.lock()
                    }
                }
            }
            return lockableEntrySet!!
        }

    // kotlin map

    override fun getOrDefault(key: K, defaultValue: V): V = delegate.getOrDefault(key, defaultValue)

    override fun forEach(action: BiConsumer<in K, in V>) = delegate.forEach(action)

    // kotlin mutable map

    override fun remove(key: K, value: V): Boolean = check {
        delegate.remove(key, value)
    }

    override fun replaceAll(function: BiFunction<in K, in V, out V>) = check {
        delegate.replaceAll(function)
    }

    override fun putIfAbsent(key: K, value: V): V? = check {
        delegate.putIfAbsent(key, value)
    }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean = check {
        delegate.replace(key, oldValue, newValue)
    }

    override fun replace(key: K, value: V): V? = check {
        delegate.replace(key, value)
    }

    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V = check {
        delegate.computeIfAbsent(key, mappingFunction)
    }

    override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V & Any, out V?>): V? =
        check {
            delegate.computeIfPresent(key, remappingFunction)
        }

    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? = check {
        delegate.compute(key, remappingFunction)
    }

    override fun merge(key: K, value: V & Any, remappingFunction: BiFunction<in V & Any, in V & Any, out V?>): V? =
        check {
            delegate.merge(key, value, remappingFunction)
        }
}
