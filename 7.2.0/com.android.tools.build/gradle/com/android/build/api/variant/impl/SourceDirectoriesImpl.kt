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
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File


/**
 * A set of source directories for a specific [SourceType]
 *
 * @param _name name of the source directories, as returned by [SourceType.name]
 * @param projectDirectory the project's directory.
 * @param variantServices the variant's [VariantPropertiesApiServices]
 * @param variantDslFilters filters set on the variant specific source directory in the DSL, may be null if
 * the is no variant specific source directory.
 */
class SourceDirectoriesImpl(
    private val _name: String,
    private val projectDirectory: Directory,
    private val variantServices: VariantPropertiesApiServices,
    private val variantDslFilters: PatternFilterable?
): SourceDirectories {

    // For compatibility with the old variant API, we must allow reading the content of this list
    // before it is finalized.
    private val variantSources = variantServices.newListPropertyForInternalUse(
        type = DirectoryEntry::class.java,
    )

    // this will contain all the directories
    private val directories = variantServices.newListPropertyForInternalUse(
        type = Directory::class.java,
    )

    /**
     * Filters to use for the variant source folders only.
     * This will be initialized from the variant DSL source folder filters if it exists or empty
     * if it does not.
     */
    val filter = PatternSet().also {
        if (variantDslFilters != null) {
            it.setIncludes(variantDslFilters.includes)
            it.setExcludes(variantDslFilters.excludes)
        }
    }

    override val all: Provider<List<Directory>> = directories

    override fun <T : Task> add(taskProvider: TaskProvider<T>, wiredWith: (T) -> Provider<Directory>) {
        val mappedValue: Provider<Directory> = taskProvider.flatMap {
            wiredWith(it)
        }
        addSource(
            TaskProviderBasedDirectoryEntryImpl(
                "$name-${taskProvider.name}",
                mappedValue,
                isUserAdded = true,
            )
        )
    }

    override fun getName(): String = _name

    override fun addSrcDir(srcDir: String) {
        val directory = projectDirectory.dir(srcDir)
        if (directory.asFile.exists() && !directory.asFile.isDirectory) {
            throw IllegalArgumentException("$srcDir does not point to a directory")
        }
        addSource(
            FileBasedDirectoryEntryImpl(
                name = "variant",
                directory = directory.asFile,
                filter = filter,
                isUserAdded = true
            )
        )
    }

    //
    // Internal APIs.
    //

    internal fun addSource(directoryEntry: DirectoryEntry) {
        variantSources.add(directoryEntry)
        directories.add(directoryEntry.asFiles(variantServices::directoryProperty))
    }

    internal fun addSources(sourceDirectories: Iterable<DirectoryEntry>) {
        sourceDirectories.forEach(::addSource)
    }

    internal fun getAsFileTrees(): Provider<List<ConfigurableFileTree>> =
            variantSources.map { entries: MutableList<DirectoryEntry> ->
                entries.map { sourceDirectory ->
                    sourceDirectory.asFileTree(variantServices::fileTree)
                }
            }

    /*
     * Internal API that can only be used by the model.
     */
    internal fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean ): List<File> = variantSources.get()
        .filter { filter.invoke(it) }
        .map { it.asFiles(variantServices::directoryProperty).get().asFile }
}
