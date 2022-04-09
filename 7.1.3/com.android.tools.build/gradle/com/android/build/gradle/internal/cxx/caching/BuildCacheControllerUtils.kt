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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_CACHE_DISABLED_ACCESS
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import java.io.InputStream
import java.io.OutputStream

/**
 * Functions in this file are wrappers over Gradle's [BuildCacheController]
 * class. They make the methods in that class Kotlin idiomatic while
 * still keeping the performance profile ('load' and 'store' are still
 * callbacks that are called only conditionally when caching is valid).
 *
 * Main changes:
 * - Nullable T is used instead of Optional
 * - Lambdas are used instead of store/load callbacks
 */

/**
 * Load an object file from cache structure. Returns null if there's a cache miss.
 */
fun <T> BuildCacheController.load(
    key : BuildCacheKey,
    load : (InputStream) -> T
) : T? {
    if (!isEnabled) {
        errorln(
            BUILD_CACHE_DISABLED_ACCESS,
            "BuildCacheController#load should not be called when build caching isn't enabled")
        return null
    }
    return load(object : BuildCacheLoadCommand<T> {
        override fun getKey() = key
        override fun load(input: InputStream): BuildCacheLoadCommand.Result<T> {
            val result = load(input)
            return object : BuildCacheLoadCommand.Result<T> {
                override fun getArtifactEntryCount() = 1L
                override fun getMetadata() = result
            }
        }
    }).orElse(null)
}

/**
 * Store an object file to cache.
 */
fun BuildCacheController.store(
    key : BuildCacheKey,
    store : (OutputStream) -> Unit) {
    if (!isEnabled) {
        errorln(
            BUILD_CACHE_DISABLED_ACCESS,
            "BuildCacheController#store should not be called when build caching isn't enabled")
        return
    }
    store(object : BuildCacheStoreCommand {
        override fun getKey() = key
        override fun store(output: OutputStream): BuildCacheStoreCommand.Result {
            store(output)
            return BuildCacheStoreCommand.Result { 1L }
        }
    })
}
