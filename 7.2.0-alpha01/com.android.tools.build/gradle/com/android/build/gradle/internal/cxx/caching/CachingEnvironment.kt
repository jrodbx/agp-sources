/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.hashing.shortSha256Of
import com.android.build.gradle.internal.cxx.json.readJsonFile
import com.android.build.gradle.internal.cxx.json.writeJsonFile
import com.android.build.gradle.internal.cxx.logging.LoggingMessage
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.utils.FileUtils.join
import com.google.common.base.Charsets
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/**
 * This file exposes functions for caching where the information about where the cache is held
 * in a stack on thread-local storage.
 *
 * Example usage,
 *
 *      val result = CachingEnvironment(...).use {
 *          cache(key) {
 *              compute() // Only called on cache miss.
 *          }
 *       }
 *
 * The purpose is to separate the concerns of other classes and functions from the need to cache
 * results to disk.
 *
 * If 'cache' is called outside of a CachingEnvironment scope then nothing will be cached. This
 * is used as a safe fallback in cases where the project doesn't have C++ code but needs to call
 * cxx functions (particularly to locate strip executable).
 */

/**
 * Read value from cache or execute the action and save the value to cache.
 * Any log messages are stored in a file next the cached key and it value
 */
inline fun <TKey, reified TValue> cache(
    key: TKey,
    action: () -> TValue
): TValue {
    val baseName = TValue::class.java.simpleName
    val prior = ThreadCachingEnvironment.readInCurrentEnvironment(
        key,
        baseName,
        TValue::class.java
    )
    if (prior != null) return prior

    PassThroughDeduplicatingLoggingEnvironment().use { logger ->
        try {
            val computed = action()
            ThreadCachingEnvironment.writeInCurrentEnvironment(
                key,
                snakeCase(baseName),
                computed,
                logger.record,
                TValue::class.java
            )
            return computed
        } catch(e: Exception) {
            // When there's an exception we still need to write the log
            ThreadCachingEnvironment.writeFailureInCurrentEnvironment(
                key,
                snakeCase(baseName),
                e,
                logger.record,
                TValue::class.java
            )
            throw e
        }
    }
}

/**
 * Convert a string from PascalCase to SnakeCase.
 */
fun snakeCase(value : String) : String {
    val sb = StringBuilder()
    value.onEach { ch ->
        if (sb.isNotEmpty() && ch.isUpperCase()) sb.append('_')
        sb.append(ch.toLowerCase())
    }
    return "$sb"
}

/**
 * File names related to caching an item.
 */
data class CacheFileNames(val key: File, val value: File, val exception: File, val log: File)

/**
 * Define the caching contract.
 */
interface CachingEnvironmentDefinition : AutoCloseable {
    fun <TKey, TValue> read(
        key: TKey,
        baseName: String,
        valueType: Class<TValue>
    ): TValue?

    fun <TKey, TValue> write(
        key: TKey,
        baseName: String,
        value: TValue,
        log: List<LoggingMessage>,
        valueType: Class<TValue>
    )

    fun <TKey, TValue> writeFailure(
        key: TKey,
        baseName: String,
        reason: Exception,
        log: List<LoggingMessage>,
        valueType: Class<TValue>
    )
}

/**
 * Base class for all CachingEnvironments. Holds the thread-local cacher stack.
 */
abstract class ThreadCachingEnvironment : CachingEnvironmentDefinition {
    init {
        // Okay to suppress because push doesn't have knowledge of derived classes.
        @Suppress("LeakingThis")
        push(this)
    }

    override fun close() {
        pop()
    }

