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

@file:JvmName("IncrementalFileMergerTaskUtils")

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.QualifiedContent.ScopeType
import com.android.builder.files.KeyedFileCache
import com.android.builder.files.IncrementalRelativeFileSets
import com.android.builder.files.RelativeFile
import com.android.builder.files.RelativeFiles
import com.android.builder.files.ZipCentralDirectory
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.builder.merge.LazyIncrementalFileMergerInput
import com.android.builder.merge.LazyIncrementalFileMergerInputs
import com.android.ide.common.resources.FileStatus
import com.android.tools.build.apkzlib.utils.CachedSupplier
import com.android.tools.build.apkzlib.utils.IOExceptionRunnable
import com.android.utils.FileUtils
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.HashSet


/**
 * Creates an [IncrementalFileMergerInput] from a file (either a directory or jar file). All
 * children of the file will be reported in the incremental input.
 *
 * @param input the input file
 * @param changedInputs the map of changed files to the type of change
 * @param zipCache the zip cache; the cache will not be modified
 * @param cacheUpdates will receive actions to update the cache for the next iteration
 * @return the input
 */
fun toIncrementalInput(
    input: File,
    changedInputs: Map<File, FileStatus>,
    zipCache: KeyedFileCache,
    cacheUpdates: MutableList<Runnable>
): IncrementalFileMergerInput {
    if (input.name.endsWith(SdkConstants.DOT_JAR)) {
        val jarCDR = ZipCentralDirectory(input)
        if (changedInputs.containsKey(input)) {
            cacheUpdates.add(IOExceptionRunnable.asRunnable {
                if (input.isFile) {
                    zipCache.add(jarCDR)
                } else {
                    zipCache.remove(input)
                }
            })
        }
        return LazyIncrementalFileMergerInput(
            input.absolutePath,
            CachedSupplier { computeUpdatesFromJar(jarCDR, changedInputs, zipCache) },
            CachedSupplier { computeFilesFromJar(jarCDR) }
        )
    }

    Preconditions.checkState(!input.isFile, "Non-directory inputs must have .jar extension: $input")
    return LazyIncrementalFileMergerInput(
        input.absolutePath,
        CachedSupplier { computeUpdatesFromDir(input, changedInputs) },
        CachedSupplier { computeFilesFromDir(input) }
    )
}

/**
 * Creates an [IncrementalFileMergerInput] from a [File]. This method assumes the input does not
 * contain incremental information. All files will be reported as new.
 *
 * @param input the file input
 * @param zipCache the zip cache; the cache will not be modified
 * @param cacheUpdates will receive actions to update the cache for the next iteration, if
 *        file.isFile is true, in which case it's assumed to be a jar file.
 * @return the input or `null` if the file does not exist
 */
fun toNonIncrementalInput(
    input: File,
    zipCache: KeyedFileCache,
    cacheUpdates: MutableList<Runnable>
): IncrementalFileMergerInput? {
    if (!input.isFile && !input.isDirectory) {
        return null
    }

    if (input.isFile) {
        cacheUpdates.add(IOExceptionRunnable.asRunnable {  zipCache.add(input) })
    }

    return LazyIncrementalFileMergerInputs.fromNew(input.absolutePath, ImmutableSet.of(input))
}

/**
 * Computes all updates in a jar file.
 *
 * @param jar the jar file
 * @param changedInputs the map of changed files to the type of change
 * @param zipCache the cache of zip files; the cache will not be modified
 * @return a mapping from all files that have changed to the type of change
 */
