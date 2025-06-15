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

package com.android.build.gradle.internal.dependency

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer

/** Contains attributes of different types. */
class AndroidAttributes @JvmOverloads constructor(
    val stringAttributes: Map<Attribute<String>, String> = emptyMap(),
    val namedAttributes: Map<Attribute<out Named>, Named> = emptyMap(),
) {

    constructor(stringAttribute: Pair<Attribute<String>, String>) : this(mapOf(stringAttribute))

    operator fun plus(other: AndroidAttributes?): AndroidAttributes {
        return if (other == null) {
            this
        } else {
            stringAttributes.keys.intersect(other.stringAttributes.keys).let {
                check(it.isEmpty()) { "Can't add 2 AndroidAttributes instances because they share the same attributes: $it" }
            }
            namedAttributes.keys.intersect(other.namedAttributes.keys).let {
                check(it.isEmpty()) { "Can't add 2 AndroidAttributes instances because they share the same attributes: $it" }
            }
            AndroidAttributes(
                stringAttributes + other.stringAttributes,
                namedAttributes + other.namedAttributes
            )
        }
    }

    fun addAttributesToContainer(container: AttributeContainer) {
        for ((key, value) in stringAttributes) {
            container.attribute(key, value)
        }
        namedAttributes.forEach { (attribute, value) ->
            addAttributeToContainer(container, attribute, value)
        }
    }

    private fun<T: Named> addAttributeToContainer(
        container: AttributeContainer,
        attribute: Attribute<T>,
        value: Named
    ) {
        @Suppress("UNCHECKED_CAST")
        container.attribute(
            attribute,
            value as T
        )
    }

    /** Returns a string listing all the attributes. */
    fun toAttributeMapString(): String {
        val stringAttrs = stringAttributes.entries.sortedBy { it.key.name }.fold("") { it, entry ->
            "$it-A${entry.key.name}=${entry.value}"
        }
        val namedAttrs = namedAttributes.entries.sortedBy { it.key.name }.fold("") { it, entry ->
            "$it-A${entry.key.name}=${entry.value}"
        }
        return stringAttrs + namedAttrs
    }

    override fun toString() = toAttributeMapString()
}
