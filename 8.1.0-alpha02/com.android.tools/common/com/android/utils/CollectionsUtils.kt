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
package com.android.utils

/**
 * Returns a new map with entries having the keys of this map and the values obtained by applying
 * the [transform] function to each entry in this [Map], excluding entries with null values.
 *
 * The returned map preserves the entry iteration order of the original map.
 */
@Suppress("UNCHECKED_CAST")
inline fun <K, V, R> Map<out K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> =
    mapValues(transform).filterValues { it != null } as Map<K, R>
