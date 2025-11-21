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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.scope.getDirectories
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

class FileCollectionBasedDirectoryEntryImpl(
    override val name: String,
    private val fileCollection: FileCollection,
): DirectoryEntry {

    override val isGenerated: Boolean = true
    override val isUserAdded: Boolean = true
    override val shouldBeAddedToIdeModel: Boolean = true

    override fun asFiles(projectDir: Provider<Directory>): Provider<out Collection<Directory>> =
        fileCollection.elements.zip(projectDir) {
                elements: MutableSet<FileSystemLocation>,
                dir: Directory ->
            elements.map { dir.dir(it.asFile.path) }
        }

    override fun asFileTree(fileTreeCreator: () -> ConfigurableFileTree): Provider<List<ConfigurableFileTree>> =
        fileCollection.elements.map { files ->
            files.filter { it.asFile.isDirectory || !it.asFile.exists() }
                .map { directory ->
                    fileTreeCreator().also {
                        it.from(directory)
                    }
            }
        }

    override fun asFileTreeWithoutTaskDependency(fileTreeCreator: () -> ConfigurableFileTree): List<ConfigurableFileTree> =
        listOf(
            fileTreeCreator().also {
                it.from(fileCollection.asFileTree.asPath)
            }
        )

    override val filter: PatternFilterable? = null
}
