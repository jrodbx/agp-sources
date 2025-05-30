/*
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 * A map that treats all the keys as resources names. This class takes care of the key
 * flattening done by AAPT where '.', '-' and ':' are all replaced with '_' and will be able to find
 * resources with those characters in the name no matter the format. Because of that, for keys
 * like 'my.key', 'my_key' and 'my-key' are considered equivalent.
 */
open class ResourceNameKeyedMap<T> {

    private val delegate: HashMap<KeyWrapper, T>

    constructor() {
        delegate = HashMap()
    }

    constructor(expectedSize: Int) {
        delegate = HashMap(expectedSize)
    }

    fun size(): Int = delegate.size

    fun containsKey(name: String): Boolean = delegate.containsKey(KeyWrapper(name))

    operator fun get(name: String): T? = delegate[KeyWrapper(name)]

    fun put(name: String, value: T): T? = delegate.put(KeyWrapper(name), value)

    operator fun set(name: String, value: T): Unit = delegate.set(KeyWrapper(name), value)

    fun remove(name: String): T? = delegate.remove(KeyWrapper(name))

    fun clear(): Unit = delegate.clear()

    fun values(): Collection<T> = delegate.values

    // A wrapper with custom equality/hashing to handle special characters in resource names.
    private class KeyWrapper(private val original: String) {

        override fun hashCode(): Int {
            // No allocations here; fold() is inlined.
            return original.fold(0) { hash, char -> hash * 31 + normalize(char).code }
        }

        override fun equals(other: Any?): Boolean {
            if (other !is KeyWrapper) return false

            val o1 = original
            val o2 = other.original
            if (o1.length != o2.length) return false

            // Avoiding allocations here.
            var i = o1.length - 1
            while (i >= 0) {
                if (normalize(o1[i]) != normalize(o2[i])) {
                    return false
                }
                --i
            }

            return true
        }

        private fun normalize(c: Char): Char {
            return if (isInvalidResourceFieldNameCharacter(c)) '_' else c
        }
    }
}
