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

import com.android.annotations.concurrency.Slow
import com.android.io.CancellableFileIo
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

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
    protected var lastReadSourceType: DataSourceType = DataSourceType.UNKNOWN_SOURCE

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

    /** Reads the given data relative to the base URL. */
    @Slow
    protected open fun findData(relative: String): InputStream? {
        if (cacheDir != null) {
            var lastModified = 0L
            synchronized(cacheDir) {
                val file = cacheDir.resolve(relative.ifEmpty { cacheKey })
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
