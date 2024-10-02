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
 * An implementation of a set for use in AGP DSL that can be locked.
 *
 * This set implementation preserves insertion order.
 *
 * This is intentionally not serializable, as model classes should take copies
 * e.g. [com.google.common.collect.ImmutableList.copyOf]
 */
class LockableSet<T> @Inject @JvmOverloads constructor(
    private val name: String,
    private val delegate: MutableSet<T> = mutableSetOf()
) :  java.util.AbstractSet<T>(), MutableSet<T>, Lockable {

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

    override fun add(element: T): Boolean = check {
         delegate.add(element)
    }

    override fun iterator(): MutableIterator<T> {
        return LockableIterator(delegate.iterator())
    }

    private inner class LockableIterator<T>(private val delegate: MutableIterator<T>): MutableIterator<T> {
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): T = delegate.next()
        override fun remove() = check {
            delegate.remove()
        }
    }

}
