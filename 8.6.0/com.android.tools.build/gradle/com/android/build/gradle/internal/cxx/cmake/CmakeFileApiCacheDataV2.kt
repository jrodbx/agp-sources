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

package com.android.build.gradle.internal.cxx.cmake

import com.android.build.gradle.internal.cxx.configure.CmakeProperty

/**
 * Schema of cache-v2-{hash}.json file
 *
 *  "entries" : [{
 *    "name" : "ANDROID_ABI",
 *    "properties" : [{
 *      "name" : "HELPSTRING",
 *      "value" : "No help, variable specified on the command line."
 *    }],
 *    "type" : "UNINITIALIZED",
 *    "value" : "x86_64"}],
 *  "kind" : "cache",
 *  "version" : { "major" : 2, "minor" : 0 }
 */
data class CmakeFileApiCacheDataV2(val entries : List<CacheEntryV2>) {
    /**
     * Get a CMake property value from the cache entries.
     * Return null if there is no property with that name
     */
    fun getCacheString(property : CmakeProperty) = entries
            .filter { it.name == property.name }
            .map { it.value }
            .singleOrNull()
}

/**
 *    "name" : "ANDROID_ABI",
 *    "value" : "x86_64",
 */
data class CacheEntryV2(
        val name : String,
        val value : String
)
