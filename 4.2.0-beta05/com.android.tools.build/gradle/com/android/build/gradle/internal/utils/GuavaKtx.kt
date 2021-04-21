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

package com.android.build.gradle.internal.utils

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet

/**
 * Kotlin Extensions for Guava types.
 */

fun <T> Iterable<T>.toImmutableList(): ImmutableList<T> {
    return ImmutableList.copyOf(this)
}

fun <T> Sequence<T>.toImmutableList(): ImmutableList<T> {
    return ImmutableList.copyOf(this.iterator())
}

fun <T> Iterable<T>.toImmutableSet(): ImmutableSet<T> {
    return ImmutableSet.copyOf(this)
}

fun <K, V> Map<K, V>.toImmutableMap(): ImmutableMap<K, V> {
    return ImmutableMap.copyOf(this)
}

/**
 * Build an immutable map with a custom transform on the value instances
 */
inline fun <K, V1, V2> Map<K, V1>.toImmutableMap(action: (V1) -> V2)
        : ImmutableMap<K, V2> {
    val builder: ImmutableMap.Builder<K, V2> = ImmutableMap.builder()

    for (entry in entries) {
        val v2 = action(entry.value)
        builder.put(entry.key, v2)
    }

    return builder.build()
}

inline fun <K, V> immutableMapBuilder(block: ImmutableMap.Builder<K,V>.() -> Unit)
        : ImmutableMap<K, V> {
    val builder: ImmutableMap.Builder<K, V> = ImmutableMap.builder()

    block(builder)

    return builder.build()
}
