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

@file:JvmName("ArtifactTypeUtil")
package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.Artifact
import com.android.utils.FileUtils
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Locale

/**
 * Returns a suitable output directory for this receiving artifact type.
 * This folder will be independent of variant or tasks names and should be further qualified
 * if necessary.
 *
 * @param parentFile the parent directory.
 */
fun Artifact<*>.getOutputDir(parentDir: File): File =
    FileUtils.join(parentDir, category.name.lowercase(Locale.US), getFolderName())

fun Artifact<*>.getIntermediateOutputDir(parentDir: File): File =
    FileUtils.join(parentDir, Artifact.Category.INTERMEDIATES.name.lowercase(Locale.US), getFolderName())

/**
 * Returns a [File] representing the artifact type location (could be a directory or regular file).
 *
 * @param buildDirectory the parent build folder
 * @param identifier the unique scoping identifier
 * @param taskName the task name to append to the path, or null if not necessary
 * @param forceFilename to overwrite default fileName
 * @return a [File] that can be safely use as task output.
 */
fun Artifact<*>.getOutputPath(
    buildDirectory: DirectoryProperty,
    variantIdentifier: String,
    vararg paths: String,
    forceFilename: String = ""
) = FileUtils.join(
    getOutputDir(buildDirectory.get().asFile),
    variantIdentifier,
    *paths,
    forceFilename.ifEmpty { getFileSystemLocationName() }
)

/**
 * Force intermediate directory
 */
fun Artifact<*>.getIntermediateOutputPath(
    buildDirectory: DirectoryProperty,
    variantIdentifier: String,
    vararg paths: String,
    forceFilename: String = ""
): File = FileUtils.join(
    getIntermediateOutputDir(buildDirectory.get().asFile),
    variantIdentifier,
    *paths,
    forceFilename.ifEmpty { getFileSystemLocationName() }
)

/**
 * Converts a [FileCollection] to a [Provider] of a [List] of [RegularFile], filtering other types
 * like [Directory]
 */
fun FileCollection.getRegularFiles(projectDirectory: Directory): Provider<List<RegularFile>> =
    elements.map {
        it.filter { file -> file.asFile.isFile }
            .map {
                fileSystemLocation -> projectDirectory.file(fileSystemLocation.asFile.absolutePath)
        }
    }

/**
 * Converts a [FileCollection] to a [Provider] of a [List] of [Directory], ignoring other types
 * like [RegularFile]
 */
fun FileCollection.getDirectories(projectDirectory: Directory): Provider<List<Directory>> =
    elements.map {
        it.filter { file -> file.asFile.isDirectory }
            .map {
                fileSystemLocation -> projectDirectory.dir(fileSystemLocation.asFile.absolutePath)
        }
    }
