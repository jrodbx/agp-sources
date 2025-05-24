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
import javax.inject.Inject

/**
 * An implementation of a list for use in AGP DSL that can be locked.
 *
 * This is intentionally not serializable, as model classes should take copies
 * e.g. by using [com.google.common.collect.ImmutableList.copyOf]
 *
 * The iterator is handled by [java.util.AbstractList] (unlike the other AbstractCollection types),
 * which calls the [get], [set], [add] and [removeAt] methods, which check if the list is locked.
 */
class LockableList<T> @Inject constructor(
    private val name: String
) :  java.util.AbstractList<T>(), MutableList<T>, Lockable {

    private val delegate: MutableList<T> = mutableListOf()

    private var locked = false

    override fun lock() {
        locked = true;
    }

    private inline fun <R>check(action: () -> R): R {
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

    override val size: Int get() = delegate.size

    override fun get(index: Int): T = delegate[index]

    override fun set(index: Int, element: T): T = check {
        delegate.set(index, element)
    }

    override fun add(index: Int, element: T) = check {
        delegate.add(index, element)
    }

    override fun removeAt(index: Int): T = check {
        delegate.removeAt(index)
    }
}
