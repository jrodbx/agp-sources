/*
 * Copyright (C) 2024 The Android Open Source Project
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
import java.util.zip.ZipInputStream
import kotlin.streams.toList

/**
 * Returns `true` if the given file's extension is `.jar`, ignoring case. It may or may not exist.
 */
val isJarFile: (File) -> Boolean = { it.path.endsWith(SdkConstants.DOT_JAR, ignoreCase = true) }

/**
 * Returns a sorted list of files in the given directory whose relative paths satisfy the given
 * filter.
 */
fun getSortedFilesInDir(
    dir: Path,
    filter: (relativePath: String) -> Boolean = { true }
): List<Path> {
    return Files.walk(dir).use { files ->
        files
            .filter { file -> filter(dir.relativize(file).toString()) }
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
 * Returns a sorted list of the relative paths of the entries in the given jar where the relative
 * paths satisfy the given filter.
 */
fun getSortedRelativePathsInJar(
    jar: File,
    filter: (relativePath: String) -> Boolean = { true }
): List<String> {
    // Zip buffered inputstream is more memory efficient than using ZipFile.entries.
    ZipInputStream(jar.inputStream().buffered()).use { stream ->
        val relativePaths = mutableListOf<String>()
        while (true) {
            val entry = stream.nextEntry ?: break
            if (filter(entry.name)) {
                relativePaths.add(entry.name)
            }
        }
        return relativePaths.sorted()
    }
}

/**
 * Returns a sorted map of the entries in the given jar whose relative paths satisfy the given
 * filter. Each entry in the map maps the relative path of a jar entry to its byte contents.
 *
 * The given jar must not have duplicate entries.
 */
fun getSortedRelativePathsInJarWithContents(
    jar: File,
    filter: (relativePath: String) -> Boolean = { true }
): SortedMap<String, ByteArray> {
    return ZipFile(jar).use { zipFile ->
        zipFile.entries()
            .toList()
            .filter { entry -> filter(entry.name) }
            .map { entry ->
                entry.name to zipFile.getInputStream(entry).buffered().use { stream ->
                    stream.readBytes()
                }
            }.toMap()
            .toSortedMap()
    }
}
