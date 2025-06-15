/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.sdklib.util

import java.io.IOException
import java.nio.file.Path

/**
 * A thread-safe cache of values indexed by a canonicalized Path. This can be used to ensure that
 * only a single instance is created for a given real path on the filesystem.
 */
class CacheByCanonicalPath<T> {
  private val cache = hashMapOf<Path?, T>()

  @Synchronized
  fun computeIfAbsent(key: Path?, creator: (Path?) -> T): T {
    // We want to minimize unnecessary calls to Path.toRealPath(), since it has to go to the
    // filesystem to canonicalize the path. First, just try the path as given.
    cache[key]?.let {
      return it
    }

    // Not found; look for it at the canonical path.
    val canonicalKey =
      try {
        key?.toRealPath() ?: key
      } catch (_: IOException) {
        // Unfortunately, we don't have a logger here. Just continue with the given key.
        key
      }

    val value = cache.getOrPut(canonicalKey) { creator(canonicalKey) }

    // If key was non-canonical, store the mapping for it as well.
    if (key != canonicalKey) {
      cache.put(key, value)
    }
    return value
  }

  /**
   * Removes the value pointed to by `key` from the cache. All other Paths that point to it are also
   * removed.
   */
  @Synchronized
  fun remove(key: Path?): Boolean {
    val removed = cache.remove(key)
    if (removed != null) {
      cache.entries.removeIf { (_, v) -> v == removed }
    }
    return removed != null
  }
}
