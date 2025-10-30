/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceNamespace.TOOLS
import java.util.AbstractMap

/**
 * A [Map] keyed by [ResourceNamespace], only supports keys declared in non-namespaced projects:
 * - [ResourceNamespace.RES_AUTO]
 * - [ResourceNamespace.TOOLS].
 *
 * Using other keys will result in [IllegalArgumentException].
 *
 * For now there is no support for [ResourceNamespace.ANDROID], since these resources don't change
 * over time and are usually stored separately (see [com.android.ide.common.util.DisjointUnionMap] for
 * a way to combine the two).
 */
class KnownNamespacesMap<V> : MutableMap<ResourceNamespace, V> {

    private var resAutoValue: V? = null
    private var toolsValue: V? = null

    companion object {
        /**
         * Checks if all given namespaces are valid keys for a [KnownNamespacesMap]. See the class
         * documentation for details.
         *
         * @see KnownNamespacesMap
         */
        @JvmStatic
        fun canContainAll(keys: Collection<ResourceNamespace>): Boolean {
            return keys.size <= 2 && keys.all { key ->
                when (key) {
                    RES_AUTO, TOOLS -> true
                    else -> false
                }
            }
        }
    }

    override val size: Int
        get() {
            var result = 0
            resAutoValue?.run { result++ }
            toolsValue?.run { result++ }
            return result
        }

    override val entries: MutableSet<MutableMap.MutableEntry<ResourceNamespace, V>>
        get() {
            val result = mutableSetOf<MutableMap.MutableEntry<ResourceNamespace, V>>()
            resAutoValue?.run { result.add(AbstractMap.SimpleEntry(RES_AUTO, resAutoValue)) }
            toolsValue?.run { result.add(AbstractMap.SimpleEntry(TOOLS, toolsValue)) }
            return result
        }

    override val keys: MutableSet<ResourceNamespace>
        get() {
            val result = mutableSetOf<ResourceNamespace>()
            resAutoValue?.run { result.add(RES_AUTO) }
            toolsValue?.run { result.add(TOOLS) }
            return result
        }

    override val values: MutableCollection<V>
        get() {
            val result = mutableSetOf<V>()
            resAutoValue?.let(result::add)
            toolsValue?.let(result::add)
            return result
        }

    override fun containsKey(key: ResourceNamespace): Boolean = when (key) {
        RES_AUTO -> resAutoValue != null
        TOOLS -> toolsValue != null
        else -> false
    }

    override fun get(key: ResourceNamespace): V? = when (key) {
        RES_AUTO -> resAutoValue
        TOOLS -> toolsValue
        else -> null
    }

    override fun put(key: ResourceNamespace, value: V): V? = when (key) {
        RES_AUTO -> resAutoValue.also { resAutoValue = value }
        TOOLS -> toolsValue.also { toolsValue = value }
        else -> throw IllegalArgumentException("${KnownNamespacesMap::class.qualifiedName}: invalid key $key.")
    }

    override fun remove(key: ResourceNamespace): V? = when (key) {
        RES_AUTO -> resAutoValue.also { resAutoValue = null }
        TOOLS -> toolsValue.also { toolsValue = null }
        else -> null
    }

    override fun putAll(from: Map<out ResourceNamespace, V>) {
        from.forEach { k, v -> put(k, v) }
    }

    override fun clear() {
        resAutoValue = null
        toolsValue = null
    }

    override fun isEmpty(): Boolean = size == 0

    override fun containsValue(value: V): Boolean = entries.any { it.value == value }
}
