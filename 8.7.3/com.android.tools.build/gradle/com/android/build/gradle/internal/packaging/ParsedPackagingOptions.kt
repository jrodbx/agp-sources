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

import com.android.build.gradle.internal.matcher.GlobPathMatcherFactory
import java.io.File
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * This class extends the information in the packaging options for resources or native libs by
 * compiling patterns and providing matching data.
 */
class ParsedPackagingOptions
/**
 * Creates a new parsed packaging options based on the provided options.
 *
 * @param excludePatterns the collection of patterns to exclude
 * @param pickFirstPatterns the collection of patterns to pick first
 * @param mergePatterns the collection of patterns to merge
 */
    constructor(
        excludePatterns: Collection<String>,
        pickFirstPatterns: Collection<String>,
        mergePatterns: Collection<String>
    )
{

    /**
     * Paths excluded.
     */
    private val excludes: Set<PathMatcher> = excludePatterns.map { compileGlob(it) }.toSet()

    /**
     * Paths that should do first-pick.
     */
    private val pickFirsts: Set<PathMatcher> = pickFirstPatterns.map { compileGlob(it) }.toSet()

    /**
     * Paths that should be merged.
     */
    private val merges: Set<PathMatcher> = mergePatterns.map { compileGlob(it) }.toSet()

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

    companion object {

        /**
         * Compiles a glob pattern.
         *
         * @param pattern the pattern
         * @return the matcher
         */
        @JvmStatic
        fun compileGlob(pattern: String): PathMatcher {

            return GlobPathMatcherFactory.create(
                if (!pattern.startsWith("/") && !pattern.startsWith("*"))
                    "/$pattern"
                else pattern
            )
        }
    }
}
