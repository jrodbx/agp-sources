/*
 * Copyright (C) 2016 The Android Open Source Project
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

@file:JvmName("ApkCreatorFactories")

package com.android.build.gradle.internal.packaging

import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater

/**
 * Time after which background compression threads should be discarded.
 */
private const val BACKGROUND_THREAD_DISCARD_TIME_MS: Long = 100

/**
 * Maximum number of compression threads.
 */
private const val MAXIMUM_COMPRESSION_THREADS = 2

/**
 * Creates an [ApkCreatorFactory] based on the definitions in the project. This is only to
 * be used with the incremental packager.
 *
 * @param debuggableBuild whether the [ApkCreatorFactory] will be used to create a
 * debuggable archive
 * @return the factory
 */
fun fromProjectProperties(
    debuggableBuild: Boolean
): ApkCreatorFactory {
    val options = ZFileOptions()
    options.noTimestamps = true
    options.coverEmptySpaceUsingExtraField = true

    val compressionExecutor = ThreadPoolExecutor(
        0, /* Number of always alive threads */
        MAXIMUM_COMPRESSION_THREADS,
        BACKGROUND_THREAD_DISCARD_TIME_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    )

    if (debuggableBuild) {
        options.compressor = DeflateExecutionCompressor(compressionExecutor, Deflater.BEST_SPEED)
    } else {
        options.compressor =
            DeflateExecutionCompressor(compressionExecutor, Deflater.DEFAULT_COMPRESSION)
        options.autoSortFiles = true
    }

    return ApkZFileCreatorFactory(options)
}
