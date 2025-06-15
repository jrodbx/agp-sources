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

class ProviderBasedDirectoryEntryImpl(
    override val name: String,
    val elements: Provider<List<Directory>>,
    override val filter: PatternFilterable?,
    override val isUserAdded: Boolean = true,
    override val isGenerated: Boolean = true,
): DirectoryEntry  {

    override val shouldBeAddedToIdeModel: Boolean = true

    override fun asFiles(
        projectDir: Provider<Directory>
    ): Provider<out Collection<Directory>> {
        return elements
    }

    override fun asFileTree(
            fileTreeCreator: () -> ConfigurableFileTree
    ): Provider<List<ConfigurableFileTree>> {
        return elements.map {
            asConfigurableFileTrees(fileTreeCreator, it)
        }
    }

    override fun asFileTreeWithoutTaskDependency(
            fileTreeCreator: () -> ConfigurableFileTree,
    ): List<ConfigurableFileTree> =
            asConfigurableFileTrees(
                    fileTreeCreator,
                    elements.get(),
            )

    private fun asConfigurableFileTrees(
            fileTreeCreator: () -> ConfigurableFileTree,
            directories: Collection<Directory>
    ) = directories.map { directory ->
        fileTreeCreator()
                .from(directory)
                .also { configurableFileTree ->
                    if (filter != null) {
                        configurableFileTree.include((filter as PatternSet).asIncludeSpec)
                        configurableFileTree.exclude(filter.asExcludeSpec)
                    }
                }
    }
}
