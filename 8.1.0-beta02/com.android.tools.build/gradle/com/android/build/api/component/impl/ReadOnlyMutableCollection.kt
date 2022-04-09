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

open class ReadOnlyMutableCollection<E>(
    private val mutableCollection: MutableCollection<E>,
    private val addErrorMessage: String,
): MutableCollection<E> by mutableCollection {
    override fun iterator(): MutableIterator<E> {
        val iterator = mutableCollection.iterator()
        return object: MutableIterator<E> by iterator {
            override fun remove() {
                throw UnsupportedOperationException("You cannot remove elements from this " +
                                                            "collection, use clear() and add back elements you want to retain")
            }
        }
    }

    override fun add(element: E): Boolean {
        throw UnsupportedOperationException(addErrorMessage)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        throw UnsupportedOperationException(addErrorMessage)
    }

    override fun remove(element: E): Boolean {
        throw UnsupportedOperationException(addErrorMessage)
    }
}
