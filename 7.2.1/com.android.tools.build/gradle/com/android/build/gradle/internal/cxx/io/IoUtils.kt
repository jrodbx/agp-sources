/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_CONTENT
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_DESTINATION_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_LENGTH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_SOURCE_AND_DESTINATION_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.NOT_SAME_SOURCE_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_CONTENT
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_CANONICAL_PATH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_ACCORDING_TO_LEXICAL_PATH
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Comparison.SAME_PATH_BY_FILE_OBJECT_IDENTITY
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Outcome.COPIED_FROM_SOURCE_TO_DESTINATION
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Outcome.CREATED_HARD_LINK_FROM_SOURCE_TO_DESTINATION
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Outcome.DELETED_DESTINATION_BECAUSE_SOURCE_DID_NOT_EXIST
import com.android.build.gradle.internal.cxx.io.SynchronizeFile.Outcome.SAME_FILE
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.logStructured
import com.android.build.gradle.internal.cxx.logging.warnln
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files.deleteIfExists
import java.nio.file.Files.isSameFile
import java.nio.file.Path

/**
 * Makes [destination] content the same as [source] either by hard linking
 * or by physically copying the file when hard linking isn't available.
 */
fun hardLinkOrCopyFile(
    source: File,
    destination: File
) {
    if (!source.isFile) error("Could not hard link or copy '$source' because it did not exist.")
    synchronizeFile(source, destination)
}

/**
 * Makes [destination] content the same as [source].
 * If [source] exists, it will attempt to hard link to [destination].
 * If hard linking doesn't exist on this platform then it does a copy instead.
 * If [source] doesn't exist, it will delete destination.
 */
fun synchronizeFile(
    source: File,
    destination: File,
    @VisibleForTesting createLink: (Path, Path) -> Unit = ::realCreateLink) {
    val comparison = compareFileContents(source, destination)
    val operationOutcome =
        if (comparison.areSameFileOrContent) {
            // This happens if source and destination paths are lexically the same
            // --or-- if one is a hard link to the other.
            // Either way, no work to do.
            SAME_FILE
        } else {
            // We know the destination is different now so delete it.
            if (destination.isFile) {
                deleteIfExists(destination.toPath())
            }

            if (!source.isFile) {
                // If source doesn't exist then leave destination as non-existing
                DELETED_DESTINATION_BECAUSE_SOURCE_DID_NOT_EXIST
            } else {

                if (!destination.parentFile.isDirectory) {
                    // Create the destination fold if it doesn't exist yet.
                    destination.parentFile.mkdirs()
                }

                try {
                    createLink(destination.toPath(), source.toPath().toRealPath())
                    infoln("hard linked $source to $destination")
                    CREATED_HARD_LINK_FROM_SOURCE_TO_DESTINATION
                } catch (e: IOException) {
                    // This can happen when hard linking from one drive to another on Windows
                    // In this case, copy the file instead.
                    warnln("Hard link from '$source' to '$destination' failed. Doing a slower copy instead.")
                    source.copyTo(destination, overwrite = true)
                    COPIED_FROM_SOURCE_TO_DESTINATION
                }

            }
        }
    logStructured { encoder ->
        val result = SynchronizeFile.newBuilder()
        result.workingDirectory = File(".").absolutePath
        result.sourceFile = source.path
        result.destinationFile = destination.path
        result.initialFileComparison = comparison
        result.outcome = operationOutcome
        result.build().encode(encoder)
    }
}

/**
 * Reference to [java.nio.file.Files.createLink] that can be passed to [synchronizeFile].
 */
private fun realCreateLink(destination : Path, source : Path) {
    java.nio.file.Files.createLink(destination, source)
}

/**
 * Returns true if the two files are the same file (including through hard links)
 * or if they have the same content.
 */
fun isSameFileOrContent(
    source: File,
    destination: File
) = compareFileContents(source, destination).areSameFileOrContent

/**
 * Returns true if the two files are the same file (including through hard links)
 * or if they have the same content.
 */
@VisibleForTesting
fun compareFileContents(
    source: File,
    destination: File,
    compareBufferSize: Int = 8192
) : SynchronizeFile.Comparison {
    when {
        source as Any === destination as Any -> return SAME_PATH_BY_FILE_OBJECT_IDENTITY
        source.path == destination.path -> return SAME_PATH_ACCORDING_TO_LEXICAL_PATH
        source.canonicalPath == destination.canonicalPath ->
        // Canonical path can throw an IO exception when the paths are not valid for the
        // underlying OS file provider. Here, we let the exception propagate rather than
        // claiming the files are the same or different.
        return SAME_PATH_ACCORDING_TO_CANONICAL_PATH
        !source.isFile && !destination.isFile -> return SAME_SOURCE_AND_DESTINATION_DID_NOT_EXIST
        !source.isFile -> return NOT_SAME_SOURCE_DID_NOT_EXIST
        !destination.isFile -> return NOT_SAME_DESTINATION_DID_NOT_EXIST
        isSameFile(source.toPath(), destination.toPath()) ->
        // This method should follow hard links and return true if those files lead to the
        // same content.
        return SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER
        source.length() != destination.length() -> return NOT_SAME_LENGTH
    }

    // Both files are the same size and length. Now check the actual content to see whether
    // there are byte-wise differences. Ideally, this path is rare because hard links are used
    // in most cases.
    val buffer1 = ByteArray(compareBufferSize)
    val buffer2 = ByteArray(compareBufferSize)
    FileInputStream(source).use { input1 ->
        FileInputStream(destination).use { input2 ->
            do {
                val size1 = input1.read(buffer1)
                if (size1 == -1) {
                    return@compareFileContents SAME_CONTENT
                }
                val size2 = input2.read(buffer2)
                assert(size1 == size2)
                if (!(buffer1 contentEquals buffer2)) {
                    return@compareFileContents NOT_SAME_CONTENT
                }
            } while(true)
        }
    }
}

private val SynchronizeFile.Comparison.areSameFileOrContent : Boolean
    get() = when(this) {
        NOT_SAME_SOURCE_DID_NOT_EXIST,
        NOT_SAME_DESTINATION_DID_NOT_EXIST,
        NOT_SAME_LENGTH,
        NOT_SAME_CONTENT -> false
        SAME_SOURCE_AND_DESTINATION_DID_NOT_EXIST,
        SAME_PATH_BY_FILE_OBJECT_IDENTITY,
        SAME_PATH_ACCORDING_TO_LEXICAL_PATH,
        SAME_PATH_ACCORDING_TO_FILE_SYSTEM_PROVIDER,
        SAME_PATH_ACCORDING_TO_CANONICAL_PATH,
        SAME_CONTENT -> true
        else -> error("Unrecognized comparison code: ${this}")
    }
