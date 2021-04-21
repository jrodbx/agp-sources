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

package com.android.build.gradle.internal.incremental

import com.android.tools.build.apkzlib.zfile.ApkCreator
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zip.StoredEntry
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.utils.FileUtils
import com.google.common.base.Function
import com.google.common.base.Predicate
import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.StandardCopyOption

/**
 * Implementation of [ApkCreator] that outputs to a folder.
 */
class FolderBasedApkCreator(private val creationData: ApkCreatorFactory.CreationData) : ApkCreator {

    init {
        val apkPath = creationData.apkPath
        if (!apkPath.exists()) {
            apkPath.mkdirs()
        }
        assert(apkPath.isDirectory)
    }

    companion object {
        fun proccessZipEntry(
            zip: File,
            isIgnored: Predicate<String>?,
            action: (StoredEntry) -> Unit
        ) {
            ZFile(zip, ZFileOptions(), true).use {
                it.entries().forEach { entry ->
                    if (isIgnored?.test(entry.centralDirectoryHeader.name) != true) {
                        action.invoke(entry)
                    }
                }
            }
        }
    }

    override fun writeZip(
        zip: File?,
        transform: Function<String, String>?,
        isIgnored: Predicate<String>?
    ) {
        if (zip == null) return
        proccessZipEntry(zip, isIgnored) { entry ->
            entry.open().use {
                val destinationFile =
                    File(creationData.apkPath, entry.centralDirectoryHeader.name)
                if (entry.centralDirectoryHeader.name.contains("../")) {
                    throw InvalidPathException(
                        entry.centralDirectoryHeader.name,
                        "Entry name contains invalid characters"
                    )
                }
                destinationFile.parentFile.mkdirs()
                Files.copy(
                    it,
                    destinationFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )

            }
        }
    }

    override fun writeFile(inputFile: File, entryPath: String) {
        val destinationFile = File(creationData.apkPath, entryPath)
        destinationFile.parentFile.mkdirs()
        FileUtils.copyFile(
            inputFile.toPath(),
            destinationFile.toPath()
        )
    }

    override fun deleteFile(entryPath: String) {
        Files.deleteIfExists(File(creationData.apkPath, entryPath).toPath())
    }

    override fun hasPendingChangesWithWait(): Boolean = false

    override fun close() {}
}
