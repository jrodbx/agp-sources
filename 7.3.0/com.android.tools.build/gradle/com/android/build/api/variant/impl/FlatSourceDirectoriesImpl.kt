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

import com.android.build.api.variant.SourceDirectories
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

/**
 * A set of source directories for a specific [SourceType]
 *
 * @param _name name of the source directories, as returned by [SourceType.name]
 * @param variantServices the variant's [VariantServices]
 * @param variantDslFilters filters set on the variant specific source directory in the DSL, may be null if
 * the is no variant specific source directory.
 */
class FlatSourceDirectoriesImpl(
    private val _name: String,
    private val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?
): SourceDirectoriesImpl(_name, variantServices, variantDslFilters),
    SourceDirectories.Flat {

    // For compatibility with the old variant API, we must allow reading the content of this list
    // before it is finalized.
    private val variantSources = variantServices.newListPropertyForInternalUse(
        type = DirectoryEntry::class.java,
    )

    // this will contain all the directories
    private val directories = variantServices.newListPropertyForInternalUse(
        type = Directory::class.java,
    )

    override val all: Provider<out Collection<Directory>> = directories

    //
    // Internal APIs.
    //

    override fun addSource(directoryEntry: DirectoryEntry) {
        variantSources.add(directoryEntry)
        directories.add(directoryEntry.asFiles(variantServices::directoryProperty))
    }


    internal fun getAsFileTrees(): Provider<List<ConfigurableFileTree>> =
            variantSources.map { entries: MutableList<DirectoryEntry> ->
                entries.map { sourceDirectory ->
                    sourceDirectory.asFileTree(variantServices::fileTree)
                }
            }

    internal fun addSources(sourceDirectories: Iterable<DirectoryEntry>) {
        sourceDirectories.forEach(::addSource)
    }

    /*
     * Internal API that can only be used by the model.
     */
    override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean ): List<File> {
        val files = mutableListOf<File>()
        variantSources.get()
            .filter { filter.invoke(it) }
            .forEach {
                val asDirectoryProperty = it.asFiles(variantServices::directoryProperty)
                if (asDirectoryProperty.isPresent) {
                    files.add(asDirectoryProperty.get().asFile)
                }
            }
        return files
    }
}
