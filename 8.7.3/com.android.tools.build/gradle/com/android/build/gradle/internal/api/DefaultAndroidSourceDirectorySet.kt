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

import com.android.build.api.variant.impl.FileBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.ProviderBasedDirectoryEntryImpl
import com.android.build.api.variant.impl.SourceDirectoriesImpl
import com.android.build.gradle.api.AndroidSourceDirectorySet
import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.scope.getDirectories
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import java.io.File
import java.nio.file.Path
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
    /**
     * Set once the sourceset is read to produce the android components, so that subsequent
     * additions (e.g. during the applicationVariants API) are not ignored.
     *
     * This ends up being the list of variant source directories this source set feeds into.
     */
    private val lateAdditionsDelegates = mutableListOf<SourceDirectoriesImpl>()

    override fun getName(): String {
        return "$sourceSetName $name"
    }

    fun getSourceSetName() = name

    override fun srcDir(srcDir: Any): AndroidSourceDirectorySet {
        if (srcDir is Iterable<*> && srcDir !is Path) {
            srcDir.forEach { src ->
                src?.let { srcDir(it) }
            }
            return this
        }
        source.add(srcDir)
        if (lateAdditionsDelegates.isNotEmpty()) {
            val entry = when(srcDir) {
                // srcDir can be anything, including a Provider<> that we must not resolve. At least
                // treat File and Directory differently, so we don't do any I/O during configuration
                // as project.file(srcDir).getDirectories(project.layout.projectDirectory) will
                // get eagerly resolved during configuration as it is a resolvable provider.
                is File ->
                    FileBasedDirectoryEntryImpl(
                        sourceSetName,
                        srcDir,
                        filter,
                        isUserAdded = false,
                        shouldBeAddedToIdeModel = true,
                    )
                is Directory ->
                    ProviderBasedDirectoryEntryImpl(
                        sourceSetName,
                        project.provider { listOf(srcDir) },
                        filter,
                        isUserAdded = false,
                        isGenerated = false,
                    )
                else ->
                    ProviderBasedDirectoryEntryImpl(
                        sourceSetName,
                        project.files(srcDir).getDirectories(project.layout.projectDirectory),
                        filter,
                        isUserAdded = false,
                        isGenerated = false,
                    )
            }
            lateAdditionsDelegates.forEach { it.addStaticSource(entry) }
        }
        return this
    }

    override fun srcDirs(vararg srcDirs: Any): AndroidSourceDirectorySet {
        for (dir in srcDirs) {
            srcDir(dir)
        }
        return this
    }

    override fun setSrcDirs(srcDirs: Iterable<*>): AndroidSourceDirectorySet {
        if (lateAdditionsDelegates.isNotEmpty()) {
            /**
             * Filter out potential duplicates to avoid re-registering things that have already
             * been registered. (Note that actually removing things that have already been added
             * is not supported after AGP DSL finalization)
             *
             *  If a build author writes in groovy `srcDirs += "othersrc"`
             *  this becomes something like
             *  setSrcDirs(mutableSetOf().also {addAll(getSrcDirs()); add("otherSrc") })
             *
             *  So if this sourceDirectorySet contained ["src/main/java"], `srcDirs += "othersrc"` will
             *  call setSrcDirs(listOf(File("/absolute/path/src/main/java"), "othersrc")).
             *
             *  And we want to avoid registering src/main/java twice to avoid duplicate definition
             *  errors when compiling.
             */
            val previousFiles = this.srcDirs
            val newFiles = project.files(srcDirs).files
            for (newFile in (newFiles - previousFiles)) {
                val directoryEntry = ProviderBasedDirectoryEntryImpl(
                    name,
                    project.files(newFile).getDirectories(project.layout.projectDirectory),
                    filter,
                    isUserAdded = false,
                    isGenerated = false,
                )
                lateAdditionsDelegates.forEach { it.addStaticSource(directoryEntry) }
            }
        }
        source.clear()
        for (dir in srcDirs) {
            source.add(dir)
        }
        return this
    }

    override val directories = object : AbstractMutableSet<String>() {
        override fun add(element: String) = source.add(element)
        override val size: Int
            get() = source.size

        override fun iterator(): MutableIterator<String> = object: MutableIterator<String> {
            val baseIterator = source.iterator()
            override fun hasNext(): Boolean = baseIterator.hasNext()
            override fun next(): String  = baseIterator.next().toString()
            override fun remove() = baseIterator.remove()
        }
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

    @Deprecated("To be removed in 9.0")
    override fun getIncludes(): Set<String> {
        return filter.includes
    }

    @Deprecated("To be removed in 9.0")
    override fun getExcludes(): Set<String> {
        return filter.excludes
    }

    @Deprecated("To be removed in 9.0")
    override fun setIncludes(includes: Iterable<String>): PatternFilterable {
        filter.setIncludes(includes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun setExcludes(excludes: Iterable<String>): PatternFilterable {
        filter.setExcludes(excludes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun include(vararg includes: String): PatternFilterable {
        filter.include(*includes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun include(includes: Iterable<String>): PatternFilterable {
        filter.include(includes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun include(includeSpec: Closure<*>): PatternFilterable {
        filter.include(includeSpec)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun exclude(excludes: Iterable<String>): PatternFilterable {
        filter.exclude(excludes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun exclude(vararg excludes: String): PatternFilterable {
        filter.exclude(*excludes)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }

    @Deprecated("To be removed in 9.0")
    override fun exclude(excludeSpec: Closure<*>): PatternFilterable {
        filter.exclude(excludeSpec)
        return this
    }

    override fun getBuildableArtifact() : FileCollection {
        return project.files(Callable<Collection<File>> { srcDirs })
    }

    internal fun addLateAdditionDelegate(lateAdditionDelegate: SourceDirectoriesImpl) {
        lateAdditionsDelegates += lateAdditionDelegate
    }
}

