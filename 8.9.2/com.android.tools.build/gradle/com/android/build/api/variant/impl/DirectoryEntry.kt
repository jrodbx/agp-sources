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

import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Abstraction of a source directory within the Variant object model.
 */
interface DirectoryEntry {

    /**
     *  source folder name, human readable but not guaranteed to be unique.
     */
    val name: String

    /**
     * true if it contains generated sources, false it is editable by the user.
     */
    val isGenerated: Boolean

    /**
     * true if the user added this source folder (generated or not), false if it is a folder
     * that was automatically created by AGP.
     */
    val isUserAdded: Boolean

    /**
     * true if the folder should be added to the IDE model, false otherwise.
     */
    val shouldBeAddedToIdeModel: Boolean

    /**
     * Return the source folder as a [Provider] of [Directory], with appropriate
     * [org.gradle.api.Task] dependency if there is one. Can be used as a task input directly.
     */
    fun asFiles(
        projectDir: Provider<Directory>,
    ): Provider<out Collection<Directory>>

    /**
     * Return the source folder as a [ConfigurableFileTree] which can be used as
     * [org.gradle.api.Task] input.
     */
    fun asFileTree(
            fileTreeCreator: () -> ConfigurableFileTree
    ): Provider<List<ConfigurableFileTree>>

    /**
     * Return the source folders as a [List] of [ConfigurableFileTree] which CANNOT be used as
     * [org.gradle.api.Task] input. This method must only be called by
     * [FlatSourceDirectoriesImpl.getAsFileTreesForOldVariantAPI], all new usages should use
     * [asFiles] or [asFileTree]
     *
     * Remove once b/260920355 is fixed.
     */
    fun asFileTreeWithoutTaskDependency(
            fileTreeCreator: () -> ConfigurableFileTree
    ): List<ConfigurableFileTree>

    /**
     * Optional filter associated with this source folder.
     */
    val filter: PatternFilterable?

    /**
     * Make this source directory entry a dependent of [task]. If [task] is scheduled,
     * any producer of this source directory will be automatically scheduled for execution
     * before [task]'s execution.
     *
     * @param task task that should depend on this source directory entry producer.
     * @param projectDir the project directory, to be able to create a task input compatible version
     * of this [DirectoryEntry] instance.
     */
    fun makeDependentOf(task: Task, projectDir: Provider<Directory>,) {
        task.dependsOn(asFiles(projectDir))
    }
}