    companion object {
        /**
         * Singly-linked list where null is used as empty list.
         * The purpose is that Thread Local has zero allocations when there are no cachers so that
         * the class loader that creates the cacher won't leak.
         */
        private data class CacheStack(
            val cacher: CachingEnvironmentDefinition,
            val next: CacheStack?
        )

        private val stack: ThreadLocal<CacheStack?> =
            ThreadLocal.withInitial { null }

        /**
         * Push a new logging environment onto the stack of environments.
         */
        private fun push(cacher: CachingEnvironmentDefinition) =
            stack.set(
                CacheStack(
                    cacher,
                    stack.get()
                )
            )

        /**
         * Pop the top logging environment.
         */
        private fun pop() {
            val next = stack.get()?.next
            if (next != null) stack.set(next) else stack.remove()
        }

        fun <TKey, TValue> readInCurrentEnvironment(
            key: TKey, baseName: String, valueType: Class<TValue>
        ) = stack.get()?.cacher?.read(key, baseName, valueType)

        fun <TKey, TValue> writeInCurrentEnvironment(
            key: TKey,
            baseName: String,
            value: TValue,
            log: List<LoggingMessage>,
            valueType: Class<TValue>
        ) = stack.get()?.cacher?.write(key, baseName, value, log, valueType)

        fun <TKey, TValue> writeFailureInCurrentEnvironment(
            key: TKey,
            baseName: String,
            reason: Exception,
            log: List<LoggingMessage>,
            valueType: Class<TValue>
        ) = stack.get()?.cacher?.writeFailure(key, baseName, reason, log, valueType)
    }
}

/**
 * Scope for caching values to a particular folder.
 */
class CachingEnvironment(
    private val cacheFolder: File
) : ThreadCachingEnvironment() {

    /**
     * Return the filenames related to this cache entry.
     */
    private fun <TKey> filenames(key: TKey, baseName: String)
            : CacheFileNames {
        val keyBase = join(cacheFolder, "${baseName}_${shortSha256Of(key)}")
        return CacheFileNames(
            File("${keyBase}_key.json"),
            File("${keyBase}.json"),
            File("${keyBase}.log"),
            File("${keyBase}_exception.txt")
        )
    }

    /**
     * Read a value from the cache. Returns null if the cache doesn't hold that key.
     */
    override fun <TKey, TValue> read(
        key: TKey,
        baseName: String,
        valueType: Class<TValue>
    ): TValue? {
        val (keyFile, valueFile) = filenames(key, baseName)
        if (!keyFile.exists()) return null
        if (!valueFile.exists()) return null
        val keyRead = readJsonFile(keyFile, valueType)
        if (keyRead != key) return null
        return readJsonFile(valueFile, valueType)
    }

    /**
     *  Write a cache key, value, and log to their respective files.
     */
    override fun <TKey, TValue> write(
        key: TKey,
        baseName: String,
        value: TValue,
        log: List<LoggingMessage>,
        valueType: Class<TValue>
    ) {
        val (keyFile, valueFile, logFile, exceptionFile) = filenames(key, baseName)
        writeJsonFile(logFile, log)
        writeJsonFile(valueFile, value)
        writeJsonFile(keyFile, key)
        // Don't leave dangling exception file from a prior run
        Files.deleteIfExists(exceptionFile.toPath())
    }

    /**
     *  Write a cache key, reason, and log to their respective files.
     */
    override fun <TKey, TValue> writeFailure(
        key: TKey,
        baseName: String,
        reason: Exception,
        log: List<LoggingMessage>,
        valueType: Class<TValue>
    ) {
        val (keyFile, _, logFile, exceptionFile) = filenames(key, baseName)
        writeJsonFile(logFile, log)
        writeExceptionFile(exceptionFile, reason)
        writeJsonFile(keyFile, key)
    }

    /**
     * Write a value to a file as text.
     */
    private fun writeExceptionFile(file : File, reason : Exception) {
        val parent = file.parentFile
        if (!parent.exists()) parent.mkdirs()
        val byteStream = ByteArrayOutputStream()
        val printStream = PrintStream(byteStream)
        reason.printStackTrace(printStream)
        val stack = byteStream.toString("UTF8")
        file.writeText(
            "${reason.javaClass}: ${reason.message ?: ""} $stack",
            Charsets.UTF_8)
    }
}
