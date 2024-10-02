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

import com.google.common.io.Closer
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile

/**
 * A bucket of [DexEntry]'s.
 *
 * It is lightweight and [Serializable] so that it can be passed to workers (see `DexMergingTask`).
 */
class DexEntryBucket(
    private val dexEntries: List<DexEntry>
) : Serializable {

    @Suppress("UnstableApiUsage")
    fun getDexEntriesWithContents(): List<DexArchiveEntry> {
        val dexEntryWithContents = mutableListOf<DexArchiveEntry>()

        Closer.create().use { closer ->
            val openedJars = mutableMapOf<File, ZipFile>()
            dexEntries.forEach { dexEntry ->
                val contents = if (dexEntry.dexDirOrJar.isDirectory) {
                    dexEntry.dexDirOrJar.resolve(dexEntry.relativePath).readBytes()
                } else {
                    val openedJar = openedJars.computeIfAbsent(dexEntry.dexDirOrJar) {
                        ZipFile(dexEntry.dexDirOrJar).also {
                            closer.register(it)
                        }
                    }
                    openedJar.getInputStream(openedJar.getEntry(dexEntry.relativePath)).buffered()
                        .use { stream ->
                            stream.readBytes()
                        }
                }

                dexEntryWithContents.add(
                    DexArchiveEntry(
                        contents,
                        dexEntry.relativePath,
                        DexArchives.fromInput(dexEntry.dexDirOrJar.toPath())
                    )
                )
            }
        }

        return dexEntryWithContents.toList()
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * A dex file and dex jar entry. It is identified by the directory or jar that contains it and its
 * relative path to the containing directory or jar.
 *
 * It is lightweight and [Serializable] so that it can be passed to workers (see `DexMergingTask`).
 */
class DexEntry(
    val dexDirOrJar: File,
    val relativePath: String
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}
