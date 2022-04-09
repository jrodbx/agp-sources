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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

class ProviderBasedDirectoryEntryImpl(
    override val name: String,
    val elements: Provider<Set<FileSystemLocation>>,
    override val filter: PatternFilterable? = null
): DirectoryEntry  {

    override val isGenerated: Boolean = true
    override val isUserAdded: Boolean = true
    override val shouldBeAddedToIdeModel: Boolean = true

    override fun asFiles(directoryPropertyCreator: () -> DirectoryProperty): Provider<Directory> {
        return elements.flatMap {
            if (it.size > 1) {
                throw RuntimeException("There are more than one element in $name\n" +
                        "${it.map { it.asFile.absolutePath }}")
            }
            directoryPropertyCreator().also { directoryProperty ->
                directoryProperty.set(it.single().asFile)
            }
        }
    }

    override fun asFileTree(fileTreeCreator: () -> ConfigurableFileTree): ConfigurableFileTree =
        fileTreeCreator().from(elements).builtBy(elements)


}
