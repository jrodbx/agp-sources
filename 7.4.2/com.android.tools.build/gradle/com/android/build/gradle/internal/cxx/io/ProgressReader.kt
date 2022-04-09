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

package com.android.build.gradle.internal.cxx.io

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.Reader
import java.lang.Long.min

// The default number of milliseconds between progress updates.
const val DEFAULT_MINIMUM_PROGRESS_INTERVAL_MILLIS = 1000L

/**
 * A callback for progress to be reported.
 * [filename] is the name of the file or other context that is being read.
 * [totalBytes] is the size of the file or other context that is being read.
 * [bytesRead] is the total number of bytes that have been read so far.
 */
typealias ProgressCallback =
            (filename: String,
             totalBytes: Long,
             bytesRead: Long)->Unit

/**
 * Create a [ProgressReader] from a [File].
 */
fun File.progressReader(
    progressIntervalMillis : Long = DEFAULT_MINIMUM_PROGRESS_INTERVAL_MILLIS
) = ProgressReader(
    BufferedReader(FileReader(this), min(length(), 1024L * 1024L).toInt()),
    path,
    length(),
    progressIntervalMillis
)

/**
 * Wraps a [Reader] with functionality that tracks the progress of reading.
 *
 * The user of this [ProgressReader] is responsible for calling [postProgress] at intervals that
 * make sense for the situation (for example, once per Ninja statement).
 *
 * The [progressIntervalMillis] field is the minimum number of milliseconds between progress posts.
 * If [progressIntervalMillis] is zero then progress is posted each time [postProgress] is called.
 * If [progressIntervalMillis] is MAX_VALUE then [postProgress] is not called.
 */
class ProgressReader(
    private val reader : Reader,
    private val filename : String,
    private val totalBytes : Long,
    private val progressIntervalMillis : Long
) : Reader() {
    private var lastProgressPostedMillis = System.currentTimeMillis()
    private var bytesRead : Long = 0L

    /**
     * Override of [read] that counts bytes read and lines read.
     */
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val bytes = reader.read(cbuf, off, len)
        bytesRead += bytes
        return bytes
    }

    /**
     * Close the underlying [Reader]
     */
    override fun close() = reader.close()

    /**
     * Called with [ProgressCallback] to report progress if sufficient time has elapsed.
     */
    fun postProgress(progress: ProgressCallback) {
        if (progressIntervalMillis == Long.MAX_VALUE) return
        if (progressIntervalMillis > 0) {
            val current = System.currentTimeMillis()
            val elapsed = current - lastProgressPostedMillis
            if (elapsed < progressIntervalMillis) return
            lastProgressPostedMillis = current
        }
        progress(
            filename,
            totalBytes,
            bytesRead
        )
    }
}
