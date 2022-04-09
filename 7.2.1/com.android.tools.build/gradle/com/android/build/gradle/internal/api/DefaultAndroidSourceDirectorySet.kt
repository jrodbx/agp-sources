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

package com.android.build.gradle.internal.api

import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.Callable

/**
 * Default implementation of the AndroidSourceDirectorySet.
 */
class DefaultAndroidSourceDirectorySet(
    private val sourceSetName: String,
    private val name: String,
    private val project: Project,
    private val type: SourceArtifactType
)
    : AndroidSourceDirectorySet {
    private val source = Lists.newArrayList<Any>()
    override val filter = PatternSet()

    override fun getName(): String {
        return "$sourceSetName $name"
    }

    fun getSourceSetName() = name

    override fun srcDir(srcDir: Any): AndroidSourceDirectorySet {
        source.add(srcDir)
        return this
    }

    override fun srcDirs(vararg srcDirs: Any): AndroidSourceDirectorySet {
        Collections.addAll(source, *srcDirs)
        return this
    }

    override fun setSrcDirs(srcDirs: Iterable<*>): AndroidSourceDirectorySet {
        source.clear()
        for (srcDir in srcDirs) {
            source.add(srcDir)
        }
        return this
    }

    override fun getSourceFiles(): FileTree {
        var src: FileTree? = null
        val sources = srcDirs
        if (sources.isNotEmpty()) {
            src = project.files(ArrayList<Any>(sources)).asFileTree.matching(filter)
        }
        return src ?: project.files().asFileTree
    }

    override fun getSourceDirectoryTrees(): List<ConfigurableFileTree> {
        return source.stream()
            .map { sourceDir ->
                project.fileTree(sourceDir) {
                    it.include(filter.asIncludeSpec)
                    it.exclude(filter.asExcludeSpec)
                }
            }
            .collect(ImmutableList.toImmutableList())
    }

    override val srcDirs: Set<File>
        get() = ImmutableSet.copyOf(project.files(*source.toTypedArray()).files)

    override fun toString()= "${super.toString()}, type=${type}, source=$source"

    @Deprecated("To be removed in 8.0")
    override fun getIncludes(): Set<String> {
        return filter.includes
    }

    @Deprecated("To be removed in 8.0")
    override fun getExcludes(): Set<String> {
        return filter.excludes
    }

    @Deprecated("To be removed in 8.0")
    override fun setIncludes(includes: Iterable<String>): PatternFilterable {
        filter.setIncludes(includes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun setExcludes(excludes: Iterable<String>): PatternFilterable {
        filter.setExcludes(excludes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun include(vararg includes: String): PatternFilterable {
        filter.include(*includes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun include(includes: Iterable<String>): PatternFilterable {
        filter.include(includes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun include(includeSpec: Closure<*>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun exclude(excludes: Iterable<String>): PatternFilterable {
        filter.exclude(excludes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun exclude(vararg excludes: String): PatternFilterable {
        filter.exclude(*excludes)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }

    @Deprecated("To be removed in 8.0")
    override fun exclude(excludeSpec: Closure<*>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }

    override fun getBuildableArtifact() : FileCollection {
        return project.files(Callable<Collection<File>> { srcDirs })
    }
}

