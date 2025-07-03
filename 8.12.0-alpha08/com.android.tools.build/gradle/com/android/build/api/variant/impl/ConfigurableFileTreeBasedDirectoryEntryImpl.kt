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

/**
 * Implementation of [DirectoryEntry] based on a [ConfigurableFileTree]. This is for backward
 * compatibility of the old variant API.
 *
 * Do not use without explicitly stating the reasons as this class should be removed once the old
 * variant API is removed.
 */
class ConfigurableFileTreeBasedDirectoryEntryImpl(
    override val name: String,
    private val configurableFileTree: ConfigurableFileTree,
): DirectoryEntry {

    override fun asFiles(
        projectDir: Provider<Directory>,
    ): Provider<out Collection<Directory>> =
        configurableFileTree.elements.zip(projectDir) { _, projectDir ->
            listOf(projectDir.dir(configurableFileTree.dir.absolutePath))
        }

    override val isGenerated: Boolean = true
    override val isUserAdded: Boolean = true
    override val shouldBeAddedToIdeModel: Boolean = true

    override val filter: PatternFilterable?
        get() = null

    override fun asFileTree(
            fileTreeCreator: () -> ConfigurableFileTree,
    ) = configurableFileTree.elements.map { listOf(configurableFileTree) }

    override fun asFileTreeWithoutTaskDependency(
            fileTreeCreator: () -> ConfigurableFileTree,
    ): List<ConfigurableFileTree> = listOf(configurableFileTree)

}

