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
@file:JvmName("GuavaUtils")

package com.android.ide.common.util

import com.google.common.collect.ImmutableSetMultimap

/** Creates a multimap for the given value pairs. */
fun <K, V> multimapOf(vararg pairs: Pair<K, V>): ImmutableSetMultimap<K, V> =
        ImmutableSetMultimap.builder<K, V>()
            .apply { pairs.forEach { (k, v) -> put(k, v) } }
            .build()

/**
 * Creates a multimap for the given value.
 *
 * The first value is the key, and all subsequent values are values associated with the key.
 */
fun <K, V> multimapWithSingleKeyOf(key: K, vararg values: V): ImmutableSetMultimap<K, V> =
        ImmutableSetMultimap.builder<K, V>()
                .apply { putAll(key, values.asIterable()) }
                .build()
