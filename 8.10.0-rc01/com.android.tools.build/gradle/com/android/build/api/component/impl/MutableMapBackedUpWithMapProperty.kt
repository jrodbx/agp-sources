/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.component.impl

import com.android.build.gradle.internal.LoggerWrapper
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

/**
 * Minimal implementation of [MutableMap] based on a [MapProperty]
 *
 * Because some methods like [MutableMap.entries] will require reading from the [MapProperty],
 * instantiation of this class should probably be guarded by
 * [com.android.build.gradle.options.BooleanOption.ENABLE_LEGACY_API]
 */
class MutableMapBackedUpWithMapProperty<K, V>(
    private val mapProperty: MapProperty<K, V>,
    private val propertyName: String,
): java.util.AbstractMap<K, V>(), MutableMap<K, V> {

    private val logger = LoggerWrapper.getLogger(MutableMapBackedUpWithMapProperty::class.java)

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = ReadOnlyMutableSet(_get().entries,
   "Cannot add elements using the $propertyName entries() returned Set," +
           " use the original collection")



    override val keys: MutableSet<K>
        get() = ReadOnlyMutableSet(_get().keys,
                   "Cannot add elements using the $propertyName keys() returned Set," +
                           " use the original collection")

    override val values: MutableCollection<V>
        get() = ReadOnlyMutableCollection(
            _get().values,
            "Cannot add elements using the $propertyName values() returned collection, " +
                    "use the original collection")

    override fun clear() {
        mapProperty.empty()
    }

    override fun remove(key: K): V? {
        throw NotImplementedError("Cannot remove elements from the $propertyName map, first do a clear() " +
                "and add back selectively the elements you want to keep")
    }

    override fun putAll(from: Map<out K, V>) {
        mapProperty.putAll(from)
    }

    override fun put(key: K, value: V): V? {
        mapProperty.put(key, value)
        return null
    }

    private fun _get(): MutableMap<K, V> {
        return mapProperty.get()
    }
}
