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
import com.google.common.base.Predicate
import com.google.common.base.Function
import java.io.File
import java.io.FileWriter

/**
 * Delegating implementation of [ApkCreator] that records all the operations requested on the
 * delegated instance and writes them out to a change list file.
 */
class CapturingChangesApkCreator(
    private val creationData: ApkCreatorFactory.CreationData,
    private val delegate: ApkCreator): ApkCreator {

    val changedItems = mutableListOf<ApkChangeList.ChangedItem>()
    val deletedItems = mutableListOf<ApkChangeList.ChangedItem>()

    override fun writeZip(
        zip: File?,
        transform: Function<String, String>?,
        isIgnored: Predicate<String>?
    ) {
        if (zip==null) return
        val zipFileLastModified = zip.lastModified()
        delegate.writeZip(zip, transform, isIgnored)
        FolderBasedApkCreator.proccessZipEntry(zip, isIgnored) { entry ->
            changedItems.add(
                ApkChangeList.ChangedItem(
                    entry.centralDirectoryHeader.name,
                    zipFileLastModified
                )
            )
        }
    }

    override fun writeFile(inputFile: File, entryPath: String) {
        delegate.writeFile(inputFile, entryPath)
        changedItems.add(ApkChangeList.ChangedItem(entryPath, inputFile.lastModified()))
    }

    override fun deleteFile(entryPath: String) {
        delegate.deleteFile(entryPath)
        deletedItems.add(ApkChangeList.ChangedItem(entryPath, 0))
    }

    override fun hasPendingChangesWithWait(): Boolean {
        return delegate.hasPendingChangesWithWait()
    }

    override fun close() {
        delegate.close()
        val outputFile = if (creationData.apkPath.isDirectory) {
            File(creationData.apkPath, ApkChangeList.CHANGE_LIST_FN)
        } else {
            File(creationData.apkPath.parentFile,
                ApkChangeList.changeListFileName(creationData.apkPath))
        }

        FileWriter(outputFile)
            .buffered()
            .use { ApkChangeList.write(this, it) }
    }
}
