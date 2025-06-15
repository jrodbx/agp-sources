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
import org.gradle.api.file.FileCollection
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
open class FlatSourceDirectoriesImpl(
    _name: String,
    private val variantServices: VariantServices,
    variantDslFilters: PatternFilterable?
): SourceDirectoriesImpl(_name, variantServices, variantDslFilters),
    SourceDirectories.Flat {

    // For compatibility with the old variant API, we must allow reading the content of this list
    // before it is finalized.
    internal val variantSources = variantServices.newListPropertyForInternalUse(
        type = DirectoryEntry::class.java,
    )

    // this will contain all the directories
    internal val directories = variantServices.newListPropertyForInternalUse(
        type = Directory::class.java,
    )

    // this will contain only non generated directories - having list to preserve order
    private val staticDirectories = variantServices.newListPropertyForInternalUse(
        type = Directory::class.java,
    )

    override val all: Provider<out Collection<Directory>> = directories

    override val static: Provider<out Collection<Directory>> = staticDirectories

    //
    // Internal APIs.
    //
    override fun addSource(directoryEntry: DirectoryEntry){
        variantSources.add(directoryEntry)
        directories.addAll(
            directoryEntry.asFiles(
                variantServices.provider {
                    variantServices.projectInfo.projectDirectory
                }
            )
        )
    }

    override fun addStaticSource(directoryEntry: DirectoryEntry){
        if (directoryEntry.isGenerated) throw IllegalArgumentException(
            "The task ${directoryEntry.name} is generating code and should not be added as a Static source")

        addSource(directoryEntry)
        staticDirectories.addAll(
            directoryEntry.asFiles(
                variantServices.provider {
                    variantServices.projectInfo.projectDirectory
                }
            )
        )
    }

    internal fun getAsFileTrees(): Provider<List<Provider<List<ConfigurableFileTree>>>> {
        val fileTreeFactory = variantServices.fileTreeFactory()
        return variantSources.map { entries: MutableList<DirectoryEntry> ->
            entries.map { sourceDirectory ->
                sourceDirectory.asFileTree(fileTreeFactory)
            }
        }
    }

    /**
     * version of the [getAsFileTrees] for consumers that are resolving the content during
     * configuration time, see b/259343260
     *
     * New code MUST NOT call this method.
     *
     */
    internal fun getAsFileTreesForOldVariantAPI(): Provider<List<ConfigurableFileTree>> {
        val fileTreeFactory = variantServices.fileTreeFactory()
        return variantSources.map { entries: MutableList<DirectoryEntry> ->
            entries.map { sourceDirectory ->
                sourceDirectory.asFileTreeWithoutTaskDependency(fileTreeFactory)
            }.flatten()
        }
    }

    internal fun getVariantSources(): List<DirectoryEntry> = variantSources.get()

    internal fun addStaticOrGeneratedSources(sourceDirectories: Iterable<DirectoryEntry>) {
        sourceDirectories.forEach { directoryEntry ->
            if (directoryEntry.isGenerated) {
                addSource(directoryEntry)
            } else {
                addStaticSource(directoryEntry)
            }
        }
    }
    /*
     * Internal API that can only be used by the model.
     */
    override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean ): List<File> =
        variantSourcesFileCollectionForModel(filter).files.toList()

    internal fun variantSourcesFileCollectionForModel(
        filter: (DirectoryEntry) -> Boolean
    ): FileCollection {
        val fileCollection = variantServices.fileCollection()
        variantSources.get()
            .filter { filter.invoke(it) }
            .forEach {
                if (it is TaskProviderBasedDirectoryEntryImpl) {
                    fileCollection.from(it.directoryProvider)
                } else {
                    fileCollection.from(
                        it.asFiles(
                            variantServices.provider {
                                variantServices.projectInfo.projectDirectory
                            }
                        )
                    )
                }
            }
        fileCollection.disallowChanges()
        return fileCollection
    }

    override fun forAllSources(action:(DirectoryEntry) -> Unit) {
        getVariantSources().forEach(action::invoke)
    }
}
