/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.api

import org.gradle.api.Incubating
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternFilterable
import java.io.File

/**
 * An AndroidSourceDirectorySet represents a set of directory inputs for an Android project.
 */
@Deprecated("Use  com.android.build.api.dsl.AndroidSourceDirectorySet")
interface AndroidSourceDirectorySet : PatternFilterable, com.android.build.api.dsl.AndroidSourceDirectorySet {

    override fun getName(): String

    override fun srcDir(srcDir: Any): AndroidSourceDirectorySet

    override fun srcDirs(vararg srcDirs: Any): AndroidSourceDirectorySet

    override fun setSrcDirs(srcDirs: Iterable<*>): AndroidSourceDirectorySet

    /**
     * Returns the list of source files as a [org.gradle.api.file.FileTree]
     *
     * @return a non null [FileTree] for all the source files in this set.
     */
    fun getSourceFiles(): FileTree

    /**
     * Returns the filter used to select the source from the source directories.
     *
     * @return a non null [org.gradle.api.tasks.util.PatternFilterable]
     */
    val filter: PatternFilterable

    /**
     * Returns the source folders as a list of [org.gradle.api.file.ConfigurableFileTree]
     *
     *
     * This is used as the input to the java compile to enable incremental compilation.
     *
     * @return a non null list of [ConfigurableFileTree]s, one per source dir in this set.
     */
    fun getSourceDirectoryTrees(): List<ConfigurableFileTree>

    /**
     * Returns the resolved directories.
     *
     * Setter can be called with a collection of [Object]s, just like
     * Gradle's `project.file(...)`.
     *
     * @return a non null set of File objects.
     */
    val srcDirs: Set<File>

    /** Returns the [FileCollection] that represents this source sets.  */
    @Incubating
    fun getBuildableArtifact(): FileCollection
}
