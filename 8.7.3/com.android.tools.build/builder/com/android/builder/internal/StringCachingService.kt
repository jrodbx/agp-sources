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

package com.android.builder.internal

/**
 * A service that can cache strings so that there are no duplicate.
 *
 * Should be used in place of [String.intern] which has performance issues
 */
interface StringCachingService {

    /**
     * Returns a cached version of the string.
     */
    fun cacheString(string: String): String
}

fun StringCachingService?.cacheString(string: String): String = this?.cacheString(string) ?: string
