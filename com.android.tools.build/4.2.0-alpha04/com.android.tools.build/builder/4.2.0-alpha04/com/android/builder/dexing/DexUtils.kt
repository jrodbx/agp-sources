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

package com.android.builder.dexing

import com.android.SdkConstants
import com.android.utils.PathUtils.toSystemIndependentPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.SortedMap
import java.util.zip.ZipFile
import kotlin.streams.toList

/**
 * Returns `true` if the given file's extension is `.jar`, ignoring case. It may or may not exist.
 */
val isJarFile: (File) -> Boolean = { it.extension.equals(SdkConstants.EXT_JAR, ignoreCase = true) }

/** Returns a sorted list of dex files in the given directory. */
fun getSortedDexFilesInDir(dir: Path): List<Path> {
    return Files.walk(dir).use { files ->
        files
            .filter { it.toString().endsWith(SdkConstants.DOT_DEX, ignoreCase = true) }
            .toList()
            .sortedWith(
                Comparator { left, right ->
                    // Normalize the paths to ensure consistent order across file systems
                    // (see commit e177f16268680ab2de45bbf6dddd3fe02d89436b).
                    val systemIndependentLeft = toSystemIndependentPath(left)
                    val systemIndependentRight = toSystemIndependentPath(right)
                    systemIndependentLeft.compareTo(systemIndependentRight)
                }
            )
    }

}

/**
 * Returns a sorted map of dex entries in the given jar. Each entry in the map maps the relative
 * path of a dex entry to its byte contents.
 *
 * The given jar must not have duplicate entries.
 */
fun getSortedDexEntriesInJar(jar: Path): SortedMap<String, ByteArray> {
    return ZipFile(jar.toFile()).use { zipFile ->
        zipFile.entries()
            .toList()
            .filter { entry -> entry.name.endsWith(SdkConstants.DOT_DEX, ignoreCase = true) }
            .map {
                it.name to zipFile.getInputStream(it).buffered().use { stream ->
                    stream.readBytes()
                }
            }.toMap()
            .toSortedMap()
    }
}

