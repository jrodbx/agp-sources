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

package com.android.builder.files

import com.google.common.hash.Hashing
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

/**
 * This repository tracks zip content using file snapshots to support incremental builds, eliminating
 * the need to store the full zip payload.
 *
 * @param directory the directory for storing snapshots
 * @param keyFunction snapshot filename generator for efficient storage and retrieval.
 *      By not passing your implementation you will rely on the default key generation mechanism,
 *      that involves hashing of the file's absolute path.
 */
class ZipSnapshotRepository @JvmOverloads constructor(
    private val directory: File,
    private val keyFunction: (File) -> String = this::defaultKey
) {

    /**
     * Attempts to retrieve a snapshot of a zip archive.
     *
     * @param file a zip archive used to for snapshot creation
     * @return a snapshot of a [file], or null if the file has not been previously seen.
     */
    fun getLastSnapshotOfZip(file: File): ZipSnapshot? {
        return File(directory, keyFunction(file)).takeIf { it.isFile }?.let {
            ZipSnapshot(ZipEntryList.deserializeFile(it)!!, file.absolutePath)
        }
    }

    /**
     * Creates a zip snapshot of a [file] zip archive. Overwriting an old snapshot of the file and
     * creating the first one are indistinguishable.
     *
     * @param file a zip archive used to for snapshot creation
     */
    fun takeSnapshotOfZip(file: File) {
        val zipEntryList = ZipEntryList.fromZip(file)
        File(directory, keyFunction(file)).apply {
            FileOutputStream(this).use { fos ->
                fos.writer().use {
                    writeBytes(zipEntryList.toByteArray())
                }
            }
        }
    }

    /**
     * Removes a zip snapshot of a [file], if it had been done before.
     *
     * @param file a zip archive of which a snapshot had previously been taken.
     * @throws IllegalArgumentException when the snapshot of a [file] is not present, which could
     *      mean it had never  been taken or had already been deleted.
     */
    fun removeSnapshotOfZip(file: File) {
        if (getLastSnapshotOfZip(file) == null)
            throw IllegalArgumentException("Trying to remove a snapshot that doesn't exist [$file]")
        File(directory, keyFunction(file)).takeIf { it.isFile }?.delete()
    }

    /**
     * Removes all the snapshots stored in this repository.
     */
    fun clear() {
        directory.listFiles()?.onEach {
            it.delete()
        }
    }

    companion object {

        /**
         * Calculates a key for fast lookups to retrieve or delete a snapshot for specific [file].
         *
         * Hashing algorithm implementation needs to be stable across processes, but doesn't need
         * to involve a long key - hence the use of [Hashing.murmur3_128].
         */
        private fun defaultKey(file: File): String {
            val sha1Sum = Hashing.murmur3_128().hashString(file.absolutePath, Charsets.UTF_8).asBytes();
            return String(Base64.getEncoder().encode(sha1Sum), Charsets.US_ASCII).replace("/", "_");
        }
    }
}

/**
 * Represents the state of a zip file at a certain point. The [originalPath] of the zip file and
 * its entries are included, which is enough for tracking changes of zip content.
 */
data class ZipSnapshot(val entryList: ZipEntryList, val originalPath: String)
