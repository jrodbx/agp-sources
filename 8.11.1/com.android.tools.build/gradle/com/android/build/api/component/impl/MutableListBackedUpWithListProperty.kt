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

/**
 * Minimal implementation of [MutableList] based on a [ListProperty]
 *
 * Because some methods like [MutableList.get] will require reading from the [ListProperty],
 * instantiation of this class should probably be guarded by
 * [com.android.build.gradle.options.BooleanOption.ENABLE_LEGACY_API]
 */
class MutableListBackedUpWithListProperty<E>(
    private val propertyList: ListProperty<E>,
    private val propertyName: String,
): java.util.AbstractList<E>(), MutableList<E>{

    private val logger = LoggerWrapper.getLogger(MutableListBackedUpWithListProperty::class.java)

    override fun get(index: Int): E  = propertyList.get()[index]

    override val size: Int
        get() = _get().size

    override fun add(element: E): Boolean {
        propertyList.add(element)
        return true
    }

    override fun add(index: Int, element: E) {
        propertyList.add(element)
    }

    override fun clear() {
        propertyList.empty()
    }

    override fun listIterator(): MutableListIterator<E> {
        val mutableIterator = _get().listIterator()
        return object: MutableListIterator<E> by mutableIterator {
            override fun add(element: E) {
                throw UnsupportedOperationException("You cannot add elements to the $propertyName collection")
            }

            override fun remove() {
                throw UnsupportedOperationException("You cannot remove elements from the $propertyName " +
                                                            "collection, use clear() and add back elements you want to retain")
            }
        }
    }

    override fun removeAt(index: Int): E {
        throw NotImplementedError("Cannot selectively remove elements from the $propertyName collection, first clear() the list" +
                " and add back the elements you want to retain")
    }

    override fun toString(): String {
        return _get().toString()
    }

    private fun _get(): MutableList<E> {
        return propertyList.get()
    }
}
