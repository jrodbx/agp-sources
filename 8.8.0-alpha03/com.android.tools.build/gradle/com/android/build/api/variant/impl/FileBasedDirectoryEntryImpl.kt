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

package com.android.build.api.variant.impl

import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File

/**
 * Implementation of [DirectoryEntry] for an existing directory. The directory is provided as a
 * [Provider] of [Directory] for convenience, the directory must exist with sources present during
 * configuration time.
 *
 * @param directory the directory that exists and contains source files.
 * @param filter optional filters to apply to the folder.
 * @param isUserAdded true if the user added this source folder or false if created by AGP.
 */
class FileBasedDirectoryEntryImpl(
    override val name: String,
    private val directory: File,
    override val filter: PatternFilterable? = null,
    override val isUserAdded: Boolean = false,
    override val shouldBeAddedToIdeModel: Boolean = false,
): DirectoryEntry {

    override fun asFiles(
        projectDir: Provider<Directory>
    ): Provider<out Collection<Directory>> =
        projectDir.map {
            setOf(it.dir(directory.absolutePath))
        }

    override val isGenerated: Boolean = false

    override fun asFileTree(
            fileTreeCreator: () -> ConfigurableFileTree
    ): Provider<List<ConfigurableFileTree>> =
        getFileTree(fileTreeCreator).run {
            elements.map { listOf(this)}
        }

    override fun asFileTreeWithoutTaskDependency(
            fileTreeCreator: () -> ConfigurableFileTree,
    ): List<ConfigurableFileTree> = listOf(getFileTree(fileTreeCreator))

    private fun getFileTree(
            fileTreeCreator: () -> ConfigurableFileTree,
    ): ConfigurableFileTree =
            fileTreeCreator().setDir(directory).also {
                if (filter != null) {
                    it.include((filter as PatternSet).asIncludeSpec)
                    it.exclude(filter.asExcludeSpec)
                }
            }
}