private fun computeUpdatesFromJar(
    jar: ZipCentralDirectory,
    changedInputs: Map<File, FileStatus>,
    zipCache: KeyedFileCache
): Map<RelativeFile, FileStatus> {
    if (jar.file in changedInputs) {
        val fileStatus = changedInputs[jar.file]
        try {
            return when (fileStatus) {
                FileStatus.NEW -> IncrementalRelativeFileSets.fromZip(jar, FileStatus.NEW)
                FileStatus.REMOVED -> {
                    val cached = zipCache.get(jar.file) ?: throw RuntimeException(
                        "File '$jar' was deleted, but previous version not found in cache"
                    )

                    IncrementalRelativeFileSets.fromZip(cached, FileStatus.REMOVED)
                }
                FileStatus.CHANGED -> IncrementalRelativeFileSets.fromZip(jar, zipCache, HashSet())
                else -> throw AssertionError("Unexpected FileStatus: $fileStatus")
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }
    return ImmutableMap.of()

}

/**
 * Computes a set with all files in a jar file.
 *
 * @param jar the jar input
 * @return all files in the jar file
 */
private fun computeFilesFromJar(jar: ZipCentralDirectory): Set<RelativeFile> {
    if (!jar.file.isFile) {
        return ImmutableSet.of()
    }
    try {
        return RelativeFiles.fromZip(jar)
    } catch (e: IOException) {
        throw UncheckedIOException(e)
    }
}

/**
 * Computes all updates in a directory.
 *
 * @param dir the directory
 * @param changedInputs the map of changed files to the type of change
 * @return a mapping from all files in dir that have changed to the type of change
 */
private fun computeUpdatesFromDir(
    dir: File,
    changedInputs: Map<File, FileStatus>
): ImmutableMap<RelativeFile, FileStatus> {
    val builder = ImmutableMap.builder<RelativeFile, FileStatus>()
    for ((file, status) in changedInputs) {
        if (!FileUtils.isFileInDirectory(file, dir)) {
            continue
        }
        val rf = RelativeFile(dir, file)
        if (!rf.file.isDirectory) {
            builder.put(rf, status)
        }
    }

    return builder.build()
}

/**
 * Computes a set with all files in a directory.
 *
 * @param dir the directory
 * @return all files in the directory
 */
private fun computeFilesFromDir(dir: File): Set<RelativeFile> {
    if (!dir.isDirectory) {
        return ImmutableSet.of()
    }
    return RelativeFiles.fromDirectory(dir)
}

/**
 * Creates a list of [IncrementalFileMergerInput] from a map of [File]s to [Scope]s.
 *
 * @param inputMap map of files to their corresponding scopes
 * @param changedInputs map of files to file status, passed from the incremental task, or null if
 * the task is not incremental
 * @param zipCache the zip cache; the cache will not be modified
 * @param cacheUpdates receives updates to the cache
 * @param full is this a full build? If not, then it is an incremental build; in full builds
 * the output is not cleaned, it is the responsibility of the caller to ensure the output
 * is properly set up; `full` cannot be `false` if changedInputs is null
 * @param scopeMap receives a mapping from all generated inputs to their scopes
 */
fun toInputs(
    inputMap: MutableMap<File, ScopeType>,
    changedInputs: Map<File, FileStatus>?,
    zipCache: KeyedFileCache,
    cacheUpdates: MutableList<Runnable>,
    full: Boolean,
    scopeMap: MutableMap<IncrementalFileMergerInput, ScopeType>
): ImmutableList<IncrementalFileMergerInput> {
    if (full) {
        cacheUpdates.add(IOExceptionRunnable.asRunnable { zipCache.clear() })
    }

    val builder = ImmutableList.builder<IncrementalFileMergerInput>()
    for ((input, scope) in inputMap.entries) {
        val fileMergerInput: IncrementalFileMergerInput? = if (full) {
            toNonIncrementalInput(input, zipCache, cacheUpdates)
        } else {
            changedInputs ?: throw RuntimeException(
                "changedInputs must be specified for incremental merging."
            )
            toIncrementalInput(input, changedInputs, zipCache, cacheUpdates)
        }

        fileMergerInput?.let {
            builder.add(it)
            // Add mapping of fileMergerInput to its scope
            scopeMap[it] = scope
        }
    }

    return builder.build()
}

