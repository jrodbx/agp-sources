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
package com.android.ide.common.repository

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.absolutePathString

/**
 * Provides a basic network cache with local disk fallback for data read from a URL.
 */
abstract class NetworkCache constructor(
    private val baseUrl: String,

    /** Key used in cache directories to locate the network cache. */
    private val cacheKey: String,

    /** Location to search for cached repository content files. */
    val cacheDir: Path? = null,

    /**
     * Number of milliseconds to wait until timing out attempting to access the remote repository.
     */
    private val networkTimeoutMs: Int = 3000,

    /** Maximum allowed age of cached data; default is 7 days. */
    private val cacheExpiryHours: Int = TimeUnit.DAYS.toHours(7).toInt(),

    /**
     * If false, this repository won't make any network requests - Make sure you make another call,
     * maybe in a background thread, with enabled network if you want the cache to update properly.
     * */
    private val networkEnabled: Boolean = true
) {
    private val RESERVED_WINDOWS_FILE_NAMES = setOf("CON", "PRN", "AUX", "NUL", "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "COM¹", "COM²", "COM³", "LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9", "LPT¹", "LPT²", "LPT³")

    protected var lastReadSourceType: DataSourceType = DataSourceType.UNKNOWN_SOURCE

    protected val locks = HashMap<String, Lock>()
    // need this counter to clean locks map eventually
    protected var findDataParallelism = 0

    @Suppress("ArrayInDataClass")
    data class ReadUrlDataResult(
        val data: ByteArray?,
        /** true if the data (if any) should be treated as fresh. */
        val modifiedSince: Boolean
    )

    /**
     * Reads the given query URL in, with the given time out, and returns the bytes found.  The
     * second element of the pair is false if the remote source reported an unmodified resource
     * and true otherwise.
     */
    @Slow
    protected abstract fun readUrlData(url: String, timeout: Int, lastModified: Long): ReadUrlDataResult

    /** Provides the data from offline/local storage, if possible. */
    protected abstract fun readDefaultData(relative: String): InputStream?

    /** Reports an error found during I/O. */
    protected abstract fun error(throwable: Throwable, message: String?)

    protected inline fun <T> withLock(file: Path, action: () -> T): T {
        val lock = synchronized(this) {
            findDataParallelism = findDataParallelism.inc()
            locks.computeIfAbsent(file.absolutePathString()) { ReentrantLock() }
        }
        try {
            return lock.withLock {
                action.invoke()
            }
        } finally {
            synchronized(this) {
                findDataParallelism = findDataParallelism.dec()
                if (findDataParallelism == 0 && locks.size > 1) locks.clear()
            }
        }
    }

    private fun getRelativePath(relative: String, treatAsDirectory: Boolean = false) =
        buildString(relative.length + 8) {
            append(relative.split('/').joinToString("/") { encode(it) })
            if (treatAsDirectory && isNotEmpty() && !endsWith('/')) {
                // If treat as directory is true, the cache location is the same as if
                // the relative path had ended in a forward-slash.
                append('/')
            }
            if (isEmpty() || endsWith('/')) {
                // Cache directory entries as path/to/entry/(index), which cannot conflict
                // with another entry as the brackets would have been url encoded.
                append("(index)")
            }
        }

    /** Reads the given data relative to the base URL.
     *  Method is safe to execute in parallel.
     *
     * @param relative The relative
     * @param treatAsDirectory store the cache content in a directory, i.e. as if the URL ended in /
     *                         This is useful if a queried URL will be a prefix of another queried
     *                         URL, which can't be represented directly on the filesystem.
     */
    @Slow
    protected open fun findData(relative: String, treatAsDirectory: Boolean = false): InputStream? {
        if (cacheDir != null) {
            var lastModified = 0L

            val relativePath = getRelativePath(relative, treatAsDirectory)
            val file = cacheDir.resolve(relativePath)
            withLock(file){
                try {
                    lastModified = CancellableFileIo.getLastModifiedTime(file).toMillis()
                    val now = System.currentTimeMillis()
                    val expiryMs = TimeUnit.HOURS.toMillis(cacheExpiryHours.toLong())

                    if (!networkEnabled || lastModified == 0L || now - lastModified <= expiryMs) {
                        // We found a cached file.
                        // - Within the "cache expiry interval" we always assume we have something as fresh as the "Builtin index".
                        // - Outside the "cache expiry interval" if a network connection is allowed we always try to download the
                        // latest version (code bellow). If a network connection is not allowed, we assume the cache only exists
                        // because it was (or will be) updated on a background task where a network connection is allowed.
                        lastReadSourceType = if (lastModified != 0L) {
                            if (now - lastModified > expiryMs) {
                                DataSourceType.CACHE_FILE_EXPIRED_NO_NETWORK
                            } else {
                                DataSourceType.CACHE_FILE_RECENT
                            }
                        } else {
                            DataSourceType.CACHE_FILE_EXPIRED_UNKNOWN
                        }
                        return CancellableFileIo.newInputStream(file)
                    }
                } catch (ignore: NoSuchFileException) {
                }

                if (networkEnabled) {
                    try {
                        val time = FileTime.fromMillis(System.currentTimeMillis())
                        val result = readUrlData("$baseUrl$relative", networkTimeoutMs, lastModified)
                        if (result.modifiedSince) {
                            result.data?.let { data ->
                                lastReadSourceType = DataSourceType.CACHE_FILE_NEW
                                // createDirectories(it) is safe for concurrent usage. It handles folder creation
                                // race condition by accepting if a folder has been created by other thread
                                file.parent?.let { Files.createDirectories(it) }
                                Files.write(file, data)
                                return ByteArrayInputStream(data)
                            }
                        }
                        else {
                            lastReadSourceType = DataSourceType.CACHE_FILE_NOT_MODIIFED_SINCE
                            Files.setLastModifiedTime(file, time)
                            return CancellableFileIo.newInputStream(file)
                        }
                    }
                    catch (e: AssertionError) {
                        // Make sure that we propagate assertions
                        throw e
                    }
                    catch (e: Throwable) {
                        // timeouts etc.: fall through to use "expired" data, if available, otherwise use the Builtin index
                        try {
                            lastReadSourceType = DataSourceType.CACHE_FILE_EXPIRED_NETWORK_ERROR
                            return CancellableFileIo.newInputStream(file)
                        } catch (ignore: NoSuchFileException) {
                        }
                    }
                }
            }
        }

        // Fallback: Builtin index, used for offline scenarios etc
        val result = readDefaultData(relative)
        // Assign after reading, so it can be used inside readDefaultData for logging purposes
        lastReadSourceType = DataSourceType.DEFAULT_DATA
        return result
    }

    private fun encode(it: String): String {
        val encoded = URLEncoder.encode(it, Charsets.UTF_8.name())
        return if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS && RESERVED_WINDOWS_FILE_NAMES.contains(encoded.substringBefore(".").uppercase(Locale.US))) "($encoded)" else encoded
    }

    enum class DataSourceType {
        UNKNOWN_SOURCE,
        // Test data, not to be used in production
        TEST_DATA,
        // Cache file is too old but no network is available
        CACHE_FILE_EXPIRED_NO_NETWORK,
        // Cache file is too old, network is available but was not able to update
        CACHE_FILE_EXPIRED_NETWORK_ERROR,
        // Cache file exist but cannot tell if too old
        CACHE_FILE_EXPIRED_UNKNOWN,
        // Cache file exists and has not expired
        CACHE_FILE_RECENT,
        // Cache file was just downloaded
        CACHE_FILE_NEW,
        // Cache file expired but server reports resource is unchanged
        CACHE_FILE_NOT_MODIIFED_SINCE,
        // Default data was used
        DEFAULT_DATA,
    }
}
