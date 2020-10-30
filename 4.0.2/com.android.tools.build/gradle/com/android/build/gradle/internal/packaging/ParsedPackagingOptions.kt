/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.packaging

import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.matcher.GlobPathMatcherFactory
import com.google.common.collect.ImmutableSet
import java.io.File
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Maintains a set of parsed packaging options. Packaging options are defined in
 * [PackagingOptions]. This class extends the information in the packaging options by
 * compiling patterns and providing matching data.
 */
class ParsedPackagingOptions
/**
 * Creates a new parsed packaging options based on the provided options.
 *
 * @param packagingOptions the DSL packaging options, if `null`, it is interpreted as
 * an empty packaging options
 */
    constructor(
        packagingOptions: SerializablePackagingOptions =
            SerializablePackagingOptions(PackagingOptions())
    )
{

    constructor(packagingOptions: PackagingOptions) :
            this(SerializablePackagingOptions(packagingOptions))

    /**
     * Paths excluded.
     */
    private val excludes: Set<PathMatcher>

    /**
     * Paths that should do first-pick.
     */
    private val pickFirsts: Set<PathMatcher>

    /**
     * Paths that should be merged.
     */
    private val merges: Set<PathMatcher>

    /**
     * Exclude patterns.
     */
    /**
     * Obtains the raw set of exclude patterns.
     *
     * @return the patterns
     */
    private val excludePatterns: Set<String>

    /**
     * Pick-first patterns.
     */
    /**
     * Obtains the raw set of pick first patterns.
     *
     * @return the patterns
     */
    private val pickFirstPatterns: Set<String>

    /**
     * Merge patterns.
     */
    /**
     * Obtains the raw set of merge patterns.
     *
     * @return the patterns
     */
    private val mergePatterns: Set<String>

    init {
        excludePatterns = ImmutableSet.copyOf(packagingOptions.excludes)
        pickFirstPatterns = ImmutableSet.copyOf(packagingOptions.pickFirsts)
        mergePatterns = ImmutableSet.copyOf(packagingOptions.merges)
        excludes = packagingOptions.excludes.map(this::compileGlob).toSet()
        pickFirsts = packagingOptions.pickFirsts.map(this::compileGlob).toSet()
        merges = packagingOptions.merges.map(this::compileGlob).toSet()
    }

    /**
     * Compiles a glob pattern.
     *
     * @param pattern the pattern
     * @return the matcher
     */
    private fun compileGlob(pattern: String): PathMatcher {

        return GlobPathMatcherFactory.create(
            if (!pattern.startsWith("/") && !pattern.startsWith("*"))
                "/$pattern"
            else pattern)
    }

    /**
     * Obtains the action to perform for a path.
     *
     * @param archivePath the path
     * @return the packaging action
     */
    fun getAction(archivePath: String): PackagingFileAction {
        var absPath = archivePath
        if (!absPath.startsWith("/")) {
            absPath = "/$absPath"
        }

        val path = Paths.get(absPath.replace('/', File.separatorChar))

        if (pickFirsts.stream().anyMatch { m -> m.matches(path) }) {
            return PackagingFileAction.PICK_FIRST
        }

        if (merges.stream().anyMatch { m -> m.matches(path) }) {
            return PackagingFileAction.MERGE
        }

        return if (excludes.stream().anyMatch { m -> m.matches(path) }) {
            PackagingFileAction.EXCLUDE
        } else PackagingFileAction.NONE

    }
}
