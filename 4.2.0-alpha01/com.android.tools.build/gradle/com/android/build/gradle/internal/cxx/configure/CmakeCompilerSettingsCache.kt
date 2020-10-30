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

package com.android.build.gradle.internal.cxx.configure

import java.io.File
import java.io.IOException

/**
 * Key-value hash on disk for CMake compiler settings.
 * The key is the set of CMake arguments that are relevant to compiler settings.
 * The value is the resulting set of compiler settings.
 *
 * Hash collision policy is to allow only one cache entry per-hash. If there is a collision then
 * the most recently written value is the winner. For this reason the caller should not assume
 * a key/value will remain cached over time.
 *
 * No file locking is needed. Instead, a checksum is used to ensure that the value is consistent.
 */
class CmakeCompilerSettingsCache(
    cacheRootFolder: File,
    private val hashAlgorithm: (Any) -> String =
        { key -> hashCodeRadix36Hash(key) }) {
    private val cacheFolder = File(cacheRootFolder, CXX_CMAKE_COMPILER_SETTINGS_CACHE_SUBFOLDER)

    /**
     * Try to get the value for the given key.
     *
     * Returns null if not found
     */
    fun tryGetValue(key: CmakeCompilerCacheKey): String? {
        try {
            val keyJson = key.toJsonString()
            val keyHash = hashAlgorithm(keyJson)
            val checksumFile = checksumFile(keyHash)
            val keyFile = keyFile(keyHash)
            val valueFile = valueFile(keyHash)
            if (!checksumFile.isFile || !keyFile.isFile || !valueFile.isFile) {
                return null
            }
            val priorKeyJson = keyFile.readText()
            if (priorKeyJson != keyJson) {
                // This could be a hash collision or it could be that the writer side has not
                // flush to disk fully. We could add a key checksum to distinguish the two
                // cases but there doesn't seem to be a functional need for this. In practical
                // non-synthetic cases this is most likely a hash collision.
                return null
            }
            val value = valueFile.readText()
            val checksum = hashCodeRadix36Hash(value)
            val priorChecksum = checksumFile.readText()

            if (checksum != priorChecksum) {
                // Not a hash collision. Instead, the writer side has not flushed to disk fully.
                return null
            }
            return value
        } catch(e : IOException) {
            // In this case, the user deleted the cache folder manually
            return null
        }
    }

    /**
     * Save the given value under the given key. Previous values will be overwritten.
     */
    fun saveKeyValue(key: CmakeCompilerCacheKey, value: String) {
        try {
            val keyJson = key.toJsonString()
            val keyHash = hashAlgorithm(keyJson)
            val checksumFile = checksumFile(keyHash)
            val keyFile = keyFile(keyHash)
            val valueFile = valueFile(keyHash)
            cacheFolder.mkdirs()
            val valueChecksum = hashCodeRadix36Hash(value)
            checksumFile.writeText(valueChecksum)
            keyFile.writeText(keyJson)
            valueFile.writeText(value)
        } catch(e: IOException) {
            // User deleted the cache folder manually
            return
        }
    }

    /**
     * The name of the file that holds a key for a particular hash.
     */
    private fun keyFile(hash: String): File {
        return File(cacheFolder, "${hash}_key.json")
    }

    /**
     * The name of the file that holds the value for a particular key.
     */
    private fun valueFile(hash: String): File {
        return File(cacheFolder, "${hash}_value.cmake")
    }

    /**
     * The name of the checksum file to use for a particular hash.
     */
    private fun checksumFile(hash: String): File {
        return File(cacheFolder, "${hash}_checksum.txt")
    }

    companion object {
        /**
         * Default hashing algorithm.
         */
        private fun hashCodeRadix36Hash(key: Any): String {
            return key.hashCode().toString(36)
        }

    }
}